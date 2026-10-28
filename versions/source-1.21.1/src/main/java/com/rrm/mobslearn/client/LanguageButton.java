package com.rrm.mobslearn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class LanguageButton extends MlButton {
    public LanguageButton(int x, int y, int width, int height, Runnable action) {
        super(x, y, width, height, Component.translatable("mobslearn.language.title"), b -> action.run());
        setTooltip(Tooltip.create(Component.translatable("mobslearn.language.title")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        Component label = getMessage();
        setMessage(Component.empty());
        super.renderWidget(g, mx, my, partial);
        setMessage(label);
        LanguageFlags.draw(g, ModLanguage.current().code(), getX() + (getWidth() - 20) / 2f,
            getY() + (getHeight() - 12) / 2f, 20, 12);
    }
}
