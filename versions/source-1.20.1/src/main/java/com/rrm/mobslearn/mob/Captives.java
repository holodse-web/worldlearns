package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.MobGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class Captives {
    static final String DAZED = "mobslearn.dazed=", CAPTIVE = "mobslearn.captive=", STUDY = "mobslearn.study=", QUESTIONED = "mobslearn.questioned";
    private static final Map<String, Set<String>> studied = new HashMap<>();
    private static final Map<UUID, Long> untied = new HashMap<>();

    private Captives() {
    }

    static void load(JsonObject o) {
        studied.clear();
        untied.clear();
        for (var e : o.entrySet()) {
            Set<String> set = new HashSet<>();
            if (e.getValue().isJsonArray()) for (JsonElement g : e.getValue().getAsJsonArray()) set.add(g.getAsString());
            studied.put(e.getKey(), set);
        }
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        for (var e : studied.entrySet()) {
            JsonArray a = new JsonArray();
            for (String g : e.getValue()) a.add(g);
            o.add(e.getKey(), a);
        }
        return o;
    }

    private static String tag(Entity e, String prefix) {
        for (String t : e.getTags()) if (t.startsWith(prefix)) return t.substring(prefix.length());
        return null;
    }

    private static void untag(Entity e, String prefix) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(prefix)) e.removeTag(t);
    }

    static boolean isCaptive(Entity e) {
        return tag(e, CAPTIVE) != null || tag(e, STUDY) != null;
    }

    static boolean isDazed(Entity e) {
        return tag(e, DAZED) != null;
    }

    private static boolean capturable(Mob mob) {
        MobGroup g = MobGroup.of(mob.getType());
        return g != null && g.hostile && g != MobGroup.SLIME && g != MobGroup.PHANTOM && !Allies.isMercenary(mob) && !GreatMarch.isWarlord(mob);
    }

    static void afterDamage(ServerLevel level, LivingEntity e, DamageSource src, float taken) {
        if (!(e instanceof Mob mob) || !mob.isAlive() || !(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p) return;
        if (isCaptive(mob) || isDazed(mob) || !capturable(mob)) return;
        if (mob.getHealth() > mob.getMaxHealth() * 0.3f || level.getRandom().nextFloat() > 0.45f) return;
        long until = level.getGameTime() + 120;
        mob.addTag(DAZED + until);
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2, false, false));
        level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 14, 0.35, 0.1, 0.35, 0.05);
        level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 0.6f, 1.6f);
        Saga.overlay(p, Component.translatable("mobslearn.captive.dazed", mob.getDisplayName()));
    }

    static boolean refusesTarget(Mob m, LivingEntity target) {
        return isCaptive(m) || isDazed(m);
    }

    static boolean mobTick(ServerLevel level, Mob mob, long t) {
        String dazed = tag(mob, DAZED);
        if (dazed != null) {
            long until;
            try {
                until = Long.parseLong(dazed);
            } catch (NumberFormatException e) {
                until = 0;
            }
            if (level.getGameTime() >= until) {
                untag(mob, DAZED);
                return false;
            }
            mob.setTarget(null);
            if (t % 20 == 0) level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 4, 0.3, 0.05, 0.3, 0.02);
            return true;
        }
        String study = tag(mob, STUDY);
        if (study != null) {
            studyTick(level, mob, study, t);
            return true;
        }
        String captor = tag(mob, CAPTIVE);
        if (captor == null) return false;
        mob.setTarget(null);
        if (mob.isAggressive()) mob.setAggressive(false);
        if (t % 100 == 0 && (mob.getType() == net.minecraft.world.entity.EntityType.ZOMBIE || mob.getType() == net.minecraft.world.entity.EntityType.SKELETON
            || mob.getType() == net.minecraft.world.entity.EntityType.STRAY || mob.getType() == net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER)) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, false, false));
        }
        if (mob.isLeashed()) {
            untied.remove(mob.getUUID());
        } else {
            long since = untied.computeIfAbsent(mob.getUUID(), k -> t);
            if (t - since > 1200) {
                untag(mob, CAPTIVE);
                untied.remove(mob.getUUID());
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                return false;
            }
        }
        if (t % 40 == 0) level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
        return true;
    }

    static InteractionResult interact(ServerLevel level, ServerPlayer p, InteractionHand hand, Entity entity) {
        if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Mob mob)) return InteractionResult.PASS;
        ItemStack held = p.getMainHandItem();
        if (isDazed(mob) && held.is(Items.LEAD)) {
            if (!p.isCreative()) held.shrink(1);
            untag(mob, DAZED);
            mob.addTag(CAPTIVE + p.getUUID());
            mob.setPersistenceRequired();
            mob.setLeashedTo(p, true);
            mob.setTarget(null);
            net.minecraft.sounds.SoundEvent knot = Saga.sound("entity.leash_knot.place");
            if (knot != null) level.playSound(null, mob.blockPosition(), knot, SoundSource.NEUTRAL, 1f, 0.8f);
            Saga.notice(p, Component.translatable("mobslearn.captive.taken", mob.getDisplayName()));
            Milestones.award(p, "captive_taken");
            return InteractionResult.SUCCESS;
        }
        String captor = tag(mob, CAPTIVE);
        if (captor != null && captor.equals(p.getUUID().toString()) && held.isEmpty()) {
            if (p.isShiftKeyDown()) interrogate(level, p, mob);
            else release(level, p, mob);
            return InteractionResult.SUCCESS;
        }
        if (entity instanceof net.minecraft.world.entity.npc.Villager v && !v.isBaby() && held.isEmpty()) {
            Mob captive = leashedCaptive(level, p);
            String village = captive == null ? null : Colonies.villageIdAt(level, v.blockPosition());
            if (captive != null && village != null) {
                handOver(level, p, captive, village, v);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    private static Mob leashedCaptive(ServerLevel level, ServerPlayer p) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10))) {
            String captor = tag(m, CAPTIVE);
            if (captor != null && captor.equals(p.getUUID().toString()) && m.isLeashed() && m.getLeashHolder() == p) return m;
        }
        return null;
    }

    private static void release(ServerLevel level, ServerPlayer p, Mob mob) {
        untag(mob, CAPTIVE);
        if (mob.isLeashed()) mob.dropLeash(true, true);
        Colonies.View home = Colonies.viewOf(mob);
        if (home != null) {
            Colonies.forgive(home.id(), p, 10);
            Saga.notice(p, Component.translatable("mobslearn.captive.released.home", mob.getDisplayName(), Colonies.kindNameOf(home.id())));
        } else {
            Saga.notice(p, Component.translatable("mobslearn.captive.released", mob.getDisplayName()));
        }
        Talk.say(level, mob, Component.translatable("mobslearn.captive.thanks." + level.getRandom().nextInt(3), p.getName()), 24);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.4, 0.4, 0.4, 0);
        Behaviors.later(100, () -> {
            if (!mob.isAlive() || isCaptive(mob)) return;
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            mob.discard();
        });
    }

    private static void interrogate(ServerLevel level, ServerPlayer p, Mob mob) {
        if (mob.getTags().contains(QUESTIONED)) {
            Talk.sayTo(p, mob, Component.translatable("mobslearn.captive.silent"));
            return;
        }
        Colonies.View home = Colonies.viewOf(mob);
        List<Colonies.View> reveal = new ArrayList<>();
        boolean chief = Colonies.isChief(mob);
        for (Colonies.View v : Colonies.views(level)) {
            if (!v.hostile()) continue;
            double d = Math.sqrt(v.center().distSqr(mob.blockPosition()));
            if (home != null && v.id().equals(home.id())) reveal.add(0, v);
            else if (chief && home != null && v.kind().equals(home.kind()) && d < 1500) reveal.add(v);
            else if (!chief && d < 900 && reveal.size() < 2) reveal.add(v);
        }
        if (reveal.isEmpty()) {
            Talk.sayTo(p, mob, Component.translatable("mobslearn.captive.nothing"));
            mob.addTag(QUESTIONED);
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Colonies.View v : reveal) {
            minX = Math.min(minX, v.center().getX());
            maxX = Math.max(maxX, v.center().getX());
            minZ = Math.min(minZ, v.center().getZ());
            maxZ = Math.max(maxZ, v.center().getZ());
        }
        int span = Math.max(maxX - minX, maxZ - minZ) + 64;
        byte scale = (byte) Math.max(1, Math.min(4, (int) Math.ceil(Math.log(Math.max(1, span / 128.0)) / Math.log(2))));
        ItemStack map = MapItem.create(level, (minX + maxX) / 2, (minZ + maxZ) / 2, scale, true, true);
        for (Colonies.View v : reveal) {
            MapItemSavedData.addTargetDecoration(map, v.center(), "mobslearn_" + v.id(), net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
            Colonies.discoverFor(v.id(), p);
        }
        Saga.name(map, Component.translatable("mobslearn.captive.map", Colonies.kindNameOf(home != null ? home.id() : reveal.get(0).id())));
        if (!p.getInventory().add(map)) level.addFreshEntity(new ItemEntity(level, p.getX(), p.getY() + 0.5, p.getZ(), map));
        mob.addTag(QUESTIONED);
        Talk.sayTo(p, mob, Component.translatable("mobslearn.captive.talk." + level.getRandom().nextInt(4)));
        Saga.notice(p, Component.translatable("mobslearn.captive.revealed", reveal.size()));
        Milestones.award(p, "interrogation");
        level.playSound(null, mob.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
    }

    private static void handOver(ServerLevel level, ServerPlayer p, Mob captive, String village, net.minecraft.world.entity.npc.Villager v) {
        untag(captive, CAPTIVE);
        captive.dropLeash(true, true);
        captive.addTag(STUDY + village + "|" + (level.getGameTime() + 1200) + "|" + p.getUUID());
        captive.getNavigation().stop();
        int gathered = 0;
        for (net.minecraft.world.entity.npc.Villager w : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, captive.getBoundingBox().inflate(32))) {
            if (w.isBaby() || w.isSleeping() || gathered >= 4) continue;
            w.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(captive.blockPosition(), 0.6f, 3));
            gathered++;
        }
        Talk.sayTo(p, v, Component.translatable("mobslearn.captive.study.start", captive.getDisplayName()));
        Saga.notice(p, Component.translatable("mobslearn.captive.study", captive.getDisplayName()));
    }

    private static void studyTick(ServerLevel level, Mob mob, String study, long t) {
        mob.setTarget(null);
        mob.getNavigation().stop();
        String[] parts = study.split("\\|");
        long end = 0;
        try {
            end = Long.parseLong(parts[1]);
        } catch (RuntimeException ignored) {
        }
        if (t % 20 == 0) level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1.6, mob.getZ(), 6, 0.4, 0.5, 0.4, 0.4);
        if (level.getGameTime() < end) return;
        MobGroup g = MobGroup.of(mob.getType());
        if (g != null) studied.computeIfAbsent(parts[0], k -> new HashSet<>()).add(g.key());
        Saga.markDirty();
        ServerPlayer p = null;
        try {
            if (parts.length > 2) p = level.getServer().getPlayerList().getPlayer(UUID.fromString(parts[2]));
        } catch (IllegalArgumentException ignored) {
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.6, 0.6, 0.6, 0);
        level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        if (p != null) {
            ItemStack pay = new ItemStack(Items.EMERALD, 3 + level.getRandom().nextInt(4));
            if (!p.getInventory().add(pay)) level.addFreshEntity(new ItemEntity(level, p.getX(), p.getY() + 0.5, p.getZ(), pay));
            ExperienceOrb.award(level, p.position(), 15);
            Saga.notice(p, Component.translatable("mobslearn.captive.studied", g == null ? Component.empty() : g.displayName()));
            Milestones.award(p, "captive_studied");
            Allies.helped(level, p, mob.blockPosition());
        }
        mob.discard();
    }

    static boolean studiedBy(ServerLevel level, BlockPos at, Mob monster) {
        if (studied.isEmpty()) return false;
        String village = Colonies.villageIdAt(level, at);
        MobGroup g = MobGroup.of(monster.getType());
        return village != null && g != null && studied.getOrDefault(village, Set.of()).contains(g.key());
    }

    static void tick(ServerLevel level, long t) {
        if (t % 40 != 0 || studied.isEmpty()) return;
        for (ServerPlayer p : level.players()) {
            for (Mob guard : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48))) {
                boolean golem = guard instanceof net.minecraft.world.entity.animal.IronGolem;
                boolean militia = guard instanceof net.minecraft.world.entity.npc.Villager && !guard.getMainHandItem().isEmpty();
                if (!golem && !militia || !(guard.getTarget() instanceof Mob foe)) continue;
                if (studiedBy(level, guard.blockPosition(), foe) && !guard.hasEffect(MobEffects.DAMAGE_BOOST)) {
                    guard.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
                }
            }
        }
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        untied.remove(e.getUUID());
    }
}
