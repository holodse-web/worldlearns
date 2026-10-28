package com.rrm.mobslearn.learn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record Skill(String id, MobGroup group, Trigger trigger, MobGroup source, int[] thresholds, String iconId, boolean absurd) {
    public static final int MAX_LEVEL = 3;
    public static final float[] SPAWN_CHANCE = {0f, 0.25f, 0.4f, 0.55f};
    public static final int MAX_PER_MOB = 4, MAX_COMBAT_PER_MOB = 2;

    public int level(double points) {
        int lvl = 0;
        for (int t : thresholds) if (points >= t) lvl++;
        return lvl;
    }

    public int nextThreshold(double points) {
        for (int i = 0; i < maxLevel(); i++) if (points < thresholds[i]) return thresholds[i];
        return -1;
    }

    public int maxLevel() {
        return group == MobGroup.WORLD && thresholds[1] > 100 ? 1 : MAX_LEVEL;
    }

    private static final java.util.Set<String> COMBAT = java.util.Set.of(
        "zombie_crit", "zombie_medic", "zombie_gapple", "zombie_strafe", "zombie_bridge", "zombie_knockback", "zombie_totem",
        "skeleton_volley", "skeleton_dodge", "skeleton_swordsman", "skeleton_medic", "creeper_flank", "creeper_dash",
        "spider_pounce", "spider_strafe", "spider_medic", "enderman_combo", "enderman_medic", "witch_kite",
        "pillager_strafe", "cow_kick", "cow_herd", "pig_charge", "sheep_ram", "rabbit_bite", "horse_kick", "golem_throw", "witch_brew");

    public boolean combat() {
        return COMBAT.contains(id);
    }

    public Component displayName() {
        return Component.translatable("mobslearn.skill." + id);
    }

    public Component description() {
        return Component.translatable("mobslearn.skill." + id + ".desc");
    }

    public ItemStack icon() {
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.withDefaultNamespace(iconId));
        return new ItemStack(item == Items.AIR ? Items.BOOK : item);
    }

    private static final Map<String, Skill> BY_ID = new LinkedHashMap<>();
    public static final List<Skill> ALL;

    private static void add(String id, MobGroup group, Trigger trigger, int a, int b, int c, String icon) {
        add(id, group, trigger, group, a, b, c, icon, false);
    }

    private static void absurd(String id, MobGroup group, Trigger trigger, int a, int b, int c, String icon) {
        add(id, group, trigger, group, a, b, c, icon, true);
    }

    private static void add(String id, MobGroup group, Trigger trigger, MobGroup source, int a, int b, int c, String icon, boolean absurd) {
        if (trigger != Trigger.WORLD && !trigger.name().startsWith("TAUGHT")) {
            a = Math.max(1, (int) Math.ceil(a / 2.5));
            b = Math.max(a + 1, (int) Math.ceil(b / 2.5));
            c = Math.max(b + 1, (int) Math.ceil(c / 2.5));
        }
        BY_ID.put(id, new Skill(id, group, trigger, source, new int[]{a, b, c}, icon, absurd));
    }

    static {
        add("zombie_shield", MobGroup.ZOMBIE, Trigger.KILL_BLADE, 15, 40, 90, "shield");
        add("zombie_armor", MobGroup.ZOMBIE, Trigger.SEE_ARMOR, 20, 50, 100, "iron_chestplate");
        add("zombie_fireproof", MobGroup.ZOMBIE, Trigger.KILL_FIRE, 8, 20, 45, "fire_charge");
        add("zombie_digger", MobGroup.ZOMBIE, Trigger.SEE_DIG, 15, 40, 80, "iron_pickaxe");
        add("zombie_builder", MobGroup.ZOMBIE, Trigger.SEE_PILLAR, 10, 25, 50, "dirt");
        add("zombie_sprinter", MobGroup.ZOMBIE, Trigger.ESCAPE, 10, 30, 60, "sugar");
        add("zombie_swimmer", MobGroup.ZOMBIE, Trigger.KILL_IN_WATER, 10, 25, 50, "prismarine_shard");
        absurd("zombie_copycat", MobGroup.ZOMBIE, Trigger.KILL_ANY, 40, 100, 200, "golden_helmet");
        absurd("zombie_dancer", MobGroup.ZOMBIE, Trigger.CROUCH_NEAR, 20, 60, 150, "note_block");
        absurd("zombie_kindergarten", MobGroup.ZOMBIE, Trigger.KILL_ANY, 60, 150, 300, "cake");
        absurd("zombie_hungry", MobGroup.ZOMBIE, Trigger.KILLED_PLAYER, 2, 5, 10, "cooked_beef");
        absurd("zombie_pumpkin", MobGroup.ZOMBIE, Trigger.SUN_BURN, 10, 30, 60, "carved_pumpkin");
        add("zombie_crit", MobGroup.ZOMBIE, Trigger.PLAYER_CRIT, 10, 25, 50, "iron_sword");
        add("zombie_medic", MobGroup.ZOMBIE, Trigger.PLAYER_HEAL, 4, 10, 20, "glistering_melon_slice");
        add("zombie_gapple", MobGroup.ZOMBIE, Trigger.PLAYER_HEAL, 8, 20, 40, "golden_apple");
        add("zombie_strafe", MobGroup.ZOMBIE, Trigger.DODGE, 10, 25, 50, "leather_boots");
        add("zombie_bridge", MobGroup.ZOMBIE, Trigger.SEE_BRIDGE, 15, 40, 80, "cobblestone");
        add("zombie_knockback", MobGroup.ZOMBIE, Trigger.KILLED_PLAYER, 3, 8, 15, "stick");
        add("zombie_totem", MobGroup.ZOMBIE, Trigger.KILL_ANY, 80, 200, 400, "totem_of_undying");
        add("skeleton_sniper", MobGroup.SKELETON, Trigger.KILL_PROJECTILE, 15, 40, 80, "bow");
        add("skeleton_flame", MobGroup.SKELETON, Trigger.SHIELD_BLOCK, 15, 40, 80, "flint_and_steel");
        add("skeleton_kite", MobGroup.SKELETON, Trigger.KILL_MELEE, 20, 50, 100, "feather");
        add("skeleton_punch", MobGroup.SKELETON, Trigger.KILL_FALL, 5, 15, 30, "piston");
        absurd("skeleton_sunhat", MobGroup.SKELETON, Trigger.SUN_BURN, 10, 30, 60, "leather_helmet");
        absurd("skeleton_xylophone", MobGroup.SKELETON, Trigger.KILL_ANY, 50, 120, 250, "bone_block");
        absurd("skeleton_fertilizer", MobGroup.SKELETON, Trigger.KILL_ANY, 30, 80, 160, "bone_meal");
        add("skeleton_volley", MobGroup.SKELETON, Trigger.KILL_PROJECTILE, 25, 60, 120, "arrow");
        add("skeleton_dodge", MobGroup.SKELETON, Trigger.KILL_PROJECTILE, 15, 40, 80, "rabbit_foot");
        add("skeleton_swordsman", MobGroup.SKELETON, Trigger.KILL_MELEE, 15, 40, 80, "stone_sword");
        add("skeleton_medic", MobGroup.SKELETON, Trigger.PLAYER_HEAL, 4, 10, 20, "glistering_melon_slice");
        add("creeper_fuse", MobGroup.CREEPER, Trigger.KILL_MELEE, 15, 40, 80, "gunpowder");
        add("creeper_ninja", MobGroup.CREEPER, Trigger.KILL_CALM, 20, 50, 100, "black_dye");
        add("creeper_heavy", MobGroup.CREEPER, Trigger.SHIELD_BLOCK, 3, 8, 15, "iron_block");
        add("creeper_jumper", MobGroup.CREEPER, Trigger.UNREACHABLE, 8, 20, 40, "slime_ball");
        add("creeper_big", MobGroup.CREEPER, Trigger.EXPLODE, 10, 30, 60, "tnt");
        absurd("creeper_party", MobGroup.CREEPER, Trigger.KILLED_PLAYER, 1, 3, 6, "firework_rocket");
        absurd("creeper_hugs", MobGroup.CREEPER, Trigger.KILL_PROJECTILE, 20, 50, 100, "pink_dye");
        absurd("creeper_angel", MobGroup.CREEPER, Trigger.ESCAPE, 10, 25, 50, "ender_eye");
        absurd("creeper_apology", MobGroup.CREEPER, Trigger.KILL_ANY, 30, 80, 150, "poppy");
        add("creeper_flank", MobGroup.CREEPER, Trigger.KILL_PROJECTILE, 15, 40, 80, "compass");
        add("creeper_dash", MobGroup.CREEPER, Trigger.KILL_CALM, 15, 40, 80, "sugar");
        add("spider_climber", MobGroup.SPIDER, Trigger.UNREACHABLE, 6, 16, 35, "vine");
        add("spider_web", MobGroup.SPIDER, Trigger.ESCAPE, 10, 25, 50, "cobweb");
        add("spider_venom", MobGroup.SPIDER, Trigger.KILL_MELEE, 20, 50, 100, "spider_eye");
        absurd("spider_ghost", MobGroup.SPIDER, Trigger.KILL_DAY, 30, 80, 150, "glass");
        absurd("spider_family", MobGroup.SPIDER, Trigger.KILL_ANY, 50, 120, 250, "string");
        add("spider_jockey", MobGroup.SPIDER, Trigger.KILL_PROJECTILE, 10, 25, 50, "saddle");
        add("spider_pounce", MobGroup.SPIDER, Trigger.PLAYER_CRIT, 8, 20, 40, "fermented_spider_eye");
        add("spider_strafe", MobGroup.SPIDER, Trigger.DODGE, 10, 25, 50, "leather_boots");
        add("spider_medic", MobGroup.SPIDER, Trigger.PLAYER_HEAL, 4, 10, 20, "glistering_melon_slice");
        add("enderman_behind", MobGroup.ENDERMAN, Trigger.UNREACHABLE, 5, 15, 30, "ender_pearl");
        absurd("enderman_umbrella", MobGroup.ENDERMAN, Trigger.WATER_HURT, 10, 25, 50, "water_bucket");
        absurd("enderman_florist", MobGroup.ENDERMAN, Trigger.KILL_ANY, 15, 40, 80, "poppy");
        absurd("enderman_thief", MobGroup.ENDERMAN, Trigger.KILLED_PLAYER, 2, 5, 10, "grass_block");
        add("enderman_combo", MobGroup.ENDERMAN, Trigger.PLAYER_CRIT, 5, 15, 30, "ender_eye");
        add("enderman_medic", MobGroup.ENDERMAN, Trigger.PLAYER_HEAL, 4, 10, 20, "chorus_fruit");
        add("witch_tank", MobGroup.WITCH, Trigger.KILL_ANY, 5, 15, 30, "glass_bottle");
        absurd("witch_broom", MobGroup.WITCH, Trigger.KILL_PROJECTILE, 5, 15, 30, "wooden_shovel");
        add("witch_kite", MobGroup.WITCH, Trigger.KILL_MELEE, 5, 15, 30, "feather");
        add("slime_sticky", MobGroup.SLIME, Trigger.KILL_MELEE, 20, 50, 100, "slime_block");
        absurd("slime_big", MobGroup.SLIME, Trigger.KILL_ANY, 40, 100, 200, "honey_block");
        absurd("slime_bouncy", MobGroup.SLIME, Trigger.KILL_FALL, 3, 8, 15, "slime_ball");
        absurd("phantom_daylight", MobGroup.PHANTOM, Trigger.KILL_PROJECTILE, 10, 25, 50, "phantom_membrane");
        add("cow_hide", MobGroup.COW, Trigger.MILK, 10, 30, 60, "milk_bucket");
        absurd("cow_gym", MobGroup.COW, Trigger.KILL_ANY, 20, 50, 100, "leather");
        absurd("sheep_style", MobGroup.SHEEP, Trigger.SHEAR, 15, 40, 80, "shears");
        absurd("sheep_balloon", MobGroup.SHEEP, Trigger.SHEAR, 25, 60, 120, "white_wool");
        absurd("chicken_fly", MobGroup.CHICKEN, Trigger.KILL_ANY, 20, 50, 100, "feather");
        add("pig_escape", MobGroup.PIG, Trigger.KILL_ANY, 20, 50, 100, "porkchop");
        add("rabbit_fast", MobGroup.RABBIT, Trigger.KILL_ANY, 10, 25, 50, "rabbit_foot");
        add("villager_fear", MobGroup.VILLAGER, Trigger.HIT_VILLAGER, 3, 10, 25, "iron_sword");
        absurd("villager_gossip", MobGroup.VILLAGER, Trigger.TRADE, 30, 80, 150, "emerald");
        add("golem_guard", MobGroup.IRON_GOLEM, Trigger.HIT_VILLAGER, MobGroup.VILLAGER, 5, 15, 30, "iron_block", false);
        add("wolf_loyal", MobGroup.WOLF, Trigger.FEED, 5, 15, 30, "bone");
        absurd("wolf_fetch", MobGroup.WOLF, Trigger.FEED, 10, 25, 50, "stick");
        absurd("cat_gift", MobGroup.CAT, Trigger.FEED, 5, 15, 30, "cod");
        absurd("parrot_mimic", MobGroup.PARROT, Trigger.FEED, 5, 15, 30, "wheat_seeds");
        add("squid_ink", MobGroup.SQUID, Trigger.KILL_ANY, 15, 40, 80, "ink_sac");
        add("bee_angry", MobGroup.BEE, Trigger.HONEY, 5, 15, 30, "honeycomb");
        absurd("fish_smart", MobGroup.FISH, Trigger.KILL_ANY, 20, 50, 100, "tropical_fish");
        add("horse_turbo", MobGroup.HORSE, Trigger.FEED, 10, 25, 50, "golden_carrot");
        add("cow_kick", MobGroup.COW, Trigger.KILL_MELEE, 16, 40, 80, "beef");
        add("cow_herd", MobGroup.COW, Trigger.KILL_ANY, 40, 100, 200, "lead");
        add("pig_charge", MobGroup.PIG, Trigger.KILL_MELEE, 16, 40, 80, "cooked_porkchop");
        absurd("pig_truffle", MobGroup.PIG, Trigger.FEED, 10, 30, 60, "gold_nugget");
        add("sheep_ram", MobGroup.SHEEP, Trigger.KILL_MELEE, 16, 40, 80, "goat_horn");
        absurd("chicken_eggs", MobGroup.CHICKEN, Trigger.KILL_MELEE, 16, 40, 80, "egg");
        add("rabbit_bite", MobGroup.RABBIT, Trigger.KILL_ANY, 20, 50, 100, "rabbit_hide");
        absurd("villager_slap", MobGroup.VILLAGER, Trigger.HIT_VILLAGER, 4, 12, 24, "leather");
        add("villager_golem", MobGroup.VILLAGER, Trigger.HIT_VILLAGER, 10, 30, 60, "bell");
        add("golem_throw", MobGroup.IRON_GOLEM, Trigger.HIT_VILLAGER, MobGroup.VILLAGER, 10, 30, 60, "iron_ingot", false);
        add("golem_flowers", MobGroup.IRON_GOLEM, Trigger.TRADE, MobGroup.VILLAGER, 20, 60, 120, "poppy", true);
        add("wolf_pack", MobGroup.WOLF, Trigger.FEED, 10, 30, 60, "wolf_armor");
        absurd("cat_ninelives", MobGroup.CAT, Trigger.FEED, 10, 30, 60, "totem_of_undying");
        add("parrot_warn", MobGroup.PARROT, Trigger.FEED, 10, 30, 60, "spyglass");
        add("bee_swarm", MobGroup.BEE, Trigger.HONEY, 10, 30, 60, "beehive");
        absurd("bee_pollinate", MobGroup.BEE, Trigger.HONEY, 20, 50, 100, "sunflower");
        add("horse_kick", MobGroup.HORSE, Trigger.KILL_MELEE, 4, 12, 24, "leather_horse_armor");
        add("slime_split", MobGroup.SLIME, Trigger.KILL_MELEE, 30, 80, 160, "magma_cream");
        add("phantom_swarm", MobGroup.PHANTOM, Trigger.KILL_ANY, 10, 30, 60, "elytra");
        add("witch_brew", MobGroup.WITCH, Trigger.KILL_MELEE, 10, 30, 60, "dragon_breath");
        add("zombie_camp", MobGroup.ZOMBIE, Trigger.SEE_BUILD, 24, 80, 200, "mossy_cobblestone");
        add("skeleton_tower", MobGroup.SKELETON, Trigger.SEE_BUILD, 24, 80, 200, "ladder");
        add("spider_nest", MobGroup.SPIDER, Trigger.SEE_BUILD, 20, 65, 160, "string");
        add("creeper_ambush", MobGroup.CREEPER, Trigger.KILL_PROJECTILE, 20, 50, 100, "oak_leaves");
        add("villager_build", MobGroup.VILLAGER, Trigger.TRADE, 20, 60, 150, "lantern");
        absurd("chicken_nest", MobGroup.CHICKEN, Trigger.FEED, 10, 30, 60, "hay_block");
        add("villager_sword", MobGroup.VILLAGER, Trigger.TAUGHT_SWORD, 1, 3, 6, "iron_sword");
        add("villager_shield", MobGroup.VILLAGER, Trigger.TAUGHT_SHIELD, 1, 3, 6, "shield");
        add("villager_tools", MobGroup.VILLAGER, Trigger.TAUGHT_TOOLS, 1, 3, 6, "iron_pickaxe");
        absurd("villager_dance", MobGroup.VILLAGER, Trigger.PLAYER_JUMP, 25, 70, 150, "jukebox");
        absurd("villager_tease", MobGroup.VILLAGER, Trigger.CHAT_NEAR, 6, 18, 40, "goat_horn");
        add("pillager_shield", MobGroup.PILLAGER, Trigger.KILL_BLADE, 15, 40, 90, "shield");
        add("pillager_armor", MobGroup.PILLAGER, Trigger.SEE_ARMOR, 20, 50, 100, "iron_chestplate");
        add("pillager_sniper", MobGroup.PILLAGER, Trigger.KILL_PROJECTILE, 15, 40, 80, "crossbow");
        add("pillager_strafe", MobGroup.PILLAGER, Trigger.DODGE, 10, 25, 50, "leather_boots");
        absurd("pillager_copycat", MobGroup.PILLAGER, Trigger.KILL_ANY, 40, 100, 200, "emerald");
        absurd("world_names", MobGroup.WORLD, Trigger.WORLD, 10, 30, 60, "name_tag");
        absurd("world_talk", MobGroup.WORLD, Trigger.WORLD, 15, 40, 70, "writable_book");
        add("world_genius", MobGroup.WORLD, Trigger.WORLD, 15, 45, 85, "enchanted_book");
        absurd("world_hats", MobGroup.WORLD, Trigger.WORLD, 20, 45, 80, "carved_pumpkin");
        add("world_herd", MobGroup.WORLD, Trigger.WORLD, 20, 101, 102, "wheat");
        add("world_doors", MobGroup.WORLD, Trigger.WORLD, 25, 101, 102, "oak_door");
        add("world_torches", MobGroup.WORLD, Trigger.WORLD, 35, 101, 102, "torch");
        add("world_track", MobGroup.WORLD, Trigger.WORLD, 45, 101, 102, "compass");
        add("world_ears", MobGroup.WORLD, Trigger.WORLD, 55, 101, 102, "echo_shard");
        add("world_storm", MobGroup.WORLD, Trigger.WORLD, 65, 101, 102, "lightning_rod");
        add("world_pack", MobGroup.WORLD, Trigger.WORLD, 75, 101, 102, "bone");
        ALL = Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static Skill byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Skill> of(MobGroup group) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL) if (s.group == group) out.add(s);
        return out;
    }
}
