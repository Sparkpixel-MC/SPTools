package cn.ymjacky.transaction;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.database.MySQLManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TransactionUploadManager {
    private final SPToolsPlugin plugin;
    private final MySQLManager mysqlManager;
    private final Economy economy;
    private final boolean enabled;
    private final String serverUrl;
    private final int retryInterval;

    private final Gson gson;

    private HttpClient httpClient;
    private ScheduledExecutorService retryExecutor;
    private ScheduledExecutorService uploadExecutor;
    private volatile boolean isConnected;
    private volatile boolean isShuttingDown;

    public TransactionUploadManager(SPToolsPlugin plugin, MySQLManager mysqlManager, Economy economy) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
        this.economy = economy;
        this.enabled = plugin.getConfig().getBoolean("transaction_upload_enabled", false);
        this.serverUrl = plugin.getConfig().getString("transaction_upload_url", "http://localhost:8080/transactions");
        this.retryInterval = plugin.getConfig().getInt("transaction_upload_retry_interval", 10);

        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        this.isConnected = false;
        this.isShuttingDown = false;

        if (enabled) {
            initialize();
        }
    }

    private void initialize() {
        try {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            testConnection();
            startRetryTask();
            startUploadTask();

            plugin.getLogger().info("交易记录上传管理器已初始化");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "初始化交易记录上传管理器失败", e);
        }
    }

    private void testConnection() {
        if (isShuttingDown) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(Duration.ofSeconds(10))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            isConnected = response.statusCode() >= 200 && response.statusCode() < 300;

            if (isConnected) {
                plugin.getLogger().info("成功连接到交易记录服务器: " + serverUrl);
                uploadUnsentRecords();
            } else {
                plugin.getLogger().warning("连接交易记录服务器失败，状态码: " + response.statusCode());
            }
        } catch (Exception e) {
            isConnected = false;
            plugin.getLogger().log(Level.WARNING, "连接交易记录服务器失败，将在 " + retryInterval + " 秒后重试", e);
        }
    }

    private void startRetryTask() {
        retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TransactionUpload-Retry");
            thread.setDaemon(true);
            return thread;
        });

        retryExecutor.scheduleAtFixedRate(() -> {
            if (!isConnected && !isShuttingDown) {
                plugin.getLogger().info("尝试重新连接交易记录服务器...");
                testConnection();
            }
        }, retryInterval, retryInterval, TimeUnit.SECONDS);
    }

    private void startUploadTask() {
        uploadExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TransactionUpload-Dispatcher");
            thread.setDaemon(true);
            return thread;
        });

        uploadExecutor.scheduleAtFixedRate(this::processQueue, 1, 1, TimeUnit.SECONDS);
    }

    private void processQueue() {
        if (!isConnected || !mysqlManager.isConnected()) {
            return;
        }

        // 从数据库获取未发送的交易记录
        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "SELECT * FROM transactions WHERE sent = 0 OR sent IS NULL LIMIT 100";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    TransactionRecord record = new TransactionRecord(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            TransactionRecord.TransactionType.valueOf(rs.getString("type")),
                            rs.getDouble("amount"),
                            rs.getDouble("balance_before"),
                            rs.getDouble("balance_after"),
                            rs.getString("description")
                    );
                    
                    uploadRecord(record, rs.getString("transaction_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "获取未发送的交易记录失败", e);
        }
    }

    private void uploadRecord(TransactionRecord record, String transactionId) {
        try {
            String json = gson.toJson(record);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .header("Connection", "keep-alive")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // 标记为已发送
                markAsSent(transactionId);
                plugin.getLogger().info("成功上传交易记录: " + transactionId);
            } else {
                plugin.getLogger().warning("上传交易记录失败，状态码: " + response.statusCode());
                isConnected = false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "上传交易记录失败", e);
            isConnected = false;
        }
    }

    private void markAsSent(String transactionId) {
        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "UPDATE transactions SET sent = 1 WHERE transaction_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, transactionId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "标记交易记录为已发送失败", e);
        }
    }

    public void addTransaction(TransactionRecord record) {
        if (!enabled) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = """
                INSERT INTO transactions (transaction_id, player_uuid, player_name, type, amount, 
                balance_before, balance_after, description, timestamp, sent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0)
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, record.getTransactionId());
                stmt.setString(2, record.getPlayerUuid().toString());
                stmt.setString(3, record.getPlayerName());
                stmt.setString(4, record.getType().name());
                stmt.setDouble(5, record.getAmount());
                stmt.setDouble(6, record.getBalanceBefore());
                stmt.setDouble(7, record.getBalanceAfter());
                stmt.setString(8, record.getDescription());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存交易记录到数据库失败", e);
        }
    }

    private void uploadUnsentRecords() {
        plugin.getLogger().info("开始上传未发送的交易记录");
    }

    public void shutdown() {
        isShuttingDown = true;

        if (retryExecutor != null) {
            retryExecutor.shutdown();
            try {
                if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryExecutor.shutdownNow();
            }
        }

        if (uploadExecutor != null) {
            uploadExecutor.shutdown();
            try {
                if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    uploadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                uploadExecutor.shutdownNow();
            }
        }

        plugin.getLogger().info("交易记录上传管理器已关闭");
    }

    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {
        private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc,
                                                      com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                                        com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}