package com.rrm.mobslearn.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieModel.class)
public abstract class ZombieModelMixin {
    @Inject(method = "setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At("TAIL"), require = 0)
    private void mobslearn$block(ZombieRenderState state, CallbackInfo ci) {
        if (!state.isUsingItem) return;
        HumanoidArm arm = state.useItemHand == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
        boolean shield = arm == HumanoidArm.LEFT ? state.leftHandItemStack != null && state.leftHandItemStack.is(Items.SHIELD)
            : state.rightHandItemStack != null && state.rightHandItemStack.is(Items.SHIELD);
        if (!shield) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        ModelPart part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        part.xRot = -0.95f;
        part.yRot = arm == HumanoidArm.LEFT ? 0.52f : -0.52f;
        part.zRot = 0f;
    }
}
