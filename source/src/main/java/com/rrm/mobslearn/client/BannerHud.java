package com.rrm.mobslearn.client;

import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public final class BannerHud {
    private static final long IN = 350, SHOW = 4200, OUT = 450;
    private record Banner(Skill skill, int level) {
    }

    private static final Deque<Banner> queue = new ArrayDeque<>();
    private static Banner current;
    private static long shownAt;
    private static MobGroup lastGroup;
    private static long lastUntil;

    public static int occupied() {
        if (current == null) return 0;
        long age = Util.getMillis() - shownAt;
        return age > IN + SHOW + OUT * 0.5 ? 0 : 52;
    }

    public static MobGroup recent() {
        long now = Util.getMillis();
        if (current != null && current.skill.group() != MobGroup.WORLD) return current.skill.group();
        return lastGroup != null && now <= lastUntil ? lastGroup : null;
    }

    private BannerHud() {
    }

    public static void push(Skill skill, int level) {
        queue.add(new Banner(skill, level));
    }

    public static void clear() {
        queue.clear();
        current = null;
    }

    private static void playSound(Banner b) {
        Minecraft mc = Minecraft.getInstance();
        if (b.skill.group() == MobGroup.WORLD) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f));
        } else if (b.level >= 3) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f, 0.6f));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + 0.2f * b.level, 1.0f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 0.4f));
        }
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (current == null) {
            if (queue.isEmpty()) return;
            current = queue.poll();
            shownAt = now;
            playSound(current);
        }
        long age = now - shownAt;
        if (age > IN + SHOW + OUT) {
            if (current.skill.group() != MobGroup.WORLD) {
                lastGroup = current.skill.group();
                lastUntil = now + 5000;
            }
            current = null;
            return;
        }
        if (mc.gui.hud.isHidden()) return;
        float slide = age < IN ? ease(age / (float) IN) : 1f;
        float alpha = age > IN + SHOW ? 1f - (age - IN - SHOW) / (float) OUT : Math.min(1f, age / (float) IN * 1.5f);
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha <= 0.02f) return;

        Font font = mc.font;
        Skill s = current.skill;
        MobGroup grp = s.group();
        int color = grp.color;
        Component title = grp == MobGroup.WORLD
            ? Component.translatable("mobslearn.banner.world")
            : Component.translatable("mobslearn.banner.title", grp.displayName().getString().toUpperCase(Locale.ROOT));
        Component name = s.displayName();
        int w = Math.max(200, Math.max(font.width(title), font.width(name) + 40) + 52);
        int h = 46;
        float out = age > IN + SHOW ? ease(Mth.clamp((age - IN - SHOW) / (float) OUT, 0f, 1f)) : 0f;
        float slideX = Math.round(-w + (w + 8) * slide - (w + 8) * out * 0.35f);
        int x = 0;
        int y = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cati") ? 56 : 8;
        g.pose().pushMatrix();
        g.pose().translate(slideX, 0);

        int a = Math.round(alpha * 255);
        g.fill(x, y, x + w, y + h, (Math.round(alpha * 0xE0) << 24) | 0x0E120D);
        Ui.border(g, x, y, w, h, (Math.round(alpha * 0x30) << 24) | 0xFFFFFF);
        g.fill(x, y, x + 3, y + h, (a << 24) | (color & 0xFFFFFF));

        g.fill(x + 8, y + 5, x + 44, y + 41, (Math.round(alpha * 0x40) << 24));
        g.pose().pushMatrix();
        g.pose().translate(x + 10, y + 7);
        g.pose().scale(2f, 2f);
        g.item(grp.icon(), 0, 0);
        g.pose().popMatrix();

        int tx = x + 50;
        g.text(font, title, tx, y + 7, (a << 24) | (color & 0xFFFFFF), false);
        g.text(font, name, tx, y + 19, (a << 24) | 0xFFFFFF, false);
        String pips = "●".repeat(current.level) + "○".repeat(Skill.MAX_LEVEL - current.level);
        g.text(font, pips, x + w - 10 - font.width(pips), y + 19, (a << 24) | 0xFFD25A, false);

        int intel = ClientKnowledge.get().intelligence();
        String label = Component.translatable("mobslearn.banner.intel", intel).getString();
        g.text(font, label, tx, y + 32, (Math.round(alpha * 0xA0) << 24) | 0xF6E7C1, false);
        int bx = tx + font.width(label) + 6, bw = x + w - 10 - bx;
        if (bw > 10) {
            g.fill(bx, y + 34, bx + bw, y + 37, (Math.round(alpha * 0x50) << 24));
            Ui.fill(g, bx, y + 34, bw * intel / 100f, 3, (a << 24) | 0x8BE03A);
        }
        g.pose().popMatrix();
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
