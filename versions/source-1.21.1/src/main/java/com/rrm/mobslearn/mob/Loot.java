package com.rrm.mobslearn.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Loot {
    private Loot() {
    }

    private record E(Item item, int min, int max, float chance) {
    }

    private static final E[] VILLAGE = {new E(Items.BREAD, 1, 3, 0.7f), new E(Items.WHEAT, 2, 6, 0.6f), new E(Items.WHEAT_SEEDS, 2, 5, 0.6f),
        new E(Items.CARROT, 1, 4, 0.5f), new E(Items.POTATO, 1, 4, 0.5f), new E(Items.APPLE, 1, 2, 0.4f), new E(Items.EMERALD, 1, 2, 0.3f),
        new E(Items.IRON_NUGGET, 1, 4, 0.4f), new E(Items.TORCH, 2, 4, 0.4f), new E(Items.COAL, 1, 3, 0.4f), new E(Items.LEATHER, 1, 2, 0.2f),
        new E(Items.BOOK, 1, 1, 0.1f)};
    private static final E[] ZOMBIE = {new E(Items.ROTTEN_FLESH, 2, 6, 0.8f), new E(Items.IRON_NUGGET, 1, 5, 0.5f), new E(Items.BREAD, 1, 2, 0.4f),
        new E(Items.POTATO, 1, 3, 0.4f), new E(Items.CARROT, 1, 2, 0.3f), new E(Items.GOLD_NUGGET, 1, 3, 0.4f), new E(Items.IRON_INGOT, 1, 1, 0.2f),
        new E(Items.EMERALD, 1, 1, 0.15f), new E(Items.STRING, 1, 2, 0.3f)};
    private static final E[] SKELETON = {new E(Items.BONE, 2, 6, 0.8f), new E(Items.ARROW, 3, 10, 0.7f), new E(Items.STRING, 1, 3, 0.5f),
        new E(Items.FLINT, 1, 3, 0.4f), new E(Items.BONE_MEAL, 2, 4, 0.4f), new E(Items.IRON_NUGGET, 1, 3, 0.3f), new E(Items.EMERALD, 1, 1, 0.1f)};
    private static final E[] CREEPER = {new E(Items.GUNPOWDER, 1, 4, 0.8f), new E(Items.MOSS_BLOCK, 1, 2, 0.5f), new E(Items.GLOW_BERRIES, 1, 3, 0.4f),
        new E(Items.AZALEA, 1, 2, 0.3f), new E(Items.OAK_SAPLING, 1, 2, 0.3f), new E(Items.STRING, 1, 2, 0.3f), new E(Items.EMERALD, 1, 1, 0.1f)};

    public static String flavor(String kind, String crew) {
        String k = crew != null ? crew : kind;
        if (kind.startsWith("v") || "villager".equals(k)) return "village";
        if (kind.equals("shouse") || kind.equals("skeleton") || kind.equals("spalisade") || "skeleton".equals(k)) return "skeleton";
        if (kind.startsWith("c") || "creeper".equals(k)) return "creeper";
        return "zombie";
    }

    public static void fill(ServerLevel level, BlockPos pos, String flavor) {
        if (!(level.getBlockEntity(pos) instanceof Container box) || !box.isEmpty()) return;
        E[] table = switch (flavor) {
            case "village" -> VILLAGE;
            case "skeleton" -> SKELETON;
            case "creeper" -> CREEPER;
            default -> ZOMBIE;
        };
        RandomSource r = level.getRandom();
        int stacks = 3 + r.nextInt(4), size = box.getContainerSize();
        for (int i = 0; i < stacks * 3 && stacks > 0; i++) {
            E e = table[r.nextInt(table.length)];
            if (r.nextFloat() > e.chance()) continue;
            int slot = r.nextInt(size);
            if (!box.getItem(slot).isEmpty()) continue;
            box.setItem(slot, new ItemStack(e.item(), e.min() + r.nextInt(e.max() - e.min() + 1)));
            stacks--;
        }
        box.setChanged();
    }
}
