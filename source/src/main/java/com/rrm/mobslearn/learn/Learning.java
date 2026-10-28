package com.rrm.mobslearn.learn;

import com.rrm.mobslearn.mob.Behaviors;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class Learning {
    private static final Map<String, Long> cooldowns = new HashMap<>();

    private Learning() {
    }

    public static void reset() { cooldowns.clear(); chase.clear(); dodge.clear(); }

    private static boolean cooldown(String key, Level level, long ticks) {
        long now = level.getGameTime();
        Long last = cooldowns.get(key);
        if (last != null && now - last < ticks) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 4096) cooldowns.clear();
        return true;
    }

    public static void onDeath(LivingEntity e, DamageSource src) {
        if (!WorldBrain.get().enabled) return;
        if (!(e.level() instanceof ServerLevel level)) return;
        if (e instanceof Player) {
            Entity killer = src.getEntity();
            if (killer != null) WorldBrain.teach(Trigger.KILLED_PLAYER, MobGroup.of(killer.getType()), 1, level, e.blockPosition());
            return;
        }
        MobGroup g = MobGroup.of(e.getType());
        if (g == null) return;
        Player p = src.getEntity() instanceof Player pp ? pp : e.getLastHurtByPlayer();
        boolean fire = src.is(DamageTypeTags.IS_FIRE);
        if (fire) WorldBrain.teach(Trigger.KILL_FIRE, g, 1, level, e.blockPosition());
        if (p == null) {
            if (fire && level.isBrightOutside() && level.canSeeSky(e.blockPosition())) WorldBrain.teach(Trigger.SUN_BURN, g, 1, level, e.blockPosition());
            return;
        }
        WorldBrain.teach(Trigger.KILL_ANY, g, 1, level, e.blockPosition());
        if (src.is(DamageTypeTags.IS_FALL)) WorldBrain.teach(Trigger.KILL_FALL, g, 1, level, e.blockPosition());
        if (src.getDirectEntity() == p) {
            WorldBrain.teach(Trigger.KILL_MELEE, g, 1, level, e.blockPosition());
            ItemStack hand = p.getMainHandItem();
            if (hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES)) WorldBrain.teach(Trigger.KILL_BLADE, g, 1, level, e.blockPosition());
            if (e instanceof Creeper c && c.getSwelling(1f) < 0.05f) WorldBrain.teach(Trigger.KILL_CALM, g, 1, level, e.blockPosition());
        }
        if (src.is(DamageTypeTags.IS_PROJECTILE)) WorldBrain.teach(Trigger.KILL_PROJECTILE, g, 1, level, e.blockPosition());
        if (level.isBrightOutside()) WorldBrain.teach(Trigger.KILL_DAY, g, 1, level, e.blockPosition());
        if (e.isInWater() || p.isInWater()) WorldBrain.teach(Trigger.KILL_IN_WATER, g, 1, level, e.blockPosition());
    }

    public static void onDamage(LivingEntity e, DamageSource src) {
        if (!WorldBrain.get().enabled) return;
        if (!(e.level() instanceof ServerLevel level)) return;
        Entity attacker = src.getEntity();
        if (e instanceof Player player) {
            if (player.isBlocking() && attacker != null) {
                MobGroup g = MobGroup.of(attacker.getType());
                if (g != null && cooldown("block" + player.getUUID() + g, level, 10)) WorldBrain.teach(Trigger.SHIELD_BLOCK, g, 1, level, e.blockPosition());
            }
            return;
        }
        MobGroup g = MobGroup.of(e.getType());
        if (g != null && attacker instanceof Player cp && src.getDirectEntity() == cp && cp.fallDistance > 0 && !cp.onGround()
            && !cp.onClimbable() && !cp.isInWater() && !cp.isPassenger() && cooldown("crit" + cp.getUUID(), level, 10)) {
            WorldBrain.teach(Trigger.PLAYER_CRIT, g, 1, level, e.blockPosition());
        }
        if (g == MobGroup.VILLAGER && attacker instanceof Player p && cooldown("hitv" + p.getUUID(), level, 20)) {
            WorldBrain.teach(Trigger.HIT_VILLAGER, MobGroup.VILLAGER, 1, level, e.blockPosition());
        }
        if (g == MobGroup.ENDERMAN && src.is(DamageTypeTags.IS_DROWNING) && cooldown("wet" + e.getUUID(), level, 40)) {
            WorldBrain.teach(Trigger.WATER_HURT, MobGroup.ENDERMAN, 1, level, e.blockPosition());
        }
    }

    public static InteractionResult useEntity(Player p, Level level, InteractionHand hand, Entity target, EntityHitResult hit) {
        if (!WorldBrain.get().enabled) return InteractionResult.PASS;
        if (level.isClientSide() || !(level instanceof ServerLevel)) return InteractionResult.PASS;
        MobGroup g = MobGroup.of(target.getType());
        if (g == null) return InteractionResult.PASS;
        ItemStack stack = p.getItemInHand(hand);
        String key = p.getUUID() + "/" + target.getUUID();
        switch (g) {
            case COW -> {
                if (stack.is(Items.BUCKET) && cooldown("milk" + key, level, 10)) WorldBrain.teach(Trigger.MILK, g, 1, (ServerLevel) level, target.blockPosition());
            }
            case SHEEP -> {
                if ((stack.is(Items.SHEARS) || stack.getItem() instanceof DyeItem) && cooldown("shear" + key, level, 10)) {
                    WorldBrain.teach(Trigger.SHEAR, g, 1, (ServerLevel) level, target.blockPosition());
                    int balloon = WorldBrain.levelAt("sheep_balloon", (ServerLevel) level, target.blockPosition());
                    if (stack.is(Items.SHEARS) && balloon > 0 && target instanceof LivingEntity sheep && level.getRandom().nextFloat() < 0.3f * balloon) {
                        Behaviors.later(2, () -> sheep.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40 + 20 * balloon, 0)));
                    }
                }
            }
            case CHICKEN -> {
                if (target instanceof net.minecraft.world.entity.animal.Animal a && a.isFood(stack) && cooldown("feedchicken" + key, level, 10)) {
                    WorldBrain.teach(Trigger.FEED, g, 1, (ServerLevel) level, target.blockPosition());
                }
            }
            case PIG -> {
                if (target instanceof net.minecraft.world.entity.animal.Animal a && a.isFood(stack) && cooldown("feedpig" + key, level, 10)) {
                    WorldBrain.teach(Trigger.FEED, g, 1, (ServerLevel) level, target.blockPosition());
                }
            }
            case HORSE -> {
                if (cooldown("horse" + key, level, 40)) WorldBrain.teach(Trigger.FEED, g, 1, (ServerLevel) level, target.blockPosition());
            }
            case WOLF, CAT, PARROT -> {
                if (!stack.isEmpty() && cooldown("feed" + key, level, 10)) WorldBrain.teach(Trigger.FEED, g, 1, (ServerLevel) level, target.blockPosition());
            }
            case VILLAGER -> {
                if (cooldown("trade" + p.getUUID(), level, 60)) WorldBrain.teach(Trigger.TRADE, g, 1, (ServerLevel) level, target.blockPosition());
            }
            default -> {
            }
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult useBlock(Player p, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!WorldBrain.get().enabled) return InteractionResult.PASS;
        if (level.isClientSide() || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        ItemStack stack = p.getItemInHand(hand);
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && !p.isCreative() && !p.isSpectator()) {
            net.minecraft.core.BlockPos clicked = hit.getBlockPos();
            net.minecraft.core.BlockPos place = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
            Behaviors.later(1, () -> {
                if (!level.getBlockState(place).is(bi.getBlock())) return;
                com.rrm.mobslearn.mob.Social.worked(p);
                for (MobGroup wg : new MobGroup[]{MobGroup.ZOMBIE, MobGroup.SKELETON, MobGroup.SPIDER}) {
                    double w = watchedBy(sl, p, wg);
                    if (w > 0 && cooldown("build" + wg + p.getUUID(), level, 10)) WorldBrain.teach(Trigger.SEE_BUILD, wg, w, sl, p.blockPosition());
                }
                if (place.getY() >= p.getY() - 0.5) return;
                boolean under = Math.abs(place.getX() + 0.5 - p.getX()) < 0.9 && Math.abs(place.getZ() + 0.5 - p.getZ()) < 0.9;
                double w = watchedBy(sl, p, MobGroup.ZOMBIE);
                if (w > 0 && cooldown((under ? "pillar" : "bridge") + p.getUUID(), level, 8)) {
                    WorldBrain.teach(under ? Trigger.SEE_PILLAR : Trigger.SEE_BRIDGE, MobGroup.ZOMBIE, w, sl, p.blockPosition());
                }
            });
        }
        if ((stack.is(Items.GLASS_BOTTLE) || stack.is(Items.SHEARS)) && level.getBlockState(hit.getBlockPos()).is(BlockTags.BEEHIVES)) {
            if (cooldown("honey" + p.getUUID(), level, 20)) {
                WorldBrain.teach(Trigger.HONEY, MobGroup.BEE, 1, sl, p.blockPosition());
                Behaviors.angerBees(sl, p);
            }
        }
        return InteractionResult.PASS;
    }

    private static final Map<UUID, double[]> chase = new HashMap<>();

    public static void observeChase(ServerLevel level, Mob mob, Player target, MobGroup g) {
        if (!WorldBrain.get().enabled) return;
        long now = level.getGameTime();
        double dist = mob.distanceTo(target);
        double dy = target.getY() - mob.getY();
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        double[] st = chase.computeIfAbsent(mob.getUUID(), k -> new double[]{dist, now});
        if (dist < st[0] - 0.5) {
            st[0] = dist;
            st[1] = now;
        }
        boolean pillar = dy > 2.2 && horiz < 5 && mob.getNavigation().isDone();
        boolean stuck = dist > 1.8 && dist < 12 && now - st[1] > 80;
        if ((pillar || stuck) && cooldown("unreach" + mob.getUUID(), level, 100)) {
            WorldBrain.teach(Trigger.UNREACHABLE, g, 1, level, mob.blockPosition());
            st[0] = dist;
            st[1] = now;
        }
        if (dist > 22 && cooldown("escape" + mob.getUUID(), level, 300)) {
            WorldBrain.teach(Trigger.ESCAPE, g, 1, level, mob.blockPosition());
        }
        if (chase.size() > 2048) chase.clear();
    }

    public static void afterBreak(Level level, Player p, net.minecraft.core.BlockPos pos) {
        if (!WorldBrain.get().enabled) return;
        if (!(level instanceof ServerLevel sl) || p.isCreative() || p.isSpectator()) return;
        com.rrm.mobslearn.mob.Social.worked(p);
        double w = watchedBy(sl, p, MobGroup.ZOMBIE);
        if (w > 0 && cooldown("dig" + p.getUUID(), level, 10)) WorldBrain.teach(Trigger.SEE_DIG, MobGroup.ZOMBIE, w, sl, pos);
    }

    private static double watchedBy(ServerLevel level, Player p, MobGroup g) {
        double best = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(g == MobGroup.ZOMBIE ? 20 : 16))) {
            if (!m.isAlive() || MobGroup.of(m.getType()) != g || !m.hasLineOfSight(p)) continue;
            if (g != MobGroup.ZOMBIE || com.rrm.mobslearn.mob.Social.observer(m)) return 1;
            best = 0.25;
        }
        return best;
    }

    private static final Map<UUID, double[]> dodge = new HashMap<>();

    public static void observeDodge(ServerLevel level, Mob mob, Player p, MobGroup g) {
        if (!WorldBrain.get().enabled) return;
        long now = level.getGameTime();
        double[] st = dodge.computeIfAbsent(mob.getUUID(), k -> new double[]{p.getX(), p.getZ(), 0, 0});
        double moved = Math.hypot(p.getX() - st[0], p.getZ() - st[1]);
        st[0] = p.getX();
        st[1] = p.getZ();
        boolean close = mob.distanceTo(p) < 3.5;
        boolean untouched = p.tickCount - p.getLastHurtByMobTimestamp() > 30 || p.getLastHurtByMob() != mob;
        if (close && moved > 0.45 && untouched) st[2] += 5;
        else if (!close) st[2] = Math.max(0, st[2] - 5);
        if (st[2] >= 60 && now - st[3] > 100) {
            st[2] = 0;
            st[3] = now;
            WorldBrain.teach(Trigger.DODGE, g, 1, level, mob.blockPosition());
        }
        if (dodge.size() > 2048) dodge.clear();
    }

    public static void observeCrouch(ServerLevel level, Player p) {
        if (!WorldBrain.get().enabled) return;
        if (cooldown("crouch" + p.getUUID(), level, 8)) WorldBrain.teach(Trigger.CROUCH_NEAR, MobGroup.ZOMBIE, 0.5, level, p.blockPosition());
    }

    public static void teachHeal(MobGroup g, ServerLevel level, net.minecraft.core.BlockPos pos) {
        WorldBrain.teach(Trigger.PLAYER_HEAL, g, 1, level, pos);
    }

    public static void onExplode(ServerLevel level, net.minecraft.core.BlockPos pos) {
        WorldBrain.teach(Trigger.EXPLODE, MobGroup.CREEPER, 1, level, pos);
    }
}
