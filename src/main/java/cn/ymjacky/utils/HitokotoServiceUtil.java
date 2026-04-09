package cn.ymjacky.utils;

import cn.ymjacky.SPToolsPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HitokotoServiceUtil {
    private static final Logger LOGGER = Logger.getLogger("SPTools");

    private static final String API_BASE_URL = "https://v1.hitokoto.cn/?c=";
    private static final Map<String, String> TYPE_MAP = Map.of(
            "i", "诗词",
            "j", "歌曲"
    );

    private static final int CACHE_SIZE = 10;
    private static final long DEFAULT_UPDATE_INTERVAL = 20 * 60 * 1000; // 20分钟
    private static final int BATCH_REQUEST_COUNT = 6;
    private static final long REQUEST_INTERVAL_MS = 1500; // 1.5秒
    private static final int LOW_CACHE_THRESHOLD = 2; // 当缓存剩余 ≤ 2 时触发补充

    private static final String UNKNOWN_SOURCE = "未知出处";
    private static final String UNKNOWN_AUTHOR = "未知作者";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // 改用线程安全的并发队列，避免手动同步，支持高效的轮转消费
    private static final ConcurrentLinkedDeque<String> QUOTE_CACHE = new ConcurrentLinkedDeque<>();
    private static final AtomicReference<String> CURRENT_QUOTE = new AtomicReference<>("");
    private static final AtomicBoolean IS_UPDATING = new AtomicBoolean(false);
    private static final AtomicBoolean IS_SUPPLEMENT_RUNNING = new AtomicBoolean(false); // 防止重复触发补充

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
        String quote = QUOTE_CACHE.pollFirst();
        if (quote != null) {
            CURRENT_QUOTE.set(quote);
            triggerSupplementIfNeeded();
            return quote;
        }
        String lastKnown = CURRENT_QUOTE.get();
        if (lastKnown != null && !lastKnown.isEmpty()) {
            triggerSupplementIfNeeded();
            return lastKnown;
        }
        triggerSupplementIfNeeded();
        return "「黑夜无论怎样悠长，白昼总会到来」— 诗词《麦克白》· 威廉·莎士比亚";
    }

    /**
     * 异步获取一言（真正的异步版本，不会阻塞调用线程）。
     */
    public static CompletableFuture<String> getHitokotoAsync() {
        return CompletableFuture.supplyAsync(HitokotoServiceUtil::getHitokoto);
    }

    public static void startUpdateTask(Plugin plugin) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> {
            plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    _ -> performScheduledUpdate(plugin),
                    0L,
                    6L,
                    TimeUnit.HOURS
            );
        });
    }
    private static void performScheduledUpdate(Plugin plugin) {
        if (!IS_UPDATING.compareAndSet(false, true)) {
            return;
        }

        try {
            int onlinePlayers = ONLINE_PLAYER_SUPPLIER.getAsInt();
            updateCacheBatchAsync(onlinePlayers);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "定时更新缓存时发生异常", e);
        } finally {
            IS_UPDATING.set(false);
        }

        // 计算下次执行延迟（毫秒），并重新调度（通过取消当前任务再创建新任务来实现动态间隔）
        // 由于 runAtFixedRate 固定周期，我们采用 cancel + 重新调度方式。
        // 但此处为了简化，在异步任务内自行休眠后重新提交，确保符合 Folia 规范。
        // 更简洁的做法：在任务末尾使用 GlobalRegionScheduler 计算延迟后重新提交一次。
        // 下面通过取消当前 task 并重新调度来实现动态间隔。
        // 由于 AsyncScheduler 返回的 ScheduledTask 无法在任务内部获取，我们采用递归式调度但切换为安全模式：
        // 在全局线程中计算延迟后，再次调用 runLater 来调度下一次更新。

        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> {
            int onlinePlayers = ONLINE_PLAYER_SUPPLIER.getAsInt();
            plugin.getServer().getAsyncScheduler().runDelayed(
                    plugin,
                    _ -> performScheduledUpdate(plugin),
                    6L,
                    TimeUnit.HOURS
            );
        });
    }

    /**
     * 触发异步补充缓存（当缓存低于阈值时调用，幂等操作）。
     */
    private static void triggerSupplementIfNeeded() {
        if (QUOTE_CACHE.size() > LOW_CACHE_THRESHOLD) {
            return;
        }
        // 防止多个调用同时触发大量补充任务
        if (IS_SUPPLEMENT_RUNNING.compareAndSet(false, true)) {
            Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("YourPluginName"); // 实际需传入插件实例，此处仅示意
            if (plugin != null) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    try {
                        supplementCache();
                    } finally {
                        IS_SUPPLEMENT_RUNNING.set(false);
                    }
                });
            } else {
                IS_SUPPLEMENT_RUNNING.set(false);
            }
        }
    }

    /**
     * 补充缓存（一次请求多条，直到缓存满或达到最大补充量）。
     */
    private static void supplementCache() {
        int needed = CACHE_SIZE - QUOTE_CACHE.size();
        if (needed <= 0) return;
        int toFetch = Math.min(needed, BATCH_REQUEST_COUNT);
        List<String> newQuotes = new ArrayList<>();

        for (int i = 0; i < toFetch; i++) {
            try {
                String quote = fetchSingleHitokoto();
                if (quote != null && !quote.isEmpty()) {
                    newQuotes.add(quote);
                }
                if (i < toFetch - 1) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "补充缓存获取句子失败", e);
            }
        }

        QUOTE_CACHE.addAll(newQuotes);
        if (!newQuotes.isEmpty()) {
            LOGGER.fine(() -> "补充了 " + newQuotes.size() + " 条新语录，当前缓存大小: " + QUOTE_CACHE.size());
        }
    }

    /**
     * 批量更新缓存（用于定时任务，会清空旧缓存并填充新数据）。
     */
    private static void updateCacheBatchAsync(int onlinePlayers) {
        LOGGER.info("开始批量更新一言缓存...");
        List<String> newQuotes = new ArrayList<>();

        for (int i = 0; i < BATCH_REQUEST_COUNT; i++) {
            try {
                String quote = fetchSingleHitokoto();
                if (quote != null && !quote.isEmpty()) {
                    newQuotes.add(quote);
                }
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
            // 清空旧缓存，用新数据填充
            QUOTE_CACHE.clear();
            QUOTE_CACHE.addAll(newQuotes);
            // 随机选一条作为当前记忆（用于极端情况回退）
            String selected = newQuotes.get(ThreadLocalRandom.current().nextInt(newQuotes.size()));
            CURRENT_QUOTE.set(selected);
            LOGGER.info("【已缓存语录】成功缓存 " + newQuotes.size() + " 条新语录");
        } else {
            LOGGER.warning("批量更新未能获取任何新语录");
        }
    }

    /**
     * 获取单条一言（包含重试）。
     */
    private static String fetchSingleHitokoto() {
        int maxRetries = 2;
        for (int retry = 0; retry <= maxRetries; retry++) {
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
                    String body = response.body();
                    if (body != null && !body.trim().isEmpty()) {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        return formatResult(json);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "获取一言失败 (尝试 {0}/{1})", new Object[]{retry + 1, maxRetries + 1});
            }

            if (retry < maxRetries) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    private static String formatResult(JsonObject json) {
        if (json == null) return null;
        try {
            if (!json.has("hitokoto")) return null;
            String hitokoto = json.get("hitokoto").getAsString();
            if (hitokoto == null || hitokoto.trim().isEmpty()) return null;

            String type = json.has("type") && !json.get("type").isJsonNull()
                    ? json.get("type").getAsString() : "unknown";
            String typeName = TYPE_MAP.getOrDefault(type, "未知类型");

            String from = UNKNOWN_SOURCE;
            if (json.has("from") && !json.get("from").isJsonNull()) {
                String f = json.get("from").getAsString();
                if (f != null && !f.trim().isEmpty()) from = f;
            }

            String author = UNKNOWN_AUTHOR;
            if (json.has("from_who") && !json.get("from_who").isJsonNull()) {
                String a = json.get("from_who").getAsString();
                if (a != null && !a.trim().isEmpty()) author = a;
            }

            return formatBeautifulString(hitokoto, typeName, from, author);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatBeautifulString(String hitokoto, String type, String from, String author) {
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(hitokoto).append("」\n—— "); // 单换行，去除多余空行

        if ("诗词".equals(type) || "歌曲".equals(type)) {
            sb.append(type);
        } else if (!"未知类型".equals(type)) {
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