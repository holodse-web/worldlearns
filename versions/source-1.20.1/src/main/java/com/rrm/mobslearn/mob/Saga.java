package com.rrm.mobslearn.mob;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.WorldBrain;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;

public final class Saga {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static MinecraftServer server;
    static boolean dirty;
    private static long ticks;
    private static final Map<UUID, Home> homes = new HashMap<>();

    record Home(String dim, BlockPos pos) {
    }

    private Saga() {
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("mobslearn_saga.json");
    }

    public static void start(MinecraftServer s) {
        server = s;
        ticks = 0;
        homes.clear();
        JsonObject root = new JsonObject();
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                JsonObject read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
                if (read != null) root = read;
            }
        } catch (Exception e) {
            MobsLearn.LOG.error("Can't read world events", e);
        }
        try {
            if (root.has("homes")) for (var e : root.getAsJsonObject("homes").entrySet()) {
                String[] p = e.getValue().getAsString().split("\\|");
                homes.put(UUID.fromString(e.getKey()), new Home(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]))));
            }
        } catch (RuntimeException ignored) {
        }
        Nemesis.load(section(root, "nemesis"));
        HomeRaids.load(section(root, "raids"));
        Captives.load(section(root, "captives"));
        Hearths.load(section(root, "hearths"));
        Frameups.load(section(root, "frameups"));
        Allies.load(section(root, "allies"));
        GreatMarch.load(section(root, "march"));
        BossMemory.load(section(root, "bosses"));
        dirty = false;
    }

    private static JsonObject section(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    public static void stop() {
        if (server == null) return;
        HomeRaids.stop(server);
        GreatMarch.stop();
        Allies.stop();
        dirty = true;
        save();
        server = null;
        homes.clear();
    }

    public static void save() {
        if (server == null || !dirty) return;
        try {
            JsonObject root = new JsonObject();
            JsonObject h = new JsonObject();
            for (var e : homes.entrySet()) {
                BlockPos p = e.getValue().pos();
                h.addProperty(e.getKey().toString(), e.getValue().dim() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ());
            }
            root.add("homes", h);
            root.add("nemesis", Nemesis.save());
            root.add("raids", HomeRaids.save());
            root.add("captives", Captives.save());
            root.add("hearths", Hearths.save());
            root.add("frameups", Frameups.save());
            root.add("allies", Allies.save());
            root.add("march", GreatMarch.save());
            root.add("bosses", BossMemory.save());
            Path f = file();
            Path tmp = f.resolveSibling("mobslearn_saga.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (Exception e) {
            MobsLearn.LOG.error("Can't save world events", e);
        }
    }

    private static final java.util.Set<String> reported = new java.util.HashSet<>();

    static void report(Exception e) {
        StackTraceElement[] st = e.getStackTrace();
        String where = e.getClass().getName() + (st.length > 0 ? st[0].toString() : "");
        if (reported.size() < 64 && reported.add(where)) MobsLearn.LOG.warn("World event failed", e);
    }

    public static void markDirty() {
        dirty = true;
    }

    static long day(ServerLevel level) {
        ServerLevel over = level.getServer().overworld();
        return (over == null ? level : over).getDayTime() / 24000L;
    }

    static String dimId(Level level) {
        return level.dimension().location().toString();
    }

    static Home home(UUID player) {
        return homes.get(player);
    }

    static void notice(ServerPlayer p, Component text) {
        com.rrm.mobslearn.net.Net.notice(p, text);
    }

    static void overlay(ServerPlayer p, Component text) {
        p.displayClientMessage(text, true);
    }

    static net.minecraft.world.item.ItemStack stack(String id, int count) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (key == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
        return item == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(item, Math.max(1, count));
    }

    static net.minecraft.server.level.ServerBossEvent bar(Component name, net.minecraft.world.BossEvent.BossBarColor color) {
        return new net.minecraft.server.level.ServerBossEvent(name, color, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
    }

    static void name(net.minecraft.world.item.ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    static net.minecraft.sounds.SoundEvent sound(String id) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + id);
        if (key == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(key);
    }

    static boolean enabled() {
        return server != null && WorldBrain.get().enabled;
    }

    public static void serverTick(MinecraftServer s) {
        if (server == null) return;
        ticks++;
        if (ticks % 20 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.isSleeping()) p.getSleepingPos().ifPresent(bed -> {
                    Home old = homes.put(p.getUUID(), new Home(dimId(p.level()), bed.immutable()));
                    if (old == null || !old.pos().equals(bed)) dirty = true;
                });
            }
        }
        if (ticks % 100 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) cleanLeakedEffects(p);
        }
        if (ticks % 1200 == 0) save();
    }

    private static void cleanLeakedEffects(ServerPlayer p) {
        MobEffectInstance jump = p.getEffect(MobEffects.JUMP);
        if (jump != null && jump.isInfiniteDuration() && !jump.isVisible() && !jump.isAmbient()) p.removeEffect(MobEffects.JUMP);
    }

    public static void levelTick(ServerLevel level) {
        if (server == null) return;
        long t = level.getGameTime();
        try {
            Nemesis.tick(level, t);
            HomeRaids.tick(level, t);
            Captives.tick(level, t);
            Hearths.tick(level, t);
            Frameups.tick(level, t);
            Allies.tick(level, t);
            GreatMarch.tick(level, t);
            BossMemory.levelTick(level, t);
        } catch (Exception e) {
            report(e);
        }
    }

    public static boolean mobTick(ServerLevel level, Mob mob, MobGroup g, long t) {
        try {
            if (Captives.mobTick(level, mob, t)) return true;
            Allies.mercTick(level, mob, t);
            Nemesis.mobTick(level, mob, t);
            HomeRaids.mobTick(level, mob, t);
            GreatMarch.mobTick(level, mob, t);
        } catch (Exception e) {
            report(e);
        }
        return false;
    }

    public static void afterDamage(LivingEntity e, DamageSource src, float taken) {
        if (server == null || taken <= 0 || !(e.level() instanceof ServerLevel level)) return;
        try {
            Captives.afterDamage(level, e, src, taken);
            Nemesis.afterDamage(level, e, src, taken);
            BossMemory.afterDamage(level, e, src, taken);
            Wildlife.afterDamage(level, e, src, taken);
            Allies.afterDamage(level, e, src);
        } catch (Exception ex) {
            report(ex);
        }
    }

    public static void onDeath(LivingEntity e, DamageSource src) {
        if (server == null || !(e.level() instanceof ServerLevel level)) return;
        try {
            Nemesis.onDeath(level, e, src);
            Captives.onDeath(level, e, src);
            HomeRaids.onDeath(level, e, src);
            Frameups.onDeath(level, e, src);
            GreatMarch.onDeath(level, e, src);
            BossMemory.onDeath(level, e, src);
            Allies.onDeath(level, e, src);
        } catch (Exception ex) {
            report(ex);
        }
    }

    public static void onEntityLoad(Entity e, ServerLevel level) {
        if (server == null) return;
        Nemesis.onLoad(level, e);
        GreatMarch.onLoad(level, e);
    }

    public static void onUnload(Entity e, ServerLevel level) {
        if (server == null) return;
        HomeRaids.onUnload(level, e);
        GreatMarch.onUnload(level, e);
    }

    public static InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity) {
        if (server == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !WorldBrain.get().enabled) return InteractionResult.PASS;
        InteractionResult r = Captives.interact(sl, sp, hand, entity);
        if (r != InteractionResult.PASS) return r;
        return Allies.interact(sl, sp, hand, entity);
    }

    public static void useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (server == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !WorldBrain.get().enabled) return;
        Frameups.useBlock(sl, sp, hand, hit);
    }

    public static void blockBroken(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        if (server == null) return;
        Hearths.blockBroken(level, player, pos, state);
        BossMemory.blockBroken(level, player, pos, state);
    }

    public static boolean refusesTarget(Mob m, LivingEntity target) {
        if (server == null || target == null) return false;
        return Captives.refusesTarget(m, target) || Allies.refusesTarget(m, target) || GreatMarch.refusesTarget(m, target);
    }

    public static void settlementTick(ServerLevel level, String id, List<Mob> residents) {
        if (server == null) return;
        HomeRaids.settlementTick(level, id, residents);
        Hearths.settlementTick(level, id, residents);
    }

    public static void chiefKilled(ServerLevel level, Mob dead, ServerPlayer p, String colonyId, List<Mob> left) {
        if (server == null) return;
        Nemesis.chiefKilled(level, dead, p, colonyId, left);
    }

    public static void sendNemeses(ServerPlayer p) {
        Nemesis.send(p);
    }

    public static boolean force(String event, ServerLevel level, ServerPlayer p) {
        if (server == null) return false;
        return switch (event) {
            case "march" -> GreatMarch.force(level);
            case "raid" -> p != null && HomeRaids.force(level, p);
            case "nemesis" -> p != null && Nemesis.force(level, p);
            default -> false;
        };
    }

    public static double learningFactor(ServerLevel level, BlockPos pos) {
        return server == null ? 1.0 : Hearths.factor(level, pos);
    }
}
