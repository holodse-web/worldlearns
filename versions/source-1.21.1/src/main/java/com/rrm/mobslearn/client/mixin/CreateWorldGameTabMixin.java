package com.rrm.mobslearn.client.mixin;

import com.rrm.mobslearn.MobsLearnRules;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin extends GridLayoutTab {
    protected CreateWorldGameTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobslearn$addToggle(CreateWorldScreen screen, CallbackInfo ci) {
        var state = screen.getUiState();
        boolean initial = MobsLearnRules.get(state.getGameRules());
        var toggle = net.minecraft.client.gui.components.Button.builder(mobslearn$toggleLabel(initial), button -> {
            boolean enabled = !MobsLearnRules.get(state.getGameRules());
            MobsLearnRules.set(state.getGameRules(), enabled, null);
            button.setMessage(mobslearn$toggleLabel(enabled));
        }).size(210, 20).tooltip(Tooltip.create(Component.translatable("mobslearn.creation.tooltip"))).build();
        state.addListener(s -> toggle.setMessage(mobslearn$toggleLabel(MobsLearnRules.get(s.getGameRules()))));
        int[] rows = {0};
        layout.visitChildren(child -> rows[0]++);
        layout.addChild(toggle, rows[0], 0);
    }

    private static Component mobslearn$toggleLabel(boolean enabled) {
        return Component.translatable("mobslearn.creation.enabled").append(": ")
            .append(Component.translatable(enabled ? "options.on" : "options.off").withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
    }
}
