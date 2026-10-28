package com.rrm.mobslearn.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class Doors {
    private Doors() {
    }

    public static void residentTick(ServerLevel level, Mob mob, long t) {
        if (!Colonies.resident(mob)) return;
        BlockPos base = mob.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof DoorBlock door) || !s.is(BlockTags.WOODEN_DOORS) || s.getValue(DoorBlock.OPEN)) continue;
            BlockPos at = p.immutable();
            door.setOpen(mob, level, s, at, true);
            Behaviors.later(40, () -> close(level, at));
            return;
        }
    }

    private static void close(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        if (!(s.getBlock() instanceof DoorBlock door) || !s.getValue(DoorBlock.OPEN)) return;
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(1.2)).isEmpty()) {
            Behaviors.later(30, () -> close(level, at));
            return;
        }
        door.setOpen(null, level, s, at, false);
    }
}
