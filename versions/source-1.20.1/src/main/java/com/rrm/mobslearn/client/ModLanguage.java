package com.rrm.mobslearn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rrm.mobslearn.MobsLearn;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;

public final class ModLanguage {
    public record Choice(String code, String name, String shortName) {}

    public static final String AUTO = "auto";
    public static final List<Choice> CHOICES = List.of(
        new Choice("en_us", "English", "EN"),
        new Choice("ru_ru", "Русский", "RU"),
        new Choice("uk_ua", "Українська", "UK"),
        new Choice("de_de", "Deutsch", "DE"),
        new Choice("fr_fr", "Français", "FR"),
        new Choice("es_es", "Español", "ES"));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean initialized;
    private static String selected = AUTO;
    private static String minecraftCode = "en_us";

    private ModLanguage() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        Path file = configPath();
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.has("language")) selected = validate(object.get("language").getAsString());
        } catch (Exception e) {
            MobsLearn.LOG.warn("Could not read the local World Learns language; using Minecraft's language", e);
        }
    }

    public static String selected() {
        initialize();
        return selected;
    }

    public static Choice current() {
        String code = selected().equals(AUTO) ? match(minecraftCode) : selected;
        return CHOICES.stream().filter(c -> c.code.equals(code)).findFirst().orElse(CHOICES.get(0));
    }

    public static Choice minecraftChoice() {
        String code = match(minecraftCode);
        return CHOICES.stream().filter(c -> c.code.equals(code)).findFirst().orElse(CHOICES.get(0));
    }

    public static void onResourceReload(ResourceManager resources, String vanillaCode) {
        initialize();
        minecraftCode = vanillaCode == null ? "en_us" : vanillaCode;
        Language base = Language.getInstance();
        while (base instanceof Overlay overlay) base = overlay.base;
        Map<String, String> translations = new HashMap<>();
        load(resources, "en_us", translations);
        String code = current().code;
        if (!code.equals("en_us")) load(resources, code, translations);
        Language.inject(new Overlay(base, Map.copyOf(translations)));
    }

    public static boolean select(String code) {
        initialize();
        selected = validate(code);
        Minecraft mc = Minecraft.getInstance();
        onResourceReload(mc.getResourceManager(), mc.getLanguageManager().getSelected());
        Path file = configPath(), temporary = null;
        try {
            Files.createDirectories(file.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("language", selected);
            temporary = Files.createTempFile(file.getParent(), "mobslearn-client-", ".tmp");
            Files.writeString(temporary, GSON.toJson(object) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            MobsLearn.LOG.warn("World Learns language applies this session, but the local preference could not be saved", e);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("mobslearn-client.json");
    }

    private static String validate(String code) {
        if (code != null) {
            String normalized = code.toLowerCase(Locale.ROOT);
            if (normalized.equals(AUTO) || CHOICES.stream().anyMatch(c -> c.code.equals(normalized))) return normalized;
        }
        return AUTO;
    }

    private static String match(String code) {
        String language = code.toLowerCase(Locale.ROOT).replace('-', '_').split("_", 2)[0];
        return switch (language) {
            case "ru" -> "ru_ru";
            case "uk" -> "uk_ua";
            case "de" -> "de_de";
            case "fr" -> "fr_fr";
            case "es" -> "es_es";
            default -> "en_us";
        };
    }

    private static boolean owns(String key) {
        return key.startsWith("mobslearn.") || key.startsWith("key.mobslearn.")
            || key.startsWith("key.category.mobslearn.") || key.startsWith("gamerule.mobslearn.");
    }

    private static void load(ResourceManager manager, String code, Map<String, String> target) {
        String path = "lang/" + code + ".json";
        try (InputStream input = ModLanguage.class.getResourceAsStream("/assets/mobslearn/" + path)) {
            if (input != null) Language.loadFromJson(input, (key, value) -> { if (owns(key)) target.put(key, value); });
        } catch (Exception e) {
            MobsLearn.LOG.warn("Could not read bundled World Learns translations for {}", code, e);
        }
        for (Resource resource : manager.getResourceStack(MobsLearn.id(path))) {
            try (InputStream input = resource.open()) {
                Language.loadFromJson(input, (key, value) -> { if (owns(key)) target.put(key, value); });
            } catch (Exception e) {
                MobsLearn.LOG.warn("Could not read World Learns translations for {} from {}", code, resource.sourcePackId(), e);
            }
        }
    }

    private static final class Overlay extends Language {
        private final Language base;
        private final Map<String, String> translations;

        private Overlay(Language base, Map<String, String> translations) {
            this.base = base;
            this.translations = translations;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            String value = owns(key) ? translations.get(key) : null;
            return value == null ? base.getOrDefault(key, fallback) : value;
        }

        @Override
        public boolean has(String key) {
            return (owns(key) && translations.containsKey(key)) || base.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return base.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return base.getVisualOrder(text);
        }
    }
}
