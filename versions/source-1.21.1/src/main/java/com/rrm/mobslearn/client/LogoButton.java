package com.rrm.mobslearn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;

public class LogoButton extends AbstractButton {
    private final Runnable action;
    private float hover;
    private long lastFrame;

    public LogoButton(int x, int y, int w, int h, Runnable action) {
        super(x, y, w, h, Component.translatable("mobslearn.title.button"));
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hover += ((isHovered() ? 1f : 0f) - hover) * (1f - (float) Math.exp(-dt * 6f));
        float e = hover * hover * (3f - 2f * hover);
        float breathe = Mth.sin(now / 900f) * 0.012f * (1f - e);
        float s = 1f + e * 0.10f + breathe;
        float tilt = e * 0.035f;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(tilt));
        g.pose().scale(s, s, 1);
        g.pose().translate(-w / 2f, -h / 2f, 0);
        Ui.logoAt(g, 0, 0, w, h);
        g.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
