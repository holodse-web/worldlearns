package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class Brawl {
    private static final String[] KINDS = {"cow_kick", "pig_charge", "sheep_ram", "rabbit_bite", "horse_kick", "chicken_eggs"};

    private static final class Fight {
        UUID player;
        long until, nextHit, nextDash, retreatUntil;
        int power, responses;
        String kind;
    }

    private static final Map<UUID, Mob> mobs = new HashMap<>();
    private static final Map<UUID, Fight> fights = new HashMap<>();

    private Brawl() {
    }

    public static void reset() {
        mobs.clear();
        fights.clear();
    }

    public static boolean fighting(Mob m) {
        return fights.containsKey(m.getUUID());
    }

    private static String kindOf(Mob mob) {
        for (String k : KINDS) if (TraitTags.get(mob, k) > 0) return k;
        return null;
    }

    public static void onHurt(ServerLevel level, Mob victim, Player p) {
        if (p.isCreative() || p.isSpectator() || !victim.isAlive()) return;
        String kind = kindOf(victim);
        if ("horse_kick".equals(kind) && victim instanceof AbstractHorse h && h.isTamed()) kind = null;
        if (kind != null) start(level, victim, p, kind, TraitTags.get(victim, kind));
        int herd = TraitTags.get(victim, "cow_herd");
        if (herd > 0) {
            int helpers = 0;
            for (Mob m : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(5 + 3 * herd))) {
                if (m != victim && m.getType() == victim.getType() && m.isAlive() && !fighting(m)) {
                    start(level, m, p, "cow_kick", 1);
                    if (++helpers >= 2) break;
                }
            }
            if (helpers > 0) Milestones.award(p, "herd");
        }
    }

    private static void start(ServerLevel level, Mob mob, Player p, String kind, int lvl) {
        long now = level.getGameTime();
        Fight f = fights.computeIfAbsent(mob.getUUID(), k -> new Fight());
        boolean fresh = f.player == null;
        f.player = p.getUUID();
        f.kind = kind;
        f.power = Math.max(1, lvl);
        f.until = now + 100 + 20L * f.power;
        f.responses = 1;
        f.retreatUntil = 0;
        if (fresh) f.nextHit = now + 10;
        mobs.put(mob.getUUID(), mob);
        mob.setLastHurtByMob(null);
        if (fresh) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    public static void tick(ServerLevel level) {
        if (fights.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Fight>> it = fights.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Fight> e = it.next();
            Mob mob = mobs.get(e.getKey());
            Fight f = e.getValue();
            if (mob == null || !mob.isAlive()) {
                mobs.remove(e.getKey());
                it.remove();
                continue;
            }
            if (mob.level() != level) continue;
            if (!WorldBrain.get().allows(Skill.byId(f.kind))) {
                mob.getNavigation().stop();
                mobs.remove(e.getKey());
                it.remove();
                continue;
            }
            Player p = level.getPlayerByUUID(f.player);
            double dist = p == null ? 999 : mob.distanceTo(p);
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator() || now > f.until || dist > 24) {
                mob.getNavigation().stop();
                mobs.remove(e.getKey());
                it.remove();
                continue;
            }
            try {
                run(level, mob, p, f, dist, now);
            } catch (Exception ignored) {
            }
        }
    }

    private static void run(ServerLevel level, Mob mob, Player p, Fight f, double dist, long now) {
        mob.setLastHurtByMob(null);
        if (f.responses <= 0) {
            if (now < f.retreatUntil && now % 5 == 0) {
                Vec3 away = flat(mob.position().subtract(p.position())).scale(7);
                mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.25);
            }
            return;
        }
        mob.getLookControl().setLookAt(p, 30f, 30f);
        if (f.kind.equals("chicken_eggs")) {
            if (now % 5 == 0) {
                if (dist < 4) {
                    Vec3 away = flat(mob.position().subtract(p.position())).scale(6);
                    mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.4);
                } else if (dist > 9) {
                    mob.getNavigation().moveTo(p, 1.2);
                } else {
                    mob.getNavigation().stop();
                }
            }
            if (now >= f.nextHit && dist < 16 && mob.hasLineOfSight(p)) {
                ThrownEgg egg = new ThrownEgg(level, mob, new ItemStack(Items.EGG));
                double dx = p.getX() - mob.getX(), dz = p.getZ() - mob.getZ();
                double dy = p.getEyeY() - 0.4 - egg.getY();
                egg.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.15, dz, 1.1f, 5f);
                level.addFreshEntity(egg);
                Milestones.award(p, "egg_artillery");
                level.playSound(null, mob.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 1.4f);
                f.nextHit = now + 20 - 4L * f.power;
                f.responses = 0;
                f.retreatUntil = now + 55;
                f.until = f.retreatUntil;
            }
            return;
        }
        double speed = switch (f.kind) {
            case "rabbit_bite" -> 1.6;
            case "horse_kick" -> 1.15;
            case "pig_charge" -> 1.35;
            default -> 1.3;
        };
        if (now % 4 == 0) mob.getNavigation().moveTo(p, speed);
        Vec3 dir = flat(p.position().subtract(mob.position()));
        if (f.kind.equals("pig_charge") && mob.onGround() && dist > 2.5 && dist < 7 && now >= f.nextDash) {
            mob.setDeltaMovement(dir.x * 0.6, 0.18, dir.z * 0.6);
            f.nextDash = now + 70;
            level.playSound(null, mob.blockPosition(), SoundEvents.HOGLIN_ANGRY, SoundSource.NEUTRAL, 0.8f, 1.4f);
        }
        if (f.kind.equals("rabbit_bite") && mob.onGround() && dist > 1.2 && dist < 3.5 && now >= f.nextHit - 6) {
            mob.setDeltaMovement(dir.x * 0.45, 0.42, dir.z * 0.45);
        }
        double reach = mob.getBbWidth() / 2 + 1.4;
        if (dist < reach && now >= f.nextHit) hit(level, mob, p, f, dir, now);
    }

    private static void hit(ServerLevel level, Mob mob, Player p, Fight f, Vec3 dir, long now) {
        int lvl = f.power;
        float dmg;
        double kb, up;
        SoundEvent sound;
        float pitch = 0.8f;
        switch (f.kind) {
            case "cow_kick" -> {
                dmg = 1 + 0.6f * lvl;
                kb = 0.35 + 0.12 * lvl;
                up = 0.25;
                sound = SoundEvents.GOAT_RAM_IMPACT;
                pitch = 0.55f;
            }
            case "pig_charge" -> {
                dmg = lvl >= 3 ? 3 : 2;
                kb = 0.45 + 0.12 * lvl;
                up = 0.22;
                sound = SoundEvents.HOGLIN_ATTACK;
                pitch = 0.6f;
            }
            case "sheep_ram" -> {
                dmg = 0.75f + 0.35f * lvl;
                kb = 0.45 + 0.15 * lvl;
                up = 0.27;
                sound = SoundEvents.GOAT_RAM_IMPACT;
            }
            case "rabbit_bite" -> {
                dmg = lvl;
                kb = 0.15;
                up = 0.1;
                sound = SoundEvents.RABBIT_ATTACK;
                pitch = 1.1f;
            }
            case "horse_kick" -> {
                dmg = 2 + lvl;
                kb = 0.65 + 0.15 * lvl;
                up = 0.3;
                sound = SoundEvents.HORSE_ANGRY;
            }
            default -> {
                return;
            }
        }
        f.nextHit = now + (f.kind.equals("rabbit_bite") ? 14 : 22);
        f.responses = 0;
        f.retreatUntil = now + 55;
        f.until = f.retreatUntil;
        dmg *= switch (WorldBrain.get().difficulty) { case 0 -> 0.85f; case 2 -> 1.1f; default -> 1f; };
        if (p.hurtServer(level, level.damageSources().mobAttack(mob), dmg)) {
            if (f.kind.equals("cow_kick")) Milestones.award(p, "cow_kick");
            if (f.kind.equals("rabbit_bite")) Milestones.award(p, "killer_bunny");
            p.push(dir.x * kb, up, dir.z * kb);
            p.needsSync = true;
            level.playSound(null, p.blockPosition(), sound, SoundSource.NEUTRAL, 1f, pitch);
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.8f, 0.9f);
            level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.3, 0.3, 0.15);
        }
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : f.normalize();
    }
}
