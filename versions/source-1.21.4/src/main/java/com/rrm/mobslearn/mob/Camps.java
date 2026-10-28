package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.net.Net;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

public final class Camps {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] KINDS = {"zombie", "skeleton", "spider", "villager"};

    private record Op(int dx, int dy, int dz, BlockState state, boolean ground, int prep) {
        Op(int dx, int dy, int dz, BlockState state, boolean ground) {
            this(dx, dy, dz, state, ground, 0);
        }
    }

    private static final class Camp {
        String kind, dim, colony, crew;
        int lvl, oldLvl, x, y, z, step, rot, style;
        long idle;
        boolean done;
        final Map<Long, Integer> bases = new HashMap<>();
        transient List<Op> plan;
        transient long nextPlace, stallSince;
        transient int stallStep = -1;
        transient UUID worker;
        transient int preparingStep = -1, lifts;
        transient long preparedAt, jumpedAt;
        transient BlockPos liftFeet;
    }

    private static final List<Camp> camps = new ArrayList<>();
    private static MinecraftServer server;
    private static boolean dirty;

    private Camps() {
    }

    private static String skillOf(String kind) {
        return switch (kind) {
            case "zombie" -> "zombie_camp";
            case "skeleton" -> "skeleton_tower";
            case "spider" -> "spider_nest";
            default -> "villager_build";
        };
    }

    private static MobGroup groupOf(String kind) {
        return switch (kind) {
            case "zombie" -> MobGroup.ZOMBIE;
            case "skeleton" -> MobGroup.SKELETON;
            case "spider" -> MobGroup.SPIDER;
            case "creeper" -> MobGroup.CREEPER;
            default -> MobGroup.VILLAGER;
        };
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("mobslearn_camps.json");
    }

    public static void start(MinecraftServer s) {
        server = s;
        camps.clear();
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null || !o.has("camps")) return;
            for (JsonElement e : o.getAsJsonArray("camps")) {
                JsonObject c = e.getAsJsonObject();
                Camp camp = new Camp();
                camp.kind = c.get("kind").getAsString();
                camp.dim = c.get("dim").getAsString();
                camp.lvl = c.get("lvl").getAsInt();
                camp.oldLvl = c.has("oldLvl") ? c.get("oldLvl").getAsInt() : 0;
                camp.x = c.get("x").getAsInt();
                camp.y = c.get("y").getAsInt();
                camp.z = c.get("z").getAsInt();
                camp.step = c.has("step") ? c.get("step").getAsInt() : 0;
                camp.done = c.has("done") && c.get("done").getAsBoolean();
                camp.colony = c.has("colony") ? c.get("colony").getAsString() : null;
                camp.crew = c.has("crew") ? c.get("crew").getAsString() : null;
                camp.rot = c.has("rot") ? c.get("rot").getAsInt() : 0;
                camp.style = c.has("style") ? c.get("style").getAsInt() : 0;
                if (c.has("bases")) {
                    for (String part : c.get("bases").getAsString().split(";")) {
                        String[] v = part.split(",");
                        if (v.length == 3) camp.bases.put(key(Integer.parseInt(v[0]), 0, Integer.parseInt(v[1])), Integer.parseInt(v[2]));
                    }
                }
                camps.add(camp);
            }
        } catch (Exception e) {
            MobsLearn.LOG.error("[MobsLearn] Failed to read camps", e);
        }
    }

    public static void stop() {
        releaseAll();
        save();
        server = null;
        camps.clear();
    }

    public static void save() {
        if (server == null || !dirty) return;
        try {
            JsonArray arr = new JsonArray();
            for (Camp c : camps) {
                JsonObject o = new JsonObject();
                o.addProperty("kind", c.kind);
                o.addProperty("dim", c.dim);
                o.addProperty("lvl", c.lvl);
                o.addProperty("oldLvl", c.oldLvl);
                o.addProperty("x", c.x);
                o.addProperty("y", c.y);
                o.addProperty("z", c.z);
                o.addProperty("step", c.step);
                o.addProperty("done", c.done);
                if (c.colony != null) o.addProperty("colony", c.colony);
                if (c.crew != null) o.addProperty("crew", c.crew);
                o.addProperty("rot", c.rot);
                o.addProperty("style", c.style);
                if (!c.bases.isEmpty() && c.kind.equals("villager")) {
                    StringBuilder b = new StringBuilder();
                    for (var e : c.bases.entrySet()) {
                        long k = e.getKey();
                        b.append(unX(k)).append(',').append(unZ(k)).append(',').append(e.getValue()).append(';');
                    }
                    o.addProperty("bases", b.toString());
                }
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.add("camps", arr);
            Path f = file();
            Path tmp = f.resolveSibling("mobslearn_camps.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (Exception e) {
            MobsLearn.LOG.error("[MobsLearn] Failed to save camps", e);
        }
    }

    public static void reset() {
        releaseAll();
        camps.clear();
        dirty = true;
        save();
    }

    private static boolean griefing(ServerLevel level) {
        try {
            return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        } catch (Exception e) {
            return false;
        }
    }

    private static String dimId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static void tick(ServerLevel level) {
        if (server == null) return;
        long t = level.getGameTime();
        if (t % 400 == 0 && griefing(level)) {
            try {
                maybeStart(level, t);
            } catch (Exception e) {
                MobsLearn.LOG.warn("[MobsLearn] camp start failed", e);
            }
        }
        if (t % 5 == 0) {
            String dim = dimId(level);
            for (Iterator<Camp> it = camps.iterator(); it.hasNext(); ) {
                Camp c = it.next();
                if (c.done || !c.dim.equals(dim)) continue;
                try {
                    if (!griefing(level) || (c.colony == null && WorldBrain.levelAt(skillOf(c.kind), level, new BlockPos(c.x, c.y, c.z)) <= 0)) {
                        release(level, c);
                        continue;
                    }
                    int reps = Math.max(1, Math.min(16, Colonies.speed() / 25));
                    boolean alive = true;
                    for (int k = 0; k < reps && alive && !c.done; k++) alive = work(level, c, t);
                    if (!alive) {
                        release(level, c);
                        it.remove();
                        dirty = true;
                    }
                } catch (Exception e) {
                    MobsLearn.LOG.warn("[MobsLearn] camp build failed", e);
                    release(level, c);
                    it.remove();
                    dirty = true;
                }
            }
        }
        if (t % 1200 == 0) save();
    }

    private static void maybeStart(ServerLevel level, long t) {
        if (level.players().isEmpty()) return;
        RandomSource r = level.getRandom();
        String dim = dimId(level);
        for (String kind : KINDS) {
            ServerPlayer observer = level.players().get(r.nextInt(level.players().size()));
            int lvl = WorldBrain.levelAt(skillOf(kind), level, observer.blockPosition());
            if (lvl <= 0) continue;
            boolean busy = false;
            int count = 0;
            for (Camp c : camps) {
                if (!c.kind.equals(kind) || c.colony != null) continue;
                if (!c.done) busy = true;
                count++;
            }
            if (busy || r.nextFloat() > 0.12f * lvl) continue;
            MobGroup g = groupOf(kind);
            boolean hostile = g.hostile;
            if (hostile && !level.isNight() && kind.equals("zombie")) continue;

            for (Camp c : camps) {
                if (!c.done || c.colony != null || !c.kind.equals(kind) || c.lvl >= lvl || !c.dim.equals(dim)) continue;
                BlockPos at = new BlockPos(c.x, c.y, c.z);
                if (!level.isLoaded(at) || level.getNearestPlayer(c.x, c.y, c.z, 96, false) == null) continue;
                if (WorldBrain.levelAt(skillOf(kind), level, at) <= c.lvl || findBuilder(level, g, at, 40) == null) continue;
                c.oldLvl = c.lvl;
                c.lvl = lvl;
                c.step = 0;
                c.idle = t;
                c.done = false;
                c.plan = null;
                dirty = true;
                announce(level, at, "mobslearn.camp.upgrade." + kind);
                return;
            }
            if (count >= 2 + lvl) continue;

            ServerPlayer p = observer;
            if (p.isSpectator()) continue;
            Mob builder = null;
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48))) {
                if (MobGroup.of(m.getType()) == g && m.isAlive() && m.getTarget() == null && m.distanceTo(p) > 14) {
                    builder = m;
                    break;
                }
            }
            if (builder == null) continue;
            BlockPos site = findSite(level, builder.blockPosition(), r, kind, lvl);
            if (site == null) continue;
            Camp c = new Camp();
            c.kind = kind;
            c.dim = dim;
            c.lvl = lvl;
            c.x = site.getX();
            c.y = site.getY();
            c.z = site.getZ();
            c.idle = t;
            camps.add(c);
            dirty = true;
            announce(level, site, "mobslearn.camp.start." + kind);
            return;
        }
    }

    private static BlockPos findSite(ServerLevel level, BlockPos near, RandomSource r, String kind, int lvl) {
        int radius = kind.equals("villager") ? 4 : 3;
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = near.getX() + r.nextInt(17) - 8, z = near.getZ() + r.nextInt(17) - 8;
            if (!level.isLoaded(new BlockPos(x, near.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos c = new BlockPos(x, y, z);
            if (!level.isLoaded(c)) continue;
            if (level.getNearestPlayer(x, y, z, 12, false) != null) continue;
            boolean far = true;
            for (Camp o : camps) {
                if (Math.abs(o.x - x) < 24 && Math.abs(o.z - z) < 24 && Math.abs(o.y - y) < 24) far = false;
            }
            if (!far) continue;
            if (flat(level, c, radius, kind)) return c;
        }
        return null;
    }

    private static boolean flat(ServerLevel level, BlockPos c, int radius, String kind) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.isLoaded(c.offset(dx, 0, dz))) return false;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + dx, c.getZ() + dz);
                if (Math.abs(h - c.getY()) > 1) return false;
                BlockPos ground = new BlockPos(c.getX() + dx, h - 1, c.getZ() + dz);
                BlockState gs = level.getBlockState(ground);
                if (!gs.getFluidState().isEmpty() || gs.hasBlockEntity()) return false;
                boolean natural = gs.is(BlockTags.DIRT) || gs.is(BlockTags.SAND) || gs.is(BlockTags.BASE_STONE_OVERWORLD)
                    || gs.is(Blocks.GRAVEL) || gs.is(Blocks.SNOW_BLOCK) || gs.is(Blocks.DIRT_PATH) || gs.is(BlockTags.TERRACOTTA);
                if (!natural) return false;
            }
        }
        return true;
    }

    private static Mob findBuilder(ServerLevel level, MobGroup g, BlockPos at, double radius) {
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(radius))) {
            if (MobGroup.of(m.getType()) != g || !m.isAlive()) continue;
            if (m.getTarget() != null && g.hostile) continue;
            if (Builders.busy(m)) continue;
            double d = m.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    private static boolean work(ServerLevel level, Camp c, long t) {
        BlockPos origin = new BlockPos(c.x, c.y, c.z);
        if (!level.isLoaded(origin)) return true;
        if (c.plan == null) c.plan = plan(c);
        while (c.step < c.plan.size()) {
            Op op = c.plan.get(c.step);
            BlockPos pos = posOf(level, c, origin, op);
            if (!level.isLoaded(pos)) return true;
            BlockState cur = level.getBlockState(pos);
            if (cur.equals(op.state) || !placeable(level, c, op, pos, cur)) {
                c.step++;
                continue;
            }
            break;
        }
        if (c.step >= c.plan.size()) {
            complete(level, c, origin);
            return true;
        }
        MobGroup g = groupOf(c.crew != null ? c.crew : c.kind);
        Op op = c.plan.get(c.step);
        BlockPos pos = posOf(level, c, origin, op);
        Mob builder = c.worker == null ? null : level.getEntity(c.worker) instanceof Mob m ? m : null;
        if (builder != null && (!builder.isAlive() || (g.hostile && builder.getTarget() != null))) {
            release(level, c);
            builder = null;
        }
        if (builder == null) builder = findBuilder(level, g, pos, 32);
        if (builder == null) {
            return t - c.idle <= 72000;
        }
        if (!builder.getUUID().equals(c.worker)) {
            release(level, c);
            c.worker = builder.getUUID();
            c.preparingStep = -1;
        }
        c.idle = t;
        if (c.stallStep != c.step) {
            c.stallStep = c.step;
            c.stallSince = t;
        }
        boolean forced = t - c.stallSince > Math.max(60, 1200 / Colonies.speed());
        double hx = builder.getX() - (pos.getX() + 0.5), hz = builder.getZ() - (pos.getZ() + 0.5);
        double horiz = Math.sqrt(hx * hx + hz * hz);
        double reach = forced || Colonies.warp() >= 50 ? 48 : g == MobGroup.VILLAGER ? 5 : 3.6;
        if (horiz > reach) {
            carry(builder, op);
            c.preparingStep = -1;
            if (t % 20 == 0) approach(level, builder, pos);
            return true;
        }
        if (!forced && g != MobGroup.VILLAGER && Colonies.warp() < 50 && (c.liftFeet != null || pos.getY() - builder.getY() > 3.6)) {
            lift(level, builder, c, pos, t);
            return true;
        }
        if (!forced && builder.getY() - pos.getY() > 3.5) {
            if (t % 20 == 0) approach(level, builder, pos);
            c.preparingStep = -1;
            return true;
        }
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos)).isEmpty() && !op.state.isAir()
            && op.state.isCollisionShapeFullBlock(level, pos)) {
            if (builder.getBoundingBox().intersects(new AABB(pos))) approach(level, builder, pos);
            c.preparingStep = -1;
            c.nextPlace = t + 10;
            return true;
        }
        builder.getNavigation().stop();
        builder.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        carry(builder, op);
        if (c.preparingStep != c.step) {
            c.preparingStep = c.step;
            c.preparedAt = t;
            c.nextPlace = t + (op.state.isAir() ? 30 : 15) / Colonies.speed();
            Builders.swing(builder);
            return true;
        }
        if (t < c.nextPlace || RookieActions.hesitate(level, builder, skillOf(c.kind), c.lvl, t)) return true;
        Builders.swing(builder);
        if (op.state.isAir()) {
            BlockState old = level.getBlockState(pos);
            if (old.is(BlockTags.LOGS)) fellTree(level, c, pos);
            level.destroyBlockProgress(builder.getId(), pos, -1);
            level.destroyBlock(pos, false, builder);
            level.playSound(null, pos, old.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8f, 0.9f);
        } else {
            if (!put(level, c, pos, op)) return true;
            Milestones.nearby(level, pos, "builders");
            level.playSound(null, pos, op.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9f, 0.9f);
        }
        c.step++;
        c.preparingStep = -1;
        c.nextPlace = t + Math.max(1L, (long) ((g == MobGroup.VILLAGER ? 30 : 15) * (c.colony != null ? Colonies.buildFactor(level, c.colony) : 1f) / Colonies.speed()));
        dirty = true;
        return true;
    }

    private static BlockPos posOf(ServerLevel level, Camp c, BlockPos origin, Op op) {
        boolean follow = isPalisade(c.kind) || c.kind.equals("vpath") || (c.kind.equals("villager") && (op.ground || Math.abs(op.dx) <= 4));
        if (!follow) return origin.offset(op.dx, op.dy, op.dz);
        int x = origin.getX() + op.dx, z = origin.getZ() + op.dz;
        if (!level.isLoaded(new BlockPos(x, origin.getY(), z))) return origin.offset(op.dx, op.dy, op.dz);
        if (c.kind.equals("villager")) {
            Integer base = c.bases.get(key(op.dx, 0, op.dz));
            if (base == null) {
                base = terrainBase(level, x, z);
                c.bases.put(key(op.dx, 0, op.dz), base);
                dirty = true;
            }
            return new BlockPos(x, base + op.dy, z);
        }
        return new BlockPos(x, terrainBase(level, x, z) + op.dy, z);
    }

    private static int terrainBase(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int i = 0; i < 40; i++) {
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            if (below.is(BlockTags.FENCES) || below.is(BlockTags.FENCE_GATES) || below.is(BlockTags.WALLS) || below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || below.is(Blocks.VINE) || below.is(Blocks.LANTERN)
                || below.getBlock() instanceof net.minecraft.world.level.block.SkullBlock || below.is(BlockTags.SMALL_FLOWERS) || below.is(Blocks.MUSHROOM_STEM)
                || below.is(Blocks.BROWN_MUSHROOM_BLOCK) || below.is(Blocks.RED_MUSHROOM_BLOCK) || below.is(Blocks.STONE_BRICK_WALL) || below.is(Blocks.AZALEA_LEAVES)
                || (below.canBeReplaced() && below.getFluidState().isEmpty())) y--;
            else break;
        }
        return y;
    }

    private static boolean placeable(ServerLevel level, Camp c, Op op, BlockPos pos, BlockState cur) {
        if (op.prep == 3) {
            if (!cur.canBeReplaced() || !cur.getFluidState().isEmpty() || Math.abs(pos.getY() - c.y) > 12) return false;
            int rad = Math.max(10, c.lvl);
            int top = Integer.MIN_VALUE;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = op.dx + d[0], nz = op.dz + d[1];
                boolean ring = Math.abs(nx) <= rad && Math.abs(nz) <= rad && (Math.abs(nx) == rad || Math.abs(nz) == rad) && Math.abs(nx) > 1 && Math.abs(nz) > 1;
                if (!ring) continue;
                int wx = pos.getX() + d[0], wz = pos.getZ() + d[1];
                if (!level.isLoaded(new BlockPos(wx, pos.getY(), wz))) continue;
                top = Math.max(top, terrainBase(level, wx, wz));
            }
            return pos.getY() <= top;
        }
        if (op.prep == 2) return !cur.hasBlockEntity() && (cur.canBeReplaced() || !cur.getFluidState().isEmpty());
        if (op.prep == 1) return !cur.isAir() && !cur.hasBlockEntity() && cur.getFluidState().isEmpty() && natural(cur);
        if (isPalisade(c.kind) && Math.abs(pos.getY() - c.y) > 12) return false;
        if ((op.state.is(BlockTags.SMALL_FLOWERS) || op.state.is(BlockTags.SAPLINGS)) && (!level.getBlockState(pos.below()).is(BlockTags.DIRT) || !op.state.canSurvive(level, pos))) return false;
        if (c.kind.equals("villager") && op.dy == 0 && level.getBlockState(pos.below()).canBeReplaced()
            && !level.getBlockState(pos.below()).is(BlockTags.FENCES)) return false;
        if (c.kind.equals("vpath") && op.ground && cur.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) return true;
        if (cur.hasBlockEntity() || !cur.getFluidState().isEmpty()) return false;
        boolean house = c.kind.equals("vhouse") || c.kind.equals("zhouse") || c.kind.equals("vspecial");
        boolean solid = !isPalisade(c.kind) && !c.kind.equals("villager") && !c.kind.equals("vpath") && !c.kind.equals("spider");
        if (solid && !op.ground && !op.state.isAir() && natural(cur)) return true;
        if (op.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && (cur.canBeReplaced() || building(cur) || natural(cur))
            && !cur.hasBlockEntity()) return true;
        if (op.state.isAir()) return oldBlockAt(c, op).equals(cur) || (house && c.oldLvl > 0 && building(cur));
        if (op.ground) return cur.is(BlockTags.DIRT) || cur.is(Blocks.DIRT_PATH) || (solid && natural(cur) && !cur.canBeReplaced());
        if (cur.canBeReplaced()) return true;
        if (house && c.oldLvl > 0 && building(cur)) return true;
        if (cur.getBlock() instanceof net.minecraft.world.level.block.SkullBlock && !(op.state.getBlock() instanceof net.minecraft.world.level.block.SkullBlock)) return true;
        return c.oldLvl > 0 && oldBlockAt(c, op).equals(cur);
    }

    private static void penAnimals(ServerLevel level, Camp c, BlockPos origin) {
        RandomSource r = level.getRandom();
        net.minecraft.world.entity.EntityType<?>[] kinds = {EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN};
        net.minecraft.world.entity.EntityType<?> kind = kinds[Math.floorMod(c.x * 7 + c.z * 3, kinds.length)];
        int n = 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            net.minecraft.world.entity.Entity e = kind.create(level, EntitySpawnReason.EVENT);
            if (!(e instanceof Mob m)) continue;
            m.moveTo(origin.getX() + 0.5 + r.nextInt(3) - 1, origin.getY(), origin.getZ() + 0.5 + r.nextInt(3) - 1, r.nextFloat() * 360f, 0f);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
        }
    }

    private static boolean put(ServerLevel level, Camp c, BlockPos pos, Op op) {
        BlockState state = op.state;
        BlockState cur = level.getBlockState(pos);
        if (cur.is(BlockTags.LOGS) && !isPalisade(c.kind)) {
            fellTree(level, c, pos);
            cur = level.getBlockState(pos);
        }
        if (c.kind.equals("villager") || c.kind.equals("vpath") || op.ground) {
            for (int k = 0; k <= 6; k++) {
                BlockPos up = pos.above(k);
                if (level.getBlockState(up).is(BlockTags.LOGS)) {
                    fellTree(level, c, up);
                    break;
                }
            }
        }
        if (c.kind.equals("vpath") && op.ground && !cur.getFluidState().isEmpty()) state = bridge(c);
        if (!level.setBlockAndUpdate(pos, shaped(level, pos, state))) return false;
        doorTop(level, pos, state);
        if (isPalisade(c.kind) && op.prep == 0 && !op.ground) {
            BlockPos below = pos.below();
            for (int i = 0; i < 8; i++, below = below.below()) {
                BlockState b = level.getBlockState(below);
                if (!b.canBeReplaced() && b.getFluidState().isEmpty()) break;
                if (b.getBlock() instanceof net.minecraft.world.level.block.SkullBlock) break;
                BlockState fill = state.getBlock() instanceof net.minecraft.world.level.block.SkullBlock ? Blocks.COBBLESTONE_WALL.defaultBlockState() : state;
                level.setBlockAndUpdate(below, shaped(level, below, fill));
            }
        }
        return true;
    }

    private static boolean madeBlock(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.DOORS)
            || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.LADDER) || s.is(BlockTags.FENCES);
    }

    private static void fellTree(ServerLevel level, Camp c, BlockPos start) {
        if (!level.getBlockState(start).is(BlockTags.LOGS)) return;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (madeBlock(level.getBlockState(start.relative(d)))) return;
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        Set<BlockPos> logs = new HashSet<>();
        queue.add(start.immutable());
        logs.add(start.immutable());
        boolean leafy = false;
        while (!queue.isEmpty() && logs.size() < 200) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (logs.contains(n) || Math.abs(n.getX() - start.getX()) > 10 || Math.abs(n.getZ() - start.getZ()) > 10 || !level.isLoaded(n)) continue;
                        BlockState st = level.getBlockState(n);
                        if (st.is(BlockTags.LEAVES) && st.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                            && !st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) leafy = true;
                        if (!st.is(BlockTags.LOGS) || madeBlock(level.getBlockState(n.above())) || madeBlock(level.getBlockState(n.below()))) continue;
                        if (n.getY() < start.getY() - 1) continue;
                        logs.add(n.immutable());
                        queue.add(n.immutable());
                    }
                }
            }
        }
        if (!leafy) return;
        Set<BlockPos> leaves = new HashSet<>();
        java.util.ArrayDeque<BlockPos[]> lq = new java.util.ArrayDeque<>();
        for (BlockPos l : logs) lq.add(new BlockPos[]{l, l});
        while (!lq.isEmpty() && leaves.size() < 900) {
            BlockPos[] e = lq.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = e[0].relative(d);
                if (leaves.contains(n) || logs.contains(n) || n.distManhattan(e[1]) > 7 || !level.isLoaded(n)) continue;
                BlockState st = level.getBlockState(n);
                if (!st.is(BlockTags.LEAVES) || !st.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    || st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) continue;
                leaves.add(n.immutable());
                lq.add(new BlockPos[]{n.immutable(), e[1]});
            }
        }
        for (BlockPos l : leaves) level.setBlock(l, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos l : logs) level.setBlock(l, Blocks.AIR.defaultBlockState(), 2);
        level.playSound(null, start, net.minecraft.sounds.SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
        if (c.colony != null) Colonies.addWood(c.colony, logs.size() / 2);
    }

    private static void unstick(ServerLevel level, Camp c, BlockPos origin) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(origin).inflate(8, 8, 8))) {
            BlockPos at = m.blockPosition();
            if (!level.getBlockState(at).isSuffocating(level, at) && !level.getBlockState(at.above()).isSuffocating(level, at.above())) continue;
            for (int k = 1; k <= 12; k++) {
                BlockPos up = at.above(k);
                if (!level.getBlockState(up).isSuffocating(level, up) && !level.getBlockState(up.above()).isSuffocating(level, up.above())
                    && level.getBlockState(up.below()).isSuffocating(level, up.below())) {
                    m.moveTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5, m.getYRot(), m.getXRot());
                    break;
                }
            }
        }
    }

    private static BlockState bridge(Camp c) {
        return Designs.wood(c.style).planks();
    }

    public static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int i = 0; i < 40; i++) {
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || below.is(Blocks.MUSHROOM_STEM) || below.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || below.is(Blocks.RED_MUSHROOM_BLOCK) || below.is(Blocks.VINE) || (below.canBeReplaced() && below.getFluidState().isEmpty())) y--;
            else break;
        }
        return y;
    }

    private static void doorTop(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
            || state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) return;
        BlockPos up = pos.above();
        BlockState cur = level.getBlockState(up);
        if (!cur.canBeReplaced() && !building(cur) && !natural(cur) && !cur.is(state.getBlock())) return;
        level.setBlock(up, state.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
    }

    private static boolean building(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.FENCES)
            || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS)
            || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.IRON_BARS) || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)
            || s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.COMPOSTER) || s.is(Blocks.HAY_BLOCK) || s.is(Blocks.COBWEB) || s.is(Blocks.BOOKSHELF);
    }

    private static BlockState oldBlockAt(Camp c, Op op) {
        if (c.oldLvl > 0) for (Op old : blueprintFor(c, c.oldLvl)) {
            if (old.dx == op.dx && old.dy == op.dy && old.dz == op.dz) return old.state;
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static void carry(Mob builder, Op op) {
        if (op.state.isAir()) Builders.present(builder, new ItemStack(Items.WOODEN_PICKAXE));
        else Builders.presentBlock(builder, op.state);
    }

    private static void approach(ServerLevel level, Mob builder, BlockPos target) {
        if (builder instanceof net.minecraft.world.entity.npc.Villager v) {
            v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                new net.minecraft.world.entity.ai.memory.WalkTarget(target.below(), 0.6f, 2));
            return;
        }
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) for (int offset = 1; offset <= 2; offset++) {
            BlockPos column = target.relative(d, offset);
            if (!level.isLoaded(column)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            int current = builder.blockPosition().getY();
            for (int dy = 1; dy >= -4; dy--) {
                int y = current + dy;
                BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
                if (!level.getBlockState(candidate).canBeReplaced() || !level.getBlockState(candidate.above()).canBeReplaced()
                    || !level.getBlockState(candidate.below()).isSolid() || !level.getBlockState(candidate).getFluidState().isEmpty()) continue;
                double dist = builder.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                if (dist < distance) { distance = dist; best = candidate; }
            }
            if (best == null && Math.abs(surface - current) <= 1) {
                BlockPos candidate = new BlockPos(column.getX(), surface, column.getZ());
                if (level.getBlockState(candidate).canBeReplaced() && level.getBlockState(candidate.above()).canBeReplaced()) best = candidate;
            }
        }
        if (best != null) builder.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 0.85);
    }

    private static void lift(ServerLevel level, Mob builder, Camp c, BlockPos target, long t) {
        builder.getNavigation().stop();
        Builders.presentBlock(builder, Blocks.DIRT.defaultBlockState());
        if (c.liftFeet == null) {
            if (!builder.onGround() || t < c.nextPlace || c.lifts >= 16) return;
            BlockPos feet = builder.blockPosition();
            if (!level.getBlockState(feet).canBeReplaced() || !level.getBlockState(feet.above(2)).canBeReplaced()
                || !level.getBlockState(feet.above(3)).canBeReplaced() || !level.getBlockState(feet).getFluidState().isEmpty()) return;
            c.liftFeet = feet;
            c.jumpedAt = t;
            builder.getLookControl().setLookAt(feet.getX() + 0.5, feet.getY() - 0.5, feet.getZ() + 0.5);
            Builders.swing(builder);
            builder.setDeltaMovement(0, 0.46, 0);
            return;
        }
        if (builder.getY() > c.liftFeet.getY() + 1.01 && level.getBlockState(c.liftFeet).canBeReplaced()
            && Math.abs(builder.getX() - (c.liftFeet.getX() + 0.5)) < 0.8 && Math.abs(builder.getZ() - (c.liftFeet.getZ() + 0.5)) < 0.8) {
            Builders.place(level, builder, c.liftFeet, Blocks.DIRT.defaultBlockState());
            c.lifts++;
            c.liftFeet = null;
            c.nextPlace = t + 20;
            c.preparingStep = -1;
        } else if (t - c.jumpedAt > 25) c.liftFeet = null;
    }

    private static void release(ServerLevel level, Camp c) {
        if (c.worker != null && level.getEntity(c.worker) instanceof Mob mob) Builders.clearPresentation(mob);
        c.worker = null;
        c.liftFeet = null;
        c.preparingStep = -1;
    }

    private static void releaseAll() {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) for (Camp c : camps) if (c.dim.equals(dimId(level))) release(level, c);
    }

    private static void complete(ServerLevel level, Camp c, BlockPos origin) {
        release(level, c);
        c.done = true;
        c.oldLvl = 0;
        c.plan = null;
        dirty = true;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, origin.getX() + 0.5, origin.getY() + 2, origin.getZ() + 0.5, 30, 2, 2, 2, 0);
        String flavor = Loot.flavor(c.kind, c.crew);
        for (Op op : blueprintFor(c, c.lvl)) {
            if (op.state.is(Blocks.CHEST) || op.state.is(Blocks.BARREL)) {
                BlockPos at = posOf(level, c, origin, op);
                if (level.isLoaded(at)) Loot.fill(level, at, flavor);
            }
        }
        if (c.kind.equals("vspecial") && c.lvl == 7) penAnimals(level, c, origin);
        if (c.colony != null) {
            Colonies.structureDone(level, c.colony, c.kind, origin);
            return;
        }
        announce(level, origin, "mobslearn.camp.done." + c.kind);
        if (c.kind.equals("skeleton")) Milestones.nearby(level, origin, "tower_top");
        if (c.kind.equals("zombie") && c.lvl >= 2) Colonies.foundNatural(level, origin, "zombie");
        if (c.kind.equals("skeleton") && c.lvl >= 2) Colonies.foundNatural(level, origin, "skeleton");
        RandomSource r = level.getRandom();
        switch (c.kind) {
            case "zombie" -> {
                Mob chief = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
                if (chief == null) return;
                chief.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.5, r.nextFloat() * 360f, 0f);
                chief.setCustomName(Component.translatable("mobslearn.camp.chief"));
                chief.addTag("mobslearn.chief");
                chief.setCustomNameVisible(true);
                chief.setPersistenceRequired();
                chief.setItemSlot(EquipmentSlot.HEAD, new ItemStack(c.lvl >= 3 ? Items.IRON_HELMET : Items.LEATHER_HELMET));
                chief.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(c.lvl >= 2 ? Items.STONE_AXE : Items.WOODEN_AXE));
                chief.setDropChance(EquipmentSlot.HEAD, 0f);
                chief.setDropChance(EquipmentSlot.MAINHAND, 0f);
                level.addFreshEntity(chief);
            }
            case "skeleton" -> {
                int top = c.lvl == 1 ? 5 : c.lvl == 2 ? 7 : 9;
                int n = 0;
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(origin).inflate(24))) {
                    if (MobGroup.of(m.getType()) != MobGroup.SKELETON || m.getTarget() != null) continue;
                    m.getNavigation().moveTo(origin.getX() + 0.5 + (n == 0 ? 1 : -1), origin.getY() + top, origin.getZ() + 0.5, 0.85);
                    m.setPersistenceRequired();
                    if (++n >= 2) break;
                }
            }
            default -> {
            }
        }
    }

    private static void announce(ServerLevel level, BlockPos at, String key) {
        if (key.startsWith("mobslearn.camp.")) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.getX(), at.getY(), at.getZ()) < 96 * 96) Net.notice(p, Component.translatable(key));
        }
    }

    private static List<Op> prep(String kind, List<Op> target) {
        List<Op> out = new ArrayList<>();
        if (isPalisade(kind)) {
            for (Op o : target) {
                if (o.dy != 0 || o.ground || o.state.getBlock() instanceof net.minecraft.world.level.block.SkullBlock) continue;
                for (int dy = 1; dy <= 4; dy++) out.add(new Op(o.dx, dy, o.dz, o.state, false, 3));
            }
            return out;
        }
        if (kind.equals("spider") || kind.equals("villager")) return out;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, maxY = 0;
        Set<Long> solid = new HashSet<>();
        for (Op o : target) {
            if (o.ground) continue;
            minX = Math.min(minX, o.dx);
            maxX = Math.max(maxX, o.dx);
            minZ = Math.min(minZ, o.dz);
            maxZ = Math.max(maxZ, o.dz);
            maxY = Math.max(maxY, o.dy);
            if (!o.state.isAir()) solid.add(key(o.dx, o.dy, o.dz));
        }
        if (minX > maxX) return out;
        BlockState base = kind.equals("vhouse") || kind.equals("vcamp") || kind.equals("vfarm") || kind.equals("cmaze") ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        for (int dy = -3; dy <= -1; dy++) {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) out.add(new Op(x, dy, z, base, false, 2));
        }
        for (int dy = maxY + 1; dy >= 0; dy--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!solid.contains(key(x, dy, z))) out.add(new Op(x, dy, z, Blocks.AIR.defaultBlockState(), false, 1));
                }
            }
        }
        return out;
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
            || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
            || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA) || s.canBeReplaced();
    }

    private static List<Op> plan(Camp c) {
        List<Op> target = blueprintFor(c, c.lvl);
        List<Op> ground = prep(c.kind, target);
        if (c.oldLvl <= 0) {
            ground.addAll(target);
            return ground;
        }
        List<Op> old = blueprintFor(c, c.oldLvl);
        Set<Long> keep = new HashSet<>();
        for (Op o : target) keep.add(key(o.dx, o.dy, o.dz));
        List<Op> out = new ArrayList<>();
        for (int i = old.size() - 1; i >= 0; i--) {
            Op o = old.get(i);
            if (!keep.contains(key(o.dx, o.dy, o.dz)) && !o.ground) out.add(new Op(o.dx, o.dy, o.dz, Blocks.AIR.defaultBlockState(), false));
        }
        out.addAll(ground);
        out.addAll(target);
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private static int unX(long k) {
        return (int) ((k >> 40) & 0xFFFFF) - 512;
    }

    private static int unZ(long k) {
        return (int) (k & 0xFFFFF) - 512;
    }

    private static final class Plan implements Designs.B {
        final Map<Long, Op> ops = new LinkedHashMap<>();

        public void put(int x, int y, int z, BlockState s) {
            ops.put(key(x, y, z), new Op(x, y, z, s, false));
        }

        public void ground(int x, int z, BlockState s) {
            ops.put(key(x, -1, z), new Op(x, -1, z, s, true));
        }

        public void clear(int x, int y, int z) {
            ops.put(key(x, y, z), new Op(x, y, z, Blocks.AIR.defaultBlockState(), false, 1));
        }

        public void remove(int x, int y, int z) {
            ops.remove(key(x, y, z));
        }

        public boolean has(int x, int y, int z) {
            return ops.containsKey(key(x, y, z));
        }

        List<Op> list() {
            List<Op> l = new ArrayList<>(ops.values());
            l.sort((a, b) -> a.dy != b.dy ? Integer.compare(a.dy, b.dy) : 0);
            return l;
        }
    }

    private static List<Op> blueprintFor(Camp c, int lvl) {
        List<Op> ops = blueprint(c.kind, lvl, c.x * 31L + c.z * 17L + c.y, c.style);
        if (c.rot == 0) return ops;
        net.minecraft.world.level.block.Rotation rotation = switch (c.rot & 3) {
            case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            default -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
        };
        List<Op> out = new ArrayList<>(ops.size());
        for (Op o : ops) {
            int x = o.dx, z = o.dz;
            int nx, nz;
            switch (c.rot & 3) {
                case 1 -> { nx = -z; nz = x; }
                case 2 -> { nx = -x; nz = -z; }
                default -> { nx = z; nz = -x; }
            }
            out.add(new Op(nx, o.dy, nz, o.state.rotate(rotation), o.ground, o.prep));
        }
        return out;
    }

    private static List<Op> blueprint(String kind, int lvl, long seed, int style) {
        RandomSource r = RandomSource.create(seed);
        Plan p = new Plan();
        switch (kind) {
            case "zombie" -> Designs.zombieHall(p, lvl, style, r);
            case "skeleton" -> Designs.skeletonHall(p, lvl, style, r);
            case "spider" -> spider(p, lvl, r);
            case "zhouse" -> Designs.zombieHome(p, lvl, style, r);
            case "vhouse" -> Designs.villagerHome(p, lvl, style, r);
            case "vspecial" -> Designs.special(p, lvl, style, r);
            case "vpath" -> Designs.path(p, (lvl >> 10) - 512, (lvl & 1023) - 512);
            case "vfarm" -> villagerFarm(p, r);
            case "cmaze" -> creeperMaze(p, r);
            case "palisade" -> palisade(p, lvl, Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.ZOMBIE_HEAD.defaultBlockState(), 4);
            case "spalisade" -> palisade(p, lvl, Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.SKELETON_SKULL.defaultBlockState(), 3);
            case "chedge" -> palisade(p, lvl, leaves(Blocks.AZALEA_LEAVES), Blocks.CREEPER_HEAD.defaultBlockState(), 2);
            case "shouse" -> Designs.skeletonHome(p, style, r);
            case "cgrove" -> Designs.creeperHall(p, lvl, style, r);
            case "cnest" -> Designs.creeperNest(p, style, r);
            case "vcamp" -> villagerCamp(p);
            default -> Designs.villageCenter(p, lvl, style, r);
        }
        return p.list();
    }

    private static void ring(Plan p, int radius, int y, java.util.function.Supplier<BlockState> s) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius) p.put(x, y, z, s.get());
            }
        }
    }

    private static void fill(Plan p, int radius, int y, java.util.function.Supplier<BlockState> s) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) p.put(x, y, z, s.get());
    }

    public static double integrity(ServerLevel level, String colony, String kind) {
        for (Camp c : camps) {
            if (!colony.equals(c.colony) || !c.kind.equals(kind) || !c.done) continue;
            BlockPos origin = new BlockPos(c.x, c.y, c.z);
            if (!level.isLoaded(origin)) return -1;
            int total = 0, ok = 0;
            for (Op op : blueprintFor(c, c.lvl)) {
                if (op.state.isAir() || op.ground) continue;
                total++;
                if (level.getBlockState(posOf(level, c, origin, op)).is(op.state.getBlock())) ok++;
            }
            return total == 0 ? -1 : ok / (double) total;
        }
        return -1;
    }

    private static void spider(Plan p, int lvl, RandomSource r) {
        int count = lvl == 1 ? 6 : lvl == 2 ? 14 : 26;
        int radius = lvl == 1 ? 2 : 3;
        int height = lvl == 1 ? 1 : lvl == 2 ? 2 : 3;
        for (int i = 0; i < count * 3 && p.ops.size() < count; i++) {
            int x = r.nextInt(radius * 2 + 1) - radius, z = r.nextInt(radius * 2 + 1) - radius, y = r.nextInt(height + 1);
            if (x == 0 && z == 0) continue;
            p.put(x, y, z, Blocks.COBWEB.defaultBlockState());
        }
        if (lvl >= 3) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x) + Math.abs(z) <= 2 && r.nextFloat() < 0.6f) p.put(x, 4, z, Blocks.COBWEB.defaultBlockState());
        }
    }

    public static boolean isPalisade(String kind) {
        return kind.equals("palisade") || kind.equals("spalisade") || kind.equals("chedge");
    }

    private static BlockState shaped(ServerLevel level, BlockPos pos, BlockState state) {
        net.minecraft.world.level.block.Block b = state.getBlock();
        if (b instanceof net.minecraft.world.level.block.CrossCollisionBlock || b instanceof net.minecraft.world.level.block.WallBlock
            || b instanceof net.minecraft.world.level.block.StairBlock) {
            BlockState s = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos);
            return s.isAir() ? state : s;
        }
        return state;
    }

    private static void villagerFarm(Plan p, RandomSource r) {
        net.minecraft.world.level.block.Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        net.minecraft.world.level.block.Block crop = crops[r.nextInt(crops.length)];
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                if (edge) {
                    if (!(z == -3 && x == 0)) p.put(x, 0, z, Blocks.OAK_FENCE.defaultBlockState());
                    continue;
                }
                if (x == 0 && z == 0) {
                    p.ground(0, 0, Blocks.WATER.defaultBlockState());
                    continue;
                }
                p.ground(x, z, Blocks.FARMLAND.defaultBlockState());
                int age = r.nextInt(8);
                BlockState c = crop.defaultBlockState();
                if (crop == Blocks.BEETROOTS) c = c.setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, Math.min(3, age / 2));
                else c = c.setValue(net.minecraft.world.level.block.CropBlock.AGE, age);
                p.put(x, 0, z, c);
            }
        }
        p.put(-3, 1, -3, Blocks.LANTERN.defaultBlockState());
        p.put(3, 1, 3, Blocks.LANTERN.defaultBlockState());
        p.put(0, 0, -4, Blocks.COMPOSTER.defaultBlockState());
        p.put(1, 0, -4, Blocks.HAY_BLOCK.defaultBlockState());
    }

    private static void creeperMaze(Plan p, RandomSource r) {
        int n = 6, size = 2 * n + 1, off = n;
        boolean[][] open = new boolean[size][size];
        boolean[][] seen = new boolean[n][n];
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{n / 2, 0});
        seen[n / 2][0] = true;
        open[2 * (n / 2) + 1][1] = true;
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !seen[nx][nz]) next.add(new int[]{nx, nz, d[0], d[1]});
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] pick = next.get(r.nextInt(next.size()));
            seen[pick[0]][pick[1]] = true;
            open[2 * cur[0] + 1 + pick[2]][2 * cur[1] + 1 + pick[3]] = true;
            open[2 * pick[0] + 1][2 * pick[1] + 1] = true;
            stack.push(new int[]{pick[0], pick[1]});
        }
        open[2 * (n / 2) + 1][0] = true;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = i - off, z = j - off;
                if (open[i][j]) {
                    p.ground(x, z, r.nextFloat() < 0.5f ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                    float f = r.nextFloat();
                    if (f < 0.05f) p.put(x, 0, z, Blocks.COBWEB.defaultBlockState());
                    else if (f < 0.12f) p.put(x, 0, z, Blocks.MOSS_CARPET.defaultBlockState());
                    continue;
                }
                for (int y = 0; y <= 1; y++) p.put(x, y, z, leaves(r.nextFloat() < 0.25f ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
                if (r.nextFloat() < 0.04f) p.put(x, 2, z, Blocks.CREEPER_HEAD.defaultBlockState());
            }
        }
        int cx = 2 * (n / 2) + 1 - off, cz = 2 * (n - 1) + 1 - off;
        p.put(cx, 0, cz, Blocks.CHEST.defaultBlockState());
        p.put(cx, 1, cz, Blocks.CREEPER_HEAD.defaultBlockState());
    }

    private static void palisade(Plan p, int radius, BlockState post, BlockState top, int every) {
        int rad = Math.max(10, radius);
        for (int x = -rad; x <= rad; x++) {
            for (int z = -rad; z <= rad; z++) {
                if (Math.abs(x) != rad && Math.abs(z) != rad) continue;
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) continue;
                p.put(x, 0, z, post);
                if (Math.floorMod(x + z, every) == 0) p.put(x, 1, z, post);
            }
        }
        for (int[] k : corners(rad)) {
            p.put(k[0], 1, k[1], post);
            p.put(k[0], 2, k[1], top);
        }
    }

    private static BlockState leaves(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    private static void villagerCamp(Plan p) {
        BlockState wool = Blocks.WHITE_WOOL.defaultBlockState();
        for (int z = -1; z <= 1; z++) {
            p.put(-2, 0, z, wool);
            p.put(2, 0, z, wool);
            p.put(-1, 1, z, wool);
            p.put(1, 1, z, wool);
            p.put(0, 2, z, wool);
        }
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH);
        p.put(0, 0, 0, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        p.put(0, 0, 1, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        p.put(3, 0, 1, Blocks.BARREL.defaultBlockState());
        p.put(3, 0, -1, Blocks.HAY_BLOCK.defaultBlockState());
        p.put(0, 0, -3, Blocks.CAMPFIRE.defaultBlockState());
        p.put(-3, 0, -2, Blocks.OAK_FENCE.defaultBlockState());
        p.put(-3, 1, -2, Blocks.LANTERN.defaultBlockState());
    }

    public static void addProject(ServerLevel level, String kind, int lvl, BlockPos origin, String colony, String crew) {
        addProject(level, kind, lvl, origin, colony, crew, 0, 0);
    }

    public static void addProject(ServerLevel level, String kind, int lvl, BlockPos origin, String colony, String crew, int rot, int style) {
        for (Camp c : camps) {
            if (colony.equals(c.colony) && c.kind.equals(kind) && c.x == origin.getX() && c.y == origin.getY() && c.z == origin.getZ()) {
                if (c.lvl >= lvl) return;
                c.oldLvl = c.done ? c.lvl : 0;
                c.lvl = lvl;
                c.step = 0;
                c.done = false;
                c.plan = null;
                c.idle = level.getGameTime();
                dirty = true;
                return;
            }
        }
        Camp c = new Camp();
        c.kind = kind;
        c.dim = dimId(level);
        c.lvl = lvl;
        c.x = origin.getX();
        c.y = origin.getY();
        c.z = origin.getZ();
        c.colony = colony;
        c.crew = crew;
        c.rot = rot;
        c.style = style;
        c.idle = level.getGameTime();
        camps.add(c);
        dirty = true;
    }

    public static int countLevel(String colony, String kind, int lvl) {
        int n = 0;
        for (Camp c : camps) if (colony.equals(c.colony) && c.kind.equals(kind) && c.lvl == lvl) n++;
        return n;
    }

    public static int count(String colony, String kind) {
        int n = 0;
        for (Camp c : camps) if (colony.equals(c.colony) && c.kind.equals(kind)) n++;
        return n;
    }

    public static void instantBuild(ServerLevel level, String colony) {
        String dim = dimId(level);
        for (Camp c : new ArrayList<>(camps)) {
            if (c.done || !colony.equals(c.colony) || !c.dim.equals(dim)) continue;
            BlockPos origin = new BlockPos(c.x, c.y, c.z);
            if (!level.isLoaded(origin)) continue;
            release(level, c);
            if (c.plan == null) c.plan = plan(c);
            for (; c.step < c.plan.size(); c.step++) {
                Op op = c.plan.get(c.step);
                BlockPos pos = posOf(level, c, origin, op);
                if (!level.isLoaded(pos)) continue;
                BlockState cur = level.getBlockState(pos);
                if (cur.equals(op.state) || !placeable(level, c, op, pos, cur)) continue;
                if (op.state.isAir()) {
                    if (cur.is(BlockTags.LOGS)) fellTree(level, c, pos);
                    level.removeBlock(pos, false);
                } else put(level, c, pos, op);
            }
            complete(level, c, origin);
            unstick(level, c, origin);
        }
    }

    public static void upgradeAll(ServerLevel level, String colony, String kind, int lvl) {
        for (Camp c : new ArrayList<>(camps)) {
            if (!colony.equals(c.colony) || !c.kind.equals(kind) || c.lvl >= lvl || !c.dim.equals(dimId(level))) continue;
            addProject(level, kind, lvl, new BlockPos(c.x, c.y, c.z), colony, c.crew, c.rot, c.style);
        }
    }

    public static BlockPos originOf(String colony, String kind) {
        for (Camp c : camps) if (colony.equals(c.colony) && c.kind.equals(kind) && c.done) return new BlockPos(c.x, c.y, c.z);
        return null;
    }

    public static int pending(String colony) {
        int n = 0;
        for (Camp c : camps) if (colony.equals(c.colony) && !c.done) n++;
        return n;
    }

    public static void reassign(String from, String to) {
        for (Camp c : camps) if (from.equals(c.colony)) c.colony = to;
        dirty = true;
    }

    public static void forgetColony(String colony) {
        camps.removeIf(c -> colony.equals(c.colony));
        dirty = true;
    }

    public static BlockPos siteNear(ServerLevel level, BlockPos near, int radius, int spread, RandomSource r) {
        return siteNear(level, near, radius, spread, r, null);
    }

    public static BlockPos siteNear(ServerLevel level, BlockPos near, int radius, int spread, RandomSource r, String colony) {
        return siteNear(level, near, radius, spread, r, colony, 3);
    }

    public static BlockPos siteNear(ServerLevel level, BlockPos near, int radius, int spread, RandomSource r, String colony, int maxDiff) {
        BlockPos best = null;
        int bestRange = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 28; attempt++) {
            int sp = attempt < 14 ? spread : spread * 2 + 4;
            int x = near.getX() + (attempt == 0 ? 0 : r.nextInt(sp * 2 + 1) - sp);
            int z = near.getZ() + (attempt == 0 ? 0 : r.nextInt(sp * 2 + 1) - sp);
            if (!level.isLoaded(new BlockPos(x - radius, near.getY(), z - radius)) || !level.isLoaded(new BlockPos(x + radius, near.getY(), z + radius))) continue;
            boolean free = true;
            for (Camp o : camps) {
                if (isPalisade(o.kind) || o.kind.equals("vpath")) continue;
                int gap = colony != null && !colony.equals(o.colony) ? 24 : radius + 7;
                if (Math.abs(o.x - x) <= gap && Math.abs(o.z - z) <= gap) free = false;
            }
            if (!free) continue;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean ok = true;
            for (int dx = -radius; dx <= radius && ok; dx++) {
                for (int dz = -radius; dz <= radius && ok; dz++) {
                    int h = groundY(level, x + dx, z + dz);
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    BlockState gs = level.getBlockState(new BlockPos(x + dx, h - 1, z + dz));
                    if (!gs.getFluidState().isEmpty() || gs.hasBlockEntity() || !natural(gs) && !gs.is(Blocks.DIRT_PATH)) ok = false;
                    for (int k = 0; k <= 3 && ok; k++) {
                        BlockState above = level.getBlockState(new BlockPos(x + dx, h + k, z + dz));
                        if (!above.isAir() && !natural(above) && !above.canBeReplaced()) ok = false;
                    }
                }
            }
            if (!ok || max - min > maxDiff) continue;
            if (max - min < bestRange) {
                bestRange = max - min;
                int cy = groundY(level, x, z);
                best = new BlockPos(x, Math.max(min, Math.min(max, cy)), z);
                if (bestRange <= 1) break;
            }
        }
        return best;
    }

    private static int[][] corners(int r) {
        return new int[][]{{r, r}, {-r, r}, {r, -r}, {-r, -r}};
    }
}
