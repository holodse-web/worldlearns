package com.rrm.mobslearn.mob;

import com.google.gson.JsonObject;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

final class GreatMarch {
    static final String TAG = "mobslearn.march=";
    static final int MIN_INTELLIGENCE = 55, COOLDOWN_DAYS = 10;
    static final double SPEED = 1.5;

    private static boolean active, materialized;
    private static String capital = "", capitalKind = "zombie", targetVillage = "", targetPlayer = "", marchId = "";
    private static double x, z, tx, tz, health = 1.0;
    private static int ty, troops, first, title, incarnation;
    private static long startDay, nextDay, atTargetSince;
    private static final List<String> members = new ArrayList<>();
    private static UUID warlord;
    private static ServerBossEvent bar;

    private GreatMarch() {
    }

    static void load(JsonObject o) {
        active = o.has("active") && o.get("active").getAsBoolean();
        materialized = false;
        warlord = null;
        bar = null;
        members.clear();
        nextDay = o.has("nextDay") ? o.get("nextDay").getAsLong() : 0;
        if (!active) return;
        try {
            capital = o.get("capital").getAsString();
            capitalKind = o.get("capitalKind").getAsString();
            targetVillage = o.get("targetVillage").getAsString();
            targetPlayer = o.get("targetPlayer").getAsString();
            marchId = o.get("id").getAsString();
            x = o.get("x").getAsDouble();
            z = o.get("z").getAsDouble();
            tx = o.get("tx").getAsDouble();
            tz = o.get("tz").getAsDouble();
            ty = o.get("ty").getAsInt();
            health = o.get("health").getAsDouble();
            troops = o.get("troops").getAsInt();
            first = o.get("first").getAsInt();
            title = o.get("title").getAsInt();
            incarnation = o.get("inc").getAsInt();
            startDay = o.get("startDay").getAsLong();
            for (var m : o.getAsJsonArray("members")) members.add(m.getAsString());
        } catch (RuntimeException e) {
            active = false;
        }
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("active", active);
        o.addProperty("nextDay", nextDay);
        if (!active) return o;
        o.addProperty("capital", capital);
        o.addProperty("capitalKind", capitalKind);
        o.addProperty("targetVillage", targetVillage);
        o.addProperty("targetPlayer", targetPlayer);
        o.addProperty("id", marchId);
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("tx", tx);
        o.addProperty("tz", tz);
        o.addProperty("ty", ty);
        o.addProperty("health", health);
        o.addProperty("troops", troops);
        o.addProperty("first", first);
        o.addProperty("title", title);
        o.addProperty("inc", incarnation);
        o.addProperty("startDay", startDay);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String m : members) arr.add(m);
        o.add("members", arr);
        return o;
    }

    static void stop() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        materialized = false;
        warlord = null;
    }

    private static boolean on() {
        return Saga.enabled() && WorldBrain.get().greatMarch && WorldBrain.get().settlements;
    }

    static boolean isMarcher(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(TAG)) return true;
        return false;
    }

    static boolean isWarlord(Entity e) {
        return e.getTags().contains(TAG + "warlord#" + marchId) || warlord != null && warlord.equals(e.getUUID());
    }

    private static Component warlordName() {
        return Component.translatable("mobslearn.march.warlord", Component.translatable("mobslearn.nemesis.first." + first),
            Component.translatable("mobslearn.nemesis.title." + title));
    }

    private static Component targetName() {
        if (!targetPlayer.isEmpty()) return Component.translatable("mobslearn.march.target.home");
        return Component.translatable("mobslearn.march.target.village");
    }

    static void tick(ServerLevel level, long t) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (!active) {
            if (t % 1200 == 600 && on()) tryStart(level);
            return;
        }
        if (!on()) {
            disband(level, null);
            return;
        }
        if (t % 20 != 0) return;
        if (bar == null) {
            bar = Saga.bar(Component.empty(), BossEvent.BossBarColor.PURPLE);
            bar.setDarkenScreen(false);
        }
        bar.setName(Component.translatable("mobslearn.march.bar", warlordName(), targetName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (ServerPlayer p : level.players()) bar.addPlayer(p);
        long day = Saga.day(level);
        if (day - startDay > 2) {
            disband(level, Component.translatable("mobslearn.march.tired"));
            return;
        }
        if (materialized) materialTick(level, t);
        else virtualTick(level);
    }

    static boolean force(ServerLevel level) {
        if (active || !level.dimension().equals(Level.OVERWORLD)) return false;
        start(level, true);
        return active;
    }

    private static void tryStart(ServerLevel level) {
        long day = Saga.day(level);
        if (day < nextDay || !level.isNight() || level.players().isEmpty() || WorldBrain.get().intelligence() < MIN_INTELLIGENCE) return;
        start(level, false);
    }

    private static void start(ServerLevel level, boolean forced) {
        List<Colonies.View> hostile = new ArrayList<>();
        List<Colonies.View> villages = new ArrayList<>();
        for (Colonies.View v : Colonies.views(level)) {
            if (v.hostile() && v.tier() >= (forced ? 1 : 2) && !v.camp() && (forced || !v.neutral())) hostile.add(v);
            if (!v.hostile() && !v.camp()) villages.add(v);
        }
        if (hostile.size() < (forced ? 1 : 2)) return;
        Colonies.View cap = hostile.get(0);
        for (Colonies.View v : hostile) if (v.tier() > cap.tier() || v.tier() == cap.tier() && v.population() > cap.population()) cap = v;
        Colonies.View target = null;
        for (Colonies.View v : villages) {
            double d = v.center().distSqr(cap.center());
            if (d > 1200 * 1200) continue;
            if (target == null || v.tier() > target.tier() || v.tier() == target.tier() && v.population() > target.population()) target = v;
        }
        BlockPos goal;
        String player = "";
        if (target != null) goal = target.center();
        else {
            ServerPlayer near = null;
            double bd = 1500 * 1500;
            for (ServerPlayer p : level.players()) {
                Saga.Home h = Saga.home(p.getUUID());
                if (h == null || !h.dim().equals(Saga.dimId(level))) continue;
                double d = h.pos().distSqr(cap.center());
                if (d < bd) {
                    bd = d;
                    near = p;
                }
            }
            if (near != null) goal = Saga.home(near.getUUID()).pos();
            else if (forced && !level.players().isEmpty()) {
                for (ServerPlayer p : level.players()) {
                    double d = p.blockPosition().distSqr(cap.center());
                    if (near == null || d < near.blockPosition().distSqr(cap.center())) near = p;
                }
                goal = near.blockPosition();
            } else return;
            player = near.getUUID().toString();
        }
        RandomSource r = level.getRandom();
        active = true;
        materialized = false;
        marchId = Long.toString(System.nanoTime() & 0xFFFFFFL, 36);
        capital = cap.id();
        capitalKind = cap.kind();
        targetVillage = target == null ? "" : target.id();
        targetPlayer = player;
        x = cap.center().getX();
        z = cap.center().getZ();
        tx = goal.getX();
        tz = goal.getZ();
        ty = goal.getY();
        health = 1.0;
        first = r.nextInt(Nemesis.FIRST_NAMES);
        title = r.nextInt(Nemesis.TITLES);
        incarnation = 0;
        startDay = Saga.day(level);
        members.clear();
        for (Colonies.View v : hostile) if (members.size() < 5) members.add(v.id());
        troops = Math.min(14, 4 + 2 * members.size());
        Saga.markDirty();
        Component msg = Component.translatable("mobslearn.march.begin", warlordName(), members.size(), targetName());
        for (ServerPlayer p : level.players()) {
            Saga.notice(p, msg);
            level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        WorldBrain.chronicle(msg, "white_banner");
    }

    private static void virtualTick(ServerLevel level) {
        double dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
        if (d <= 24) {
            arrive(level);
            return;
        }
        x += dx / d * SPEED;
        z += dz / d * SPEED;
        double left = Math.max(0, d - SPEED);
        if (bar != null) bar.setProgress((float) Math.max(0.02, Math.min(1.0, left / 1200.0)));
        BlockPos at = new BlockPos((int) x, 64, (int) z);
        if (!level.isLoaded(at) || level.getNearestPlayer(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z), z, 72, false) == null) return;
        materialize(level);
    }

    private static EntityType<? extends Mob> warlordType() {
        return switch (capitalKind) {
            case "skeleton" -> EntityType.WITHER_SKELETON;
            case "nether" -> EntityType.PIGLIN_BRUTE;
            case "enderman" -> EntityType.ENDERMAN;
            case "zombie" -> EntityType.ZOMBIE;
            default -> EntityType.VINDICATOR;
        };
    }

    private static void materialize(ServerLevel level) {
        RandomSource r = level.getRandom();
        int gx = (int) x, gz = (int) z;
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
        Mob boss = warlordType().create(level);
        if (boss == null) return;
        incarnation++;
        boss.moveTo(gx + 0.5, gy, gz + 0.5, r.nextFloat() * 360f, 0f);
        boss.addTag(TraitTags.SEEN);
        boss.addTag(TAG + "warlord#" + marchId);
        boss.addTag(TAG + "inc#" + marchId + "#" + incarnation);
        boss.setPersistenceRequired();
        boss.setCustomName(warlordName().copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        boss.setCustomNameVisible(true);
        modifier(boss, Attributes.MAX_HEALTH, "warlord_hp", 100);
        modifier(boss, Attributes.ATTACK_DAMAGE, "warlord_dmg", 5);
        modifier(boss, Attributes.ARMOR, "warlord_armor", 8);
        modifier(boss, Attributes.KNOCKBACK_RESISTANCE, "warlord_kb", 0.8);
        modifier(boss, Attributes.SCALE, "warlord_scale", 0.35);
        boss.setHealth((float) Math.max(10, boss.getMaxHealth() * health));
        gear(boss, EquipmentSlot.HEAD, Items.DIAMOND_HELMET);
        gear(boss, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
        if (boss.getMainHandItem().isEmpty()) gear(boss, EquipmentSlot.MAINHAND, capitalKind.equals("nether") ? Items.GOLDEN_AXE : Items.DIAMOND_SWORD);
        level.addFreshEntity(boss);
        warlord = boss.getUUID();
        int made = 0;
        for (int i = 0; i < troops * 2 && made < troops && !members.isEmpty(); i++) {
            String colony = members.get(i % members.size());
            int ox = gx + r.nextInt(13) - 6, oz = gz + r.nextInt(13) - 6;
            Mob m = Colonies.spawnResidentFor(level, colony, new BlockPos(ox, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz), oz));
            if (m == null) continue;
            m.removeTag(Colonies.homeTag(colony));
            m.addTag(TAG + "troop#" + marchId);
            m.addTag(TAG + "inc#" + marchId + "#" + incarnation);
            made++;
        }
        materialized = true;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, gx + 0.5, gy + 1, gz + 0.5, 60, 4, 1, 4, 0.02);
        level.playSound(null, new BlockPos(gx, gy, gz), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.5f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(gx, gy, gz) < 160 * 160) Saga.notice(p, Component.translatable("mobslearn.march.here", warlordName()));
        }
    }

    private static void gear(Mob m, EquipmentSlot slot, net.minecraft.world.item.Item item) {
        m.setItemSlot(slot, new ItemStack(item));
        m.setDropChance(slot, 0.25f);
    }

    private static void modifier(Mob mob, Holder<Attribute> attr, String id, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(MobsLearn.id(id));
        inst.addPermanentModifier(new AttributeModifier(MobsLearn.id(id), value, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void materialTick(ServerLevel level, long t) {
        Entity e = warlord == null ? null : level.getEntity(warlord);
        if (!(e instanceof Mob boss) || !boss.isAlive()) {
            materialized = false;
            return;
        }
        x = boss.getX();
        z = boss.getZ();
        health = boss.getHealth() / boss.getMaxHealth();
        if (bar != null) bar.setProgress((float) Math.max(0, Math.min(1, health)));
        double d = Math.sqrt((tx - x) * (tx - x) + (tz - z) * (tz - z));
        if (d < 20) {
            if (atTargetSince == 0) atTargetSince = t;
            if (t - atTargetSince > 2400) arrive(level);
        } else {
            atTargetSince = 0;
            if (boss.getTarget() == null && (boss.getNavigation().isDone() || t % 100 == 0)) {
                double step = Math.min(d, 24);
                boss.getNavigation().moveTo(x + (tx - x) / d * step, ty, z + (tz - z) / d * step, 1.0);
            }
        }
        if (t % 40 == 0) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(48))) {
                if (!m.getTags().contains(TAG + "troop#" + marchId) || m.getTarget() != null) continue;
                if (m.distanceTo(boss) > 8) m.getNavigation().moveTo(boss, 1.1);
            }
            level.sendParticles(ParticleTypes.WITCH, boss.getX(), boss.getY() + boss.getBbHeight() + 0.4, boss.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
        }
    }

    static void mobTick(ServerLevel level, Mob mob, long t) {
    }

    static boolean refusesTarget(Mob m, LivingEntity target) {
        return isMarcher(m) && isMarcher(target);
    }

    private static void arrive(ServerLevel level) {
        Component msg;
        if (!targetVillage.isEmpty()) {
            Colonies.burnVillage(level, targetVillage);
            msg = Component.translatable("mobslearn.march.burned", warlordName());
        } else {
            try {
                HomeRaids.markAway(UUID.fromString(targetPlayer), capital, Saga.day(level));
            } catch (IllegalArgumentException ignored) {
            }
            msg = Component.translatable("mobslearn.march.sacked", warlordName());
        }
        for (ServerPlayer p : level.players()) Saga.notice(p, msg);
        WorldBrain.chronicle(msg, "fire_charge");
        end(level, COOLDOWN_DAYS);
    }

    private static void disband(ServerLevel level, Component why) {
        if (why != null) for (ServerPlayer p : level.players()) Saga.notice(p, why);
        end(level, COOLDOWN_DAYS / 2);
    }

    private static void end(ServerLevel level, long cooldown) {
        Entity boss = warlord == null ? null : level.getEntity(warlord);
        for (Mob m : level.getEntitiesOfClass(Mob.class, boss != null ? boss.getBoundingBox().inflate(64) : new net.minecraft.world.phys.AABB(new BlockPos((int) x, 64, (int) z)).inflate(64, 128, 64))) {
            if (!isMarcher(m)) continue;
            for (String tag : List.copyOf(m.getTags())) if (tag.startsWith(TAG)) m.removeTag(tag);
        }
        active = false;
        materialized = false;
        warlord = null;
        atTargetSince = 0;
        nextDay = Saga.day(level) + cooldown;
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        Saga.markDirty();
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        if (!active || warlord == null || !warlord.equals(e.getUUID())) return;
        Component msg = Component.translatable("mobslearn.march.broken", warlordName());
        for (ServerPlayer p : level.players()) {
            Saga.notice(p, msg);
            if (p.distanceTo(e) < 64) Milestones.award(p, "march_broken");
        }
        WorldBrain.chronicle(msg, "totem_of_undying");
        ExperienceOrb.award(level, e.position(), 200);
        for (ItemStack s : List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.EMERALD, 16), new ItemStack(Items.DIAMOND, 3),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), Frameups.bannerFor(capital, capitalKind))) {
            level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), s));
        }
        level.playSound(null, e.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        end(level, COOLDOWN_DAYS);
    }

    static void onUnload(ServerLevel level, Entity e) {
        if (!active || warlord == null || !warlord.equals(e.getUUID()) || e.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) return;
        if (e instanceof Mob boss) health = boss.getHealth() / boss.getMaxHealth();
        x = e.getX();
        z = e.getZ();
        materialized = false;
        warlord = null;
        Saga.markDirty();
    }

    static void onLoad(ServerLevel level, Entity e) {
        if (!isMarcher(e)) return;
        boolean current = false;
        for (String t : e.getTags()) if (active && t.equals(TAG + "inc#" + marchId + "#" + incarnation)) current = true;
        if (current) return;
        Behaviors.later(1, () -> {
            if (!e.isAlive()) return;
            for (String tag : List.copyOf(e.getTags())) if (tag.startsWith(TAG)) e.removeTag(tag);
            if (e instanceof Mob m && m.hasCustomName() && m.getMaxHealth() > 60) e.discard();
        });
    }
}
