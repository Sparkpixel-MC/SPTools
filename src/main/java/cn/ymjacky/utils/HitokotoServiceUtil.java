package cn.ymjacky.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HitokotoServiceUtil {
    private static final Logger LOGGER = Logger.getLogger(HitokotoServiceUtil.class.getName());

    private static final String API_BASE_URL = "https://v1.hitokoto.cn/?c=";
    private static final Map<String, String> TYPE_MAP = Map.of(
            "i", "诗词",
            "j", "歌曲"
    );

    private static final int CACHE_SIZE = 10;
    private static final long DEFAULT_UPDATE_INTERVAL = 20 * 60 * 1000; // 20分钟
    private static final int BATCH_REQUEST_COUNT = 6;
    private static final long REQUEST_INTERVAL_MS = 1500; // 1.5秒

    private static final String UNKNOWN_SOURCE = "未知出处";
    private static final String UNKNOWN_AUTHOR = "未知作者";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Deque<String> QUOTE_CACHE = new ArrayDeque<>(CACHE_SIZE);
    private static final AtomicReference<String> CURRENT_QUOTE = new AtomicReference<>("");
    private static final AtomicBoolean IS_UPDATING = new AtomicBoolean(false); // 线程安全更新标志

    private static volatile IntSupplier ONLINE_PLAYER_SUPPLIER = () -> 0;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String[] TYPES = TYPE_MAP.keySet().toArray(new String[0]);

    public static void init(IntSupplier onlinePlayerSupplier) {
        ONLINE_PLAYER_SUPPLIER = onlinePlayerSupplier;
    }

    public static String getHitokoto() {
        String cached = CURRENT_QUOTE.get();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        synchronized (QUOTE_CACHE) {
            if (!QUOTE_CACHE.isEmpty()) {
                String quote = QUOTE_CACHE.peekLast();
                if (quote != null) {
                    CURRENT_QUOTE.set(quote);
                    return quote;
                }
            }
        }
        return "「黑夜无论怎样悠长，白昼总会到来」— 诗词《麦克白》· 威廉·莎士比亚";
    }

    public static CompletableFuture<String> getHitokotoAsync() {
        return CompletableFuture.completedFuture(getHitokoto());
    }

    /**
     * 启动定时更新任务（在插件 onEnable 中调用）
     * @param plugin 插件实例
     */
    public static void startUpdateTask(org.bukkit.plugin.Plugin plugin) {
        // 首次调度在全局线程中执行
        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> scheduleNextUpdate(plugin));
    }

    /**
     * 调度下一次更新（必须在全局区域线程调用）
     */
    private static void scheduleNextUpdate(org.bukkit.plugin.Plugin plugin) {
        int onlinePlayers = ONLINE_PLAYER_SUPPLIER.getAsInt(); // 全局线程安全获取
        long interval = calculateUpdateInterval(onlinePlayers);
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                _ -> runUpdateTask(plugin), interval / 50); // 转换为 tick
    }

    /**
     * 启动更新任务（在全局线程调用，内部切到异步线程）
     */
    private static void runUpdateTask(org.bukkit.plugin.Plugin plugin) {
        // 确保同一时间只有一个更新任务在执行
        if (!IS_UPDATING.compareAndSet(false, true)) {
            return; // 已经在更新中
        }

        // 提前获取在线人数，避免在异步线程中调用 Bukkit API
        int onlinePlayers = ONLINE_PLAYER_SUPPLIER.getAsInt();

        // 切换到异步线程执行网络请求和休眠
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                updateCacheBatchAsync(onlinePlayers);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "更新缓存时发生异常", e);
            } finally {
                IS_UPDATING.set(false);
                // 异步任务完成后，切回全局线程调度下一次更新
                plugin.getServer().getGlobalRegionScheduler().run(plugin,
                        _ -> scheduleNextUpdate(plugin));
            }
        });
    }

    /**
     * 异步批量更新缓存（在异步线程执行）
     * @param onlinePlayers 当前在线人数（用于可能的日志或动态调整，此处未使用，但保留参数）
     */
    private static void updateCacheBatchAsync(int onlinePlayers) {
        LOGGER.info("开始批量更新一言缓存...");
        List<String> newQuotes = new ArrayList<>();

        for (int i = 0; i < BATCH_REQUEST_COUNT; i++) {
            try {
                String quote = fetchSingleHitokoto(); // 网络请求 + 重试（内部可能休眠）
                if (quote != null && !quote.isEmpty()) {
                    newQuotes.add(quote);
                }

                // 在异步线程中休眠是安全的
                if (i < BATCH_REQUEST_COUNT - 1) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "批量更新被中断");
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "获取一言时发生异常", e);
            }
        }

        if (!newQuotes.isEmpty()) {
            synchronized (QUOTE_CACHE) {
                QUOTE_CACHE.clear();
                QUOTE_CACHE.addAll(newQuotes);

                String selected = newQuotes.get(ThreadLocalRandom.current().nextInt(newQuotes.size()));
                CURRENT_QUOTE.set(selected);

                LOGGER.info("【已缓存语录】成功缓存 " + newQuotes.size() + " 条新语录");
            }
        } else {
            LOGGER.warning("批量更新未能获取任何新语录");
        }
    }

    /**
     * 获取单条一言（可包含重试逻辑）
     */
    private static String fetchSingleHitokoto() {
        int maxRetries = 2;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                String type = TYPES[ThreadLocalRandom.current().nextInt(TYPES.length)];
                String apiUrl = API_BASE_URL + type;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                if (response.statusCode() == 200) {
                    String responseBody = response.body();

                    if (responseBody != null && !responseBody.trim().isEmpty()) {
                        try {
                            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                            return formatResult(json);
                        } catch (JsonSyntaxException e) {
                            LOGGER.warning("JSON解析失败");
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "获取一言失败 (尝试 " + (retryCount + 1) + ")");
            }

            retryCount++;
            if (retryCount <= maxRetries) {
                try {
                    Thread.sleep(1000); // 重试等待
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return null;
    }

    /**
     * 计算更新间隔（纯逻辑，无API调用）
     */
    private static long calculateUpdateInterval(int onlinePlayers) {
        if (onlinePlayers == 0) {
            return 8 * 60 * 60 * 1000; // 8小时
        } else if (onlinePlayers == 1) {
            return 4 * 60 * 60 * 1000; // 4小时
        } else if (onlinePlayers >= 2 && onlinePlayers <= 5) {
            return 60 * 60 * 1000; // 1小时
        } else if (onlinePlayers >= 6 && onlinePlayers <= 15) {
            return 30 * 60 * 1000; // 30分钟
        } else {
            return DEFAULT_UPDATE_INTERVAL; // 20分钟
        }
    }

    private static String formatResult(JsonObject json) {
        if (json == null) {
            return null;
        }

        try {
            if (!json.has("hitokoto")) {
                return null;
            }

            String hitokoto = json.get("hitokoto").getAsString();
            if (hitokoto == null || hitokoto.trim().isEmpty()) {
                return null;
            }

            String type = "unknown";
            if (json.has("type") && !json.get("type").isJsonNull()) {
                type = json.get("type").getAsString();
            }

            String typeName = TYPE_MAP.getOrDefault(type, "未知类型");
            String from = UNKNOWN_SOURCE;
            if (json.has("from") && !json.get("from").isJsonNull()) {
                String fromTemp = json.get("from").getAsString();
                if (fromTemp != null && !fromTemp.trim().isEmpty()) {
                    from = fromTemp;
                }
            }

            String fromWho = UNKNOWN_AUTHOR;
            if (json.has("from_who") && !json.get("from_who").isJsonNull()) {
                String authorTemp = json.get("from_who").getAsString();
                if (authorTemp != null && !authorTemp.trim().isEmpty()) {
                    fromWho = authorTemp;
                }
            }

            return formatBeautifulString(hitokoto, typeName, from, fromWho);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatBeautifulString(String hitokoto, String type, String from, String author) {
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(hitokoto).append("」\n\n—— ");

        if (type.equals("诗词") || type.equals("歌曲")) {
            sb.append(type);
        } else if (!type.equals("未知类型")) {
            sb.append(type);
        }

        if (!UNKNOWN_SOURCE.equals(from)) {
            sb.append("《").append(from).append("》");
        }

        if (!UNKNOWN_AUTHOR.equals(author)) {
            sb.append(" · ").append(author);
        } else {
            String[] unknownAuthorPhrases = {"佚名", "未知作者", "作者不详"};
            sb.append(" · ").append(unknownAuthorPhrases[ThreadLocalRandom.current().nextInt(unknownAuthorPhrases.length)]);
        }

        return sb.toString();
    }
}