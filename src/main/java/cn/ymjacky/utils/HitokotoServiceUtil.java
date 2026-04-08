package cn.ymjacky.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.Plugin;

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
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HitokotoServiceUtil {
    private static final Logger LOGGER = Logger.getLogger(HitokotoServiceUtil.class.getName());

    private static final String API_BASE_URL = "https://v1.hitokoto.cn/?c=";
    private static final Map<String, String> TYPE_MAP = Map.of(
            "i", "诗词",
            "j", "歌曲"
    );
    private static final String[] TYPES = TYPE_MAP.keySet().toArray(new String[0]);

    private static final int CACHE_SIZE = 10;
    private static final long DEFAULT_UPDATE_INTERVAL_MS = 20 * 60 * 1000;
    private static final long REQUEST_INTERVAL_MS = 1500;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 2;

    private static final String UNKNOWN_SOURCE = "未知出处";
    private static final String UNKNOWN_AUTHOR = "未知作者";
    private static final String[] UNKNOWN_AUTHOR_PHRASES = {"佚名", "未知作者", "作者不详"};

    private static final String EMERGENCY_FALLBACK = "「黑夜无论怎样悠长，白昼总会到来」— 诗词《麦克白》· 威廉·莎士比亚";

    private static final Deque<String> QUOTE_POOL = new ArrayDeque<>(CACHE_SIZE);
    private static final AtomicBoolean IS_UPDATING = new AtomicBoolean(false);
    private static volatile boolean INITIALIZED = false;

    private static volatile IntSupplier ONLINE_PLAYER_SUPPLIER = () -> 0;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    public static void init(IntSupplier onlinePlayerSupplier) {
        ONLINE_PLAYER_SUPPLIER = onlinePlayerSupplier;
    }

    public static void startUpdateTask(Plugin plugin) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, _ -> {
            try {
                fillPoolToCapacity();
                INITIALIZED = true;
                LOGGER.info("一言缓存池初始化完成，当前缓存数量: " + QUOTE_POOL.size());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "初始缓存填充失败，将依赖后续定时更新", e);
            } finally {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> scheduleNextUpdate(plugin));
            }
        });
    }

    public static Component getHitokoto() {
        String rawQuote;
        synchronized (QUOTE_POOL) {
            if (!QUOTE_POOL.isEmpty()) {
                rawQuote = QUOTE_POOL.pollFirst();
                QUOTE_POOL.offerLast(rawQuote);
            } else {
                rawQuote = null;
            }
        }

        if (rawQuote != null) {
            return formatToComponent(rawQuote);
        }

        if (!INITIALIZED) {
            return Component.text("正在加载一言，请稍候...", NamedTextColor.GRAY);
        }

        triggerEmergencyRefill();

        return formatToComponent(EMERGENCY_FALLBACK);
    }

    public static CompletableFuture<Component> getHitokotoAsync() {
        return CompletableFuture.completedFuture(getHitokoto());
    }

    private static void scheduleNextUpdate(Plugin plugin) {
        int onlinePlayers = ONLINE_PLAYER_SUPPLIER.getAsInt();
        long intervalMs = calculateUpdateInterval(onlinePlayers);
        long ticks = intervalMs / 50;
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                _ -> runUpdateTask(plugin), ticks);
    }

    private static void runUpdateTask(Plugin plugin) {
        if (!IS_UPDATING.compareAndSet(false, true)) {
            return;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, _ -> {
            try {
                performScheduledUpdate();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "定时更新缓存时发生异常", e);
            } finally {
                IS_UPDATING.set(false);
                plugin.getServer().getGlobalRegionScheduler().run(plugin,
                        _ -> scheduleNextUpdate(plugin));
            }
        });
    }

    private static void performScheduledUpdate() {
        LOGGER.info("开始定时补充一言缓存池...");
        fillPoolToCapacity();
        LOGGER.info("缓存池补充完成，当前数量: " + QUOTE_POOL.size());
    }

    private static void fillPoolToCapacity() {
        int needed;
        synchronized (QUOTE_POOL) {
            needed = CACHE_SIZE - QUOTE_POOL.size();
        }

        if (needed <= 0) {
            return;
        }

        List<String> newQuotes = new ArrayList<>(needed);
        for (int i = 0; i < needed; i++) {
            try {
                String quote = fetchSingleHitokoto();
                if (quote != null && !quote.isEmpty()) {
                    newQuotes.add(quote);
                }

                if (i < needed - 1) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("缓存填充被中断");
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "获取单条一言失败", e);
            }
        }

        if (!newQuotes.isEmpty()) {
            synchronized (QUOTE_POOL) {
                QUOTE_POOL.addAll(newQuotes);
                while (QUOTE_POOL.size() > CACHE_SIZE) {
                    QUOTE_POOL.pollFirst();
                }
            }
        }
    }

    private static void triggerEmergencyRefill() {
        if (IS_UPDATING.get()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            if (!IS_UPDATING.compareAndSet(false, true)) {
                return;
            }
            try {
                String quote = fetchSingleHitokoto();
                if (quote != null && !quote.isEmpty()) {
                    synchronized (QUOTE_POOL) {
                        QUOTE_POOL.offerLast(quote);
                        while (QUOTE_POOL.size() > CACHE_SIZE) {
                            QUOTE_POOL.pollFirst();
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "紧急补充一言失败", e);
            } finally {
                IS_UPDATING.set(false);
            }
        });
    }

    private static String fetchSingleHitokoto() {
        int retries = 0;
        while (retries <= MAX_RETRIES) {
            try {
                String type = TYPES[ThreadLocalRandom.current().nextInt(TYPES.length)];
                String apiUrl = API_BASE_URL + type;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (compatible; HitokotoPlugin/2.0)")
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
                        String formatted = formatResult(json);
                        if (formatted != null) {
                            return formatted;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (JsonSyntaxException e) {
                LOGGER.fine("JSON 解析失败");
            } catch (Exception _) {
            }

            retries++;
            if (retries <= MAX_RETRIES) {
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
        if (json == null || !json.has("hitokoto")) {
            return null;
        }

        try {
            String hitokoto = json.get("hitokoto").getAsString();
            if (hitokoto == null || hitokoto.isBlank()) {
                return null;
            }

            String type = json.has("type") && !json.get("type").isJsonNull()
                    ? json.get("type").getAsString()
                    : "unknown";
            String typeName = TYPE_MAP.getOrDefault(type, "未知类型");

            String from = UNKNOWN_SOURCE;
            if (json.has("from") && !json.get("from").isJsonNull()) {
                String fromTemp = json.get("from").getAsString();
                if (fromTemp != null && !fromTemp.isBlank()) {
                    from = fromTemp;
                }
            }

            String author = UNKNOWN_AUTHOR;
            if (json.has("from_who") && !json.get("from_who").isJsonNull()) {
                String authorTemp = json.get("from_who").getAsString();
                if (authorTemp != null && !authorTemp.isBlank()) {
                    author = authorTemp;
                }
            }

            return hitokoto + "|" + typeName + "|" + from + "|" + author;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "格式化一言时发生异常", e);
            return null;
        }
    }

    private static Component formatToComponent(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4) {
            return Component.text(raw);
        }

        String hitokoto = parts[0];
        String type = parts[1];
        String from = parts[2];
        String author = parts[3];

        TextComponent.Builder builder = Component.text()
                .append(Component.text("「", NamedTextColor.GOLD))
                .append(Component.text(hitokoto, NamedTextColor.WHITE))
                .append(Component.text("」", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("—— ", NamedTextColor.DARK_GRAY));

        if (!"未知类型".equals(type)) {
            builder.append(Component.text(type, NamedTextColor.YELLOW, TextDecoration.ITALIC));
        }

        if (!UNKNOWN_SOURCE.equals(from)) {
            builder.append(Component.text("《" + from + "》", NamedTextColor.GREEN));
        }

        builder.append(Component.text(" · ", NamedTextColor.DARK_GRAY));

        if (!UNKNOWN_AUTHOR.equals(author)) {
            builder.append(Component.text(author, NamedTextColor.AQUA));
        } else {
            String randomAuthor = UNKNOWN_AUTHOR_PHRASES[ThreadLocalRandom.current().nextInt(UNKNOWN_AUTHOR_PHRASES.length)];
            builder.append(Component.text(randomAuthor, NamedTextColor.GRAY, TextDecoration.ITALIC));
        }

        return builder.build();
    }

    private static long calculateUpdateInterval(int onlinePlayers) {
        if (onlinePlayers == 0) {
            return 8 * 60 * 60 * 1000L;
        } else if (onlinePlayers == 1) {
            return 4 * 60 * 60 * 1000L;
        } else if (onlinePlayers <= 5) {
            return 60 * 60 * 1000L;
        } else if (onlinePlayers <= 15) {
            return 30 * 60 * 1000L;
        } else {
            return DEFAULT_UPDATE_INTERVAL_MS;
        }
    }

    private HitokotoServiceUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
}