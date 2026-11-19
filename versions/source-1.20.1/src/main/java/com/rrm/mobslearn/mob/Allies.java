package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.MobGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class Allies {
    static final String MERC = "mobslearn.merc=", UNTIL = "mobslearn.merc_until=", GUARD = "mobslearn.merc_guard=";
    static final int MERC_DAYS = 3, MAX_MERCS = 4;

    private static final Map<String, Set<String>> allies = new HashMap<>();

    private record Call(String village, String dim, BlockPos at, long until) {
    }

    private static final List<Call> calls = new ArrayList<>();

    private Allies() {
    }

    static void load(JsonObject o) {
        allies.clear();
        calls.clear();
        if (!o.has("villages")) return;
        for (var e : o.getAsJsonObject("villages").entrySet()) {
            Set<String> set = new HashSet<>();
            for (JsonElement p : e.getValue().getAsJsonArray()) set.add(p.getAsString());
            allies.put(e.getKey(), set);
        }
    }

    static JsonObject save() {
        JsonObject o = new JsonObject(), v = new JsonObject();
        for (var e : allies.entrySet()) {
            JsonArray a = new JsonArray();
            for (String p : e.getValue()) a.add(p);
            v.add(e.getKey(), a);
        }
        o.add("villages", v);
        return o;
    }

    static void stop() {
        calls.clear();
    }

    private static String tag(Entity e, String prefix) {
        for (String t : e.getTags()) if (t.startsWith(prefix)) return t.substring(prefix.length());
        return null;
    }

    static boolean isMercenary(Entity e) {
        return tag(e, MERC) != null;
    }

    static String mercenaryEgg(String kind) {
        return switch (kind) {
            case "zombie" -> "zombie_spawn_egg";
            case "skeleton" -> "skeleton_spawn_egg";
            case "pillager" -> "pillager_spawn_egg";
            case "nether" -> "piglin_spawn_egg";
            default -> null;
        };
    }

    static String mercenaryFee(String kind) {
        return switch (kind) {
            case "skeleton" -> "bone";
            case "pillager" -> "iron_ingot";
            case "nether" -> "gold_ingot";
            default -> "rotten_flesh";
        };
    }

    private static int mercsOf(ServerLevel level, ServerPlayer p) {
        int n = 0;
        String id = p.getUUID().toString();
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(96))) if (id.equals(tag(m, MERC))) n++;
        return n;
    }

    static boolean canHire(ServerLevel level, ServerPlayer p, Mob chief) {
        if (mercsOf(level, p) >= MAX_MERCS) {
            Saga.notice(p, Component.translatable("mobslearn.merc.full", MAX_MERCS));
            return false;
        }
        return true;
    }

    static void hire(ServerLevel level, ServerPlayer p, Mob chief, String kind, int count) {
        Colonies.View home = Colonies.viewOf(chief);
        if (home == null) return;
        long until = Saga.day(level) + MERC_DAYS;
        int made = 0;
        for (int i = 0; i < count; i++) {
            BlockPos at = chief.blockPosition().offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
            Mob m = Colonies.spawnResidentFor(level, home.id(), at);
            if (m == null) continue;
            m.removeTag(Colonies.homeTag(home.id()));
            m.addTag(MERC + p.getUUID());
            m.addTag(UNTIL + until);
            m.setPersistenceRequired();
            m.setCustomName(Component.translatable("mobslearn.merc.name", p.getName()).withStyle(ChatFormatting.AQUA));
            m.setCustomNameVisible(true);
            if (m.getMainHandItem().isEmpty()) {
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                m.setDropChance(EquipmentSlot.MAINHAND, 0f);
            }
            m.setTarget(null);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1.5, m.getZ(), 6, 0.3, 0.3, 0.3, 0);
            made++;
        }
        if (made == 0) return;
        Saga.notice(p, Component.translatable("mobslearn.merc.hired", made, MERC_DAYS));
        Milestones.award(p, "mercenaries");
        level.playSound(null, chief.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1f, 0.9f);
    }

    static void mercTick(ServerLevel level, Mob m, long t) {
        if (t % 10 != 0) return;
        String owner = tag(m, MERC);
        if (owner == null) return;
        long until = 0;
        try {
            until = Long.parseLong(tag(m, UNTIL));
        } catch (RuntimeException ignored) {
        }
        ServerPlayer p = null;
        try {
            p = level.getPlayerByUUID(UUID.fromString(owner)) instanceof ServerPlayer sp ? sp : null;
        } catch (IllegalArgumentException ignored) {
        }
        if (Saga.day(level) > until) {
            if (p != null) Saga.notice(p, Component.translatable("mobslearn.merc.leave", m.getDisplayName()));
            Talk.say(level, m, Component.translatable("mobslearn.merc.bye"), 24);
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            m.discard();
            return;
        }
        if (t % 100 == 0 && (m.getType() == net.minecraft.world.entity.EntityType.ZOMBIE || m.getType() == net.minecraft.world.entity.EntityType.SKELETON
            || m.getType() == net.minecraft.world.entity.EntityType.STRAY || m.getType() == net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER)) {
            m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false));
        }
        if (m.getTarget() instanceof Player || m.getTarget() != null && isMercenary(m.getTarget())) m.setTarget(null);
        String guard = tag(m, GUARD);
        BlockPos post = guard == null ? null : BlockPos.of(Long.parseLong(guard));
        Entity anchor = post == null && p != null && p.level() == level ? p : null;
        if (m.getTarget() == null || !m.getTarget().isAlive()) {
            Mob foe = foeNear(level, m, anchor != null ? anchor.blockPosition() : post != null ? post : m.blockPosition());
            if (foe != null) m.setTarget(foe);
        }
        if (m.getTarget() != null) return;
        if (post != null) {
            if (m.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) > 9) m.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
            return;
        }
        if (anchor == null) return;
        double d = m.distanceTo(anchor);
        if (d > 48) {
            m.moveTo(anchor.getX() + level.getRandom().nextInt(5) - 2, anchor.getY(), anchor.getZ() + level.getRandom().nextInt(5) - 2, m.getYRot(), 0f);
            m.getNavigation().stop();
        } else if (d > 7) {
            m.getNavigation().moveTo(anchor, 1.15);
        }
    }

    private static Mob foeNear(ServerLevel level, Mob merc, BlockPos around) {
        Mob best = null;
        double bd = 16 * 16;
        for (Mob o : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(around).inflate(16, 6, 16))) {
            if (o == merc || !o.isAlive() || isMercenary(o) || Captives.isCaptive(o) || Colonies.peaceful(o)) continue;
            MobGroup g = MobGroup.of(o.getType());
            boolean hostile = g != null && g.hostile || o instanceof net.minecraft.world.entity.monster.Enemy;
            if (!hostile || g == MobGroup.BOSS) continue;
            double d = o.distanceToSqr(around.getX(), around.getY(), around.getZ());
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    static boolean refusesTarget(Mob m, LivingEntity target) {
        if (isMercenary(m)) return target instanceof Player || isMercenary(target) || target instanceof net.minecraft.world.entity.npc.Villager
            || target instanceof net.minecraft.world.entity.animal.IronGolem;
        return isMercenary(target) && m instanceof net.minecraft.world.entity.animal.IronGolem;
    }

    static InteractionResult interact(ServerLevel level, ServerPlayer p, InteractionHand hand, Entity entity) {
        if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Mob m) || !p.getUUID().toString().equals(tag(m, MERC))) return InteractionResult.PASS;
        if (!p.isShiftKeyDown()) {
            Saga.overlay(p, Component.translatable("mobslearn.merc.hint"));
            return InteractionResult.SUCCESS;
        }
        String guard = tag(m, GUARD);
        if (guard != null) {
            m.removeTag(GUARD + guard);
            Saga.overlay(p, Component.translatable("mobslearn.merc.follow", m.getDisplayName()));
        } else {
            m.addTag(GUARD + m.blockPosition().asLong());
            Saga.overlay(p, Component.translatable("mobslearn.merc.guard", m.getDisplayName()));
        }
        level.playSound(null, m.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    static void afterDamage(ServerLevel level, LivingEntity e, DamageSource src) {
        if (!(e instanceof Mob m) || !isMercenary(m) || !(src.getEntity() instanceof Mob attacker) || isMercenary(attacker)) return;
        String owner = tag(m, MERC);
        for (Mob other : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(12))) {
            if (other != m && owner.equals(tag(other, MERC)) && other.getTarget() == null) other.setTarget(attacker);
        }
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        String owner = tag(e, MERC);
        if (owner == null) return;
        try {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(UUID.fromString(owner));
            if (p != null) Saga.notice(p, Component.translatable("mobslearn.merc.died", e.getDisplayName()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    static boolean isAlly(String village, Player p) {
        Set<String> set = allies.get(village);
        return set != null && set.contains(p.getUUID().toString());
    }

    static void helped(ServerLevel level, ServerPlayer p, BlockPos where) {
        String village = Colonies.villageIdAt(level, where);
        if (village == null) return;
        if (!allies.computeIfAbsent(village, k -> new HashSet<>()).add(p.getUUID().toString())) return;
        Saga.markDirty();
        for (net.minecraft.world.entity.npc.Villager v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, p.getBoundingBox().inflate(64))) {
            v.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, 20);
        }
        Saga.notice(p, Component.translatable("mobslearn.ally.joined"));
        Milestones.award(p, "village_ally");
        level.playSound(null, where, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1.1f);
    }

    static void villageAttacked(ServerLevel level, BlockPos center, Component attackers) {
        String village = Colonies.villageIdAt(level, center);
        if (village == null || !allies.containsKey(village)) return;
        calls.removeIf(c -> c.village().equals(village));
        calls.add(new Call(village, Saga.dimId(level), center, level.getGameTime() + 3600));
        for (ServerPlayer p : level.players()) {
            if (!isAlly(village, p) || p.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 40 * 40) continue;
            Saga.notice(p, Component.translatable("mobslearn.ally.call", attackers, (int) Math.sqrt(p.distanceToSqr(center.getX(), center.getY(), center.getZ()))));
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    static void tick(ServerLevel level, long t) {
        if (t % 40 != 0 || calls.isEmpty()) return;
        calls.removeIf(c -> level.getGameTime() > c.until());
        String dim = Saga.dimId(level);
        for (Call c : calls) {
            if (!c.dim().equals(dim)) continue;
            for (ServerPlayer p : level.players()) {
                if (!isAlly(c.village(), p)) continue;
                double dx = c.at().getX() + 0.5 - p.getX(), dz = c.at().getZ() + 0.5 - p.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 40) continue;
                float yaw = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
                float rel = Mth.wrapDegrees(yaw - p.getYRot());
                int idx = Math.floorMod(Math.round(rel / 45f), 8);
                Saga.overlay(p, Component.translatable("mobslearn.ally.compass", ARROWS[idx], (int) dist).withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
