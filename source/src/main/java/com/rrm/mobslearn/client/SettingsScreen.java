package com.rrm.mobslearn.client;

import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.net.Net;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private double speed;
    private boolean absurd, combat, enabled;
    private int difficulty, warp;
    private static final int[] WARPS = {1, 2, 5, 10, 20, 50, 100, 500, 1000};
    private int x, y, w, row;
    private MlButton apply;
    private long sentAt, sentRevision, openedAt;
    private String status;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("mobslearn.settings.title"));
        this.parent = parent;
        Knowledge k = ClientKnowledge.get();
        speed = Math.max(0.25, Math.min(4.0, k.speed));
        absurd = k.absurd;
        combat = k.combat;
        difficulty = k.difficulty;
        enabled = k.enabled;
        warp = Math.max(1, k.warp);
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        w = Math.min(368, width - 24);
        row = Math.min(30, Math.max(22, (height - 82) / 6));
        int panelH = 74 + row * 6;
        x = (width - w) / 2;
        y = Math.max(6, (height - panelH) / 2);
        int cy = y + 32, controlW = Math.min(156, w / 2 - 12), controlX = x + w - controlW - 12;
        boolean editable = ClientKnowledge.canConfigure();
        MlButton toggle = new MlButton(controlX, cy, controlW, 20, onOff(enabled), b -> {
            enabled = !enabled;
            b.setMessage(onOff(enabled));
            changed();
        });
        toggle.active = editable;
        addRenderableWidget(toggle);
        cy += row;
        var slider = new SpeedSlider(controlX, cy, controlW);
        slider.active = editable;
        addRenderableWidget(slider);
        cy += row;
        var absurdButton = new MlButton(controlX, cy, controlW, 20, onOff(absurd), b -> {
            absurd = !absurd;
            b.setMessage(onOff(absurd));
            changed();
        });
        absurdButton.active = editable;
        addRenderableWidget(absurdButton);
        cy += row;
        var combatButton = new MlButton(controlX, cy, controlW, 20, onOff(combat), b -> {
            combat = !combat;
            b.setMessage(onOff(combat));
            changed();
        });
        combatButton.active = editable;
        addRenderableWidget(combatButton);
        cy += row;
        var difficultyButton = new MlButton(controlX, cy, controlW, 20, difficultyName(), b -> {
            difficulty = (difficulty + 1) % 3;
            b.setMessage(difficultyName());
            changed();
        });
        difficultyButton.active = editable;
        addRenderableWidget(difficultyButton);
        cy += row;
        var warpButton = new MlButton(controlX, cy, controlW, 20, warpName(), b -> {
            int i = 0;
            while (i < WARPS.length && WARPS[i] <= warp) i++;
            warp = i >= WARPS.length ? WARPS[0] : WARPS[i];
            b.setMessage(warpName());
            changed();
        });
        warpButton.active = editable;
        addRenderableWidget(warpButton);
        cy += row + 8;
        addRenderableWidget(new MlButton(x + 12, cy, (w - 28) / 2, 20, Component.translatable("mobslearn.gui.back"), b -> onClose()));
        apply = addRenderableWidget(new MlButton(x + w / 2 + 2, cy, (w - 28) / 2, 20,
            Component.translatable("mobslearn.settings.apply"), b -> save()).style(MlButton.Style.PRIMARY));
        apply.active = editable && sentAt == 0;
    }

    private void changed() {
        status = null;
        sentAt = 0;
        if (apply != null) apply.active = ClientKnowledge.canConfigure();
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private Component warpName() {
        return Component.literal(warp + "×");
    }

    private Component difficultyName() {
        return Component.translatable("mobslearn.settings.difficulty." + difficulty);
    }

    private void save() {
        if (!ClientKnowledge.canConfigure() || !ClientPlayNetworking.canSend(Net.SettingsPayload.TYPE)) return;
        sentRevision = ClientKnowledge.revision();
        sentAt = Util.getMillis();
        status = "mobslearn.settings.saving";
        apply.active = false;
        ClientPlayNetworking.send(new Net.SettingsPayload(speed, absurd, combat, difficulty, enabled, warp));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00E150D, 0xFA080C08);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        long now = Util.getMillis();
        if (sentAt != 0 && ClientKnowledge.revision() > sentRevision) {
            Knowledge k = ClientKnowledge.get();
            if (Math.abs(k.speed - speed) < 0.001 && k.absurd == absurd && k.combat == combat
                && k.difficulty == difficulty && k.enabled == enabled && k.warp == warp) {
                sentAt = 0;
                status = "mobslearn.settings.saved";
            }
        }
        if (sentAt != 0 && now - sentAt > 8000) {
            sentAt = 0;
            status = "mobslearn.settings.failed";
            apply.active = ClientKnowledge.canConfigure();
        }
        float t = Math.max(0f, Math.min(1f, (now - openedAt) / 260f));
        float intro = 1f - (float) Math.pow(1f - t, 3);
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - intro) * 9f);
        Ui.panel(g, x, y, w, 74 + row * 6);
        Ui.scaledText(g, font, title, width / 2, y + 10, 1.15f, Ui.GREEN, true, false);
        String[] labels = {"enabled", "speed", "absurd", "combat", "difficulty", "warp"};
        for (int i = 0; i < labels.length; i++) {
            String label = font.plainSubstrByWidth(Component.translatable("mobslearn.settings." + labels[i]).getString(), w / 2 - 20);
            g.text(font, label, x + 12, y + 38 + row * i, Ui.CREAM, false);
        }
        super.extractRenderState(g, mx, my, partial);
        g.pose().popMatrix();
        if (!ClientKnowledge.canConfigure()) {
            Ui.center(g, font, Component.translatable("mobslearn.settings.readonly"), width / 2, y + row * 6 + 66, Ui.MUTED);
        } else if (status != null) {
            Ui.center(g, font, Component.translatable(status), width / 2, y + row * 6 + 66,
                status.equals("mobslearn.settings.failed") ? Ui.RED : Ui.GREEN);
        }
    }

    private final class SpeedSlider extends AbstractSliderButton {
        SpeedSlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), Math.log(Math.max(0.25, Math.min(4, speed)) / 0.25) / Math.log(16));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.ROOT, "%.2f×", speed)));
        }

        @Override
        protected void applyValue() {
            speed = Math.round(0.25 * Math.pow(16, value) * 100.0) / 100.0;
            changed();
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
            int x = getX(), y = getY(), w = getWidth();
            g.fill(x, y, x + w, y + 20, active ? 0xC0101410 : 0x80101010);
            Ui.border(g, x, y, w, 20, active && isHovered() ? Ui.GREEN : Ui.HAIRLINE);
            Ui.fill(g, x + 4, y + 16, (float) ((w - 8) * value), 2, active ? Ui.GREEN_DARK : Ui.MUTED);
            Ui.fill(g, x + 3 + (float) ((w - 10) * value), y + 15, 4, 4, active ? Ui.GREEN : Ui.MUTED);
            Ui.center(g, Minecraft.getInstance().font, getMessage(), x + w / 2, y + 4, active ? Ui.CREAM : Ui.MUTED);
        }
    }
}
