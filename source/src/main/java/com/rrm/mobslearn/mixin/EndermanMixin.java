package com.rrm.mobslearn.mixin;

import com.rrm.mobslearn.mob.TraitTags;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enderman.class)
public abstract class EndermanMixin {
    @Inject(method = "isSensitiveToWater", at = @At("HEAD"), cancellable = true)
    private void mobslearn$umbrella(CallbackInfoReturnable<Boolean> cir) {
        if (TraitTags.get((Enderman) (Object) this, "enderman_umbrella") > 0) cir.setReturnValue(false);
    }
}
