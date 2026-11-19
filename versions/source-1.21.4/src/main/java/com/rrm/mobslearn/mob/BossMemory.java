package com.rrm.mobslearn.mob;

import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Trigger;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class BossMemory {
    private static int dragonBeds, dragonPillar;
    private static final Map<UUID, int[]> witherFight = new HashMap<>();
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private static final Map<UUID, Long> hiddenSince = new HashMap<>();
    private static final Map<UUID, UUID> wardenTarget = new HashMap<>();

    private BossMemory() {
    }

    static void load(JsonObject o) {
        dragonBeds = o.has("dragonBeds") ? o.get("dragonBeds").getAsInt() : 0;
        dragonPillar = o.has("dragonPillar") ? o.get("dragonPillar").getAsInt() : 0;
        witherFight.clear();
        cooldown.clear();
        hiddenSince.clear();
        wardenTarget.clear();
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("dragonBeds", dragonBeds);
        o.addProperty("dragonPillar", dragonPillar);
        return o;
    }

    private static int lvl(String skill) {
        return WorldBrain.get().bossMemory ? WorldBrain.level(skill) : 0;
    }

    private static boolean ready(UUID id, String what, long now, long every) {
        UUID key = new UUID(id.getMostSignificantBits() ^ what.hashCode(), id.getLeastSignificantBits());
        Long last = cooldown.get(key);
        if (last != null && now - last < every) return false;
        cooldown.put(key, now);
        if (cooldown.size() > 512) cooldown.clear();
        return true;
    }

    private static void teach(Trigger trigger, ServerLevel level, BlockPos at, double amount) {
        if (WorldBrain.get().bossMemory) WorldBrain.teach(trigger, MobGroup.BOSS, amount, level, at);
    }

    static boolean onPillar(ServerLevel level, Player p) {
        BlockPos feet = p.blockPosition();
        if (!level.getBlockState(feet.below()).isSolid()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) if (!level.getBlockState(feet.below().relative(d)).isAir()) return false;
        int air = 0;
        for (int i = 2; i < 12; i++) if (level.getBlockState(feet.below(i).relative(Direction.NORTH)).isAir()) air++;
        return air >= 5;
    }

    static void afterDamage(ServerLevel level, LivingEntity e, DamageSource src, float taken) {
        if (e instanceof EnderDragon dragon) {
            if (src.is(DamageTypeTags.IS_EXPLOSION)) {
                dragonBeds++;
                int beds = lvl("dragon_beds");
                if (beds > 0) {
                    dragon.heal(taken * (0.25f + 0.25f * beds));
                    level.sendParticles(ParticleTypes.PORTAL, dragon.getX(), dragon.getY() + 2, dragon.getZ(), 20, 2, 1, 2, 0.02);
                }
            }
            if (src.getEntity() instanceof Player p && onPillar(level, p)) dragonPillar++;
            return;
        }
        if (e instanceof WitherBoss wither && src.getEntity() instanceof Player p) {
            int[] f = witherFight.computeIfAbsent(wither.getUUID(), k -> new int[2]);
            if (!wither.hasLineOfSight(p) || !level.getBlockState(p.blockPosition().above(2)).isAir()) f[0]++;
            else f[1]++;
            return;
        }
        if (e.getType() == EntityType.ELDER_GUARDIAN && e instanceof Mob elder) {
            int call = lvl("guardian_call");
            if (call > 0 && ready(elder.getUUID(), "call", level.getGameTime(), 600 - 120L * call)) {
                for (int i = 0; i < call; i++) {
                    Mob g = EntityType.GUARDIAN.create(level, EntitySpawnReason.REINFORCEMENT);
                    if (g == null) continue;
                    g.moveTo(elder.getX() + level.getRandom().nextGaussian() * 3, elder.getY(), elder.getZ() + level.getRandom().nextGaussian() * 3, 0f, 0f);
                    if (src.getEntity() instanceof LivingEntity attacker) g.setTarget(attacker);
                    level.addFreshEntity(g);
                }
                level.playSound(null, elder.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1f, 1.3f);
            }
        }
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        BlockPos at = e.blockPosition();
        if (e instanceof EnderDragon) {
            teach(Trigger.DRAGON_CRYSTALS, level, at, 1);
            if (dragonBeds >= 2) teach(Trigger.DRAGON_BEDS, level, at, 1);
            if (dragonPillar >= 3) teach(Trigger.DRAGON_PILLAR, level, at, 1);
            dragonBeds = dragonPillar = 0;
            Saga.markDirty();
            return;
        }
        if (e instanceof WitherBoss) {
            int[] f = witherFight.remove(e.getUUID());
            teach(Trigger.WITHER_DOWN, level, at, 1);
            if (f != null && f[0] > f[1]) teach(Trigger.WITHER_HIDE, level, at, 1);
            return;
        }
        if (e.getType() == EntityType.ELDER_GUARDIAN) teach(Trigger.GUARDIAN_DOWN, level, at, 1);
    }

    static void blockBroken(ServerLevel level, Player p, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.PRISMARINE) && !state.is(Blocks.PRISMARINE_BRICKS) && !state.is(Blocks.DARK_PRISMARINE) && !state.is(Blocks.SEA_LANTERN)
            && !state.is(Blocks.WET_SPONGE)) return;
        if (!ready(p.getUUID(), "mine", level.getGameTime(), 200)) return;
        if (level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.getType() == EntityType.ELDER_GUARDIAN).isEmpty()) return;
        teach(Trigger.GUARDIAN_MINE, level, pos, 1);
    }

    static void levelTick(ServerLevel level, long t) {
        if (t % 20 != 0 || !level.dimension().equals(Level.END) || level.players().isEmpty()) return;
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, new AABB(-300, -64, -300, 300, 320, 300), d -> d.isAlive());
        for (EnderDragon dragon : dragons) dragonTick(level, dragon, t);
    }

    private static void dragonTick(ServerLevel level, EnderDragon dragon, long t) {
        int cages = lvl("dragon_cages");
        if (cages > 0 && !dragon.getTags().contains("mobslearn.caged")) {
            dragon.addTag("mobslearn.caged");
            int left = 2 + 2 * cages;
            for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, new AABB(-120, 0, -120, 120, 200, 120))) {
                if (left <= 0) break;
                if (cage(level, crystal.blockPosition())) left--;
            }
            for (ServerPlayer p : level.players()) Saga.notice(p, Component.translatable("mobslearn.boss.cages"));
        }
        int pillar = lvl("dragon_pillar");
        if (pillar > 0 && t % 60 == 0) {
            for (ServerPlayer p : level.players()) {
                if (p.isCreative() || p.isSpectator() || !onPillar(level, p) || !ready(p.getUUID(), "pillar", level.getGameTime(), 400 - 80L * pillar)) continue;
                if (pillar >= 3) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                    dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(new Vec3(p.getX(), p.getY(), p.getZ()));
                } else {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
                    dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(p);
                }
                Saga.overlay(p, Component.translatable("mobslearn.boss.pillar"));
                break;
            }
        }
    }

    private static boolean cage(ServerLevel level, BlockPos crystal) {
        int placed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!ring && dy < 3) continue;
                    BlockPos at = crystal.offset(dx, dy, dz);
                    if (!level.getBlockState(at).isAir()) continue;
                    level.setBlockAndUpdate(at, Blocks.IRON_BARS.defaultBlockState());
                    placed++;
                }
            }
        }
        return placed > 8;
    }

    static void tick(ServerLevel level, Mob mob, long t) {
        if (mob instanceof Warden warden) wardenTick(level, warden, t);
        else if (mob instanceof WitherBoss wither) witherTick(level, wither, t);
        else if (mob.getType() == EntityType.ELDER_GUARDIAN && t % 60 == 0) {
            int curse = lvl("guardian_curse");
            if (curse <= 0) return;
            for (ServerPlayer p : level.players()) {
                if (p.isCreative() || p.isSpectator() || p.distanceTo(mob) > 30 + 10 * curse || !p.isInWater()) continue;
                MobEffectInstance cur = p.getEffect(MobEffects.DIG_SLOWDOWN);
                if (cur == null || cur.getDuration() < 600) p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, Math.min(3, 1 + curse)), mob);
            }
        }
    }

    private static void wardenTick(ServerLevel level, Warden warden, long t) {
        if (t % 10 != 0) return;
        int sneak = lvl("warden_sneak"), hunt = lvl("warden_hunt");
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            double d = p.distanceTo(warden);
            if (!p.isShiftKeyDown() || d > 14) continue;
            if (sneak > 0 && d < 3 + 3 * sneak) {
                warden.increaseAngerAt(p, 10 + 10 * sneak, false);
            } else if (warden.getTarget() == null && ready(p.getUUID(), "sneak", level.getGameTime(), 600)) {
                teach(Trigger.WARDEN_SNEAK, level, warden.blockPosition(), 1);
            }
        }
        LivingEntity target = warden.getTarget();
        UUID was = wardenTarget.get(warden.getUUID());
        if (target instanceof Player tp) wardenTarget.put(warden.getUUID(), tp.getUUID());
        else if (was != null) {
            wardenTarget.remove(warden.getUUID());
            ServerPlayer gone = level.getServer().getPlayerList().getPlayer(was);
            if (gone != null && gone.isAlive() && gone.distanceTo(warden) > 20) teach(Trigger.WARDEN_ESCAPE, level, warden.blockPosition(), 1);
        }
        if (hunt > 0 && target != null) {
            warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 600L * hunt);
            if (hunt >= 3 && !warden.hasEffect(MobEffects.MOVEMENT_SPEED)) warden.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false));
        }
    }

    private static void witherTick(ServerLevel level, WitherBoss wither, long t) {
        if (t % 10 != 0 || wither.getInvulnerableTicks() > 0) return;
        int guard = lvl("wither_guard");
        if (guard > 0 && wither.getHealth() < wither.getMaxHealth() * 0.5f && !wither.getTags().contains("mobslearn.guarded")) {
            wither.addTag("mobslearn.guarded");
            RandomSource r = level.getRandom();
            for (int i = 0; i < 2 * guard; i++) {
                Mob s = EntityType.WITHER_SKELETON.create(level, EntitySpawnReason.REINFORCEMENT);
                if (s == null) continue;
                s.moveTo(wither.getX() + r.nextGaussian() * 3, wither.getY() - 2, wither.getZ() + r.nextGaussian() * 3, r.nextFloat() * 360f, 0f);
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                s.addTag(TraitTags.SEEN);
                level.addFreshEntity(s);
            }
            level.playSound(null, wither.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.4f);
        }
        int hunter = lvl("wither_hunter");
        if (hunter <= 0) return;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || p.distanceTo(wither) > 40) continue;
            boolean hidden = !wither.hasLineOfSight(p);
            if (!hidden) {
                hiddenSince.remove(p.getUUID());
                continue;
            }
            long since = hiddenSince.computeIfAbsent(p.getUUID(), k -> level.getGameTime());
            if (level.getGameTime() - since < 80 - 15L * hunter || !ready(wither.getUUID(), "dig", level.getGameTime(), 70 - 15L * hunter)) continue;
            Vec3 from = new Vec3(wither.getX(), wither.getY() + 3, wither.getZ());
            Vec3 dir = p.position().add(0, 1, 0).subtract(from).normalize();
            WitherSkull skull = new WitherSkull(level, wither, dir);
            skull.setDangerous(true);
            skull.setPos(from.x, from.y, from.z);
            level.addFreshEntity(skull);
            level.playSound(null, wither.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1f, 0.8f);
            break;
        }
    }
}
