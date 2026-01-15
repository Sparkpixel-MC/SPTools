package cn.ymjacky.transaction;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.database.MySQLManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
    private final boolean enabled;
    private final String serverUrl;
    private final int retryInterval;

    private final Gson gson;

    private HttpClient httpClient;
    private ScheduledExecutorService retryExecutor;
    private ScheduledExecutorService uploadExecutor;
    private volatile boolean isConnected;
    private volatile boolean isShuttingDown;

    public TransactionUploadManager(SPToolsPlugin plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
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

            plugin.getLogger().info("Transaction upload manager initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize transaction upload manager", e);
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
                plugin.getLogger().info("Successfully connected to transaction server: " + serverUrl);
                uploadUnsentRecords();
            } else {
                plugin.getLogger().warning("Failed to connect to transaction server, status code: " + response.statusCode());
            }
        } catch (Exception e) {
            isConnected = false;
            plugin.getLogger().log(Level.WARNING, "Failed to connect to transaction server, retrying in " + retryInterval + " seconds", e);
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
                plugin.getLogger().info("Attempting to reconnect to transaction server...");
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
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve unsent transaction records", e);
        }
    }

    private void uploadRecord(TransactionRecord record, String transactionId) {
        try {
            String json = gson.toJson(record);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                markAsSent(transactionId);
                plugin.getLogger().info("Successfully uploaded transaction record: " + transactionId);
            } else {
                plugin.getLogger().warning("Failed to upload transaction record, status code: " + response.statusCode());
                isConnected = false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to upload transaction record", e);
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
            plugin.getLogger().log(Level.WARNING, "Failed to mark transaction record as sent", e);
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
            plugin.getLogger().log(Level.SEVERE, "Failed to save transaction record to database", e);
        }
    }

    private void uploadUnsentRecords() {
        plugin.getLogger().info("Starting to upload unsent records...");
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

        plugin.getLogger().info("TransactionUploadManager closed");
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