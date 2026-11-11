package com.rrm.mobslearn.mob;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Visible signs of a settlement's growth: banners on poles, watchtowers and a village wall. */
public final class Landmarks {
    public static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private Landmarks() {
    }

    private static BlockState block(String id, Block fallback) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        return (b == null || b == Blocks.AIR ? fallback : b).defaultBlockState();
    }

    /** Banner colour per settlement kind (index into COLORS). */
    public static int color(String kind, int i) {
        return switch (kind) {
            case "zombie" -> i % 2 == 0 ? 13 : 7;
            case "skeleton" -> i % 2 == 0 ? 0 : 8;
            case "creeper" -> i % 2 == 0 ? 5 : 15;
            default -> i % 2 == 0 ? 11 : 0;
        };
    }

    public static int material(String kind) {
        return switch (kind) {
            case "zombie" -> 1;
            case "creeper" -> 2;
            case "skeleton" -> 3;
            default -> 0;
        };
    }

    /** A 4-high pole with a coloured banner on top. lvl = colour index. */
    public static void flag(Designs.B p, int lvl) {
        BlockState pole = Blocks.SPRUCE_FENCE.defaultBlockState();
        p.put(0, 0, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
        for (int y = 1; y <= 3; y++) p.put(0, y, 0, pole);
        p.put(0, 4, 0, block(COLORS[Math.floorMod(lvl, COLORS.length)] + "_banner", Blocks.TORCH));
    }

    /** A small lookout tower (5x5 platform at y=7) with a ladder. lvl = material set. */
    public static void watchtower(Designs.B p, int lvl) {
        BlockState post, floor, rail, light;
        switch (lvl) {
            case 1 -> {
                post = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
                rail = Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
                light = Blocks.ZOMBIE_HEAD.defaultBlockState();
            }
            case 2 -> {
                post = Blocks.MOSS_BLOCK.defaultBlockState();
                floor = Blocks.OAK_PLANKS.defaultBlockState();
                rail = Designs.leaves(Blocks.AZALEA_LEAVES);
                light = Blocks.CREEPER_HEAD.defaultBlockState();
            }
            case 3 -> {
                post = Blocks.STONE_BRICKS.defaultBlockState();
                floor = Blocks.BONE_BLOCK.defaultBlockState();
                rail = Blocks.STONE_BRICK_WALL.defaultBlockState();
                light = Blocks.SKELETON_SKULL.defaultBlockState();
            }
            default -> {
                post = Blocks.OAK_LOG.defaultBlockState();
                floor = Blocks.OAK_PLANKS.defaultBlockState();
                rail = Blocks.OAK_FENCE.defaultBlockState();
                light = Blocks.LANTERN.defaultBlockState();
            }
        }
        int top = 7;
        for (int y = 0; y < top; y++) {
            p.put(-1, y, -1, post);
            p.put(1, y, -1, post);
            p.put(-1, y, 1, post);
            p.put(1, y, 1, post);
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y < top; y++) {
            p.put(0, y, -1, y == 0 ? post : floor);
            p.put(0, y, 0, ladder);
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                p.put(x, top, z, floor);
                if (Math.abs(x) == 2 || Math.abs(z) == 2) p.put(x, top + 1, z, rail);
            }
        }
        p.put(0, top, 0, ladder);
        p.put(0, top + 1, 0, Blocks.AIR.defaultBlockState());
        p.put(2, top + 2, 2, light);
        p.put(-2, top + 2, -2, light);
    }
}
