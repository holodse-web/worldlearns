package com.rrm.mobslearn.learn;

import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.MobsLearnRules;
import com.rrm.mobslearn.mob.TraitTags;
import com.rrm.mobslearn.mob.Traits;
import com.rrm.mobslearn.net.Net;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

public final class WorldBrain {
    private static MinecraftServer server;
    private static final Knowledge knowledge = new Knowledge();
    private static final Map<UUID, String> travelers = new HashMap<>();
    private static boolean dirty;
    private static int ticks;

    private WorldBrain() {}
    public static Knowledge get() { return knowledge; }
    public static int level(String skillId) { return knowledge.level(skillId); }
    public static String regionKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "/" + Math.floorDiv(pos.getX(), 256) + "/" + Math.floorDiv(pos.getZ(), 256);
    }

    public static int levelAt(String id, ServerLevel level, BlockPos pos) {
        Skill skill = Skill.byId(id);
        if (skill == null || !knowledge.allows(skill)) return 0;
        int learned = knowledge.localLevel(skill, regionKey(level, pos));
        int intel = knowledge.intelligence();
        if (intel >= 12 && (id.equals("zombie_builder") || id.equals("zombie_bridge") || id.equals("zombie_digger"))) learned = Math.max(1, learned);
        if (intel >= 15 && (id.equals("zombie_camp") || id.equals("skeleton_tower") || id.equals("spider_nest") || id.equals("villager_build"))) learned = Math.max(1, learned);
        return learned;
    }

    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("mobslearn.json"); }
    public static void start(MinecraftServer s) {
        server = s; ticks = 0; dirty = false; travelers.clear(); knowledge.clear(); Learning.reset();
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                String json = Files.readString(f, StandardCharsets.UTF_8);
                var original = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                Path backup = f.resolveSibling("mobslearn.before-regions.json");
                if (!original.has("schema") && !Files.exists(backup)) Files.copy(f, backup);
                knowledge.readJson(json);
            }
        } catch (Exception e) { MobsLearn.LOG.error("[MobsLearn] Failed to read knowledge", e); }
        knowledge.enabled = s.getGameRules().get(MobsLearnRules.ENABLED);
        MobsLearn.LOG.info("[MobsLearn] World intelligence: {}%, enabled: {}", knowledge.intelligence(), knowledge.enabled);
    }
    public static void stop() { save(); server = null; travelers.clear(); Learning.reset(); }
    public static void save() {
        if (server == null) return;
        try {
            Path f = file(), tmp = f.resolveSibling("mobslearn.json.tmp");
            Files.writeString(tmp, knowledge.toJson(), StandardCharsets.UTF_8);
            try { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (Exception e) { MobsLearn.LOG.error("[MobsLearn] Failed to save knowledge", e); }
    }

    public static void teach(Trigger trigger, MobGroup source, double amount) { teach(trigger, source, amount, null, null); }
    public static void teach(Trigger trigger, MobGroup source, double amount, ServerLevel level, BlockPos pos) {
        if (server == null || !knowledge.enabled || source == null || amount <= 0 || !Double.isFinite(amount)) return;
        int intelBefore = knowledge.intelligence();
        Map<Skill, Integer> before = new HashMap<>();
        boolean witness = false;
        if (level != null && pos != null) {
            witness = !level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(24),
                m -> m.isAlive() && MobGroup.of(m.getType()) == source && level.clip(new net.minecraft.world.level.ClipContext(m.getEyePosition(), net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, m)).getType() == net.minecraft.world.phys.HitResult.Type.MISS).isEmpty();
        }
        Map<String, Double> area = witness ? knowledge.region(regionKey(level, pos)) : null;
        for (Skill skill : Skill.ALL) {
            if (!knowledge.allows(skill) || skill.source() != source) continue;
            double weight = skill.trigger() == trigger ? 1.0 :
                (trigger == Trigger.UNREACHABLE && source == MobGroup.ZOMBIE && skill.id().equals("zombie_bridge") ? 0.3 : 0);
            if (weight == 0) continue;
            before.put(skill, knowledge.level(skill));
            double gain = amount * knowledge.speed * weight;
            knowledge.points.compute(skill.id(), (id, old) -> Math.min(skill.thresholds()[2], (old == null ? 0 : old) + gain));
            if (area != null) area.compute(skill.id(), (id, old) -> Math.min(skill.thresholds()[2], (old == null ? 0 : old) + gain * 1.15));
            dirty = true;
        }
        for (Map.Entry<Skill, Integer> entry : before.entrySet()) {
            int now = knowledge.level(entry.getKey());
            if (now > entry.getValue()) announce(entry.getKey(), now);
        }
        checkWorld(intelBefore);
    }

    public static void setLevel(Skill skill, int level) {
        int before = knowledge.intelligence();
        if (skill.group() == MobGroup.WORLD) return;
        level = Math.max(0, Math.min(Skill.MAX_LEVEL, level));
        knowledge.points.put(skill.id(), level == 0 ? 0.0 : (double) skill.thresholds()[level - 1]);
        if (level > 0) announce(skill, level);
        checkWorld(before); save(); syncAll();
    }
    public static void reset() {
        knowledge.points.clear(); knowledge.regions.clear(); knowledge.journal.clear(); travelers.clear();
        save(); syncAll();
    }
    public static void setSpeed(double speed) {
        if (!Double.isFinite(speed)) return;
        knowledge.speed = Math.max(0.1, Math.min(100000, speed)); save(); syncAll();
    }
    public static boolean canConfigure(ServerPlayer player) {
        return server != null && (server.isSingleplayerOwner(player.nameAndId())
            || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack()));
    }
    public static void configure(ServerPlayer player, Net.SettingsPayload settings) {
        if (!canConfigure(player) || !Double.isFinite(settings.speed())) { syncTo(player); return; }
        knowledge.speed = Math.max(0.1, Math.min(100000, settings.speed()));
        knowledge.absurd = settings.absurd(); knowledge.combat = settings.combat();
        knowledge.difficulty = Math.max(0, Math.min(2, settings.difficulty())); knowledge.enabled = settings.enabled();
        com.rrm.mobslearn.mob.Colonies.setWarp(settings.warp());
        server.getGameRules().set(MobsLearnRules.ENABLED, knowledge.enabled, server);
        com.rrm.mobslearn.mob.Behaviors.reset();
        for (ServerLevel level : server.getAllLevels()) Traits.refreshSettings(level);
        save(); syncAll();
    }
    private static void checkWorld(int before) {
        int now = knowledge.intelligence();
        if (now == before) return;
        for (Skill skill : Skill.of(MobGroup.WORLD)) {
            if (knowledge.allows(skill) && skill.level(now) > skill.level(before)) announce(skill, skill.level(now));
        }
        if (now >= 50) for (ServerPlayer player : server.getPlayerList().getPlayers()) Milestones.award(player, "half_world");
        if (now >= 85) for (ServerPlayer player : server.getPlayerList().getPlayers()) Milestones.award(player, "world_geniuses");
    }
    private static void announce(Skill skill, int level) {
        long day = server.overworld() == null ? 1 : server.overworld().getOverworldClockTime() / 24000L + 1;
        knowledge.record(day, skill, level);
        dirty = true;
        MobsLearn.LOG.info("[MobsLearn] {} learned {} level {}", skill.group(), skill.id(), level);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Milestones.award(player, "first_lesson");
            if (ServerPlayNetworking.canSend(player, Net.LearnPayload.TYPE)) ServerPlayNetworking.send(player, new Net.LearnPayload(skill.id(), level));
        }
        save(); syncAll();
    }
    public static void syncAll() {
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) syncTo(player);
    }
    public static void syncTo(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Net.SyncPayload.TYPE)) {
            var json = knowledge.json(false);
            json.addProperty("canConfigure", canConfigure(player));
            json.addProperty("warp", com.rrm.mobslearn.mob.Colonies.warp());
            ServerPlayNetworking.send(player, new Net.SyncPayload(json.toString()));
        }
    }
    private static void spread(ServerLevel level) {
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(40))) {
                if (!mob.isAlive() || !visited.add(mob.getUUID())) continue;
                String key = regionKey(level, mob.blockPosition());
                String oldKey = travelers.put(mob.getUUID(), key);
                if (oldKey == null || oldKey.equals(key)) continue;
                Map<String, Double> home = knowledge.regions.get(oldKey);
                if (home == null) continue;
                MobGroup group = MobGroup.of(mob.getType());
                for (Skill skill : Skill.of(group == null ? MobGroup.WORLD : group)) {
                    if (!knowledge.allows(skill) || TraitTags.get(mob, skill.id()) <= 0) continue;
                    int memory = skill.level(home.getOrDefault(skill.id(), 0.0));
                    if (memory <= 0) continue;
                    var area = knowledge.region(key);
                    double seed = skill.thresholds()[0];
                    if (area.getOrDefault(skill.id(), 0.0) < seed) { area.put(skill.id(), seed); dirty = true; }
                }
            }
        }
        if (travelers.size() > 4096) travelers.clear();
    }
    public static void serverTick() {
        if (server == null) return;
        ticks++;
        if (ticks % 20 == 0) {
            boolean enabled = server.getGameRules().get(MobsLearnRules.ENABLED);
            if (enabled != knowledge.enabled) {
                knowledge.enabled = enabled;
                com.rrm.mobslearn.mob.Behaviors.reset();
                for (ServerLevel level : server.getAllLevels()) Traits.refreshSettings(level);
                dirty = true; syncAll();
            }
        }
        if (ticks % 200 == 0 && knowledge.enabled) {
            for (ServerLevel level : server.getAllLevels()) spread(level);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) Milestones.checkWorld(player);
        }
        if (ticks % 400 == 0 && dirty) { save(); syncAll(); }
    }
}
