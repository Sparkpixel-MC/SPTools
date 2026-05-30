package cn.ymjacky.utils;

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
    private static final int BATCH_REQUEST_COUNT = 6;
    private static final long REQUEST_INTERVAL_MS = 1500;
    private static final int LOW_CACHE_THRESHOLD = 2;

    private static final String UNKNOWN_SOURCE = "未知出处";
    private static final String UNKNOWN_AUTHOR = "未知作者";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final ConcurrentLinkedDeque<String> QUOTE_CACHE = new ConcurrentLinkedDeque<>();
    private static final AtomicReference<String> CURRENT_QUOTE = new AtomicReference<>("");
    private static final AtomicBoolean IS_UPDATING = new AtomicBoolean(false);
    private static final AtomicBoolean IS_SUPPLEMENT_RUNNING = new AtomicBoolean(false); // 防止重复触发补充

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String[] TYPES = TYPE_MAP.keySet().toArray(new String[0]);

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

    public static CompletableFuture<String> getHitokotoAsync() {
        return CompletableFuture.supplyAsync(HitokotoServiceUtil::getHitokoto);
    }

    public static void startUpdateTask(Plugin plugin) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                _ -> performScheduledUpdate(plugin),
                0L,
                6L,
                TimeUnit.HOURS
        ));
    }
    private static void performScheduledUpdate(Plugin plugin) {
        if (!IS_UPDATING.compareAndSet(false, true)) {
            return;
        }

        try {
            updateCacheBatchAsync();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "定时更新缓存时发生异常", e);
        } finally {
            IS_UPDATING.set(false);
        }

        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> plugin.getServer().getAsyncScheduler().runDelayed(
                plugin,
                _ -> performScheduledUpdate(plugin),
                6L,
                TimeUnit.HOURS
        ));
    }

    private static void triggerSupplementIfNeeded() {
        if (QUOTE_CACHE.size() > LOW_CACHE_THRESHOLD) {
            return;
        }
        if (IS_SUPPLEMENT_RUNNING.compareAndSet(false, true)) {
            Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("SPToolsPlugin");
            if (plugin != null) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, _ -> {
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
            LOGGER.fine(() -> "补充了 " + newQuotes.size() + " 条新一言，当前缓存大小: " + QUOTE_CACHE.size());
        }
    }

    private static void updateCacheBatchAsync() {
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
            QUOTE_CACHE.clear();
            QUOTE_CACHE.addAll(newQuotes);
            String selected = newQuotes.get(ThreadLocalRandom.current().nextInt(newQuotes.size()));
            CURRENT_QUOTE.set(selected);
            LOGGER.info("成功缓存 " + newQuotes.size() + " 条新一言");
        } else {
            LOGGER.warning("批量更新未能获取任何新一言");
        }
    }

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
        sb.append("「").append(hitokoto).append("」\n—— ");

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