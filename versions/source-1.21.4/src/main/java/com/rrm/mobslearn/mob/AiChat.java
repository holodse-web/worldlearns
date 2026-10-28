package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rrm.mobslearn.MobsLearn;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class AiChat {
    private AiChat() {
    }

    public static boolean enabled;
    public static String url = "https://api.groq.com/openai/v1/chat/completions";
    public static String model = "llama-3.1-8b-instant";
    public static String key = "";
    public static String lastError = "";
    private static boolean loaded;
    private static HttpClient client;
    private static final Pattern MOOD = Pattern.compile("\\[\\s*mood\\s*:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mobslearn-ai.json");
    }

    public static void load() {
        loaded = true;
        try {
            if (!Files.exists(file())) {
                save();
                return;
            }
            JsonObject j = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            enabled = j.has("enabled") && j.get("enabled").getAsBoolean();
            if (j.has("url")) url = j.get("url").getAsString();
            if (j.has("model")) model = j.get("model").getAsString();
            if (j.has("key")) key = j.get("key").getAsString();
        } catch (Exception e) {
            MobsLearn.LOG.warn("[MobsLearn] Failed to read AI config", e);
        }
    }

    public static void save() {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("enabled", enabled);
            j.addProperty("url", url);
            j.addProperty("model", model);
            j.addProperty("key", key);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), j.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobsLearn.LOG.warn("[MobsLearn] Failed to save AI config", e);
        }
    }

    public static boolean local() {
        return url.contains("://localhost") || url.contains("://127.0.0.1");
    }

    public static boolean ready() {
        if (!loaded) load();
        return enabled && !url.isBlank() && !model.isBlank() && (!key.isBlank() || local());
    }

    public static void preset(String name) {
        switch (name) {
            case "groq" -> {
                url = "https://api.groq.com/openai/v1/chat/completions";
                model = "llama-3.1-8b-instant";
            }
            case "openrouter" -> {
                url = "https://openrouter.ai/api/v1/chat/completions";
                model = "meta-llama/llama-3.3-70b-instruct:free";
            }
            case "ollama" -> {
                url = "http://localhost:11434/v1/chat/completions";
                model = "llama3.2";
            }
            default -> {
            }
        }
        save();
    }

    public static int moodOf(String reply) {
        Matcher m = MOOD.matcher(reply);
        int v = 0;
        while (m.find()) {
            try {
                v = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(-3, Math.min(3, v));
    }

    public static String clean(String reply) {
        String s = MOOD.matcher(reply).replaceAll("");
        s = s.replace('§', ' ').replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (s.length() > 1 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("«") && s.endsWith("»")))) s = s.substring(1, s.length() - 1).trim();
        if (s.length() > 220) s = s.substring(0, 217) + "...";
        return s;
    }

    public static void ask(MinecraftServer server, String system, List<String[]> history, String text, Consumer<String> ok, Runnable fail) {
        try {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 140);
            body.addProperty("temperature", 0.9);
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", system);
            messages.add(sys);
            for (String[] h : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", h[0]);
                m.addProperty("content", h[1]);
                messages.add(m);
            }
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", text.length() > 300 ? text.substring(0, 300) : text);
            messages.add(user);
            body.add("messages", messages);
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            if (!key.isBlank()) req.header("Authorization", "Bearer " + key);
            client.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((resp, err) -> {
                String reply = null;
                if (err != null) {
                    lastError = err.getClass().getSimpleName() + ": " + err.getMessage();
                } else if (resp.statusCode() / 100 != 2) {
                    lastError = "HTTP " + resp.statusCode() + ": " + (resp.body().length() > 200 ? resp.body().substring(0, 200) : resp.body());
                } else {
                    try {
                        JsonObject j = JsonParser.parseString(resp.body()).getAsJsonObject();
                        reply = j.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                        lastError = "";
                    } catch (Exception e) {
                        lastError = "Bad response: " + e.getMessage();
                    }
                }
                String result = reply;
                server.execute(() -> {
                    if (result == null || result.isBlank()) fail.run();
                    else ok.accept(result);
                });
            });
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            fail.run();
        }
    }
}
