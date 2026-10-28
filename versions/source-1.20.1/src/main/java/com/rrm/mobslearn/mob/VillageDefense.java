package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.Trigger;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.net.Net;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class VillageDefense {
    private static final String[][] SWORD = {
        {"", "iron_ingot", "", "", "iron_ingot", "", "", "stick", ""},
        {"iron_ingot", "iron_ingot", "", "iron_ingot", "stick", "", "", "stick", ""},
        {"", "iron_ingot", "", "", "stick", "", "", "stick", ""},
        {"iron_ingot", "iron_ingot", "iron_ingot", "", "stick", "", "", "stick", ""}};
    private static final String[][] SHIELD = {
        {"oak_planks", "iron_ingot", "oak_planks", "oak_planks", "oak_planks", "oak_planks", "", "oak_planks", ""},
        {"oak_planks", "oak_planks", "oak_planks", "oak_planks", "iron_ingot", "oak_planks", "oak_planks", "oak_planks", "oak_planks"},
        {"oak_planks", "", "oak_planks", "oak_planks", "iron_ingot", "oak_planks", "", "oak_planks", ""},
        {"iron_ingot", "oak_planks", "iron_ingot", "oak_planks", "oak_planks", "oak_planks", "", "oak_planks", ""}};

    private static final String[][] PICKAXE = {
        {"iron_ingot", "iron_ingot", "iron_ingot", "", "stick", "", "", "stick", ""},
        {"iron_ingot", "iron_ingot", "", "iron_ingot", "stick", "", "", "stick", ""},
        {"", "iron_ingot", "", "", "stick", "", "", "stick", ""},
        {"iron_ingot", "", "iron_ingot", "", "stick", "", "", "stick", ""}};
    private static final String[][] AXE = {
        {"iron_ingot", "iron_ingot", "", "iron_ingot", "stick", "", "", "stick", ""},
        {"iron_ingot", "iron_ingot", "", "", "stick", "", "", "stick", ""},
        {"iron_ingot", "iron_ingot", "iron_ingot", "", "stick", "", "", "stick", ""},
        {"", "iron_ingot", "", "", "iron_ingot", "", "", "stick", ""}};
    private static final String[] REQUESTS = {"planks", "bread", "iron", "torch"};
    private static final int[] REQUEST_COUNT = {24, 6, 5, 16};

    private static final class Request {
        UUID village;
        String kind;
        int left;
        long until;
        net.minecraft.core.BlockPos where;
    }

    private static final Map<UUID, Request> requests = new HashMap<>();

    private static final class Ask {
        UUID villager, player;
        String topic;
        long until;
        boolean sent;
        int correct;
    }

    private static final Map<UUID, Ask> asks = new HashMap<>();
    private static final Map<UUID, Ask> pending = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Long> strikes = new HashMap<>();

    private VillageDefense() {
    }

    public static void reset() {
        asks.clear();
        pending.clear();
        cooldowns.clear();
        strikes.clear();
        requests.clear();
    }

    private static String skillOf(String topic) {
        return switch (topic) {
            case "sword" -> "villager_sword";
            case "shield" -> "villager_shield";
            default -> "villager_tools";
        };
    }

    public static void tick(ServerLevel level, Villager v, long t, RandomSource r) {
        if (v.isBaby() || !v.isAlive()) return;
        defend(level, v, t);
        Ask a = asks.get(v.getUUID());
        if (a != null) {
            Player p = level.getPlayerByUUID(a.player);
            if (p == null || !p.isAlive() || t > a.until || p.distanceTo(v) > 24) {
                asks.remove(v.getUUID());
                if (p != null && pending.get(p.getUUID()) == a) pending.remove(p.getUUID());
                return;
            }
            v.getLookControl().setLookAt(p, 30f, 30f);
            if (!a.sent) {
                if (v.distanceTo(p) > 2.8) {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, 0.6f, 2));
                } else if (p instanceof ServerPlayer sp && com.rrm.mobslearn.net.SNet.canSend(sp, Net.AskPayload.TYPE)) {
                    a.sent = true;
                    a.until = t + 1200;
                    pending.put(p.getUUID(), a);
                    v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1.2f);
                    com.rrm.mobslearn.net.SNet.send(sp, new Net.AskPayload(v.getId(), a.topic, options(a, r)));
                } else {
                    asks.remove(v.getUUID());
                }
            } else {
                v.getNavigation().stop();
            }
            return;
        }
        if ((t / 5 + v.getId()) % 4 != 0 || v.isSleeping()) return;
        for (Player p : level.getEntitiesOfClass(Player.class, v.getBoundingBox().inflate(10))) {
            if (p.isSpectator() || pending.containsKey(p.getUUID()) || !v.hasLineOfSight(p)) continue;
            boolean busy = false;
            for (Ask other : asks.values()) if (other.player.equals(p.getUUID())) busy = true;
            if (busy) continue;
            String topic = null;
            if (p.getMainHandItem().is(ItemTags.SWORDS)) topic = "sword";
            else if (p.getOffhandItem().is(Items.SHIELD) || p.getMainHandItem().is(Items.SHIELD)) topic = "shield";
            else if (p.getMainHandItem().is(ItemTags.PICKAXES)) topic = "pickaxe";
            else if (p.getMainHandItem().is(ItemTags.AXES)) topic = "axe";
            if (topic == null) {
                request(level, v, p, t, r);
                continue;
            }
            if (WorldBrain.levelAt(skillOf(topic), level, v.blockPosition()) >= 3) continue;
            Long cd = cooldowns.get(p.getUUID() + topic);
            if (cd != null && t < cd) continue;
            Ask a2 = new Ask();
            a2.villager = v.getUUID();
            a2.player = p.getUUID();
            a2.topic = topic;
            a2.until = t + 600;
            asks.put(v.getUUID(), a2);
            cooldowns.put(p.getUUID() + topic, t + 600);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.2, v.getZ(), 4, 0.3, 0.2, 0.3, 0);
            if (p instanceof ServerPlayer sp) Net.notice(sp, Component.translatable("mobslearn.ask.coming", v.getDisplayName()));
            return;
        }
    }

    public static void offer(ServerLevel level, Villager v, ServerPlayer p) {
        request(level, v, p, level.getGameTime(), level.getRandom(), true);
    }

    private static void request(ServerLevel level, Villager v, Player p, long t, RandomSource r) {
        request(level, v, p, t, r, false);
    }

    private static void request(ServerLevel level, Villager v, Player p, long t, RandomSource r, boolean force) {
        if (!(p instanceof ServerPlayer sp)) return;
        if (!force && (p.isCreative() || level.isNight() || r.nextInt(40) != 0)) return;
        Request old = requests.get(p.getUUID());
        if (old != null && t < old.until) return;
        Long cd = cooldowns.get(p.getUUID() + "request");
        if (!force && cd != null && t < cd) return;
        if (!Colonies.villageNear(level, v.blockPosition())) return;
        int i = r.nextInt(REQUESTS.length);
        Request q = new Request();
        q.village = v.getUUID();
        q.kind = REQUESTS[i];
        q.left = REQUEST_COUNT[i];
        q.until = t + 24000;
        q.where = v.blockPosition();
        requests.put(p.getUUID(), q);
        cooldowns.put(p.getUUID() + "request", t + 12000);
        v.getLookControl().setLookAt(p, 30f, 30f);
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, 0.6f, 2));
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1f, 1.1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.2, v.getZ(), 4, 0.3, 0.2, 0.3, 0);
        Talk.sayTo(sp, v, Component.translatable("mobslearn.request." + q.kind, q.left));
    }

    private static boolean fits(ItemStack stack, String kind) {
        return switch (kind) {
            case "planks" -> stack.is(ItemTags.PLANKS);
            case "bread" -> stack.is(Items.BREAD);
            case "iron" -> stack.is(Items.IRON_INGOT);
            default -> stack.is(Items.TORCH);
        };
    }

    public static net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.level.Level lvl, net.minecraft.world.InteractionHand hand, net.minecraft.world.entity.Entity e) {
        if (!(lvl instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || !(e instanceof Villager v)) return net.minecraft.world.InteractionResult.PASS;
        Request q = requests.get(player.getUUID());
        ItemStack stack = player.getItemInHand(hand);
        if (q == null || stack.isEmpty() || !fits(stack, q.kind) || level.getGameTime() > q.until
            || v.distanceToSqr(q.where.getX(), q.where.getY(), q.where.getZ()) > 80 * 80) return net.minecraft.world.InteractionResult.PASS;
        int give = Math.min(q.left, stack.getCount());
        stack.shrink(give);
        q.left -= give;
        v.getLookControl().setLookAt(player, 30f, 30f);
        if (q.left > 0) {
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.2f);
            Net.notice(sp, Component.translatable("mobslearn.request.more", q.left));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        requests.remove(player.getUUID());
        RandomSource r = level.getRandom();
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2, v.getZ(), 20, 0.6, 0.5, 0.6, 0);
        ItemStack reward = new ItemStack(Items.EMERALD, 3 + r.nextInt(6));
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, v.getX(), v.getY() + 1, v.getZ(), reward));
        Talk.sayTo(sp, v, Component.translatable("mobslearn.request.thanks." + q.kind));
        Colonies.gift(level, v.blockPosition(), q.kind, sp);
        Milestones.award(sp, "good_neighbor");
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    private static String options(Ask a, RandomSource r) {
        String[][] set = switch (a.topic) {
            case "sword" -> SWORD;
            case "shield" -> SHIELD;
            case "pickaxe" -> PICKAXE;
            default -> AXE;
        };
        List<Integer> wrong = new ArrayList<>(List.of(1, 2, 3));
        Collections.shuffle(wrong, new java.util.Random(r.nextLong()));
        List<Integer> picks = new ArrayList<>(List.of(0, wrong.get(0), wrong.get(1)));
        Collections.shuffle(picks, new java.util.Random(r.nextLong()));
        a.correct = picks.indexOf(0);
        JsonArray arr = new JsonArray();
        for (int idx : picks) {
            JsonArray grid = new JsonArray();
            for (String id : set[idx]) grid.add(id);
            JsonObject o = new JsonObject();
            o.add("grid", grid);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("options", arr);
        root.addProperty("result", switch (a.topic) {
            case "sword" -> "iron_sword";
            case "shield" -> "shield";
            case "pickaxe" -> "iron_pickaxe";
            default -> "iron_axe";
        });
        return root.toString();
    }

    public static void answer(ServerPlayer player, int villagerId, int choice) {
        Ask a = pending.remove(player.getUUID());
        if (a == null || !(player.level() instanceof ServerLevel level)) return;
        asks.remove(a.villager);
        if (!(level.getEntity(villagerId) instanceof Villager v) || !v.getUUID().equals(a.villager) || v.distanceTo(player) > 10) return;
        long t = level.getGameTime();
        if (choice < 0) {
            cooldowns.put(player.getUUID() + a.topic, t + 1200);
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 0.8f);
            return;
        }
        boolean ok = choice == a.correct;
        if (ok) {
            WorldBrain.teach(switch (a.topic) {
                case "sword" -> Trigger.TAUGHT_SWORD;
                case "shield" -> Trigger.TAUGHT_SHIELD;
                default -> Trigger.TAUGHT_TOOLS;
            }, MobGroup.VILLAGER, 1, level, v.blockPosition());
            cooldowns.put(player.getUUID() + a.topic, t + 2400);
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2, v.getZ(), 15, 0.5, 0.4, 0.5, 0);
            Milestones.award(player, "teacher");
        } else {
            cooldowns.put(player.getUUID() + a.topic, t + 900);
            level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
            level.sendParticles(ParticleTypes.SMOKE, v.getX(), v.getY() + 2, v.getZ(), 8, 0.3, 0.2, 0.3, 0.01);
        }
        if (com.rrm.mobslearn.net.SNet.canSend(player, Net.ResultPayload.TYPE)) com.rrm.mobslearn.net.SNet.send(player, new Net.ResultPayload(ok, a.topic));
    }

    private static void defend(ServerLevel level, Villager v, long t) {
        int lvl = WorldBrain.levelAt("villager_sword", level, v.blockPosition());
        if (lvl <= 0 || v.isSleeping()) return;
        Long last = strikes.get(v.getUUID());
        if (last != null && t - last < 16) return;
        Monster foe = null;
        for (Monster m : level.getEntitiesOfClass(Monster.class, v.getBoundingBox().inflate(2.6))) {
            if (m.isAlive() && v.hasLineOfSight(m)) {
                foe = m;
                break;
            }
        }
        if (foe == null) return;
        strikes.put(v.getUUID(), t);
        if (strikes.size() > 2048) strikes.clear();
        ItemStack sword = new ItemStack(lvl >= 3 ? Items.IRON_SWORD : lvl == 2 ? Items.STONE_SWORD : Items.WOODEN_SWORD);
        Builders.present(v, sword);
        Builders.swing(v);
        v.getLookControl().setLookAt(foe, 30f, 30f);
        float dmg = lvl >= 3 ? 6f : lvl == 2 ? 5f : 4f;
        if (foe.hurt(level.damageSources().mobAttack(v), dmg)) {
            Vec3 d = foe.position().subtract(v.position());
            Vec3 f = new Vec3(d.x, 0, d.z);
            if (f.lengthSqr() > 1.0E-4) f = f.normalize();
            foe.push(f.x * 0.45, 0.2, f.z * 0.45);
            level.playSound(null, v.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8f, 1.1f);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY() + 1, foe.getZ(), 1, 0, 0, 0, 0);
        }
        Behaviors.later(40, () -> Builders.clearPresentation(v));
    }

    public static boolean blocks(Villager v, DamageSource src) {
        if (!(v.level() instanceof ServerLevel level) || !(src.getEntity() instanceof Monster)) return false;
        int lvl = WorldBrain.levelAt("villager_shield", level, v.blockPosition());
        if (lvl <= 0 || v.getRandom().nextFloat() > 0.2f * lvl) return false;
        if (v.getOffhandItem().isEmpty()) {
            v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            v.setDropChance(EquipmentSlot.OFFHAND, 0f);
            Behaviors.later(30, () -> {
                if (v.getOffhandItem().is(Items.SHIELD)) v.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            });
        }
        level.playSound(null, v.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 1f, 1f);
        return true;
    }
}
