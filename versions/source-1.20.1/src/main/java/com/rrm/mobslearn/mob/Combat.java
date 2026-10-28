package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.MobGroup;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class Combat {
    private static final ResourceLocation CRIT_ID = MobsLearn.id("crit");

    private static final class State {
        long nextAttack, staggerUntil, jumpAt, healUntil, nextHeal, nextGapple, nextVolley, nextDash, nextDodge, nextStrafeSwitch, recoveryAt, recoveryUntil;
        int strafeDir = 1, heals, gapples;
        boolean jumping;
        ItemStack storedWeapon;
        float storedWeaponDrop;
    }

    private static final Map<UUID, Mob> fighters = new HashMap<>();
    private static final Map<UUID, State> states = new HashMap<>();

    private Combat() {
    }

    public static void reset() {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Mob mob = fighters.get(entry.getKey());
            if (mob != null && entry.getValue().storedWeapon != null) restoreWeapon(mob, entry.getValue());
        }
        fighters.clear();
        states.clear();
    }

    public static void track(Mob mob) {
        fighters.put(mob.getUUID(), mob);
    }

    public static void forget(Mob mob) {
        State state = states.remove(mob.getUUID());
        if (state != null && state.storedWeapon != null) restoreWeapon(mob, state);
        fighters.remove(mob.getUUID());
        Builders.forget(mob);
    }

    public static void tick(ServerLevel level) {
        if (fighters.isEmpty()) return;
        long t = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Mob>> it = fighters.entrySet().iterator(); it.hasNext(); ) {
            Mob mob = it.next().getValue();
            if (mob.level() != level) continue;
            LivingEntity target = mob.getTarget();
            if (!mob.isAlive() || !(target instanceof Player p) || !p.isAlive() || p.isCreative() || p.isSpectator()) {
                State st = states.remove(mob.getUUID());
                if (st != null && st.storedWeapon != null && mob.isAlive()) restoreWeapon(mob, st);
                Builders.stop(mob);
                it.remove();
                continue;
            }
            try {
                run(level, mob, p, states.computeIfAbsent(mob.getUUID(), k -> new State()), t);
            } catch (Exception ignored) {
            }
        }
    }

    private static void run(ServerLevel level, Mob mob, Player target, State st, long t) {
        MobGroup g = MobGroup.of(mob.getType());
        if (g == null) return;
        RandomSource r = level.getRandom();
        if (g == MobGroup.ZOMBIE && t >= st.healUntil && Builders.tick(level, mob, target, t)) return;
        double dist = mob.distanceTo(target);
        int lvl;

        if (t >= st.recoveryAt && t < st.recoveryUntil) {
            mob.getNavigation().stop();
            Vec3 motion = mob.getDeltaMovement();
            mob.setDeltaMovement(motion.x * 0.6, motion.y, motion.z * 0.6);
            if (mob instanceof net.minecraft.world.entity.monster.Creeper cr) cr.setSwellDir(-1);
            return;
        }

        if ((lvl = TraitTags.get(mob, g.key() + "_medic")) > 0 && t >= st.nextHeal && st.heals < (lvl >= 3 ? 2 : 1)
            && mob.getHealth() < mob.getMaxHealth() * 0.3f) {
            st.heals++;
            st.healUntil = t + 50 + 10L * lvl;
            st.nextHeal = t + 900;
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50 + 10 * lvl, lvl >= 3 ? 1 : 0));
            if (mob instanceof EnderMan en) {
                for (int i = 0; i < 8; i++) {
                    if (en.randomTeleport(mob.getX() + (r.nextDouble() - 0.5) * 32, mob.getY() + r.nextInt(9) - 4, mob.getZ() + (r.nextDouble() - 0.5) * 32, true)) break;
                }
            }
        }
        if ((lvl = TraitTags.get(mob, "zombie_gapple")) > 0 && t >= st.nextGapple && st.gapples < (lvl >= 3 ? 2 : 1)
            && mob.getHealth() < mob.getMaxHealth() * 0.4f) {
            st.gapples++;
            st.nextGapple = t + 900;
            st.healUntil = Math.max(st.healUntil, t + 32);
            eatGapple(level, mob, lvl, r);
        }
        if (t < st.healUntil) {
            if (t % 5 == 0) flee(mob, target, 1.1);
            if (t % 10 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.2, mob.getZ(), 3, 0.3, 0.4, 0.3, 0);
            return;
        }

        if (mob.hurtTime > 0) st.staggerUntil = t + 16;
        if (t < st.staggerUntil) return;

        int crit = TraitTags.get(mob, "zombie_crit");
        int pounce = TraitTags.get(mob, "spider_pounce");
        if (crit > 0 || pounce > 0) {
            if (mob.onGround() && !st.jumping) {
                Vec3 dir = flat(target.position().subtract(mob.position()));
                if (crit > 0 && dist > 1.6 && dist < 3.4 && t >= st.nextAttack - 6 && r.nextFloat() < 0.05f * crit) {
                    if (RookieActions.hesitate(level, mob, "zombie_crit", crit, t)) return;
                    mob.setDeltaMovement(dir.x * 0.32, 0.42, dir.z * 0.32);
                    st.jumping = true;
                    st.jumpAt = t;
                } else if (pounce > 0 && dist > 2.5 && dist < 6.5 && t >= st.nextDash && r.nextFloat() < 0.035f * pounce) {
                    if (RookieActions.hesitate(level, mob, "spider_pounce", pounce, t)) return;
                    double power = 0.35 + dist * 0.07;
                    mob.setDeltaMovement(dir.x * power, 0.38, dir.z * power);
                    st.jumping = true;
                    st.jumpAt = t;
                    st.nextDash = t + 100;
                }
            } else if (st.jumping) {
                if (!mob.onGround() && mob.getDeltaMovement().y < 0 && dist < (pounce > 0 ? 2.0 : 2.4) && t >= st.nextAttack) {
                    critHit(level, mob, target, 1.25 + 0.05 * Math.max(crit, pounce));
                    st.jumping = false;
                    st.nextAttack = t + 50;
                } else if (mob.onGround() && t - st.jumpAt > 4) {
                    st.jumping = false;
                    st.recoveryAt = t;
                    st.recoveryUntil = t + 8;
                }
            }
        }

        int strafe = Math.max(Math.max(TraitTags.get(mob, "zombie_strafe"), TraitTags.get(mob, "spider_strafe")), TraitTags.get(mob, "pillager_strafe"));
        if (strafe > 0 && mob.onGround() && dist > 1.4 && dist < 4.5) {
            if (t >= st.nextStrafeSwitch) {
                st.strafeDir = r.nextBoolean() ? 1 : -1;
                st.nextStrafeSwitch = t + 15 + r.nextInt(25);
            }
            Vec3 to = flat(target.position().subtract(mob.position()));
            Vec3 side = new Vec3(-to.z, 0, to.x).scale(st.strafeDir * (0.018 + 0.008 * strafe));
            mob.setDeltaMovement(mob.getDeltaMovement().add(side));
        }

        if ((lvl = TraitTags.get(mob, "skeleton_volley")) > 0 && t >= st.nextVolley && dist > 4 && dist < 24 && mob.hasLineOfSight(target)) {
            if (RookieActions.hesitate(level, mob, "skeleton_volley", lvl, t)) return;
            st.nextVolley = t + 220 - 30L * lvl;
            shootArrow(level, mob, target, 9f - 1.5f * lvl);
            level.playSound(null, mob.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1f, 1.2f);
            st.recoveryAt = t;
            st.recoveryUntil = t + 7;
        }
        if ((lvl = TraitTags.get(mob, "skeleton_dodge")) > 0 && t >= st.nextDodge && mob.onGround() && target.isUsingItem()
            && (target.getUseItem().is(Items.BOW) || target.getUseItem().is(Items.CROSSBOW) || target.getUseItem().is(Items.TRIDENT))
            && watched(target, mob)) {
            Vec3 to = flat(target.position().subtract(mob.position()));
            int side = r.nextBoolean() ? 1 : -1;
            mob.setDeltaMovement(-to.z * 0.42 * side, 0.28, to.x * 0.42 * side);
            st.nextDodge = t + 90 - 10L * lvl;
        }
        if ((lvl = TraitTags.get(mob, "skeleton_swordsman")) > 0 && mob instanceof AbstractSkeleton sk) {
            if (dist < 3.0 && st.storedWeapon == null && sk.getMainHandItem().is(Items.BOW)) {
                st.storedWeapon = sk.getMainHandItem().copy();
                st.storedWeaponDrop = com.rrm.mobslearn.mob.Compat.dropChance(sk, EquipmentSlot.MAINHAND);
                sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(lvl >= 3 ? Items.IRON_SWORD : lvl == 2 ? Items.STONE_SWORD : Items.WOODEN_SWORD));
                sk.setDropChance(EquipmentSlot.MAINHAND, 0f);
                sk.reassessWeaponGoal();
                level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6f, 1.3f);
            } else if (dist > 7.0 && st.storedWeapon != null) {
                restoreWeapon(mob, st);
            }
        }
        if (TraitTags.get(mob, "witch_kite") > 0 && dist < 5 && t % 10 == 0) flee(mob, target, 1.1);

        if (TraitTags.get(mob, "creeper_flank") > 0 && t % 10 == 0 && dist > 4 && dist < 14 && watched(target, mob)) {
            Vec3 look = flat(target.getLookAngle());
            Vec3 behind = target.position().subtract(look.scale(3));
            mob.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.05);
        }
        if ((lvl = TraitTags.get(mob, "creeper_dash")) > 0 && mob.onGround() && t >= st.nextDash && dist > 3 && dist < 6.5
            && r.nextFloat() < 0.025f * lvl) {
            if (RookieActions.hesitate(level, mob, "creeper_dash", lvl, t)) return;
            Vec3 dir = flat(target.position().subtract(mob.position()));
            mob.setDeltaMovement(dir.x * 0.5, 0.2, dir.z * 0.5);
            st.nextDash = t + 140;
            st.recoveryAt = t + 10;
            st.recoveryUntil = t + 22;
        }
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : f.normalize();
    }

    private static boolean griefing(ServerLevel level) {
        try {
            return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        } catch (Exception e) {
            return true;
        }
    }

    static boolean watched(Player p, net.minecraft.world.entity.Entity e) {
        Vec3 look = p.getViewVector(1f);
        Vec3 dir = e.getEyePosition().subtract(p.getEyePosition());
        double len = dir.length();
        if (len < 1.0E-4) return true;
        return look.dot(dir.scale(1.0 / len)) > 0.93 && p.hasLineOfSight(e);
    }

    private static void flee(Mob mob, LivingEntity from, double speed) {
        Vec3 away = flat(mob.position().subtract(from.position())).scale(8);
        mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, speed);
    }

    public static void critHit(ServerLevel level, Mob mob, LivingEntity target, double multiplier) {
        AttributeInstance atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) com.rrm.mobslearn.mob.Compat.addOrUpdate(atk, com.rrm.mobslearn.mob.Compat.mod(CRIT_ID, multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        boolean hit;
        try {
            hit = mob.doHurtTarget(target);
        } finally {
            if (atk != null) atk.removeModifier(com.rrm.mobslearn.mob.Compat.uuid(CRIT_ID));
        }
        if (hit) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 14, 0.35, 0.35, 0.35, 0.2);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1f, 1f);
        }
    }

    private static void shootArrow(ServerLevel level, Mob mob, LivingEntity target, float inaccuracy) {
        Arrow arrow = new Arrow(level, mob);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + d * 0.2, dz, 1.6f, Math.max(0.5f, inaccuracy));
        level.addFreshEntity(arrow);
    }

    private static void restoreWeapon(Mob mob, State st) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, st.storedWeapon);
        mob.setDropChance(EquipmentSlot.MAINHAND, st.storedWeaponDrop);
        st.storedWeapon = null;
        if (mob instanceof AbstractSkeleton sk) sk.reassessWeaponGoal();
    }

    private static void eatGapple(ServerLevel level, Mob mob, int lvl, RandomSource r) {
        boolean enchanted = lvl >= 3 && r.nextFloat() < 0.08f;
        Builders.present(mob, new ItemStack(enchanted ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE));
        for (int i = 0; i < 4; i++) {
            Behaviors.later(i * 8, () -> {
                if (mob.isAlive()) level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.8f, 0.9f + r.nextFloat() * 0.2f);
            });
        }
        Behaviors.later(32, () -> {
            if (!mob.isAlive()) return;
            Builders.clearPresentation(mob);
            mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, enchanted ? 1 : 0));
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, enchanted ? 160 : 80, enchanted ? 1 : 0));
            level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 1f, 0.9f);
        });
    }
}
