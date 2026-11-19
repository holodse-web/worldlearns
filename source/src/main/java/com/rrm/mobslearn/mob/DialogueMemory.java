package com.rrm.mobslearn.mob;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rrm.mobslearn.MobsLearn;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class DialogueMemory {
    private static final int MAX_CONVERSATIONS = 4096, MAX_EXCHANGES = 4, MAX_MESSAGE = 300;
    private static final Gson GSON = new Gson();
    private static final Map<Pair, Conversation> conversations = new LinkedHashMap<>(16, 0.75f, true);
    private static MinecraftServer server;
    private static boolean dirty;

    record Pair(UUID mob, UUID player) {}
    record Exchange(String message, Component reply, boolean ai) {}

    private static final class Conversation {
        int mood;
        final List<Exchange> exchanges = new ArrayList<>();

        void add(Exchange exchange) {
            if (exchanges.size() == MAX_EXCHANGES) exchanges.remove(0);
            exchanges.add(exchange);
        }
    }

    private DialogueMemory() {}

    public static void start(MinecraftServer world) {
        server = world;
        dirty = false;
        conversations.clear();
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) return;
            JsonArray entries = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement entry : entries) {
                try {
                    JsonObject object = entry.getAsJsonObject();
                    Pair pair = new Pair(UUID.fromString(object.get("mob").getAsString()), UUID.fromString(object.get("player").getAsString()));
                    Conversation conversation = new Conversation();
                    conversation.mood = Math.max(-10, Math.min(10, object.get("mood").getAsInt()));
                    for (JsonElement value : object.getAsJsonArray("exchanges")) {
                        JsonObject exchange = value.getAsJsonObject();
                        String message = limit(exchange.get("message").getAsString());
                        boolean ai = exchange.get("ai").getAsBoolean();
                        decodeReply(exchange.get("reply"))
                            .ifPresent(reply -> conversation.add(new Exchange(message, reply, ai)));
                    }
                    conversations.put(pair, conversation);
                    trim();
                } catch (RuntimeException ignored) {
                }
            }
        } catch (Exception e) {
            MobsLearn.LOG.warn("Can't read dialogue memory", e);
        }
    }

    public static void stop() {
        save();
        server = null;
        dirty = false;
        conversations.clear();
    }

    public static void save() {
        if (server == null || !dirty) return;
        Path path = file(), temp = path.resolveSibling("mobslearn_dialogues.json.tmp");
        try {
            JsonArray entries = new JsonArray();
            for (var entry : conversations.entrySet()) {
                JsonObject object = new JsonObject();
                object.addProperty("mob", entry.getKey().mob().toString());
                object.addProperty("player", entry.getKey().player().toString());
                object.addProperty("mood", entry.getValue().mood);
                JsonArray exchanges = new JsonArray();
                for (Exchange exchange : entry.getValue().exchanges) {
                    encodeReply(exchange.reply()).ifPresent(reply -> {
                        JsonObject value = new JsonObject();
                        value.addProperty("message", exchange.message());
                        value.addProperty("ai", exchange.ai());
                        value.add("reply", reply);
                        exchanges.add(value);
                    });
                }
                object.add("exchanges", exchanges);
                entries.add(object);
            }
            Files.writeString(temp, GSON.toJson(entries), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (Exception e) {
            MobsLearn.LOG.warn("Can't save dialogue memory", e);
        }
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("mobslearn_dialogues.json");
    }

    private static Optional<Component> decodeReply(JsonElement reply) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, reply).result();
    }

    private static Optional<JsonElement> encodeReply(Component reply) {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, reply).result();
    }

    private static void trim() {
        if (conversations.size() <= MAX_CONVERSATIONS) return;
        var entries = conversations.keySet().iterator();
        entries.next();
        entries.remove();
    }

    static int mood(Pair pair) {
        Conversation conversation = conversations.get(pair);
        return conversation == null ? 0 : conversation.mood;
    }

    static int shiftedMood(Pair pair, int delta) {
        return Math.max(-10, Math.min(10, mood(pair) + delta));
    }

    static void remember(Pair pair, int mood, String text, Component reply, boolean ai) {
        Conversation conversation = conversations.computeIfAbsent(pair, ignored -> new Conversation());
        conversation.mood = mood;
        conversation.add(new Exchange(limit(text), reply.copy(), ai));
        trim();
        dirty = true;
    }

    static Exchange last(Pair pair) {
        Conversation conversation = conversations.get(pair);
        if (conversation == null || conversation.exchanges.isEmpty()) return null;
        return conversation.exchanges.get(conversation.exchanges.size() - 1);
    }

    static List<String[]> aiHistory(Pair pair) {
        Conversation conversation = conversations.get(pair);
        if (conversation == null) return List.of();
        List<String[]> history = new ArrayList<>();
        for (Exchange exchange : conversation.exchanges) {
            if (!exchange.ai()) continue;
            history.add(new String[]{"user", exchange.message()});
            history.add(new String[]{"assistant", exchange.reply().getString()});
        }
        return history;
    }

    private static String limit(String text) {
        if (text.length() <= MAX_MESSAGE) return text;
        int end = Character.isHighSurrogate(text.charAt(MAX_MESSAGE - 1)) ? MAX_MESSAGE - 1 : MAX_MESSAGE;
        return text.substring(0, end);
    }
}
