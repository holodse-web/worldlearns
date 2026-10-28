package com.rrm.mobslearn.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NoticeHud {
    private static final long IN = 320, OUT = 420;
    private static final int WIDTH = 236;

    private record Style(String kicker, Item icon, int color) {
    }

    private record Notice(Component text, Style style) {
    }

    private static final Deque<Notice> queue = new ArrayDeque<>();
    private static Notice current;
    private static long shownAt, showFor;
    private static String lastKey = "";
    private static long lastPushed;
    private static float offset;
    private static long lastFrame;

    private NoticeHud() {
    }

    public static void clear() {
        queue.clear();
        current = null;
    }

    private static String keyOf(Component c) {
        if (!(c.getContents() instanceof TranslatableContents tc)) return c.getString();
        String key = tc.getKey();
        for (Object arg : tc.getArgs()) {
            if (arg instanceof Component ac && ac.getContents() instanceof TranslatableContents at && at.getKey().startsWith("mobslearn.settlements.kind.")) {
                return key + "." + at.getKey().substring("mobslearn.settlements.kind.".length());
            }
        }
        return key;
    }

    private static Style styleOf(String key) {
        boolean colony = key.startsWith("mobslearn.colony.") || key.startsWith("mobslearn.camp.");
        if (colony && !key.contains("raid") && !key.contains("thief") && !key.contains("fell") && !key.contains("gloom")) {
            if (key.endsWith(".zombie")) return new Style("mobslearn.notice.settlement", Items.ZOMBIE_HEAD, 0xFF8BC24A);
            if (key.endsWith(".skeleton")) return new Style("mobslearn.notice.settlement", Items.SKELETON_SKULL, 0xFFD8D4C4);
            if (key.endsWith(".creeper")) return new Style("mobslearn.notice.settlement", Items.CREEPER_HEAD, 0xFF6FD26F);
            if (key.endsWith(".village") || key.endsWith(".camp") || key.endsWith(".villager")) return new Style("mobslearn.notice.village", Items.BELL, 0xFF7FC8BE);
        }
        if (key.contains("raid") || key.contains("thief") || key.contains("hostile") || key.contains("fall") || key.contains("gloom")) {
            return new Style("mobslearn.notice.alert", Items.IRON_SWORD, 0xFFE8726A);
        }
        if (key.contains("tier.zombie")) return new Style("mobslearn.notice.settlement", Items.ZOMBIE_HEAD, 0xFF8BC24A);
        if (key.contains("tier.skeleton")) return new Style("mobslearn.notice.settlement", Items.SKELETON_SKULL, 0xFFD8D4C4);
        if (key.contains("tier.creeper")) return new Style("mobslearn.notice.settlement", Items.CREEPER_HEAD, 0xFF6FD26F);
        if (key.contains("fair")) return new Style("mobslearn.notice.village", Items.EMERALD, Ui.GOLD);
        if (key.contains("hero") || key.contains("help_progress")) return new Style("mobslearn.notice.village", Items.TOTEM_OF_UNDYING, Ui.GOLD);
        if (key.contains("village") || key.contains("daughter") || key.contains("camp") || key.contains("request") || key.contains("ask.") || key.contains("tier.village")) {
            return new Style("mobslearn.notice.village", Items.BELL, 0xFF7FC8BE);
        }
        if (key.contains("trade")) return new Style("mobslearn.notice.village", Items.EMERALD, Ui.GOLD);
        if (key.contains("colony")) return new Style("mobslearn.notice.settlement", Items.BELL, 0xFFB8D86A);
        return new Style("mobslearn.notice.info", Items.WRITABLE_BOOK, Ui.GREEN);
    }

    private static final java.util.Map<String, java.util.Optional<net.minecraft.resources.Identifier>> bakedCache = new java.util.HashMap<>();

    private static net.minecraft.resources.Identifier baked(String id) {
        return bakedCache.computeIfAbsent(id, k -> {
            net.minecraft.resources.Identifier ident = com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + k + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(ident).isPresent() ? java.util.Optional.of(ident) : java.util.Optional.empty();
        }).orElse(null);
    }

    public static void push(Component text) {
        long now = Util.getMillis();
        String key = keyOf(text);
        if (key.equals(lastKey) && now - lastPushed < 4000) return;
        lastKey = key;
        lastPushed = now;
        if (queue.size() >= 5) queue.poll();
        queue.add(new Notice(text, styleOf(key)));
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int target = BannerHud.occupied();
        offset = Anim.approach(offset, target, dt, 9f);
        if (current == null) {
            if (queue.isEmpty()) return;
            current = queue.poll();
            shownAt = now;
            Font f = mc.font;
            int lines = f.split(current.text, WIDTH - 66).size();
            showFor = 2800 + 1100L * Math.min(lines, 4);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.7f));
        }
        long age = now - shownAt;
        if (age > IN + showFor + OUT) {
            current = null;
            return;
        }
        if (mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        Style st = current.style;
        List<FormattedCharSequence> lines = font.split(current.text, WIDTH - 66);
        if (lines.size() > 4) lines = lines.subList(0, 4);
        int h = Math.max(46, 26 + lines.size() * 10 + 6);
        float slide = age < IN ? Anim.ease(age / (float) IN) : 1f;
        float out = age > IN + showFor ? Anim.ease(Mth.clamp((age - IN - showFor) / (float) OUT, 0f, 1f)) : 0f;
        float alpha = Mth.clamp(age < IN ? slide : 1f - out, 0f, 1f);
        if (alpha <= 0.02f) return;
        int baseY = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cati") ? 56 : 8;
        int y = baseY + Math.round(offset);
        int w = WIDTH;
        int slideX = Math.round(-w + (w + 8) * slide - (w + 8) * out * 0.35f);
        int a = Math.round(alpha * 255);
        int color = st.color & 0xFFFFFF;

        g.pose().pushMatrix();
        g.pose().translate(slideX, 0);
        g.fill(0, y, w, y + h, (Math.round(alpha * 0xE0) << 24) | 0x0E120D);
        Ui.border(g, 0, y, w, h, (Math.round(alpha * 0x30) << 24) | 0xFFFFFF);
        g.fill(0, y, 3, y + h, (a << 24) | color);
        int iy = y + (h - 36) / 2;
        g.fill(8, iy, 44, iy + 36, (Math.round(alpha * 0x40) << 24));
        g.pose().pushMatrix();
        g.pose().translate(10, iy + 2);
        g.pose().scale(2f, 2f);
        net.minecraft.resources.Identifier baked = baked(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.icon).getPath());
        if (baked != null) g.blit(baked, 0, 0, 16, 16, 0f, 1f, 0f, 1f);
        else g.item(new ItemStack(st.icon), 0, 0);
        g.pose().popMatrix();
        int tx = 54;
        g.text(font, Component.translatable(st.kicker).getString().toUpperCase(Locale.ROOT), tx, y + 7, (a << 24) | color, false);
        int ly = y + 20;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, tx, ly, (a << 24) | 0xFFFFFF, false);
            ly += 10;
        }
        float life = Mth.clamp(1f - (age - IN) / (float) showFor, 0f, 1f);
        if (age >= IN) {
            g.fill(3, y + h - 2, 3 + Math.round((w - 3) * life), y + h, (Math.round(alpha * 0x90) << 24) | color);
        }
        g.pose().popMatrix();
    }
}
