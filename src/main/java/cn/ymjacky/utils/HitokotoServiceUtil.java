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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HitokotoServiceUtil {
    private static final Logger LOGGER = Logger.getLogger(HitokotoServiceUtil.class.getName());

    private static final String API_BASE_URL = "https://v1.hitokoto.cn/?c=";
    private static final Map<String, String> TYPE_MAP = Map.of(
            "i", "诗词",
            "j", "歌曲"
    );

    private static final String UNKNOWN_SOURCE = "未知出处";
    private static final String UNKNOWN_AUTHOR = "未知作者";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final List<String> FALLBACK_QUOTES = List.of(
            "「博观而约取，厚积而薄发」\n\n—— 诗词《稼说送张琥》· 苏轼",
            "「长风破浪会有时，直挂云帆济沧海」\n\n—— 诗词《行路难》· 李白",
            "「黑夜无论怎样悠长，白昼总会到来」\n\n—— 诗词《麦克白》· 威廉·莎士比亚",
            "「岁月不居，时节如流」\n\n—— 诗词《与吴质书》· 孔融",
            "「星光不问赶路人，时光不负有心人」\n\n—— 格言，作者不详"
    );

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String[] TYPES = TYPE_MAP.keySet().toArray(new String[0]);

    public static CompletableFuture<String> getHitokotoAsync() {
        return CompletableFuture.supplyAsync(HitokotoServiceUtil::getHitokotoSync)
                .exceptionally(e -> {
                    LOGGER.log(Level.WARNING, "Failed to fetch hitokoto asynchronously", e);
                    return getFallbackHitokoto();
                });
    }

    private static String getHitokotoSync() {
        int maxRetries = 2;
        int retryCount = 0;
        while (retryCount <= maxRetries) {
            try {
                String type = getRandomType();
                String apiUrl = API_BASE_URL + type;

                LOGGER.fine("Requesting API: " + apiUrl + " (Attempt " + (retryCount + 1) + ")");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                LOGGER.fine("API response status code: " + response.statusCode());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    LOGGER.fine("API response content: " + responseBody);

                    if (responseBody != null && !responseBody.trim().isEmpty()) {
                        try {
                            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                            String result = formatResult(json);

                            if (result != null && !result.trim().isEmpty()) {
                                return result;
                            } else {
                                LOGGER.warning("Formatted result is empty, using fallback quote");
                            }
                        } catch (JsonSyntaxException e) {
                            LOGGER.warning("JSON parsing failed: " + e.getMessage() + " Response content: " + responseBody);
                        }
                    } else {
                        LOGGER.warning("API returned empty response body");
                    }
                } else {
                    LOGGER.warning("API returned non-200 status code: " + response.statusCode());
                }

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception occurred while fetching hitokoto (Attempt " + (retryCount + 1) + ")", e);
            }

            retryCount++;
            if (retryCount <= maxRetries) {
                try {
                    long waitTime = (long) (Math.pow(2, retryCount) * 500);
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOGGER.info("All retries failed, using fallback quote");
        return getFallbackHitokoto();
    }

    private static String formatResult(JsonObject json) {
        if (json == null) {
            LOGGER.warning("JSON object is null");
            return null;
        }
        try {
            if (!json.has("hitokoto")) {
                LOGGER.warning("JSON missing hitokoto field");
                return null;
            }
            String hitokoto;
            try {
                hitokoto = json.get("hitokoto").getAsString();
            } catch (Exception e) {
                LOGGER.warning("Failed to parse hitokoto field: " + e.getMessage());
                return null;
            }
            if (hitokoto == null || hitokoto.trim().isEmpty()) {
                LOGGER.warning("Hitokoto content is empty");
                return null;
            }
            String type = "unknown";
            if (json.has("type") && !json.get("type").isJsonNull()) {
                try {
                    type = json.get("type").getAsString();
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse type field: " + e.getMessage());
                    type = "unknown";
                }
            }
            String typeName = TYPE_MAP.getOrDefault(type, "未知类型");
            String from = UNKNOWN_SOURCE;
            if (json.has("from") && !json.get("from").isJsonNull()) {
                try {
                    String fromTemp = json.get("from").getAsString();
                    if (fromTemp != null && !fromTemp.trim().isEmpty()) {
                        from = fromTemp;
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse from field: " + e.getMessage());
                }
            }
            String fromWho = UNKNOWN_AUTHOR;
            if (json.has("from_who") && !json.get("from_who").isJsonNull()) {
                try {
                    String authorTemp = json.get("from_who").getAsString();
                    if (authorTemp != null && !authorTemp.trim().isEmpty()) {
                        fromWho = authorTemp;
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse from_who field: " + e.getMessage());
                }
            }
            return formatBeautifulString(hitokoto, typeName, from, fromWho);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception occurred while formatting result", e);
            return null;
        }
    }

    private static String formatBeautifulString(String hitokoto, String type, String from, String author) {
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(hitokoto).append("」");
        sb.append("\n\n");
        sb.append("—— 出自");
        if (type.equals("诗词") || type.equals("歌曲")) {
            sb.append(type);
        } else if (!type.equals("未知类型")) {
            sb.append("「").append(type).append("」");
        }
        if (!UNKNOWN_SOURCE.equals(from)) {
            sb.append("《").append(from).append("》");
        }
        if (!UNKNOWN_AUTHOR.equals(author)) {
            sb.append("，作者：").append(author);
        } else {
            String[] unknownAuthorPhrases = {
                    "，作者佚名",
                    "，著者未详",
                    "，不知何人所著",
                    "，无署名作者"
            };
            int idx = ThreadLocalRandom.current().nextInt(unknownAuthorPhrases.length);
            sb.append(unknownAuthorPhrases[idx]);
        }
        return sb.toString();
    }

    private static String getRandomType() {
        int index = ThreadLocalRandom.current().nextInt(TYPES.length);
        return TYPES[index];
    }

    private static String getFallbackHitokoto() {
        int index = ThreadLocalRandom.current().nextInt(FALLBACK_QUOTES.size());
        return FALLBACK_QUOTES.get(index);
    }

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        for (int i = 0; i < 5; i++) {
            System.out.println("\n=== Test " + (i + 1) + " ===");
            String result = getHitokotoSync();
            System.out.println(Objects.requireNonNullElse(result, "Failed to fetch"));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}