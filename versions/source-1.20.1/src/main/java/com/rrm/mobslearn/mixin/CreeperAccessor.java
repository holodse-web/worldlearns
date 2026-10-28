package com.rrm.mobslearn.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("maxSwell")
    void mobslearn$setMaxSwell(int value);

    @Accessor("explosionRadius")
    void mobslearn$setExplosionRadius(int value);
}
