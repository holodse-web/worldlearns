package com.rrm.mobslearn.mob;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

public final class Compat {
    private Compat() {
    }

    public static float dropChance(Mob mob, EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> mob.getHandDropChances()[slot.getIndex()];
            case HUMANOID_ARMOR -> mob.getArmorDropChances()[slot.getIndex()];
            default -> 0.085f;
        };
    }
}
