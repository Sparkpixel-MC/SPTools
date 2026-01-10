package cn.ymjacky.transaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TransactionUploadManager {
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String serverUrl;
    private final int retryInterval;
    private final int maxCacheSize;

    private final Queue<TransactionRecord> offlineCache;
    private final Gson gson;

    private HttpClient httpClient;
    private ScheduledExecutorService retryExecutor;
    private ScheduledExecutorService uploadExecutor;
    private volatile boolean isConnected;
    private volatile boolean isShuttingDown;

    public TransactionUploadManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("transaction_upload_enabled", false);
        this.serverUrl = plugin.getConfig().getString("transaction_upload_url", "http://localhost:8080/transactions");
        this.retryInterval = plugin.getConfig().getInt("transaction_upload_retry_interval", 10);
        this.maxCacheSize = plugin.getConfig().getInt("transaction_cache_max_size", 1000);

        this.offlineCache = new ConcurrentLinkedQueue<>();
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
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }}, null);

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
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
                uploadCachedRecords();
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
        if (!isConnected || offlineCache.isEmpty()) {
            return;
        }

        List<TransactionRecord> batch = new ArrayList<>();
        while (!offlineCache.isEmpty() && batch.size() < 100) {
            TransactionRecord record = offlineCache.poll();
            if (record != null) {
                batch.add(record);
            }
        }

        if (!batch.isEmpty()) {
            uploadBatch(batch);
        }
    }

    private void uploadBatch(List<TransactionRecord> batch) {
        try {
            String json = gson.toJson(batch);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .header("Connection", "keep-alive")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                plugin.getLogger().info("成功上传 " + batch.size() + " 条交易记录");
            } else {
                plugin.getLogger().warning("上传交易记录失败，状态码: " + response.statusCode());
                offlineCache.addAll(batch);
                isConnected = false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "上传交易记录失败", e);
            offlineCache.addAll(batch);
            isConnected = false;
        }
    }

    public void addTransaction(TransactionRecord record) {
        if (!enabled) {
            return;
        }

        if (offlineCache.size() >= maxCacheSize) {
            plugin.getLogger().warning("交易记录缓存已满，丢弃最旧的记录");
            offlineCache.poll();
        }

        offlineCache.add(record);
    }

    private void uploadCachedRecords() {
        plugin.getLogger().info("开始上传缓存的交易记录，共 " + offlineCache.size() + " 条");
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