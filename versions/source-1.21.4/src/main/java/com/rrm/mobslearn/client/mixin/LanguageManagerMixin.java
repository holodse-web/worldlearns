package com.rrm.mobslearn.client.mixin;

import com.rrm.mobslearn.client.ModLanguage;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void mobslearn$reloadLanguage(ResourceManager resources, CallbackInfo ci) {
        ModLanguage.onResourceReload(resources, ((LanguageManager) (Object) this).getSelected());
    }
}
