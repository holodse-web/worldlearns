package com.rrm.mobslearn.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.Util;

public final class LanguageScreen extends Screen {
    private final Screen parent;
    private final List<OptionButton> choices = new ArrayList<>();
    private long openedAt;
    private int x, y, w, panelH, footerY;
    private boolean saveFailed;

    public LanguageScreen(Screen parent) {
        super(Component.translatable("mobslearn.language.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        choices.clear();
        w = Math.min(430, width - 24);
        panelH = Math.min(252, height - 12);
        x = (width - w) / 2;
        y = (height - panelH) / 2;
        int rowH = Math.max(20, (panelH - 132) / 3);
        addChoice(x + 12, y + 42, w - 24, 26, ModLanguage.AUTO, null);
        int cardW = (w - 30) / 2;
        for (int i = 0; i < ModLanguage.CHOICES.size(); i++) {
            ModLanguage.Choice choice = ModLanguage.CHOICES.get(i);
            addChoice(x + 12 + (i % 2) * (cardW + 6), y + 76 + (i / 2) * (rowH + 5), cardW, rowH, choice.code(), choice);
        }
        footerY = y + 86 + 3 * rowH;
        int backW = Math.min(160, w - 24);
        addRenderableWidget(new MlButton(width / 2 - backW / 2, footerY + 18, backW, 20,
            Component.translatable("mobslearn.gui.done"), b -> onClose()).style(MlButton.Style.PRIMARY));
    }

    private void addChoice(int x, int y, int w, int h, String code, ModLanguage.Choice choice) {
        OptionButton button = new OptionButton(x, y, w, h, code, choice);
        choices.add(button);
        addRenderableWidget(button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    }

    public void mlBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00E150D, 0xFA080C08);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        mlBackground(g, mx, my, partial);
        float t = Math.max(0f, Math.min(1f, (Util.getMillis() - openedAt) / 240f));
        float intro = 1f - (float) Math.pow(1f - t, 3);
        g.pose().pushPose();
        g.pose().translate(0, (1f - intro) * 8f, 0);
        Ui.panel(g, x, y, w, panelH);
        Ui.scaledText(g, font, title, width / 2, y + 10, 1.12f, Ui.GREEN, true, false);
        Component subtitle = Component.translatable("mobslearn.language.subtitle");
        float subtitleScale = Math.min(1f, (w - 28f) / Math.max(1, font.width(subtitle)));
        Ui.scaledText(g, font, subtitle, width / 2, y + 27, subtitleScale, Ui.MUTED, true, false);
        Component status = Component.translatable(saveFailed ? "mobslearn.language.save_failed" : "mobslearn.language.local");
        float statusScale = Math.min(1f, (w - 28f) / Math.max(1, font.width(status)));
        Ui.scaledText(g, font, status, width / 2, footerY + 3, statusScale, saveFailed ? Ui.RED : Ui.MUTED, true, false);
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    private final class OptionButton extends AbstractButton {
        private final String code;
        private final ModLanguage.Choice choice;
        private float hover;
        private long lastFrame;

        OptionButton(int x, int y, int w, int h, String code, ModLanguage.Choice choice) {
            super(x, y, w, h, choice == null ? Component.translatable("mobslearn.language.auto") : Component.literal(choice.name()));
            this.code = code;
            this.choice = choice;
        }

        @Override
        public void onPress() {
            saveFailed = !ModLanguage.select(code);
            for (OptionButton button : choices) button.setTooltip(null);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;
            boolean selected = code.equals(ModLanguage.selected());
            hover += ((isHovered() || selected ? 1f : 0f) - hover) * (1f - (float) Math.exp(-dt * 15f));
            int bx = getX(), by = getY(), bw = getWidth(), bh = getHeight();
            g.fill(bx, by, bx + bw, by + bh, Ui.mix(0xC0101710, 0xF023381B, hover));
            Ui.border(g, bx, by, bw, bh, Ui.mix(Ui.HAIRLINE, Ui.GREEN, hover));
            LanguageFlags.draw(g, code, bx + 9, by + (bh - 14) / 2f, 22, 14);
            int textX = bx + 40;
            Component label = choice == null ? Component.translatable("mobslearn.language.auto") : Component.literal(choice.name());
            int textY = by + (bh - 9) / 2;
            if (choice == null) {
                textY = by + 4;
                Component follows = Component.translatable("mobslearn.language.minecraft", ModLanguage.minecraftChoice().name());
                float scale = Math.min(0.8f, (bw - 58f) / Math.max(1, font.width(follows)));
                Ui.scaledText(g, font, follows, textX, by + 15, scale, Ui.MUTED, false, false);
            }
            float labelScale = Math.min(1f, (bw - 58f) / Math.max(1, font.width(label)));
            Ui.scaledText(g, font, label, textX, textY, labelScale, selected ? 0xFFFFFFFF : Ui.CREAM, false, false);
            if (selected) {
                Ui.fill(g, bx + bw - 14, by + bh / 2f, 2, 3, Ui.GREEN);
                Ui.fill(g, bx + bw - 12, by + bh / 2f + 2, 2, 2, Ui.GREEN);
                Ui.fill(g, bx + bw - 10, by + bh / 2f - 2, 2, 5, Ui.GREEN);
            }
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            MutableComponent message = super.createNarrationMessage();
            if (code.equals(ModLanguage.selected())) message.append(". ").append(Component.translatable("mobslearn.language.selected"));
            return message;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
