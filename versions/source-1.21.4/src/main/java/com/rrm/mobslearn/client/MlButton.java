package com.rrm.mobslearn.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class MlButton extends AbstractButton {
    public enum Style { NORMAL, PRIMARY, DANGER, TAB }

    private final Consumer<MlButton> action;
    private Style style = Style.NORMAL;
    private boolean selected;
    private float textScale = 1f;
    private float hover;
    private long lastFrame;

    public MlButton(int x, int y, int w, int h, Component message, Consumer<MlButton> action) {
        super(x, y, w, h, message);
        this.action = action;
    }

    public MlButton style(Style s) {
        this.style = s;
        return this;
    }

    public MlButton textScale(float s) {
        this.textScale = s;
        return this;
    }

    public MlButton selected(boolean s) {
        this.selected = s;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        Ui.click(1f);
    }

    @Override
    public void onPress() {
        action.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = (isHovered() || selected) && active;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        hover += ((hot ? 1f : 0f) - hover) * (1f - (float) Math.exp(-dt * 15f));
        int fill, edge, text;
        if (!active) {
            fill = 0x80101010; edge = 0x18FFFFFF; text = 0xFF6E6A60;
        } else {
            switch (style) {
                case PRIMARY -> {
                    fill = Ui.mix(0xFF4E9A1E, 0xFF62B828, hover); edge = Ui.mix(0x00A6F05A, 0xFFA6F05A, hover); text = 0xFFFFFFFF;
                }
                case DANGER -> {
                    fill = Ui.mix(0xFF8A2C28, 0xFFB23A34, hover); edge = Ui.mix(0x00E8726A, 0xFFE8726A, hover); text = 0xFFFFFFFF;
                }
                default -> {
                    fill = Ui.mix(0xC0101410, 0xE0213318, hover); edge = Ui.mix(Ui.HAIRLINE, Ui.GREEN, hover); text = Ui.mix(Ui.CREAM, 0xFFFFFFFF, hover);
                }
            }
        }
        g.fill(x, y, x + w, y + h, fill);
        if ((edge >>> 24) != 0) Ui.border(g, x, y, w, h, edge);
        Font font = Minecraft.getInstance().font;
        Component msg = getMessage();
        int ty = y + Math.round((h - font.lineHeight * textScale) / 2f) + 1;
        Ui.scaledText(g, font, msg, x + w / 2, ty, textScale, text, true, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
