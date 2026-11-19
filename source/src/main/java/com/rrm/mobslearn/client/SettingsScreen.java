package com.rrm.mobslearn.client;

import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.net.Net;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class SettingsScreen extends Screen {
    private static final String[] TABS = {"learning", "settlements", "events"};
    private static final int[] WARPS = {1, 2, 5, 10, 20, 50, 100, 500, 1000};
    private static final double[] SETTLEMENT_SPEEDS = {0.25, 0.5, 1, 2, 3, 5};
    private static int lastTab;

    private final Screen parent;
    private double speed, settlementSpeed;
    private boolean absurd, combat, enabled, villages, settlements, nemesis, raids, bosses, march;
    private int difficulty, warp;

    private record Row(int tab, String key, AbstractWidget control, boolean needsSettlements) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<MlButton> tabButtons = new ArrayList<>();
    private int tab = lastTab, x, y, w, rowH, panelH, top;
    private float underline = -1, underlineW;
    private MlButton apply;
    private long sentAt, sentRevision, openedAt, tabAt, lastFrame;
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
        settlementSpeed = k.settlementSpeed;
        villages = k.villages;
        settlements = k.settlements;
        nemesis = k.nemesis;
        raids = k.homeRaids;
        bosses = k.bossMemory;
        march = k.greatMarch;
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        rows.clear();
        tabButtons.clear();
        w = Math.min(400, width - 24);
        rowH = Math.min(34, Math.max(26, (height - 120) / 5));
        panelH = 96 + rowH * 5;
        x = (width - w) / 2;
        y = Math.max(6, (height - panelH) / 2);
        top = y + 58;
        boolean editable = ClientKnowledge.canConfigure();
        int tabW = (w - 24 - 8) / 3;
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            MlButton b = new MlButton(x + 12 + i * (tabW + 4), y + 28, tabW, 18, Component.translatable("mobslearn.settings.tab." + TABS[i]), btn -> selectTab(index));
            b.style(MlButton.Style.TAB);
            tabButtons.add(addRenderableWidget(b));
        }
        int cw = Math.min(150, w / 2 - 16), cx = x + w - cw - 12;
        row(0, "enabled", toggle(cx, cw, () -> enabled, v -> enabled = v), false);
        SpeedSlider slider = new SpeedSlider(cx, 0, cw);
        row(0, "speed", slider, false);
        row(0, "absurd", toggle(cx, cw, () -> absurd, v -> absurd = v), false);
        row(0, "combat", toggle(cx, cw, () -> combat, v -> combat = v), false);
        row(0, "difficulty", new MlButton(cx, 0, cw, 20, difficultyName(), b -> {
            difficulty = (difficulty + 1) % 3;
            b.setMessage(difficultyName());
            changed();
        }), false);
        row(1, "settlements", toggle(cx, cw, () -> settlements, v -> settlements = v), false);
        row(1, "villages", toggle(cx, cw, () -> villages, v -> villages = v), true);
        row(1, "settlement_speed", new MlButton(cx, 0, cw, 20, settlementSpeedName(), b -> {
            int i = 0;
            while (i < SETTLEMENT_SPEEDS.length && SETTLEMENT_SPEEDS[i] <= settlementSpeed) i++;
            settlementSpeed = SETTLEMENT_SPEEDS[i == SETTLEMENT_SPEEDS.length ? 0 : i];
            b.setMessage(settlementSpeedName());
            changed();
        }), true);
        row(1, "warp", new MlButton(cx, 0, cw, 20, warpName(), b -> {
            int i = 0;
            while (i < WARPS.length && WARPS[i] <= warp) i++;
            warp = i >= WARPS.length ? WARPS[0] : WARPS[i];
            b.setMessage(warpName());
            changed();
        }), true);
        row(2, "nemesis", toggle(cx, cw, () -> nemesis, v -> nemesis = v), false);
        row(2, "home_raids", toggle(cx, cw, () -> raids, v -> raids = v), true);
        row(2, "boss_memory", toggle(cx, cw, () -> bosses, v -> bosses = v), false);
        row(2, "great_march", toggle(cx, cw, () -> march, v -> march = v), true);
        int by = y + panelH - 28;
        addRenderableWidget(new MlButton(x + 12, by, (w - 28) / 2, 20, Component.translatable("mobslearn.gui.back"), b -> onClose()));
        apply = addRenderableWidget(new MlButton(x + w / 2 + 2, by, (w - 28) / 2, 20,
            Component.translatable("mobslearn.settings.apply"), b -> save()).style(MlButton.Style.PRIMARY));
        apply.active = editable && sentAt == 0;
        layout();
    }

    private interface Getter {
        boolean get();
    }

    private interface Setter {
        void set(boolean value);
    }

    private MlButton toggle(int cx, int cw, Getter get, Setter set) {
        return new MlButton(cx, 0, cw, 20, onOff(get.get()), b -> {
            set.set(!get.get());
            b.setMessage(onOff(get.get()));
            changed();
            layout();
        });
    }

    private void row(int tab, String key, AbstractWidget control, boolean needsSettlements) {
        rows.add(new Row(tab, key, addRenderableWidget(control), needsSettlements));
    }

    private void selectTab(int index) {
        if (index == tab) return;
        tab = lastTab = index;
        tabAt = Util.getMillis();
        layout();
    }

    private void layout() {
        boolean editable = ClientKnowledge.canConfigure();
        int i = 0;
        for (Row r : rows) {
            boolean shown = r.tab == tab;
            r.control.visible = shown;
            if (!shown) continue;
            r.control.setY(top + i * rowH + (rowH - 20) / 2);
            r.control.active = editable && (!r.needsSettlements || settlements);
            i++;
        }
        for (int t = 0; t < tabButtons.size(); t++) tabButtons.get(t).selected(t == tab);
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

    private Component settlementSpeedName() {
        return Component.literal(String.format(Locale.ROOT, "%.2f×", settlementSpeed));
    }

    private Component difficultyName() {
        return Component.translatable("mobslearn.settings.difficulty." + difficulty);
    }

    private int flags() {
        return (settlements ? Knowledge.F_SETTLEMENTS : 0) | (nemesis ? Knowledge.F_NEMESIS : 0) | (raids ? Knowledge.F_RAIDS : 0)
            | (bosses ? Knowledge.F_BOSSES : 0) | (march ? Knowledge.F_MARCH : 0);
    }

    private void save() {
        if (!ClientKnowledge.canConfigure() || !ClientPlayNetworking.canSend(Net.SettingsPayload.TYPE)) return;
        sentRevision = ClientKnowledge.revision();
        sentAt = Util.getMillis();
        status = "mobslearn.settings.saving";
        apply.active = false;
        ClientPlayNetworking.send(new Net.SettingsPayload(speed, absurd, combat, difficulty, enabled, warp, settlementSpeed, villages, flags()));
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
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (sentAt != 0 && ClientKnowledge.revision() > sentRevision) {
            Knowledge k = ClientKnowledge.get();
            if (Math.abs(k.speed - speed) < 0.001 && k.absurd == absurd && k.combat == combat
                && k.difficulty == difficulty && k.enabled == enabled && k.warp == warp
                && Math.abs(k.settlementSpeed - settlementSpeed) < 0.001 && k.villages == villages && k.flags() == flags()) {
                sentAt = 0;
                status = "mobslearn.settings.saved";
            }
        }
        if (sentAt != 0 && now - sentAt > 8000) {
            sentAt = 0;
            status = "mobslearn.settings.failed";
            apply.active = ClientKnowledge.canConfigure();
        }
        float intro = Anim.ease((now - openedAt) / 260f);
        float swap = tabAt == 0 ? 1f : Anim.ease((now - tabAt) / 220f);
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - intro) * 9f);
        Ui.panel(g, x, y, w, panelH);
        Ui.scaledText(g, font, title, width / 2, y + 10, 1.15f, Ui.GREEN, true, false);
        MlButton active = tabButtons.isEmpty() ? null : tabButtons.get(tab);
        if (active != null) {
            if (underline < 0) {
                underline = active.getX();
                underlineW = active.getWidth();
            }
            underline = Anim.approach(underline, active.getX(), dt, 14f);
            underlineW = Anim.approach(underlineW, active.getWidth(), dt, 14f);
            Ui.fill(g, underline, active.getY() + active.getHeight() + 1, underlineW, 2, Ui.GREEN);
        }
        g.fill(x + 12, top - 6, x + w - 12, top - 5, Ui.HAIRLINE);
        int labelW = w - Math.min(150, w / 2 - 16) - 36;
        int i = 0;
        for (Row r : rows) {
            if (r.tab != tab) continue;
            int ry = top + i * rowH;
            float local = Anim.ease((now - Math.max(tabAt, openedAt) - i * 35L) / 200f) * swap;
            int dx = Math.round((1f - local) * 10f);
            boolean dim = r.needsSettlements && !settlements;
            Component label = Component.translatable("mobslearn.settings." + r.key);
            Component hint = dim ? Component.translatable("mobslearn.settings.needs_settlements")
                : Component.translatable("mobslearn.settings." + r.key + ".hint");
            float scale = Math.min(1f, (float) labelW / Math.max(1, font.width(label)));
            List<String> lines = wrap(hint.getString(), (int) (labelW / 0.75f), 2);
            int block = 9 + 2 + lines.size() * 7;
            int ly = ry + (rowH - block) / 2 + 1;
            Ui.scaledText(g, font, label, x + 14 + dx, ly, scale, Anim.fade(dim ? Ui.MUTED : Ui.CREAM, local), false, false);
            for (int li = 0; li < lines.size(); li++) {
                Ui.scaledText(g, font, Component.literal(lines.get(li)), x + 14 + dx, ly + 11 + li * 7, 0.75f, Anim.fade(dim ? 0xFF7A7466 : Ui.MUTED, local), false, false);
            }
            if (i > 0) g.fill(x + 14, ry, x + w - 14, ry + 1, 0x10FFFFFF);
            i++;
        }
        super.extractRenderState(g, mx, my, partial);
        g.pose().popMatrix();
        int sy = y + panelH + 6;
        if (!ClientKnowledge.canConfigure()) {
            Ui.center(g, font, Component.translatable("mobslearn.settings.readonly"), width / 2, sy, Ui.MUTED);
        } else if (status != null) {
            Ui.center(g, font, Component.translatable(status), width / 2, sy, status.equals("mobslearn.settings.failed") ? Ui.RED : Ui.GREEN);
        }
    }

    private List<String> wrap(String text, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");
        for (int wi = 0; wi < words.length; wi++) {
            String word = words[wi];
            String next = line.length() == 0 ? word : line + " " + word;
            if (font.width(next) <= maxW) {
                line = new StringBuilder(next);
                continue;
            }
            if (line.length() == 0) {
                line = new StringBuilder(font.plainSubstrByWidth(word, maxW));
                continue;
            }
            if (out.size() == maxLines - 1) {
                String rest = line + " " + String.join(" ", java.util.Arrays.copyOfRange(words, wi, words.length));
                out.add(font.plainSubstrByWidth(rest, maxW - font.width("…")) + "…");
                return out;
            }
            out.add(line.toString());
            line = new StringBuilder(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
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
