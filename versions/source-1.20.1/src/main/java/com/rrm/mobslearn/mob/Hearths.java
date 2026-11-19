package com.rrm.mobslearn.mob;

import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class Hearths {
    static final double BOOST = 1.25, SCORCHED = 0.4;
    static final long SCORCH_DAYS = 5;

    private static final class Hearth {
        String dim, region;
        BlockPos fire, lectern;
        long brokenDay = -1;
    }

    private static final Map<String, Hearth> hearths = new HashMap<>();
    private static final Map<String, Long> scorched = new HashMap<>();

    private Hearths() {
    }

    static void load(JsonObject o) {
        hearths.clear();
        scorched.clear();
        if (o.has("hearths")) for (var e : o.getAsJsonObject("hearths").entrySet()) {
            try {
                JsonObject j = e.getValue().getAsJsonObject();
                Hearth h = new Hearth();
                h.dim = j.get("dim").getAsString();
                h.region = j.get("region").getAsString();
                h.fire = BlockPos.of(j.get("fire").getAsLong());
                h.lectern = BlockPos.of(j.get("lectern").getAsLong());
                h.brokenDay = j.get("broken").getAsLong();
                hearths.put(e.getKey(), h);
            } catch (RuntimeException ignored) {
            }
        }
        if (o.has("scorched")) for (var e : o.getAsJsonObject("scorched").entrySet()) scorched.put(e.getKey(), e.getValue().getAsLong());
    }

    static JsonObject save() {
        JsonObject o = new JsonObject(), hs = new JsonObject(), sc = new JsonObject();
        for (var e : hearths.entrySet()) {
            Hearth h = e.getValue();
            JsonObject j = new JsonObject();
            j.addProperty("dim", h.dim);
            j.addProperty("region", h.region);
            j.addProperty("fire", h.fire.asLong());
            j.addProperty("lectern", h.lectern.asLong());
            j.addProperty("broken", h.brokenDay);
            hs.add(e.getKey(), j);
        }
        for (var e : scorched.entrySet()) sc.addProperty(e.getKey(), e.getValue());
        o.add("hearths", hs);
        o.add("scorched", sc);
        return o;
    }

    static double factor(ServerLevel level, BlockPos pos) {
        if (hearths.isEmpty() && scorched.isEmpty()) return 1.0;
        String region = WorldBrain.regionKey(level, pos);
        Long until = scorched.get(region);
        if (until != null && Saga.day(level) <= until) return SCORCHED;
        for (Hearth h : hearths.values()) if (h.brokenDay < 0 && h.region.equals(region)) return BOOST;
        return 1.0;
    }

    static void settlementTick(ServerLevel level, String id, List<Mob> residents) {
        if (level.getGameTime() % 400 != 0) return;
        Colonies.View v = Colonies.viewById(id);
        if (v == null || !v.hostile() || !v.hallDone() || v.camp() || !Builders.griefing(level)) return;
        Hearth h = hearths.get(id);
        long day = Saga.day(level);
        if (h != null && h.brokenDay < 0) {
            boolean fire = level.getBlockState(h.fire).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock;
            boolean book = level.getBlockState(h.lectern).is(Blocks.LECTERN);
            if (!fire || !book) destroy(level, id, h, null);
            return;
        }
        if (h != null && day - h.brokenDay < SCORCH_DAYS) return;
        if (residents.isEmpty()) return;
        build(level, id, v);
    }

    private static void build(ServerLevel level, String id, Colonies.View v) {
        RandomSource r = level.getRandom();
        BlockPos c = v.center();
        boolean undead = v.kind().equals("zombie") || v.kind().equals("skeleton") || v.kind().equals("nether");
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 5 + r.nextInt(5);
            int x = c.getX() + (int) Math.round(Math.cos(a) * d), z = c.getZ() + (int) Math.round(Math.sin(a) * d);
            int y = v.kind().equals("nether") ? Camps.groundY(level, x, z, c.getY(), 6) : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y == Integer.MIN_VALUE) continue;
            BlockPos fire = new BlockPos(x, y, z), lectern = fire.relative(Direction.EAST, 2);
            if (!placeable(level, fire) || !placeable(level, lectern)) continue;
            level.setBlockAndUpdate(fire, (undead ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState());
            level.setBlockAndUpdate(lectern, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.WEST));
            Hearth h = new Hearth();
            h.dim = Saga.dimId(level);
            h.region = WorldBrain.regionKey(level, fire);
            h.fire = fire;
            h.lectern = lectern;
            hearths.put(id, h);
            Saga.markDirty();
            level.sendParticles(ParticleTypes.ENCHANT, fire.getX() + 0.5, fire.getY() + 1, fire.getZ() + 0.5, 40, 1.2, 0.6, 1.2, 0.6);
            return;
        }
    }

    private static boolean placeable(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at), below = level.getBlockState(at.below());
        return s.canBeReplaced() && s.getFluidState().isEmpty() && below.isFaceSturdy(level, at.below(), Direction.UP) && level.getBlockState(at.above()).isAir();
    }

    static void blockBroken(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        if (hearths.isEmpty()) return;
        for (var e : hearths.entrySet()) {
            Hearth h = e.getValue();
            if (h.brokenDay >= 0 || !h.dim.equals(Saga.dimId(level))) continue;
            if (pos.equals(h.fire) || pos.equals(h.lectern)) {
                destroy(level, e.getKey(), h, player instanceof ServerPlayer sp ? sp : null);
                return;
            }
        }
    }

    private static void destroy(ServerLevel level, String id, Hearth h, ServerPlayer by) {
        h.brokenDay = Saga.day(level);
        scorched.put(h.region, h.brokenDay + SCORCH_DAYS);
        if (level.getBlockState(h.fire).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock) level.destroyBlock(h.fire, false);
        if (level.getBlockState(h.lectern).is(Blocks.LECTERN)) level.destroyBlock(h.lectern, true);
        Map<String, Double> region = WorldBrain.get().regions.get(h.region);
        if (region != null) {
            region.replaceAll((skill, points) -> points * 0.7);
            WorldBrain.touch();
        }
        Saga.markDirty();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, h.fire.getX() + 0.5, h.fire.getY() + 0.8, h.fire.getZ() + 0.5, 30, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, h.fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.7f);
        Component kind = Colonies.kindNameOf(id);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(h.fire.getX(), h.fire.getY(), h.fire.getZ()) < 96 * 96) Saga.notice(p, Component.translatable("mobslearn.hearth.out", kind, SCORCH_DAYS));
        }
        if (by != null) {
            Milestones.award(by, "hearth_breaker");
            Colonies.provokeBy(id, by);
            WorldBrain.chronicle(Component.translatable("mobslearn.hearth.chronicle", by.getName(), kind), "campfire");
        }
    }

    static void tick(ServerLevel level, long t) {
        if (t % 30 != 0 || hearths.isEmpty()) return;
        String dim = Saga.dimId(level);
        RandomSource r = level.getRandom();
        for (Hearth h : hearths.values()) {
            if (h.brokenDay >= 0 || !h.dim.equals(dim) || !level.isLoaded(h.lectern)) continue;
            if (level.getNearestPlayer(h.lectern.getX(), h.lectern.getY(), h.lectern.getZ(), 24, false) == null) continue;
            level.sendParticles(ParticleTypes.ENCHANT, h.lectern.getX() + 0.5, h.lectern.getY() + 1.2, h.lectern.getZ() + 0.5, 4, 0.4, 0.3, 0.4, 0.5);
            if (r.nextInt(6) == 0) level.playSound(null, h.lectern, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.5f, 0.7f + r.nextFloat() * 0.3f);
        }
    }
}
