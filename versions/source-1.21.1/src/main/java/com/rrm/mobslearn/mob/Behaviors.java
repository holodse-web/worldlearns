package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.net.Net;
import com.rrm.mobslearn.learn.Learning;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.WorldBrain;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

public final class Behaviors {
    private static final float[] SHIELD_BLOCK = {0f, 0.12f, 0.2f, 0.28f};
    private static final float[] DIG_HARDNESS = {0f, 0.8f, 2.0f, 3.5f};
    private static final int[] XYLOPHONE = {0, 2, 4, 5, 7, 9, 11, 12};

    private record Scheduled(long due, Runnable task) {
    }

    private static final List<Scheduled> scheduled = new ArrayList<>();
    private static long serverTicks;
    private static final Map<UUID, Boolean> sneaking = new HashMap<>();
    private static final Map<UUID, Integer> lastTarget = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, int[]> digging = new HashMap<>();

    private Behaviors() {
    }

    private static boolean cooldown(String key, long now, long ticks) {
        Long last = cooldowns.get(key);
        if (last != null && now - last < ticks) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 8192) cooldowns.clear();
        return true;
    }

    public static void later(int ticks, Runnable r) {
        scheduled.add(new Scheduled(serverTicks + ticks, r));
    }

    public static void serverTick(MinecraftServer server) {
        serverTicks++;
        if (scheduled.isEmpty()) return;
        List<Scheduled> due = new ArrayList<>();
        for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext(); ) {
            Scheduled s = it.next();
            if (s.due <= serverTicks) {
                due.add(s);
                it.remove();
            }
        }
        for (Scheduled s : due) {
            try {
                s.task.run();
            } catch (Exception ignored) {
            }
        }
    }

    public static void reset() {
        Combat.reset();
        Brawl.reset();
        VillageDefense.reset();
        Builders.reset();
        RookieActions.reset();
        Critters.reset();
        scheduled.clear();
        sneaking.clear();
        lastTarget.clear();
        cooldowns.clear();
        digging.clear();
        Social.reset();
    }

    public static void levelTick(ServerLevel level) {
        Combat.tick(level);
        Brawl.tick(level);
        Camps.tick(level);
        Colonies.tick(level);
        Social.tick(level);
        Chatter.tick(level, level.getGameTime());
        long t = level.getGameTime();
        if (t % 5 != 0) return;
        Set<Mob> done = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (p.isUsingItem() && p.getUseItem().has(DataComponents.FOOD) && cooldown("heal" + p.getUUID(), t, 30)) {
                Set<MobGroup> hunters = new HashSet<>();
                for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16))) {
                    MobGroup mg = MobGroup.of(m.getType());
                    if (mg != null && mg.hostile && m.getTarget() == p) hunters.add(mg);
                }
                for (MobGroup mg : hunters) Learning.teachHeal(mg, level, p.blockPosition());
            }
            boolean sneak = p.isShiftKeyDown();
            Boolean prev = sneaking.put(p.getUUID(), sneak);
            if (sneak && (prev == null || !prev)) {
                for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6))) {
                    if (MobGroup.of(m.getType()) == MobGroup.ZOMBIE) {
                        Learning.observeCrouch(level, p);
                        break;
                    }
                }
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) {
                if (done.add(mob)) {
                    try {
                        tickMob(level, mob, t);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (lastTarget.size() > 4096) lastTarget.clear();
        if (digging.size() > 512) digging.clear();
    }

    private static void tickMob(ServerLevel level, Mob mob, long t) {
        MobGroup g = MobGroup.of(mob.getType());
        if (g == null || !mob.isAlive()) return;
        RandomSource r = level.getRandom();
        Critters.tick(level, mob, g, t, r);
        if (g.hostile) Critters.climb(mob);
        LivingEntity target = mob.getTarget();
        Player targetPlayer = target instanceof Player tp && !tp.isCreative() && !tp.isSpectator() ? tp : null;
        if (g.hostile) WorldSkills.hostileTick(level, mob, targetPlayer, t);
        if (g == MobGroup.ZOMBIE && !Colonies.resident(mob) && !Builders.busy(mob) && Social.observe(level, mob, targetPlayer, t)) return;

        if (g.hostile && targetPlayer != null) Colonies.calm(mob);
        if (g.hostile && targetPlayer != null && mob.getTarget() == targetPlayer) {
            if (t % 20 == 0) Learning.observeChase(level, mob, targetPlayer, g);
            Learning.observeDodge(level, mob, targetPlayer, g);
            Integer prev = lastTarget.put(mob.getUUID(), targetPlayer.getId());
            if (prev == null || prev != targetPlayer.getId()) talk(level, mob, targetPlayer, g, r);
            Combat.track(mob);
        } else if (g.hostile) {
            lastTarget.remove(mob.getUUID());
        }

        int lvl;
        switch (g) {
            case PILLAGER -> {
                if (targetPlayer != null) {
                    if (TraitTags.get(mob, "pillager_shield") > 0) Social.raiseShield(level, mob, targetPlayer, t);
                } else if (Social.shielding(mob)) {
                    mob.stopUsingItem();
                }
            }
            case ZOMBIE -> {
                if (targetPlayer != null) {
                    if ((lvl = TraitTags.get(mob, "zombie_digger")) > 0) dig(level, mob, targetPlayer, lvl);
                    if (TraitTags.get(mob, "zombie_shield") > 0) Social.raiseShield(level, mob, targetPlayer, t);
                } else if (Social.shielding(mob)) {
                    mob.stopUsingItem();
                }
                if (TraitTags.get(mob, "zombie_dancer") > 0) dance(level, mob, t);
            }
            case SKELETON -> {
                if (targetPlayer != null && TraitTags.get(mob, "skeleton_kite") > 0 && t % 10 == 0 && mob.distanceTo(targetPlayer) < 5) {
                    flee(mob, targetPlayer, 1.15);
                }
                if (target == null && TraitTags.get(mob, "skeleton_xylophone") > 0 && t % 40 == 0 && r.nextFloat() < 0.35f
                    && level.getNearestPlayer(mob, 12) != null) {
                    float pitch = (float) Math.pow(2.0, (XYLOPHONE[r.nextInt(XYLOPHONE.length)] - 12) / 12.0);
                    level.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), SoundSource.HOSTILE, 1.0f, pitch);
                    level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 2.2, mob.getZ(), 1, 0, 0, 0, 0.5);
                }
            }
            case CREEPER -> {
                if (targetPlayer != null && TraitTags.get(mob, "creeper_angel") > 0 && watched(targetPlayer, mob)) {
                    mob.getNavigation().stop();
                    mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
                    if (mob instanceof Creeper cr) cr.setSwellDir(-1);
                }
            }
            case WOLF -> {
                if ((lvl = TraitTags.get(mob, "wolf_fetch")) > 0 && mob instanceof TamableAnimal wolf && wolf.isTame()
                    && wolf.getOwner() instanceof Player owner && owner.distanceTo(wolf) < 8 && r.nextInt(700) == 0) {
                    ItemStack toy = lvl >= 3 && r.nextFloat() < 0.15f ? new ItemStack(Items.BONE)
                        : (lvl >= 2 && r.nextFloat() < 0.2f ? new ItemStack(Items.ARROW, 1 + r.nextInt(3)) : new ItemStack(Items.STICK));
                    level.addFreshEntity(new ItemEntity(level, wolf.getX(), wolf.getY() + 0.3, wolf.getZ(), toy));
                    level.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.9, wolf.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                }
            }
            case ENDERMAN -> {
                if (targetPlayer != null && (lvl = TraitTags.get(mob, "enderman_behind")) > 0 && t % 20 == 0
                    && mob.distanceTo(targetPlayer) > 3 && r.nextFloat() < 0.04f * lvl) {
                    Vec3 look = targetPlayer.getLookAngle();
                    Vec3 dest = targetPlayer.position().subtract(look.x * 2, 0, look.z * 2);
                    ((EnderMan) mob).randomTeleport(dest.x, dest.y, dest.z, true);
                }
            }
            case COW -> {
                if (TraitTags.get(mob, "cow_hide") > 0 && t % 10 == 0) {
                    Player p = level.getNearestPlayer(mob, 6);
                    if (p != null && (p.getMainHandItem().is(Items.BUCKET) || p.getOffhandItem().is(Items.BUCKET))) flee(mob, p, 1.6);
                }
            }
            case PIG -> {
                if (TraitTags.get(mob, "pig_escape") > 0 && t % 10 == 0) {
                    Player p = level.getNearestPlayer(mob, 7);
                    if (p != null && armed(p)) flee(mob, p, 1.5);
                }
            }
            case CHICKEN -> {
                if (TraitTags.get(mob, "chicken_fly") > 0 && t % 10 == 0) {
                    Player p = level.getNearestPlayer(mob, 4);
                    if (p != null) {
                        flee(mob, p, 1.6);
                        if (mob.onGround()) mob.getJumpControl().jump();
                    }
                }
            }
            case VILLAGER -> {
                if (mob instanceof net.minecraft.world.entity.npc.Villager villager) {
                    Colonies.villagerTick(level, villager, t, r);
                    VillageDefense.tick(level, villager, t, r);
                }
                if (TraitTags.get(mob, "villager_fear") > 0 && t % 10 == 0) {
                    Player p = level.getNearestPlayer(mob, 8);
                    if (p != null && armed(p)) flee(mob, p, 0.8);
                }
                if (TraitTags.get(mob, "villager_gossip") > 0 && t % 20 == 0) {
                    Player p = level.getNearestPlayer(mob, 4);
                    if (p instanceof ServerPlayer sp && cooldown("gossip" + sp.getUUID(), t, 6000)) {
                        Talk.sayTo(sp, mob, Component.translatable("mobslearn.gossip." + r.nextInt(6), sp.getName()));
                        level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
                    }
                }
            }
            case CAT -> {
                if ((lvl = TraitTags.get(mob, "cat_gift")) > 0 && mob instanceof TamableAnimal cat && cat.isTame()
                    && cat.getOwner() instanceof Player owner && owner.distanceTo(cat) < 8 && r.nextInt(900) == 0) {
                    ItemStack gift = catGift(r, lvl);
                    ItemEntity item = new ItemEntity(level, cat.getX(), cat.getY() + 0.3, cat.getZ(), gift);
                    level.addFreshEntity(item);
                    level.sendParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.8, cat.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private static boolean griefing(ServerLevel level) {
        try {
            return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        } catch (Exception e) {
            return true;
        }
    }

    private static void dig(ServerLevel level, Mob mob, Player target, int lvl) {
        if (!griefing(level)) return;
        if (Builders.busy(mob)) return;
        if (!mob.getNavigation().isDone() && !mob.getNavigation().isStuck()) return;
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY() - mob.getY();
        if (horiz < 1.2 && Math.abs(dy) < 1.5) return;
        BlockPos feet = mob.blockPosition();
        BlockPos[] candidates;
        if (dy > 1.5 && horiz < 1.6) {
            candidates = new BlockPos[]{feet.above(2)};
        } else {
            int sx = Math.abs(dx) >= Math.abs(dz) ? (int) Math.signum(dx) : 0;
            int sz = Math.abs(dz) > Math.abs(dx) ? (int) Math.signum(dz) : 0;
            BlockPos front = feet.offset(sx, 0, sz);
            candidates = dy > 0.8 ? new BlockPos[]{front.above(), front, feet.above(2)} : new BlockPos[]{front.above(), front};
        }
        BlockPos pick = null;
        BlockState state = null;
        for (BlockPos c : candidates) {
            BlockState s = level.getBlockState(c);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) continue;
            float h = s.getDestroySpeed(level, c);
            if (h < 0 || h > DIG_HARDNESS[lvl]) return;
            pick = c;
            state = s;
            break;
        }
        if (pick == null) return;
        if (RookieActions.hesitate(level, mob, "zombie_digger", lvl, level.getGameTime())) return;
        int[] st = digging.get(mob.getUUID());
        if (st == null || st[0] != pick.getX() || st[1] != pick.getY() || st[2] != pick.getZ()) {
            st = new int[]{pick.getX(), pick.getY(), pick.getZ(), 0};
            digging.put(mob.getUUID(), st);
        }
        float hardness = state.getDestroySpeed(level, pick);
        int need = Math.max(10, Math.round((20 + hardness * 40) / (0.6f + 0.4f * lvl)));
        st[3] += 5;
        if (st[3] % 10 == 0) {
            level.playSound(null, pick, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
            mob.getLookControl().setLookAt(pick.getX() + 0.5, pick.getY() + 0.5, pick.getZ() + 0.5);
            Builders.swing(mob);
        }
        if (st[3] >= need) {
            level.destroyBlockProgress(mob.getId(), pick, -1);
            boolean keeps = TraitTags.get(mob, "zombie_builder") > 0 || TraitTags.get(mob, "zombie_bridge") > 0;
            level.destroyBlock(pick, !keeps, mob);
            if (keeps) Builders.addStock(mob, state, 1);
            digging.remove(mob.getUUID());
        } else {
            level.destroyBlockProgress(mob.getId(), pick, Math.min(9, st[3] * 10 / need));
        }
    }

    private static void build(ServerLevel level, Mob mob, Player target) {
        if (!griefing(level) || !mob.onGround()) return;
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        if (target.getY() - mob.getY() < 1.5 || dx * dx + dz * dz > 6.25) return;
        BlockPos feet = mob.blockPosition();
        if (!level.getBlockState(feet.above(2)).isAir() || !level.getBlockState(feet).canBeReplaced()) return;
        mob.setPos(mob.getX(), Math.floor(mob.getY()) + 1.05, mob.getZ());
        level.setBlockAndUpdate(feet, Blocks.DIRT.defaultBlockState());
        level.playSound(null, feet, SoundEvents.GRASS_PLACE, SoundSource.HOSTILE, 1f, 0.9f);
    }

    private static void dance(ServerLevel level, Mob mob, long t) {
        Player p = level.getNearestPlayer(mob, 8);
        boolean party = p != null && p.isShiftKeyDown();
        if (party) {
            mob.setPose((t / 5) % 2 == 0 ? Pose.CROUCHING : Pose.STANDING);
            if (t % 20 == 0) level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 2.3, mob.getZ(), 1, 0, 0, 0, 1);
        } else if (mob.getPose() == Pose.CROUCHING) {
            mob.setPose(Pose.STANDING);
        }
    }

    private static boolean watched(Player p, Entity e) {
        Vec3 look = p.getViewVector(1f);
        Vec3 dir = e.getEyePosition().subtract(p.getEyePosition());
        double len = dir.length();
        if (len < 1.0E-4) return true;
        return look.dot(dir.scale(1.0 / len)) > 0.93 && p.hasLineOfSight(e);
    }

    private static boolean armed(Player p) {
        ItemStack h = p.getMainHandItem();
        return h.is(ItemTags.SWORDS) || h.is(ItemTags.AXES) || h.is(Items.BOW) || h.is(Items.CROSSBOW);
    }

    private static void flee(Mob mob, Entity from, double speed) {
        if (Brawl.fighting(mob)) return;
        Vec3 away = mob.position().subtract(from.position());
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        away = away.normalize().scale(7);
        mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, speed);
    }

    private static ItemStack catGift(RandomSource r, int lvl) {
        if (lvl >= 3 && r.nextFloat() < 0.03f) return new ItemStack(Items.DIAMOND);
        ItemStack[] gifts = lvl >= 2
            ? new ItemStack[]{new ItemStack(Items.STRING), new ItemStack(Items.FEATHER), new ItemStack(Items.RABBIT_HIDE), new ItemStack(Items.PHANTOM_MEMBRANE), new ItemStack(Items.GOLD_NUGGET)}
            : new ItemStack[]{new ItemStack(Items.STRING), new ItemStack(Items.FEATHER), new ItemStack(Items.ROTTEN_FLESH)};
        return gifts[r.nextInt(gifts.length)];
    }

    private static void talk(ServerLevel level, Mob mob, Player p, MobGroup g, RandomSource r) {
        int lvl = WorldBrain.levelAt("world_talk", level, mob.blockPosition());
        if (lvl <= 0 || !(p instanceof ServerPlayer sp)) return;
        if (r.nextFloat() > 0.2f + 0.12f * lvl) return;
        if (!cooldown("talk" + p.getUUID(), level.getGameTime(), 200)) return;
        boolean specific = switch (g) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, WITCH -> true;
            default -> false;
        };
        String key = specific && r.nextBoolean() ? "mobslearn.talk." + g.key() + "." + r.nextInt(4) : "mobslearn.talk.generic." + r.nextInt(8);
        Net.notice(sp, Component.translatable("mobslearn.talk.format", mob.getDisplayName(), Component.translatable(key)));
    }

    public static boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
        if (e instanceof net.minecraft.world.entity.npc.Villager v && VillageDefense.blocks(v, src)) return false;
        int ink = TraitTags.get(e, "squid_ink");
        if (ink > 0 && src.getEntity() instanceof Player p && e.level() instanceof ServerLevel sl && e.getRandom().nextFloat() < 0.3f * ink) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30 + 20 * ink, 0), e);
            sl.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 0.5, e.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
        }
        int lvl = Math.max(TraitTags.get(e, "zombie_shield"), TraitTags.get(e, "pillager_shield"));
        if (lvl > 0 && src.getDirectEntity() != null && !src.is(DamageTypeTags.IS_EXPLOSION)
            && !(src.getEntity() instanceof Player hitter && hitter.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) && src.getSourcePosition() != null) {
            Vec3 to = src.getSourcePosition().subtract(e.position());
            to = new Vec3(to.x, 0, to.z);
            Vec3 view = e.getViewVector(1f);
            view = new Vec3(view.x, 0, view.z);
            if (to.lengthSqr() > 1.0E-4 && view.lengthSqr() > 1.0E-4 && to.normalize().dot(view.normalize()) > 0.3
                && (e instanceof Mob sm && Social.shielding(sm) ? e.getRandom().nextFloat() < 0.9f : e.getRandom().nextFloat() < SHIELD_BLOCK[Math.min(3, lvl)])) {
                e.level().playSound(null, e.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 0.9f + e.getRandom().nextFloat() * 0.2f);
                return false;
            }
        }
        return true;
    }

    public static void afterDamage(LivingEntity e, DamageSource src, float base, float taken, boolean blocked) {
        if (!(e instanceof Player) && e instanceof Mob victim && src.getEntity() instanceof Player hitter && e.level() instanceof ServerLevel sl && taken > 0) {
            Critters.mobHurt(sl, victim, hitter);
        }
        if (!(e instanceof Player p) || blocked || taken <= 0 || !(e.level() instanceof ServerLevel level)) return;
        Entity attacker = src.getEntity();
        if (attacker == null) return;
        Critters.playerHurt(level, p, attacker);
        int lvl;
        if ((lvl = TraitTags.get(attacker, "spider_venom")) > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 30 + 20 * lvl, 0), attacker);
        }
        if ((lvl = TraitTags.get(attacker, "spider_web")) > 0 && level.getRandom().nextFloat() < 0.1f * lvl) {
            BlockPos at = p.blockPosition();
            if (level.getBlockState(at).isAir()) {
                level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
                later(60, () -> {
                    if (level.getBlockState(at).is(Blocks.COBWEB)) level.removeBlock(at, false);
                });
            }
        }
        if ((lvl = TraitTags.get(attacker, "enderman_combo")) > 0 && attacker instanceof EnderMan en && level.getRandom().nextFloat() < 0.15f * lvl) {
            later(8, () -> {
                if (!en.isAlive() || !p.isAlive()) return;
                Vec3 look = p.getLookAngle();
                if (en.randomTeleport(p.getX() - look.x * 1.6, p.getY(), p.getZ() - look.z * 1.6, true)) {
                    later(6, () -> {
                        if (en.isAlive() && p.isAlive() && en.distanceTo(p) < 3.5) Combat.critHit(level, en, p, 1.15);
                    });
                }
            });
        }
        if ((lvl = TraitTags.get(attacker, "slime_sticky")) > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 * lvl, 0), attacker);
        }
        RandomSource r = level.getRandom();
        if ((lvl = TraitTags.get(attacker, "slime_bouncy")) > 0 && r.nextFloat() < 0.15f * lvl) {
            p.push(0, 0.45 + 0.12 * lvl, 0);
            p.hurtMarked = true;
            level.playSound(null, p.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 1f, 1.4f);
        }
        if ((lvl = TraitTags.get(attacker, "zombie_hungry")) > 0 && r.nextFloat() < 0.12f * lvl) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty() && st.has(DataComponents.FOOD)) {
                    Component food = st.getHoverName();
                    st.shrink(1);
                    level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 1f, 0.8f);
                    if (p instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.msg.ate", attacker.getDisplayName(), food));
                    break;
                }
            }
        }
        if ((lvl = TraitTags.get(attacker, "enderman_thief")) > 0 && attacker instanceof EnderMan en && en.getCarriedBlock() == null
            && r.nextFloat() < 0.1f * lvl && p.getMainHandItem().getItem() instanceof BlockItem bi) {
            Component what = p.getMainHandItem().getHoverName();
            en.setCarriedBlock(bi.getBlock().defaultBlockState());
            p.getMainHandItem().shrink(1);
            level.playSound(null, en.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.5f);
            if (p instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.msg.stole", attacker.getDisplayName(), what));
        }
    }

    public static void onDeath(LivingEntity e, DamageSource src) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Critters.onDeath(level, e, src);
        if (src.getEntity() instanceof ServerPlayer hero && e instanceof Mob dead) Colonies.mobKilled(level, hero, dead);
        if (e instanceof Mob dm) Colonies.residentDied(level, dm, src);
        if (src.getEntity() instanceof Player killer) {
            if (e.getTags().contains("mobslearn.chief")) com.rrm.mobslearn.learn.Milestones.award(killer, "chief_slayer");
            if (TraitTags.get(e, "genius") > 0) com.rrm.mobslearn.learn.Milestones.award(killer, "professor");
            if (e instanceof Mob m && Builders.busy(m)) com.rrm.mobslearn.learn.Milestones.award(killer, "outsmarted");
        }
        RandomSource rnd = level.getRandom();
        int fert = TraitTags.get(e, "skeleton_fertilizer");
        if (fert > 0) {
            BlockState[] plants = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()};
            for (int i = 0; i < 5 * fert; i++) {
                BlockPos at = e.blockPosition().offset(rnd.nextInt(7) - 3, rnd.nextInt(3) - 1, rnd.nextInt(7) - 3);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK)) {
                    level.setBlockAndUpdate(at, plants[rnd.nextInt(plants.length)]);
                }
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 0.5, e.getZ(), 15, 1.5, 0.5, 1.5, 0.0);
        }
        int sorry = TraitTags.get(e, "creeper_apology");
        if (sorry > 0 && src.getEntity() instanceof Player) {
            ItemStack gift = sorry >= 3 && rnd.nextFloat() < 0.15f ? new ItemStack(Items.CAKE) : new ItemStack(Items.POPPY);
            level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.3, e.getZ(), gift));
            level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 1, e.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
        }
        int lvl = TraitTags.get(e, "spider_family");
        if (lvl > 0) {
            int n = 1 + level.getRandom().nextInt(lvl);
            for (int i = 0; i < n; i++) {
                Mob baby = EntityType.CAVE_SPIDER.create(level);
                if (baby == null) continue;
                baby.moveTo(e.getX() + level.getRandom().nextGaussian() * 0.4, e.getY(), e.getZ() + level.getRandom().nextGaussian() * 0.4,
                    level.getRandom().nextFloat() * 360f, 0f);
                baby.addTag(TraitTags.SEEN);
                level.addFreshEntity(baby);
            }
        }
    }

    public static void onUnload(Entity e, ServerLevel level) {
        if (e instanceof Mob mob) Combat.forget(mob);
        if (!(e instanceof Creeper c) || e.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
        if (c.getSwelling(1f) < 0.3f) return;
        Learning.onExplode(level, c.blockPosition());
        int lvl = TraitTags.get(c, "creeper_party");
        if (lvl <= 0) return;
        RandomSource r = level.getRandom();
        int[] palette = {0xFF5A5A, 0xFFD25A, 0x8BE03A, 0x5FD1FF, 0xB46CFF, 0xFF7AD9};
        for (int i = 0; i < lvl; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[r.nextInt(FireworkExplosion.Shape.values().length)];
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(shape,
                IntArrayList.of(palette[r.nextInt(palette.length)], palette[r.nextInt(palette.length)]), IntArrayList.of(0xFFFFFF), true, true))));
            FireworkRocketEntity fw = new FireworkRocketEntity(level, c.getX(), c.getY() + 0.5, c.getZ(), rocket);
            level.addFreshEntity(fw);
        }
    }

    public static void angerBees(ServerLevel level, Player p) {
        int lvl = WorldBrain.level("bee_angry");
        if (lvl <= 0 || p.isCreative()) return;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6 + 2 * lvl))) {
            if (MobGroup.of(m.getType()) != MobGroup.BEE || !(m instanceof NeutralMob bee)) continue;
            if (level.getRandom().nextFloat() > 0.3f * lvl) continue;
            bee.setPersistentAngerTarget(p.getUUID());
            bee.startPersistentAngerTimer();
            m.setTarget(p);
        }
    }

    public static void onChat(PlayerChatMessage message, ServerPlayer sender) {
        String said0 = message.signedContent();
        if (said0 != null && !said0.isBlank()) Social.chat(sender, said0);
        int lvl = WorldBrain.level("parrot_mimic");
        if (lvl <= 0) return;
        ServerLevel level = (ServerLevel) sender.level();
        String text = message.signedContent();
        if (text == null || text.isBlank()) return;
        if (text.length() > 64) text = text.substring(0, 64) + "...";
        for (Mob m : level.getEntitiesOfClass(Mob.class, sender.getBoundingBox().inflate(16))) {
            if (MobGroup.of(m.getType()) != MobGroup.PARROT) continue;
            if (level.getRandom().nextFloat() > 0.35f * lvl) continue;
            String said = text;
            later(30 + level.getRandom().nextInt(30), () -> {
                if (!m.isAlive()) return;
                level.playSound(null, m.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 1f, 1.2f);
                Component line = Component.translatable("mobslearn.parrot.says", m.getDisplayName(), said);
                for (ServerPlayer pl : level.players()) if (pl.distanceTo(m) < 32) pl.sendSystemMessage(line);
            });
            break;
        }
    }
}
