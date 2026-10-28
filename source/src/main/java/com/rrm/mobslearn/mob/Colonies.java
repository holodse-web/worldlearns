package com.rrm.mobslearn.mob;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.Trigger;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.net.Net;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

public final class Colonies {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long TIER_TIME = 240000, PEACE_TIME = 48000, HOUSE_TIME = 72000, DAUGHTER_TIME = 240000;
    private static final String HOME = "mobslearn.home.";
    private static final String CHIEF = "mobslearn.chief";

    private static final class Colony {
        String id, kind, dim, state = "active", attacker;
        int x, y, z, tier, houses, gloom, decay, chestTier = -1;
        long age, lastTier, peace, chiefDeadAge, lastRaidedAge = -999999, emptySince, lastFairDay = -1, lastRaidNightDay = -1, lastDaughter;
        boolean natural, pending, outposted, hallDone, neutral, camp;
        List<String> known = new ArrayList<>(), hidden = new ArrayList<>();
        java.util.Map<String, Integer> grudges = new java.util.HashMap<>();
        transient List<UUID> sortie = new ArrayList<>();
        transient long sortieUntil, nextSortie, nextSeed, nextArrival;
        transient boolean chiefAlive, retreating;
        long lastSeen, lastClock, lastExtra, lastBirth;
        int wood = 30;
        int woodKind = -1;
        transient long chiefDeadTick, nextBirth, nextRepair;
        int extraHouses;
        int warLoss, px, pz;
        boolean big, hasPartner;
        transient List<UUID> raiders = new ArrayList<>();
        transient UUID raidPlayer;
        transient BlockPos raidPos;
        transient long raidUntil, nextSpawn, nextRaid, nextVillageRaid, nextSocial, fairUntil;
        transient int population;

        BlockPos center() {
            return new BlockPos(x, y, z);
        }

        boolean active() {
            return state.equals("active") && !pending;
        }
    }

    private static final List<Colony> colonies = new ArrayList<>();
    private static MinecraftServer server;
    private static boolean dirty;
    private static int warp = 1;

    private Colonies() {
    }

    public static int speed() {
        return warp * 2;
    }

    public static float buildFactor(ServerLevel level, String colonyId) {
        Colony c = byId(colonyId);
        if (c == null || hostile(c.kind)) return 1f;
        int tools = WorldBrain.levelAt("villager_tools", level, c.center());
        return 1f / (1f + tools * 0.5f);
    }

    private static final java.util.Map<String, java.util.Map<Long, Long>> visited = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> heroes = new java.util.HashMap<>();

    private static final java.util.Map<UUID, Integer> helped = new java.util.HashMap<>();
    public static final int HERO_NEEDS = 3;

    public static void grantHero(Player p, long until) {
        heroes.put(p.getUUID(), until);
    }

    private static void helpPoint(ServerLevel level, ServerPlayer p, BlockPos where) {
        int n = helped.merge(p.getUUID(), 1, Integer::sum);
        dirty = true;
        if (n < HERO_NEEDS) {
            Net.notice(p, Component.translatable("mobslearn.colony.help_progress", n, HERO_NEEDS));
            return;
        }
        helped.remove(p.getUUID());
        grantHero(p, level.getGameTime() + 72000);
        Net.notice(p, Component.translatable("mobslearn.colony.hero_earned"));
        level.playSound(null, where, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1f);
        Milestones.award(p, "village_guard");
    }

    public static int warp() {
        return warp;
    }

    public static void setWarp(int w) {
        warp = Math.max(1, Math.min(100000, w));
        dirty = true;
        save();
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("mobslearn_colonies.json");
    }

    public static void start(MinecraftServer s) {
        server = s;
        colonies.clear();
        warp = 1;
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null) return;
            if (o.has("warp")) warp = Math.max(1, o.get("warp").getAsInt());
            visited.clear();
            helped.clear();
            heroes.clear();
            if (o.has("helped")) for (var e : o.getAsJsonObject("helped").entrySet()) helped.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
            if (o.has("heroes")) for (var e : o.getAsJsonObject("heroes").entrySet()) heroes.put(UUID.fromString(e.getKey()), e.getValue().getAsLong());
            relations.clear();
            aliases.clear();
            if (o.has("relations")) for (var e : o.getAsJsonObject("relations").entrySet()) relations.put(e.getKey(), e.getValue().getAsString());
            if (o.has("aliases")) for (var e : o.getAsJsonObject("aliases").entrySet()) aliases.put(e.getKey(), e.getValue().getAsString());
            if (o.has("visited")) for (JsonElement e : o.getAsJsonArray("visited")) {
                JsonObject v = e.getAsJsonObject();
                visited.computeIfAbsent(v.get("dim").getAsString(), k -> new java.util.HashMap<>()).put(v.get("cell").getAsLong(), v.get("t").getAsLong());
            }
            if (!o.has("colonies")) return;
            for (JsonElement e : o.getAsJsonArray("colonies")) {
                JsonObject j = e.getAsJsonObject();
                Colony c = new Colony();
                c.id = j.get("id").getAsString();
                c.kind = j.get("kind").getAsString();
                c.dim = j.get("dim").getAsString();
                c.x = j.get("x").getAsInt();
                c.y = j.get("y").getAsInt();
                c.z = j.get("z").getAsInt();
                c.tier = j.get("tier").getAsInt();
                c.houses = num(j, "houses", 0);
                c.age = numL(j, "age", 0);
                c.lastTier = numL(j, "lastTier", 0);
                c.peace = numL(j, "peace", 0);
                c.chiefDeadAge = numL(j, "chiefDeadAge", 0);
                c.lastRaidedAge = numL(j, "lastRaidedAge", -999999);
                c.lastFairDay = numL(j, "lastFairDay", -1);
                c.lastRaidNightDay = numL(j, "lastRaidNightDay", -1);
                c.gloom = num(j, "gloom", 0);
                c.decay = num(j, "decay", 0);
                c.chestTier = num(j, "chestTier", -1);
                c.natural = bool(j, "natural");
                c.pending = bool(j, "pending");
                c.outposted = bool(j, "outposted");
                c.hallDone = bool(j, "hallDone");
                c.neutral = bool(j, "neutral");
                c.state = j.has("state") ? j.get("state").getAsString() : "active";
                c.attacker = j.has("attacker") ? j.get("attacker").getAsString() : null;
                c.camp = bool(j, "camp");
                c.lastDaughter = numL(j, "lastDaughter", 0);
                c.lastSeen = numL(j, "lastSeen", 0);
                c.lastClock = numL(j, "lastClock", 0);
                c.lastExtra = numL(j, "lastExtra", 0);
                c.wood = num(j, "wood", 30);
                c.woodKind = num(j, "woodKind2", -1);
                c.extraHouses = num(j, "extraHouses", 0);
                c.warLoss = num(j, "warLoss", 0);
                c.px = num(j, "px", 0);
                c.pz = num(j, "pz", 0);
                c.big = j.has("big") && j.get("big").getAsBoolean();
                c.hasPartner = j.has("hasPartner") && j.get("hasPartner").getAsBoolean();
                if (j.has("known")) for (JsonElement k : j.getAsJsonArray("known")) c.known.add(k.getAsString());
                if (j.has("hidden")) for (JsonElement k : j.getAsJsonArray("hidden")) c.hidden.add(k.getAsString());
                if (j.has("grudges")) for (var ge : j.getAsJsonObject("grudges").entrySet()) c.grudges.put(ge.getKey(), ge.getValue().getAsInt());
                colonies.add(c);
            }
        } catch (Exception e) {
            MobsLearn.LOG.error("[MobsLearn] Failed to read colonies", e);
        }
    }

    private static int num(JsonObject j, String k, int d) {
        return j.has(k) ? j.get(k).getAsInt() : d;
    }

    private static long numL(JsonObject j, String k, long d) {
        return j.has(k) ? j.get(k).getAsLong() : d;
    }

    private static boolean bool(JsonObject j, String k) {
        return j.has(k) && j.get(k).getAsBoolean();
    }

    public static void stop() {
        dirty = true;
        save();
        server = null;
        colonies.clear();
    }

    public static void save() {
        if (server == null || !dirty) return;
        try {
            JsonArray arr = new JsonArray();
            for (Colony c : colonies) {
                JsonObject o = new JsonObject();
                o.addProperty("id", c.id);
                o.addProperty("kind", c.kind);
                o.addProperty("dim", c.dim);
                o.addProperty("state", c.state);
                o.addProperty("x", c.x);
                o.addProperty("y", c.y);
                o.addProperty("z", c.z);
                o.addProperty("tier", c.tier);
                o.addProperty("houses", c.houses);
                o.addProperty("age", c.age);
                o.addProperty("lastTier", c.lastTier);
                o.addProperty("peace", c.peace);
                o.addProperty("chiefDeadAge", c.chiefDeadAge);
                o.addProperty("lastRaidedAge", c.lastRaidedAge);
                o.addProperty("lastFairDay", c.lastFairDay);
                o.addProperty("lastRaidNightDay", c.lastRaidNightDay);
                o.addProperty("gloom", c.gloom);
                o.addProperty("decay", c.decay);
                o.addProperty("chestTier", c.chestTier);
                o.addProperty("natural", c.natural);
                o.addProperty("pending", c.pending);
                o.addProperty("outposted", c.outposted);
                o.addProperty("hallDone", c.hallDone);
                o.addProperty("neutral", c.neutral);
                if (c.attacker != null) o.addProperty("attacker", c.attacker);
                o.addProperty("camp", c.camp);
                o.addProperty("lastDaughter", c.lastDaughter);
                o.addProperty("lastSeen", c.lastSeen);
                o.addProperty("lastClock", c.lastClock);
                o.addProperty("lastExtra", c.lastExtra);
                o.addProperty("wood", c.wood);
                o.addProperty("woodKind2", c.woodKind);
                o.addProperty("extraHouses", c.extraHouses);
                o.addProperty("warLoss", c.warLoss);
                o.addProperty("px", c.px);
                o.addProperty("pz", c.pz);
                o.addProperty("big", c.big);
                o.addProperty("hasPartner", c.hasPartner);
                JsonArray known = new JsonArray();
                for (String k : c.known) known.add(k);
                o.add("known", known);
                JsonArray hidden = new JsonArray();
                for (String k : c.hidden) hidden.add(k);
                o.add("hidden", hidden);
                JsonObject gr = new JsonObject();
                for (var e : c.grudges.entrySet()) gr.addProperty(e.getKey(), e.getValue());
                o.add("grudges", gr);
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("warp", warp);
            root.add("colonies", arr);
            JsonArray vis = new JsonArray();
            for (var dimEntry : visited.entrySet()) {
                for (var cell : dimEntry.getValue().entrySet()) {
                    JsonObject v = new JsonObject();
                    v.addProperty("dim", dimEntry.getKey());
                    v.addProperty("cell", cell.getKey());
                    v.addProperty("t", cell.getValue());
                    vis.add(v);
                }
            }
            root.add("visited", vis);
            JsonObject hp = new JsonObject(), hr = new JsonObject();
            for (var e : helped.entrySet()) hp.addProperty(e.getKey().toString(), e.getValue());
            for (var e : heroes.entrySet()) hr.addProperty(e.getKey().toString(), e.getValue());
            root.add("helped", hp);
            root.add("heroes", hr);
            JsonObject rel = new JsonObject(), al = new JsonObject();
            for (var e : relations.entrySet()) rel.addProperty(e.getKey(), e.getValue());
            for (var e : aliases.entrySet()) al.addProperty(e.getKey(), e.getValue());
            root.add("relations", rel);
            root.add("aliases", al);
            Path f = file();
            Path tmp = f.resolveSibling("mobslearn_colonies.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (Exception e) {
            MobsLearn.LOG.error("[MobsLearn] Failed to save colonies", e);
        }
    }

    private static String dimId(Level level) {
        return level.dimension().identifier().toString();
    }

    private static boolean hostile(String kind) {
        return !kind.equals("village");
    }

    private static MobGroup crewGroup(String kind) {
        return switch (kind) {
            case "skeleton" -> MobGroup.SKELETON;
            case "creeper" -> MobGroup.CREEPER;
            case "village" -> MobGroup.VILLAGER;
            default -> MobGroup.ZOMBIE;
        };
    }

    private static String crewName(String kind) {
        return kind.equals("village") ? "villager" : kind;
    }

    private static String hallOf(String kind) {
        return switch (kind) {
            case "skeleton" -> "skeleton";
            case "creeper" -> "cgrove";
            default -> "zombie";
        };
    }

    private static String houseOf(String kind) {
        return switch (kind) {
            case "skeleton" -> "shouse";
            case "creeper" -> "cnest";
            default -> "zhouse";
        };
    }

    private static Colony byId(String id) {
        if (id == null) return null;
        for (Colony c : colonies) if (c.id.equals(id)) return c;
        String alias = aliases.get(id);
        return alias != null && !alias.equals(id) ? byId(alias) : null;
    }

    private static Colony homeOf(Entity e) {
        for (String tag : e.entityTags()) if (tag.startsWith(HOME)) return byId(tag.substring(HOME.length()));
        return null;
    }

    private static Colony near(Level level, BlockPos pos, String kind, double radius, boolean activeOnly) {
        String dim = dimId(level);
        Colony best = null;
        double bestD = radius * radius;
        for (Colony c : colonies) {
            if (c.pending || !c.dim.equals(dim) || (kind != null && !c.kind.equals(kind)) || (activeOnly && !c.active())) continue;
            double d = pos.distSqr(c.center());
            if (d <= bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static final String[] IMPORTANT = {"mobslearn.colony.found.", "mobslearn.colony.tier.", "mobslearn.colony.grew_away", "mobslearn.colony.neutral",
        "mobslearn.colony.angry", "mobslearn.colony.collapsed", "mobslearn.colony.village_fell", "mobslearn.colony.daughter", "mobslearn.colony.grudge",
        "mobslearn.colony.banned", "mobslearn.colony.forgiven", "mobslearn.colony.moved", "mobslearn.colony.war", "mobslearn.colony.alliance",
        "mobslearn.colony.friends", "mobslearn.colony.cleared"};

    private static boolean important(Component msg) {
        if (!(msg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return false;
        for (String k : IMPORTANT) if (tc.getKey().startsWith(k)) return true;
        return false;
    }

    private static Colony nearestTo(ServerPlayer p) {
        String dim = dimId(p.level());
        Colony best = null;
        double bd = Double.MAX_VALUE;
        for (Colony c : colonies) {
            if (c.pending || !c.dim.equals(dim)) continue;
            double d = p.distanceToSqr(c.x, c.y, c.z);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static void tell(ServerLevel level, BlockPos at, int radius, Component msg) {
        if (!important(msg)) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.getX(), at.getY(), at.getZ()) >= (double) radius * radius) continue;
            Colony near = nearestTo(p);
            if (near != null && near.center().distSqr(at) > 48 * 48) continue;
            Net.notice(p, msg);
        }
    }

    private static void awardNear(ServerLevel level, BlockPos at, int radius, String id) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(at.getX(), at.getY(), at.getZ()) < (double) radius * radius) Milestones.award(p, id);
        }
    }

    public static boolean found(ServerLevel level, BlockPos pos, String kind, boolean natural) {
        if (natural && hostile(kind) && nearVillage(level, pos, 80)) return false;
        return create(level, pos, kind, natural, natural && kind.equals("zombie") ? 1 : 0, false) != null;
    }

    private static String buildSkill(String kind) {
        return switch (kind) {
            case "village" -> "villager_build";
            case "skeleton" -> "skeleton_tower";
            case "creeper" -> "creeper_ambush";
            default -> "zombie_camp";
        };
    }

    public static boolean canSettle(ServerLevel level, String kind, BlockPos pos) {
        return !hostile(kind) || WorldBrain.levelAt(buildSkill(kind), level, pos) > 0;
    }

    private static Colony create(ServerLevel level, BlockPos pos, String kind, boolean natural, int tier, boolean force) {
        if (!force && near(level, pos, null, natural ? 140 : 48, false) != null) return null;
        if (!force && natural && !canSettle(level, kind, pos)) return null;
        Colony c = new Colony();
        c.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + Integer.toString(level.getRandom().nextInt(1296), 36);
        c.kind = kind;
        c.dim = dimId(level);
        c.x = pos.getX();
        c.y = pos.getY();
        c.z = pos.getZ();
        c.natural = natural;
        c.tier = tier;
        colonies.add(c);
        dirty = true;
        queue(level, c);
        if (hostile(kind)) {
            for (int i = 0; i < (natural ? 1 : 2); i++) spawnResident(level, c, pos.offset(i * 2 - 1, 0, -4));
            c.nextSpawn = level.getGameTime() + 1200 / warp;
        } else if (!natural) {
            if (level.getBlockState(pos).canBeReplaced()) level.setBlockAndUpdate(pos, Blocks.BELL.defaultBlockState());
            for (int i = 0; i < 3; i++) {
                Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
                if (v == null) continue;
                v.snapTo(pos.getX() + 2.5 - i * 2, pos.getY(), pos.getZ() + 2.5, level.getRandom().nextFloat() * 360f, 0f);
                if (i == 2) v.setBaby(true);
                level.addFreshEntity(v);
            }
        }
        tell(level, pos, 96, Component.translatable("mobslearn.colony.found." + kind));
        if (hostile(kind)) awardNear(level, pos, 96, "colony_neighbors");
        return c;
    }

    public static void chunkLoaded(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk, boolean generated) {
        if (!generated || server == null || !WorldBrain.get().enabled || level.dimension() != Level.OVERWORLD || !Builders.griefing(level)) return;
        RandomSource r = level.getRandom();
        if (r.nextInt(350) != 0) return;
        int x = chunk.getPos().getMiddleBlockX(), z = chunk.getPos().getMiddleBlockZ();
        String dim = dimId(level);
        for (Colony o : colonies) {
            if (o.dim.equals(dim) && Math.abs(o.x - x) < 220 && Math.abs(o.z - z) < 220) return;
        }
        float f = r.nextFloat();
        String kind = f < 0.4f ? "village" : f < 0.8f ? "zombie" : f < 0.92f ? "skeleton" : "creeper";
        Colony seed = new Colony();
        seed.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + "n";
        seed.kind = kind;
        seed.dim = dim;
        seed.x = x;
        seed.z = z;
        seed.y = level.getSeaLevel();
        seed.tier = kind.equals("village") ? r.nextInt(4) : 1 + r.nextInt(3);
        seed.natural = true;
        seed.pending = true;
        colonies.add(seed);
        dirty = true;
    }

    public static void foundNatural(ServerLevel level, BlockPos origin, String kind) {
        if (near(level, origin, null, 96, true) != null) return;
        found(level, origin, kind, true);
    }

    private static BlockPos townSite(ServerLevel level, BlockPos near, int search, int radius) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int cx = -search; cx <= search; cx += 8) {
            for (int cz = -search; cz <= search; cz += 8) {
                int x = near.getX() + cx, z = near.getZ() + cz;
                if (!level.isLoaded(new BlockPos(x - radius, 64, z - radius)) || !level.isLoaded(new BlockPos(x + radius, 64, z + radius))) continue;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
                for (int sx = -radius; sx <= radius; sx += 4) {
                    for (int sz = -radius; sz <= radius; sz += 4) {
                        int h = Camps.groundY(level, x + sx, z + sz);
                        min = Math.min(min, h);
                        max = Math.max(max, h);
                        BlockState gs = level.getBlockState(new BlockPos(x + sx, h - 1, z + sz));
                        if (!gs.getFluidState().isEmpty()) wet++;
                        else if (!(gs.is(BlockTags.SUBSTRATE_OVERWORLD) || gs.is(BlockTags.SAND) || gs.is(BlockTags.BASE_STONE_OVERWORLD) || gs.is(Blocks.GRAVEL)
                            || gs.is(Blocks.SNOW_BLOCK) || gs.is(Blocks.SNOW) || gs.is(BlockTags.TERRACOTTA) || gs.is(BlockTags.LEAVES) || gs.is(BlockTags.LOGS))) wet += 10;
                    }
                }
                int score = (max - min) * 4 + wet * 6;
                if (max - min <= 4 && wet <= 2 && score < bestScore && near(level, new BlockPos(x, min, z), null, 120, false) == null) {
                    bestScore = score;
                    best = new BlockPos(x, Camps.groundY(level, x, z), z);
                }
            }
        }
        return best;
    }

    public static boolean remove(ServerLevel level, BlockPos pos) {
        Colony c = near(level, pos, null, 64, false);
        if (c == null) return false;
        colonies.remove(c);
        Camps.forgetColony(c.id);
        dirty = true;
        return true;
    }

    public static List<String> describe(ServerLevel level) {
        List<String> out = new ArrayList<>();
        for (Colony c : colonies) {
            out.add(c.kind + " #" + c.id + " " + (c.pending ? "?" : c.state + " tier " + c.tier + (c.neutral ? " neutral" : ""))
                + " @ " + c.x + " " + c.y + " " + c.z + " " + c.dim);
        }
        return out;
    }

    private static int maxTier(ServerLevel level, Colony c) {
        if (c.camp) return 0;
        if (!c.natural) return c.big ? 8 : 6;
        String skill = buildSkill(c.kind);
        int lvl = WorldBrain.levelAt(skill, level, c.center());
        if (c.kind.equals("village")) lvl += WorldBrain.levelAt("villager_tools", level, c.center());
        return Math.min(c.big ? 8 : 6, (c.kind.equals("village") ? 3 : 2) + lvl + (c.big ? 2 : 0));
    }

    private static long day(ServerLevel level) {
        return level.getOverworldClockTime() / 24000L;
    }

    public static void tick(ServerLevel level) {
        if (server == null) return;
        long t = level.getGameTime();
        if (t % 200 == 0) markMaps(level);
        if (t % 600 == 0) remember(level, t);
        if (t % 6000 == 0) seedVisited(level, t);
        if (!climbers.isEmpty()) climbTick(level, t);
        if (t % 20 == 0 && !colonies.isEmpty()) enforce(level, t);
        if (t % 20 == 0 && !warriors.isEmpty()) warTick(level, t);
        if (t % 1200 == 600 && !colonies.isEmpty()) relationsTick(level, t);
        if (t % 5 == 0 && !foresters.isEmpty()) foresterTick(level, t);
        if (colonies.isEmpty() || t % 100 != 0) return;
        String dim = dimId(level);
        if (t % 1200 == 0) seed(level, t);
        for (Colony c : new ArrayList<>(colonies)) {
            if (!c.dim.equals(dim)) continue;
            BlockPos center = c.center();
            if (!level.isLoaded(center) || level.getNearestPlayer(c.x, c.y, c.z, 160, false) == null) continue;
            try {
                if (c.pending) settle(level, c);
                else update(level, c, t);
            } catch (Exception e) {
                MobsLearn.LOG.warn("[MobsLearn] colony update failed", e);
            }
        }
        if (t % 1200 == 0) save();
    }

    private static void settle(ServerLevel level, Colony c) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.x, c.z);
        BlockPos site = townSite(level, new BlockPos(c.x, y, c.z), 40, 10);
        if (site != null && hostile(c.kind) && nearVillage(level, site, 80)) {
            BlockPos moved = null;
            for (int i = 0; i < 4 && moved == null; i++) {
                double a = i * Math.PI / 2 + level.getRandom().nextDouble();
                BlockPos alt = townSite(level, site.offset((int) (Math.cos(a) * 96), 0, (int) (Math.sin(a) * 96)), 32, 10);
                if (alt != null && !nearVillage(level, alt, 80)) moved = alt;
            }
            site = moved;
        }
        colonies.remove(c);
        dirty = true;
        if (site == null) return;
        Colony made = create(level, site, c.kind, true, 0, false);
        if (made != null) made.lastSeen = c.lastSeen > 0 ? c.lastSeen : level.getGameTime();
        if (made != null && c.tier > 0) {
            made.tier = Math.min(c.tier, maxTier(level, made));
            made.age = made.tier * TIER_TIME;
            made.lastTier = made.age;
            made.extraHouses = level.getRandom().nextInt(3);
            for (int pass = 0; pass < 4; pass++) {
                queue(level, made);
                Camps.instantBuild(level, made.id);
            }
        }
        if (made != null && c.kind.equals("village")) {
            if (level.getBlockState(site).canBeReplaced()) level.setBlockAndUpdate(site, Blocks.BELL.defaultBlockState());
            for (int i = 0; i < 3 + made.tier; i++) {
                Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
                if (v == null) continue;
                v.snapTo(site.getX() + 2.5 - i * 2, site.getY(), site.getZ() + 2.5, level.getRandom().nextFloat() * 360f, 0f);
                if (i == 2) v.setBaby(true);
                level.addFreshEntity(v);
            }
        }
    }

    private static void update(ServerLevel level, Colony c, long t) {
        dirty = true;
        switch (c.state) {
            case "abandoned" -> {
                decay(level, c);
                return;
            }
            case "ruins", "fallen" -> {
                return;
            }
            default -> {
            }
        }
        long gap = c.lastSeen == 0 ? 0 : t - c.lastSeen;
        long clock = level.getOverworldClockTime();
        if (c.lastClock != 0) gap = Math.max(gap, clock - c.lastClock);
        c.lastClock = clock;
        c.lastSeen = t;
        if (gap > 2400) catchUp(level, c, gap, t);
        c.age += 100L * speed();
        if ((t / 100) % 240 == 0 && !c.grudges.isEmpty()) {
            c.grudges.replaceAll((k, v) -> v - 2);
            c.grudges.values().removeIf(v -> v <= 0);
        }
        discover(level, c);
        if (hostile(c.kind)) zombieLife(level, c, t);
        else villageLife(level, c, t);
        if (!c.active()) return;
        boolean watched = level.getNearestPlayer(c.x, c.y, c.z, 96, false) != null;
        if (watched && c.population == 0 && !(c.kind.equals("village") && c.attacker != null && c.age - c.lastRaidedAge < 72000)) {
            if (c.emptySince == 0) c.emptySince = c.age;
            if (c.age - c.emptySince >= (hostile(c.kind) ? 96000 : 48000)) {
                abandon(level, c);
                return;
            }
        } else if (c.population > 0) {
            c.emptySince = 0;
        }
        if (c.gloom > 0) gloom(level, c);
        boolean grows = canSettle(level, c.kind, c.center());
        if (grows && Camps.pending(c.id) == 0 && (t / 100) % 6 == 0) queue(level, c);
        if (warp >= 20 && Camps.pending(c.id) > 0) Camps.instantBuild(level, c.id);
        if (grows && Camps.pending(c.id) == 0 && c.tier >= maxTier(level, c) && c.extraHouses < extraCap(c)) {
            long base = Math.max(c.lastExtra, c.lastTier);
            if (c.age - base >= HOUSE_TIME) {
                c.extraHouses++;
                c.lastExtra = c.age;
                queue(level, c);
            }
        }
        if (grows) expand(level, c, 1);
        if (grows && Camps.pending(c.id) == 0 && c.age - c.lastTier >= TIER_TIME && c.tier < maxTier(level, c)) {
            c.tier++;
            c.lastTier = c.age;
            queue(level, c);
            BlockPos center = c.center();
            tell(level, center, 128, Component.translatable("mobslearn.colony.tier." + c.kind, c.tier + 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.getX() + 0.5, center.getY() + 3, center.getZ() + 0.5, 40, 2, 2, 2, 0.2);
        }
    }

    private static void queue(ServerLevel level, Colony c) {
        scanWood(level, c);
        BlockPos center = c.center();
        String crew = crewName(c.kind);
        RandomSource r = level.getRandom();
        if (c.camp) {
            Camps.addProject(level, "vcamp", 1, center, c.id, crew);
            return;
        }
        if (hostile(c.kind)) {
            int hall = c.tier < 1 ? 1 : c.tier < 3 ? 2 : 3;
            Camps.addProject(level, hallOf(c.kind), hall, center, c.id, crew, 0, style(c));
            int houses = (c.tier == 0 ? 1 : c.tier * 2) + c.extraHouses;
            for (int attempt = 0; attempt < 12 && Camps.count(c.id, houseOf(c.kind)) < houses; attempt++) {
                int idx = Camps.count(c.id, houseOf(c.kind)) + attempt;
                int ring = idx < 6 ? 12 : idx < 14 ? 19 : idx < 24 ? 26 : 33;
                double step = idx < 6 ? Math.PI / 3 : idx < 14 ? Math.PI / 4 : idx < 24 ? Math.PI / 5 : Math.PI / 7;
                double a = idx * step + (idx >= 6 ? 0.9 : 0.4) + r.nextGaussian() * 0.2;
                BlockPos want = center.offset((int) Math.round(Math.cos(a) * ring), 0, (int) Math.round(Math.sin(a) * ring));
                if (c.hasPartner && idx % 2 == 1) {
                    double f = 0.2 + r.nextDouble() * 0.7;
                    double dx = c.px - c.x, dz = c.pz - c.z, len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                    double side = (r.nextDouble() - 0.5) * 24;
                    want = new BlockPos((int) (c.x + dx * f - dz / len * side), c.y, (int) (c.z + dz * f + dx / len * side));
                }
                BlockPos site = Camps.siteNear(level, want, 3, 4, r, c.id);
                if (site != null) Camps.addProject(level, houseOf(c.kind), houseLevel(c), site, c.id, crew, facing(site, center), style(c));
            }
            if (houseOf(c.kind).equals("zhouse")) Camps.upgradeAll(level, c.id, "zhouse", houseLevel(c));
            c.houses = Camps.count(c.id, houseOf(c.kind));
            if (c.kind.equals("creeper") && c.tier >= 2 && Camps.count(c.id, "cmaze") == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    BlockPos site = Camps.siteNear(level, center.offset((int) (Math.cos(a) * 20), 0, (int) (Math.sin(a) * 20)), 6, 6, r);
                    if (site != null) {
                        Camps.addProject(level, "cmaze", 1, site, c.id, crew);
                        break;
                    }
                }
            }
            if (c.tier >= 2 && c.kind.equals("zombie") && Camps.count(c.id, "skeleton") == 0) {
                BlockPos site = Camps.siteNear(level, center.offset(0, 0, 16), 2, 5, r);
                if (site != null) Camps.addProject(level, "skeleton", 2, site, c.id, crew);
            }
            if (c.tier >= 3) {
                int rad = palisadeRadius(level, center);
                if (rad > 0) Camps.addProject(level, c.kind.equals("skeleton") ? "spalisade" : c.kind.equals("creeper") ? "chedge" : "palisade", rad, center, c.id, crew);
            }
        } else {
            Camps.addProject(level, "villager", Math.min(3, c.tier + 1), center, c.id, crew, 0, style(c));
            int adults = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(48, 24, 48), v -> !v.isBaby()).size();
            int want = Math.max(1, Math.min(homeCap(c), adults + 1));
            for (int attempt = 0; attempt < 6 && Camps.count(c.id, "vhouse") < want && (c.wood >= HOUSE_WOOD || Camps.count(c.id, "vhouse") < 2); attempt++) {
                if (houseNear(level, c, r)) c.wood = Math.max(0, c.wood - HOUSE_WOOD);
            }
            Camps.upgradeAll(level, c.id, "vhouse", houseLevel(c));
            c.houses = Camps.count(c.id, "vhouse");
            int farms = c.tier >= 1 ? Math.min(4, 1 + c.tier / 2 + c.extraHouses / 4) : 0;
            for (int attempt = 0; attempt < 4 && Camps.count(c.id, "vfarm") < farms; attempt++) {
                double a = r.nextDouble() * Math.PI * 2;
                int d = 16 + r.nextInt(14);
                BlockPos site = Camps.siteNear(level, center.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)), 4, 5, r, c.id, 2);
                if (site != null) Camps.addProject(level, "vfarm", 1, site, c.id, "villager", facing(site, center), style(c));
            }
            int allowed = Math.min(SPECIALS.length, 1 + c.tier + (c.extraHouses >= 4 ? 1 : 0) + c.extraHouses / 6);
            for (int i = 0; i < allowed; i++) {
                int type = SPECIALS[i];
                int need = 0;
                for (int k = 0; k <= i; k++) if (SPECIALS[k] == type) need++;
                if (Camps.countLevel(c.id, "vspecial", type) >= need || c.wood < SPECIAL_WOOD) continue;
                int d = type == 1 ? 10 : 14 + r.nextInt(12);
                double a = r.nextDouble() * Math.PI * 2;
                BlockPos site = Camps.siteNear(level, center.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)), type == 5 ? 4 : 3, 5, r, c.id, 2);
                if (site == null) continue;
                Camps.addProject(level, "vspecial", type, site, c.id, "villager", facing(site, center), style(c));
                if (type != 6) path(level, c, site, facing(site, center), type == 5 ? 6 : 5);
                c.wood -= SPECIAL_WOOD;
            }
        }
    }

    private static final int HOUSE_WOOD = 2, SPECIAL_WOOD = 2;
    private static final int[] SPECIALS = {1, 6, 7, 2, 3, 6, 4, 5, 7};

    private static int homeCap(Colony c) {
        return Math.min(30, 3 + c.tier * 3 + c.extraHouses);
    }

    static int style(Colony c) {
        int base = Math.abs(c.id.hashCode() % 100000);
        int n = Designs.WOODS.length;
        if (c.woodKind < 0) return base;
        return base - Math.floorMod(base, n) + c.woodKind;
    }

    private static int biomeWood(ServerLevel level, BlockPos at) {
        String b = level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        if (b.contains("cherry")) return 6;
        if (b.contains("mangrove")) return 7;
        if (b.contains("dark_forest")) return 3;
        if (b.contains("birch")) return 2;
        if (b.contains("jungle") || b.contains("bamboo")) return 5;
        if (b.contains("savanna") || b.contains("badlands") || b.contains("desert")) return 4;
        if (b.contains("taiga") || b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("peak")
            || b.contains("slope") || b.contains("windswept")) return 1;
        return 0;
    }

    private static void scanWood(ServerLevel level, Colony c) {
        if (c.woodKind >= 0 || !level.isLoaded(c.center())) return;
        int[] count = new int[Designs.LOGS.length];
        RandomSource r = level.getRandom();
        int seen = 0;
        for (int i = 0; i < 400; i++) {
            int x = c.x + r.nextInt(161) - 80, z = c.z + r.nextInt(161) - 80;
            if (!level.isLoaded(new BlockPos(x, c.y, z))) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int dy = 0; dy <= 20; dy++) {
                BlockState st = level.getBlockState(new BlockPos(x, h - dy, z));
                if (st.is(BlockTags.LEAVES) || st.isAir() || st.canBeReplaced()) continue;
                for (int k = 0; k < Designs.LOGS.length; k++) {
                    if (st.is(Designs.LOGS[k])) {
                        count[k]++;
                        seen++;
                    }
                }
                break;
            }
        }
        int best = 0;
        for (int k = 1; k < count.length; k++) if (count[k] > count[best]) best = k;
        c.woodKind = seen < 3 ? biomeWood(level, c.center()) : best;
        dirty = true;
    }

    static int facing(BlockPos site, BlockPos center) {
        int dx = center.getX() - site.getX(), dz = center.getZ() - site.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    private static void path(ServerLevel level, Colony c, BlockPos site, int rot) {
        path(level, c, site, rot, 4);
    }

    private static void path(ServerLevel level, Colony c, BlockPos site, int rot, int ahead) {
        int fx, fz;
        switch (rot) {
            case 1 -> { fx = ahead; fz = 0; }
            case 2 -> { fx = 0; fz = ahead; }
            case 3 -> { fx = -ahead; fz = 0; }
            default -> { fx = 0; fz = -ahead; }
        }
        BlockPos start = site.offset(fx, 0, fz);
        BlockPos center = c.center();
        double dx = start.getX() - center.getX(), dz = start.getZ() - center.getZ(), len = Math.sqrt(dx * dx + dz * dz);
        if (len < 6) return;
        int tx = (int) Math.round(center.getX() + dx / len * 4) - start.getX(), tz = (int) Math.round(center.getZ() + dz / len * 4) - start.getZ();
        if (Math.abs(tx) > 60 || Math.abs(tz) > 60) return;
        Camps.addProject(level, "vpath", ((tx + 512) << 10) | (tz + 512), start, c.id, "villager");
    }

    private static int palisadeRadius(ServerLevel level, BlockPos center) {
        for (int rad : new int[]{22, 18, 14}) {
            int bad = 0, total = 0;
            for (int i = -rad; i <= rad; i += 4) {
                for (int[] k : new int[][]{{i, -rad}, {i, rad}, {-rad, i}, {rad, i}}) {
                    BlockPos at = center.offset(k[0], 0, k[1]);
                    if (!level.isLoaded(at)) continue;
                    total++;
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                    BlockState g = level.getBlockState(new BlockPos(at.getX(), h - 1, at.getZ()));
                    if (Math.abs(h - center.getY()) > 5 || !g.getFluidState().isEmpty()) bad++;
                }
            }
            if (total > 0 && bad * 5 <= total) return rad;
        }
        return 0;
    }

    private static int houseLevel(Colony c) {
        return c.tier >= 4 ? 3 : c.tier >= 2 ? 2 : 1;
    }

    public static boolean nearVillage(ServerLevel level, BlockPos pos, int radius) {
        String dim = dimId(level);
        for (Colony o : colonies) {
            if (o.dim.equals(dim) && o.kind.equals("village") && !o.pending && pos.distSqr(o.center()) < (double) radius * radius) return true;
        }
        try {
            if (level.getPoiManager().findClosest(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), pos, radius,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).isPresent()) return true;
        } catch (Exception ignored) {
        }
        if (!level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(radius * 0.75, 24, radius * 0.75), v -> !v.isBaby()).isEmpty()) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = pos.offset(dx * 24, 0, dz * 24);
                if (level.isLoaded(at) && level.structureManager().getStructureWithPieceAt(at, net.minecraft.tags.StructureTags.VILLAGE).isValid()) return true;
            }
        }
        return false;
    }

    private static boolean houseNear(ServerLevel level, Colony c, RandomSource r) {
        BlockPos center = c.center();
        for (int i = 0; i < 8; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 12 + r.nextInt(10 + Math.min(16, c.houses * 2));
            BlockPos site = Camps.siteNear(level, center.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)), 3, 4, r, c.id);
            if (site != null) {
                int rot = facing(site, center);
                Camps.addProject(level, "vhouse", houseLevel(c), site, c.id, "villager", rot, style(c));
                path(level, c, site, rot);
                return true;
            }
        }
        return false;
    }

    private static List<Mob> residents(ServerLevel level, Colony c, double radius) {
        List<Mob> out = new ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(c.center()).inflate(radius, 32, radius))) {
            if (!m.isAlive()) continue;
            if (m.entityTags().contains(HOME + c.id)) out.add(m);
            else if (!aliases.isEmpty()) {
                for (String tag : List.copyOf(m.entityTags())) {
                    if (!tag.startsWith(HOME)) continue;
                    Colony real = byId(tag.substring(HOME.length()));
                    if (real == c) {
                        m.removeTag(tag);
                        m.addTag(HOME + c.id);
                        out.add(m);
                    }
                    break;
                }
            }
        }
        return out;
    }

    private static final java.util.Map<UUID, Climb> climbers = new java.util.HashMap<>();
    private static final String TOWER = "mobslearn.tower";

    private static final class Climb {
        BlockPos ladderBase, stand;
        int topY;
        long started;
        boolean climbing;
    }

    private static void towerGuards(ServerLevel level, Colony c, List<Mob> residents, long t) {
        int hall = c.tier < 1 ? 1 : c.tier < 3 ? 2 : 3;
        BlockPos center = c.center();
        BlockPos ladder = center.offset(0, 0, hall >= 3 ? 0 : -1);
        if (!level.getBlockState(ladder).is(Blocks.LADDER) && !level.getBlockState(ladder.above()).is(Blocks.LADDER)) return;
        int top = hall == 1 ? 5 : hall == 2 ? 8 : 9;
        BlockPos stand = hall == 1 ? center.offset(1, 5, 0) : hall == 2 ? center.offset(0, 8, 0) : center.offset(1, 9, 1);
        int up = 0;
        for (Mob m : residents) {
            if (m.entityTags().contains(TOWER) && m.getY() < center.getY() + top - 2) m.removeTag(TOWER);
            if (m.entityTags().contains(TOWER)) {
                if (m.getTarget() == null) m.getNavigation().stop();
                up++;
            } else if (climbers.containsKey(m.getUUID())) {
                up++;
            }
        }
        for (Mob m : residents) {
            if (up >= (hall >= 3 ? 2 : 1)) break;
            if (m.getTarget() != null || c.raiders.contains(m.getUUID()) || m.entityTags().contains(TOWER) || climbers.containsKey(m.getUUID())
                || m.entityTags().contains(CHIEF) || Builders.busy(m)) continue;
            Climb cl = new Climb();
            cl.ladderBase = ladder;
            cl.stand = stand;
            cl.topY = center.getY() + top;
            cl.started = t;
            climbers.put(m.getUUID(), cl);
            m.setPersistenceRequired();
            up++;
        }
    }

    private static void climbTick(ServerLevel level, long t) {
        for (java.util.Iterator<java.util.Map.Entry<UUID, Climb>> it = climbers.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof Mob m)) {
                if (t - e.getValue().started > 2400) it.remove();
                continue;
            }
            Climb cl = e.getValue();
            if (!m.isAlive() || m.getTarget() != null || t - cl.started > 900) {
                it.remove();
                continue;
            }
            double cx = cl.ladderBase.getX() + 0.5, cz = cl.ladderBase.getZ() + 0.5;
            if (!cl.climbing) {
                double dx = m.getX() - cx, dz = m.getZ() - cz;
                if (dx * dx + dz * dz < 1.6 * 1.6 && Math.abs(m.getY() - cl.ladderBase.getY()) < 2) {
                    cl.climbing = true;
                    m.getNavigation().stop();
                    m.snapTo(cx, m.getY(), cz, m.getYRot(), m.getXRot());
                } else if (t % 20 == 0) {
                    m.getNavigation().moveTo(cx, cl.ladderBase.getY(), cz - (level.getBlockState(cl.ladderBase.north()).isAir() ? 1 : 0), 0.9);
                }
                continue;
            }
            m.getNavigation().stop();
            if (m.getY() < cl.topY - 0.2) {
                m.setPos(cx, m.getY(), cz);
                m.setDeltaMovement(0, 0.2, 0);
                m.needsSync = true;
                m.fallDistance = 0;
                if (t % 6 == 0) level.playSound(null, m.blockPosition(), SoundEvents.LADDER_STEP, SoundSource.HOSTILE, 0.5f, 1f);
            } else {
                m.teleportTo(cl.stand.getX() + 0.5, cl.stand.getY(), cl.stand.getZ() + 0.5);
                m.addTag(TOWER);
                m.getNavigation().stop();
                it.remove();
            }
        }
    }

    private static void zombieLife(ServerLevel level, Colony c, long t) {
        BlockPos center = c.center();
        List<Mob> residents = residents(level, c, 72);
        c.population = residents.size();
        RandomSource r = level.getRandom();

        c.peace += 100L * speed();
        if (!c.neutral && c.peace >= PEACE_TIME && c.tier >= 1) {
            c.neutral = true;
            tell(level, center, 96, Component.translatable("mobslearn.colony.neutral." + c.kind));
            awardNear(level, center, 96, "peacemaker");
        }
        Mob chief = null;
        for (Mob m : residents) {
            if (m.entityTags().contains(CHIEF)) chief = m;
            c.chiefAlive = chief != null;
            if (c.neutral && m.getTarget() instanceof Player) m.setTarget(null);
        }
        if (chief != null && c.hallDone) homeChief(level, c, chief, center);

        int target = Math.min(8, 3 + c.tier);
        if (residents.size() < target && t >= c.nextSpawn && level.getDifficulty() != Difficulty.PEACEFUL) {
            Mob z = spawnResident(level, c, center.offset(r.nextInt(5) - 2, 0, -4));
            if (z != null && !residents.isEmpty()) {
                Mob elder = residents.get(r.nextInt(residents.size()));
                Behaviors.later(3, () -> inherit(level, z, elder));
            }
            c.nextSpawn = t + Math.max(20, Math.max(600, 2400 - 400L * c.tier) / speed());
        }
        for (Mob m : residents) {
            if (m.getTarget() == null && !c.raiders.contains(m.getUUID()) && m.distanceToSqr(center.getX(), center.getY(), center.getZ()) > 36 * 36
                && m.getNavigation().isDone()) {
                m.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
            }
        }

        if (c.hallDone && c.kind.equals("skeleton") && (t / 100) % 3 == 0) towerGuards(level, c, residents, t);
        if (c.kind.equals("creeper") && (t / 100) % 3 == 0) {
            BlockPos maze = Camps.originOf(c.id, "cmaze");
            if (maze != null) {
                for (Mob m : residents) {
                    if (m.getTarget() != null || m.entityTags().contains(CHIEF) || r.nextFloat() > 0.3f) continue;
                    m.getNavigation().moveTo(maze.getX() + 0.5 + r.nextInt(11) - 5, maze.getY(), maze.getZ() + 0.5 + r.nextInt(11) - 5, 0.8);
                }
            }
        }
        if (c.hallDone) {
            if (chief == null) {
                if (c.chiefDeadAge == 0) c.chiefDeadAge = c.age;
                double integrity = Camps.integrity(level, c.id, hallOf(c.kind));
                if (integrity >= 0 && integrity < 0.4) {
                    collapse(level, c, residents);
                    return;
                }
                if (c.chiefDeadTick == 0) c.chiefDeadTick = t;
                if (t - c.chiefDeadTick >= 1200) {
                    Mob heir = null;
                    for (Mob m : residents) if (!c.raiders.contains(m.getUUID()) && !Builders.busy(m)) heir = m;
                    if (heir != null) promote(level, c, heir);
                    else spawnChief(level, c, center);
                    c.chiefAlive = true;
                    c.chiefDeadAge = 0;
                    c.chiefDeadTick = 0;
                }
            } else {
                c.chiefDeadAge = 0;
                c.chiefDeadTick = 0;
            }
        }

        Colony enemy = near(level, center, "village", 220, true);
        if (enemy != null && (enemy.camp || c.kind.equals("creeper"))) enemy = null;
        if (enemy != null && c.tier >= 2 && !c.outposted && Math.sqrt(center.distSqr(enemy.center())) > 90
            && hostileNear(level, enemy.center(), 70) == null) {
            c.outposted = true;
            double dx = c.x - enemy.x, dz = c.z - enemy.z, len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            Colony outpost = new Colony();
            outpost.id = c.id + "o" + r.nextInt(1000);
            outpost.kind = c.kind;
            outpost.dim = c.dim;
            outpost.x = enemy.x + (int) (dx / len * 130);
            outpost.z = enemy.z + (int) (dz / len * 130);
            outpost.y = enemy.y;
            outpost.natural = true;
            outpost.pending = true;
            colonies.add(outpost);
        }

        long day = day(level);
        if (day % 4 == 2 && level.isDarkOutside() && c.lastRaidNightDay != day && c.tier >= 1 && !c.neutral && residents.size() >= 2) {
            c.lastRaidNightDay = day;
            for (int i = 0; i < 2 && level.getDifficulty() != Difficulty.PEACEFUL; i++) {
                Mob z = spawnResident(level, c, center.offset(r.nextInt(7) - 3, 0, -5));
                if (z != null) residents.add(z);
            }
            List<Mob> party = new ArrayList<>();
            for (Mob m : residents) if (m != chief && !Builders.busy(m)) party.add(m);
            Player player = level.getNearestPlayer(c.x, c.y, c.z, 160, false);
            if (enemy != null) startRaid(level, c, party, null, enemy, t);
            else if (player != null && !player.isCreative() && !player.isSpectator()) startRaid(level, c, party, player, null, t);
            tell(level, center, 160, Component.translatable("mobslearn.colony.raidnight." + c.kind));
            awardNear(level, center, 160, "raid_night");
            level.playSound(null, center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.8f);
            return;
        }
        raid(level, c, residents, enemy, t);
    }

    private static void startRaid(ServerLevel level, Colony c, List<Mob> party, Player player, Colony village, long t) {
        c.raiders.clear();
        for (Mob m : party) c.raiders.add(m.getUUID());
        c.raidPlayer = player == null ? null : player.getUUID();
        c.raidPos = village == null ? null : village.center();
        c.raidUntil = t + Math.max(1200, 6000 / Math.max(1, Math.min(warp, 5)));
        if (village != null) {
            village.lastRaidedAge = village.age;
            village.attacker = c.id;
        }
        for (Mob m : party) {
            if (player != null) m.getNavigation().moveTo(player, 1.15);
            else m.getNavigation().moveTo(village.x + 0.5, village.y, village.z + 0.5, 1.15);
        }
    }

    private static void raid(ServerLevel level, Colony c, List<Mob> residents, Colony enemy, long t) {
        if (!c.raiders.isEmpty()) {
            Player target = c.raidPlayer == null ? null : level.getPlayerByUUID(c.raidPlayer);
            if (t > c.raidUntil || (c.raidPlayer != null && (target == null || !target.isAlive()))) {
                c.raiders.clear();
                c.raidPlayer = null;
                c.raidPos = null;
                return;
            }
            for (UUID id : new ArrayList<>(c.raiders)) {
                if (!(level.getEntity(id) instanceof Mob m) || !m.isAlive()) {
                    c.raiders.remove(id);
                    continue;
                }
                if (target != null) {
                    if (c.neutral) continue;
                    if (m.distanceTo(target) > 14) m.getNavigation().moveTo(target, 1.15);
                    else m.setTarget(target);
                } else if (c.raidPos != null) {
                    if (m.distanceToSqr(c.raidPos.getX(), c.raidPos.getY(), c.raidPos.getZ()) > 14 * 14) {
                        m.getNavigation().moveTo(c.raidPos.getX() + 0.5, c.raidPos.getY(), c.raidPos.getZ() + 0.5, 1.15);
                    } else if (m.getTarget() == null) {
                        Villager prey = level.getNearestEntity(Villager.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat(), m,
                            m.getX(), m.getY(), m.getZ(), m.getBoundingBox().inflate(20));
                        if (prey != null) m.setTarget(prey);
                    }
                }
            }
            return;
        }
        if (c.tier < 2 || !level.isDarkOutside() || residents.size() < 3) return;
        if (enemy != null && t >= c.nextVillageRaid) {
            c.nextVillageRaid = t + Math.max(2400, 48000 / warp);
            List<Mob> party = new ArrayList<>();
            int size = Math.min(residents.size() - 2, 2 + c.tier / 2);
            for (Mob m : residents) {
                if (party.size() >= size) break;
                if (!Builders.busy(m) && !m.entityTags().contains(CHIEF)) party.add(m);
            }
            if (!party.isEmpty()) {
                startRaid(level, c, party, null, enemy, t);
                tell(level, enemy.center(), 96, Component.translatable("mobslearn.colony.village_raid." + c.kind));
                level.playSound(null, enemy.center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1.2f);
            }
            return;
        }
        if (c.neutral || t < c.nextRaid) return;
        Player target = level.getNearestPlayer(c.x, c.y, c.z, 120, false);
        if (target == null || target.isCreative() || target.isSpectator() || target.distanceToSqr(c.x, c.y, c.z) < 24 * 24) return;
        c.nextRaid = t + Math.max(2400, 24000 / warp);
        List<Mob> party = new ArrayList<>();
        int size = Math.min(residents.size() - 1, 1 + c.tier);
        for (Mob m : residents) {
            if (party.size() >= size) break;
            if (!Builders.busy(m) && !m.entityTags().contains(CHIEF)) party.add(m);
        }
        startRaid(level, c, party, target, null, t);
        if (target instanceof ServerPlayer sp) Milestones.award(sp, "colony_raid");
        level.playSound(null, target.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 1.3f);
    }

    private static final class Forester {
        String colony;
        BlockPos target;
        boolean plant, working;
        long started, next;
        int chopped;
    }

    private static final java.util.Map<UUID, Forester> foresters = new java.util.HashMap<>();

    private static void assignForesters(ServerLevel level, Colony c, List<Villager> villagers, long t) {
        if (c.wood >= 40 || level.isDarkOutside()) return;
        int have = 0;
        for (Forester f : foresters.values()) if (f.colony.equals(c.id)) have++;
        if (have >= (c.tier >= 2 ? 2 : 1)) return;
        RandomSource r = level.getRandom();
        Villager pick = null;
        for (Villager v : villagers) {
            if (v.isBaby() || v.isSleeping() || foresters.containsKey(v.getUUID()) || c.sortie.contains(v.getUUID())) continue;
            if (pick == null || r.nextBoolean()) pick = v;
        }
        if (pick == null) return;
        Forester f = new Forester();
        f.colony = c.id;
        f.started = t;
        f.target = findTree(level, c.center(), r);
        if (f.target == null) {
            for (int i = 0; i < 12 && f.target == null; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                int d = 16 + r.nextInt(16);
                int x = c.x + (int) (Math.cos(a) * d), z = c.z + (int) (Math.sin(a) * d);
                if (!level.isLoaded(new BlockPos(x, c.y, z))) continue;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos at = new BlockPos(x, h, z);
                if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && level.getBlockState(at).isAir()) f.target = at;
            }
            f.plant = true;
        }
        if (f.target == null) return;
        foresters.put(pick.getUUID(), f);
        pick.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(f.target, 0.6f, 1));
    }

    private static BlockPos findTree(ServerLevel level, BlockPos center, RandomSource r) {
        for (int i = 0; i < 90; i++) {
            int x = center.getX() + r.nextInt(81) - 40, z = center.getZ() + r.nextInt(81) - 40;
            if (Math.abs(x - center.getX()) < 8 && Math.abs(z - center.getZ()) < 8) continue;
            if (!level.isLoaded(new BlockPos(x, center.getY(), z))) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos top = new BlockPos(x, h - 1, z);
            if (!level.getBlockState(top).is(BlockTags.LEAVES)) continue;
            for (int dy = 1; dy <= 14; dy++) {
                BlockPos p = top.below(dy);
                BlockState st = level.getBlockState(p);
                if (st.is(BlockTags.LOGS)) {
                    while (level.getBlockState(p.below()).is(BlockTags.LOGS)) p = p.below();
                    if (!level.getBlockState(p.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) break;
                    BlockState side = level.getBlockState(p.north());
                    if (side.is(BlockTags.PLANKS)) break;
                    return p;
                }
                if (!st.is(BlockTags.LEAVES) && !st.isAir()) break;
            }
        }
        return null;
    }

    private static void foresterTick(ServerLevel level, long t) {
        for (java.util.Iterator<java.util.Map.Entry<UUID, Forester>> it = foresters.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Forester f = e.getValue();
            Colony c = byId(f.colony);
            if (c == null || t - f.started > 6000) {
                it.remove();
                continue;
            }
            if (!(level.getEntity(e.getKey()) instanceof Villager v)) {
                if (t - f.started > 1200) it.remove();
                continue;
            }
            if (!v.isAlive() || v.isSleeping() || level.isDarkOutside()) {
                it.remove();
                continue;
            }
            double d = Math.sqrt(v.distanceToSqr(f.target.getX() + 0.5, f.target.getY(), f.target.getZ() + 0.5));
            if (d > 3.2) {
                if (t % 10 == 0) {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(f.target, 0.6f, 1));
                    if (t - f.started > 1200) v.getNavigation().moveTo(f.target.getX() + 0.5, f.target.getY(), f.target.getZ() + 0.5, 0.6);
                }
                if (t - f.started > 2400 && d < 64) {
                    c.wood = Math.min(200, c.wood + 4);
                    dirty = true;
                    it.remove();
                }
                continue;
            }
            v.getLookControl().setLookAt(f.target.getX() + 0.5, f.target.getY() + 1, f.target.getZ() + 0.5);
            if (t < f.next) continue;
            f.next = t + Math.max(5, 15 / Math.max(1, Math.min(3, warp)));
            Builders.swing(v);
            if (f.plant) {
                if (level.getBlockState(f.target).isAir() && level.getBlockState(f.target.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) {
                    level.setBlockAndUpdate(f.target, (level.getRandom().nextBoolean() ? Blocks.OAK_SAPLING : Blocks.BIRCH_SAPLING).defaultBlockState());
                    level.playSound(null, f.target, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 1f, 1f);
                }
                it.remove();
                continue;
            }
            BlockPos log = null;
            for (int dy = 0; dy < 16 && log == null; dy++) {
                BlockPos p = f.target.above(dy);
                if (level.getBlockState(p).is(BlockTags.LOGS)) log = p;
            }
            if (log == null) {
                for (BlockPos p : BlockPos.betweenClosed(f.target.offset(-2, 1, -2), f.target.offset(2, 10, 2))) {
                    if (level.getBlockState(p).is(BlockTags.LOGS)) {
                        log = p.immutable();
                        break;
                    }
                }
            }
            if (log == null || f.chopped >= 24) {
                BlockPos base = f.target;
                if (level.getBlockState(base).isAir() && level.getBlockState(base.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) {
                    level.setBlockAndUpdate(base, Blocks.OAK_SAPLING.defaultBlockState());
                }
                it.remove();
                continue;
            }
            BlockState st = level.getBlockState(log);
            level.destroyBlock(log, false, v);
            level.playSound(null, log, st.getSoundType().getBreakSound(), SoundSource.NEUTRAL, 1f, 0.9f);
            f.chopped++;
            c.wood = Math.min(200, c.wood + 4);
            dirty = true;
        }
    }

    private static void villageLife(ServerLevel level, Colony c, long t) {
        BlockPos bell = c.center();
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(48, 24, 48));
        c.population = villagers.size();
        if (villagers.isEmpty()) {
            if (level.getNearestPlayer(c.x, c.y, c.z, 96, false) == null) return;
            if (c.emptySince == 0) c.emptySince = c.age;
            if (c.attacker != null && c.age - c.lastRaidedAge < 72000 && c.age - c.emptySince >= 2400) {
                fall(level, c);
            }
            return;
        }
        RandomSource rr = level.getRandom();
        if (c.camp) {
            if (villagers.size() >= 3) {
                c.camp = false;
                c.tier = 0;
                c.lastTier = c.age;
                BlockPos bellAt = bell.offset(0, 0, -5);
                int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bellAt.getX(), bellAt.getZ());
                bellAt = new BlockPos(bellAt.getX(), by, bellAt.getZ());
                if (level.getBlockState(bellAt).canBeReplaced()) level.setBlockAndUpdate(bellAt, Blocks.BELL.defaultBlockState());
                queue(level, c);
                tell(level, bell, 96, Component.translatable("mobslearn.colony.camp.grown"));
            } else if (c.age >= c.nextArrival) {
                c.nextArrival = c.age + 24000;
                if (rr.nextFloat() < 0.6f) {
                    double a = rr.nextDouble() * Math.PI * 2;
                    int x = c.x + (int) (Math.cos(a) * 26), z = c.z + (int) (Math.sin(a) * 26);
                    BlockPos from = new BlockPos(x, 64, z);
                    if (level.isLoaded(from)) {
                        Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
                        if (v != null) {
                            v.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, rr.nextFloat() * 360f, 0f);
                            level.addFreshEntity(v);
                            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, 0.6f, 3));
                            tell(level, bell, 64, Component.translatable("mobslearn.colony.camp.arrival"));
                        }
                    }
                }
            }
            return;
        }
        int adults = 0, babies = 0;
        for (Villager v : villagers) if (v.isBaby()) babies++; else adults++;
        int homes = Camps.count(c.id, "vhouse");
        if (adults > homeCap(c) + 1 && c.age - c.lastDaughter > 48000) {
            c.lastDaughter = c.age;
            for (int attempt = 0; attempt < 4; attempt++) {
                double a = rr.nextDouble() * Math.PI * 2;
                int dist = 190 + rr.nextInt(90);
                BlockPos want = bell.offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
                if (!level.isLoaded(want)) {
                    Colony pending = new Colony();
                    pending.id = c.id + "m" + rr.nextInt(100000);
                    pending.kind = "village";
                    pending.dim = c.dim;
                    pending.x = want.getX();
                    pending.z = want.getZ();
                    pending.y = c.y;
                    pending.natural = true;
                    pending.pending = true;
                    if (near(level, want, null, 150, false) != null) continue;
                    colonies.add(pending);
                } else {
                    BlockPos site = townSite(level, want, 32, 8);
                    if (site == null || near(level, site, null, 150, false) != null) continue;
                    Colony child = create(level, site, "village", true, 0, false);
                    if (child == null) continue;
                    if (level.getBlockState(site).canBeReplaced()) level.setBlockAndUpdate(site, Blocks.BELL.defaultBlockState());
                }
                int moved = 0;
                for (Villager v : villagers) {
                    if (moved >= 2 || v.isBaby() || v.isSleeping()) continue;
                    level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1, v.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                    v.discard();
                    moved++;
                }
                tell(level, bell, 128, Component.translatable("mobslearn.colony.moved"));
                dirty = true;
                break;
            }
        } else if (homes > adults + babies && villagers.size() < homeCap(c) + 2 && c.age - c.lastBirth > 36000) {
            c.lastBirth = c.age;
            baby(level, bell);
        }
        assignForesters(level, c, villagers, t);
        for (Villager v : villagers) {
            if (v.isInWall()) {
                BlockPos at = v.blockPosition();
                int y = Camps.groundY(level, at.getX(), at.getZ());
                if (y > at.getY()) v.snapTo(at.getX() + 0.5, y, at.getZ() + 0.5, v.getYRot(), 0f);
            } else if (v.isInWater() && v.distanceToSqr(bell.getX() + 0.5, v.getY(), bell.getZ() + 0.5) < 9) {
                int y = Camps.groundY(level, bell.getX() + 3, bell.getZ());
                v.snapTo(bell.getX() + 3.5, y, bell.getZ() + 0.5, v.getYRot(), 0f);
            }
        }
        if ((t / 100) % 6 == 0) c.wood = Math.min(200, c.wood + 1);
        if (warp >= 20) c.wood = Math.min(200, c.wood + 2);
        sortie(level, c, villagers, t);
        upkeep(level, c, villagers, t);
        long day = day(level);
        if (day % 3 == 1 && !level.isDarkOutside() && villagers.size() >= 2 && c.lastFairDay != day) {
            c.lastFairDay = day;
            c.fairUntil = t + 6000;
            tell(level, bell, 96, Component.translatable("mobslearn.colony.fair"));
            awardNear(level, bell, 64, "fair");
            net.minecraft.world.entity.Mob trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
            if (trader != null) {
                trader.snapTo(bell.getX() + 2.5, bell.getY(), bell.getZ() + 0.5, 0f, 0f);
                level.addFreshEntity(trader);
            }
        }
        if (t < c.fairUntil && !level.isDarkOutside()) {
            for (Villager v : villagers) {
                if (!v.isSleeping() && v.distanceToSqr(bell.getX(), bell.getY(), bell.getZ()) > 25) {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, 0.55f, 4));
                }
            }
            RandomSource r = level.getRandom();
            for (int i = 0; i < 3; i++) {
                level.sendParticles(r.nextBoolean() ? ParticleTypes.NOTE : ParticleTypes.FIREWORK,
                    bell.getX() + 0.5 + r.nextGaussian() * 3, bell.getY() + 2 + r.nextFloat() * 2, bell.getZ() + 0.5 + r.nextGaussian() * 3, 1, 0, 0, 0, 0.02);
            }
            for (ServerPlayer p : level.players()) {
                Long until = heroes.get(p.getUUID());
                if (until != null && until > t && p.distanceToSqr(bell.getX(), bell.getY(), bell.getZ()) < 32 * 32) {
                    p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 220, 0, true, false));
                }
            }
        }
    }

    private static void fall(ServerLevel level, Colony village) {
        BlockPos bell = village.center();
        Colony attacker = byId(village.attacker);
        colonies.remove(village);
        Camps.forgetColony(village.id);
        int tier = attacker == null ? 1 : Math.max(1, attacker.tier - 1);
        String takenKind = attacker == null ? "zombie" : attacker.kind;
        if (attacker != null && attacker.big && attacker.active()) {
            colonies.remove(village);
            Camps.forgetColony(village.id);
            attacker.extraHouses += 4;
            attacker.hasPartner = true;
            attacker.px = bell.getX();
            attacker.pz = bell.getZ();
            tell(level, bell, 160, Component.translatable("mobslearn.colony.village_fell." + takenKind));
            awardNear(level, bell, 160, "fallen_village");
            dirty = true;
            return;
        }
        Colony taken = create(level, bell, takenKind, true, tier, true);
        if (taken == null) return;
        taken.gloom = 60;
        if (attacker != null && attacker.active()) {
            for (Mob m : residents(level, attacker, 220)) {
                m.removeTag(HOME + attacker.id);
                m.addTag(HOME + taken.id);
                m.getNavigation().moveTo(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5, 1.0);
            }
            attacker.state = "abandoned";
            attacker.decay = 80;
            Camps.forgetColony(attacker.id);
        }
        tell(level, bell, 160, Component.translatable("mobslearn.colony.village_fell." + takenKind));
        awardNear(level, bell, 160, "fallen_village");
        level.playSound(null, bell, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 3f, 0.7f);
        dirty = true;
    }

    private static void collapse(ServerLevel level, Colony c, List<Mob> residents) {
        for (Mob m : residents) m.removeTag(HOME + c.id);
        c.state = "abandoned";
        c.decay = 80;
        c.raiders.clear();
        Camps.forgetColony(c.id);
        tell(level, c.center(), 128, Component.translatable("mobslearn.colony.collapsed." + c.kind));
        awardNear(level, c.center(), 64, "liberator");
        dirty = true;
    }

    private static void gloom(ServerLevel level, Colony c) {
        RandomSource r = level.getRandom();
        int samples = 25 * Math.min(warp, 10);
        for (int i = 0; i < samples; i++) {
            int x = c.x + r.nextInt(57) - 28, z = c.z + r.nextInt(57) - 28;
            if (!level.isLoaded(new BlockPos(x, c.y, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            transformGloom(level, new BlockPos(x, top, z), r);
            transformGloom(level, new BlockPos(x, top - 1, z), r);
            transformGloom(level, new BlockPos(x, c.y + r.nextInt(10) - 2, z), r);
        }
        c.gloom--;
    }

    private static void transformGloom(ServerLevel level, BlockPos at, RandomSource r) {
        BlockState s = level.getBlockState(at);
        BlockState n = null;
        if (s.is(Blocks.GRASS_BLOCK)) n = r.nextFloat() < 0.55f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
        else if (s.is(BlockTags.SMALL_FLOWERS) || s.is(Blocks.SHORT_GRASS)) n = r.nextFloat() < 0.4f ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState();
        else if (s.is(Blocks.TORCH)) n = Blocks.SOUL_TORCH.defaultBlockState();
        else if (s.is(Blocks.WALL_TORCH)) n = Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, s.getValue(WallTorchBlock.FACING));
        else if (s.is(Blocks.LANTERN)) n = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, s.getValue(LanternBlock.HANGING));
        else if (s.is(Blocks.COBBLESTONE)) n = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        else if (s.is(Blocks.STONE_BRICKS)) n = r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        else if (s.is(Blocks.GLASS_PANE) && r.nextFloat() < 0.4f) n = Blocks.AIR.defaultBlockState();
        else if (s.isAir() && r.nextFloat() < 0.12f && level.getBlockState(at.above()).isSolid()) n = Blocks.COBWEB.defaultBlockState();
        if (n != null && !s.hasBlockEntity()) {
            level.setBlockAndUpdate(at, n);
            if (n.is(Blocks.DEAD_BUSH) && !n.canSurvive(level, at)) level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
    }

    private static void decay(ServerLevel level, Colony c) {
        if (c.decay <= 0) {
            c.state = "ruins";
            return;
        }
        RandomSource r = level.getRandom();
        int samples = 30 * Math.min(warp, 10);
        for (int i = 0; i < samples; i++) {
            int x = c.x + r.nextInt(53) - 26, z = c.z + r.nextInt(53) - 26;
            BlockPos at = new BlockPos(x, c.y + r.nextInt(12) - 1, z);
            if (!level.isLoaded(at)) continue;
            BlockState s = level.getBlockState(at);
            if (s.hasBlockEntity()) continue;
            BlockState n = null;
            if (s.is(Blocks.COBBLESTONE)) n = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            else if (s.is(Blocks.STONE_BRICKS)) n = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            else if ((s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOODEN_SLABS) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.LADDER)) && r.nextFloat() < 0.25f) n = Blocks.AIR.defaultBlockState();
            else if (s.is(BlockTags.LOGS) && r.nextFloat() < 0.1f) n = Blocks.AIR.defaultBlockState();
            else if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT)) n = s.setValue(CampfireBlock.LIT, false);
            else if (s.is(Blocks.GRASS_BLOCK)) n = Blocks.COARSE_DIRT.defaultBlockState();
            else if (s.isAir() && r.nextFloat() < 0.15f && level.getBlockState(at.above()).isSolid()) n = Blocks.COBWEB.defaultBlockState();
            if (n != null) level.setBlockAndUpdate(at, n);
        }
        c.decay--;
    }

    private static Mob spawnResident(ServerLevel level, Colony c, BlockPos at) {
        Mob z = switch (c.kind) {
            case "skeleton" -> EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
            case "creeper" -> EntityTypes.CREEPER.create(level, EntitySpawnReason.EVENT);
            default -> (level.getRandom().nextFloat() < 0.15f ? EntityTypes.ZOMBIE_VILLAGER : EntityTypes.ZOMBIE).create(level, EntitySpawnReason.EVENT);
        };
        if (z == null) return null;
        if (c.kind.equals("skeleton")) z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        int y = level.isLoaded(at) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) : at.getY();
        z.snapTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        z.addTag(HOME + c.id);
        z.setPersistenceRequired();
        if (!c.kind.equals("creeper") && z.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            z.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        level.addFreshEntity(z);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, z.getX(), z.getY() + 0.8, z.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, z.blockPosition(), c.kind.equals("skeleton") ? SoundEvents.SKELETON_AMBIENT
            : c.kind.equals("creeper") ? SoundEvents.CREEPER_PRIMED : SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.8f);
        return z;
    }

    private static void homeChief(ServerLevel level, Colony c, Mob chief, BlockPos center) {
        double hx = center.getX() + 0.5, hz = center.getZ() + 1.5;
        double d = Math.sqrt(chief.distanceToSqr(hx, chief.getY(), hz));
        if (chief.getTarget() != null && d > 18) chief.setTarget(null);
        if (chief.getTarget() != null) return;
        if (d > 40 && level.getNearestPlayer(chief, 24) == null) {
            int y = Camps.groundY(level, center.getX(), center.getZ() + 1);
            chief.snapTo(hx, y, hz, chief.getYRot(), 0f);
            chief.getNavigation().stop();
        } else if (d > 6) {
            chief.getNavigation().moveTo(hx, center.getY(), hz, 0.9);
        }
    }

    private static void spawnChief(ServerLevel level, Colony c, BlockPos origin) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        Mob chief = spawnResident(level, c, origin.offset(0, 0, 1));
        if (chief == null) return;
        chief.addTag(CHIEF);
        chief.setCustomName(Component.translatable(switch (c.kind) {
            case "skeleton" -> "mobslearn.camp.captain";
            case "creeper" -> "mobslearn.camp.elder";
            default -> "mobslearn.camp.chief";
        }));
        chief.setCustomNameVisible(true);
        if (c.kind.equals("creeper")) {
            chief.setGlowingTag(false);
            return;
        }
        chief.setItemSlot(EquipmentSlot.HEAD, new ItemStack(c.tier >= 3 ? Items.IRON_HELMET : Items.GOLDEN_HELMET));
        if (c.kind.equals("zombie")) {
            chief.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(c.tier >= 2 ? Items.STONE_AXE : Items.WOODEN_AXE));
            chief.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    private static void inherit(ServerLevel level, Mob child, Mob elder) {
        if (!child.isAlive() || !elder.isAlive()) return;
        int copied = 0;
        for (String tag : new ArrayList<>(elder.entityTags())) {
            if (copied >= 2 || !tag.startsWith(TraitTags.PREFIX) || !tag.contains("=")) continue;
            String id = tag.substring(TraitTags.PREFIX.length(), tag.indexOf('='));
            Skill s = Skill.byId(id);
            MobGroup home = MobGroup.of(child.getType());
            if (s == null || s.group() != home || TraitTags.get(child, id) > 0) continue;
            int lvl = TraitTags.get(elder, id);
            if (lvl <= 0 || level.getRandom().nextFloat() > 0.5f) continue;
            Traits.apply(level, child, id, Math.min(lvl, 2), level.getRandom());
            copied++;
        }
        if (copied > 0) level.sendParticles(ParticleTypes.ENCHANT, child.getX(), child.getY() + 1.6, child.getZ(), 20, 0.3, 0.4, 0.3, 0.5);
    }

    private static int extraCap(Colony c) {
        return hostile(c.kind) ? (c.big ? 24 : 12) : 16;
    }

    private static int expand(ServerLevel level, Colony c, int limit) {
        if (c.camp || c.tier < (hostile(c.kind) ? 3 : 2)) return 0;
        int made = 0;
        long base = Math.max(c.lastDaughter, c.lastTier);
        RandomSource r = level.getRandom();
        while (made < limit && c.age - base >= DAUGHTER_TIME) {
            int same = 0;
            for (Colony o : colonies) {
                if (o.kind.equals(c.kind) && o.dim.equals(c.dim) && Math.abs(o.x - c.x) < 700 && Math.abs(o.z - c.z) < 700) same++;
            }
            if (same >= 6) break;
            base += DAUGHTER_TIME;
            made++;
            double a = r.nextDouble() * Math.PI * 2;
            int d = 170 + r.nextInt(150);
            Colony outpost = new Colony();
            outpost.id = c.id + "e" + r.nextInt(100000);
            outpost.kind = c.kind;
            outpost.dim = c.dim;
            outpost.x = c.x + (int) (Math.cos(a) * d);
            outpost.z = c.z + (int) (Math.sin(a) * d);
            outpost.y = c.y;
            outpost.natural = true;
            outpost.pending = true;
            colonies.add(outpost);
        }
        if (made > 0) {
            c.lastDaughter = base;
            dirty = true;
        }
        return made;
    }

    public static void structureDone(ServerLevel level, String colonyId, String kind, BlockPos origin) {
        Colony c = byId(colonyId);
        if (c == null) return;
        switch (kind) {
            case "zombie", "skeleton", "cgrove" -> {
                if (!hostile(c.kind) || !kind.equals(hallOf(c.kind))) break;
                c.hallDone = true;
                boolean hasChief = false;
                for (Mob m : residents(level, c, 64)) if (m.entityTags().contains(CHIEF)) hasChief = true;
                if (!hasChief) spawnChief(level, c, origin);
                fillChest(level, c, origin);
            }
            case "vhouse" -> {
                awardNear(level, origin, 16, "village_grows");
                tell(level, origin, 64, Component.translatable("mobslearn.colony.house.done"));
            }
            case "zhouse", "shouse", "cnest" -> {
                if (level.getDifficulty() != Difficulty.PEACEFUL) spawnResident(level, c, origin.offset(0, 0, -3));
            }
            case "vcamp" -> tell(level, origin, 64, Component.translatable("mobslearn.colony.camp.done"));
            default -> {
            }
        }
        dirty = true;
    }

    private static void fillChest(ServerLevel level, Colony c, BlockPos origin) {
        if (c.chestTier >= c.tier) return;
        BlockPos at = origin.offset(1, 0, 1);
        if (!(level.getBlockEntity(at) instanceof Container)) {
            at = origin.offset(2, 0, 2);
            if (!(level.getBlockEntity(at) instanceof Container) && level.getBlockState(at).canBeReplaced()) level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
        }
        if (!(level.getBlockEntity(at) instanceof Container chest)) return;
        c.chestTier = c.tier;
        RandomSource r = level.getRandom();
        List<ItemStack> pool = new ArrayList<>(List.of(
            new ItemStack(Items.IRON_INGOT, 2 + r.nextInt(4 + c.tier * 2)), new ItemStack(Items.GOLD_INGOT, 1 + r.nextInt(2 + c.tier)),
            new ItemStack(Items.BREAD, 2 + r.nextInt(5)), new ItemStack(Items.ARROW, 6 + r.nextInt(12)), new ItemStack(Items.COAL, 4 + r.nextInt(8)),
            new ItemStack(Items.LEATHER, 2 + r.nextInt(4)), new ItemStack(Items.BONE, 3 + r.nextInt(6)), new ItemStack(Items.GUNPOWDER, 2 + r.nextInt(5)),
            new ItemStack(Items.STRING, 2 + r.nextInt(6)), new ItemStack(Items.PUMPKIN_PIE, 1 + r.nextInt(3)), new ItemStack(Items.CARROT, 3 + r.nextInt(6)),
            new ItemStack(Items.IRON_SWORD), new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.SHIELD)));
        if (c.tier >= 1) pool.addAll(List.of(new ItemStack(Items.EMERALD, 1 + r.nextInt(3 + c.tier * 2)), new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.GOLDEN_CARROT, 2 + r.nextInt(4)), new ItemStack(Items.LAPIS_LAZULI, 3 + r.nextInt(8)), new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.NAME_TAG), new ItemStack(Items.SADDLE), new ItemStack(Items.BOOK, 2 + r.nextInt(3))));
        if (c.tier >= 2) pool.addAll(List.of(new ItemStack(Items.DIAMOND, 1 + r.nextInt(2)), new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + r.nextInt(8)),
            new ItemStack(Items.ENDER_PEARL, 1 + r.nextInt(3)), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.MUSIC_DISC_CAT),
            new ItemStack(Items.IRON_HORSE_ARMOR), new ItemStack(Items.CROSSBOW)));
        if (c.tier >= 3) pool.addAll(List.of(new ItemStack(Items.DIAMOND, 2 + r.nextInt(3)), new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.GOLDEN_HORSE_ARMOR), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.MUSIC_DISC_13)));
        if (c.tier >= 4) pool.addAll(List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.NETHERITE_SCRAP, 1 + r.nextInt(2)),
            new ItemStack(Items.DIAMOND_HORSE_ARMOR)));
        java.util.Collections.shuffle(pool, new java.util.Random(r.nextLong()));
        List<ItemStack> loot = pool.subList(0, Math.min(pool.size(), 4 + c.tier + r.nextInt(3)));
        for (ItemStack s : loot) {
            for (int tries = 0; tries < 10; tries++) {
                int slot = r.nextInt(chest.getContainerSize());
                if (chest.getItem(slot).isEmpty()) {
                    chest.setItem(slot, s);
                    break;
                }
            }
        }
        chest.setChanged();
    }

    public static void residentHurt(ServerLevel level, Mob victim, Player p) {
        Colony c = homeOf(victim);
        if (c == null) return;
        c.peace = 0;
        if (c.neutral) {
            c.neutral = false;
            tell(level, c.center(), 96, Component.translatable("mobslearn.colony.angry." + c.kind));
        }
        dirty = true;
    }

    public static final int WARN = 10, BAN = 20;

    public static String villageIdAt(ServerLevel level, BlockPos at) {
        Colony c = settlementAt(level, at, 64);
        return c == null || hostile(c.kind) ? null : c.id;
    }

    public static List<String> villageIds(ServerLevel level) {
        List<String> out = new ArrayList<>();
        String dim = dimId(level);
        for (Colony c : colonies) if (!hostile(c.kind) && c.dim.equals(dim)) out.add(c.id);
        return out;
    }

    public static void addWood(String id, int n) {
        Colony c = byId(id);
        if (c == null || hostile(c.kind)) return;
        c.wood = Math.min(200, c.wood + n);
        dirty = true;
    }

    public static int woodOf(String id) {
        Colony c = byId(id);
        return c == null ? 99 : c.wood;
    }

    public static int grudgeOf(String id, UUID player) {
        Colony c = byId(id);
        return c == null ? 0 : c.grudges.getOrDefault(player.toString(), 0);
    }

    private static Colony settlementAt(ServerLevel level, BlockPos at, int radius) {
        String dim = dimId(level);
        Colony best = null;
        double bd = (double) radius * radius;
        for (Colony c : colonies) {
            if (c.pending || !c.active() || !c.dim.equals(dim)) continue;
            double d = at.distSqr(c.center());
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static void setGrudge(ServerLevel level, Colony c, ServerPlayer p, int g) {
        String id = p.getUUID().toString();
        int before = c.grudges.getOrDefault(id, 0);
        g = Math.max(0, Math.min(60, g));
        if (g == 0) c.grudges.remove(id);
        else c.grudges.put(id, g);
        dirty = true;
        Component kind = Component.translatable("mobslearn.settlements.kind." + c.kind);
        if (before < WARN && g >= WARN && g < BAN) Net.notice(p, Component.translatable("mobslearn.colony.grudge", kind));
        if (before < BAN && g >= BAN) {
            Net.notice(p, Component.translatable("mobslearn.colony.banned", kind));
            if (hostile(c.kind)) {
                c.neutral = false;
                c.peace = 0;
            }
        }
        if (before > 0 && g == 0) Net.notice(p, Component.translatable("mobslearn.colony.forgiven", kind));
    }

    public static void offend(ServerLevel level, BlockPos at, ServerPlayer p, int pts) {
        if (p.isSpectator()) return;
        Colony c = settlementAt(level, at, 40);
        if (c == null) return;
        setGrudge(level, c, p, c.grudges.getOrDefault(p.getUUID().toString(), 0) + pts);
    }

    public static void blockBroken(Level lvl, Player player, BlockPos pos, BlockState state) {
        if (!(lvl instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return;
        if (state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAND)
            || state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(Blocks.GRAVEL) || state.is(BlockTags.SAPLINGS)) return;
        if (state.is(BlockTags.LOGS)) {
            boolean built = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockState n = level.getBlockState(pos.relative(d));
                if (n.is(BlockTags.PLANKS) || n.is(BlockTags.DOORS) || n.is(Blocks.GLASS_PANE) || n.is(Blocks.COBBLESTONE)) built = true;
            }
            if (!built) return;
        }
        offend(level, pos, sp, 2);
    }

    public static void apologize(ServerLevel level, Villager v, ServerPlayer p) {
        Colony c = near(level, v.blockPosition(), "village", 56, true);
        if (c == null) return;
        int g = c.grudges.getOrDefault(p.getUUID().toString(), 0);
        if (g <= 0) return;
        String key = "apology" + c.id + p.getUUID();
        long t = level.getGameTime();
        Long last = apologies.get(key);
        if (last != null && t - last < 6000) return;
        apologies.put(key, t);
        setGrudge(level, c, p, g - 8);
    }

    private static final java.util.Map<String, Long> apologies = new java.util.HashMap<>();

    private static void enforce(ServerLevel level, long t) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            Colony c = settlementAt(level, p.blockPosition(), 40);
            if (c == null) continue;
            int g = c.grudges.getOrDefault(p.getUUID().toString(), 0);
            if (p.isSprinting() && (p.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS) || p.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES))
                && c.kind.equals("village") && p.distanceToSqr(c.x, c.y, c.z) < 24 * 24 && (t / 20) % 5 == 0) {
                setGrudge(level, c, p, g + 1);
                g++;
            }
            if (g < BAN) continue;
            if (hostile(c.kind)) {
                for (Mob m : residents(level, c, 40)) if (m.getTarget() == null && m.distanceTo(p) < 24) m.setTarget(p);
                continue;
            }
            for (net.minecraft.world.entity.animal.golem.IronGolem golem : level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, p.getBoundingBox().inflate(24))) {
                if (golem.getTarget() == null) golem.setTarget(p);
            }
            Villager kicker = null;
            for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8), v -> !v.isBaby() && !v.isSleeping())) {
                if (kicker == null || v.distanceTo(p) < kicker.distanceTo(p)) kicker = v;
            }
            if (kicker == null) continue;
            kicker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, 0.7f, 1));
            if (kicker.distanceTo(p) < 2.6 && (t / 20) % 2 == 0) {
                Vec3 away = p.position().subtract(c.x + 0.5, p.getY(), c.z + 0.5);
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
                away = away.normalize();
                p.push(away.x * 0.9, 0.35, away.z * 0.9);
                p.needsSync = true;
                Builders.swing(kicker);
                level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 1f, 1.2f);
                if ((t / 20) % 6 == 0) Talk.say(level, kicker, Component.translatable("mobslearn.chat.banned." + level.getRandom().nextInt(3), p.getName()), 20);
            }
        }
    }

    public static boolean peaceful(Mob m) {
        Colony c = homeOf(m);
        return c != null && c.neutral;
    }

    public static void calm(Mob m) {
        if (!(m.getTarget() instanceof Player)) return;
        Colony c = homeOf(m);
        if (c != null && c.neutral && !c.raiders.contains(m.getUUID())) m.setTarget(null);
    }

    public static InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer psp && level instanceof ServerLevel sl && (entity instanceof Villager || entity.entityTags().contains(CHIEF))) {
            Colony home = entity instanceof Villager ? near(sl, entity.blockPosition(), "village", 56, true) : homeOf(entity);
            if (home != null) {
                int g = home.grudges.getOrDefault(psp.getUUID().toString(), 0);
                ItemStack held = player.getItemInHand(hand);
                if (g > 0 && held.is(Items.EMERALD)) {
                    int need = Math.max(1, (g + 1) / 2), pay = Math.min(need, held.getCount());
                    held.shrink(pay);
                    g -= pay * 2;
                    setGrudge(sl, home, psp, g);
                    sl.playSound(null, entity.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
                    if (g > 0) Net.notice(psp, Component.translatable("mobslearn.colony.paid", pay, (g + 1) / 2));
                    return InteractionResult.SUCCESS;
                }
                if (g >= BAN && entity instanceof Villager v) {
                    Talk.sayTo(psp, v, Component.translatable("mobslearn.chat.notrade." + sl.getRandom().nextInt(3), psp.getName()));
                    sl.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        if (!entity.entityTags().contains(CHIEF)) return InteractionResult.PASS;
        Colony c = homeOf(entity);
        if (c == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!c.neutral) {
            Net.notice(sp, Component.translatable("mobslearn.trade.hostile"));
            level.playSound(null, entity.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1f, 0.6f);
            return InteractionResult.SUCCESS;
        }
        if (ServerPlayNetworking.canSend(sp, Net.TradeOpenPayload.TYPE)) {
            ServerPlayNetworking.send(sp, new Net.TradeOpenPayload(entity.getId(), tradeKind(c), offers(c).toString()));
            level.playSound(null, entity.blockPosition(), c.kind.equals("skeleton") ? SoundEvents.SKELETON_AMBIENT
                : c.kind.equals("creeper") ? SoundEvents.CREEPER_PRIMED : SoundEvents.ZOMBIE_AMBIENT, SoundSource.NEUTRAL, 0.8f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    private record Offer(String a, int ac, String b, int bc, String r, int rc, int tier) {
    }

    private static String tradeKind(Colony c) {
        return c.kind.equals("skeleton") || c.kind.equals("creeper") ? c.kind : "zombie";
    }

    private static Offer[] offersOf(Colony c) {
        return switch (tradeKind(c)) {
            case "skeleton" -> SKELETON_OFFERS;
            case "creeper" -> CREEPER_OFFERS;
            default -> OFFERS;
        };
    }

    private static final Offer[] SKELETON_OFFERS = {
        new Offer("bone", 24, "", 0, "emerald", 1, 0),
        new Offer("emerald", 2, "", 0, "arrow", 16, 0),
        new Offer("emerald", 3, "bone", 16, "spectral_arrow", 12, 1),
        new Offer("diamond", 2, "", 0, "music_disc_cat", 1, 1),
        new Offer("emerald", 8, "bone_block", 8, "skeleton_skull", 1, 2),
        new Offer("emerald", 10, "", 0, "bow", 1, 2),
        new Offer("emerald", 24, "diamond", 2, "enchanted_golden_apple", 1, 3)};

    private static final Offer[] CREEPER_OFFERS = {
        new Offer("gunpowder", 16, "", 0, "emerald", 1, 0),
        new Offer("emerald", 4, "", 0, "tnt", 2, 0),
        new Offer("emerald", 3, "gunpowder", 20, "firework_rocket", 16, 1),
        new Offer("diamond", 2, "", 0, "music_disc_stal", 1, 1),
        new Offer("emerald", 8, "moss_block", 16, "creeper_head", 1, 2),
        new Offer("emerald", 12, "", 0, "totem_of_undying", 1, 2),
        new Offer("emerald", 24, "diamond", 2, "enchanted_golden_apple", 1, 3)};

    private static final Offer[] OFFERS = {
        new Offer("rotten_flesh", 16, "", 0, "emerald", 1, 0),
        new Offer("gold_ingot", 6, "", 0, "golden_apple", 1, 0),
        new Offer("emerald", 3, "rotten_flesh", 20, "experience_bottle", 8, 1),
        new Offer("diamond", 2, "", 0, "music_disc_13", 1, 1),
        new Offer("emerald", 8, "bone", 16, "zombie_head", 1, 2),
        new Offer("emerald", 12, "", 0, "totem_of_undying", 1, 2),
        new Offer("emerald", 24, "diamond", 2, "enchanted_golden_apple", 1, 3)};

    private static JsonArray offers(Colony c) {
        JsonArray arr = new JsonArray();
        Offer[] list = offersOf(c);
        for (int i = 0; i < list.length; i++) {
            Offer o = list[i];
            JsonObject j = new JsonObject();
            j.addProperty("i", i);
            j.addProperty("a", o.a);
            j.addProperty("ac", o.ac);
            j.addProperty("b", o.b);
            j.addProperty("bc", o.bc);
            j.addProperty("r", o.r);
            j.addProperty("rc", o.rc);
            j.addProperty("locked", o.tier > c.tier);
            j.addProperty("tier", o.tier + 1);
            arr.add(j);
        }
        return arr;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }

    private static int count(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void take(Player p, Item item, int amount) {
        for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(item)) continue;
            int k = Math.min(amount, s.getCount());
            s.shrink(k);
            amount -= k;
        }
    }

    public static void trade(ServerPlayer player, int chiefId, int offer) {
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(chiefId) instanceof Mob chief) || chief.distanceTo(player) > 8) return;
        Colony c = homeOf(chief);
        if (c == null || !c.neutral || offer < 0 || offer >= offersOf(c).length) return;
        Offer o = offersOf(c)[offer];
        if (o.tier > c.tier) return;
        Item a = item(o.a), b = o.b.isEmpty() ? null : item(o.b);
        if (count(player, a) < o.ac || (b != null && count(player, b) < o.bc)) {
            Net.notice(player, Component.translatable("mobslearn.trade.poor"));
            return;
        }
        take(player, a, o.ac);
        if (b != null) take(player, b, o.bc);
        ItemStack reward = new ItemStack(item(o.r), o.rc);
        if (!player.getInventory().add(reward)) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), reward));
        level.playSound(null, chief.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.5f, 1.6f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chief.getX(), chief.getY() + 2, chief.getZ(), 8, 0.4, 0.3, 0.4, 0);
        Milestones.award(player, "chief_trade");
    }

    private static Colony hostileNear(Level level, BlockPos pos, double radius) {
        String dim = dimId(level);
        for (Colony c : colonies) {
            if (c.dim.equals(dim) && hostile(c.kind) && (c.pending || c.active()) && pos.distSqr(c.center()) <= radius * radius) return c;
        }
        return null;
    }

    private static void seed(ServerLevel level, long t) {
        if (level.players().isEmpty() || !Builders.griefing(level)) return;
        RandomSource r = level.getRandom();
        int intel = WorldBrain.get().intelligence();
        float chance = Math.min(0.6f, 0.2f + intel * 0.004f) * Math.min(5, warp);
        String dim = dimId(level);
        for (Colony v : new ArrayList<>(colonies)) {
            if (!v.kind.equals("village") || !v.active() || v.camp || !v.dim.equals(dim) || t < v.nextSeed) continue;
            if (level.getNearestPlayer(v.x, v.y, v.z, 160, false) == null) continue;
            v.nextSeed = t + Math.max(1200, 24000 / warp);
            if (hostileNear(level, v.center(), 160) != null || r.nextFloat() > chance) continue;
            List<String> kinds = new ArrayList<>(List.of("zombie", "zombie", "zombie"));
            if (WorldBrain.levelAt("skeleton_tower", level, v.center()) > 0 || intel >= 20) kinds.add("skeleton");
            if (WorldBrain.levelAt("creeper_ambush", level, v.center()) > 0 || intel >= 30) kinds.add("creeper");
            double a = r.nextDouble() * Math.PI * 2;
            int d = 170 + r.nextInt(60);
            Colony seed = new Colony();
            seed.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + "s";
            seed.kind = kinds.get(r.nextInt(kinds.size()));
            seed.dim = dim;
            seed.x = v.x + (int) (Math.cos(a) * d);
            seed.z = v.z + (int) (Math.sin(a) * d);
            seed.y = v.y;
            seed.natural = true;
            seed.pending = true;
            colonies.add(seed);
            dirty = true;
        }
    }

    private static void discover(ServerLevel level, Colony c) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(c.x, c.y, c.z) > 72 * 72) continue;
            String id = p.getUUID().toString();
            if (c.known.contains(id) && !c.hidden.contains(id)) continue;
            c.hidden.remove(id);
            if (!c.known.contains(id)) c.known.add(id);
            Net.notice(p, Component.translatable("mobslearn.colony.discovered", Component.translatable("mobslearn.settlements.kind." + c.kind)));
            dirty = true;
        }
    }

    private static void abandon(ServerLevel level, Colony c) {
        c.state = "abandoned";
        c.decay = 60;
        c.raiders.clear();
        Camps.forgetColony(c.id);
        tell(level, c.center(), 128, Component.translatable("mobslearn.colony.abandoned", Component.translatable("mobslearn.settlements.kind." + c.kind)));
        dirty = true;
    }

    private static void sortie(ServerLevel level, Colony c, List<Villager> villagers, long t) {
        BlockPos bell = c.center();
        if (!c.sortie.isEmpty()) {
            Colony enemy = hostileNear(level, bell, 100);
            int alive = 0;
            for (UUID id : c.sortie) if (level.getEntity(id) instanceof Villager v && v.isAlive()) alive++;
            boolean retreat = enemy == null || t > c.sortieUntil || alive * 2 < c.sortie.size() || level.isDarkOutside();
            for (UUID id : new ArrayList<>(c.sortie)) {
                if (!(level.getEntity(id) instanceof Villager v) || !v.isAlive()) continue;
                boolean hurt = v.getHealth() < v.getMaxHealth() * 0.5f;
                if (retreat || hurt) {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, 0.7f, 3));
                    if (hurt && !retreat) c.sortie.remove(id);
                } else {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(enemy.center(), 0.65f, 3));
                }
            }
            if (retreat) {
                if (!c.retreating) tell(level, bell, 128, Component.translatable("mobslearn.colony.sortie.retreat"));
                c.sortie.clear();
                c.retreating = false;
            }
            return;
        }
        if (t < c.nextSortie || level.isDarkOutside()) return;
        int sword = WorldBrain.levelAt("villager_sword", level, bell);
        if (sword <= 0) return;
        Colony enemy = hostileNear(level, bell, 90);
        if (enemy == null || enemy.pending || !level.isLoaded(enemy.center())) return;
        c.nextSortie = t + Math.max(2400, 24000 / warp);
        int foes = residents(level, enemy, 48).size();
        List<Villager> fighters = new ArrayList<>();
        for (Villager v : villagers) if (!v.isBaby() && !v.isSleeping() && v.getHealth() > v.getMaxHealth() * 0.7f) fighters.add(v);
        List<net.minecraft.world.entity.animal.golem.IronGolem> golems =
            level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, new AABB(bell).inflate(48, 16, 48));
        double power = Math.min(6, fighters.size()) * (0.5 + 0.5 * sword) + golems.size() * 4;
        if (power <= foes * 1.5 + 1 || fighters.isEmpty()) return;
        for (int i = 0; i < Math.min(4, fighters.size()); i++) {
            Villager v = fighters.get(i);
            c.sortie.add(v.getUUID());
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(enemy.center(), 0.65f, 3));
        }
        List<Mob> targets = residents(level, enemy, 48);
        for (net.minecraft.world.entity.animal.golem.IronGolem g : golems) {
            g.getNavigation().moveTo(enemy.x + 0.5, enemy.y, enemy.z + 0.5, 1.0);
            if (!targets.isEmpty()) g.setTarget(targets.get(level.getRandom().nextInt(targets.size())));
        }
        c.sortieUntil = t + 3600;
        tell(level, bell, 128, Component.translatable("mobslearn.colony.sortie.go"));
        awardNear(level, bell, 96, "counterattack");
    }

    public static void forget(ServerPlayer player, String id) {
        Colony c = byId(id);
        if (c == null) return;
        String me = player.getUUID().toString();
        if (!c.hidden.contains(me)) c.hidden.add(me);
        c.known.remove(me);
        dirty = true;
        sendList(player);
    }

    public static void sendList(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Net.ColoniesPayload.TYPE)) return;
        String dim = dimId(player.level());
        List<Colony> list = new ArrayList<>();
        String me = player.getUUID().toString();
        for (Colony c : colonies) if (!c.pending && c.dim.equals(dim) && !c.hidden.contains(me)) list.add(c);
        list.sort(Comparator.comparingDouble(c -> player.distanceToSqr(c.x, c.y, c.z)));
        JsonArray arr = new JsonArray();
        for (int i = 0; i < Math.min(40, list.size()); i++) {
            Colony c = list.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("kind", c.kind);
            j.addProperty("state", c.state);
            j.addProperty("tier", c.tier + 1);
            j.addProperty("x", c.x);
            j.addProperty("y", c.y);
            j.addProperty("z", c.z);
            j.addProperty("pop", c.population);
            j.addProperty("neutral", c.neutral);
            j.addProperty("raiding", !c.raiders.isEmpty());
            j.addProperty("projects", Camps.pending(c.id));
            boolean known = c.known.contains(player.getUUID().toString());
            j.addProperty("known", known);
            if (!known) {
                j.addProperty("x", 0);
                j.addProperty("y", 0);
                j.addProperty("z", 0);
            }
            j.addProperty("chief", c.chiefAlive);
            j.addProperty("peace", hostile(c.kind) ? (int) Math.min(100, c.peace * 100 / PEACE_TIME) : -1);
            j.addProperty("houses", c.houses);
            j.addProperty("days", c.age / 24000);
            j.addProperty("natural", c.natural);
            j.addProperty("id", c.id);
            j.addProperty("wood", c.wood);
            j.addProperty("grudge", c.grudges.getOrDefault(player.getUUID().toString(), 0));
            j.addProperty("camp", c.camp);
            int max = player.level() instanceof ServerLevel sl ? maxTier(sl, c) : c.tier;
            j.addProperty("maxTier", max + 1);
            j.addProperty("next", c.tier >= max ? -1 : (int) Math.min(100, (c.age - c.lastTier) * 100 / TIER_TIME));
            arr.add(j);
        }
        JsonObject root = new JsonObject();
        root.add("colonies", arr);
        root.addProperty("warp", warp);
        ServerPlayNetworking.send(player, new Net.ColoniesPayload(root.toString()));
    }

    private static void markMaps(ServerLevel level) {
        if (colonies.isEmpty()) return;
        String dim = dimId(level);
        for (ServerPlayer p : level.players()) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack stack = p.getInventory().getItem(i);
                if (!stack.is(Items.FILLED_MAP)) continue;
                MapDecorations existing = stack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
                for (Colony c : colonies) {
                    if (c.pending || !c.dim.equals(dim) || !c.known.contains(p.getUUID().toString()) || p.distanceToSqr(c.x, c.y, c.z) > 2048.0 * 2048.0) continue;
                    Holder<MapDecorationType> type = c.kind.equals("village") ? MapDecorationTypes.PLAINS_VILLAGE
                        : c.active() ? MapDecorationTypes.RED_X : MapDecorationTypes.ABANDONED_CAMP;
                    String key = "mobslearn_" + c.id;
                    MapDecorations.Entry e = existing.decorations().get(key);
                    if (e != null && e.type().equals(type)) continue;
                    MapItemSavedData.addTargetDecoration(stack, c.center(), key, type);
                }
            }
        }
    }

    public static void villagerTick(ServerLevel level, Villager v, long t, RandomSource r) {
        long phase = (t / 5 + v.getId()) % 80;
        if (v.isBaby()) {
            if (TraitTags.get(v, "wasbaby") == 0) TraitTags.set(v, "wasbaby", 1);
        } else if (TraitTags.get(v, "wasbaby") > 0) {
            TraitTags.set(v, "wasbaby", 0);
            grownUp(level, v);
        }
        if (phase == 0) {
            var meeting = v.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
            if (meeting.isPresent()) village(level, meeting.get());
            else if (!v.isBaby() && near(level, v.blockPosition(), "village", 96, false) == null && Builders.griefing(level)) loner(level, v);
        }
        Colony village = (phase % 12 == 3 || phase % 40 == 7) ? near(level, v.blockPosition(), "village", 64, true) : null;
        if (village == null) return;
        if (phase % 12 == 3 && village.tier >= 1 && Builders.griefing(level)) clearSnow(level, v);
        if (phase % 40 == 7 && !v.isSleeping() && t >= village.nextSocial) {
            for (Villager other : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(3))) {
                if (other == v || other.isBaby() || other.isSleeping()) continue;
                v.getLookControl().setLookAt(other, 30f, 30f);
                other.getLookControl().setLookAt(v, 30f, 30f);
                v.getNavigation().stop();
                other.getNavigation().stop();
                level.sendParticles(ParticleTypes.NOTE, (v.getX() + other.getX()) / 2, v.getY() + 2.3, (v.getZ() + other.getZ()) / 2, 2, 0.4, 0.1, 0.4, 0.5);
                level.playSound(null, v.blockPosition(), r.nextBoolean() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 0.9f + r.nextFloat() * 0.3f);
                village.nextSocial = t + 200;
                break;
            }
        }
    }

    private static void village(ServerLevel level, GlobalPos gp) {
        if (!gp.dimension().equals(level.dimension())) return;
        BlockPos bell = gp.pos();
        if (!level.isLoaded(bell) || near(level, bell, null, 48, false) != null) return;
        create(level, bell, "village", true, 0, false);
    }

    public static boolean resident(Entity m) {
        for (String tag : m.entityTags()) if (tag.startsWith(HOME)) return true;
        return false;
    }

    private static final java.util.Map<UUID, Integer> defense = new java.util.HashMap<>();

    public static void mobKilled(ServerLevel level, ServerPlayer p, Mob m) {
        if (!(m instanceof net.minecraft.world.entity.monster.Enemy)) return;
        boolean raider = false;
        for (Colony c : colonies) if (c.raiders.remove(m.getUUID())) raider = true;
        Colony v = near(level, m.blockPosition(), "village", 48, true);
        if (v == null || (!raider && !level.isDarkOutside())) return;
        int score = defense.merge(p.getUUID(), raider ? 2 : 1, Integer::sum);
        if (score < 6) return;
        defense.remove(p.getUUID());
        helpPoint(level, p, v.center());
    }

    public static boolean villageNear(ServerLevel level, BlockPos pos) {
        return near(level, pos, "village", 64, true) != null;
    }

    public static void gift(ServerLevel level, BlockPos pos, String what, ServerPlayer p) {
        Colony c = near(level, pos, "village", 64, true);
        helpPoint(level, p, pos);
        if (c == null) return;
        RandomSource r = level.getRandom();
        switch (what) {
            case "planks" -> {
                c.extraHouses++;
                houseNear(level, c, r);
            }
            case "bread" -> {
                baby(level, c.center());
                baby(level, c.center());
            }
            case "iron" -> {
                net.minecraft.world.entity.animal.golem.IronGolem g = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (g != null) {
                    BlockPos at = c.center().offset(2, 0, 2);
                    g.snapTo(at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5, 0f, 0f);
                    level.addFreshEntity(g);
                }
            }
            default -> c.lastTier -= TIER_TIME / 2;
        }
        dirty = true;
    }

    private static void catchUp(ServerLevel level, Colony c, long gap, long t) {
        if (!c.active()) return;
        long gain = gap * speed();
        c.age += gain;
        if (!hostile(c.kind)) c.wood = (int) Math.min(200, c.wood + gain / 600);
        if (hostile(c.kind)) c.peace += gain;
        Camps.instantBuild(level, c.id);
        int grown = 0;
        while (c.tier < maxTier(level, c) && c.age - c.lastTier >= TIER_TIME && grown < 8) {
            c.tier++;
            c.lastTier += TIER_TIME;
            queue(level, c);
            Camps.instantBuild(level, c.id);
            grown++;
        }
        long extraBase = Math.max(c.lastExtra, c.lastTier);
        if (c.tier >= maxTier(level, c)) {
            while (c.extraHouses < extraCap(c) && c.age - extraBase >= HOUSE_TIME) {
                c.extraHouses++;
                extraBase += HOUSE_TIME;
                grown++;
            }
            c.lastExtra = extraBase;
        }
        if (grown > 0) {
            for (int pass = 0; pass < 4; pass++) {
                queue(level, c);
                Camps.instantBuild(level, c.id);
            }
        }
        expand(level, c, 3);
        BlockPos center = c.center();
        if (hostile(c.kind) && level.getDifficulty() != Difficulty.PEACEFUL) {
            int have = residents(level, c, 72).size(), target = Math.min(8, 3 + c.tier);
            for (int i = have; i < target; i++) spawnResident(level, c, center.offset(level.getRandom().nextInt(9) - 4, 0, -5));
        } else if (!hostile(c.kind) && !c.camp) {
            int have = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(48, 24, 48)).size();
            for (int i = have; i < Math.min(have + grown * 2, 4 + 3 * c.tier); i++) baby(level, center);
        }
        if (grown > 0) tell(level, center, 128, Component.translatable("mobslearn.colony.grew_away", Component.translatable(
            "mobslearn.settlements.kind." + c.kind), c.tier + 1));
        dirty = true;
    }

    private static void baby(ServerLevel level, BlockPos at) {
        Villager b = EntityTypes.VILLAGER.create(level, EntitySpawnReason.BREEDING);
        if (b == null) return;
        b.setBaby(true);
        int x = at.getX() + level.getRandom().nextInt(7) - 3, z = at.getZ() + level.getRandom().nextInt(7) - 3;
        b.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        level.addFreshEntity(b);
        level.sendParticles(ParticleTypes.HEART, b.getX(), b.getY() + 0.8, b.getZ(), 5, 0.4, 0.3, 0.4, 0);
    }

    private static void promote(ServerLevel level, Colony c, Mob heir) {
        heir.addTag(CHIEF);
        heir.setCustomName(Component.translatable(switch (c.kind) {
            case "skeleton" -> "mobslearn.camp.captain";
            case "creeper" -> "mobslearn.camp.elder";
            default -> "mobslearn.camp.chief";
        }));
        heir.setCustomNameVisible(true);
        if (!c.kind.equals("creeper")) {
            heir.setItemSlot(EquipmentSlot.HEAD, new ItemStack(c.tier >= 3 ? Items.IRON_HELMET : Items.GOLDEN_HELMET));
            heir.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, heir.getX(), heir.getY() + 1.5, heir.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        tell(level, c.center(), 96, Component.translatable("mobslearn.colony.new_chief", Component.translatable("mobslearn.settlements.kind." + c.kind)));
    }

    public static InteractionResult chestOpened(Player player, Level lvl, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(lvl instanceof ServerLevel level) || player.isSpectator()) return InteractionResult.PASS;
        BlockState s = level.getBlockState(hit.getBlockPos());
        if (!s.is(Blocks.CHEST) && !s.is(Blocks.BARREL)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) offend(level, hit.getBlockPos(), sp, 3);
        if (player.isCreative()) return InteractionResult.PASS;
        Colony c = hostileNear(level, hit.getBlockPos(), 30);
        if (c == null || c.pending || !c.active()) return InteractionResult.PASS;
        c.peace = 0;
        boolean was = c.neutral;
        c.neutral = false;
        int angry = 0;
        for (Mob m : residents(level, c, 48)) {
            m.setTarget(player);
            angry++;
        }
        if (angry > 0 || was) {
            if (player instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.colony.thief"));
            level.playSound(null, hit.getBlockPos(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8f, 1.5f);
        }
        dirty = true;
        return InteractionResult.PASS;
    }

    private static long cell(int x, int z) {
        return ((long) (x >> 8) << 32) | ((z >> 8) & 0xFFFFFFFFL);
    }

    private static void remember(ServerLevel level, long t) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        var map = visited.computeIfAbsent(dimId(level), k -> new java.util.HashMap<>());
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) map.put(cell(p.getBlockX(), p.getBlockZ()), t);
        }
        if (map.size() > 600) {
            long oldest = Long.MAX_VALUE, key = 0;
            for (var e : map.entrySet()) if (e.getValue() < oldest) {
                oldest = e.getValue();
                key = e.getKey();
            }
            map.remove(key);
        }
        dirty = true;
    }

    private static void seedVisited(ServerLevel level, long t) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        var map = visited.get(dimId(level));
        if (map == null || map.isEmpty() || colonies.size() >= 90 || !Builders.griefing(level)) return;
        RandomSource r = level.getRandom();
        if (r.nextFloat() > Math.min(0.9f, 0.25f * Math.min(4, warp))) return;
        List<Long> cells = new ArrayList<>(map.keySet());
        long pick = cells.get(r.nextInt(cells.size()));
        int cx = (int) (pick >> 32), cz = (int) pick;
        int x = (cx << 8) + 32 + r.nextInt(192), z = (cz << 8) + 32 + r.nextInt(192);
        BlockPos at = new BlockPos(x, 64, z);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(x, p.getY(), z) < 200 * 200) return;
        if (near(level, at, null, 180, false) != null) return;
        int intel = WorldBrain.get().intelligence();
        List<String> kinds = new ArrayList<>(List.of("zombie", "zombie", "village", "village"));
        if (intel >= 10) kinds.add("skeleton");
        if (intel >= 20) kinds.add("creeper");
        Colony seed = new Colony();
        seed.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + "v";
        seed.kind = kinds.get(r.nextInt(kinds.size()));
        seed.dim = dimId(level);
        seed.x = x;
        seed.z = z;
        seed.y = 64;
        seed.natural = true;
        seed.pending = true;
        seed.lastSeen = t;
        colonies.add(seed);
        dirty = true;
    }

    private static void upkeep(ServerLevel level, Colony c, List<Villager> villagers, long t) {
        if (c.tier < 1 || villagers.isEmpty() || !Builders.griefing(level) || t < c.nextRepair) return;
        c.nextRepair = t + Math.max(20, 200 / Math.min(speed(), 10));
        RandomSource r = level.getRandom();
        BlockPos bell = c.center();
        for (int i = 0; i < 8; i++) {
            int x = c.x + r.nextInt(57) - 28, z = c.z + r.nextInt(57) - 28;
            if (!level.isLoaded(new BlockPos(x, c.y, z))) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int[] around = {level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 2, z), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x - 2, z),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + 2), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z - 2)};
            java.util.Arrays.sort(around);
            int median = (around[1] + around[2]) / 2;
            BlockPos top = new BlockPos(x, h, z);
            BlockState ground = level.getBlockState(top.below());
            if (median - h >= 2 && Math.abs(median - c.y) <= 6 && ground.getFluidState().isEmpty() && (ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(BlockTags.BASE_STONE_OVERWORLD))) {
                level.setBlockAndUpdate(top, h + 1 >= median ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                worker(level, villagers, top, Items.WOODEN_SHOVEL);
                continue;
            }
            BlockState surface = level.getBlockState(top.below());
            if ((surface.is(Blocks.DIRT) || surface.is(Blocks.COARSE_DIRT) || surface.is(Blocks.GRASS_BLOCK)) && level.getBlockState(top).isAir()) {
                int paths = 0;
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    if (level.getBlockState(top.below().relative(d)).is(Blocks.DIRT_PATH)) paths++;
                }
                if (paths >= 2 && !surface.is(Blocks.GRASS_BLOCK) || paths >= 3) {
                    level.setBlockAndUpdate(top.below(), Blocks.DIRT_PATH.defaultBlockState());
                    worker(level, villagers, top, Items.WOODEN_SHOVEL);
                }
            }
        }
        if (c.tier >= 1 && villagers.size() < 4 + 3 * c.tier && t >= c.nextBirth && !level.isDarkOutside()) {
            c.nextBirth = t + Math.max(200, 9600 / speed());
            if (r.nextFloat() < 0.5f) baby(level, bell);
        }
    }

    private static void worker(ServerLevel level, List<Villager> villagers, BlockPos at, Item tool) {
        Villager best = null;
        double bd = 24 * 24;
        for (Villager v : villagers) {
            double d = v.distanceToSqr(at.getX(), at.getY(), at.getZ());
            if (d < bd && !v.isSleeping()) {
                bd = d;
                best = v;
            }
        }
        level.playSound(null, at, SoundEvents.GRAVEL_PLACE, SoundSource.NEUTRAL, 0.7f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0);
        if (best == null) return;
        Villager v = best;
        Builders.present(v, new ItemStack(tool));
        Builders.swing(v);
        v.getLookControl().setLookAt(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        Behaviors.later(30, () -> Builders.clearPresentation(v));
    }

    private static void loner(ServerLevel level, Villager v) {
        BlockPos site = townSite(level, v.blockPosition(), 16, 4);
        if (site == null) return;
        Colony c = new Colony();
        c.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + "c";
        c.kind = "village";
        c.dim = dimId(level);
        c.x = site.getX();
        c.y = site.getY();
        c.z = site.getZ();
        c.natural = true;
        c.camp = true;
        c.nextArrival = 24000;
        colonies.add(c);
        queue(level, c);
        dirty = true;
        tell(level, site, 64, Component.translatable("mobslearn.colony.camp.start"));
    }

    private static void grownUp(ServerLevel level, Villager v) {
        Colony c = near(level, v.blockPosition(), "village", 80, true);
        if (c == null) return;
        c.extraHouses++;
        houseNear(level, c, level.getRandom());
        dirty = true;
        tell(level, v.blockPosition(), 64, Component.translatable("mobslearn.colony.house.start"));
    }

    private static void clearSnow(ServerLevel level, Villager v) {
        BlockPos feet = v.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = feet.offset(dx, 0, dz);
                BlockState s = level.getBlockState(at);
                if (!s.is(Blocks.SNOW)) continue;
                BlockState below = level.getBlockState(at.below());
                if (!below.is(Blocks.DIRT_PATH) && !below.is(Blocks.GRAVEL) && !below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.COBBLESTONE)) continue;
                Builders.present(v, new ItemStack(Items.WOODEN_SHOVEL));
                Builders.swing(v);
                v.getLookControl().setLookAt(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                level.playSound(null, at, SoundEvents.SNOW_BREAK, SoundSource.NEUTRAL, 0.8f, 1f);
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
                awardNear(level, at, 16, "snow_day");
                Behaviors.later(30, () -> Builders.clearPresentation(v));
                return;
            }
        }
    }

    private static final java.util.Map<String, String> relations = new java.util.HashMap<>();
    private static final java.util.Map<String, String> aliases = new java.util.HashMap<>();
    private static final java.util.Map<UUID, String> warriors = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> warUntil = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> nextBattle = new java.util.HashMap<>();

    private static String pair(Colony a, Colony b) {
        return a.id.compareTo(b.id) < 0 ? a.id + "|" + b.id : b.id + "|" + a.id;
    }

    private static String relation(Colony a, Colony b) {
        return relations.getOrDefault(pair(a, b), "");
    }

    public static String relationOf(String a, String b) {
        Colony ca = byId(a), cb = byId(b);
        return ca == null || cb == null ? "" : relation(ca, cb);
    }

    public static String friendOf(String id) {
        Colony c = byId(id);
        if (c == null) return null;
        for (Colony o : colonies) if (o != c && relation(c, o).equals("friend")) return o.id;
        return null;
    }

    private static Component kindName(Colony c) {
        return Component.translatable("mobslearn.settlements.kind." + c.kind);
    }

    private static void relationsTick(ServerLevel level, long t) {
        String dim = dimId(level);
        RandomSource r = level.getRandom();
        List<Colony> list = new ArrayList<>();
        for (Colony c : colonies) if (c.dim.equals(dim) && !c.pending && c.active() && !c.camp) list.add(c);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Colony a = list.get(i), b = list.get(j);
                double d = Math.sqrt(a.center().distSqr(b.center()));
                if (d > 280) continue;
                String key = pair(a, b);
                String rel = relations.get(key);
                boolean watched = level.getNearestPlayer(a.x, a.y, a.z, 200, false) != null || level.getNearestPlayer(b.x, b.y, b.z, 200, false) != null;
                if (rel == null) {
                    if (!watched || a.tier < 1 || b.tier < 1) continue;
                    float roll = r.nextFloat();
                    if (!hostile(a.kind) && !hostile(b.kind)) rel = roll < 0.4f ? "friend" : "peace";
                    else if (hostile(a.kind) && hostile(b.kind)) {
                        if (a.kind.equals(b.kind)) rel = roll < 0.55f ? "war" : roll < 0.7f ? "ally" : "peace";
                        else rel = roll < 0.45f ? "war" : roll < 0.75f ? "friend" : "peace";
                    } else continue;
                    relations.put(key, rel);
                    dirty = true;
                    switch (rel) {
                        case "war" -> {
                            Component msg = Component.translatable("mobslearn.colony.war", kindName(a), kindName(b));
                            tell(level, a.center(), 200, msg);
                            tell(level, b.center(), 200, msg);
                        }
                        case "friend" -> {
                            Component msg = Component.translatable("mobslearn.colony.friends", kindName(a), kindName(b));
                            tell(level, a.center(), 200, msg);
                            tell(level, b.center(), 200, msg);
                        }
                        case "ally" -> merge(level, a, b);
                        default -> {
                        }
                    }
                    continue;
                }
                if (rel.equals("friend") && !hostile(a.kind)) {
                    if (a.wood < 8 && b.wood > 20) {
                        a.wood += 8;
                        b.wood -= 8;
                    } else if (b.wood < 8 && a.wood > 20) {
                        b.wood += 8;
                        a.wood -= 8;
                    }
                }
                if (rel.equals("war") && watched) battle(level, a, b, t);
            }
        }
    }

    private static void merge(ServerLevel level, Colony a, Colony b) {
        Colony main = a.tier > b.tier || (a.tier == b.tier && a.age >= b.age) ? a : b;
        Colony other = main == a ? b : a;
        main.extraHouses += other.houses + other.extraHouses + 4;
        main.tier = Math.max(main.tier, other.tier);
        main.big = true;
        main.hasPartner = true;
        main.px = other.x;
        main.pz = other.z;
        for (String k : other.known) if (!main.known.contains(k)) main.known.add(k);
        aliases.put(other.id, main.id);
        for (Mob m : residents(level, other, 96)) {
            m.removeTag(HOME + other.id);
            m.addTag(HOME + main.id);
            if (m.entityTags().contains(CHIEF)) m.removeTag(CHIEF);
        }
        Camps.reassign(other.id, main.id);
        colonies.remove(other);
        relations.entrySet().removeIf(e -> e.getKey().contains(other.id));
        dirty = true;
        Component msg = Component.translatable("mobslearn.colony.alliance", kindName(main));
        tell(level, main.center(), 220, msg);
        tell(level, new BlockPos(other.x, other.y, other.z), 220, msg);
        BlockPos mid = new BlockPos((main.x + other.x) / 2, main.y, (main.z + other.z) / 2);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.getX() + 0.5, mid.getY() + 3, mid.getZ() + 0.5, 40, 3, 2, 3, 0.2);
    }

    private static void battle(ServerLevel level, Colony a, Colony b, long t) {
        String key = pair(a, b);
        if (t < nextBattle.getOrDefault(key, 0L)) return;
        RandomSource r = level.getRandom();
        nextBattle.put(key, t + Math.max(1200, 9600 / Math.max(1, Math.min(warp, 8))));
        Colony att = r.nextBoolean() ? a : b, def = att == a ? b : a;
        if (!level.isLoaded(att.center()) || !level.isLoaded(def.center())) return;
        List<Mob> party = new ArrayList<>();
        for (Mob m : residents(level, att, 64)) {
            if (party.size() >= 2 + att.tier) break;
            if (m.entityTags().contains(CHIEF) || Builders.busy(m) || warriors.containsKey(m.getUUID()) || att.raiders.contains(m.getUUID())) continue;
            party.add(m);
        }
        if (party.isEmpty()) return;
        for (Mob m : party) {
            warriors.put(m.getUUID(), def.id);
            warUntil.put(m.getUUID(), t + 4800);
            m.getNavigation().moveTo(def.x + 0.5, def.y, def.z + 0.5, 1.0);
        }
    }

    private static void warTick(ServerLevel level, long t) {
        for (var it = warriors.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Colony def = byId(e.getValue());
            if (!(level.getEntity(e.getKey()) instanceof Mob m) || !m.isAlive() || def == null || !def.active() || t > warUntil.getOrDefault(e.getKey(), 0L)) {
                if (level.getEntity(e.getKey()) instanceof Mob back && back.isAlive()) {
                    Colony home = homeOf(back);
                    if (home != null) back.getNavigation().moveTo(home.x + 0.5, home.y, home.z + 0.5, 1.0);
                    if (back.getTarget() != null && homeOf(back.getTarget()) != null) back.setTarget(null);
                }
                warUntil.remove(e.getKey());
                it.remove();
                continue;
            }
            if (m.getTarget() != null && m.getTarget().isAlive()) continue;
            Mob foe = null;
            double best = 20 * 20;
            for (Mob o : residents(level, def, 96)) {
                double d = o.distanceToSqr(m);
                if (d < best) {
                    best = d;
                    foe = o;
                }
            }
            if (foe != null) {
                m.setTarget(foe);
                if (foe.getTarget() == null) foe.setTarget(m);
            } else if (t % 60 == 0 || m.getNavigation().isDone()) {
                double dx = def.x + 0.5 - m.getX(), dz = def.z + 0.5 - m.getZ(), len = Math.sqrt(dx * dx + dz * dz);
                double k = Math.min(1.0, 24.0 / Math.max(1.0, len));
                int tx = (int) (m.getX() + dx * k), tz = (int) (m.getZ() + dz * k);
                if (level.isLoaded(new BlockPos(tx, def.y, tz))) m.getNavigation().moveTo(tx + 0.5, Camps.groundY(level, tx, tz), tz + 0.5, 1.0);
            }
        }
    }

    public static void residentDied(ServerLevel level, Mob dead, net.minecraft.world.damagesource.DamageSource src) {
        Colony c = homeOf(dead);
        warriors.remove(dead.getUUID());
        if (c == null || !c.active() || !hostile(c.kind)) return;
        Entity killer = src.getEntity();
        if (killer instanceof Mob km) {
            Colony kc = homeOf(km);
            if (kc != null && kc != c && relation(kc, c).equals("war")) {
                c.warLoss++;
                dirty = true;
                if (c.warLoss >= 6 + 3 * c.tier) {
                    kc.extraHouses += 2 + c.tier;
                    kc.warLoss = Math.max(0, kc.warLoss - 4);
                    relations.remove(pair(kc, c));
                    Component msg = Component.translatable("mobslearn.colony.war_end", kindName(kc), kindName(c));
                    tell(level, kc.center(), 220, msg);
                    tell(level, c.center(), 220, msg);
                    List<Mob> left = residents(level, c, 96);
                    left.remove(dead);
                    collapse(level, c, left);
                }
            }
            return;
        }
        if (!(killer instanceof ServerPlayer p)) return;
        List<Mob> left = residents(level, c, 48);
        left.remove(dead);
        boolean chief = dead.entityTags().contains(CHIEF);
        for (Mob m : left) if (m.entityTags().contains(CHIEF)) chief = false;
        if (!chief && c.chiefAlive) return;
        if (left.size() > 1) return;
        for (Mob m : left) m.removeTag(HOME + c.id);
        c.state = "abandoned";
        c.decay = 80;
        c.raiders.clear();
        Camps.forgetColony(c.id);
        Net.notice(p, Component.translatable("mobslearn.colony.cleared", kindName(c)));
        awardNear(level, c.center(), 64, "liberator");
        dirty = true;
    }
}
