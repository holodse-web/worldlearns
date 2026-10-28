package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.WorldBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WorldSkills {
    private WorldSkills() {
    }

    private static boolean has(String id) {
        return WorldBrain.get().enabled && WorldBrain.level(id) > 0;
    }

    public static void hostileTick(ServerLevel level, Mob mob, Player target, long t) {
        if (target != null && MobGroup.of(mob.getType()) != MobGroup.ZOMBIE && (t / 5 + mob.getId()) % 2 == 0 && has("world_doors") && mob.distanceTo(target) < 16) openDoors(level, mob);
        if ((t / 5 + mob.getId()) % 12 == 0 && level.isDarkOutside() && has("world_torches")) {
            Player near = level.getNearestPlayer(mob, 24);
            if (near != null && !near.isCreative()) snuffTorches(level, mob);
        }
    }

    private static void openDoors(ServerLevel level, Mob mob) {
        BlockPos base = mob.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof DoorBlock door) || !s.is(BlockTags.WOODEN_DOORS) || s.getValue(DoorBlock.OPEN)) continue;
            door.setOpen(mob, level, s, p.immutable(), true);
            level.sendParticles(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.3);
            return;
        }
    }

    private static void snuffTorches(ServerLevel level, Mob mob) {
        BlockPos base = mob.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 2, 2))) {
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.TORCH) && !s.is(Blocks.WALL_TORCH)) continue;
            level.destroyBlock(p.immutable(), true, mob);
            level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6f, 1.2f);
            level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 8, 0.1, 0.1, 0.1, 0.01);
            return;
        }
    }

    public static void playerTick(ServerLevel level, ServerPlayer p, long t) {
        if (p.isCreative() || p.isSpectator()) return;
        if (t % 20 == 0 && p.isSprinting() && level.isDarkOutside() && has("world_ears")) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20, 8, 20))) {
                MobGroup g = MobGroup.of(m.getType());
                if (g == null || !g.hostile || m.getTarget() != null || Colonies.resident(m) && Colonies.peaceful(m)) continue;
                m.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.0);
                if (m.distanceTo(p) < 12) m.setTarget(p);
            }
        }
        if (t % 20 == 0 && armed(p) && has("world_herd")) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8, 4, 8))) {
                MobGroup g = MobGroup.of(m.getType());
                if (g != MobGroup.COW && g != MobGroup.SHEEP && g != MobGroup.PIG && g != MobGroup.CHICKEN && g != MobGroup.RABBIT && g != MobGroup.HORSE) continue;
                if (m.isLeashed() || m.isVehicle()) continue;
                Vec3 away = m.position().subtract(p.position());
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
                away = away.normalize().scale(8);
                m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.4);
            }
        }
        if (t % 200 == 0 && level.isThundering() && has("world_storm")) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32, 12, 32))) {
                MobGroup g = MobGroup.of(m.getType());
                if (g == null || !g.hostile) continue;
                m.addEffect(new MobEffectInstance(MobEffects.SPEED, 240, 0));
                m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 240, 0));
            }
        }
    }

    public static void hurt(ServerLevel level, Mob victim, Player p) {
        if (p.isCreative() || !has("world_pack")) return;
        MobGroup g = MobGroup.of(victim.getType());
        if (g == null || !g.hostile) return;
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(12, 6, 12))) {
            if (m == victim || MobGroup.of(m.getType()) != g || m.getTarget() != null) continue;
            if (Colonies.resident(m) && Colonies.peaceful(m)) continue;
            m.setTarget(p);
            if (++n >= 4) break;
        }
    }

    private static boolean armed(Player p) {
        ItemStack h = p.getMainHandItem();
        return h.is(ItemTags.SWORDS) || h.is(ItemTags.AXES) || h.is(Items.BOW) || h.is(Items.CROSSBOW);
    }

    public static double trackBonus() {
        return has("world_track") ? 8.0 : 0.0;
    }
}
