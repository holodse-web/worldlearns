package com.rrm.mobslearn.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

public final class Chatter {
    private Chatter() {
    }

    public static final int WHO = 14, DID = 32, TWIST = 16, OPEN = 8, MORE = 6, REACT = 20, STORY = 5, PAIRS = 20, ABOUT = 8, ROOTS = 12, SUFFIXES = 6;
    private static final Map<UUID, Long> nextFor = new HashMap<>();
    private static final Map<UUID, Long> busy = new HashMap<>();

    public static Component villageName(String id) {
        int h = Math.abs(id.hashCode());
        return Component.translatable("mobslearn.vname", Component.translatable("mobslearn.vroot." + (h % ROOTS)),
            Component.translatable("mobslearn.vsuf." + ((h / ROOTS) % SUFFIXES)));
    }

    private static Component place(ServerLevel level, String own, RandomSource r) {
        List<String> ids = Colonies.villageIds(level);
        if (own != null && ids.size() > 1) ids.remove(own);
        if (!ids.isEmpty() && r.nextFloat() < 0.8f) return villageName(ids.get(r.nextInt(ids.size())));
        return villageName("x" + r.nextInt(100000));
    }

    public static Component story(ServerLevel level, Villager v, RandomSource r) {
        return Component.translatable("mobslearn.chatter.story." + r.nextInt(STORY), who(r), place(level, Colonies.villageIdAt(level, v.blockPosition()), r),
            did(r), Component.translatable("mobslearn.chatter.twist." + r.nextInt(TWIST)));
    }

    public static Component gossip(ServerLevel level, Villager v, Component listener, RandomSource r) {
        return Component.translatable("mobslearn.chatter.open." + r.nextInt(OPEN), listener, place(level, Colonies.villageIdAt(level, v.blockPosition()), r), who(r), did(r));
    }

    private static Component who(RandomSource r) {
        return Component.translatable("mobslearn.chatter.who." + r.nextInt(WHO));
    }

    private static Component did(RandomSource r) {
        return Component.translatable("mobslearn.chatter.did." + r.nextInt(DID));
    }

    public static void tick(ServerLevel level, long t) {
        if (t % 40 != 0 || level.players().isEmpty()) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            Long next = nextFor.get(p.getUUID());
            if (next == null) {
                nextFor.put(p.getUUID(), t + 200 + r.nextInt(400));
                continue;
            }
            if (t < next) continue;
            List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(14, 6, 14),
                v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !v.isTrading() && busy.getOrDefault(v.getUUID(), 0L) < t);
            if (near.size() < 2) {
                nextFor.put(p.getUUID(), t + 200);
                continue;
            }
            Villager a = near.get(r.nextInt(near.size()));
            Villager b = null;
            double bd = 8 * 8;
            for (Villager v : near) {
                if (v == a) continue;
                double d = v.distanceToSqr(a);
                if (d < bd) {
                    bd = d;
                    b = v;
                }
            }
            if (b == null) {
                nextFor.put(p.getUUID(), t + 200);
                continue;
            }
            long until = t + 500 + r.nextInt(700);
            for (ServerPlayer o : level.players()) if (o.distanceTo(a) < 24) nextFor.put(o.getUUID(), until);
            busy.put(a.getUUID(), t + 300);
            busy.put(b.getUUID(), t + 300);
            if (busy.size() > 2048) busy.clear();
            converse(level, a, b, p, r);
        }
    }

    private static void converse(ServerLevel level, Villager a, Villager b, ServerPlayer p, RandomSource r) {
        a.getNavigation().stop();
        b.getNavigation().stop();
        a.getLookControl().setLookAt(b, 30f, 30f);
        b.getLookControl().setLookAt(a, 30f, 30f);
        String village = Colonies.villageIdAt(level, a.blockPosition());
        Component an = a.getDisplayName(), bn = b.getDisplayName(), pn = p.getName();
        Component first, second = Component.translatable("mobslearn.chatter.react." + r.nextInt(REACT), an), third = null;
        float roll = r.nextFloat();
        if (village != null && Colonies.grudgeOf(village, p.getUUID()) >= Colonies.WARN && roll < 0.35f) {
            first = Component.translatable("mobslearn.chatter.grudge." + r.nextInt(3), pn);
        } else if (village != null && Colonies.woodOf(village) < 4 && roll < 0.25f) {
            first = Component.translatable("mobslearn.chatter.wood." + r.nextInt(3));
        } else if (village != null && Colonies.friendOf(village) != null && roll < 0.3f) {
            first = Component.translatable("mobslearn.chatter.friend." + r.nextInt(4), villageName(Colonies.friendOf(village)), bn);
        } else if (level.isNight() && roll < 0.3f) {
            first = Component.translatable("mobslearn.chatter.night." + r.nextInt(3));
        } else if (roll < 0.36f) {
            first = gossip(level, a, bn, r);
            if (r.nextFloat() < 0.45f) third = Component.translatable("mobslearn.chatter.more." + r.nextInt(MORE));
        } else if (roll < 0.52f) {
            first = story(level, a, r);
        } else if (roll < 0.88f) {
            int k = r.nextInt(PAIRS);
            first = Component.translatable("mobslearn.chatter.pair." + k + ".a", bn, pn);
            second = Component.translatable("mobslearn.chatter.pair." + k + ".b", an, pn);
        } else {
            first = Component.translatable("mobslearn.chatter.about." + r.nextInt(ABOUT), pn, bn);
        }
        Component one = first, two = second, three = third;
        Talk.say(level, a, one, 16);
        Behaviors.later(45 + r.nextInt(30), () -> {
            if (!b.isAlive()) return;
            b.getLookControl().setLookAt(a, 30f, 30f);
            Talk.say(level, b, two, 16);
        });
        if (three != null) Behaviors.later(110 + r.nextInt(30), () -> {
            if (a.isAlive()) Talk.say(level, a, three, 16);
        });
    }
}
