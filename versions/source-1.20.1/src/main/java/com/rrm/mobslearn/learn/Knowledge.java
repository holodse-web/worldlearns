package com.rrm.mobslearn.learn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Knowledge {
    private static final Gson GSON = new Gson();
    public static final int MAX_REGIONS = 512, MAX_JOURNAL = 256;
    public final Map<String, Double> points = new HashMap<>();
    public final Map<String, Map<String, Double>> regions = new LinkedHashMap<>();
    public record JournalEntry(long day, String skill, int level) {}
    public final List<JournalEntry> journal = new ArrayList<>();
    /** World chronicle: notable events (settlements founded, wars, raids, truces...). args prefixed "t:" are translation keys. */
    public record ChronicleEntry(long day, String key, List<String> args, String icon) {
        public net.minecraft.network.chat.Component text() {
            Object[] parts = new Object[args.size()];
            for (int i = 0; i < parts.length; i++) {
                String a = args.get(i);
                parts[i] = a.startsWith("t:") ? net.minecraft.network.chat.Component.translatable(a.substring(2)) : a;
            }
            return net.minecraft.network.chat.Component.translatable(key, parts);
        }
    }
    public static final int MAX_CHRONICLE = 160;
    public final List<ChronicleEntry> chronicle = new ArrayList<>();
    public double speed = 1.0;
    public boolean enabled = true, absurd = true, combat = true;
    public int difficulty = 1;
    public int warp = 1;
    public int buildLimit = 32;

    public void clear() {
        points.clear(); regions.clear(); journal.clear(); chronicle.clear();
        speed = 1.0; enabled = absurd = combat = true; difficulty = 1; buildLimit = 32;
    }

    public boolean allows(Skill s) {
        return enabled && (s == null || ((!s.absurd() || absurd)
            && (combat || !(s.combat() || (s.group().hostile && !s.absurd())))));
    }

    public double points(Skill s) { return points.getOrDefault(s.id(), 0.0); }

    public int intelligence() {
        int sum = 0, max = 0;
        for (Skill s : Skill.ALL) {
            if (s.group() == MobGroup.WORLD) continue;
            sum += s.level(points(s)); max += Skill.MAX_LEVEL;
        }
        return max == 0 ? 0 : Math.round(sum * 100f / max);
    }

    public int level(Skill s) {
        if (!allows(s)) return 0;
        return s.group() == MobGroup.WORLD ? s.level(intelligence()) : s.level(points(s));
    }

    public int level(String id) {
        Skill s = Skill.byId(id);
        return s == null ? 0 : level(s);
    }

    public int groupLevels(MobGroup g) {
        int n = 0;
        for (Skill s : Skill.of(g)) n += level(s);
        return n;
    }

    public int localLevel(Skill skill, String regionKey) {
        if (!allows(skill)) return 0;
        int global = level(skill);
        if (skill.group() == MobGroup.WORLD) return global;
        Map<String, Double> area = regions.get(regionKey);
        int local = area == null ? 0 : skill.level(area.getOrDefault(skill.id(), 0.0));
        int floor = global == 0 ? 0 : Math.max(1, global - 1);
        return Math.max(floor, Math.min(local, Math.min(Skill.MAX_LEVEL, global + 1)));
    }

    public Map<String, Double> region(String key) {
        Map<String, Double> existing = regions.get(key);
        if (existing != null) return existing;
        if (regions.size() >= MAX_REGIONS) regions.remove(regions.keySet().iterator().next());
        Map<String, Double> created = new HashMap<>();
        regions.put(key, created);
        return created;
    }

    public void record(long day, Skill skill, int level) {
        if (journal.size() >= MAX_JOURNAL) journal.remove(0);
        journal.add(new JournalEntry(Math.max(1, day), skill.id(), level));
    }

    public boolean chronicle(long day, String key, List<String> args, String icon) {
        int from = Math.max(0, chronicle.size() - 24);
        for (int i = from; i < chronicle.size(); i++) {
            ChronicleEntry e = chronicle.get(i);
            if (e.day() == day && e.key().equals(key) && e.args().equals(args)) return false;
        }
        if (chronicle.size() >= MAX_CHRONICLE) chronicle.remove(0);
        chronicle.add(new ChronicleEntry(Math.max(1, day), key, List.copyOf(args), icon == null ? "writable_book" : icon));
        return true;
    }

    public JsonObject json(boolean includeRegions) {
        JsonObject o = new JsonObject();
        o.addProperty("schema", 2);
        o.add("points", GSON.toJsonTree(points));
        o.addProperty("speed", speed); o.addProperty("enabled", enabled);
        o.addProperty("absurd", absurd); o.addProperty("combat", combat);
        o.addProperty("difficulty", difficulty);
        o.addProperty("buildLimit", buildLimit);
        JsonArray log = new JsonArray();
        for (JournalEntry entry : journal) {
            JsonObject line = new JsonObject();
            line.addProperty("day", entry.day()); line.addProperty("skill", entry.skill()); line.addProperty("level", entry.level());
            log.add(line);
        }
        o.add("journal", log);
        JsonArray chron = new JsonArray();
        for (ChronicleEntry entry : chronicle) {
            JsonObject line = new JsonObject();
            line.addProperty("day", entry.day()); line.addProperty("key", entry.key()); line.addProperty("icon", entry.icon());
            JsonArray a = new JsonArray();
            for (String arg : entry.args()) a.add(arg);
            line.add("args", a);
            chron.add(line);
        }
        o.add("chronicle", chron);
        if (includeRegions) o.add("regions", GSON.toJsonTree(regions));
        return o;
    }

    public String toJson() { return GSON.toJson(json(true)); }

    private static void readPoints(JsonObject input, Map<String, Double> target) {
        for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
            Skill skill = Skill.byId(entry.getKey());
            if (skill == null || skill.group() == MobGroup.WORLD || !entry.getValue().isJsonPrimitive()) continue;
            try {
                double value = entry.getValue().getAsDouble();
                if (Double.isFinite(value) && value > 0) target.put(skill.id(), Math.min(value, skill.thresholds()[2]));
            } catch (RuntimeException ignored) {}
        }
    }

    public void readJson(String json) {
        clear();
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        if (o == null) return;
        if (o.has("points") && o.get("points").isJsonObject()) readPoints(o.getAsJsonObject("points"), points);
        if (o.has("speed")) {
            double value = o.get("speed").getAsDouble();
            if (Double.isFinite(value)) speed = Math.max(0.1, Math.min(100, value));
        }
        if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
        if (o.has("absurd")) absurd = o.get("absurd").getAsBoolean();
        if (o.has("combat")) combat = o.get("combat").getAsBoolean();
        if (o.has("difficulty")) difficulty = Math.max(0, Math.min(2, o.get("difficulty").getAsInt()));
        if (o.has("buildLimit")) buildLimit = Math.max(1, Math.min(4096, o.get("buildLimit").getAsInt()));
        if (o.has("warp")) warp = Math.max(1, o.get("warp").getAsInt());
        if (o.has("journal") && o.get("journal").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("journal")) {
                try {
                    JsonObject line = e.getAsJsonObject();
                    Skill s = Skill.byId(line.get("skill").getAsString());
                    int level = line.get("level").getAsInt();
                    if (s != null && level >= 1 && level <= Skill.MAX_LEVEL) record(line.get("day").getAsLong(), s, level);
                } catch (RuntimeException ignored) {}
            }
        }
        if (o.has("chronicle") && o.get("chronicle").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("chronicle")) {
                try {
                    JsonObject line = e.getAsJsonObject();
                    String key = line.get("key").getAsString();
                    if (!key.startsWith("mobslearn.")) continue;
                    List<String> args = new ArrayList<>();
                    if (line.has("args")) for (JsonElement a : line.getAsJsonArray("args")) args.add(a.getAsString());
                    chronicle(line.get("day").getAsLong(), key, args, line.has("icon") ? line.get("icon").getAsString() : null);
                } catch (RuntimeException ignored) {}
            }
        }
        if (o.has("regions") && o.get("regions").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : o.getAsJsonObject("regions").entrySet()) {
                if (regions.size() >= MAX_REGIONS) break;
                if (entry.getValue().isJsonObject()) readPoints(entry.getValue().getAsJsonObject(), region(entry.getKey()));
            }
        }
    }
}
