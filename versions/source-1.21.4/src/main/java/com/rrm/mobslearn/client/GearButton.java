package com.rrm.mobslearn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

public final class GearButton extends MlButton {
    private float angle, hover;
    private long last;

    public GearButton(int x, int y, int width, int height, Runnable action) {
        super(x, y, width, height, Component.translatable("mobslearn.settings.title"), b -> action.run());
        setTooltip(Tooltip.create(Component.translatable("mobslearn.settings.title")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        Component label = getMessage();
        setMessage(Component.empty());
        super.renderWidget(g, mx, my, partial);
        setMessage(label);
        long now = Util.getMillis();
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        hover = Anim.approach(hover, isHovered() ? 1f : 0f, dt, 10f);
        angle += dt * (0.4f + hover * 3.2f);
        int cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2;
        int color = Anim.lerp(0xFFC9B68A, 0xFFFFFFFF, hover);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                double r = Math.sqrt(dx * dx + dy * dy);
                double a = Math.atan2(dy, dx);
                boolean tooth = Math.cos(8 * a + angle * 2) > 0.25;
                boolean on = r <= 3.7 ? r >= 1.7 : r <= 5.7 && tooth;
                if (on) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
    }
}
