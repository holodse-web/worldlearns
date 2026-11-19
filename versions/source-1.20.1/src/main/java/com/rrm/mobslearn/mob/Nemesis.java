package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

final class Nemesis {
    static final String TAG = "mobslearn.nemesis=";
    static final int FIRST_NAMES = 20, TITLES = 20, MAX_ALIVE = 3, MAX_RANK = 5, KEEP_PER_PLAYER = 12;

    static final class Foe {
        String id, owner, ownerName = "", type, trophy = "", scar = "", dim = "minecraft:overworld", status = "alive", heirOf = "";
        int first, title, rank = 1, meetings = 1, kills, x, y, z, incarnation;
        long born, seen, nextHunt, lastTaunt;
    }

    private record Wound(UUID player, long at) {
    }

    private static final List<Foe> foes = new ArrayList<>();
    private static final Map<UUID, Wound> wounds = new HashMap<>();
    private static final Map<UUID, String> scarOf = new HashMap<>();

    private Nemesis() {
    }

    static void load(JsonObject o) {
        foes.clear();
        wounds.clear();
        scarOf.clear();
        if (!o.has("foes")) return;
        for (JsonElement e : o.getAsJsonArray("foes")) {
            try {
                JsonObject j = e.getAsJsonObject();
                Foe f = new Foe();
                f.id = j.get("id").getAsString();
                f.owner = j.get("owner").getAsString();
                f.ownerName = str(j, "ownerName");
                f.type = j.get("type").getAsString();
                f.trophy = str(j, "trophy");
                f.scar = str(j, "scar");
                f.dim = str(j, "dim");
                f.status = str(j, "status");
                f.heirOf = str(j, "heirOf");
                f.first = num(j, "first");
                f.title = num(j, "title");
                f.rank = Math.max(1, num(j, "rank"));
                f.meetings = num(j, "meetings");
                f.kills = num(j, "kills");
                f.x = num(j, "x");
                f.y = num(j, "y");
                f.z = num(j, "z");
                f.incarnation = num(j, "inc");
                f.born = j.has("born") ? j.get("born").getAsLong() : 0;
                f.seen = j.has("seen") ? j.get("seen").getAsLong() : 0;
                f.nextHunt = j.has("nextHunt") ? j.get("nextHunt").getAsLong() : 0;
                foes.add(f);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static String str(JsonObject j, String k) {
        return j.has(k) ? j.get(k).getAsString() : "";
    }

    private static int num(JsonObject j, String k) {
        return j.has(k) ? j.get(k).getAsInt() : 0;
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Foe f : foes) arr.add(json(f, true));
        o.add("foes", arr);
        return o;
    }

    private static JsonObject json(Foe f, boolean full) {
        JsonObject j = new JsonObject();
        j.addProperty("id", f.id);
        if (full) j.addProperty("owner", f.owner);
        j.addProperty("ownerName", f.ownerName);
        j.addProperty("type", f.type);
        j.addProperty("trophy", f.trophy);
        j.addProperty("scar", f.scar);
        j.addProperty("dim", f.dim);
        j.addProperty("status", f.status);
        j.addProperty("heirOf", f.heirOf);
        j.addProperty("first", f.first);
        j.addProperty("title", f.title);
        j.addProperty("rank", f.rank);
        j.addProperty("meetings", f.meetings);
        j.addProperty("kills", f.kills);
        j.addProperty("x", f.x);
        j.addProperty("y", f.y);
        j.addProperty("z", f.z);
        j.addProperty("inc", f.incarnation);
        j.addProperty("born", f.born);
        j.addProperty("seen", f.seen);
        j.addProperty("nextHunt", f.nextHunt);
        return j;
    }

    private static Foe byId(String id) {
        for (Foe f : foes) if (f.id.equals(id)) return f;
        return null;
    }

    static Foe of(Entity e) {
        for (String tag : e.getTags()) {
            if (!tag.startsWith(TAG)) continue;
            String[] p = tag.substring(TAG.length()).split("#");
            Foe f = byId(p[0]);
            if (f == null) return null;
            try {
                if (p.length > 1 && Integer.parseInt(p[1]) != f.incarnation) return null;
            } catch (NumberFormatException ignored) {
                return null;
            }
            return f;
        }
        return null;
    }

    static boolean isNemesis(Entity e) {
        for (String tag : e.getTags()) if (tag.startsWith(TAG)) return true;
        return false;
    }

    private static int aliveFor(String owner) {
        int n = 0;
        for (Foe f : foes) if (f.owner.equals(owner) && f.status.equals("alive")) n++;
        return n;
    }

    static boolean eligible(Mob mob) {
        MobGroup g = MobGroup.of(mob.getType());
        if (g != MobGroup.ZOMBIE && g != MobGroup.SKELETON && g != MobGroup.SPIDER && g != MobGroup.ENDERMAN
            && g != MobGroup.WITCH && g != MobGroup.PILLAGER) return false;
        return !Captives.isCaptive(mob) && !Allies.isMercenary(mob) && !GreatMarch.isMarcher(mob) && !Colonies.isChief(mob);
    }

    private static boolean on() {
        return Saga.enabled() && WorldBrain.get().nemesis;
    }

    static Component name(Foe f) {
        return Component.translatable("mobslearn.nemesis.name", Component.translatable("mobslearn.nemesis.first." + f.first),
            Component.translatable("mobslearn.nemesis.title." + f.title));
    }

    static void afterDamage(ServerLevel level, LivingEntity e, DamageSource src, float taken) {
        if (!on() || !(e instanceof Mob mob) || !(src.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (!eligible(mob) && !isNemesis(mob)) return;
        scarOf.put(mob.getUUID(), scarFrom(src));
        Foe f = of(mob);
        if (f != null) {
            f.x = mob.getBlockX();
            f.y = mob.getBlockY();
            f.z = mob.getBlockZ();
            return;
        }
        wounds.put(mob.getUUID(), new Wound(p.getUUID(), level.getGameTime()));
        if (wounds.size() > 256) wounds.clear();
    }

    private static String scarFrom(DamageSource src) {
        if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.FIREBALL)) return "fire";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return "arrow";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return "blast";
        return "blade";
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        if (!on()) return;
        if (e instanceof ServerPlayer victim) {
            if (victim.isCreative() || victim.isSpectator()) return;
            if (src.getEntity() instanceof Mob killer && killer.isAlive() && (eligible(killer) || isNemesis(killer))) {
                Foe f = promote(level, killer, victim, "kill");
                if (f != null) {
                    f.kills++;
                    BlockPos at = victim.blockPosition();
                    Behaviors.later(3, () -> takeTrophy(level, killer, f, at));
                    Saga.markDirty();
                }
            }
            return;
        }
        wounds.remove(e.getUUID());
        Foe f = of(e);
        if (f == null || !f.status.equals("alive")) return;
        Entity killer = src.getEntity();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(UUID.fromString(f.owner));
        if (killer instanceof ServerPlayer p && p.getUUID().toString().equals(f.owner)) {
            f.status = "slain";
            f.seen = Saga.day(level);
            Saga.markDirty();
            ExperienceOrb.award(level, e.position(), 20 + 15 * f.rank);
            ItemStack bounty = new ItemStack(Items.EMERALD, 3 + 2 * f.rank);
            level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), bounty));
            if (f.rank >= 3) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.GOLDEN_APPLE)));
            level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, e.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.35f, 1.6f);
            Saga.notice(p, Component.translatable("mobslearn.nemesis.slain", name(f), f.meetings));
            WorldBrain.chronicle(Component.translatable("mobslearn.nemesis.chronicle.slain", p.getName(), name(f)), "skeleton_skull");
            Milestones.award(p, "nemesis_slain");
        } else {
            f.status = "fallen";
            Saga.markDirty();
            if (owner != null) Saga.notice(owner, Component.translatable("mobslearn.nemesis.fallen", name(f)));
        }
    }

    static void chiefKilled(ServerLevel level, Mob dead, ServerPlayer p, String colonyId, List<Mob> left) {
        if (!on() || p.isCreative()) return;
        Mob heir = null;
        for (Mob m : left) {
            if (!m.isAlive() || !eligible(m) || isNemesis(m)) continue;
            if (heir == null || m.getMaxHealth() > heir.getMaxHealth()) heir = m;
        }
        if (heir == null) return;
        Foe f = promote(level, heir, p, "heir");
        if (f == null) return;
        f.heirOf = colonyId;
        f.rank = Math.max(f.rank, 2);
        dress(level, heir, f);
        Talk.say(level, heir, Component.translatable("mobslearn.nemesis.vow", p.getName()), 48);
    }

    static boolean force(ServerLevel level, ServerPlayer p) {
        Mob best = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16))) {
            if (!m.isAlive() || !eligible(m) || isNemesis(m)) continue;
            if (best == null || m.distanceTo(p) < best.distanceTo(p)) best = m;
        }
        return best != null && promote(level, best, p, "scar") != null;
    }

    static Foe promote(ServerLevel level, Mob mob, ServerPlayer p, String reason) {
        if (!on()) return null;
        String owner = p.getUUID().toString();
        Foe f = of(mob);
        if (f != null) {
            if (!f.owner.equals(owner)) return null;
            f.meetings++;
            if (f.rank < MAX_RANK) {
                f.rank++;
                Saga.notice(p, Component.translatable("mobslearn.nemesis.stronger", name(f), f.rank));
            }
            dress(level, mob, f);
            remember(level, mob, f);
            Saga.markDirty();
            return f;
        }
        if (isNemesis(mob) || !eligible(mob) || aliveFor(owner) >= MAX_ALIVE) return null;
        RandomSource r = level.getRandom();
        f = new Foe();
        f.id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + r.nextInt(1000);
        f.owner = owner;
        f.ownerName = p.getName().getString();
        f.type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        f.first = r.nextInt(FIRST_NAMES);
        f.title = r.nextInt(TITLES);
        f.scar = reason.equals("kill") ? "" : scarOf.getOrDefault(mob.getUUID(), "blade");
        f.born = f.seen = Saga.day(level);
        f.nextHunt = f.born + 2;
        foes.add(0, f);
        trim(owner);
        mob.addTag(TAG + f.id + "#0");
        mob.setPersistenceRequired();
        dress(level, mob, f);
        mob.setHealth(mob.getMaxHealth());
        remember(level, mob, f);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 6, 0.4, 0.2, 0.4, 0);
        level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6f, 1.5f);
        Saga.notice(p, Component.translatable("mobslearn.nemesis.born." + reason, name(f)));
        Milestones.award(p, "nemesis_born");
        Saga.markDirty();
        return f;
    }

    private static void trim(String owner) {
        int seen = 0;
        for (int i = 0; i < foes.size(); i++) {
            Foe f = foes.get(i);
            if (!f.owner.equals(owner)) continue;
            if (++seen > KEEP_PER_PLAYER && !f.status.equals("alive")) foes.remove(i--);
        }
    }

    private static void remember(ServerLevel level, Mob mob, Foe f) {
        f.dim = Saga.dimId(level);
        f.x = mob.getBlockX();
        f.y = mob.getBlockY();
        f.z = mob.getBlockZ();
        f.seen = Saga.day(level);
    }

    static void dress(ServerLevel level, Mob mob, Foe f) {
        Component name = name(f).copy().withStyle(f.rank >= 4 ? ChatFormatting.DARK_RED : ChatFormatting.RED)
            .append(Component.literal(" " + "☠".repeat(Math.min(5, f.rank))).withStyle(ChatFormatting.GRAY));
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
        modifier(mob, Attributes.MAX_HEALTH, "nemesis_hp", 4.0 * f.rank);
        modifier(mob, Attributes.ATTACK_DAMAGE, "nemesis_dmg", 0.75 * f.rank);
        modifier(mob, Attributes.FOLLOW_RANGE, "nemesis_range", 8.0 + 2.0 * f.rank);
        if (f.scar.equals("blade")) modifier(mob, Attributes.ARMOR, "nemesis_scar", 2.0 + f.rank);
        if (f.scar.equals("fire")) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        if (f.scar.equals("arrow") && mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            mob.setDropChance(EquipmentSlot.OFFHAND, 0f);
        }
        if (f.scar.equals("blast")) modifier(mob, Attributes.KNOCKBACK_RESISTANCE, "nemesis_scar", 0.5);
    }

    private static void modifier(Mob mob, Attribute attr, String id, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(com.rrm.mobslearn.mob.Compat.uuid(MobsLearn.id(id)));
        inst.addPermanentModifier(com.rrm.mobslearn.mob.Compat.mod(MobsLearn.id(id), value, AttributeModifier.Operation.ADDITION));
    }

    private static int worth(ItemStack s) {
        if (s.isEmpty()) return -1;
        int v = s.isEnchanted() ? 40 : 0;
        String key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (key.startsWith("netherite_")) v += 60;
        else if (key.startsWith("diamond_")) v += 45;
        else if (key.startsWith("iron_")) v += 25;
        else if (key.startsWith("golden_") || key.startsWith("chainmail_")) v += 15;
        else if (key.equals("bow") || key.equals("crossbow") || key.equals("trident") || key.equals("shield")) v += 20;
        else return -1;
        return v;
    }

    private static EquipmentSlot slotFor(ItemStack s) {
        String key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (key.endsWith("_helmet")) return EquipmentSlot.HEAD;
        if (key.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (key.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (key.endsWith("_boots")) return EquipmentSlot.FEET;
        if (key.equals("shield")) return EquipmentSlot.OFFHAND;
        return EquipmentSlot.MAINHAND;
    }

    private static void takeTrophy(ServerLevel level, Mob killer, Foe f, BlockPos at) {
        if (!killer.isAlive()) return;
        ItemEntity best = null;
        int bestWorth = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(4))) {
            int w = worth(item.getItem());
            if (w > bestWorth) {
                bestWorth = w;
                best = item;
            }
        }
        if (best == null) return;
        ItemStack stack = best.getItem().copy();
        stack.setCount(1);
        best.getItem().shrink(1);
        if (best.getItem().isEmpty()) best.discard();
        EquipmentSlot slot = slotFor(stack);
        if (killer.getType() == net.minecraft.world.entity.EntityType.SKELETON && slot == EquipmentSlot.MAINHAND && !stack.is(Items.BOW)) slot = EquipmentSlot.OFFHAND;
        ItemStack old = killer.getItemBySlot(slot);
        if (!old.isEmpty()) level.addFreshEntity(new ItemEntity(level, killer.getX(), killer.getY() + 0.5, killer.getZ(), old));
        killer.setItemSlot(slot, stack);
        killer.setDropChance(slot, 1.0f);
        f.trophy = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Saga.markDirty();
        Talk.say(level, killer, Component.translatable("mobslearn.nemesis.trophy", stack.getHoverName()), 32);
    }

    static void tick(ServerLevel level, long t) {
        if (!on()) return;
        if (t % 100 == 0 && !wounds.isEmpty()) {
            RandomSource r = level.getRandom();
            for (var it = wounds.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                Entity ent = level.getEntity(e.getKey());
                if (ent == null) {
                    if (t - e.getValue().at() > 6000) it.remove();
                    continue;
                }
                if (!(ent instanceof Mob mob) || !mob.isAlive()) {
                    it.remove();
                    continue;
                }
                ServerPlayer p = level.getPlayerByUUID(e.getValue().player()) instanceof ServerPlayer sp ? sp : null;
                boolean gone = p == null || p.level() != level || p.distanceTo(mob) > 40 || t - e.getValue().at() > 1800;
                if (!gone) continue;
                it.remove();
                if (p != null && mob.getHealth() <= mob.getMaxHealth() * 0.6f && r.nextFloat() < 0.35f) promote(level, mob, p, "scar");
            }
        }
        if (t % 600 == 300) hunt(level, t);
    }

    private static void hunt(ServerLevel level, long t) {
        String dim = Saga.dimId(level);
        boolean nether = level.dimension().equals(net.minecraft.world.level.Level.NETHER);
        if (!nether && !level.isNight()) return;
        long day = Saga.day(level);
        for (Foe f : foes) {
            if (!f.status.equals("alive") || !f.dim.equals(dim) || day < f.nextHunt) continue;
            ServerPlayer p = level.getPlayerByUUID(UUID.fromString(f.owner)) instanceof ServerPlayer sp ? sp : null;
            if (p == null || p.level() != level || p.isCreative() || p.isSpectator() || p.isSleeping()) continue;
            if (loadedNear(level, f, p, 96)) {
                f.nextHunt = day + 1;
                continue;
            }
            Mob mob = incarnate(level, f, p);
            f.nextHunt = day + Math.max(1, 4 - f.rank / 2);
            Saga.markDirty();
            if (mob == null) continue;
            Saga.notice(p, Component.translatable("mobslearn.nemesis.hunting", name(f)));
            level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2f, 0.7f);
            Talk.sayTo(p, mob, Component.translatable("mobslearn.nemesis.found." + level.getRandom().nextInt(4), p.getName()));
            break;
        }
    }

    private static boolean loadedNear(ServerLevel level, Foe f, ServerPlayer p, double radius) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius))) if (of(m) == f) return true;
        return false;
    }

    private static Mob incarnate(ServerLevel level, Foe f, ServerPlayer p) {
        ResourceLocation typeId = ResourceLocation.tryParse(f.type);
        if (typeId == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 22 + r.nextInt(10);
            int x = p.getBlockX() + (int) (Math.cos(a) * d), z = p.getBlockZ() + (int) (Math.sin(a) * d);
            BlockPos probe = new BlockPos(x, p.getBlockY(), z);
            if (!level.isLoaded(probe)) continue;
            int y = Camps.groundY(level, x, z, p.getBlockY(), 12);
            if (y == Integer.MIN_VALUE) y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getFluidState(at).isEmpty()) continue;
            Entity made = type.create(level);
            if (!(made instanceof Mob mob)) return null;
            mob.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0f);
            mob.addTag(TraitTags.SEEN);
            f.incarnation++;
            mob.addTag(TAG + f.id + "#" + f.incarnation);
            mob.setPersistenceRequired();
            dress(level, mob, f);
            mob.setHealth(mob.getMaxHealth());
            if (!f.trophy.isEmpty()) {
                ItemStack trophy = Saga.stack(f.trophy, 1);
                if (!trophy.isEmpty()) {
                    EquipmentSlot slot = slotFor(trophy);
                    mob.setItemSlot(slot, trophy);
                    mob.setDropChance(slot, 1.0f);
                }
            }
            if (mob.getMainHandItem().isEmpty() && mob.getType() == net.minecraft.world.entity.EntityType.SKELETON) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            level.addFreshEntity(mob);
            mob.setTarget(p);
            mob.getNavigation().moveTo(p, 1.1);
            return mob;
        }
        return null;
    }

    static void mobTick(ServerLevel level, Mob mob, long t) {
        if (!on() || t % 20 != 0) return;
        Foe f = of(mob);
        if (f == null) return;
        if (!f.status.equals("alive")) return;
        if (t % 100 == 0) remember(level, mob, f);
        ServerPlayer owner = level.getPlayerByUUID(UUID.fromString(f.owner)) instanceof ServerPlayer sp ? sp : null;
        if (owner == null || owner.level() != level || owner.isCreative() || owner.isSpectator()) return;
        double d = mob.distanceTo(owner);
        if (d < 28 && mob.getTarget() == null) mob.setTarget(owner);
        if (t % 40 == 0) level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 2, 0.15, 0.05, 0.15, 0.0);
        if (d < 16 && level.getGameTime() - f.lastTaunt > 2400 && mob.hasLineOfSight(owner)) {
            f.lastTaunt = level.getGameTime();
            Component line;
            if (f.kills > 0 && !f.trophy.isEmpty()) {
                ItemStack trophy = Saga.stack(f.trophy, 1);
                line = Component.translatable("mobslearn.nemesis.taunt.trophy", owner.getName(), trophy.getHoverName());
            } else if (!f.heirOf.isEmpty()) {
                line = Component.translatable("mobslearn.nemesis.taunt.heir", owner.getName());
            } else if (!f.scar.isEmpty()) {
                line = Component.translatable("mobslearn.nemesis.taunt.scar." + f.scar, owner.getName());
            } else {
                line = Component.translatable("mobslearn.nemesis.taunt." + level.getRandom().nextInt(4), owner.getName(), f.meetings);
            }
            Talk.sayTo(owner, mob, line);
            if (f.meetings < 99) f.meetings++;
            Saga.markDirty();
        }
    }

    static void onLoad(ServerLevel level, Entity e) {
        if (!isNemesis(e)) return;
        Foe current = of(e);
        if (current == null || !current.status.equals("alive")) {
            Behaviors.later(1, () -> {
                if (e.isAlive()) e.discard();
            });
        }
    }

    static void send(ServerPlayer p) {
        if (!com.rrm.mobslearn.net.SNet.canSend(p, com.rrm.mobslearn.net.Net.ColoniesPayload.TYPE)) return;
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        String owner = p.getUUID().toString();
        for (Foe f : foes) if (f.owner.equals(owner)) arr.add(json(f, false));
        root.add("nemeses", arr);
        root.addProperty("day", p.level() instanceof ServerLevel sl ? Saga.day(sl) : 0);
        root.addProperty("enabled", WorldBrain.get().nemesis);
        com.rrm.mobslearn.net.SNet.send(p, new com.rrm.mobslearn.net.Net.ColoniesPayload(root.toString()));
    }
}
