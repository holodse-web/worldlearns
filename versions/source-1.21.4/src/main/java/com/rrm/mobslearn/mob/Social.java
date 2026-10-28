package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Trigger;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class Social {
    public static final String OBSERVER = "mobslearn.observer";
    public static final int TEASES = 24;
    private static final Map<UUID, Boolean> grounded = new HashMap<>();
    private static final Map<UUID, Long> work = new HashMap<>();
    private static final Map<UUID, long[]> watch = new HashMap<>();
    private static final Map<UUID, UUID> watching = new HashMap<>();
    private static final Map<UUID, Long> decided = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Long> shields = new HashMap<>();
    private static final Map<UUID, Integer> carried = new HashMap<>();

    private Social() {
    }

    public static void reset() {
        grounded.clear();
        work.clear();
        watch.clear();
        watching.clear();
        decided.clear();
        cooldowns.clear();
        shields.clear();
        carried.clear();
    }

    private static boolean cooldown(String key, long t, long ticks) {
        Long last = cooldowns.get(key);
        if (last != null && t - last < ticks) return false;
        cooldowns.put(key, t);
        if (cooldowns.size() > 4096) cooldowns.clear();
        return true;
    }

    public static void worked(Player p) {
        work.put(p.getUUID(), p.level().getGameTime());
    }

    public static boolean observer(Mob m) {
        return m.getTags().contains(OBSERVER);
    }

    public static void tick(ServerLevel level) {
        long t = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            boolean on = p.onGround();
            Boolean was = grounded.put(p.getUUID(), on);
            if (was != null && was && !on && p.getDeltaMovement().y > 0.2 && !p.getAbilities().flying && !p.isInWater() && !p.onClimbable()) jumped(level, p, t);
            if (t % 40 == 0) armor(level, p, t);
            if (t % 200 == 0) ambient(level, p, t);
            WorldSkills.playerTick(level, p, t);
        }
        if (grounded.size() > 256) grounded.clear();
        if (watch.size() > 256) {
            watch.clear();
            watching.clear();
        }
        for (UUID id : watch.keySet()) {
            if (level.getEntity(id) instanceof Mob m && m.getTarget() != null) {
                m.setTarget(null);
                m.setAggressive(false);
            }
        }
    }

    private static void jumped(ServerLevel level, ServerPlayer p, long t) {
        List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(10));
        if (near.isEmpty()) return;
        if (WorldBrain.get().enabled && cooldown("jump" + p.getUUID(), t, 10)) WorldBrain.teach(Trigger.PLAYER_JUMP, MobGroup.VILLAGER, 1, level, p.blockPosition());
        int lvl = WorldBrain.levelAt("villager_dance", level, p.blockPosition());
        if (lvl <= 0) return;
        RandomSource r = level.getRandom();
        int dancers = 0;
        for (Villager v : near) {
            if (v.isSleeping() || v.distanceTo(p) > 4 + lvl * 2 || r.nextFloat() > 0.3f + 0.2f * lvl) continue;
            dancers++;
            int type = r.nextInt(lvl);
            Behaviors.later(2 + r.nextInt(5), () -> dance(level, v, p, type, lvl, r));
        }
        if (dancers > 0 && r.nextInt(4) == 0) level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8f, 1f + r.nextFloat() * 0.3f);
        if (dancers >= 3) Milestones.award(p, "dance_floor");
    }

    private static void hop(Villager v, double power) {
        if (!v.isAlive() || !v.onGround()) return;
        v.setDeltaMovement(v.getDeltaMovement().x, power, v.getDeltaMovement().z);
        v.hurtMarked = true;
    }

    private static void dance(ServerLevel level, Villager v, Player p, int type, int lvl, RandomSource r) {
        if (!v.isAlive() || !v.onGround()) return;
        v.getNavigation().stop();
        v.getLookControl().setLookAt(p, 30f, 30f);
        switch (type) {
            case 0 -> {
                hop(v, 0.42 + 0.04 * lvl);
                level.sendParticles(ParticleTypes.NOTE, v.getX(), v.getY() + 2.2, v.getZ(), 1, 0, 0, 0, r.nextFloat());
            }
            case 1 -> {
                hop(v, 0.38);
                float start = v.getYRot();
                float dir = r.nextBoolean() ? 1f : -1f;
                for (int i = 1; i <= 14; i++) {
                    int step = i;
                    Behaviors.later(i, () -> {
                        if (!v.isAlive()) return;
                        float yaw = start + dir * 360f * step / 14f;
                        v.setYRot(yaw);
                        v.setYHeadRot(yaw);
                        v.setYBodyRot(yaw);
                        if (step % 4 == 0) level.sendParticles(ParticleTypes.FIREWORK, v.getX(), v.getY() + 1.0, v.getZ(), 2, 0.25, 0.4, 0.25, 0.02);
                    });
                }
            }
            default -> {
                float head = v.getYRot();
                for (int i = 0; i < 4; i++) {
                    int step = i;
                    Behaviors.later(i * 5, () -> {
                        if (!v.isAlive()) return;
                        hop(v, 0.3);
                        Builders.swing(v);
                        float yaw = head + (step % 2 == 0 ? 40f : -40f);
                        v.setYHeadRot(yaw);
                        v.getLookControl().setLookAt(v.getX() + Math.cos(Math.toRadians(yaw + 90)), v.getEyeY(), v.getZ() + Math.sin(Math.toRadians(yaw + 90)));
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.9, v.getZ(), 2, 0.3, 0.2, 0.3, 0);
                    });
                }
                level.sendParticles(ParticleTypes.NOTE, v.getX(), v.getY() + 2.3, v.getZ(), 2, 0.3, 0.1, 0.3, 0.6);
            }
        }
    }

    private static void armor(ServerLevel level, ServerPlayer p, long t) {
        if (!WorldBrain.get().enabled || p.isCreative()) return;
        int stored = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var st = p.getInventory().getItem(i);
            if (!st.isEmpty() && st.has(net.minecraft.core.component.DataComponents.EQUIPPABLE) && !st.is(Items.ELYTRA) && !st.is(Items.CARVED_PUMPKIN)) stored++;
        }
        Integer before = carried.put(p.getUUID(), stored);
        if (before != null && stored > before) pickedArmor(level, p);
        int pieces = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!p.getItemBySlot(s).isEmpty()) pieces++;
        }
        if (pieces < 2) return;
        for (MobGroup watcher : new MobGroup[]{MobGroup.ZOMBIE, MobGroup.PILLAGER}) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16))) {
                if (MobGroup.of(m.getType()) == watcher && m.isAlive() && m.hasLineOfSight(p)) {
                    if (cooldown("armor" + watcher + p.getUUID(), t, 200)) WorldBrain.teach(Trigger.SEE_ARMOR, watcher, pieces >= 4 ? 1.5 : 1, level, p.blockPosition());
                    break;
                }
            }
        }
    }

    private static void pickedArmor(ServerLevel level, Player p) {
        if (!WorldBrain.get().enabled || !cooldown("pick" + p.getUUID(), level.getGameTime(), 100)) return;
        for (MobGroup watcher : new MobGroup[]{MobGroup.ZOMBIE, MobGroup.PILLAGER}) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16))) {
                if (MobGroup.of(m.getType()) == watcher && m.isAlive() && m.hasLineOfSight(p)) {
                    WorldBrain.teach(Trigger.SEE_ARMOR, watcher, 1, level, p.blockPosition());
                    break;
                }
            }
        }
    }

    private static final java.util.Set<Integer> QUOTED = java.util.Set.of(1, 6, 12);
    public static final int AMBIENT = 14;

    private static void ambient(ServerLevel level, ServerPlayer p, long t) {
        int lvl = WorldBrain.levelAt("villager_tease", level, p.blockPosition());
        if (lvl <= 0 || p.isCreative()) return;
        RandomSource r = level.getRandom();
        if (r.nextFloat() > 0.25f + 0.12f * lvl) return;
        List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(9));
        near.removeIf(v -> v.isSleeping() || v.isBaby() || !v.hasLineOfSight(p));
        if (near.isEmpty() || !cooldown("ambient" + p.getUUID(), t, 3600 - 600L * lvl)) return;
        Villager v = near.get(r.nextInt(near.size()));
        Component line;
        if (r.nextInt(3) == 0) {
            int idx;
            do {
                idx = r.nextInt(TEASES);
            } while (QUOTED.contains(idx));
            line = Component.translatable("mobslearn.tease." + idx, p.getName(), "");
        } else {
            line = Component.translatable("mobslearn.tease.ambient." + r.nextInt(AMBIENT), p.getName());
        }
        speak(level, p, v, line, r, 10 + r.nextInt(20));
    }

    private static void speak(ServerLevel level, ServerPlayer p, Villager v, Component line, RandomSource r, int delay) {
        Behaviors.later(delay, () -> {
            if (!v.isAlive()) return;
            v.getLookControl().setLookAt(p, 30f, 30f);
            level.playSound(null, v.blockPosition(), r.nextBoolean() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1f, 1.1f + r.nextFloat() * 0.2f);
            Talk.say(level, v, line, 32);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 1, 0.2, 0.1, 0.2, 0);
            Milestones.award(p, "teased");
        });
    }

    public static void chat(ServerPlayer sender, String text) {
        ServerLevel level = (ServerLevel) sender.level();
        long t = level.getGameTime();
        if (Talk.converse(sender, text)) {
            if (WorldBrain.get().enabled && cooldown("chat" + sender.getUUID(), t, 100)) WorldBrain.teach(Trigger.CHAT_NEAR, MobGroup.VILLAGER, 1, level, sender.blockPosition());
            return;
        }
        List<Villager> near = level.getEntitiesOfClass(Villager.class, sender.getBoundingBox().inflate(12));
        near.removeIf(v -> v.isSleeping() || v.isBaby());
        if (near.isEmpty()) return;
        if (WorldBrain.get().enabled && cooldown("chat" + sender.getUUID(), t, 100)) WorldBrain.teach(Trigger.CHAT_NEAR, MobGroup.VILLAGER, 1, level, sender.blockPosition());
        int lvl = WorldBrain.levelAt("villager_tease", level, sender.blockPosition());
        RandomSource r = level.getRandom();
        if (lvl <= 0 || r.nextFloat() > 0.25f + 0.2f * lvl || !cooldown("tease" + sender.getUUID(), t, 300 - 60L * lvl)) return;
        Villager v = near.get(r.nextInt(near.size()));
        int line = r.nextInt(TEASES);
        String quote = text.length() > 24 ? text.substring(0, 24) + "..." : text;
        speak(level, sender, v, Component.translatable("mobslearn.tease." + line, sender.getName(), quote), r, 25 + r.nextInt(30));
    }

    public static boolean observe(ServerLevel level, Mob mob, Player target, long t) {
        UUID id = mob.getUUID();
        long[] st = watch.get(id);
        if (st != null) {
            Player p = level.getPlayerByUUID(watching.get(id));
            if (p == null || !p.isAlive() || p.isSpectator() || t >= st[1]) {
                stop(mob);
                decided.put(id, t);
                return false;
            }
            mob.setTarget(null);
            mob.setAggressive(false);
            double d = mob.distanceTo(p);
            if (t < st[0]) {
                if (d < 3.2) {
                    stop(mob);
                    mob.setTarget(p);
                    decided.put(id, t);
                    return false;
                }
                mob.getLookControl().setLookAt(p, 30f, 30f);
                if (d < 7) away(mob, p, 6, 1.0);
                else if (d > 15) mob.getNavigation().moveTo(p, 0.9);
                else mob.getNavigation().stop();
                if (t % 60 == 0) level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 2.3, mob.getZ(), 6, 0.3, 0.2, 0.3, 0.4);
            } else if (mob.getNavigation().isDone()) {
                away(mob, p, 24, 1.0);
            }
            return true;
        }
        Player p = target;
        if (p == null && (t / 5 + mob.getId()) % 4 == 0) {
            Player near = level.getNearestPlayer(mob, 18);
            if (near != null && !near.isCreative() && !near.isSpectator() && mob.hasLineOfSight(near)) p = near;
        }
        if (p == null) return false;
        Long w = work.get(p.getUUID());
        if (w == null || t - w > 100) return false;
        double d = mob.distanceTo(p);
        if (d < 5 || d > 18) return false;
        Long dec = decided.get(id);
        if (dec != null && t - dec < 2400) return false;
        decided.put(id, t);
        if (decided.size() > 4096) decided.clear();
        if (level.getRandom().nextFloat() >= 0.25f) return false;
        long watchFor = 200 + level.getRandom().nextInt(200);
        watch.put(id, new long[]{t + watchFor, t + watchFor + 200});
        watching.put(id, p.getUUID());
        mob.addTag(OBSERVER);
        mob.setTarget(null);
        mob.getNavigation().stop();
        level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.6f);
        if (p instanceof ServerPlayer sp && p.distanceTo(mob) < 20) Milestones.award(sp, "watched");
        return true;
    }

    private static void stop(Mob mob) {
        watch.remove(mob.getUUID());
        watching.remove(mob.getUUID());
        mob.removeTag(OBSERVER);
    }

    private static void away(Mob mob, Player p, double dist, double speed) {
        Vec3 dir = mob.position().subtract(p.position());
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(1, 0, 0);
        dir = dir.normalize().scale(dist);
        mob.getNavigation().moveTo(mob.getX() + dir.x, mob.getY(), mob.getZ() + dir.z, speed);
    }

    public static void raiseShield(ServerLevel level, Mob mob, Player target, long t) {
        if (!mob.getOffhandItem().is(Items.SHIELD) || mob.distanceTo(target) > 6) return;
        Long last = shields.get(mob.getUUID());
        if (last != null && t - last < 60) {
            if (mob.isUsingItem() && t - last > 24) mob.stopUsingItem();
            return;
        }
        if (target.getAttackStrengthScale(0f) < 0.6f && !target.isUsingItem()) return;
        if (level.getRandom().nextFloat() > 0.35f) return;
        shields.put(mob.getUUID(), t);
        if (shields.size() > 2048) shields.clear();
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    public static boolean shielding(Mob mob) {
        return mob.isUsingItem() && mob.getUseItem().is(Items.SHIELD);
    }
}
