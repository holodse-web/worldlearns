package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.mixin.CreeperAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Traits {
    private static final float[] CHANCE = Skill.SPAWN_CHANCE;
    private static final int MAX_TRAITS = Skill.MAX_PER_MOB, MAX_COMBAT = Skill.MAX_COMBAT_PER_MOB;
    private static final String SPEED_ID = "speed_bonus";
    private static String applyingSkill;
    private static final float[] NAME_CHANCE = {0f, 0.015f, 0.03f, 0.05f};
    private static final float[] GENIUS_CHANCE = {0f, 0.004f, 0.008f, 0.015f};
    private static final float[] HAT_CHANCE = {0f, 0.04f, 0.08f, 0.14f};
    private static final Item[] HATS = {Items.CAKE, Items.MELON, Items.FLOWER_POT, Items.GLASS, Items.OAK_LEAVES, Items.CACTUS,
        Items.BOOKSHELF, Items.BEEHIVE, Items.TNT, Items.JUKEBOX, Items.SEA_LANTERN, Items.HAY_BLOCK, Items.CRAFTING_TABLE,
        Items.FURNACE, Items.SPONGE, Items.TARGET, Items.LANTERN, Items.BELL};
    public static final int NAME_COUNT = 24;
    public static final int FISH_NAME_COUNT = 8;

    private Traits() {
    }

    public static void onLoad(Entity e, ServerLevel level) {
        if (!(e instanceof Mob mob)) return;
        Builders.clearPresentation(mob);
        if ((mob.getType() == EntityTypes.SQUID && TraitTags.raw(mob, "squid_glow") > 0) || TraitTags.raw(mob, "genius") > 0) {
            MobEffectInstance glow = mob.getEffect(MobEffects.GLOWING);
            if (glow != null && glow.isInfiniteDuration() && !glow.isVisible()) mob.removeEffect(MobEffects.GLOWING);
            if (mob.getType() == EntityTypes.SQUID) TraitTags.set(mob, "squid_glow", 0);
        }
        if (mob.entityTags().contains(TraitTags.SEEN)) {
            migrate(mob);
            refreshMob(level, mob);
            relearn(level, mob);
            return;
        }
        mob.addTag(TraitTags.SEEN);
        mob.addTag(BALANCE_V2);
        MobGroup g = MobGroup.of(mob.getType());
        if (g == null) return;
        Knowledge k = WorldBrain.get();
        RandomSource r = level.getRandom();
        try {
            int genius = WorldBrain.levelAt("world_genius", level, mob.blockPosition());
            boolean isGenius = g.hostile && genius > 0 && r.nextFloat() < GENIUS_CHANCE[genius];
            java.util.List<Skill> list = new java.util.ArrayList<>(Skill.of(g));
            java.util.Collections.shuffle(list, new java.util.Random(r.nextLong()));
            int traits = 0, combat = 0;
            for (Skill s : list) {
                int lvl = k.allows(s) ? WorldBrain.levelAt(s.id(), level, mob.blockPosition()) : 0;
                if (lvl <= 0) continue;
                if (isGenius) {
                    apply(level, mob, s.id(), Math.min(2, lvl), r);
                } else if (traits < MAX_TRAITS && (!s.combat() || combat < MAX_COMBAT) && r.nextFloat() < spawnChance(lvl)) {
                    apply(level, mob, s.id(), lvl, r);
                    traits++;
                    if (s.combat()) combat++;
                }
            }
            if (!g.hostile) TraitTags.set(mob, "epoch", localEpoch(level, mob, g));
            if (g.hostile) updateAwareness(mob);
            if (isGenius) {
                mob.setCustomName(Component.translatable("mobslearn.genius", Component.translatable("mobslearn.name." + r.nextInt(NAME_COUNT))).withStyle(net.minecraft.ChatFormatting.GOLD));
                mob.setCustomNameVisible(true);
                TraitTags.set(mob, "genius", 1);
            } else {
                int hats = WorldBrain.levelAt("world_hats", level, mob.blockPosition());
                if (hats > 0 && (g == MobGroup.ZOMBIE || g == MobGroup.SKELETON) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && r.nextFloat() < HAT_CHANCE[hats]) {
                    equip(mob, EquipmentSlot.HEAD, HATS[r.nextInt(HATS.length)]);
                    TraitTags.set(mob, "world_hats", hats);
                }
                int names = WorldBrain.levelAt("world_names", level, mob.blockPosition());
                if (names > 0 && !mob.hasCustomName() && r.nextFloat() < NAME_CHANCE[names]) {
                    mob.setCustomName(Component.translatable("mobslearn.name." + r.nextInt(NAME_COUNT)));
                }
            }
        } catch (Exception ex) {
            MobsLearn.LOG.warn("[MobsLearn] Failed to apply traits to {}", mob, ex);
        }
    }

    private static final String BALANCE_V2 = "mobslearn.balance2";

    private static final java.util.List<Holder<Attribute>> MOD_ATTRIBUTES = java.util.List.of(Attributes.FOLLOW_RANGE,
        Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE, Attributes.MAX_HEALTH, Attributes.KNOCKBACK_RESISTANCE,
        Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, Attributes.JUMP_STRENGTH, Attributes.SCALE, Attributes.ATTACK_KNOCKBACK);
    private static final String[] MODIFIERS = {"dumb", "speed_bonus", "armor_weight", "skeleton_sniper", "creeper_heavy",
        "witch_tank", "cow_gym", "zombie_knockback", "horse_turbo", "wolf_loyal", "wolf_pack", "wolf_pack_hp", "crit", "track"};

    private static void updateAwareness(Mob mob) {
        MobGroup group = MobGroup.of(mob.getType());
        if (group == null || !group.hostile) return;
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow == null) return;
        int intelligence = WorldBrain.get().intelligence();
        double value = !WorldBrain.get().enabled || intelligence >= 15 ? 0 : -0.35 * (1.0 - intelligence / 15.0) * mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE);
        double track = WorldSkills.trackBonus();
        if (track <= 0) follow.removeModifier(MobsLearn.id("track"));
        else follow.addOrUpdateTransientModifier(new AttributeModifier(MobsLearn.id("track"), track, AttributeModifier.Operation.ADD_VALUE));
        AttributeModifier existing = follow.getModifier(MobsLearn.id("dumb"));
        if (value == 0) { if (existing != null) follow.removeModifier(MobsLearn.id("dumb")); }
        else if (existing == null || Math.abs(existing.amount() - value) > 0.001) {
            follow.addOrUpdateTransientModifier(new AttributeModifier(MobsLearn.id("dumb"), value, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void refreshSettings(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !mob.entityTags().contains(TraitTags.SEEN)) continue;
            refreshMob(level, mob);
        }
    }

    private static void refreshMob(ServerLevel level, Mob mob) {
            Builders.stop(mob);
            reconcileEquipment(mob);
            for (Holder<Attribute> attr : MOD_ATTRIBUTES) {
                AttributeInstance instance = mob.getAttribute(attr);
                if (instance != null) for (String id : MODIFIERS) instance.removeModifier(MobsLearn.id(id));
            }
            for (Skill s : Skill.ALL) if (TraitTags.raw(mob, s.id()) > 0) {
                boolean enabled = WorldBrain.get().allows(s);
                int rank = TraitTags.raw(mob, s.id());
                refreshEffects(mob, s.id(), rank, enabled);
                if (!enabled) continue;
                switch (s.id()) {
                    case "zombie_sprinter" -> speed(mob, 0.04 * rank);
                    case "creeper_hugs" -> speed(mob, 0.05);
                    case "creeper_angel" -> speed(mob, 0.03 * rank);
                    case "zombie_armor" -> {
                        AttributeInstance movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                        if (movement != null) movement.addOrUpdateTransientModifier(new AttributeModifier(MobsLearn.id("armor_weight"), -0.025 * rank, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    }
                    case "skeleton_sniper" -> attribute(mob, Attributes.FOLLOW_RANGE, s.id(), 5.0 * rank);
                    case "pillager_sniper" -> attribute(mob, Attributes.FOLLOW_RANGE, s.id(), 4.0 * rank);
                    case "creeper_heavy" -> {
                        attribute(mob, Attributes.KNOCKBACK_RESISTANCE, s.id(), Math.min(0.8, 0.25 * rank));
                        attribute(mob, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, s.id(), Math.min(0.8, 0.25 * rank));
                    }
                    case "witch_tank" -> attribute(mob, Attributes.MAX_HEALTH, s.id(), 2.0 * rank);
                    case "cow_gym" -> {
                        attribute(mob, Attributes.SCALE, s.id(), 0.15 * rank);
                        attribute(mob, Attributes.MAX_HEALTH, s.id(), 4.0 * rank);
                    }
                    case "zombie_knockback" -> attribute(mob, Attributes.ATTACK_KNOCKBACK, s.id(), 0.3 * rank);
                    case "horse_turbo" -> {
                        attribute(mob, Attributes.MOVEMENT_SPEED, s.id(), 0.04 * rank);
                        attribute(mob, Attributes.JUMP_STRENGTH, s.id(), 0.08 * rank);
                    }
                    case "wolf_loyal" -> attribute(mob, Attributes.MAX_HEALTH, s.id(), 4.0 * rank);
                    case "wolf_pack" -> {
                        attribute(mob, Attributes.ATTACK_DAMAGE, s.id(), 1.0 * rank);
                        attribute(mob, Attributes.MAX_HEALTH, "wolf_pack_hp", 2.0 * rank);
                    }
                    default -> { }
                }
            }
            if (mob instanceof Creeper creeper) {
                int fuse = TraitTags.get(mob, "creeper_fuse"), blast = TraitTags.get(mob, "creeper_big");
                if (TraitTags.raw(mob, "creeper_fuse") > 0) ((CreeperAccessor) creeper).mobslearn$setMaxSwell(30 - 3 * fuse);
                if (TraitTags.raw(mob, "creeper_big") > 0) ((CreeperAccessor) creeper).mobslearn$setExplosionRadius(blast > 0 ? 4 : 3);
                if (TraitTags.raw(mob, "creeper_ninja") > 0) mob.setSilent(TraitTags.get(mob, "creeper_ninja") > 0);
            }
            mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
            updateAwareness(mob);
    }

    private static void refreshEffects(Mob mob, String id, int rank, boolean enabled) {
        switch (id) {
            case "zombie_fireproof", "phantom_daylight" -> reconcileEffect(mob, MobEffects.FIRE_RESISTANCE, 0, enabled);
            case "zombie_swimmer" -> reconcileEffect(mob, MobEffects.DOLPHINS_GRACE, rank - 1, enabled);
            case "creeper_jumper", "spider_climber" -> reconcileEffect(mob, MobEffects.JUMP_BOOST, Math.min(1, rank - 1), enabled);
            case "spider_ghost" -> reconcileEffect(mob, MobEffects.INVISIBILITY, 0, enabled);
            case "witch_tank" -> {
                if (rank >= 2) reconcileEffect(mob, MobEffects.RESISTANCE, 0, enabled);
                if (rank >= 3) reconcileEffect(mob, MobEffects.REGENERATION, 0, enabled);
            }
            case "witch_broom", "chicken_fly" -> {
                reconcileEffect(mob, MobEffects.SLOW_FALLING, 0, enabled);
                reconcileEffect(mob, MobEffects.JUMP_BOOST, rank, enabled);
            }
            case "rabbit_fast" -> {
                reconcileEffect(mob, MobEffects.SPEED, rank - 1, enabled);
                reconcileEffect(mob, MobEffects.JUMP_BOOST, 0, enabled);
            }
            case "golem_guard" -> {
                reconcileEffect(mob, MobEffects.STRENGTH, rank - 1, enabled);
                reconcileEffect(mob, MobEffects.SPEED, 0, enabled);
            }
            case "fish_smart" -> reconcileEffect(mob, MobEffects.SPEED, rank - 1, enabled);
            default -> { }
        }
    }

    private static void reconcileEffect(Mob mob, Holder<MobEffect> type, int amplifier, boolean enabled) {
        if (enabled) effect(mob, type, amplifier);
        else {
            MobEffectInstance current = mob.getEffect(type);
            if (current != null && current.isInfiniteDuration() && !current.isVisible()) mob.removeEffect(type);
        }
    }

    private static void migrate(Mob mob) {
        if (mob.entityTags().contains(BALANCE_V2)) return;
        mob.addTag(BALANCE_V2);
        MobEffectInstance sp = mob.getEffect(MobEffects.SPEED);
        if (sp != null && sp.isInfiniteDuration() && !sp.isVisible()) {
            mob.removeEffect(MobEffects.SPEED);
            if (TraitTags.get(mob, "zombie_sprinter") > 0 || TraitTags.get(mob, "creeper_hugs") > 0 || TraitTags.get(mob, "creeper_angel") > 0) speed(mob, 0.06);
        }
    }

    public static void relearn(ServerLevel level, Mob mob) {
        MobGroup g = MobGroup.of(mob.getType());
        if (g != null && g.hostile && mob.isAlive()) {
            updateAwareness(mob);
            if (g == MobGroup.ZOMBIE) practicalBasics(level, mob);
        }
        if (g == null || g.hostile || g == MobGroup.WORLD || !mob.isAlive()) return;
        Knowledge k = WorldBrain.get();
        int epoch = localEpoch(level, mob, g);
        if (epoch <= TraitTags.get(mob, "epoch")) return;
        TraitTags.set(mob, "epoch", epoch);
        RandomSource r = level.getRandom();
        java.util.List<Skill> list = new java.util.ArrayList<>(Skill.of(g));
        int traits = 0, combat = 0;
        for (Skill s : list) {
            if (TraitTags.get(mob, s.id()) > 0) {
                traits++;
                if (s.combat()) combat++;
            }
        }
        java.util.Collections.shuffle(list, new java.util.Random(r.nextLong()));
        try {
            for (Skill s : list) {
                int lvl = k.allows(s) ? WorldBrain.levelAt(s.id(), level, mob.blockPosition()) : 0;
                if (lvl <= 0) continue;
                int have = TraitTags.get(mob, s.id());
                if (have >= lvl) continue;
                if (have > 0) {
                    TraitTags.set(mob, s.id(), lvl);
                } else if (traits < MAX_TRAITS && (!s.combat() || combat < MAX_COMBAT) && r.nextFloat() < spawnChance(lvl)) {
                    apply(level, mob, s.id(), lvl, r);
                    traits++;
                    if (s.combat()) combat++;
                }
            }
        } catch (Exception ex) {
            MobsLearn.LOG.warn("[MobsLearn] Failed to update traits of {}", mob, ex);
        }
    }

    private static float spawnChance(int rank) {
        return CHANCE[rank] * switch (WorldBrain.get().difficulty) { case 0 -> 0.8f; case 2 -> 1.15f; default -> 1f; };
    }

    private static int localEpoch(ServerLevel level, Mob mob, MobGroup group) {
        int total = 0;
        for (Skill skill : Skill.of(group)) total += WorldBrain.levelAt(skill.id(), level, mob.blockPosition());
        return total;
    }

    private static void practicalBasics(ServerLevel level, Mob mob) {
        int intel = WorldBrain.get().intelligence();
        if (intel < 12 || !WorldBrain.get().enabled || TraitTags.raw(mob, "practical_epoch") >= intel) return;
        TraitTags.set(mob, "practical_epoch", intel);
        int count = 0, combat = 0;
        for (Skill skill : Skill.of(MobGroup.ZOMBIE)) if (TraitTags.get(mob, skill.id()) > 0) {
            count++;
            if (skill.combat()) combat++;
        }
        for (String id : new String[]{"zombie_builder", "zombie_digger", "zombie_bridge"}) {
            Skill skill = Skill.byId(id);
            int rank = WorldBrain.levelAt(id, level, mob.blockPosition());
            if (skill == null || rank <= 0 || TraitTags.raw(mob, id) > 0 || count >= MAX_TRAITS || (skill.combat() && combat >= MAX_COMBAT)) continue;
            if (level.getRandom().nextFloat() < 0.3f) {
                apply(level, mob, id, 1, level.getRandom());
                count++;
                if (skill.combat()) combat++;
            }
        }
    }

    public static void apply(ServerLevel level, Mob mob, String id, int lvl, RandomSource r) {
        Skill skill = Skill.byId(id);
        if (skill != null && !WorldBrain.get().allows(skill)) return;
        TraitTags.set(mob, id, lvl);
        String previousSkill = applyingSkill;
        applyingSkill = id;
        try {
        switch (id) {
            case "zombie_shield", "pillager_shield" -> equip(mob, EquipmentSlot.OFFHAND, Items.SHIELD);
            case "pillager_armor" -> armor(mob, lvl);
            case "pillager_sniper" -> {
                enchantMainHand(level, mob, Enchantments.QUICK_CHARGE, Math.min(3, lvl));
                attribute(mob, Attributes.FOLLOW_RANGE, id, 4.0 * lvl);
            }
            case "zombie_armor" -> {
                armor(mob, lvl);
                AttributeInstance movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (movement != null) movement.addOrUpdateTransientModifier(new AttributeModifier(MobsLearn.id("armor_weight"), -0.025 * lvl, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            case "zombie_fireproof", "phantom_daylight" -> effect(mob, MobEffects.FIRE_RESISTANCE, 0);
            case "zombie_sprinter" -> speed(mob, 0.04 * lvl);
            case "zombie_swimmer" -> effect(mob, MobEffects.DOLPHINS_GRACE, lvl - 1);
            case "zombie_copycat", "pillager_copycat" -> {
                Player p = level.getNearestPlayer(mob, 96);
                if (p != null) {
                    copy(mob, p, EquipmentSlot.HEAD);
                    if (lvl >= 2) copy(mob, p, EquipmentSlot.FEET);
                    if (lvl >= 3) copy(mob, p, EquipmentSlot.CHEST);
                    if (!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && mob.distanceTo(p) < 48) com.rrm.mobslearn.learn.Milestones.award(p, "copycat");
                }
            }
            case "zombie_kindergarten" -> {
                if (mob instanceof Zombie z && !z.isBaby() && r.nextFloat() < 0.03f * lvl) z.setBaby(true);
            }
            case "skeleton_sniper" -> {
                enchantMainHand(level, mob, Enchantments.POWER, Math.min(2, lvl));
                attribute(mob, Attributes.FOLLOW_RANGE, id, 5.0 * lvl);
            }
            case "skeleton_flame" -> enchantMainHand(level, mob, Enchantments.FLAME, 1);
            case "skeleton_punch" -> enchantMainHand(level, mob, Enchantments.PUNCH, Math.min(2, lvl));
            case "skeleton_sunhat" -> equip(mob, EquipmentSlot.HEAD, lvl >= 3 ? Items.TURTLE_HELMET : Items.LEATHER_HELMET);
            case "creeper_fuse" -> {
                if (mob instanceof Creeper c) ((CreeperAccessor) c).mobslearn$setMaxSwell(30 - 3 * lvl);
            }
            case "creeper_ninja" -> mob.setSilent(true);
            case "creeper_heavy" -> {
                attribute(mob, Attributes.KNOCKBACK_RESISTANCE, id, Math.min(0.8, 0.25 * lvl));
                attribute(mob, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, id, Math.min(0.8, 0.25 * lvl));
            }
            case "creeper_jumper" -> effect(mob, MobEffects.JUMP_BOOST, Math.min(1, lvl - 1));
            case "creeper_big" -> {
                if (mob instanceof Creeper c) ((CreeperAccessor) c).mobslearn$setExplosionRadius(4);
            }
            case "creeper_hugs" -> speed(mob, 0.05);
            case "spider_climber" -> {
                effect(mob, MobEffects.JUMP_BOOST, Math.min(1, lvl - 1));
            }
            case "spider_ghost" -> effect(mob, MobEffects.INVISIBILITY, 0);
            case "enderman_florist" -> {
                if (mob instanceof Enderman en && en.getCarriedBlock() == null) en.setCarriedBlock(flower(r, lvl));
            }
            case "witch_tank" -> {
                if (lvl >= 2) effect(mob, MobEffects.RESISTANCE, 0);
                if (lvl >= 3) effect(mob, MobEffects.REGENERATION, 0);
                attribute(mob, Attributes.MAX_HEALTH, id, 2.0 * lvl);
                mob.setHealth(mob.getMaxHealth());
            }
            case "witch_broom" -> {
                effect(mob, MobEffects.SLOW_FALLING, 0);
                effect(mob, MobEffects.JUMP_BOOST, lvl);
            }
            case "slime_big" -> {
                if (mob instanceof Slime s) s.setSize(Math.min(12, s.getSize() + 2 * lvl), true);
            }
            case "cow_gym" -> {
                attribute(mob, Attributes.SCALE, id, 0.15 * lvl);
                attribute(mob, Attributes.MAX_HEALTH, id, 4.0 * lvl);
                mob.setHealth(mob.getMaxHealth());
            }
            case "sheep_style" -> {
                if (mob instanceof Sheep sh) {
                    sh.setColor(DyeColor.values()[r.nextInt(DyeColor.values().length)]);
                    if (lvl >= 3 && r.nextFloat() < 0.2f) sh.setCustomName(Component.literal("jeb_"));
                }
            }
            case "chicken_fly" -> {
                effect(mob, MobEffects.SLOW_FALLING, 0);
                effect(mob, MobEffects.JUMP_BOOST, lvl);
            }
            case "pig_escape" -> {
                if (lvl >= 3 && r.nextFloat() < 0.3f) equip(mob, EquipmentSlot.SADDLE, Items.SADDLE);
            }
            case "rabbit_fast" -> {
                effect(mob, MobEffects.SPEED, lvl - 1);
                effect(mob, MobEffects.JUMP_BOOST, 0);
            }
            case "golem_guard" -> {
                effect(mob, MobEffects.STRENGTH, lvl - 1);
                effect(mob, MobEffects.SPEED, 0);
            }
            case "wolf_loyal" -> {
                attribute(mob, Attributes.MAX_HEALTH, id, 4.0 * lvl);
                mob.setHealth(mob.getMaxHealth());
            }
            case "fish_smart" -> {
                effect(mob, MobEffects.SPEED, lvl - 1);
                if (!mob.hasCustomName()) mob.setCustomName(Component.translatable("mobslearn.fishname." + r.nextInt(FISH_NAME_COUNT)));
            }
            case "zombie_pumpkin" -> equip(mob, EquipmentSlot.HEAD, Items.CARVED_PUMPKIN);
            case "zombie_knockback" -> attribute(mob, Attributes.ATTACK_KNOCKBACK, id, 0.3 * lvl);
            case "zombie_totem" -> {
                if (r.nextFloat() < 0.04f * lvl) equip(mob, EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING);
            }
            case "creeper_angel" -> speed(mob, 0.03 * lvl);
            case "horse_turbo" -> {
                attribute(mob, Attributes.MOVEMENT_SPEED, id, 0.04 * lvl);
                attribute(mob, Attributes.JUMP_STRENGTH, id, 0.08 * lvl);
            }
            case "spider_jockey" -> {
                if (r.nextFloat() < 0.07f * lvl) {
                    Behaviors.later(1, () -> {
                        if (!mob.isAlive() || mob.isVehicle()) return;
                        Mob rider = EntityTypes.SKELETON.create(level, EntitySpawnReason.JOCKEY);
                        if (rider == null) return;
                        rider.addTag(TraitTags.SEEN);
                        rider.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0f);
                        rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                        rider.setDropChance(EquipmentSlot.MAINHAND, 0f);
                        level.addFreshEntity(rider);
                        rider.startRiding(mob);
                    });
                }
            }
            case "wolf_pack" -> {
                attribute(mob, Attributes.ATTACK_DAMAGE, id, 1.0 * lvl);
                attribute(mob, Attributes.MAX_HEALTH, "wolf_pack_hp", 2.0 * lvl);
                mob.setHealth(mob.getMaxHealth());
            }
            case "phantom_swarm" -> {
                if (r.nextFloat() < 0.2f * lvl) {
                    Behaviors.later(2, () -> {
                        if (!mob.isAlive()) return;
                        Mob extra = EntityTypes.PHANTOM.create(level, EntitySpawnReason.TRIGGERED);
                        if (extra == null) return;
                        extra.addTag(TraitTags.SEEN);
                        extra.snapTo(mob.getX() + r.nextGaussian() * 3, mob.getY() + r.nextDouble() * 2, mob.getZ() + r.nextGaussian() * 3, mob.getYRot(), 0f);
                        level.addFreshEntity(extra);
                    });
                }
            }
            default -> {
            }
        }
        } finally { applyingSkill = previousSkill; }
    }

    private static void effect(Mob mob, Holder<MobEffect> effect, int amp) {
        mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, Math.max(0, amp), false, false));
    }

    private static void attribute(Mob mob, Holder<Attribute> attr, String id, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || inst.hasModifier(MobsLearn.id(id))) return;
        inst.addPermanentModifier(new AttributeModifier(MobsLearn.id(id), value, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void speed(Mob mob, double pct) {
        AttributeInstance inst = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null || inst.hasModifier(MobsLearn.id(SPEED_ID))) return;
        inst.addPermanentModifier(new AttributeModifier(MobsLearn.id(SPEED_ID), pct, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void equip(Mob mob, EquipmentSlot slot, Item item) {
        if (!mob.getItemBySlot(slot).isEmpty()) return;
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, 0f);
        mob.addTag("mobslearn.equipped." + slot.getName() + "=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        mob.addTag("mobslearn.equip_skill." + slot.getName() + "=" + (applyingSkill == null ? "world_hats" : applyingSkill));
    }

    private static void reconcileEquipment(Mob mob) {
        String ownerPrefix = "mobslearn.equip_skill.";
        for (String owner : java.util.List.copyOf(mob.entityTags())) {
            if (!owner.startsWith(ownerPrefix)) continue;
            int split = owner.indexOf('=');
            if (split < 0) continue;
            String slotName = owner.substring(ownerPrefix.length(), split);
            Skill skill = Skill.byId(owner.substring(split + 1));
            if (skill != null && WorldBrain.get().allows(skill)) continue;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!slot.getName().equals(slotName)) continue;
                String itemPrefix = "mobslearn.equipped." + slotName + "=";
                for (String item : java.util.List.copyOf(mob.entityTags())) if (item.startsWith(itemPrefix)) {
                    String current = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(slot).getItem()).toString();
                    if (current.equals(item.substring(itemPrefix.length()))) mob.setItemSlot(slot, ItemStack.EMPTY);
                    mob.removeTag(item);
                }
                mob.removeTag(owner);
            }
        }
        String previousSkill = applyingSkill;
        for (Skill skill : Skill.ALL) {
            int rank = TraitTags.get(mob, skill.id());
            if (rank <= 0) continue;
            applyingSkill = skill.id();
            switch (skill.id()) {
                case "zombie_shield", "pillager_shield" -> equip(mob, EquipmentSlot.OFFHAND, Items.SHIELD);
                case "zombie_armor", "pillager_armor" -> armor(mob, rank);
                case "skeleton_sunhat" -> equip(mob, EquipmentSlot.HEAD, rank >= 3 ? Items.TURTLE_HELMET : Items.LEATHER_HELMET);
                case "zombie_pumpkin" -> equip(mob, EquipmentSlot.HEAD, Items.CARVED_PUMPKIN);
                default -> { }
            }
        }
        applyingSkill = previousSkill;
    }

    private static void armor(Mob mob, int lvl) {
        Player p = mob.level().getNearestPlayer(mob, 64);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Item[] fallback = {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
        int pieces = lvl >= 3 ? 4 : lvl == 2 ? 2 : 1;
        for (int i = 0; i < pieces; i++) {
            ItemStack worn = p == null || p.isCreative() ? ItemStack.EMPTY : p.getItemBySlot(slots[i]);
            boolean real = !worn.isEmpty() && worn.has(net.minecraft.core.component.DataComponents.EQUIPPABLE) && !worn.is(Items.ELYTRA);
            equip(mob, slots[i], real ? worn.getItem() : fallback[i]);
        }
    }

    private static void copy(Mob mob, Player p, EquipmentSlot slot) {
        ItemStack worn = p.getItemBySlot(slot);
        if (!worn.isEmpty()) equip(mob, slot, worn.getItem());
    }

    private static void enchantMainHand(ServerLevel level, Mob mob, ResourceKey<Enchantment> key, int lvl) {
        ItemStack hand = mob.getMainHandItem();
        if (hand.isEmpty()) return;
        Holder<Enchantment> ench = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        hand.enchant(ench, lvl);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    private static BlockState flower(RandomSource r, int lvl) {
        if (lvl >= 3 && r.nextFloat() < 0.15f) return Blocks.TNT.defaultBlockState();
        BlockState[] f = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.BLUE_ORCHID.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState()};
        return f[r.nextInt(f.length)];
    }
}
