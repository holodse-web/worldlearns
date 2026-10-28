package com.rrm.mobslearn.mob;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Designs {
    private Designs() {
    }

    public interface B {
        void put(int x, int y, int z, BlockState s);

        void ground(int x, int z, BlockState s);

        void clear(int x, int y, int z);

        void remove(int x, int y, int z);

        boolean has(int x, int y, int z);
    }

    public record Wood(BlockState log, BlockState planks, Block stairs, BlockState slab, Block door, BlockState fence, Block gate, Block trapdoor) {
    }

    public static final Wood[] WOODS = {
        new Wood(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_STAIRS, Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_DOOR, Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_FENCE_GATE, Blocks.OAK_TRAPDOOR),
        new Wood(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_FENCE_GATE, Blocks.SPRUCE_TRAPDOOR),
        new Wood(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB.defaultBlockState(), Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE.defaultBlockState(), Blocks.BIRCH_FENCE_GATE, Blocks.BIRCH_TRAPDOOR),
        new Wood(Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB.defaultBlockState(), Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_FENCE.defaultBlockState(), Blocks.DARK_OAK_FENCE_GATE, Blocks.DARK_OAK_TRAPDOOR),
        new Wood(Blocks.ACACIA_LOG.defaultBlockState(), Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB.defaultBlockState(), Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE.defaultBlockState(), Blocks.ACACIA_FENCE_GATE, Blocks.ACACIA_TRAPDOOR),
        new Wood(Blocks.JUNGLE_LOG.defaultBlockState(), Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB.defaultBlockState(), Blocks.JUNGLE_DOOR, Blocks.JUNGLE_FENCE.defaultBlockState(), Blocks.JUNGLE_FENCE_GATE, Blocks.JUNGLE_TRAPDOOR),
        new Wood(Blocks.CHERRY_LOG.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB.defaultBlockState(), Blocks.CHERRY_DOOR, Blocks.CHERRY_FENCE.defaultBlockState(), Blocks.CHERRY_FENCE_GATE, Blocks.CHERRY_TRAPDOOR),
        new Wood(Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB.defaultBlockState(), Blocks.MANGROVE_DOOR, Blocks.MANGROVE_FENCE.defaultBlockState(), Blocks.MANGROVE_FENCE_GATE, Blocks.MANGROVE_TRAPDOOR)};

    public static final Block[] LOGS = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.DARK_OAK_LOG, Blocks.ACACIA_LOG, Blocks.JUNGLE_LOG, Blocks.CHERRY_LOG, Blocks.MANGROVE_LOG};

    public static Wood wood(int style) {
        return WOODS[Math.floorMod(style, WOODS.length)];
    }

    public static Wood roofWood(int style) {
        return r4(style / 7 + 1);
    }

    static Wood r4(int k) {
        return WOODS[Math.floorMod(k, 4)];
    }

    static BlockState stairs(Block b, Direction facing) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, facing);
    }

    static void door(B p, int x, int z, Block door) {
        BlockState d = door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH);
        p.put(x, 0, z, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        p.put(x, 1, z, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        for (int y = 0; y <= 2; y++) {
            p.clear(x, y, z - 1);
            p.clear(x, y, z - 2);
        }
    }

    static void bed(B p, int x, int z, int color, Direction facing) {
        Block b = switch (Math.floorMod(color, 6)) {
            case 0 -> Blocks.BED.red();
            case 1 -> Blocks.BED.blue();
            case 2 -> Blocks.BED.green();
            case 3 -> Blocks.BED.yellow();
            case 4 -> Blocks.BED.lightBlue();
            default -> Blocks.BED.brown();
        };
        BlockState bed = b.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
        p.put(x, 0, z, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        p.put(x + facing.getStepX(), 0, z + facing.getStepZ(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    static void walls(B p, int hx, int zMin, int zMax, int y0, int y1, BlockState corner, BlockState wall, BlockState base) {
        for (int y = y0; y <= y1; y++) {
            for (int x = -hx; x <= hx; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (Math.abs(x) != hx && z != zMin && z != zMax) continue;
                    boolean c = Math.abs(x) == hx && (z == zMin || z == zMax);
                    p.put(x, y, z, c ? corner : (y == y0 && base != null) ? base : wall);
                }
            }
        }
    }

    static void floor(B p, int hx, int zMin, int zMax, BlockState s) {
        for (int x = -hx + 1; x <= hx - 1; x++) for (int z = zMin + 1; z <= zMax - 1; z++) p.ground(x, z, s);
    }

    static void ceiling(B p, int hx, int zMin, int zMax, int y, BlockState s) {
        for (int x = -hx + 1; x <= hx - 1; x++) for (int z = zMin + 1; z <= zMax - 1; z++) p.put(x, y, z, s);
    }

    static void gableZ(B p, int hx, int zMin, int zMax, int base, Block stairs, BlockState slab, BlockState gable, int over) {
        for (int k = 0; ; k++) {
            int left = -(hx + 1) + k, right = (hx + 1) - k, y = base + k;
            if (left > right) break;
            for (int z = zMin - over; z <= zMax + over; z++) {
                if (left == right) p.put(left, y, z, slab);
                else {
                    p.put(left, y, z, stairs(stairs, Direction.EAST));
                    p.put(right, y, z, stairs(stairs, Direction.WEST));
                }
            }
            for (int x = left + 1; x <= right - 1; x++) {
                p.put(x, y, zMin, gable);
                p.put(x, y, zMax, gable);
            }
            if (left == right) break;
        }
    }

    static void gableX(B p, int hx, int zMin, int zMax, int base, Block stairs, BlockState slab, BlockState gable, int over) {
        for (int k = 0; ; k++) {
            int front = zMin - 1 + k, back = zMax + 1 - k, y = base + k;
            if (front > back) break;
            for (int x = -hx - over; x <= hx + over; x++) {
                if (front == back) p.put(x, y, front, slab);
                else {
                    p.put(x, y, front, stairs(stairs, Direction.SOUTH));
                    p.put(x, y, back, stairs(stairs, Direction.NORTH));
                }
            }
            for (int z = front + 1; z <= back - 1; z++) {
                p.put(-hx, y, z, gable);
                p.put(hx, y, z, gable);
            }
            if (front == back) break;
        }
    }

    static void pyramid(B p, int hx, int zMin, int zMax, int base, Block stairs, BlockState top) {
        int x0 = -hx - 1, x1 = hx + 1, z0 = zMin - 1, z1 = zMax + 1;
        int y = base;
        while (x0 <= x1 && z0 <= z1) {
            if (x0 == x1 || z0 == z1) {
                for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) p.put(x, y, z, top);
                break;
            }
            for (int x = x0; x <= x1; x++) {
                p.put(x, y, z0, stairs(stairs, Direction.SOUTH));
                p.put(x, y, z1, stairs(stairs, Direction.NORTH));
            }
            for (int z = z0 + 1; z <= z1 - 1; z++) {
                p.put(x0, y, z, stairs(stairs, Direction.EAST));
                p.put(x1, y, z, stairs(stairs, Direction.WEST));
            }
            x0++;
            x1--;
            z0++;
            z1--;
            y++;
        }
    }

    static void chimney(B p, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) p.put(x, y, z, Blocks.COBBLESTONE.defaultBlockState());
        p.put(x, y1 + 1, z, Blocks.CAMPFIRE.defaultBlockState());
    }

    static void windows(B p, int hx, int zMin, int zMax, int y) {
        BlockState g = Blocks.GLASS_PANE.defaultBlockState();
        for (int z = zMin + 1; z <= zMax - 1; z++) {
            if ((z - zMin) % 2 == 0 || zMax - zMin <= 3) {
                p.put(hx, y, z, g);
                p.put(-hx, y, z, g);
            }
        }
        if (hx >= 2) {
            p.put(-1, y, zMax, g);
            p.put(1, y, zMax, g);
        } else {
            p.put(0, y, zMax, g);
        }
    }

    static BlockState furniture(RandomSource r) {
        Block[] f = {Blocks.CRAFTING_TABLE, Blocks.SMOKER, Blocks.FURNACE, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.COMPOSTER,
            Blocks.BOOKSHELF, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.BREWING_STAND, Blocks.LECTERN, Blocks.CAULDRON, Blocks.JUKEBOX, Blocks.BOOKSHELF,
            Blocks.CRAFTING_TABLE};
        return f[r.nextInt(f.length)].defaultBlockState();
    }

    static BlockState storage(RandomSource r) {
        return r.nextInt(3) == 0 ? Blocks.CHEST.defaultBlockState() : Blocks.BARREL.defaultBlockState();
    }

    static BlockState plant(RandomSource r) {
        Block[] f = {Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_FERN, Blocks.POTTED_CACTUS, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_RED_TULIP,
            Blocks.POTTED_AZALEA, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_LILY_OF_THE_VALLEY};
        return f[r.nextInt(f.length)].defaultBlockState();
    }

    static BlockState light(RandomSource r) {
        int k = r.nextInt(5);
        return k == 0 ? Blocks.SOUL_LANTERN.defaultBlockState() : k == 1 ? plant(r) : Blocks.LANTERN.defaultBlockState();
    }

    static Block carpet(int color) {
        return switch (Math.floorMod(color, 8)) {
            case 0 -> Blocks.CARPET.red();
            case 1 -> Blocks.CARPET.blue();
            case 2 -> Blocks.CARPET.green();
            case 3 -> Blocks.CARPET.yellow();
            case 4 -> Blocks.CARPET.lightBlue();
            case 5 -> Blocks.CARPET.brown();
            case 6 -> Blocks.CARPET.white();
            default -> Blocks.CARPET.orange();
        };
    }

    static void interior(B p, int hx, int zMin, int zMax, int y, RandomSource r) {
        int c1 = r.nextInt(8), c2 = r.nextInt(8);
        float rug = 0.15f + r.nextFloat() * 0.45f;
        int extra = r.nextInt(3);
        for (int i = 0; i < 12 && extra > 0; i++) {
            int x = -hx + 1 + r.nextInt(Math.max(1, hx * 2 - 1)), z = zMin + 1 + r.nextInt(Math.max(1, zMax - zMin - 1));
            if (x == 0 || p.has(x, y, z) || z == zMin + 1) continue;
            p.put(x, y, z, r.nextBoolean() ? furniture(r) : plant(r));
            extra--;
        }
        for (int x = -hx + 1; x <= hx - 1; x++) {
            for (int z = zMin + 1; z <= zMax - 1; z++) {
                if (p.has(x, y, z) || r.nextFloat() > rug) continue;
                p.put(x, y, z, carpet((x + z) % 2 == 0 ? c1 : c2).defaultBlockState());
            }
        }
    }

    public static void villagerHome(B p, int lvl, int style, RandomSource r) {
        Wood w = r.nextFloat() < 0.7f ? wood(style) : WOODS[r.nextInt(4)];
        Wood roof = r.nextFloat() < 0.5f ? w : roofWood(style);
        int design = Math.floorMod(style * 3 + r.nextInt(5), 5);
        if (lvl <= 1 && design == 3) design = 1;
        BlockState cobble = r.nextFloat() < 0.3f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        int color = r.nextInt(6);
        Direction bedFace = r.nextBoolean() ? Direction.SOUTH : Direction.NORTH;
        switch (design) {
            case 0 -> {
                int hz = lvl >= 2 ? 3 : 2;
                walls(p, 2, -hz, hz, 0, 2, w.log(), w.planks(), lvl >= 2 ? cobble : null);
                door(p, 0, -hz, w.door());
                windows(p, 2, -hz, hz, 1);
                floor(p, 2, -hz, hz, w.planks());
                ceiling(p, 2, -hz, hz, 3, w.planks());
                gableZ(p, 2, -hz, hz, 3, roof.stairs(), roof.slab(), w.planks(), lvl >= 2 ? 0 : 1);
                int bx = r.nextBoolean() ? 1 : -1;
                bed(p, bx, hz - 2, color, Direction.SOUTH);
                p.put(-bx, 0, hz - 1, furniture(r));
                p.put(-bx, 0, -hz + 1, light(r));
                if (lvl >= 2) {
                    p.put(-bx, 0, 0, storage(r));
                    chimney(p, -1, hz, 3, 6);
                }
                interior(p, 2, -hz, hz, 0, r);
            }
            case 1 -> {
                walls(p, 2, -2, 2, 0, 2, w.log(), lvl >= 2 ? w.planks() : cobble, cobble);
                door(p, 0, -2, w.door());
                p.put(2, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
                p.put(-2, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
                p.put(0, 1, 2, Blocks.GLASS_PANE.defaultBlockState());
                floor(p, 2, -2, 2, r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : w.planks());
                pyramid(p, 2, -2, 2, 3, roof.stairs(), roof.slab());
                int bx = r.nextBoolean() ? 1 : -1;
                bed(p, bx, 0, color, bedFace);
                p.put(-bx, 0, 1, furniture(r));
                p.put(-bx, 0, -1, light(r));
                p.put(-3, 0, -3, plant(r));
                interior(p, 2, -2, 2, 0, r);
            }
            case 2 -> {
                walls(p, 3, -2, 2, 0, 2, w.log(), w.planks(), lvl >= 2 ? cobble : null);
                door(p, 0, -2, w.door());
                p.put(-2, 1, -2, Blocks.GLASS_PANE.defaultBlockState());
                p.put(2, 1, -2, Blocks.GLASS_PANE.defaultBlockState());
                p.put(-1, 1, 2, Blocks.GLASS_PANE.defaultBlockState());
                p.put(1, 1, 2, Blocks.GLASS_PANE.defaultBlockState());
                p.put(3, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
                floor(p, 3, -2, 2, w.planks());
                ceiling(p, 3, -2, 2, 3, w.planks());
                gableX(p, 3, -2, 2, 3, roof.stairs(), roof.slab(), w.planks(), 0);
                bed(p, -2, 0, color, bedFace);
                if (r.nextBoolean()) bed(p, 2, 0, color + 1, bedFace);
                else {
                    p.put(2, 0, 1, storage(r));
                    p.put(2, 0, -1, furniture(r));
                }
                p.put(0, 0, 1, furniture(r));
                p.put(-1, 0, -1, light(r));
                if (lvl >= 2) p.put(1, 0, 1, storage(r));
                interior(p, 3, -2, 2, 0, r);
            }
            case 3 -> {
                walls(p, 2, -2, 2, 0, 5, w.log(), w.planks(), cobble);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 3, z, w.planks());
                door(p, 0, -2, w.door());
                p.put(2, 1, 0, Blocks.GLASS_PANE.defaultBlockState());
                p.put(-2, 4, 0, Blocks.GLASS_PANE.defaultBlockState());
                p.put(2, 4, 0, Blocks.GLASS_PANE.defaultBlockState());
                p.put(0, 4, 2, Blocks.GLASS_PANE.defaultBlockState());
                p.put(0, 4, -2, Blocks.GLASS_PANE.defaultBlockState());
                floor(p, 2, -2, 2, w.planks());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
                for (int y = 0; y <= 3; y++) p.put(1, y, 1, ladder);
                pyramid(p, 2, -2, 2, 6, roof.stairs(), roof.slab());
                p.put(-1, 0, 1, furniture(r));
                p.put(1, 0, -1, light(r));
                bed(p, -1, -1, color, Direction.SOUTH);
                p.put(1, 4, -1, light(r));
                p.put(-1, 4, 1, r.nextBoolean() ? Blocks.BOOKSHELF.defaultBlockState() : storage(r));
                p.put(-1, 4, -1, furniture(r));
                p.clear(1, 4, 1);
                interior(p, 2, -2, 2, 4, r);
            }
            default -> {
                walls(p, 2, -1, 3, 0, 2, w.log(), w.planks(), lvl >= 2 ? cobble : null);
                door(p, 0, -1, w.door());
                p.put(2, 1, 1, Blocks.GLASS_PANE.defaultBlockState());
                p.put(-2, 1, 1, Blocks.GLASS_PANE.defaultBlockState());
                p.put(0, 1, 3, Blocks.GLASS_PANE.defaultBlockState());
                floor(p, 2, -1, 3, w.planks());
                ceiling(p, 2, -1, 3, 3, w.planks());
                gableZ(p, 2, -1, 3, 3, roof.stairs(), roof.slab(), w.planks(), 0);
                for (int x = -2; x <= 2; x += 4) {
                    p.put(x, 0, -3, w.fence());
                    p.put(x, 1, -3, w.fence());
                }
                for (int x = -2; x <= 2; x++) p.put(x, 2, -3, w.slab());
                for (int x = -2; x <= 2; x++) p.put(x, 2, -2, w.slab());
                p.remove(0, 2, -2);
                p.put(0, 2, -2, w.planks());
                p.ground(-1, -2, w.planks());
                p.ground(1, -2, w.planks());
                p.ground(0, -2, w.planks());
                int bx = r.nextBoolean() ? 1 : -1;
                bed(p, bx, 1, color, Direction.SOUTH);
                p.put(-bx, 0, 2, furniture(r));
                p.put(-bx, 0, 0, light(r));
                p.put(-1, 0, -2, r.nextBoolean() ? storage(r) : stairs(w.stairs(), Direction.NORTH));
                for (int y = 0; y <= 1; y++) for (int z = -3; z <= -2; z++) p.clear(0, y, z);
                interior(p, 2, -1, 3, 0, r);
            }
        }
        if (r.nextBoolean()) p.put(r.nextBoolean() ? 3 : -3, 0, -3, r.nextBoolean() ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
    }

    public static void special(B p, int type, int style, RandomSource r) {
        Wood w = r.nextFloat() < 0.7f ? wood(style) : WOODS[r.nextInt(4)];
        Wood roof = roofWood(style);
        switch (type) {
            case 1 -> {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && z == 0) {
                            p.ground(0, 0, Blocks.WATER.defaultBlockState());
                            p.clear(0, 0, 0);
                            continue;
                        }
                        p.put(x, 0, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
                    }
                }
                for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                    p.put(c[0], 1, c[1], w.fence());
                    p.put(c[0], 2, c[1], w.fence());
                }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 3, z, roof.slab());
                p.put(0, 2, 0, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) == 2 || Math.abs(z) == 2) p.ground(x, z, Blocks.DIRT_PATH.defaultBlockState());
                p.put(2, 0, 2, Blocks.BARREL.defaultBlockState());
            }
            case 2 -> {
                int v = r.nextInt(2);
                for (int[] c : new int[][]{{-2, -1}, {2, -1}, {-2, 1}, {2, 1}}) {
                    p.put(c[0], 0, c[1], w.fence());
                    p.put(c[0], 1, c[1], w.fence());
                }
                if (v == 0) {
                    Block[] wool = {Blocks.WOOL.red(), Blocks.WOOL.yellow(), Blocks.WOOL.white(), Blocks.WOOL.blue(), Blocks.WOOL.green()};
                    for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) p.put(x, 2, z, wool[Math.floorMod(x + (style & 3), wool.length)].defaultBlockState());
                } else {
                    for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) p.put(x, 2, z, w.slab());
                }
                p.put(-1, 0, 0, Blocks.BARREL.defaultBlockState());
                p.put(0, 0, 0, Blocks.HAY_BLOCK.defaultBlockState());
                p.put(1, 0, 0, r.nextBoolean() ? Blocks.MELON.defaultBlockState() : Blocks.PUMPKIN.defaultBlockState());
                p.put(1, 0, 1, r.nextBoolean() ? Blocks.PUMPKIN.defaultBlockState() : Blocks.BEEHIVE.defaultBlockState());
                p.put(-1, 0, 1, Blocks.COMPOSTER.defaultBlockState());
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= -2; z++) p.ground(x, z, Blocks.DIRT_PATH.defaultBlockState());
            }
            case 3 -> {
                walls(p, 2, -2, 2, 0, 2, w.log(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
                for (int x = -1; x <= 1; x++) {
                    p.remove(x, 0, -2);
                    p.remove(x, 1, -2);
                    p.clear(x, 0, -2);
                    p.clear(x, 1, -2);
                }
                floor(p, 2, -2, 2, Blocks.STONE_BRICKS.defaultBlockState());
                ceiling(p, 2, -2, 2, 3, Blocks.STONE_BRICKS.defaultBlockState());
                pyramid(p, 2, -2, 2, 3, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB.defaultBlockState());
                p.put(-1, 0, 1, Blocks.FURNACE.defaultBlockState());
                p.put(0, 0, 1, Blocks.BLAST_FURNACE.defaultBlockState());
                p.put(1, 0, 1, Blocks.SMITHING_TABLE.defaultBlockState());
                p.put(1, 0, 0, Blocks.ANVIL.defaultBlockState());
                p.put(-1, 0, 0, Blocks.CHEST.defaultBlockState());
                p.put(-1, 0, -1, Blocks.LANTERN.defaultBlockState());
                chimney(p, -2, 2, 3, 6);
            }
            case 4 -> {
                walls(p, 2, -3, 3, 0, 3, w.log(), w.planks(), Blocks.COBBLESTONE.defaultBlockState());
                door(p, 0, -3, w.door());
                for (int z = -2; z <= 2; z++) {
                    p.put(-1, 0, z, Blocks.BOOKSHELF.defaultBlockState());
                    p.put(-1, 1, z, Blocks.BOOKSHELF.defaultBlockState());
                }
                p.put(1, 0, 0, Blocks.LECTERN.defaultBlockState());
                p.put(1, 0, 2, Blocks.LANTERN.defaultBlockState());
                p.put(2, 2, -1, Blocks.GLASS_PANE.defaultBlockState());
                p.put(2, 2, 1, Blocks.GLASS_PANE.defaultBlockState());
                p.put(-2, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
                floor(p, 2, -3, 3, w.planks());
                ceiling(p, 2, -3, 3, 4, w.planks());
                gableZ(p, 2, -3, 3, 4, roof.stairs(), roof.slab(), w.planks(), 0);
                interior(p, 2, -3, 3, 0, r);
            }
            case 6 -> {
                net.minecraft.core.Direction.Axis[] axes = {net.minecraft.core.Direction.Axis.X, net.minecraft.core.Direction.Axis.Z, net.minecraft.core.Direction.Axis.Y};
                int[][] base = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {2, 0}, {-2, 1}, {1, -1}};
                int n = 6 + r.nextInt(5);
                for (int i = 0; i < n; i++) {
                    p.put(base[i][0], 0, base[i][1], Blocks.HAY_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axes[r.nextInt(3)]));
                }
                int up = 2 + r.nextInt(3);
                for (int i = 0; i < up; i++) {
                    p.put(base[i][0], 1, base[i][1], Blocks.HAY_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axes[r.nextInt(3)]));
                }
                if (r.nextBoolean()) p.put(0, 2, 0, Blocks.HAY_BLOCK.defaultBlockState());
                if (r.nextBoolean()) p.put(-2, 0, -1, Blocks.COMPOSTER.defaultBlockState());
            }
            case 7 -> {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        if (Math.abs(x) != 3 && Math.abs(z) != 3) continue;
                        p.put(x, 0, z, w.fence());
                    }
                }
                p.put(0, 0, -3, w.gate().defaultBlockState());
                for (int y = 0; y <= 2; y++) p.clear(0, y, -4);
                p.put(-3, 1, -3, Blocks.LANTERN.defaultBlockState());
                p.put(3, 1, 3, Blocks.LANTERN.defaultBlockState());
                p.put(1, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
                p.put(-1, 0, 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
                if (r.nextBoolean()) p.put(1, 0, -1, Blocks.COMPOSTER.defaultBlockState());
            }
            default -> {
                int v = r.nextInt(3);
                BlockState path = Blocks.DIRT_PATH.defaultBlockState();
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        int m = Math.max(Math.abs(x), Math.abs(z));
                        if (m == 3 || (x == 0 && z <= -3)) p.ground(x, z, path);
                        else p.ground(x, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                }
                Block leaf = v == 0 ? Blocks.OAK_LEAVES : v == 1 ? Blocks.BIRCH_LEAVES : Blocks.CHERRY_LEAVES;
                BlockState trunk = v == 0 ? Blocks.OAK_LOG.defaultBlockState() : v == 1 ? Blocks.BIRCH_LOG.defaultBlockState() : Blocks.CHERRY_LOG.defaultBlockState();
                for (int y = 0; y <= 3; y++) p.put(0, y, 0, trunk);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) + Math.abs(z) <= 3 && !(x == 0 && z == 0)) p.put(x, 3, z, leaves(leaf));
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 4, z, leaves(leaf));
                p.put(0, 5, 0, leaves(leaf));
                Block[] f = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY,
                    Blocks.RED_TULIP, Blocks.PINK_TULIP, Blocks.BLUE_ORCHID};
                for (int[] k : new int[][]{{-2, -1}, {2, 1}, {1, 2}, {-1, -2}, {-2, 2}, {2, -2}, {-1, 2}, {2, -1}}) if (r.nextFloat() < 0.75f) p.put(k[0], 0, k[1], f[r.nextInt(f.length)].defaultBlockState());
                p.put(4, 0, 0, stairs(w.stairs(), Direction.EAST));
                p.put(-4, 0, 0, stairs(w.stairs(), Direction.WEST));
                p.put(0, 0, 4, stairs(w.stairs(), Direction.SOUTH));
                for (int[] k : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
                    p.put(k[0], 0, k[1], w.fence());
                    p.put(k[0], 1, k[1], Blocks.LANTERN.defaultBlockState());
                }
                if (r.nextBoolean()) {
                    p.ground(-4, 2, Blocks.WATER.defaultBlockState());
                    p.ground(-4, 3, Blocks.WATER.defaultBlockState());
                    p.put(-4, 0, 2, Blocks.LILY_PAD.defaultBlockState());
                } else {
                    p.put(-4, 0, 2, Blocks.BEEHIVE.defaultBlockState());
                    p.put(-4, 0, 3, Blocks.ROSE_BUSH.defaultBlockState());
                }
            }
        }
    }

    public static void zombieHome(B p, int lvl, int style, RandomSource r) {
        Wood zw = r.nextFloat() < 0.6f ? wood(style) : darkWood(style + r.nextInt(2));
        Stone zs = r.nextFloat() < 0.6f ? stone(style) : STONES[r.nextInt(STONES.length)];
        BlockState log = zw.log(), planks = zw.planks();
        int v = Math.floorMod(style + r.nextInt(4), 4);
        if (lvl <= 1) {
            switch (v) {
                case 0 -> {
                    for (int z = -2; z <= 2; z++) {
                        p.put(-2, 0, z, stairs(zw.stairs(), Direction.EAST));
                        p.put(2, 0, z, stairs(zw.stairs(), Direction.WEST));
                        p.put(-1, 1, z, stairs(zw.stairs(), Direction.EAST));
                        p.put(1, 1, z, stairs(zw.stairs(), Direction.WEST));
                        p.put(0, 2, z, zw.slab());
                    }
                    for (int x = -1; x <= 1; x++) p.put(x, 0, 2, r.nextBoolean() ? zs.alt() : zs.main());
                    p.put(0, 1, 2, zs.alt());
                    p.put(0, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.COBWEB.defaultBlockState());
                }
                case 1 -> {
                    for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) p.put(x, y, 2, log);
                    for (int x = -2; x <= 2; x++) {
                        p.put(x, 2, 1, zw.slab());
                        p.put(x, 2, 0, zw.slab());
                        p.put(x, 1, -1, stairs(zw.stairs(), Direction.SOUTH));
                    }
                    p.put(-2, 0, -1, zw.fence());
                    p.put(2, 0, -1, zw.fence());
                    p.put(1, 0, 1, Blocks.BARREL.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.COBWEB.defaultBlockState());
                }
                case 2 -> {
                    corners(p, 2, 0, 1, r.nextBoolean() ? zw.fence() : log);
                    fill(p, 2, 2, zw::slab);
                    for (int x = -1; x <= 1; x++) p.put(x, 0, 2, zs.main());
                    p.put(1, 0, 1, Blocks.BARREL.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
                }
                default -> {
                    roundRing(p, 2, 0, () -> r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
                    roundRing(p, 2, 1, () -> Blocks.DIRT.defaultBlockState());
                    fill(p, 1, 2, () -> Blocks.GRASS_BLOCK.defaultBlockState());
                    p.put(0, 2, 2, Blocks.GRASS_BLOCK.defaultBlockState());
                    p.put(0, 3, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
                    p.put(1, 0, 1, Blocks.COBWEB.defaultBlockState());
                }
            }
            p.put(2, 0, -3, Blocks.CAMPFIRE.defaultBlockState());
            gap(p, 0, -2, 2);
            return;
        }
        if (lvl == 2) {
            switch (v) {
                case 0 -> {
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                            p.put(x, 0, z, r.nextFloat() < 0.5f ? zs.alt() : zs.main());
                            p.put(x, 1, z, r.nextFloat() < 0.6f ? zs.alt() : Blocks.COARSE_DIRT.defaultBlockState());
                        }
                    }
                    fill(p, 2, 2, () -> Blocks.GRASS_BLOCK.defaultBlockState());
                    p.put(0, 3, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
                    p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.COMPOSTER.defaultBlockState());
                    p.put(1, 0, 1, Blocks.SOUL_LANTERN.defaultBlockState());
                }
                case 1 -> {
                    walls(p, 2, -2, 2, 0, 2, log, planks, zs.main());
                    fill(p, 2, 3, zw::slab);
                    p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.BARREL.defaultBlockState());
                    p.put(1, 0, 1, Blocks.COBWEB.defaultBlockState());
                    p.put(1, 0, -1, Blocks.SOUL_LANTERN.defaultBlockState());
                }
                case 2 -> {
                    for (int y = 0; y <= 1; y++) roundRing(p, 2, y, () -> r.nextFloat() < 0.5f ? zs.alt() : Blocks.COARSE_DIRT.defaultBlockState());
                    pyramid(p, 2, -2, 2, 2, zw.stairs(), zw.slab());
                    p.put(0, 0, 0, Blocks.HAY_BLOCK.defaultBlockState());
                    p.put(1, 0, 1, Blocks.BARREL.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.SOUL_LANTERN.defaultBlockState());
                }
                default -> {
                    walls(p, 2, -2, 2, 0, 2, log, log, zs.alt());
                    gableZ(p, 2, -2, 2, 3, zw.stairs(), zw.slab(), planks, 0);
                    ceiling(p, 2, -2, 2, 3, planks);
                    p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-1, 0, 1, Blocks.COMPOSTER.defaultBlockState());
                    p.put(1, 0, 1, Blocks.BARREL.defaultBlockState());
                    p.put(1, 0, -1, Blocks.COBWEB.defaultBlockState());
                }
            }
            floor(p, 2, -2, 2, Blocks.COARSE_DIRT.defaultBlockState());
            gap(p, 0, -2, 2);
            return;
        }
        switch (v) {
            case 0 -> {
                for (int y = 0; y <= 2; y++) ring(p, 2, y, mix(zs, r, 0.4f));
                p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                pyramid(p, 2, -2, 2, 3, zs.stairs(), zs.slab());
                p.put(-1, 0, 1, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.EAST));
                p.put(1, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
                p.put(1, 0, -1, Blocks.SOUL_LANTERN.defaultBlockState());
                p.put(-3, 0, -3, zw.fence());
                p.put(-3, 1, -3, Blocks.ZOMBIE_HEAD.defaultBlockState());
            }
            case 1 -> {
                for (int y = 0; y <= 2; y++) {
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                            boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                            p.put(x, y, z, corner ? log : y <= 1 ? (r.nextFloat() < 0.4f ? zs.alt() : zs.main()) : planks);
                        }
                    }
                }
                p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(0, 1, 2, Blocks.IRON_BARS.defaultBlockState());
                ceiling(p, 2, -2, 2, 3, planks);
                gableZ(p, 2, -2, 2, 3, zw.stairs(), zw.slab(), planks, 0);
                p.put(-1, 0, -1, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.EAST));
                p.put(1, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
                p.put(1, 0, -1, Blocks.SOUL_LANTERN.defaultBlockState());
            }
            case 2 -> {
                for (int y = 0; y <= 4; y++) ring(p, 2, y, y <= 1 ? mix(zs, r, 0.4f) : () -> planks);
                corners(p, 2, 0, 4, log);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!(x == 1 && z == 1)) p.put(x, 2, z, planks);
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
                for (int y = 0; y <= 2; y++) p.put(1, y, 1, ladder);
                fill(p, 2, 5, zs::slab);
                for (int x = -2; x <= 2; x += 2) p.put(x, 3, -2, Blocks.IRON_BARS.defaultBlockState());
                p.put(2, 3, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-1, 0, 1, Blocks.BARREL.defaultBlockState());
                p.put(-1, 3, 1, Blocks.SOUL_LANTERN.defaultBlockState());
                p.put(0, 6, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
            }
            default -> {
                for (int y = 0; y <= 1; y++) ring(p, 3, y, mix(zs, r, 0.5f));
                fill(p, 3, 2, zs::slab);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 2, z, Blocks.GRASS_BLOCK.defaultBlockState());
                chimney(p, 2, 2, 2, 3);
                p.put(-2, 0, 2, Blocks.SOUL_LANTERN.defaultBlockState());
                p.put(2, 0, -1, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
                p.put(-2, 0, -1, Blocks.COBWEB.defaultBlockState());
                p.put(3, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                floor(p, 3, -3, 3, Blocks.COARSE_DIRT.defaultBlockState());
                gap(p, 0, -3, 2);
                return;
            }
        }
        floor(p, 2, -2, 2, Blocks.COARSE_DIRT.defaultBlockState());
        gap(p, 0, -2, 2);
    }

    static void ring(B p, int radius, int y, java.util.function.Supplier<BlockState> s) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) if (Math.abs(x) == radius || Math.abs(z) == radius) p.put(x, y, z, s.get());
    }

    static void roundRing(B p, int radius, int y, java.util.function.Supplier<BlockState> s) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean edge = Math.abs(x) == radius || Math.abs(z) == radius;
                if (edge && !(Math.abs(x) == radius && Math.abs(z) == radius)) p.put(x, y, z, s.get());
            }
        }
        for (int[] k : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) p.put(k[0] * (radius - 1), y, k[1] * (radius - 1), s.get());
    }

    static void fill(B p, int radius, int y, java.util.function.Supplier<BlockState> s) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) p.put(x, y, z, s.get());
    }

    static void corners(B p, int radius, int y0, int y1, BlockState s) {
        for (int y = y0; y <= y1; y++) for (int[] k : new int[][]{{radius, radius}, {-radius, radius}, {radius, -radius}, {-radius, -radius}}) p.put(k[0], y, k[1], s);
    }

    static void gap(B p, int x, int z, int h) {
        for (int y = 0; y < h; y++) {
            p.remove(x, y, z);
            p.clear(x, y, z);
        }
        for (int y = 0; y <= 2; y++) p.clear(x, y, z - 1);
    }

    static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    record Stone(BlockState main, BlockState alt, Block stairs, BlockState slab, BlockState wall) {
    }

    static final Stone[] STONES = {
        new Stone(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState()),
        new Stone(Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState()),
        new Stone(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState()),
        new Stone(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState()),
        new Stone(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(), Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB.defaultBlockState(), Blocks.MUD_BRICK_WALL.defaultBlockState())};

    static Stone stone(int style) {
        return STONES[Math.floorMod(style / 3, STONES.length)];
    }

    static java.util.function.Supplier<BlockState> mix(Stone st, RandomSource r, float alt) {
        return () -> r.nextFloat() < alt ? st.alt() : st.main();
    }

    static Wood darkWood(int style) {
        Wood[] dark = {WOODS[1], WOODS[3], WOODS[0]};
        return dark[Math.floorMod(style, dark.length)];
    }

    public static void zombieHall(B p, int lvl, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(3), 3);
        Wood w = r.nextFloat() < 0.6f ? wood(style) : darkWood(style);
        Stone st = stone(style);
        if (lvl <= 1) {
            if (v == 0) {
                for (int y = 0; y <= 1; y++) ring(p, 2, y, () -> r.nextFloat() < 0.5f ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                corners(p, 2, 0, 2, w.log());
                fill(p, 2, 2, () -> Blocks.DIRT.defaultBlockState());
                gap(p, 0, -2, 2);
            } else if (v == 1) {
                for (int z = -2; z <= 2; z++) {
                    p.put(-2, 0, z, stairs(w.stairs(), Direction.EAST));
                    p.put(2, 0, z, stairs(w.stairs(), Direction.WEST));
                    p.put(-1, 1, z, stairs(w.stairs(), Direction.EAST));
                    p.put(1, 1, z, stairs(w.stairs(), Direction.WEST));
                    p.put(0, 2, z, w.slab());
                }
                for (int x = -1; x <= 1; x++) p.put(x, 0, 2, st.alt());
                p.put(0, 1, 2, st.alt());
                p.put(-2, 0, -4, Blocks.CAMPFIRE.defaultBlockState());
                p.put(-1, 0, 1, Blocks.COBWEB.defaultBlockState());
                for (int y = 0; y <= 1; y++) p.clear(0, y, -3);
            } else {
                roundRing(p, 2, 0, () -> r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
                corners(p, 2, 0, 1, w.fence());
                fill(p, 2, 2, () -> w.slab());
                p.put(0, 1, 2, st.alt());
                gap(p, 0, -2, 1);
                p.put(2, 0, -3, Blocks.HAY_BLOCK.defaultBlockState());
            }
        } else if (lvl == 2) {
            if (v == 0) {
                for (int y = 0; y <= 2; y++) ring(p, 3, y, mix(st, r, 0.35f));
                corners(p, 3, 0, 3, w.log());
                fill(p, 3, 3, () -> w.planks());
                p.remove(3, 1, 0);
                p.remove(-3, 1, 0);
                p.remove(0, 1, 3);
                p.put(0, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
                p.put(0, 4, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
                gap(p, 0, -3, 2);
            } else if (v == 1) {
                walls(p, 3, -3, 3, 0, 2, w.log(), w.planks(), st.main());
                p.put(3, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-3, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-1, 1, 3, Blocks.IRON_BARS.defaultBlockState());
                p.put(1, 1, 3, Blocks.IRON_BARS.defaultBlockState());
                ceiling(p, 3, -3, 3, 3, w.planks());
                gableZ(p, 3, -3, 3, 3, w.stairs(), w.slab(), w.planks(), 1);
                floor(p, 3, -3, 3, Blocks.COARSE_DIRT.defaultBlockState());
                p.put(-2, 0, 2, Blocks.CAMPFIRE.defaultBlockState());
                p.put(2, 0, 2, Blocks.COBWEB.defaultBlockState());
                gap(p, 0, -3, 2);
                p.put(-1, 0, -4, w.fence());
                p.put(-1, 1, -4, Blocks.ZOMBIE_HEAD.defaultBlockState());
            } else {
                for (int y = 0; y <= 2; y++) roundRing(p, 3, y, mix(st, r, 0.5f));
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) p.put(x, 3, z, w.slab());
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 4, z, w.slab());
                p.remove(0, 3, 0);
                p.remove(0, 4, 0);
                p.put(0, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
                p.put(3, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                p.put(-3, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                gap(p, 0, -3, 2);
                p.put(-2, 0, 2, Blocks.COBWEB.defaultBlockState());
            }
        } else {
            if (v == 0) {
                for (int y = 0; y <= 7; y++) ring(p, 3, y, mix(st, r, 0.4f));
                fill(p, 2, 4, () -> w.planks());
                fill(p, 3, 8, () -> st.main());
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if ((Math.abs(x) == 3 || Math.abs(z) == 3) && (x + z) % 2 == 0) p.put(x, 9, z, st.wall());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
                for (int y = 0; y <= 8; y++) p.put(-2, y, 0, ladder);
                for (int y : new int[]{2, 6}) {
                    p.remove(3, y, 0);
                    p.remove(0, y, 3);
                    p.remove(0, y, -3);
                }
                p.put(0, 9, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
                p.put(1, 5, 1, Blocks.CAMPFIRE.defaultBlockState());
                gap(p, 0, -3, 2);
            } else if (v == 1) {
                for (int y = 0; y <= 4; y++) ring(p, 4, y, mix(st, r, 0.3f));
                for (int[] k : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
                    for (int y = 0; y <= 6; y++) p.put(k[0], y, k[1], st.main());
                    p.put(k[0], 7, k[1], st.wall());
                }
                fill(p, 3, 5, () -> w.planks());
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if ((Math.abs(x) == 4 || Math.abs(z) == 4) && Math.abs(x) + Math.abs(z) < 8 && (x + z) % 2 == 0) p.put(x, 5, z, st.wall());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
                for (int y = 0; y <= 5; y++) p.put(-3, y, 0, ladder);
                for (int z : new int[]{-2, 2}) {
                    p.put(4, 2, z, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-4, 2, z, Blocks.IRON_BARS.defaultBlockState());
                }
                p.put(2, 0, 2, Blocks.CAMPFIRE.defaultBlockState());
                p.put(-2, 0, 2, Blocks.SOUL_LANTERN.defaultBlockState());
                p.put(0, 6, 0, Blocks.ZOMBIE_HEAD.defaultBlockState());
                gap(p, 0, -4, 3);
                p.put(-2, 0, -5, w.fence());
                p.put(-2, 1, -5, Blocks.ZOMBIE_HEAD.defaultBlockState());
                p.put(2, 0, -5, w.fence());
                p.put(2, 1, -5, Blocks.ZOMBIE_HEAD.defaultBlockState());
            } else {
                walls(p, 3, -4, 4, 0, 3, w.log(), w.planks(), st.main());
                for (int z = -3; z <= 3; z += 2) {
                    p.put(3, 2, z, Blocks.IRON_BARS.defaultBlockState());
                    p.put(-3, 2, z, Blocks.IRON_BARS.defaultBlockState());
                }
                ceiling(p, 3, -4, 4, 4, w.planks());
                gableZ(p, 3, -4, 4, 4, w.stairs(), w.slab(), w.planks(), 1);
                floor(p, 3, -4, 4, st.main());
                p.put(-2, 0, 3, Blocks.CAMPFIRE.defaultBlockState());
                p.put(2, 0, 3, Blocks.SOUL_LANTERN.defaultBlockState());
                p.put(-2, 0, -2, Blocks.COBWEB.defaultBlockState());
                gap(p, 0, -4, 2);
                p.put(-2, 0, -5, w.fence());
                p.put(-2, 1, -5, Blocks.ZOMBIE_HEAD.defaultBlockState());
                p.put(2, 0, -5, w.fence());
                p.put(2, 1, -5, Blocks.ZOMBIE_HEAD.defaultBlockState());
            }
        }
        p.put(1, 0, 1, Blocks.CHEST.defaultBlockState());
    }

    public static void skeletonHall(B p, int lvl, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(3), 3);
        Wood w = WOODS[Math.floorMod(style, WOODS.length)];
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        BlockState pillar = v == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : v == 1 ? w.log() : Blocks.POLISHED_DIORITE.defaultBlockState();
        if (lvl <= 1) {
            for (int y = 0; y <= 3; y++) p.put(0, y, 0, pillar);
            fill(p, 1, 4, () -> w.planks());
            for (int y = 0; y <= 4; y++) p.put(0, y, -1, ladder);
            if (v == 1) for (int[] k : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) p.put(k[0], 5, k[1], w.fence());
            if (v == 2) p.put(1, 5, 1, Blocks.SKELETON_SKULL.defaultBlockState());
        } else if (lvl == 2) {
            for (int y = 0; y <= 5; y++) p.put(0, y, 0, v == 0 ? w.log() : pillar);
            for (int y = 0; y <= 6; y++) p.put(0, y, -1, ladder);
            fill(p, 1, 6, () -> w.planks());
            p.put(0, 6, -1, ladder);
            ring(p, 1, 7, () -> v == 2 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : w.fence());
            p.remove(0, 7, -1);
            p.put(0, 7, 0, Blocks.BONE_BLOCK.defaultBlockState());
            if (v == 1) for (int[] k : new int[][]{{1, 1}, {-1, 1}, {1, -1}}) p.put(k[0], 8, k[1], Blocks.SKELETON_SKULL.defaultBlockState());
        } else {
            Stone st = new Stone[]{STONES[0], STONES[2], STONES[3]}[v];
            for (int y = 0; y <= 7; y++) ring(p, 1, y, mix(st, r, 0.3f));
            p.remove(0, 0, -1);
            p.remove(0, 1, -1);
            for (int y = 0; y <= 8; y++) p.put(0, y, 0, ladder);
            fill(p, 2, 8, () -> st.main());
            p.put(0, 8, 0, ladder);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if ((Math.abs(x) == 2 || Math.abs(z) == 2) && (x + z) % 2 == 0) p.put(x, 9, z, st.wall());
            p.put(2, 10, 2, Blocks.BONE_BLOCK.defaultBlockState());
            if (v >= 1) {
                p.put(-2, 10, -2, Blocks.SKELETON_SKULL.defaultBlockState());
                p.put(1, 4, -2, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
    }

    public static void skeletonHome(B p, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(3), 3);
        if (v == 0) {
            for (int y = 0; y <= 1; y++) ring(p, 2, y, () -> {
                float f = r.nextFloat();
                return f < 0.25f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : f < 0.4f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            });
            corners(p, 2, 0, 2, Blocks.BONE_BLOCK.defaultBlockState());
            fill(p, 2, 2, () -> Blocks.STONE_BRICK_SLAB.defaultBlockState());
            corners(p, 2, 2, 2, Blocks.BONE_BLOCK.defaultBlockState());
            p.put(0, 3, 0, Blocks.SKELETON_SKULL.defaultBlockState());
            p.remove(2, 1, 0);
            p.remove(-2, 1, 0);
        } else if (v == 1) {
            corners(p, 2, 0, 2, Blocks.BONE_BLOCK.defaultBlockState());
            ring(p, 2, 0, () -> Blocks.COBBLESTONE_WALL.defaultBlockState());
            corners(p, 2, 0, 2, Blocks.BONE_BLOCK.defaultBlockState());
            fill(p, 2, 3, () -> Blocks.BIRCH_SLAB.defaultBlockState());
            p.put(0, 4, 0, Blocks.SKELETON_SKULL.defaultBlockState());
            p.put(-1, 0, 1, Blocks.BONE_BLOCK.defaultBlockState());
        } else {
            for (int y = 0; y <= 1; y++) ring(p, 2, y, () -> r.nextFloat() < 0.3f ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
            fill(p, 2, 2, () -> Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            p.put(0, 3, 0, Blocks.SKELETON_SKULL.defaultBlockState());
            p.put(1, 0, 1, Blocks.SOUL_LANTERN.defaultBlockState());
            p.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
        }
        gap(p, 0, -2, 2);
    }

    public static void creeperHall(B p, int lvl, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(3), 3);
        Block leaf = v == 0 ? Blocks.AZALEA_LEAVES : v == 1 ? Blocks.OAK_LEAVES : Blocks.JUNGLE_LEAVES;
        Block bloom = v == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : v == 1 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
        int rad = lvl >= 2 ? 3 : 2;
        int h = lvl >= 3 ? 3 : 2;
        java.util.function.Supplier<BlockState> base = v == 2 ? () -> r.nextFloat() < 0.5f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState()
            : () -> r.nextFloat() < 0.6f ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        for (int y = 0; y < h; y++) {
            if (v == 1) roundRing(p, rad, y, base);
            else ring(p, rad, y, base);
        }
        fill(p, rad, h, () -> leaves(r.nextFloat() < 0.3f ? bloom : leaf));
        if (lvl >= 2) fill(p, rad - 1, h + 1, () -> leaves(leaf));
        if (lvl >= 3) fill(p, rad - 2, h + 2, () -> leaves(bloom));
        p.put(0, h + (lvl >= 3 ? 3 : lvl >= 2 ? 2 : 1), 0, Blocks.CREEPER_HEAD.defaultBlockState());
        gap(p, 0, -rad, 2);
        p.put(-1, 0, 1, Blocks.MOSS_CARPET.defaultBlockState());
        if (v == 2) p.put(-1, 0, -1, Blocks.SPORE_BLOSSOM.defaultBlockState());
        if (lvl >= 2 && v != 0) p.put(rad, 1, 0, Blocks.GLOW_LICHEN.defaultBlockState());
        p.put(1, 0, 1, Blocks.CHEST.defaultBlockState());
    }

    public static void creeperNest(B p, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(3), 3);
        Block leaf = v == 0 ? Blocks.AZALEA_LEAVES : v == 1 ? Blocks.OAK_LEAVES : Blocks.JUNGLE_LEAVES;
        if (v == 2) {
            ring(p, 1, 0, () -> Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            fill(p, 1, 1, () -> leaves(leaf));
            p.put(0, 2, 0, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
        } else {
            ring(p, 1, 0, () -> Blocks.MOSS_BLOCK.defaultBlockState());
            ring(p, 1, 1, () -> leaves(leaf));
            fill(p, 1, 2, () -> leaves(r.nextBoolean() ? Blocks.FLOWERING_AZALEA_LEAVES : leaf));
        }
        gap(p, 0, -1, v == 2 ? 1 : 2);
    }

    public static void villageCenter(B p, int lvl, int style, RandomSource r) {
        int v = Math.floorMod(style + r.nextInt(4), 4);
        Wood w = wood(style);
        BlockState path = Blocks.DIRT_PATH.defaultBlockState();
        BlockState tile = v == 3 ? Blocks.COBBLESTONE.defaultBlockState() : path;
        for (int i = -4; i <= 4; i++) {
            for (int k = -1; k <= 1; k++) {
                p.ground(i, k, path);
                p.ground(k, i, path);
            }
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) == 2 || Math.abs(z) == 2) p.ground(x, z, tile);
        if (lvl >= 2) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if ((Math.abs(x) == 3 || Math.abs(z) == 3) && r.nextFloat() < 0.6f) p.ground(x, z, r.nextBoolean() ? path : Blocks.GRAVEL.defaultBlockState());
        BlockState[][] sets = {
            {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()},
            {Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()},
            {Blocks.RED_TULIP.defaultBlockState(), Blocks.ORANGE_TULIP.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState()}};
        BlockState[] flowers = sets[Math.floorMod(style / 5, sets.length)];
        switch (v) {
            case 0 -> {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    p.ground(x, z, Blocks.WATER.defaultBlockState());
                    p.clear(x, 0, z);
                }
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) == 2 || Math.abs(z) == 2) p.put(x, 0, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                for (int[] k : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) p.put(k[0], 1, k[1], Blocks.LANTERN.defaultBlockState());
            }
            case 1 -> {
                Block leaf = Math.floorMod(style, 3) == 0 ? Blocks.OAK_LEAVES : Math.floorMod(style, 3) == 1 ? Blocks.BIRCH_LEAVES : Blocks.CHERRY_LEAVES;
                BlockState trunk = leaf == Blocks.OAK_LEAVES ? Blocks.OAK_LOG.defaultBlockState() : leaf == Blocks.BIRCH_LEAVES ? Blocks.BIRCH_LOG.defaultBlockState() : Blocks.CHERRY_LOG.defaultBlockState();
                p.ground(0, 0, Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y <= 3; y++) p.put(0, y, 0, trunk);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) + Math.abs(z) <= 3 && !(x == 0 && z == 0)) p.put(x, 3, z, leaves(leaf));
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.put(x, 4, z, leaves(leaf));
                p.put(0, 5, 0, leaves(leaf));
                for (int[] k : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                    p.ground(k[0], k[1], Blocks.GRASS_BLOCK.defaultBlockState());
                    p.put(k[0], 0, k[1], flowers[r.nextInt(flowers.length)]);
                }
            }
            case 2 -> {
                p.put(0, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
                p.put(2, 0, 0, stairs(w.stairs(), Direction.EAST));
                p.put(-2, 0, 0, stairs(w.stairs(), Direction.WEST));
                p.put(0, 0, 2, stairs(w.stairs(), Direction.SOUTH));
                p.put(0, 0, -2, stairs(w.stairs(), Direction.NORTH));
                for (int[] k : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) p.put(k[0], 0, k[1], flowers[r.nextInt(flowers.length)]);
            }
            default -> {
                p.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
                p.put(0, 1, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                p.put(0, 2, 0, Blocks.STONE_BRICK_WALL.defaultBlockState());
                p.put(0, 3, 0, Blocks.LANTERN.defaultBlockState());
                for (int[] k : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) p.put(k[0], 0, k[1], plant(r));
                for (int[] k : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) p.put(k[0], 0, k[1], flowers[r.nextInt(flowers.length)]);
            }
        }
        BlockState post = Math.floorMod(style, 2) == 0 ? Blocks.COBBLESTONE_WALL.defaultBlockState() : w.fence();
        if (lvl >= 2) {
            for (int[] k : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
                p.put(k[0], 0, k[1], post);
                p.put(k[0], 1, k[1], post);
                p.put(k[0], 2, k[1], Blocks.LANTERN.defaultBlockState());
            }
            for (int[] k : new int[][]{{3, 4}, {-4, 3}, {4, -3}, {-3, -4}}) if (r.nextFloat() < 0.7f) p.put(k[0], 0, k[1], flowers[r.nextInt(flowers.length)]);
        }
        if (lvl >= 3) {
            p.put(3, 0, -3, Blocks.BARREL.defaultBlockState());
            p.put(-3, 0, 3, r.nextBoolean() ? Blocks.COMPOSTER.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            p.put(3, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
            p.put(-3, 0, -3, plant(r));
            p.put(4, 0, 2, stairs(w.stairs(), Direction.EAST));
            p.put(-4, 0, -2, stairs(w.stairs(), Direction.WEST));
        }
    }

    public static void path(B p, int tx, int tz) {
        int x = 0, z = 0, dx = Math.abs(tx), dz = Math.abs(tz), sx = Integer.signum(tx), sz = Integer.signum(tz), err = dx - dz;
        int guard = 0;
        boolean alongX = dx >= dz;
        while (guard++ < 200) {
            p.ground(x, z, Blocks.DIRT_PATH.defaultBlockState());
            if (alongX) {
                p.ground(x, z - 1, Blocks.DIRT_PATH.defaultBlockState());
                p.ground(x, z + 1, Blocks.DIRT_PATH.defaultBlockState());
            } else {
                p.ground(x - 1, z, Blocks.DIRT_PATH.defaultBlockState());
                p.ground(x + 1, z, Blocks.DIRT_PATH.defaultBlockState());
            }
            if (x == tx && z == tz) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            } else {
                err += dx;
                z += sz;
            }
        }
    }
}
