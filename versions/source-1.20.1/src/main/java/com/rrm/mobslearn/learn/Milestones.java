package com.rrm.mobslearn.learn;

import com.rrm.mobslearn.MobsLearn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class Milestones {
    private Milestones() {}
    public static void award(Player player, String id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !WorldBrain.get().enabled) return;
        var server = serverPlayer.level().getServer();
        var root = server.getAdvancements().getAdvancement(MobsLearn.id("root"));
        if (root != null) serverPlayer.getAdvancements().award(root, "earned");
        var advancement = server.getAdvancements().getAdvancement(MobsLearn.id(id));
        if (advancement != null) serverPlayer.getAdvancements().award(advancement, "earned");
    }
    public static void nearby(ServerLevel level, BlockPos pos, String id) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5) <= 16*16) award(player, id);
        }
    }
    public static void checkWorld(ServerPlayer player) {
        Knowledge k = WorldBrain.get();
        if (!k.enabled) return;
        if (!k.points.isEmpty()) award(player, "first_lesson");
        if (k.intelligence() >= 50) award(player, "half_world");
        if (k.intelligence() >= 85) award(player, "world_geniuses");
        for (Skill skill : Skill.ALL) {
            if (skill.group() != MobGroup.WORLD && k.level(skill) == 3) { award(player, "skill_master"); break; }
        }
    }
}
