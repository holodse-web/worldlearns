package com.rrm.mobslearn.mob;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

public final class Compat {
    private Compat() {
    }

    public static float dropChance(Mob mob, EquipmentSlot slot) {
        return 0.085f;
    }
}
