package cn.ymjacky.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HitokotoServiceUtil {
    private static final String API_URL = "https://uapis.cn/api/v1/saying";

    public static CompletableFuture<String> getHitokotoAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Accept", "application/json");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (json.has("text")) {
                        return json.get("text").getAsString();
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
            return null;
        });
    }

    public static String getHitokotoWithTimeout() {
        try {
            return getHitokotoAsync().get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return null;
    }
}