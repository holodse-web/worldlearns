package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The second wave of learned behaviours: sappers, cover-takers, door breakers, ceiling ambushers, burglars, healers and jokes. */
public final class NewSkills {
    private static final String CEILING_TAG = "mobslearn.ceiling", FLOWER_TAG = "mobslearn.flower", GOODS_TAG = "mobslearn.goods";

    private record Hide(UUID player, long until) {
    }

    private static final Map<UUID, BlockPos> ceiling = new HashMap<>();
    private static final Map<UUID, Long> dropping = new HashMap<>();
    private static final Map<UUID, int[]> doorWork = new HashMap<>();
    private static final Map<UUID, Hide> hiding = new HashMap<>();
    private static final Map<UUID, Long> peekUntil = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<String, LinkedHashSet<Long>> built = new HashMap<>();
    private static final int MAX_BUILT = 8192;

    private NewSkills() {
    }

    public static void reset() {
        ceiling.clear();
        dropping.clear();
        doorWork.clear();
        hiding.clear();
        peekUntil.clear();
        cooldowns.clear();
    }

    private static boolean cooldown(String key, long now, long ticks) {
        Long last = cooldowns.get(key);
        if (last != null && now - last < ticks) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 8192) cooldowns.clear();
        return true;
    }

    /** Mobs in the middle of one of these behaviours keep the full tick rate and are not treated as having "lost" their target. */
    public static boolean busy(Mob m) {
        UUID id = m.getUUID();
        return ceiling.containsKey(id) || hiding.containsKey(id) || dropping.containsKey(id);
    }

    /** Skills that only make sense for a particular entity type inside a group should not take a trait slot elsewhere. */
    public static boolean applies(Skill s, Mob mob) {
        if (s.id().equals("drowned_ashore")) return mob.getType() == EntityType.DROWNED;
        if (s.id().equals("zombie_merchant")) return mob.getType() == EntityType.ZOMBIE || mob.getType() == EntityType.HUSK;
        if (s.id().equals("zombie_doorbreaker")) return mob.getType() != EntityType.DROWNED;
        return true;
    }

    private static boolean griefing(ServerLevel level) {
        return Builders.griefing(level);
    }

    private static boolean sheltered(Player p, Level level) {
        BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        for (int k = 1; k <= 6; k++) if (!level.getBlockState(head.above(k)).isAir()) return true;
        return false;
    }

    private static BlockHitResult clip(Level level, Vec3 from, Vec3 to, Entity who) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
    }

    public static void tick(ServerLevel level, Mob mob, MobGroup g, Player target, long t, RandomSource r) {
        UUID id = mob.getUUID();
        int lvl;
        switch (g) {
            case CREEPER -> {
                if (target != null && (lvl = TraitTags.get(mob, "creeper_sapper")) > 0 && mob instanceof Creeper cr) sapper(level, cr, target, lvl, t);
                if ((lvl = TraitTags.get(mob, "creeper_sneeze")) > 0 && mob instanceof Creeper cr2) sneeze(level, cr2, lvl, t, r);
            }
            case SKELETON -> {
                if ((lvl = TraitTags.get(mob, "skeleton_cover")) > 0 || hiding.containsKey(id)) cover(level, mob, target, Math.max(1, lvl), t, r);
            }
            case ZOMBIE -> {
                if (target != null && (lvl = TraitTags.get(mob, "zombie_doorbreaker")) > 0) breakDoor(level, mob, target, lvl);
                else doorWork.remove(id);
                if ((lvl = TraitTags.get(mob, "drowned_ashore")) > 0 && mob.getType() == EntityType.DROWNED) ashore(level, mob, target, lvl, t);
                if (TraitTags.get(mob, "zombie_merchant") > 0) merchantTick(level, mob, t, r);
            }
            case SPIDER -> {
                if ((lvl = TraitTags.get(mob, "spider_ceiling")) > 0 || ceiling.containsKey(id) || dropping.containsKey(id)) ceilingTick(level, mob, target, Math.max(1, lvl), t, r);
            }
            case ENDERMAN -> {
                if (target == null && (lvl = TraitTags.get(mob, "enderman_burglar")) > 0 && mob instanceof EnderMan en) burgle(level, en, lvl, t, r);
            }
            case WITCH -> {
                if ((lvl = TraitTags.get(mob, "witch_healer")) > 0) heal(level, mob, lvl, t);
            }
            case PHANTOM -> {
                if (target != null) {
                    boolean covered = sheltered(target, level);
                    if (covered && mob.distanceTo(target) < 24 && cooldown("phroof" + id, t, 400)) {
                        WorldBrain.teach(com.rrm.mobslearn.learn.Trigger.UNREACHABLE, MobGroup.PHANTOM, 1, level, mob.blockPosition());
                    }
                    if (covered && (lvl = TraitTags.get(mob, "phantom_hunter")) > 0) phantom(level, mob, target, lvl, t);
                }
            }
            default -> {
            }
        }
    }

    // --- Creeper sapper: blows a hole in the wall of a shelter the player hides in -------------------------------------------

    private static void sapper(ServerLevel level, Creeper cr, Player target, int lvl, long t) {
        if (!griefing(level) || cr.isIgnited() || t % 20 != 0) return;
        double dist = cr.distanceTo(target);
        if (dist > 6 + 3 * lvl) return;
        boolean blocked = !cr.hasLineOfSight(target);
        boolean stuck = cr.getNavigation().isDone() && dist > 3.0;
        if (!blocked && !stuck) return;
        BlockHitResult hit = clip(level, cr.getEyePosition(), target.getEyePosition(), cr);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos wall = hit.getBlockPos();
        BlockState ws = level.getBlockState(wall);
        if (ws.isAir() || ws.getDestroySpeed(level, wall) < 0 || ws.getDestroySpeed(level, wall) > 20) return;
        double toWall = Math.sqrt(cr.distanceToSqr(wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 0.5));
        if (toWall < 2.2) {
            if (RookieActions.hesitate(level, cr, "creeper_sapper", lvl, t)) return;
            cr.ignite();
            level.playSound(null, cr.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1f, 0.7f);
        } else if (lvl >= 2) {
            cr.getNavigation().moveTo(wall.getX() + 0.5, wall.getY(), wall.getZ() + 0.5, 1.0 + 0.1 * lvl);
        }
    }

    // --- Creeper sneeze: a tiny harmless-ish pop --------------------------------------------------------------------------

    private static void sneeze(ServerLevel level, Creeper cr, int lvl, long t, RandomSource r) {
        if (t % 20 != 0 || cr.isIgnited() || r.nextFloat() > 0.012f * lvl) return;
        Player near = level.getNearestPlayer(cr, 16);
        if (near == null || !cooldown("sneeze" + cr.getUUID(), t, 600)) return;
        level.playSound(null, cr.blockPosition(), SoundEvents.PANDA_PRE_SNEEZE, SoundSource.HOSTILE, 1f, 1.3f);
        cr.getNavigation().stop();
        Behaviors.later(18, () -> {
            if (!cr.isAlive()) return;
            level.playSound(null, cr.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.HOSTILE, 1.4f, 1.2f);
            level.sendParticles(ParticleTypes.CLOUD, cr.getX(), cr.getY() + 1.2, cr.getZ(), 12, 0.3, 0.2, 0.3, 0.05);
            level.explode(cr, cr.getX(), cr.getY() + 0.5, cr.getZ(), 0.45f + 0.1f * lvl, Level.ExplosionInteraction.NONE);
            for (ServerPlayer sp : level.players()) {
                if (sp.distanceTo(cr) < 10) Net.notice(sp, Component.translatable("mobslearn.msg.sneeze", cr.getDisplayName()));
            }
        });
    }

    // --- Skeleton cover: duck behind a block, then peek out and shoot -------------------------------------------------------

    private static void cover(ServerLevel level, Mob mob, Player target, int lvl, long t, RandomSource r) {
        UUID id = mob.getUUID();
        Hide hide = hiding.get(id);
        if (hide != null) {
            Player p = level.getPlayerByUUID(hide.player());
            if (p == null || !p.isAlive() || p.isSpectator() || p.isCreative() || mob.distanceTo(p) > 40) {
                hiding.remove(id);
                return;
            }
            if (t >= hide.until() || mob.hurtTime > 0) {
                hiding.remove(id);
                peekUntil.put(id, t + 50 + 10L * lvl);
                mob.setTarget(p);
            }
            return;
        }
        if (target == null) return;
        Long peek = peekUntil.get(id);
        if (peek != null && t < peek) return;
        if (!mob.getMainHandItem().is(Items.BOW) || !cooldown("cover" + id, t, 40)) return;
        boolean underFire = mob.tickCount - mob.getLastHurtByMobTimestamp() < 60;
        if (!underFire && r.nextFloat() > 0.25f * lvl) return;
        double dist = mob.distanceTo(target);
        if (dist < 5 || dist > 28) return;
        BlockPos spot = null;
        Vec3 eye = target.getEyePosition();
        for (int i = 0; i < 10 + 4 * lvl && spot == null; i++) {
            BlockPos c = mob.blockPosition().offset(r.nextInt(11) - 5, 0, r.nextInt(11) - 5);
            int y = Camps.groundY(level, c.getX(), c.getZ());
            if (Math.abs(y - mob.getBlockY()) > 2) continue;
            BlockPos feet = new BlockPos(c.getX(), y, c.getZ());
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (feet.distSqr(target.blockPosition()) < 25) continue;
            Vec3 head = new Vec3(feet.getX() + 0.5, feet.getY() + 1.6, feet.getZ() + 0.5);
            if (clip(level, eye, head, target).getType() != HitResult.Type.BLOCK) continue;
            spot = feet;
        }
        if (spot == null) return;
        hiding.put(id, new Hide(target.getUUID(), t + 30 + r.nextInt(30)));
        mob.setTarget(null);
        mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.2 + 0.05 * lvl);
    }

    // --- Zombie door breaker ----------------------------------------------------------------------------------------------

    private static void breakDoor(ServerLevel level, Mob mob, Player target, int lvl) {
        if (!griefing(level)) return;
        if (mob.hasLineOfSight(target) && mob.distanceTo(target) < 2.5) return;
        BlockPos base = mob.blockPosition();
        Vec3 toward = target.position().subtract(mob.position());
        BlockPos door = null;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.OPEN)) continue;
            boolean wooden = s.is(BlockTags.WOODEN_DOORS);
            if (!wooden && lvl < 3) continue;
            Vec3 d = new Vec3(p.getX() + 0.5 - mob.getX(), 0, p.getZ() + 0.5 - mob.getZ());
            if (d.lengthSqr() > 1.0E-4 && d.normalize().dot(new Vec3(toward.x, 0, toward.z).normalize()) < 0.2) continue;
            door = p.immutable();
            break;
        }
        if (door == null) {
            doorWork.remove(mob.getUUID());
            return;
        }
        int[] st = doorWork.get(mob.getUUID());
        if (st == null || st[0] != door.getX() || st[1] != door.getY() || st[2] != door.getZ()) {
            st = new int[]{door.getX(), door.getY(), door.getZ(), 0};
            doorWork.put(mob.getUUID(), st);
        }
        boolean iron = !level.getBlockState(door).is(BlockTags.WOODEN_DOORS);
        int need = (iron ? 360 : 180) / lvl;
        st[3] += 5;
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5);
        if (st[3] % 20 == 0) {
            level.playSound(null, door, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            Builders.swing(mob);
        }
        if (st[3] >= need) {
            level.destroyBlockProgress(mob.getId(), door, -1);
            level.destroyBlock(door, false, mob);
            level.playSound(null, door, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1f, 1f);
            doorWork.remove(mob.getUUID());
        } else {
            level.destroyBlockProgress(mob.getId(), door, Math.min(9, st[3] * 10 / need));
        }
    }

    // --- Drowned that follow you ashore -----------------------------------------------------------------------------------

    private static void ashore(ServerLevel level, Mob mob, Player target, int lvl, long t) {
        if (t % 10 != 0) return;
        Player p = target;
        if (p == null) {
            Player near = level.getNearestPlayer(mob, 10 + 4 * lvl);
            if (near == null || near.isCreative() || near.isSpectator() || near.isInWater()) return;
            if (!mob.hasLineOfSight(near)) return;
            p = near;
            mob.setTarget(p);
        }
        if (p.isInWater()) return;
        mob.getNavigation().moveTo(p, 1.0 + 0.1 * lvl);
        if (mob.distanceTo(p) < 2.0 && cooldown("ashorehit" + mob.getUUID(), t, 20)) Combat.critHit(level, mob, p, 1.0);
    }

    // --- Spider ceiling ambush --------------------------------------------------------------------------------------------

    private static void release(Mob mob) {
        ceiling.remove(mob.getUUID());
        mob.setNoGravity(false);
        mob.removeTag(CEILING_TAG);
    }

    private static void ceilingTick(ServerLevel level, Mob mob, Player target, int lvl, long t, RandomSource r) {
        UUID id = mob.getUUID();
        Long dropAt = dropping.get(id);
        if (dropAt != null) {
            Player p = target;
            if (p != null && mob.distanceTo(p) < 2.2 && cooldown("drop" + id, t, 40)) {
                Combat.critHit(level, mob, p, 1.3 + 0.1 * lvl);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40 + 20 * lvl, 0), mob);
                dropping.remove(id);
            } else if (t - dropAt > 40 || mob.onGround() && t - dropAt > 10) {
                dropping.remove(id);
            }
            return;
        }
        BlockPos at = ceiling.get(id);
        if (at != null) {
            BlockPos roof = at.above();
            if (!mob.isAlive() || target != null || mob.hurtTime > 0 || level.getBlockState(roof).isAir() || TraitTags.get(mob, "spider_ceiling") <= 0
                || level.isDay() && level.canSeeSky(at)) {
                release(mob);
                return;
            }
            double wantY = roof.getY() - mob.getBbHeight() - 0.02;
            if (mob.getY() < wantY - 0.05) mob.setDeltaMovement(0, 0.12, 0);
            else {
                mob.setDeltaMovement(0, 0, 0);
                mob.setPos(mob.getX(), wantY, mob.getZ());
            }
            mob.getNavigation().stop();
            for (Player p : level.players()) {
                if (p.isCreative() || p.isSpectator()) continue;
                double dx = p.getX() - mob.getX(), dz = p.getZ() - mob.getZ(), dy = mob.getY() - p.getY();
                if (dx * dx + dz * dz < 2.2 * 2.2 && dy > 0.5 && dy < 8) {
                    release(mob);
                    mob.setDeltaMovement(dx * 0.15, -0.5, dz * 0.15);
                    mob.setTarget(p);
                    dropping.put(id, t);
                    level.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
                    return;
                }
            }
            return;
        }
        if (target != null || t % 20 != 0 || !mob.onGround() || r.nextFloat() > 0.08f * lvl) return;
        BlockPos feet = mob.blockPosition();
        if (level.canSeeSky(feet) || level.getMaxLocalRawBrightness(feet) > 9) return;
        if (level.getNearestPlayer(mob, 24) == null) return;
        for (int k = 3; k <= 6; k++) {
            BlockPos roof = feet.above(k);
            BlockState s = level.getBlockState(roof);
            if (s.isAir()) continue;
            if (!s.isCollisionShapeFullBlock(level, roof)) return;
            for (int j = 1; j < k; j++) if (!level.getBlockState(feet.above(j)).isAir()) return;
            ceiling.put(id, roof.below());
            mob.setNoGravity(true);
            mob.addTag(CEILING_TAG);
            return;
        }
    }

    // --- EnderMan burglar: pinches blocks out of player builds ------------------------------------------------------------

    public static void playerPlaced(ServerLevel level, BlockPos pos) {
        LinkedHashSet<Long> set = built.computeIfAbsent(level.dimension().toString(), k -> new LinkedHashSet<>());
        set.add(pos.asLong());
        if (set.size() > MAX_BUILT) {
            Iterator<Long> it = set.iterator();
            it.next();
            it.remove();
        }
    }

    private static boolean tp(EnderMan en, double x, double y, double z) {
        return en.randomTeleport(x, y, z, true);
    }

    private static void burgle(ServerLevel level, EnderMan en, int lvl, long t, RandomSource r) {
        if (!griefing(level) || en.getCarriedBlock() != null || t % 100 != 0 || r.nextFloat() > 0.12f * lvl) return;
        LinkedHashSet<Long> set = built.get(level.dimension().toString());
        if (set == null || set.isEmpty()) return;
        BlockPos best = null;
        double bd = 24 * 24;
        List<Long> stale = new ArrayList<>();
        for (Long l : set) {
            BlockPos p = BlockPos.of(l);
            double d = en.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d >= bd) continue;
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(level, p) < 0 || s.getDestroySpeed(level, p) > 5 || !s.getFluidState().isEmpty()) {
                stale.add(l);
                continue;
            }
            if (!level.getBlockState(p.above()).isAir()) continue;
            best = p;
            bd = d;
        }
        set.removeAll(stale);
        if (best == null || !cooldown("burgle" + en.getUUID(), t, 1200)) return;
        BlockState take = level.getBlockState(best);
        if (!tp(en, best.getX() + 0.5, best.getY() + 1, best.getZ() + 0.5)) return;
        level.removeBlock(best, false);
        en.setCarriedBlock(take.getBlock().defaultBlockState());
        set.remove(best.asLong());
        level.playSound(null, best, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.4f);
        level.sendParticles(ParticleTypes.PORTAL, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.4);
        Component what = take.getBlock().getName();
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(best.getX(), best.getY(), best.getZ()) < 48 * 48) Net.notice(sp, Component.translatable("mobslearn.msg.burglar", what));
        }
        for (int i = 0; i < 8; i++) {
            if (tp(en, en.getX() + (r.nextDouble() - 0.5) * 32, en.getY() + r.nextInt(9) - 4, en.getZ() + (r.nextDouble() - 0.5) * 32)) break;
        }
    }

    // --- Witch healer: patches up nearby monsters -------------------------------------------------------------------------

    private static void heal(ServerLevel level, Mob witch, int lvl, long t) {
        if (t % 20 != 0 || !cooldown("witchheal" + witch.getUUID(), t, 100 - 15L * lvl)) return;
        LivingEntity best = null;
        float worst = 0.65f;
        for (Mob m : level.getEntitiesOfClass(Mob.class, witch.getBoundingBox().inflate(8))) {
            if (m == witch || !m.isAlive()) continue;
            MobGroup g = MobGroup.of(m.getType());
            if (g == null || !g.hostile) continue;
            float frac = m.getHealth() / m.getMaxHealth();
            if (frac < worst && witch.hasLineOfSight(m)) {
                worst = frac;
                best = m;
            }
        }
        if (best == null) return;
        witch.getLookControl().setLookAt(best, 30f, 30f);
        Builders.swing(witch);
        best.heal(3f + 2f * lvl);
        level.playSound(null, witch.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1f, 0.9f);
        level.playSound(null, best.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 0.8f, 1.1f);
        level.sendParticles(ParticleTypes.HEART, best.getX(), best.getY() + best.getBbHeight() + 0.2, best.getZ(), 3 + lvl, 0.3, 0.2, 0.3, 0.0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, best.getX(), best.getY() + 0.8, best.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
    }

    // --- Phantom hunter: swoops in from the side when you hide under a roof -----------------------------------------------

    private static void phantom(ServerLevel level, Mob ph, Player target, int lvl, long t) {
        Vec3 eye = target.getEyePosition();
        Vec3 side = null;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + (t / 200) * 0.7;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 out = eye.add(d.scale(5));
            if (clip(level, eye, out, target).getType() == HitResult.Type.MISS) {
                side = eye.add(d.scale(3));
                break;
            }
        }
        if (side == null) return;
        double dist = ph.distanceTo(target);
        Vec3 goal = dist > 4 ? side : eye;
        Vec3 dir = goal.subtract(ph.position());
        if (dir.lengthSqr() < 1.0E-4) return;
        Vec3 v = dir.normalize().scale(0.35 + 0.08 * lvl);
        ph.setDeltaMovement(v);
        ph.setYRot((float) (Math.atan2(v.z, v.x) * 180 / Math.PI) - 90f);
        if (dist < 2.2 && ph.hasLineOfSight(target) && cooldown("phhit" + ph.getUUID(), t, 30)) {
            Combat.critHit(level, ph, target, 1.0);
            ph.setDeltaMovement(-v.x, 0.6, -v.z);
        }
    }

    // --- Zombie merchant --------------------------------------------------------------------------------------------------

    private static final net.minecraft.world.item.Item[][] GOODS = {
        {Items.ROTTEN_FLESH, Items.POTATO, Items.CARROT, Items.STRING, Items.BONE, Items.STICK, Items.GLASS_BOTTLE, Items.BREAD},
        {Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COOKED_BEEF, Items.ARROW, Items.LEAD, Items.APPLE},
        {Items.IRON_INGOT, Items.GOLDEN_CARROT, Items.NAME_TAG, Items.EXPERIENCE_BOTTLE, Items.DIAMOND}};

    public static void setupMerchant(Mob mob, int lvl, RandomSource r) {
        if (!mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) return;
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD));
        TraitTags.set(mob, "merchant_left", 2 + lvl);
        mob.addTag(GOODS_TAG);
    }

    private static boolean merchant(Mob m) {
        return m.getTags().contains(GOODS_TAG) && TraitTags.get(m, "zombie_merchant") > 0;
    }

    private static void merchantTick(ServerLevel level, Mob mob, long t, RandomSource r) {
        if (!merchant(mob) || t % 20 != 0) return;
        Player p = level.getNearestPlayer(mob, 6);
        if (p instanceof ServerPlayer sp && !sp.isSpectator() && cooldown("pitch" + mob.getUUID() + sp.getUUID(), t, 1200)) {
            mob.getLookControl().setLookAt(sp, 30f, 30f);
            level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1f, 1.4f);
            Net.notice(sp, Component.translatable("mobslearn.merchant.offer." + r.nextInt(3), mob.getDisplayName()));
        }
    }

    public static InteractionResult interact(Player player, Level lvl, InteractionHand hand, Entity entity) {
        if (lvl.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Mob mob) || !(lvl instanceof ServerLevel level)) return InteractionResult.PASS;
        if (!WorldBrain.get().enabled || !merchant(mob) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        ItemStack hold = player.getMainHandItem();
        if (!hold.is(Items.EMERALD)) {
            Net.notice(sp, Component.translatable("mobslearn.merchant.need", mob.getDisplayName()));
            return InteractionResult.SUCCESS;
        }
        int rank = Math.max(1, TraitTags.get(mob, "zombie_merchant"));
        RandomSource r = level.getRandom();
        int tier = r.nextFloat() < 0.15f * rank ? Math.min(2, rank - 1 + (r.nextFloat() < 0.3f ? 1 : 0)) : 0;
        net.minecraft.world.item.Item[] pool = GOODS[Math.max(0, Math.min(2, tier))];
        net.minecraft.world.item.Item item = pool[r.nextInt(pool.length)];
        if (item == Items.DIAMOND && r.nextFloat() > 0.3f) item = Items.IRON_INGOT;
        ItemStack deal = new ItemStack(item, item == Items.ARROW ? 4 + r.nextInt(5) : item == Items.ROTTEN_FLESH ? 3 : 1);
        if (!player.isCreative()) hold.shrink(1);
        level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), deal));
        level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.HOSTILE, 1f, 0.6f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.8, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
        Net.notice(sp, Component.translatable("mobslearn.merchant.deal." + r.nextInt(3), mob.getDisplayName(), deal.getHoverName()));
        int left = TraitTags.raw(mob, "merchant_left") - 1;
        TraitTags.set(mob, "merchant_left", Math.max(0, left));
        if (left <= 0) {
            mob.removeTag(GOODS_TAG);
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            Net.notice(sp, Component.translatable("mobslearn.merchant.closed", mob.getDisplayName()));
        }
        return InteractionResult.SUCCESS;
    }

    /** A zombie that is still in business does not attack customers (unless they hit it first). */
    public static boolean refusesTarget(Mob m, LivingEntity target) {
        return target instanceof Player p && merchant(m) && m.getLastHurtByMob() != p;
    }

    // --- Skeleton flowers: some arrows turn into a bouquet ----------------------------------------------------------------

    public static void onEntityLoad(Entity e, ServerLevel level) {
        if (e instanceof Mob mob && mob.getTags().contains(CEILING_TAG) && !ceiling.containsKey(mob.getUUID())) {
            mob.setNoGravity(false);
            mob.removeTag(CEILING_TAG);
        }
        if (!(e instanceof Projectile proj) || !(proj.getOwner() instanceof Mob shooter)) return;
        int lvl = TraitTags.get(shooter, "skeleton_flowers");
        if (lvl > 0 && level.getRandom().nextFloat() < 0.12f * lvl) proj.addTag(FLOWER_TAG);
    }

    private static final net.minecraft.world.item.Item[] FLOWERS = {Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.AZURE_BLUET,
        Items.OXEYE_DAISY, Items.ALLIUM, Items.BLUE_ORCHID, Items.LILY_OF_THE_VALLEY};

    /** @return true when the hit was a flower arrow (damage must be cancelled). */
    public static boolean flowerHit(LivingEntity victim, Entity direct) {
        if (direct == null || !direct.getTags().contains(FLOWER_TAG) || !(victim.level() instanceof ServerLevel level)) return false;
        RandomSource r = level.getRandom();
        ItemStack bouquet = new ItemStack(FLOWERS[r.nextInt(FLOWERS.length)]);
        level.addFreshEntity(new ItemEntity(level, victim.getX(), victim.getY() + 0.6, victim.getZ(), bouquet));
        level.sendParticles(ParticleTypes.HEART, victim.getX(), victim.getY() + victim.getBbHeight(), victim.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, victim.blockPosition(), SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.HOSTILE, 1f, 1.3f);
        if (victim instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.msg.flowers"));
        direct.discard();
        return true;
    }

    public static void onUnload(Entity e) {
        if (e instanceof Mob mob && ceiling.containsKey(mob.getUUID())) release(mob);
        if (e instanceof Mob mob) {
            UUID id = mob.getUUID();
            hiding.remove(id);
            peekUntil.remove(id);
            doorWork.remove(id);
            dropping.remove(id);
        }
    }
}
