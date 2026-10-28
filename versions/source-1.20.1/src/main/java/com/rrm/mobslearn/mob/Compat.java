package com.rrm.mobslearn.mob;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class Compat {
    private Compat() {
    }

    public static float dropChance(Mob mob, EquipmentSlot slot) {
        return 0.085f;
    }

    public static UUID uuid(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static AttributeModifier mod(ResourceLocation id, double value, AttributeModifier.Operation op) {
        return new AttributeModifier(uuid(id), id.toString(), value, op);
    }

    public static void addOrUpdate(AttributeInstance inst, AttributeModifier m) {
        inst.removeModifier(m.getId());
        inst.addTransientModifier(m);
    }
}
