package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.Milestones;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class Builders {
    private static final int MAX_STOCK = 16;
    private static final String DISPLAY = "mobslearn.build_display.";

    private static final class Job {
        boolean pillar, jumping, mining, bridging;
        int baseY, still, mineProgress, mineNeed, dirX, dirZ, phase;
        double lastX, lastZ;
        BlockPos miningAt, bridgeAt;
        BlockState minedState;
        long nextAction, jumpedAt, bridgeStart;
    }

    private static final class Display {
        final Mob mob;
        final EquipmentSlot slot;
        final float drop;
        ItemStack expected = ItemStack.EMPTY;
        Display(Mob mob, EquipmentSlot slot, float drop) { this.mob = mob; this.slot = slot; this.drop = drop; }
    }
    private static final Map<UUID, Job> jobs = new HashMap<>();
    private static final Map<UUID, BlockState> material = new HashMap<>();
    private static final Map<UUID, Display> displays = new HashMap<>();

    private Builders() {}

    public static void reset() {
        for (Display d : java.util.List.copyOf(displays.values())) clearPresentation(d.mob);
        jobs.clear();
        material.clear();
    }

    public static int stock(Mob m) { return TraitTags.get(m, "blocks"); }

    public static void addStock(Mob m, BlockState mined, int n) {
        TraitTags.set(m, "blocks", Math.min(MAX_STOCK, stock(m) + n));
        material.put(m.getUUID(), materialFor(mined));
    }

    public static boolean take(Mob m) {
        int count = stock(m);
        if (count <= 0) return false;
        TraitTags.set(m, "blocks", count - 1);
        return true;
    }

    public static BlockState material(Mob m) { return material.getOrDefault(m.getUUID(), Blocks.DIRT.defaultBlockState()); }

    private static BlockState materialFor(BlockState s) {
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)) return Blocks.COBBLESTONE.defaultBlockState();
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) return Blocks.OAK_PLANKS.defaultBlockState();
        return Blocks.DIRT.defaultBlockState();
    }

    public static boolean busy(Mob m) {
        Job j = jobs.get(m.getUUID());
        return j != null && (j.pillar || j.mining || j.bridging);
    }

    public static boolean griefing(ServerLevel level) {
        try { return level.getGameRules().get(GameRules.MOB_GRIEFING); }
        catch (Exception e) { return false; }
    }

    public static void present(Mob mob, ItemStack item) {
        Display d = displays.get(mob.getUUID());
        if (d != null && !ItemStack.isSameItemSameComponents(mob.getItemBySlot(d.slot), d.expected)) {
            clearPresentation(mob);
            d = null;
        }
        if (d == null) {
            EquipmentSlot slot = mob.getMainHandItem().isEmpty() ? EquipmentSlot.MAINHAND
                : mob.getOffhandItem().isEmpty() ? EquipmentSlot.OFFHAND : null;
            if (slot == null) return;
            d = new Display(mob, slot, mob.getDropChances().byEquipment(slot));
            displays.put(mob.getUUID(), d);
            mob.addTag(DISPLAY + slot.getName());
        }
        d.expected = item.copy();
        mob.setItemSlot(d.slot, item.copy());
        mob.setDropChance(d.slot, 0f);
    }

    public static void presentBlock(Mob mob, BlockState block) {
        if (block.getBlock().asItem() != Items.AIR) present(mob, new ItemStack(block.getBlock().asItem()));
    }

    public static void swing(Mob mob) {
        Display d = displays.get(mob.getUUID());
        mob.swing(d != null && d.slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
            net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    public static void clearPresentation(Mob mob) {
        Display d = displays.remove(mob.getUUID());
        if (d != null) {
            if (ItemStack.isSameItemSameComponents(mob.getItemBySlot(d.slot), d.expected)) mob.setItemSlot(d.slot, ItemStack.EMPTY);
            mob.setDropChance(d.slot, d.drop);
        }
        for (String tag : java.util.List.copyOf(mob.entityTags())) {
            if (!tag.startsWith(DISPLAY)) continue;
            if (d == null) {
                EquipmentSlot slot = tag.endsWith(EquipmentSlot.OFFHAND.getName()) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
            mob.removeTag(tag);
        }
    }

    public static void stop(Mob mob) {
        Job j = jobs.remove(mob.getUUID());
        if (j != null && j.miningAt != null && mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(), j.miningAt, -1);
        clearPresentation(mob);
    }

    public static void forget(Mob mob) {
        stop(mob);
        material.remove(mob.getUUID());
    }

    public static boolean tick(ServerLevel level, Mob mob, Player target, long t) {
        int builder = TraitTags.get(mob, "zombie_builder"), bridge = TraitTags.get(mob, "zombie_bridge");
        if ((builder <= 0 && bridge <= 0) || !griefing(level)) { stop(mob); return false; }
        Job j = jobs.computeIfAbsent(mob.getUUID(), k -> new Job());
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz), dy = target.getY() - mob.getY();
        if (Math.abs(mob.getX() - j.lastX) + Math.abs(mob.getZ() - j.lastZ) > 0.65) {
            j.lastX = mob.getX(); j.lastZ = mob.getZ(); j.still = 0;
        } else j.still++;

        if (j.mining) {
            if (horiz > 14 || mob.distanceToSqr(j.miningAt.getX() + 0.5, j.miningAt.getY() + 0.5, j.miningAt.getZ() + 0.5) > 12) { stop(mob); return false; }
            mine(level, mob, j, t); return true;
        }
        if (j.pillar) {
            if (builder <= 0 || dy < 0.6 || horiz > 4.5) { stop(mob); return false; }
            pillar(level, mob, j, t); return true;
        }
        if (j.bridging) {
            if (bridge <= 0 || horiz < 1.5 || Math.abs(dy) > 2.2 || t - j.bridgeStart > 400) { stop(mob); return false; }
            bridge(level, mob, j, bridge, t); return true;
        }
        boolean wantPillar = builder > 0 && dy >= 1.6 && horiz <= 2.5 + builder * 0.5 && j.still >= 18 - 3 * builder;
        int sx = Math.abs(dx) >= Math.abs(dz) ? (int) Math.signum(dx) : 0;
        int sz = Math.abs(dz) > Math.abs(dx) ? (int) Math.signum(dz) : 0;
        BlockPos front = mob.blockPosition().offset(sx, 0, sz);
        boolean wantBridge = bridge > 0 && Math.abs(dy) < 1.5 && horiz > 2 && horiz < 12
            && level.isLoaded(front) && level.getBlockState(front).canBeReplaced() && dryEmpty(level, front.below()) && mob.onGround();
        if (!wantPillar && !wantBridge) return false;
        if (stock(mob) <= 0) return startMining(level, mob, j, t);
        if (wantPillar && mob.onGround() && dryEmpty(level, mob.blockPosition().above(2))) {
            j.pillar = true; j.baseY = mob.blockPosition().getY(); j.nextAction = t + 12;
            presentBlock(mob, material(mob)); mob.getNavigation().stop(); return true;
        }
        if (wantBridge) {
            j.bridging = true; j.bridgeAt = front.below(); j.nextAction = t + 10; j.dirX = sx; j.dirZ = sz; j.phase = 0;
            j.baseY = mob.blockPosition().getY(); j.bridgeStart = t; j.jumpedAt = t;
            presentBlock(mob, material(mob)); mob.getNavigation().stop(); return true;
        }
        return false;
    }

    private static boolean dryEmpty(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        return s.canBeReplaced() && s.getFluidState().isEmpty() && !s.hasBlockEntity();
    }

    private static void pillar(ServerLevel level, Mob mob, Job j, long t) {
        mob.getNavigation().stop();
        Vec3 v = mob.getDeltaMovement(); mob.setDeltaMovement(v.x * 0.25, v.y, v.z * 0.25);
        BlockPos feet = BlockPos.containing(mob.getX(), j.baseY, mob.getZ());
        mob.getLookControl().setLookAt(feet.getX() + 0.5, feet.getY() - 0.5, feet.getZ() + 0.5);
        if (!j.jumping && mob.onGround() && t >= j.nextAction) {
            if (stock(mob) <= 0 || !dryEmpty(level, feet.above(2)) || !dryEmpty(level, feet.above(3))) { stop(mob); return; }
            if (RookieActions.hesitate(level, mob, "zombie_builder", TraitTags.get(mob, "zombie_builder"), t)) return;
            swing(mob); mob.setDeltaMovement(0, 0.46, 0); j.jumping = true; j.jumpedAt = t;
        }
        if (j.jumping) {
            if (mob.getY() >= j.baseY + 1.01 && dryEmpty(level, feet) && take(mob)) {
                place(level, mob, feet, material(mob));
                j.baseY++; j.jumping = false; j.nextAction = t + 14;
            } else if (t - j.jumpedAt > 20 && mob.onGround()) {
                j.jumping = false; j.nextAction = t + 12; j.baseY = mob.blockPosition().getY();
            }
        }
    }

    private static void bridge(ServerLevel level, Mob mob, Job j, int lvl, long t) {
        if (mob.getY() < j.baseY - 0.6 || (mob.isInWater() && !mob.onGround())) { stop(mob); return; }
        BlockPos pos = j.bridgeAt;
        BlockPos stand = pos.above().offset(-j.dirX, 0, -j.dirZ);
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setYRot((float) (Math.atan2(j.dirZ, j.dirX) * 180 / Math.PI) - 90f);
        mob.setYBodyRot(mob.getYRot());
        presentBlock(mob, material(mob));
        mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 30f, 90f);
        if (j.phase == 0) {
            double ex = stand.getX() + 0.5 + j.dirX * 0.32, ez = stand.getZ() + 0.5 + j.dirZ * 0.32;
            double ox = ex - mob.getX(), oz = ez - mob.getZ();
            double along = (mob.getX() - (stand.getX() + 0.5)) * j.dirX + (mob.getZ() - (stand.getZ() + 0.5)) * j.dirZ;
            if (along > 0.42) {
                mob.setDeltaMovement(-j.dirX * 0.08, mob.getDeltaMovement().y, -j.dirZ * 0.08);
                return;
            }
            if (ox * ox + oz * oz > 0.03) {
                Vec3 v = mob.getDeltaMovement();
                double len = Math.sqrt(ox * ox + oz * oz);
                mob.setDeltaMovement(ox / len * 0.07, v.y, oz / len * 0.07);
                if (t - j.jumpedAt > 60) stop(mob);
                return;
            }
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            if (t < j.nextAction || RookieActions.hesitate(level, mob, "zombie_bridge", lvl, t)) return;
            if (!dryEmpty(level, pos)) {
                j.phase = 1;
                j.jumpedAt = t;
                return;
            }
            if (!level.getBlockState(pos.above()).canBeReplaced() || !level.getBlockState(pos.above(2)).canBeReplaced() || !take(mob)) { stop(mob); return; }
            place(level, mob, pos, material(mob));
            j.phase = 1;
            j.jumpedAt = t;
            return;
        }
        double tx = pos.getX() + 0.5, tz = pos.getZ() + 0.5;
        double ox = tx - mob.getX(), oz = tz - mob.getZ();
        if (ox * ox + oz * oz > 0.05) {
            double len = Math.sqrt(ox * ox + oz * oz);
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(ox / len * 0.1, v.y, oz / len * 0.1);
            if (t - j.jumpedAt > 50) stop(mob);
            return;
        }
        BlockPos next = pos.offset(j.dirX, 0, j.dirZ);
        j.still = 0;
        if (dryEmpty(level, next) && level.getBlockState(next.above()).canBeReplaced() && stock(mob) > 0) {
            j.bridgeAt = next;
            j.phase = 0;
            j.jumpedAt = t;
            j.nextAction = t + Math.max(4, 14 - 3 * lvl);
            return;
        }
        j.bridging = false;
        clearPresentation(mob);
    }

    public static void place(ServerLevel level, Mob mob, BlockPos at, BlockState block) {
        if (!level.isLoaded(at)) return;
        if (!level.setBlockAndUpdate(at, block)) return;
        Milestones.nearby(level, at, "builders");
        level.playSound(null, at, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.85f, 0.95f);
        swing(mob);
    }

    private static boolean startMining(ServerLevel level, Mob mob, Job j, long t) {
        if (!mob.onGround()) return false;
        BlockPos feet = mob.blockPosition();
        for (int dy = 0; dy >= -1; dy--) for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos at = feet.relative(direction).above(dy);
            if (!level.isLoaded(at)) continue;
            BlockState s = level.getBlockState(at);
            if (!(s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL))
                || !s.getFluidState().isEmpty() || s.hasBlockEntity()) continue;
            if (dy == -1 && !level.getBlockState(at.above()).canBeReplaced()) continue;
            float hardness = s.getDestroySpeed(level, at);
            if (hardness <= 0.1f || hardness > 2f) continue;
            j.mining = true; j.miningAt = at; j.minedState = s; j.mineProgress = 0; j.mineNeed = Math.round(28 + hardness * 22); j.nextAction = t + 10;
            present(mob, new ItemStack(s.is(BlockTags.BASE_STONE_OVERWORLD) ? Items.WOODEN_PICKAXE : Items.WOODEN_SHOVEL));
            mob.getNavigation().stop(); return true;
        }
        return false;
    }

    private static void mine(ServerLevel level, Mob mob, Job j, long t) {
        BlockPos at = j.miningAt;
        if (!level.isLoaded(at)) { stop(mob); return; }
        BlockState s = level.getBlockState(at);
        if (!s.equals(j.minedState) || s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) { stop(mob); return; }
        mob.getNavigation().stop();
        Vec3 v = mob.getDeltaMovement(); mob.setDeltaMovement(v.x * 0.25, v.y, v.z * 0.25);
        mob.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        if (t < j.nextAction) return;
        j.mineProgress++;
        if (j.mineProgress % 8 == 1) {
            level.playSound(null, at, s.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.65f, 0.9f); swing(mob);
        }
        if (j.mineProgress >= j.mineNeed) {
            level.destroyBlockProgress(mob.getId(), at, -1);
            if (level.destroyBlock(at, false, mob)) addStock(mob, s, 2);
            j.mining = false; j.miningAt = null; j.still = 40; clearPresentation(mob);
        } else level.destroyBlockProgress(mob.getId(), at, Math.min(9, j.mineProgress * 10 / j.mineNeed));
    }
}
