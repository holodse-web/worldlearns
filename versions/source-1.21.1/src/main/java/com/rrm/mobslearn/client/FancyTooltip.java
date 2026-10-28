package com.rrm.mobslearn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;

public final class FancyTooltip {
    public record Line(Component text, int color) {
    }

    public static final class Content {
        final Object key;
        final Component title;
        final int accent;
        final String icon;
        final List<Line> lines = new ArrayList<>();
        Component badge;
        int badgeColor;

        public Content(Object key, Component title, int accent) {
            this(key, title, accent, null);
        }

        public Content(Object key, Component title, int accent, String icon) {
            this.key = key;
            this.title = title;
            this.accent = accent;
            this.icon = icon;
        }

        public Content line(Component text, int color) {
            lines.add(new Line(text, color));
            return this;
        }

        public Content badge(Component text, int color) {
            this.badge = text;
            this.badgeColor = color;
            return this;
        }
    }

    public interface IconDrawer {
        void draw(GuiGraphics g, String id, int x, int y);
    }

    private Object key;
    private long since;
    private long lastFrame;
    private float lastX = Float.NaN, lastY;

    public void draw(GuiGraphics g, Font font, Content c, int mx, int my, int sw, int sh, IconDrawer icons) {
        if (c == null) {
            key = null;
            lastFrame = 0;
            lastX = Float.NaN;
            return;
        }
        long now = Util.getMillis();
        if (!Objects.equals(key, c.key)) {
            key = c.key;
            since = now;
        }
        float t = Mth.clamp((now - since) / 160f, 0f, 1f);
        float p = 1f - (1f - t) * (1f - t) * (1f - t);

        int maxW = Math.min(210, sw - 24);
        int iconW = c.icon != null ? 22 : 0;
        List<FormattedCharSequence> title = font.split(c.title, maxW - iconW);
        int w = iconW;
        for (FormattedCharSequence s : title) w = Math.max(w, iconW + font.width(s));
        int badgeW = c.badge != null ? font.width(c.badge) + 8 : 0;
        if (badgeW > 0) w = Math.max(w, iconW + badgeW);
        List<List<FormattedCharSequence>> body = new ArrayList<>();
        for (Line l : c.lines) {
            List<FormattedCharSequence> split = font.split(l.text, maxW);
            body.add(split);
            for (FormattedCharSequence s : split) w = Math.max(w, font.width(s));
        }
        int headH = Math.max(title.size() * 10 + (badgeW > 0 ? 13 : 0), c.icon != null ? 18 : 0);
        int h = headH;
        for (List<FormattedCharSequence> b : body) h += 3 + b.size() * 10;
        if (!body.isEmpty()) h += 2;
        int pad = 7, bw = w + 2 * pad, bh = h + 2 * pad - 1;

        int x = mx + 12, y = my - 4;
        if (x + bw > sw - 4) x = mx - 12 - bw;
        if (x < 4) x = 4;
        if (y + bh > sh - 4) y = sh - 4 - bh;
        if (y < 4) y = 4;
        if (Float.isNaN(lastX) || t < 0.05f) {
            lastX = x;
            lastY = y;
        }
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float follow = 1f - (float) Math.exp(-dt * 28f);
        lastX += (x - lastX) * follow;
        lastY += (y - lastY) * follow;
        int dx = 0, dy = 0;

        g.pose().translate(0, 0, 400);
        g.pose().pushPose();
        g.pose().translate(lastX, lastY + (1f - p) * 5f, 0);
        g.fill(dx + 2, dy + 2, dx + bw + 2, dy + bh + 2, fade(0x60000000, p));
        g.fillGradient(dx, dy, dx + bw, dy + bh, fade(0xF6141C13, p), fade(0xF60C110C, p));
        Ui.border(g, dx, dy, bw, bh, fade(0x55000000 | (c.accent & 0xFFFFFF), p));
        g.fill(dx + 1, dy, dx + bw - 1, dy + 2, fade(c.accent, p));

        int cy = dy + pad + 1;
        int tx = dx + pad + iconW;
        if (c.icon != null) {
            g.fill(dx + pad - 1, cy - 1, dx + pad + 17, cy + 17, fade(0x40000000, p));
            if (p > 0.5f && icons != null) icons.draw(g, c.icon, dx + pad, cy);
        }
        int ty = cy + (c.icon != null && title.size() == 1 && badgeW == 0 ? 4 : 0);
        for (FormattedCharSequence s : title) {
            com.rrm.mobslearn.client.Ui.text(g, font, s, tx, ty, fade(0xFFFFFFFF, p), true);
            ty += 10;
        }
        if (badgeW > 0) {
            g.fill(tx, ty, tx + badgeW, ty + 11, fade(0x38000000 | (c.badgeColor & 0xFFFFFF), p));
            com.rrm.mobslearn.client.Ui.text(g, font, c.badge, tx + 4, ty + 2, fade(c.badgeColor, p), false);
        }
        cy += headH;
        for (int i = 0; i < body.size(); i++) {
            cy += 3;
            if (i == 0) {
                g.fill(dx + pad, cy - 2, dx + bw - pad, cy - 1, fade(0x18FFFFFF, p));
                cy += 2;
            }
            for (FormattedCharSequence s : body.get(i)) {
                com.rrm.mobslearn.client.Ui.text(g, font, s, dx + pad, cy, fade(c.lines.get(i).color, p), false);
                cy += 10;
            }
        }
        g.pose().popPose();
    }

    private static int fade(int argb, float alpha) {
        int base = (argb >>> 24) == 0 ? 0xFF : (argb >>> 24);
        int al = Math.round(base * Mth.clamp(alpha, 0f, 1f));
        if (al < 8) al = 8;
        return (al << 24) | (argb & 0xFFFFFF);
    }
}
