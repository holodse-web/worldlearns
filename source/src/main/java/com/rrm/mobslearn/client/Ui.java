package com.rrm.mobslearn.client;

import com.rrm.mobslearn.MobsLearn;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class Ui {
    private static long lastClick;

    public static void click(float pitch) {
        long now = net.minecraft.util.Util.getMillis();
        if (now - lastClick < 150) return;
        lastClick = now;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    public static final Identifier BACKGROUND = MobsLearn.id("textures/gui/background.png");
    public static final Identifier LOGO = MobsLearn.id("textures/gui/logo.png");
    public static final int BG_W = 1920, BG_H = 1080;
    public static final int LOGO_W = 1024, LOGO_H = 428;

    public static final int OUTLINE = 0xFF07100A;
    public static final int PANEL = 0xD80E120D;
    public static final int PANEL_SOFT = 0xA00A0E0A;
    public static final int HAIRLINE = 0x26FFFFFF;
    public static final int GREEN = 0xFF8BE03A;
    public static final int GREEN_DARK = 0xFF3E7A1A;
    public static final int GREEN_DEEP = 0xFF1E3A12;
    public static final int CREAM = 0xFFF6E7C1;
    public static final int CREAM_DARK = 0xFFC9B68A;
    public static final int MUTED = 0xFFA8A08A;
    public static final int GOLD = 0xFFFFD25A;
    public static final int RED = 0xFFE5534B;

    private Ui() {
    }

    public static void fill(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        if (w <= 0f || h <= 0f) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(w, h);
        g.fill(0, 0, 1, 1, color);
        g.pose().popMatrix();
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    public static void background(GuiGraphicsExtractor g, int w, int h) {
        cover(g, BACKGROUND, BG_W, BG_H, 0, 0, w, h);
        g.fillGradient(0, h / 2, w, h, 0x00000000, 0x80000000);
    }

    public static int logo(GuiGraphicsExtractor g, int cx, int top, int width) {
        int height = Math.round(width * (LOGO_H / (float) LOGO_W));
        g.blit(LOGO, cx - width / 2, top, cx - width / 2 + width, top + height, 0f, 1f, 0f, 1f);
        return height;
    }

    public static void logoAt(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blit(LOGO, x, y, x + width, y + height, 0f, 1f, 0f, 1f);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, PANEL, HAIRLINE);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, fill);
        border(g, x, y, w, h, edge);
    }

    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
        progress = Math.max(0f, Math.min(1f, progress));
        g.fill(x, y, x + w, y + h, 0x50000000);
        fill(g, x, y, w * progress, h, color);
    }

    public static void scaledText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color, boolean centered) {
        scaledText(g, font, text, x, y, scale, color, centered, true);
    }

    public static void scaledText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color, boolean centered, boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int dx = centered ? -font.width(text) / 2 : 0;
        g.text(font, text, dx, 0, color, shadow);
        g.pose().popMatrix();
    }

    public static void scaledText(GuiGraphicsExtractor g, Font font, String text, int x, int y, float scale, int color, boolean centered) {
        scaledText(g, font, Component.literal(text), x, y, scale, color, centered);
    }

    public static void center(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void center(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void center(GuiGraphicsExtractor g, Font font, net.minecraft.util.FormattedCharSequence text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void outlinedTitle(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, float scale, int color) {
        scaledText(g, font, text, cx, y, scale, color, true);
    }

    public static void cover(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int x, int y, int w, int h) {
        float scale = Math.max(w / (float) texW, h / (float) texH);
        float vw = w / scale, vh = h / scale;
        float u0 = (texW - vw) / 2f / texW, u1 = 1f - u0;
        float v0 = (texH - vh) / 2f / texH, v1 = 1f - v0;
        g.blit(tex, x, y, x + w, y + h, u0, u1, v0, v1);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0x40000000);
    }

    public static String time(long ms, boolean tenths) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600, m = (totalSec / 60) % 60, s = totalSec % 60;
        String base = h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
        return tenths ? base + "." + (ms / 100) % 10 : base;
    }

    public static String stars(int n) {
        return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
    }
}
