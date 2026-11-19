package com.rrm.mobslearn.mob;

import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class HomeRaids {
    static final String RAIDER = "mobslearn.homeraid=";

    private static final class State {
        long lastDay = -1;
        String awayColony = "";
        long awayDay;
    }

    private static final class Raid {
        UUID player;
        String colony, dim;
        BlockPos home;
        final List<UUID> raiders = new ArrayList<>();
        final List<BlockPos> chests = new ArrayList<>();
        ServerBossEvent bar;
        int total, stolen;
        long started;
    }

    private static final Map<UUID, State> states = new HashMap<>();
    private static final Map<UUID, Raid> raids = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> bags = new HashMap<>();
    private static final Map<UUID, Long> stuckSince = new HashMap<>();
    private static final Map<UUID, double[]> progress = new HashMap<>();

    private HomeRaids() {
    }

    static void load(JsonObject o) {
        states.clear();
        raids.clear();
        bags.clear();
        progress.clear();
        for (var e : o.entrySet()) {
            try {
                JsonObject j = e.getValue().getAsJsonObject();
                State s = new State();
                s.lastDay = j.get("lastDay").getAsLong();
                s.awayColony = j.has("awayColony") ? j.get("awayColony").getAsString() : "";
                s.awayDay = j.has("awayDay") ? j.get("awayDay").getAsLong() : 0;
                states.put(UUID.fromString(e.getKey()), s);
            } catch (RuntimeException ignored) {
            }
        }
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        for (var e : states.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("lastDay", e.getValue().lastDay);
            j.addProperty("awayColony", e.getValue().awayColony);
            j.addProperty("awayDay", e.getValue().awayDay);
            o.add(e.getKey().toString(), j);
        }
        return o;
    }

    static void stop(MinecraftServer server) {
        for (Raid raid : raids.values()) {
            if (raid.bar != null) raid.bar.removeAllPlayers();
            ServerLevel level = server.overworld();
            for (UUID id : raid.raiders) stash(level, raid.colony, bags.remove(id));
        }
        raids.clear();
        bags.clear();
        progress.clear();
    }

    private static boolean on() {
        return Saga.enabled() && WorldBrain.get().homeRaids && WorldBrain.get().settlements;
    }

    static void tick(ServerLevel level, long t) {
        if (!raids.isEmpty() && t % 10 == 0) for (Raid raid : new ArrayList<>(raids.values())) if (raid.dim.equals(Saga.dimId(level))) runRaid(level, raid, t);
        if (t % 400 != 0 || !on() || !level.dimension().equals(Level.OVERWORLD)) return;
        long day = Saga.day(level);
        int intel = WorldBrain.get().intelligence();
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            Saga.Home home = Saga.home(p.getUUID());
            if (home == null || !home.dim().equals(Saga.dimId(level))) continue;
            State st = states.computeIfAbsent(p.getUUID(), k -> new State());
            double dist = Math.sqrt(p.distanceToSqr(home.pos().getX() + 0.5, home.pos().getY(), home.pos().getZ() + 0.5));
            if (!st.awayColony.isEmpty() && dist < 32) {
                ransacked(level, p, home.pos(), st);
                continue;
            }
            if (raids.containsKey(p.getUUID()) || intel < 20) continue;
            if (st.lastDay < 0) {
                st.lastDay = day;
                Saga.markDirty();
                continue;
            }
            long interval = Math.max(3, 7 - intel / 25);
            if (day - st.lastDay < interval || !level.isDarkOutside()) continue;
            Colonies.View raiders = raidersFor(level, p, home.pos());
            if (raiders == null) continue;
            st.lastDay = day;
            Saga.markDirty();
            if (dist <= 64) startRaid(level, p, home.pos(), raiders, t);
            else if (dist > 160) {
                st.awayColony = raiders.id();
                st.awayDay = day;
            }
        }
    }

    private static Colonies.View raidersFor(ServerLevel level, ServerPlayer p, BlockPos home) {
        Colonies.View best = null;
        double bestD = 640 * 640;
        for (Colonies.View v : Colonies.views(level)) {
            if (!v.hostile() || v.camp() || v.tier() < 1 || v.population() < 2 || !Colonies.isAngryAt(v.id(), p)) continue;
            double d = v.center().distSqr(home);
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    static boolean force(ServerLevel level, ServerPlayer p) {
        Saga.Home home = Saga.home(p.getUUID());
        BlockPos at = home != null && home.dim().equals(Saga.dimId(level)) ? home.pos() : p.blockPosition();
        if (raids.containsKey(p.getUUID())) return false;
        Colonies.View best = null;
        double bd = 1000 * 1000;
        for (Colonies.View v : Colonies.views(level)) {
            if (!v.hostile() || v.camp()) continue;
            double d = v.center().distSqr(at);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        if (best == null) return false;
        startRaid(level, p, at, best, level.getGameTime());
        return raids.containsKey(p.getUUID());
    }

    private static void startRaid(ServerLevel level, ServerPlayer p, BlockPos home, Colonies.View from, long t) {
        RandomSource r = level.getRandom();
        double dx = from.center().getX() - home.getX(), dz = from.center().getZ() - home.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        Raid raid = new Raid();
        raid.player = p.getUUID();
        raid.colony = from.id();
        raid.dim = Saga.dimId(level);
        raid.home = home;
        raid.started = t;
        List<Colonies.View> near = new ArrayList<>();
        for (Colonies.View v : Colonies.views(level)) if (v.center().distSqr(home) < 60 * 60) near.add(v);
        scan:
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-12, -5, -12), home.offset(12, 5, 12))) {
            if (!(level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) || raid.chests.size() >= 24) continue;
            for (Colonies.View v : near) if (v.center().distSqr(at) < 28 * 28) continue scan;
            raid.chests.add(at.immutable());
        }
        int n = Math.min(6, 3 + from.tier());
        for (int i = 0; i < n * 8 && raid.raiders.size() < n; i++) {
            double a = Math.atan2(dz / len, dx / len) + (r.nextDouble() - 0.5) * (0.9 + i * 0.25);
            int d = 22 + r.nextInt(7);
            int x = home.getX() + (int) (Math.cos(a) * d), z = home.getZ() + (int) (Math.sin(a) * d);
            BlockPos at = dryGround(level, x, z, home, i < n * 6 ? 10 : 64);
            if (at == null) continue;
            Mob m = Colonies.spawnResidentFor(level, from.id(), at);
            if (m == null) continue;
            m.addTag(RAIDER + p.getUUID());
            m.removeTag(Colonies.homeTag(from.id()));
            m.addTag("mobslearn.raid_home=" + from.id());
            raid.raiders.add(m.getUUID());
            m.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.1);
        }
        if (raid.raiders.isEmpty()) return;
        raid.total = raid.raiders.size();
        raid.bar = Saga.bar(Component.translatable("mobslearn.raid.bar", Colonies.kindNameOf(from.id())), BossEvent.BossBarColor.RED);
        raid.bar.setProgress(1f);
        raid.bar.addPlayer(p);
        raids.put(p.getUUID(), raid);
        Saga.notice(p, Component.translatable("mobslearn.raid.start", Colonies.kindNameOf(from.id())));
        level.playSound(null, home, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1.1f);
        BlockPos flag = home.offset((int) Math.signum(dx) * 6, 0, (int) Math.signum(dz) * 6);
        level.addFreshEntity(new ItemEntity(level, flag.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flag.getX(), flag.getZ()) + 0.5,
            flag.getZ() + 0.5, Frameups.bannerFor(from.id(), from.kind())));
    }

    private static void runRaid(ServerLevel level, Raid raid, long t) {
        ServerPlayer p = level.getPlayerByUUID(raid.player) instanceof ServerPlayer sp ? sp : null;
        int alive = 0;
        for (UUID id : List.copyOf(raid.raiders)) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            alive++;
            raiderTick(level, m, raid, p, t);
        }
        if (raid.bar != null) {
            raid.bar.setProgress(raid.total == 0 ? 0f : alive / (float) raid.total);
            if (p != null && p.level() == level && p.distanceToSqr(raid.home.getX(), raid.home.getY(), raid.home.getZ()) < 96 * 96) raid.bar.addPlayer(p);
            else if (p != null) raid.bar.removePlayer(p);
        }
        boolean timeout = t - raid.started > 6000;
        if (alive > 0 && !timeout) return;
        if (raid.bar != null) raid.bar.removeAllPlayers();
        raids.remove(raid.player);
        for (UUID id : raid.raiders) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob m && m.isAlive()) goHome(level, m, raid);
        }
        if (p == null) return;
        if (alive == 0 && raid.stolen == 0) {
            Saga.notice(p, Component.translatable("mobslearn.raid.repelled"));
            Milestones.award(p, "home_defended");
            WorldBrain.chronicle(Component.translatable("mobslearn.raid.chronicle.repelled", p.getName(), Colonies.kindNameOf(raid.colony)), "shield");
        } else if (raid.stolen > 0) {
            Saga.notice(p, Component.translatable("mobslearn.raid.robbed", raid.stolen, Colonies.kindNameOf(raid.colony)));
            Colonies.discoverFor(raid.colony, p);
        }
    }

    private static void goHome(ServerLevel level, Mob m, Raid raid) {
        List<ItemStack> carried = new ArrayList<>();
        List<ItemStack> bag = bags.remove(m.getUUID());
        if (bag != null) carried.addAll(bag);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
            ItemStack worn = m.getItemBySlot(slot);
            if (!worn.isEmpty() && m.getDropChances().byEquipment(slot) >= 1.0f) carried.add(worn.copy());
        }
        stash(level, raid.colony, carried);
        level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        m.discard();
    }

    private static boolean near(Mob m, BlockPos chest) {
        double dx = m.getX() - (chest.getX() + 0.5), dz = m.getZ() - (chest.getZ() + 0.5), dy = m.getY() - chest.getY();
        return dx * dx + dz * dz < 2.6 * 2.6 && dy > -3.2 && dy < 2.5;
    }

    private static BlockPos dryGround(ServerLevel level, int x, int z, BlockPos home, int maxRise) {
        if (!level.isLoaded(new BlockPos(x, home.getY(), z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z), below = at.below();
        if (Math.abs(y - home.getY()) > maxRise) return null;
        if (!level.getFluidState(at).isEmpty() || !level.getFluidState(below).isEmpty()) return null;
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return null;
        return at;
    }

    private static boolean progressStalled(Mob m, BlockPos goal, long t) {
        double d = Math.sqrt(m.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5));
        double[] st = progress.computeIfAbsent(m.getUUID(), k -> new double[]{d, 0});
        if (d < st[0] - 1.0) {
            st[0] = d;
            st[1] = 0;
            return false;
        }
        return ++st[1] >= 6;
    }

    private static boolean unstick(ServerLevel level, Mob m, BlockPos goal, long t) {
        if (t % 100 != 0) return false;
        double d = Math.sqrt(m.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5));
        double[] st = progress.computeIfAbsent(m.getUUID(), k -> new double[]{d, 0});
        if (d < st[0] - 1.0) {
            st[0] = d;
            st[1] = 0;
            return false;
        }
        if (++st[1] < 3 || d < 2.5) return false;
        RandomSource r = level.getRandom();
        for (int i = 0; i < 16; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int dist = 5 + r.nextInt(5);
            BlockPos at = dryGround(level, goal.getX() + (int) (Math.cos(a) * dist), goal.getZ() + (int) (Math.sin(a) * dist), goal, 6);
            if (at == null) continue;
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            m.getNavigation().stop();
            progress.remove(m.getUUID());
            return true;
        }
        st[1] = 0;
        return false;
    }

    private static void raiderTick(ServerLevel level, Mob m, Raid raid, ServerPlayer p, long t) {
        List<ItemStack> bag = bags.get(m.getUUID());
        if (bag != null) {
            Colonies.View home = Colonies.viewById(raid.colony);
            if (home != null && (m.getNavigation().isDone() || t % 40 == 0)) {
                m.getNavigation().moveTo(home.center().getX() + 0.5, home.center().getY(), home.center().getZ() + 0.5, 1.25);
            }
            boolean stuck = home != null && t % 100 == 0 && progressStalled(m, home.center(), t);
            if (stuck || m.distanceToSqr(raid.home.getX(), raid.home.getY(), raid.home.getZ()) > 52 * 52) {
                int n = 0;
                for (ItemStack s : bag) n += s.getCount();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
                    if (!m.getItemBySlot(slot).isEmpty() && m.getDropChances().byEquipment(slot) >= 1.0f) n++;
                }
                raid.stolen += n;
                if (p != null) Saga.notice(p, Component.translatable("mobslearn.raid.escaped", m.getDisplayName()));
                raid.raiders.remove(m.getUUID());
                goHome(level, m, raid);
            }
            return;
        }
        if (p != null && p.level() == level && m.distanceTo(p) < 10 && !p.isCreative() && !p.isSpectator()) {
            if (m.getTarget() == null) m.setTarget(p);
            return;
        }
        if (m.getTarget() instanceof net.minecraft.world.entity.player.Player) m.setTarget(null);
        BlockPos chest = nearestChest(level, raid, m);
        BlockPos goal = chest != null ? chest : raid.home;
        if (chest != null && near(m, chest)) {
            loot(level, m, chest, raid);
            return;
        }
        if (unstick(level, m, goal, t)) return;
        if (m.getNavigation().isDone() || t % 40 == 0) m.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.1);
        breakDoors(level, m, t);
    }

    static void mobTick(ServerLevel level, Mob m, long t) {
        if (t % 200 != 0) return;
        UUID owner = raidOf(m);
        if (owner == null || raids.containsKey(owner)) return;
        for (String tag : List.copyOf(m.entityTags())) if (tag.startsWith(RAIDER)) m.removeTag(tag);
    }

    private static UUID raidOf(Mob m) {
        for (String tag : m.entityTags()) {
            if (!tag.startsWith(RAIDER)) continue;
            try {
                return UUID.fromString(tag.substring(RAIDER.length()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static BlockPos nearestChest(ServerLevel level, Raid raid, Mob m) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos c : raid.chests) {
            if (!(level.getBlockEntity(c) instanceof Container box) || box.isEmpty()) continue;
            double d = m.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static void breakDoors(ServerLevel level, Mob m, long t) {
        if (!Builders.griefing(level)) return;
        BlockPos found = null;
        for (BlockPos at : BlockPos.betweenClosed(m.blockPosition().offset(-1, 0, -1), m.blockPosition().offset(1, 1, 1))) {
            if (level.getBlockState(at).getBlock() instanceof DoorBlock && !level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.IRON_DOOR)) {
                found = at.immutable();
                break;
            }
        }
        if (found == null) {
            stuckSince.remove(m.getUUID());
            return;
        }
        long since = stuckSince.computeIfAbsent(m.getUUID(), k -> t);
        if (t - since > 50) {
            level.destroyBlock(found, true, m);
            stuckSince.remove(m.getUUID());
        } else if (t % 20 == 0) {
            level.levelEvent(1019, found, 0);
            Builders.swing(m);
        }
    }

    private static int worth(ItemStack s) {
        if (s.isEmpty()) return -1;
        String key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        int v = s.isEnchanted() ? 40 : 0;
        if (key.contains("netherite")) v += 60;
        else if (key.contains("diamond")) v += 50;
        else if (key.contains("emerald")) v += 35;
        else if (key.contains("gold")) v += 25;
        else if (key.contains("iron")) v += 20;
        else if (s.has(net.minecraft.core.component.DataComponents.FOOD)) v += 8;
        return v + Math.min(10, s.getCount() / 8);
    }

    private static List<ItemStack> grab(Container box, int stacks) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < box.getContainerSize(); i++) if (!box.getItem(i).isEmpty()) slots.add(i);
        slots.sort(Comparator.comparingInt((Integer i) -> -worth(box.getItem(i))));
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < Math.min(stacks, slots.size()); i++) {
            out.add(box.getItem(slots.get(i)).copy());
            box.setItem(slots.get(i), ItemStack.EMPTY);
        }
        box.setChanged();
        return out;
    }

    private static void loot(ServerLevel level, Mob m, BlockPos chest, Raid raid) {
        if (!(level.getBlockEntity(chest) instanceof Container box)) return;
        List<ItemStack> took = grab(box, 2 + level.getRandom().nextInt(2));
        if (took.isEmpty()) return;
        level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.8f);
        level.sendParticles(ParticleTypes.SMOKE, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.01);
        List<ItemStack> bag = new ArrayList<>();
        for (ItemStack s : took) {
            EquipmentSlot slot = wearSlot(s);
            if (slot != null && m.getItemBySlot(slot).isEmpty()) {
                m.setItemSlot(slot, s);
                m.setDropChance(slot, 1.0f);
            } else bag.add(s);
        }
        bags.put(m.getUUID(), bag);
        progress.remove(m.getUUID());
        m.setTarget(null);
        ServerPlayer p = level.getPlayerByUUID(raid.player) instanceof ServerPlayer sp ? sp : null;
        if (p != null) Saga.notice(p, Component.translatable("mobslearn.raid.looting", m.getDisplayName()));
    }

    private static EquipmentSlot wearSlot(ItemStack s) {
        String key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (key.endsWith("_helmet")) return EquipmentSlot.HEAD;
        if (key.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (key.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (key.endsWith("_boots")) return EquipmentSlot.FEET;
        if (key.endsWith("_sword") || key.endsWith("_axe")) return EquipmentSlot.MAINHAND;
        return null;
    }

    static void stash(ServerLevel level, String colony, List<ItemStack> items) {
        if (items == null || items.isEmpty() || level == null) return;
        Container chest = Colonies.hallChest(level, colony, true);
        Colonies.View v = Colonies.viewById(colony);
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            boolean placed = false;
            if (chest != null) {
                for (int i = 0; i < chest.getContainerSize() && !placed; i++) {
                    if (chest.getItem(i).isEmpty()) {
                        chest.setItem(i, s);
                        placed = true;
                    }
                }
            }
            if (!placed && v != null) {
                BlockPos c = v.center();
                level.addFreshEntity(new ItemEntity(level, c.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()) + 0.5, c.getZ() + 0.5, s));
            }
        }
        if (chest != null) chest.setChanged();
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        List<ItemStack> bag = bags.remove(e.getUUID());
        if (bag == null) return;
        for (ItemStack s : bag) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), s));
    }

    static void onUnload(ServerLevel level, Entity e) {
        if (e.isAlive() || e.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            List<ItemStack> bag = bags.get(e.getUUID());
            if (bag == null || !(e instanceof Mob m)) return;
            UUID owner = raidOf(m);
            Raid raid = owner == null ? null : raids.get(owner);
            if (raid == null) return;
            bags.remove(e.getUUID());
            stash(level, raid.colony, bag);
        }
    }

    private static void ransacked(ServerLevel level, ServerPlayer p, BlockPos home, State st) {
        String colony = st.awayColony;
        st.awayColony = "";
        Saga.markDirty();
        if (Colonies.viewById(colony) == null) return;
        RandomSource r = level.getRandom();
        List<ItemStack> stolen = new ArrayList<>();
        int budget = 3 + r.nextInt(4);
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-12, -5, -12), home.offset(12, 5, 12))) {
            if (budget <= 0) break;
            if (!(level.getBlockEntity(at) instanceof Container box) || box.isEmpty()) continue;
            List<ItemStack> took = grab(box, Math.min(budget, 2));
            budget -= took.size();
            stolen.addAll(took);
        }
        BlockPos door = null;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
            BlockState s = level.getBlockState(at);
            if (s.getBlock() instanceof DoorBlock && !s.is(net.minecraft.world.level.block.Blocks.IRON_DOOR) && s.is(BlockTags.WOODEN_DOORS)) {
                door = at.immutable();
                break;
            }
        }
        if (door != null && Builders.griefing(level)) level.destroyBlock(door, true);
        BlockPos at = door != null ? door : home;
        level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, Frameups.bannerFor(colony, Colonies.viewById(colony).kind())));
        int n = 0;
        for (ItemStack s : stolen) n += s.getCount();
        stash(level, colony, stolen);
        Colonies.discoverFor(colony, p);
        Saga.notice(p, n > 0 ? Component.translatable("mobslearn.raid.away", n, Colonies.kindNameOf(colony))
            : Component.translatable("mobslearn.raid.away_empty", Colonies.kindNameOf(colony)));
        WorldBrain.chronicle(Component.translatable("mobslearn.raid.chronicle.away", p.getName(), Colonies.kindNameOf(colony)), "chest");
        level.playSound(null, home, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6f, 0.8f);
    }

    static void markAway(UUID player, String colony, long day) {
        State st = states.computeIfAbsent(player, k -> new State());
        st.awayColony = colony;
        st.awayDay = day;
        Saga.markDirty();
    }

    static void settlementTick(ServerLevel level, String id, List<Mob> residents) {
        if (level.getGameTime() % 1200 != 0 || residents.isEmpty()) return;
        Container chest = Colonies.hallChest(level, id, false);
        if (chest == null) return;
        int dressed = 0;
        for (int i = 0; i < chest.getContainerSize() && dressed < 2; i++) {
            ItemStack s = chest.getItem(i);
            EquipmentSlot slot = wearSlot(s);
            if (slot == null) continue;
            for (Mob m : residents) {
                if (!m.getItemBySlot(slot).isEmpty() || Colonies.isChief(m) && slot == EquipmentSlot.HEAD) continue;
                m.setItemSlot(slot, s.copy());
                m.setDropChance(slot, 1.0f);
                chest.setItem(i, ItemStack.EMPTY);
                dressed++;
                break;
            }
        }
        if (dressed > 0) chest.setChanged();
    }
}
