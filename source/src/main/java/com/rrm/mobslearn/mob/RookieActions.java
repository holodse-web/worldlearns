package com.rrm.mobslearn.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

final class RookieActions {
    private static final class State { long next, pause; }
    private static final Map<UUID, State> states = new HashMap<>();
    private RookieActions() {}
    static void reset() { states.clear(); }
    static boolean hesitate(ServerLevel level, Mob mob, String skill, int rank, long now) {
        if (rank != 1) return false;
        State s = states.computeIfAbsent(mob.getUUID(), id -> new State());
        if (now < s.pause) { mob.getNavigation().stop(); return true; }
        if (now < s.next) return false;
        s.next = now + 600 + level.getRandom().nextInt(300);
        if (level.getRandom().nextFloat() >= 0.12f) return false;
        s.pause = now + 10;
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(mob.getX() + 0.4, mob.getY() + 0.6, mob.getZ());
        Builders.swing(mob);
        if (states.size() > 2048) { states.clear(); states.put(mob.getUUID(), s); }
        return true;
    }
}
