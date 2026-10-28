package com.rrm.mobslearn.client;

import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;

public final class JournalScreen extends Screen {
    private static final int CARD = 0xFF121A11, CARD_HOT = 0xFF19241A, DIM = 0xFFA8A08A, FAINT = 0xFF6E6A5C, PINK = 0xFFFF7AD9, TEAL = 0xFF6FC0B4;

    private final Screen parent;
    private float scroll, target, dt;
    private int contentH;
    private long lastFrame, openedAt;
    private MobGroup filter;
    private final List<int[]> chipHits = new ArrayList<>();
    private final List<MobGroup> chipGroups = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();

    public JournalScreen(Screen parent) {
        super(Component.translatable("mobslearn.journal.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        addRenderableWidget(new MlButton(width / 2 - 55, height - 27, 110, 20, Component.translatable("mobslearn.gui.back"), b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int maxScroll() {
        return Math.max(0, contentH - (height - 80));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sy) {
        target = Mth.clamp(target - (float) sy * 32f, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        for (int i = 0; i < chipHits.size(); i++) {
            int[] h = chipHits.get(i);
            if (mouseX >= h[0] && mouseX < h[0] + h[2] && mouseY >= h[1] && mouseY < h[1] + h[3]) {
                MobGroup g = chipGroups.get(i);
                filter = filter == g ? null : g;
                Ui.click(filter == null ? 0.9f : 1.25f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME) target = 0;
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END) target = maxScroll();
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN) target = Math.min(maxScroll(), target + height * 0.7f);
        else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) target = Math.max(0, target - height * 0.7f);
        else return super.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public void mlBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00E150D, 0xFA080C08);
    }

    private float hot(String key, boolean on) {
        float v = Anim.approach(hover.getOrDefault(key, 0f), on ? 1f : 0f, dt, 12f);
        hover.put(key, v);
        return v;
    }

    private void icon(GuiGraphics g, String id, int x, int y, int size) {
        com.rrm.mobslearn.client.Ui.blitUV(g,MobsLearn.id("textures/gui/icon/" + id + ".png"), x, y, x + size, y + size, 0f, 1f, 0f, 1f);
    }

    private static String roman(int n) {
        return n == 1 ? "I" : n == 2 ? "II" : "III";
    }

    private long today() {
        return minecraft.level == null ? 1 : minecraft.level.getDayTime() / 24000L + 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        mlBackground(g, mx, my, partial);
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        scroll += (target - scroll) * (1f - (float) Math.exp(-dt * 13f));
        int w = Math.min(500, width - 24), x = (width - w) / 2, top = 44, bottom = height - 36;
        Ui.scaledText(g, font, title, width / 2, 12, 1.3f, Ui.GREEN, true, false);
        Ui.center(g, font, Component.translatable("mobslearn.journal.subtitle"), width / 2, 29, Ui.MUTED);
        Ui.panel(g, x, top, w, bottom - top);
        g.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        chipHits.clear();
        chipGroups.clear();
        Knowledge k = ClientKnowledge.get();
        float intro = Anim.ease((now - openedAt) / 320f);
        int y = top + 10 - Math.round(scroll) + Math.round((1f - intro) * 10f);
        int ix = x + 12, iw = w - 24;
        boolean inside = my >= top && my < bottom;

        int learned = 0, total = 0;
        for (Skill s : Skill.ALL) {
            if (s.group() == MobGroup.WORLD || !k.allows(s)) continue;
            total++;
            if (k.level(s) > 0) learned++;
        }
        String[] values = {String.valueOf(today()), k.intelligence() + "%", learned + "/" + total, String.valueOf(k.journal.size())};
        String[] labels = {"day", "intel", "skills", "entries"};
        int[] colors = {Ui.GOLD, Ui.GREEN, TEAL, PINK};
        int cols = iw >= 360 ? 4 : 2, gap = 6, cw = (iw - gap * (cols - 1)) / cols, ch = 44;
        for (int i = 0; i < 4; i++) {
            int cx = ix + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
            float hv = hot("stat" + i, inside && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch);
            g.fill(cx, cy, cx + cw, cy + ch, Anim.fade(Ui.mix(CARD, CARD_HOT, hv), intro));
            Ui.border(g, cx, cy, cw, ch, Anim.fade(Ui.mix(0x22FFFFFF, colors[i], hv), intro));
            g.fill(cx, cy, cx + cw, cy + 2, Anim.fade(colors[i], intro));
            Ui.scaledText(g, font, Component.literal(values[i]), cx + cw / 2, cy + 9, 1.6f, Anim.fade(colors[i], intro), true, false);
            Ui.center(g, font, Component.translatable("mobslearn.journal.stat." + labels[i]), cx + cw / 2, cy + 30, Anim.fade(DIM, intro));
            if (i == 1) {
                int bx = cx + 8, bw = cw - 16;
                g.fill(bx, cy + ch - 5, bx + bw, cy + ch - 3, 0x30FFFFFF);
                Ui.fill(g, bx, cy + ch - 5, bw * k.intelligence() / 100f * intro, 2, Ui.GREEN);
            }
        }
        y += ((3 + cols) / cols) * (ch + gap) + 6;

        List<Skill> soon = new ArrayList<>();
        for (Skill s : Skill.ALL) {
            if (s.group() == MobGroup.WORLD || !k.allows(s) || k.level(s) >= s.maxLevel() || k.points(s) <= 0) continue;
            soon.add(s);
        }
        soon.sort((a, b) -> Double.compare(progress(k, b), progress(k, a)));
        if (soon.size() > 4) soon = soon.subList(0, 4);
        com.rrm.mobslearn.client.Ui.text(g, font, Component.translatable("mobslearn.journal.soon"), ix, y, Ui.GOLD, false);
        y += 13;
        if (soon.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable("mobslearn.journal.soon.empty"), iw)) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, ix, y, FAINT, false);
                y += 11;
            }
            y += 4;
        } else {
            int scols = iw >= 360 ? 2 : 1, sw = (iw - gap * (scols - 1)) / scols, sh = 34;
            for (int i = 0; i < soon.size(); i++) {
                Skill s = soon.get(i);
                int cx = ix + (i % scols) * (sw + gap), cy = y + (i / scols) * (sh + gap);
                g.fill(cx, cy, cx + sw, cy + sh, CARD);
                Ui.border(g, cx, cy, sw, sh, 0x22FFFFFF);
                g.fill(cx, cy, cx + 2, cy + sh, s.group().color);
                icon(g, s.iconId(), cx + 7, cy + 5, 24);
                int lvl = k.level(s);
                String name = font.plainSubstrByWidth(s.displayName().getString(), sw - 80);
                com.rrm.mobslearn.client.Ui.text(g, font, name, cx + 37, cy + 6, 0xFFFFFFFF, false);
                String next = "→ " + roman(lvl + 1);
                com.rrm.mobslearn.client.Ui.text(g, font, next, cx + sw - 8 - font.width(next), cy + 6, Ui.GOLD, false);
                float f = (float) progress(k, s);
                int bx = cx + 37, bw = sw - 45;
                g.fill(bx, cy + 21, bx + bw, cy + 24, 0x30FFFFFF);
                Ui.fill(g, bx, cy + 21, bw * f * intro, 3, s.group().color);
                com.rrm.mobslearn.client.Ui.text(g, font, Math.round(f * 100) + "%", bx, cy + 26 - 1, FAINT, false);
            }
            y += ((soon.size() + scols - 1) / scols) * (sh + gap) + 6;
        }

        Map<MobGroup, Integer> counts = new LinkedHashMap<>();
        for (Knowledge.JournalEntry e : k.journal) {
            Skill s = Skill.byId(e.skill());
            if (s != null) counts.merge(s.group(), 1, Integer::sum);
        }
        if (!counts.isEmpty()) {
            int cx = ix;
            List<MobGroup> order = new ArrayList<>();
            order.add(null);
            order.addAll(counts.keySet());
            for (MobGroup grp : order) {
                Component label = grp == null ? Component.translatable("mobslearn.journal.filter.all", k.journal.size())
                    : Component.literal(grp.displayName().getString() + " " + counts.get(grp));
                int lw = font.width(label) + (grp == null ? 12 : 26);
                if (cx + lw > ix + iw) {
                    cx = ix;
                    y += 18;
                }
                boolean sel = filter == grp;
                boolean over = inside && mx >= cx && mx < cx + lw && my >= y && my < y + 15;
                float hv = hot("chip" + (grp == null ? "all" : grp.key()), over || sel);
                int col = grp == null ? Ui.GREEN : grp.color;
                g.fill(cx, y, cx + lw, y + 15, Ui.mix(0x18FFFFFF, (0x50 << 24) | (col & 0xFFFFFF), hv));
                Ui.border(g, cx, y, lw, 15, sel ? col : Ui.mix(0x22FFFFFF, col, hv));
                if (grp != null) icon(g, grp.iconId, cx + 4, y + 1, 13);
                com.rrm.mobslearn.client.Ui.text(g, font, label, cx + (grp == null ? 6 : 20), y + 4, sel ? 0xFFFFFFFF : Ui.mix(DIM, 0xFFFFFFFF, hv), false);
                chipHits.add(new int[]{cx, y, lw, 15});
                chipGroups.add(grp);
                cx += lw + 4;
            }
            y += 24;
        }

        if (k.journal.isEmpty()) {
            g.fill(ix, y, ix + iw, y + 60, CARD);
            Ui.border(g, ix, y, iw, 60, 0x22FFFFFF);
            icon(g, "writable_book", ix + 12, y + 14, 32);
            int ty = y + 16;
            for (FormattedCharSequence line : font.split(Component.translatable("mobslearn.journal.empty"), iw - 64)) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, ix + 54, ty, DIM, false);
                ty += 11;
            }
            y += 70;
        }

        long day = today(), lastDay = Long.MIN_VALUE;
        int shown = 0;
        for (int i = k.journal.size() - 1; i >= 0; i--) {
            Knowledge.JournalEntry e = k.journal.get(i);
            Skill s = Skill.byId(e.skill());
            if (s == null || (filter != null && s.group() != filter)) continue;
            if (e.day() != lastDay) {
                lastDay = e.day();
                long ago = Math.max(0, day - e.day());
                Component when = ago == 0 ? Component.translatable("mobslearn.journal.today") : ago == 1 ? Component.translatable("mobslearn.journal.yesterday")
                    : Component.translatable("mobslearn.journal.ago", ago);
                Component pill = Component.translatable("mobslearn.journal.day", e.day());
                int pw = font.width(pill) + 12;
                g.fill(ix, y, ix + pw, y + 14, 0x40FFD25A);
                com.rrm.mobslearn.client.Ui.text(g, font, pill, ix + 6, y + 3, Ui.GOLD, false);
                com.rrm.mobslearn.client.Ui.text(g, font, when, ix + pw + 6, y + 3, FAINT, false);
                g.fill(ix + pw + 12 + font.width(when), y + 7, ix + iw, y + 8, 0x18FFFFFF);
                y += 20;
            }
            boolean world = s.group() == MobGroup.WORLD;
            Component head = world ? Component.translatable("mobslearn.journal.world", s.displayName())
                : Component.translatable("mobslearn.journal.learned", s.group().displayName(), s.displayName());
            Component why = world ? Component.translatable("mobslearn.journal.world_why")
                : Component.translatable("mobslearn.journal.because", s.trigger().describe());
            List<FormattedCharSequence> headL = font.split(head, iw - 90), whyL = font.split(why, iw - 50), descL = font.split(s.description(), iw - 50);
            int eh = 10 + headL.size() * 11 + whyL.size() * 10 + Math.min(2, descL.size()) * 10 + 10;
            boolean over = inside && mx >= ix && mx < ix + iw && my >= y && my < y + eh;
            float hv = hot("e" + i, over);
            int edge = s.absurd() ? PINK : s.group().color;
            g.fill(ix + 10, y, ix + iw, y + eh, Ui.mix(CARD, CARD_HOT, hv));
            Ui.border(g, ix + 10, y, iw - 10, eh, Ui.mix(0x1CFFFFFF, edge, hv));
            g.fill(ix + 3, y - 6, ix + 4, y + eh + 6, 0x30FFFFFF);
            g.fill(ix + 1, y + 10, ix + 6, y + 15, edge);
            icon(g, s.iconId(), ix + 16, y + 7, 24);
            int ty = y + 7;
            for (FormattedCharSequence line : headL) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, ix + 46, ty, 0xFFFFFFFF, false);
                ty += 11;
            }
            String lv = roman(e.level());
            int lw = font.width(lv) + 10;
            g.fill(ix + iw - 8 - lw, y + 6, ix + iw - 8, y + 18, 0x50FFD25A);
            com.rrm.mobslearn.client.Ui.text(g, font, lv, ix + iw - 3 - lw, y + 8, Ui.GOLD, false);
            for (FormattedCharSequence line : whyL) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, ix + 46, ty, TEAL, false);
                ty += 10;
            }
            for (int d = 0; d < Math.min(2, descL.size()); d++) {
                com.rrm.mobslearn.client.Ui.text(g, font, descL.get(d), ix + 46, ty, DIM, false);
                ty += 10;
            }
            y += eh + 6;
            shown++;
        }
        if (shown == 0 && !k.journal.isEmpty()) {
            com.rrm.mobslearn.client.Ui.text(g, font, Component.translatable("mobslearn.journal.none"), ix, y, FAINT, false);
            y += 14;
        }
        contentH = y + Math.round(scroll) - top + 10;
        target = Mth.clamp(target, 0, maxScroll());
        g.disableScissor();
        if (maxScroll() > 0) {
            float track = bottom - top - 8, bar = Math.max(18, track * (bottom - top) / Math.max(1, contentH));
            Ui.fill(g, x + w - 4, top + 4 + (track - bar) * scroll / maxScroll(), 2, bar, 0x60FFFFFF);
        }
        super.render(g, mx, my, partial);
    }

    private static double progress(Knowledge k, Skill s) {
        int lvl = k.level(s);
        if (lvl >= s.maxLevel()) return 1;
        int prev = lvl == 0 ? 0 : s.thresholds()[lvl - 1], next = s.thresholds()[lvl];
        return Mth.clamp((k.points(s) - prev) / Math.max(1, next - prev), 0, 1);
    }
}
