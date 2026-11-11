package com.rrm.mobslearn.mixin;

import com.rrm.mobslearn.mob.Colonies;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Peaceful settlement residents must not even acquire a player as a target (vanilla bow charge survives a cleared target). */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mobslearn$peaceful(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target != null && !self.level().isClientSide() && (Colonies.refusesTarget(self, target) || com.rrm.mobslearn.mob.NewSkills.refusesTarget(self, target))) {
            ci.cancel();
            if (self.getTarget() instanceof net.minecraft.world.entity.player.Player) self.setTarget(null);
            if (self.isUsingItem()) self.stopUsingItem();
            self.setAggressive(false);
        }
    }
}
