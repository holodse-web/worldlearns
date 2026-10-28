package com.rrm.mobslearn.learn;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum MobGroup {
    ZOMBIE("zombie_spawn_egg", 0xFF6FBF4A, true, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER),
    SKELETON("skeleton_spawn_egg", 0xFFD8D8D0, true, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED),
    CREEPER("creeper_spawn_egg", 0xFF4FD15A, true, EntityType.CREEPER),
    SPIDER("spider_spawn_egg", 0xFFC2453A, true, EntityType.SPIDER, EntityType.CAVE_SPIDER),
    ENDERMAN("enderman_spawn_egg", 0xFFB46CFF, true, EntityType.ENDERMAN),
    WITCH("witch_spawn_egg", 0xFF9B4FD1, true, EntityType.WITCH),
    SLIME("slime_spawn_egg", 0xFF7FD957, true, EntityType.SLIME),
    PHANTOM("phantom_spawn_egg", 0xFF5C7FD6, true, EntityType.PHANTOM),
    PILLAGER("pillager_spawn_egg", 0xFF8A9A9C, true, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.ILLUSIONER),
    COW("cow_spawn_egg", 0xFFB08560, false, EntityType.COW, EntityType.MOOSHROOM),
    SHEEP("sheep_spawn_egg", 0xFFEDEDED, false, EntityType.SHEEP),
    CHICKEN("chicken_spawn_egg", 0xFFF2F2F2, false, EntityType.CHICKEN),
    PIG("pig_spawn_egg", 0xFFF2A2A8, false, EntityType.PIG),
    RABBIT("rabbit_spawn_egg", 0xFFC9A67A, false, EntityType.RABBIT),
    VILLAGER("villager_spawn_egg", 0xFFB3866A, false, EntityType.VILLAGER),
    IRON_GOLEM("iron_golem_spawn_egg", 0xFFCFCFC4, false, EntityType.IRON_GOLEM),
    WOLF("wolf_spawn_egg", 0xFFD9D2C8, false, EntityType.WOLF),
    CAT("cat_spawn_egg", 0xFFE8B86A, false, EntityType.CAT),
    PARROT("parrot_spawn_egg", 0xFFE84A3A, false, EntityType.PARROT),
    SQUID("squid_spawn_egg", 0xFF4F7FA8, false, EntityType.SQUID, EntityType.GLOW_SQUID),
    BEE("bee_spawn_egg", 0xFFF2C53A, false, EntityType.BEE),
    FISH("cod_spawn_egg", 0xFF8FB7C9, false, EntityType.COD, EntityType.SALMON),
    HORSE("horse_spawn_egg", 0xFFC08A52, false, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE),
    WORLD("knowledge_book", 0xFFFFD25A, false);

    public final String iconId;
    public final int color;
    public final boolean hostile;
    private final Set<EntityType<?>> types;

    MobGroup(String iconId, int color, boolean hostile, EntityType<?>... types) {
        this.iconId = iconId;
        this.color = color;
        this.hostile = hostile;
        this.types = Set.of(types);
    }

    public boolean contains(EntityType<?> type) {
        return types.contains(type);
    }

    public static MobGroup of(EntityType<?> type) {
        for (MobGroup g : values()) if (g.types.contains(type)) return g;
        return null;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("mobslearn.group." + key());
    }

    public ItemStack icon() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(iconId));
        return new ItemStack(item == Items.AIR ? Items.BOOK : item);
    }
}
