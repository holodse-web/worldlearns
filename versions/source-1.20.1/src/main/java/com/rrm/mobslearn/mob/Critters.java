package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.net.Net;
import com.rrm.mobslearn.learn.MobGroup;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Critters {
    private static final Map<UUID, Long> golemCalls = new HashMap<>();

    private Critters() {
    }

    public static void reset() {
        golemCalls.clear();
    }

    public static void mobHurt(ServerLevel level, Mob victim, Player p) {
        Colonies.residentHurt(level, victim, p);
        if (p instanceof ServerPlayer sp && (victim instanceof net.minecraft.world.entity.npc.Villager || Colonies.resident(victim)
            || victim instanceof net.minecraft.world.entity.animal.IronGolem)) Colonies.offend(level, victim.blockPosition(), sp, 4);
        WorldSkills.hurt(level, victim, p);
        if (p.isCreative() || p.isSpectator()) return;
        Brawl.onHurt(level, victim, p);
        RandomSource r = level.getRandom();
        int lvl;
        if ((lvl = TraitTags.get(victim, "villager_slap")) > 0 && r.nextFloat() < 0.25f * lvl && victim.distanceTo(p) < 3.2) {
            Behaviors.later(6, () -> {
                if (!victim.isAlive() || !p.isAlive() || victim.distanceTo(p) > 3.5) return;
                if (p.hurt(level.damageSources().mobAttack(victim), 1f)) {
                    Vec3 d = flat(p.position().subtract(victim.position()));
                    p.push(d.x * 0.7, 0.25, d.z * 0.7);
                    p.hurtMarked = true;
                    level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
                    level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1f, 1.3f);
                    if (p instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.msg.slap", victim.getDisplayName()));
                    com.rrm.mobslearn.learn.Milestones.award(p, "slapped");
                }
            });
        }
        if ((lvl = TraitTags.get(victim, "villager_golem")) > 0 && r.nextFloat() < 0.1f * lvl) {
            long now = level.getGameTime();
            Long last = golemCalls.get(p.getUUID());
            if (last == null || now - last > 2400) {
                golemCalls.put(p.getUUID(), now);
                IronGolem golem = EntityType.IRON_GOLEM.create(level);
                if (golem != null) {
                    golem.addTag(TraitTags.SEEN);
                    double a = r.nextDouble() * Math.PI * 2;
                    golem.moveTo(victim.getX() + Math.cos(a) * 3, victim.getY(), victim.getZ() + Math.sin(a) * 3, r.nextFloat() * 360f, 0f);
                    level.addFreshEntity(golem);
                    golem.setTarget(p);
                    level.playSound(null, victim.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2f, 1f);
                    level.sendParticles(ParticleTypes.CLOUD, golem.getX(), golem.getY() + 1, golem.getZ(), 20, 0.6, 1, 0.6, 0.02);
                    if (p instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.msg.golem", victim.getDisplayName()));
                }
            }
        }
    }

    public static void playerHurt(ServerLevel level, Player p, Entity attacker) {
        int lvl;
        if ((lvl = TraitTags.get(attacker, "golem_throw")) > 0) {
            int l = lvl;
            Behaviors.later(1, () -> {
                p.push(0, 0.3 * l, 0);
                p.hurtMarked = true;
            });
            com.rrm.mobslearn.learn.Milestones.award(p, "sky_toss");
        }
        if ((lvl = TraitTags.get(attacker, "bee_swarm")) > 0 && !p.isCreative()) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(4 + 3 * lvl))) {
                if (MobGroup.of(m.getType()) != MobGroup.BEE || !(m instanceof NeutralMob bee) || m == attacker) continue;
                bee.setPersistentAngerTarget(p.getUUID());
                bee.startPersistentAngerTimer();
                m.setTarget(p);
            }
        }
        if ((lvl = TraitTags.get(attacker, "witch_brew")) > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), attacker);
            if (lvl >= 2) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0), attacker);
            if (lvl >= 3) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), attacker);
        }
    }

    public static boolean allowDeath(LivingEntity e, DamageSource src) {
        int lvl = TraitTags.get(e, "cat_ninelives");
        if (lvl <= 0 || !(e.level() instanceof ServerLevel level)) return true;
        int used = TraitTags.get(e, "lives_used");
        if (used >= lvl) return true;
        TraitTags.set(e, "lives_used", used + 1);
        e.setHealth(Math.max(2f, e.getMaxHealth() * 0.5f));
        e.clearFire();
        level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.4, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        RandomSource r = level.getRandom();
        for (int i = 0; i < 10; i++) {
            BlockPos at = e.blockPosition().offset(r.nextInt(13) - 6, r.nextInt(5) - 2, r.nextInt(13) - 6);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isSolid()) {
                e.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                break;
            }
        }
        level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 2f);
        com.rrm.mobslearn.learn.Milestones.nearby(level, e.blockPosition(), "nine_lives");
        return false;
    }

    public static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        int lvl = TraitTags.get(e, "slime_split");
        if (lvl > 0 && e instanceof Slime && src.getEntity() instanceof Player) {
            for (int i = 0; i < lvl; i++) {
                Slime s = EntityType.SLIME.create(level);
                if (s == null) continue;
                s.setSize(1, true);
                s.addTag(TraitTags.SEEN);
                s.moveTo(e.getX() + level.getRandom().nextGaussian() * 0.5, e.getY() + 0.3, e.getZ() + level.getRandom().nextGaussian() * 0.5,
                    level.getRandom().nextFloat() * 360f, 0f);
                level.addFreshEntity(s);
            }
        }
    }

    public static void tick(ServerLevel level, Mob mob, MobGroup g, long t, RandomSource r) {
        int lvl;
        switch (g) {
            case IRON_GOLEM -> {
                if ((lvl = TraitTags.get(mob, "golem_flowers")) > 0 && t % 100 == 0 && mob.getTarget() == null && r.nextFloat() < 0.2f * lvl
                    && mob instanceof IronGolem golem) {
                    Player p = level.getNearestPlayer(mob, 6);
                    if (p != null && !p.isSpectator()) {
                        golem.offerFlower(true);
                        Behaviors.later(60, () -> golem.offerFlower(false));
                        Vec3 d = flat(p.position().subtract(mob.position()));
                        ItemEntity flower = new ItemEntity(level, mob.getX() + d.x, mob.getY() + 1.6, mob.getZ() + d.z, new ItemStack(Items.POPPY));
                        flower.setDeltaMovement(d.x * 0.2, 0.15, d.z * 0.2);
                        level.addFreshEntity(flower);
                        level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 2.8, mob.getZ(), 3, 0.4, 0.2, 0.4, 0);
                    }
                }
            }
            case PARROT -> {
                if ((lvl = TraitTags.get(mob, "parrot_warn")) > 0 && t % 40 == 0 && level.getNearestPlayer(mob, 10) != null) {
                    boolean any = false;
                    for (Monster m : level.getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(4 + 4 * lvl))) {
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.25, m.getZ(), 2, 0.15, 0.15, 0.15, 0);
                        any = true;
                    }
                    if (any && r.nextFloat() < 0.35f) level.playSound(null, mob.blockPosition(), SoundEvents.PARROT_IMITATE_ZOMBIE, SoundSource.NEUTRAL, 1.2f, 1.6f);
                }
            }
            case BEE -> {
                if ((lvl = TraitTags.get(mob, "bee_pollinate")) > 0 && t % 60 == 0 && r.nextFloat() < 0.2f * lvl) {
                    BlockPos base = mob.blockPosition();
                    outer:
                    for (int dy = 0; dy >= -3; dy--) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos at = base.offset(dx, dy, dz);
                                BlockState st = level.getBlockState(at);
                                if (st.getBlock() instanceof CropBlock crop && !crop.isMaxAge(st)) {
                                    level.setBlockAndUpdate(at, crop.getStateForAge(crop.getAge(st) + 1));
                                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
            case CREEPER -> {
                if ((lvl = TraitTags.get(mob, "creeper_ambush")) > 0 && mob.getTarget() == null && level.isNight()) {
                    if (TraitTags.get(mob, "hid") > 0) {
                        mob.getNavigation().stop();
                    } else if (t % 40 == 0 && mob.onGround() && r.nextFloat() < 0.3f && griefing(level)) {
                        Player near = level.getNearestPlayer(mob, 28);
                        if (near != null && near.distanceTo(mob) > 10) {
                            java.util.List<net.minecraft.core.Direction> dirs = new java.util.ArrayList<>(net.minecraft.core.Direction.Plane.HORIZONTAL.stream().toList());
                            java.util.Collections.shuffle(dirs, new java.util.Random(r.nextLong()));
                            int sides = Math.min(3, lvl + 1);
                            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
                            BlockPos feet = mob.blockPosition();
                            for (int i = 0; i < sides; i++) {
                                BlockPos side = feet.relative(dirs.get(i));
                                for (int dy = 0; dy <= (lvl >= 2 ? 1 : 0); dy++) {
                                    BlockPos at = side.above(dy);
                                    if (level.getBlockState(at).isAir() && level.getBlockState(side.below()).isSolid()) level.setBlockAndUpdate(at, leaves);
                                }
                            }
                            level.playSound(null, feet, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.HOSTILE, 0.6f, 1f);
                            TraitTags.set(mob, "hid", 1);
                            mob.getNavigation().stop();
                        }
                    }
                }
            }
            case CHICKEN -> {
                if ((lvl = TraitTags.get(mob, "chicken_nest")) > 0 && t % 200 == 0 && r.nextFloat() < 0.35f) {
                    BlockPos nest = null;
                    for (BlockPos at : BlockPos.betweenClosed(mob.blockPosition().offset(-6, -2, -6), mob.blockPosition().offset(6, 2, 6))) {
                        if (level.getBlockState(at).is(Blocks.HAY_BLOCK)) {
                            nest = at.immutable();
                            break;
                        }
                    }
                    if (nest == null) {
                        BlockPos feet = mob.blockPosition();
                        BlockState below = level.getBlockState(feet.below());
                        if (griefing(level) && mob.onGround() && level.getBlockState(feet).isAir() && (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT))) {
                            level.setBlockAndUpdate(feet.below(), Blocks.HAY_BLOCK.defaultBlockState());
                            level.playSound(null, feet, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 1f, 1.2f);
                        }
                    } else if (mob.distanceToSqr(nest.getX() + 0.5, nest.getY() + 1, nest.getZ() + 0.5) < 9 && r.nextFloat() < 0.15f * lvl) {
                        Item item = lvl >= 3 && r.nextFloat() < 0.08f ? Items.GOLD_NUGGET : Items.EGG;
                        level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY() + 0.3, mob.getZ(), new ItemStack(item)));
                        level.playSound(null, mob.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 1f);
                    } else if (mob.getNavigation().isDone()) {
                        mob.getNavigation().moveTo(nest.getX() + 0.5, nest.getY() + 1, nest.getZ() + 0.5, 1.0);
                    }
                }
            }
            case PIG -> {
                if ((lvl = TraitTags.get(mob, "pig_truffle")) > 0 && t % 100 == 0 && mob.onGround() && r.nextFloat() < 0.08f * lvl
                    && !Brawl.fighting(mob) && level.getNearestPlayer(mob, 16) != null) {
                    BlockState below = level.getBlockState(mob.blockPosition().below());
                    if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.PODZOL) || below.is(Blocks.COARSE_DIRT)) {
                        Item[] pool = lvl >= 3 ? new Item[]{Items.CARROT, Items.POTATO, Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.EMERALD, Items.GOLDEN_CARROT}
                            : lvl == 2 ? new Item[]{Items.CARROT, Items.POTATO, Items.BEETROOT, Items.GOLD_NUGGET, Items.IRON_NUGGET}
                            : new Item[]{Items.CARROT, Items.POTATO, Items.BEETROOT, Items.BROWN_MUSHROOM};
                        ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.3, mob.getZ(), new ItemStack(pool[r.nextInt(pool.length)]));
                        item.setDeltaMovement((r.nextDouble() - 0.5) * 0.2, 0.25, (r.nextDouble() - 0.5) * 0.2);
                        level.addFreshEntity(item);
                        level.playSound(null, mob.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 0.4, mob.getZ(), 6, 0.3, 0.2, 0.3, 0);
                    }
                }
            }
            default -> {
            }
        }
        if ((t / 5 + mob.getId()) % 20 == 0) Traits.relearn(level, mob);
    }

    private static boolean griefing(ServerLevel level) {
        try {
            return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        } catch (Exception e) {
            return true;
        }
    }

    public static void climb(Mob mob) {
        if (!mob.horizontalCollision || mob.isPassenger() || (mob.getNavigation().isDone() && mob.getTarget() == null)) return;
        float yaw = mob.getYRot() * ((float) Math.PI / 180f);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        if (mob.isInWater()) {
            mob.setDeltaMovement(fx * 0.18, 0.45, fz * 0.18);
            return;
        }
        if (!mob.onGround()) return;
        BlockPos feet = mob.blockPosition();
        BlockPos front = BlockPos.containing(mob.getX() + fx * 0.9, mob.getY() + 0.2, mob.getZ() + fz * 0.9);
        if (front.equals(feet)) return;
        var level = mob.level();
        boolean wall1 = level.getBlockState(front).isSolid(), wall2 = level.getBlockState(front.above()).isSolid();
        boolean open = !level.getBlockState(front.above(2)).isSolid() && !level.getBlockState(front.above(3)).isSolid()
            && !level.getBlockState(feet.above(2)).isSolid() && !level.getBlockState(feet.above(3)).isSolid();
        if (wall1 && wall2 && open) mob.setDeltaMovement(fx * 0.12, 0.62, fz * 0.12);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : f.normalize();
    }
}
