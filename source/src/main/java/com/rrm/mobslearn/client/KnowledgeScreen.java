package com.rrm.mobslearn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public class KnowledgeScreen extends Screen {
    private static final int CARD = 0xFF121A11, CARD_HOT = 0xFF1A261A;
    private static final int TEXT = 0xFFF6E7C1, DIM = 0xFFA8A08A, FAINT = 0xFF6E6A5C, ACCENT = 0xFF8BE03A,
        PINK = 0xFFFF7AD9, GOLD = 0xFFFFD25A, RED = 0xFFFF6A5A, TEAL = 0xFF7FC8BE;
    private static final int TOP_H = 66, ROW_H = 24, HEAD_H = 18, CARD_H = 36, GAP = 6;

    private static MobGroup selected = MobGroup.ZOMBIE;

    private final Screen parent;
    private final FancyTooltip tooltip = new FancyTooltip();
    private FancyTooltip.Content hoverTip;
    private final List<float[]> pixels = new ArrayList<>();
    private final Map<Object, Float> hover = new HashMap<>();
    private long lastFrame, openedAt, switchedAt;
    private float dt;
    private int mouseX, mouseY;

    private int sideX, sideY, sideW, sideH, mainX, mainY, mainW, mainH;
    private boolean focusRecent;
    private float sideScroll, sideTarget, mainScroll, mainTarget, selY = Float.NaN, intelShown;
    private int cols, cardW, mainContentH;
    private final List<Entry> side = new ArrayList<>();
    private final List<CardRect> cardRects = new ArrayList<>();
    private int sideContentH;

    private Skill detail, shownDetail;
    private float detailAnim;
    private boolean help;
    private float helpAnim;
    private int[] modalRect = new int[4];
    private int[] helpOkRect = new int[4];
    private int[] closeRect = new int[4];

    private record Entry(MobGroup group, String header, int y, int h) {
    }

    private record CardRect(Skill skill, int x, float y, int w, int h) {
    }

    public KnowledgeScreen(Screen parent) {
        super(Component.translatable("mobslearn.screen.title"));
        this.parent = parent;
        RandomSource r = RandomSource.create(7);
        for (int i = 0; i < 50; i++) {
            pixels.add(new float[]{r.nextFloat(), r.nextFloat(), 0.004f + r.nextFloat() * 0.01f, 1 + r.nextInt(2), r.nextFloat()});
        }
    }

    @Override
    protected void init() {
        if (openedAt == 0) {
            openedAt = Util.getMillis();
            switchedAt = openedAt + 120;
            if (!helpSeen()) help = true;
            MobGroup recent = BannerHud.recent();
            if (recent != null) {
                selected = recent;
                focusRecent = true;
            }
        }
        sideW = this.width < 420 ? 104 : 128;
        sideX = 8;
        sideY = TOP_H + 6;
        sideH = this.height - sideY - 8;
        mainX = sideX + sideW + 6;
        mainY = sideY;
        mainW = this.width - mainX - 8;
        mainH = sideH;
        cols = Math.max(1, Math.min(4, (mainW - 16 + GAP) / (150 + GAP)));
        cardW = (mainW - 16 - (cols - 1) * GAP) / cols;
        buildSide();
        if (focusRecent) {
            focusRecent = false;
            for (Entry e : side) {
                if (e.group == selected && e.y + e.h > sideH - 8) {
                    sideTarget = Math.min(maxSide(), e.y + e.h - sideH + 16);
                    sideScroll = sideTarget;
                }
            }
        }

        int bw = 52;
        addRenderableWidget(new MlButton(this.width - 8 - bw, 8, bw, 18, Component.translatable("mobslearn.screen.wiki"),
            b -> this.minecraft.gui.setScreen(new com.rrm.mobslearn.client.wiki.WikiScreen(this))));
        addRenderableWidget(new MlButton(this.width - 8 - bw - 4 - 18, 8, 18, 18, Component.literal("?"), b -> openHelp()));
        addRenderableWidget(new LanguageButton(this.width - 112, 8, 24, 18,
            () -> this.minecraft.gui.setScreen(new LanguageScreen(this))));
        addRenderableWidget(new GearButton(this.width - 136, 8, 18, 18, () -> this.minecraft.gui.setScreen(new SettingsScreen(this))));
        addRenderableWidget(new MlButton(this.width - 88, 42, 80, 18, Component.translatable("mobslearn.journal.title"),
            b -> this.minecraft.gui.setScreen(new JournalScreen(this))));
        addRenderableWidget(new MlButton(this.width - 172, 42, 80, 18, Component.translatable("mobslearn.settlements.title"),
            b -> this.minecraft.gui.setScreen(new SettlementsScreen(this))));
    }

    private void buildSide() {
        side.clear();
        int y = 0;
        side.add(new Entry(null, "mobslearn.screen.cat.world", y, HEAD_H));
        y += HEAD_H;
        side.add(new Entry(MobGroup.WORLD, null, y, ROW_H));
        y += ROW_H + 4;
        side.add(new Entry(null, "mobslearn.screen.cat.hostile", y, HEAD_H));
        y += HEAD_H;
        for (MobGroup g : MobGroup.values()) {
            if (g.hostile) {
                side.add(new Entry(g, null, y, ROW_H));
                y += ROW_H;
            }
        }
        y += 4;
        side.add(new Entry(null, "mobslearn.screen.cat.peaceful", y, HEAD_H));
        y += HEAD_H;
        for (MobGroup g : MobGroup.values()) {
            if (!g.hostile && g != MobGroup.WORLD) {
                side.add(new Entry(g, null, y, ROW_H));
                y += ROW_H;
            }
        }
        sideContentH = y + 8;
    }

    private List<MobGroup> groupOrder() {
        List<MobGroup> l = new ArrayList<>();
        for (Entry e : side) if (e.group != null) l.add(e.group);
        return l;
    }

    private void select(MobGroup g) {
        if (g == selected) return;
        selected = g;
        switchedAt = Util.getMillis();
        mainScroll = mainTarget = 0;
        sound(1.25f);
        for (Entry e : side) {
            if (e.group == g) {
                if (e.y < sideTarget) sideTarget = Math.max(0, e.y - HEAD_H);
                else if (e.y + e.h > sideTarget + sideH - 8) sideTarget = Math.min(maxSide(), e.y + e.h - sideH + 16);
            }
        }
    }

    private int maxSide() {
        return Math.max(0, sideContentH - sideH + 4);
    }

    private int maxMain() {
        return Math.max(0, mainContentH - mainH + 8);
    }

    private static Path helpFlag() {
        return FabricLoader.getInstance().getConfigDir().resolve("mobslearn-help-seen");
    }

    private static boolean helpSeen() {
        try {
            return Files.exists(helpFlag());
        } catch (Exception e) {
            return true;
        }
    }

    private void openHelp() {
        help = true;
        sound(1.1f);
    }

    private void closeHelp() {
        help = false;
        sound(1.0f);
        try {
            Files.createDirectories(helpFlag().getParent());
            if (!Files.exists(helpFlag())) Files.writeString(helpFlag(), "1");
        } catch (Exception ignored) {
        }
    }

    private boolean overlayOpen() {
        return help || detail != null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE && overlayOpen()) {
            if (help) closeHelp();
            else detail = null;
            return true;
        }
        if (MobsLearnClient.treeKey != null && MobsLearnClient.treeKey.matches(event)) {
            if (overlayOpen()) {
                if (help) closeHelp();
                else detail = null;
            } else onClose();
            return true;
        }
        if (!overlayOpen() && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)) {
            List<MobGroup> order = groupOrder();
            int i = order.indexOf(selected) + (key == InputConstants.KEY_DOWN ? 1 : -1);
            select(order.get(Math.floorMod(i, order.size())));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) event.x(), y = (int) event.y();
        if (help) {
            if (inRect(x, y, helpOkRect) || !inRect(x, y, modalRect)) closeHelp();
            return true;
        }
        if (detail != null) {
            if (inRect(x, y, closeRect) || !inRect(x, y, modalRect)) {
                detail = null;
                sound(1.0f);
            }
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (x >= sideX && x < sideX + sideW && y >= sideY && y < sideY + sideH) {
            for (Entry e : side) {
                float ry = sideY + 4 + e.y - sideScroll;
                if (e.group != null && y >= ry && y < ry + e.h) {
                    select(e.group);
                    return true;
                }
            }
        }
        if (x >= mainX && x < mainX + mainW && y >= mainY && y < mainY + mainH) {
            for (CardRect c : cardRects) {
                if (x >= c.x && x < c.x + c.w && y >= c.y && y < c.y + c.h) {
                    detail = shownDetail = c.skill;
                    detailAnim = 0f;
                    sound(1.4f);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx2, double sy2) {
        if (overlayOpen()) return true;
        if (x < mainX) sideTarget = Mth.clamp(sideTarget - (float) sy2 * 24f, 0, maxSide());
        else mainTarget = Mth.clamp(mainTarget - (float) sy2 * 30f, 0, maxMain());
        return true;
    }

    private static boolean inRect(int x, int y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private void sound(float pitch) {
        Ui.click(pitch);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fillGradient(0, 0, this.width, this.height, 0xE80E150D, 0xF40A0E0A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        mouseX = mx;
        mouseY = my;
        float k = 1f - (float) Math.exp(-dt * 12f);
        sideScroll += (sideTarget - sideScroll) * k;
        mainScroll += (mainTarget - mainScroll) * k;
        detailAnim += ((detail != null ? 1f : 0f) - detailAnim) * (1f - (float) Math.exp(-dt * 14f));
        helpAnim += ((help ? 1f : 0f) - helpAnim) * (1f - (float) Math.exp(-dt * 14f));
        if (detail == null && detailAnim < 0.02f) shownDetail = null;

        Knowledge kn = ClientKnowledge.get();
        hoverTip = null;
        float intro = ease(Mth.clamp((now - openedAt) / 450f, 0f, 1f));

        drawPixels(g, now);
        drawTop(g, kn, now);
        g.pose().pushMatrix();
        g.pose().translate(-(1f - intro) * 26f, 0);
        drawSide(g, kn, now);
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - intro) * 18f);
        drawMain(g, kn, now);
        g.pose().popMatrix();
        super.extractRenderState(g, mx, my, a);

        if (shownDetail != null && detailAnim > 0.02f) drawDetail(g, kn, shownDetail, ease(detailAnim));
        if (helpAnim > 0.02f) drawHelp(g, ease(helpAnim), now);
        tooltip.draw(g, this.font, overlayOpen() ? null : hoverTip, mx, my, this.width, this.height, (gg, id, x, y) -> gg.item(WikiIcons.stack(id), x, y));
    }

    private void drawPixels(GuiGraphicsExtractor g, long now) {
        float t = (now % 1_000_000L) / 1000f;
        for (float[] p : pixels) {
            float y = (p[1] - t * p[2] * 0.6f) % 1f;
            if (y < 0) y += 1f;
            float px = p[0] * this.width, py = y * this.height;
            int size = (int) p[3];
            int alpha = Math.round(0x14 + 0x24 * (0.5f + 0.5f * Mth.sin(t * 1.3f + p[4] * 6.28f)));
            int col = p[4] > 0.7f ? 0xF6E7C1 : 0x8BE03A;
            Ui.fill(g, px, py, size, size, (alpha << 24) | col);
        }
    }

    private void drawTop(GuiGraphicsExtractor g, Knowledge kn, long now) {
        g.fill(0, 0, this.width, TOP_H, 0x90080B08);
        g.fill(0, TOP_H - 1, this.width, TOP_H, 0x22FFFFFF);
        int logoH = 24, logoW = Math.round(logoH * (Ui.LOGO_W / (float) Ui.LOGO_H));
        Ui.logoAt(g, 10, 7, logoW, logoH);
        Component credit = Component.translatable("mobslearn.credit");
        g.text(this.font, credit, 10, 47, 0x90F6E7C1, false);

        boolean synced = ClientKnowledge.synced();
        int intel = synced ? kn.intelligence() : 0;
        float appear = ease(Mth.clamp((now - openedAt) / 1000f, 0f, 1f));
        intelShown += (intel * appear - intelShown) * (1f - (float) Math.exp(-dt * 8f));

        int cx = this.width / 2;
        int left = 10 + logoW + 12, right = this.width - 124;
        int bw = Math.max(80, Math.min(240, 2 * Math.min(cx - left, right - cx)));
        int bx = cx - bw / 2;
        Component label = Component.translatable("mobslearn.screen.intel");
        Component pct = Component.literal(" " + Math.round(intelShown) + "%");
        int labelW = this.font.width(label) + this.font.width(pct);
        float labelScale = Math.min(1f, bw / (float) Math.max(1, labelW));
        int lw = Math.round(labelW * labelScale);
        g.pose().pushMatrix();
        g.pose().translate(cx, 6);
        g.pose().scale(labelScale, labelScale);
        g.text(this.font, label, -labelW / 2, 0, DIM, false);
        g.text(this.font, pct, -labelW / 2 + this.font.width(label), 0, GOLD, false);
        g.pose().popMatrix();

        int barY = 19;
        g.fill(bx, barY, bx + bw, barY + 5, 0x40000000);
        g.fill(bx, barY, bx + bw, barY + 1, 0x18FFFFFF);
        float fw = bw * intelShown / 100f;
        Ui.fill(g, bx, barY, fw, 5, ACCENT);
        if (fw > 6) {
            float gx = bx + (now / 12f) % (fw + 40) - 20;
            if (gx > bx && gx < bx + fw - 2) Ui.fill(g, gx, barY, Math.min(bx + fw - gx, 3), 5, 0x60FFFFFF);
        }
        int nextPct = -1;
        Skill nextSkill = null;
        int nextLvl = 0;
        for (Skill s : Skill.of(MobGroup.WORLD)) {
            for (int l = 0; l < s.maxLevel(); l++) {
                int th = s.thresholds()[l];
                int tx = bx + Math.round(bw * th / 100f);
                boolean reached = intel >= th;
                g.fill(tx, barY - 2, tx + 1, barY + 7, reached ? GOLD : 0x50FFFFFF);
                if (!overlayOpen() && mouseX >= tx - 3 && mouseX <= tx + 3 && mouseY >= barY - 4 && mouseY <= barY + 9) {
                    hoverTip = new FancyTooltip.Content("tick" + s.id() + l, s.displayName(), GOLD, s.iconId())
                        .badge(Component.translatable("mobslearn.screen.intel_tip", l + 1, th), reached ? GOLD : DIM)
                        .line(s.description(), DIM)
                        .line(Component.translatable(reached ? "mobslearn.tip.unlocked" : "mobslearn.tip.locked"), reached ? ACCENT : FAINT);
                }
                if (!reached && (nextPct < 0 || th < nextPct)) {
                    nextPct = th;
                    nextSkill = s;
                    nextLvl = l + 1;
                }
            }
        }
        Component under;
        int underColor;
        if (!synced) {
            under = Component.translatable("mobslearn.screen.nosync");
            underColor = Ui.RED;
        } else if (nextSkill != null) {
            under = Component.translatable("mobslearn.screen.next_unlock", nextSkill.displayName(), nextLvl, nextPct);
            underColor = FAINT;
        } else {
            under = Component.translatable("mobslearn.screen.all_unlocked");
            underColor = GOLD;
        }
        String u = this.font.plainSubstrByWidth(under.getString(), Math.max(60, right - left));
        g.text(this.font, u, cx - this.font.width(u) / 2, 31, underColor, false);
        boolean labelHot = !overlayOpen() && mouseY >= 3 && mouseY < 17 && mouseX >= cx - lw / 2 - 3 && mouseX < cx + lw / 2 + 3;
        float lh = hoverAnim("intelLabel", labelHot);
        if (lh > 0.02f) Ui.fill(g, cx - lw / 2f * lh, 16, lw * lh, 1, fade(ACCENT, lh));
        if (hoverTip == null && labelHot) {
            hoverTip = new FancyTooltip.Content("intel", Component.translatable("mobslearn.screen.intel_title", intel), ACCENT, "enchanted_book")
                .line(Component.translatable("mobslearn.screen.intel_explain"), DIM);
        }
    }

    private void drawSide(GuiGraphicsExtractor g, Knowledge kn, long now) {
        g.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0x900C110C);
        Ui.border(g, sideX, sideY, sideW, sideH, 0x1EFFFFFF);
        g.enableScissor(sideX + 1, sideY + 1, sideX + sideW - 1, sideY + sideH - 1);
        int scroll = (int) Math.floor(sideScroll);
        g.pose().pushMatrix();
        g.pose().translate(0, scroll - sideScroll);
        for (Entry e : side) {
            if (e.group == selected) {
                float target = e.y;
                if (Float.isNaN(selY)) selY = target;
                selY += (target - selY) * (1f - (float) Math.exp(-dt * 16f));
                float hy = sideY + 4 + selY - scroll;
                Ui.fill(g, sideX + 3, hy, sideW - 6, ROW_H, 0x24FFFFFF);
                Ui.fill(g, sideX + 3, hy, 2, ROW_H, selected.color);
            }
        }
        for (Entry e : side) {
            int ry = sideY + 4 + e.y - scroll;
            if (ry > sideY + sideH || ry + e.h < sideY) continue;
            if (e.header != null) {
                g.text(this.font, Component.translatable(e.header), sideX + 9, ry + 7, FAINT, false);
                continue;
            }
            MobGroup grp = e.group;
            boolean sel = grp == selected;
            boolean hot = !overlayOpen() && mouseX >= sideX && mouseX < sideX + sideW && mouseY >= ry && mouseY < ry + ROW_H
                && mouseY >= sideY && mouseY < sideY + sideH;
            float hv = hoverAnim("side" + grp, hot && !sel);
            if (hv > 0.02f) g.fill(sideX + 3, ry, sideX + sideW - 3, ry + ROW_H, fade(0x14FFFFFF, hv));
            g.pose().pushMatrix();
            g.pose().translate(hv * 2f, 0);
            g.item(grp.icon(), sideX + 9, ry + 4);
            List<Skill> skills = Skill.of(grp);
            int learned = 0;
            for (Skill s : skills) if (kn.level(s) > 0) learned++;
            String cnt = learned + "/" + skills.size();
            int cw = this.font.width(cnt);
            String nm = this.font.plainSubstrByWidth(grp.displayName().getString(), sideW - 36 - cw - 6);
            g.text(this.font, nm, sideX + 29, ry + 4, sel ? 0xFFFFFFFF : (hot ? TEXT : 0xFFCFC6AC), false);
            g.pose().popMatrix();
            g.text(this.font, cnt, sideX + sideW - 7 - cw, ry + 4, learned > 0 ? grp.color : 0x50FFFFFF, false);
            int pw = sideW - 36;
            int levels = kn.groupLevels(grp), max = skills.size() * Skill.MAX_LEVEL;
            g.fill(sideX + 29, ry + 16, sideX + 29 + pw, ry + 18, 0x22FFFFFF);
            if (max > 0 && levels > 0) g.fill(sideX + 29, ry + 16, sideX + 29 + Math.max(1, Math.round(pw * levels / (float) max)), ry + 18, grp.color);
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (maxSide() > 0) {
            int track = sideH - 8;
            int bar = Math.max(16, track * sideH / sideContentH);
            float by = sideY + 4 + (track - bar) * (sideScroll / maxSide());
            Ui.fill(g, sideX + sideW - 3, by, 1, bar, 0x40FFFFFF);
        }
    }

    private void drawMain(GuiGraphicsExtractor g, Knowledge kn, long now) {
        g.fill(mainX, mainY, mainX + mainW, mainY + mainH, 0x900C110C);
        Ui.border(g, mainX, mainY, mainW, mainH, 0x1EFFFFFF);
        g.enableScissor(mainX + 1, mainY + 1, mainX + mainW - 1, mainY + mainH - 1);
        cardRects.clear();
        int scroll = (int) Math.floor(mainScroll);
        g.pose().pushMatrix();
        g.pose().translate(0, scroll - mainScroll);
        float headIn = ease(Mth.clamp((now - switchedAt) / 300f, 0f, 1f));
        int hx = mainX + 10, hy = mainY + 10 - scroll;
        MobGroup grp = selected;
        List<Skill> skills = Skill.of(grp);

        g.fill(hx, hy, hx + 34, hy + 34, fade(0xFF101710, headIn));
        Ui.border(g, hx, hy, 34, 34, fade(grp.color, headIn));
        int bob = Math.round(Mth.sin(now / 650f) * 1.2f);
        if (headIn > 0.5f) {
            g.pose().pushMatrix();
            g.pose().translate(hx + 1, hy + 1 + bob);
            g.pose().scale(2f, 2f);
            g.item(grp.icon(), 0, 0);
            g.pose().popMatrix();
        }
        int tx = hx + 44;
        big(g, Component.literal(grp.displayName().getString().toUpperCase(Locale.ROOT)), tx, hy + 2, 1.4f, fade(grp.color, headIn));
        int learned = 0;
        for (Skill s : skills) if (kn.level(s) > 0) learned++;
        Component sub = Component.translatable("mobslearn.screen.learned_skills", learned, skills.size());
        g.text(this.font, sub, tx, hy + 18, fade(DIM, headIn), false);
        int levels = kn.groupLevels(grp), maxLv = skills.size() * Skill.MAX_LEVEL;
        int pbx = tx + this.font.width(sub) + 8, pbw = Math.min(120, mainX + mainW - 14 - pbx);
        if (pbw > 20) {
            g.fill(pbx, hy + 21, pbx + pbw, hy + 23, fade(0x22FFFFFF, headIn));
            float pf = pbw * (maxLv == 0 ? 0 : levels / (float) maxLv) * headIn;
            Ui.fill(g, pbx, hy + 21, pf, 2, fade(grp.color, headIn));
        }
        int py = hy + 40;

        List<float[]> dim = new ArrayList<>();
        int gy = py;
        for (int i = 0; i < skills.size(); i++) {
            int cx = hx + (i % cols) * (cardW + GAP), cy = gy + (i / cols) * (CARD_H + GAP);
            drawCard(g, kn, skills.get(i), i, cx, cy, now, dim);
        }
        int rows = (skills.size() + cols - 1) / cols;
        mainContentH = (gy + scroll - mainY) + rows * (CARD_H + GAP) + 6;
        mainTarget = Mth.clamp(mainTarget, 0, maxMain());
        if (!dim.isEmpty()) {
            g.nextStratum();
            for (float[] d : dim) Ui.fill(g, d[0], d[1], 16, 16, 0xA0121A11);
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (maxMain() > 0) {
            int track = mainH - 8;
            int bar = Math.max(16, track * mainH / mainContentH);
            float by = mainY + 4 + (track - bar) * (mainScroll / maxMain());
            Ui.fill(g, mainX + mainW - 3, by, 1, bar, 0x40FFFFFF);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, Knowledge kn, Skill s, int i, int x, int y, long now, List<float[]> dim) {
        float appear = ease(Mth.clamp((now - switchedAt - i * 26L) / 320f, 0f, 1f));
        if (appear < 0.03f) return;
        int lvl = kn.level(s);
        boolean world = s.group() == MobGroup.WORLD;
        boolean inside = mouseY >= mainY && mouseY < mainY + mainH;
        boolean hot = !overlayOpen() && inside && mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H;
        float hv = hoverAnim(s, hot);
        float offset = (1f - appear) * 10f - hv * 2f;
        int cy = y;
        if (cy + offset > mainY + mainH || cy + offset + CARD_H < mainY) return;
        cardRects.add(new CardRect(s, x, cy + offset + (float) Math.floor(mainScroll) - mainScroll, cardW, CARD_H));
        int edge = s.absurd() ? PINK : s.combat() ? RED : selected.color;
        if (hot && hv > 0.5f) {
            FancyTooltip.Content tc = new FancyTooltip.Content(s, s.displayName(), edge, s.iconId())
                .badge(lvl > 0 ? Component.translatable("mobslearn.screen.lvl", lvl, s.maxLevel()) : Component.translatable("mobslearn.screen.not_learned"), lvl > 0 ? GOLD : FAINT)
                .line(s.description(), TEXT);
            Component how = world
                ? Component.translatable("mobslearn.screen.how_world", s.nextThreshold(kn.intelligence()) > 0 ? s.nextThreshold(kn.intelligence()) : s.thresholds()[2])
                : Component.translatable("mobslearn.screen.how", s.trigger().describe());
            tc.line(how, TEAL);
            if (s.combat()) tc.line(Component.literal("⚔ ").append(Component.translatable("mobslearn.help.legend.pvp")), RED);
            if (s.absurd()) tc.line(Component.literal("✦ ").append(Component.translatable("mobslearn.help.legend.absurd")), PINK);
            tc.line(Component.translatable("mobslearn.tip.click"), FAINT);
            hoverTip = tc;
        }

        g.pose().pushMatrix();
        g.pose().translate(0, offset);
        g.fill(x, cy, x + cardW, cy + CARD_H, fade(lerpColor(CARD, CARD_HOT, hv), appear));
        int border = lerpColor(lvl > 0 ? (0x70000000 | (edge & 0xFFFFFF)) : 0x20FFFFFF, edge, hv);
        Ui.border(g, x, cy, cardW, CARD_H, fade(border, appear));
        if (lvl > 0) g.fill(x, cy, x + 2, cy + CARD_H, fade(edge, appear));

        g.fill(x + 6, cy + 6, x + 26, cy + 26, fade(0x40000000, appear));
        if (appear > 0.5f) {
            g.item(s.icon(), x + 8, cy + 8);
            if (lvl == 0) dim.add(new float[]{x + 8, cy + 8 + offset});
        }
        if (appear < 0.15f) {
            g.pose().popMatrix();
            return;
        }

        int tagX = x + cardW - 6;
        if (s.combat()) {
            tagX -= this.font.width("⚔");
            g.text(this.font, "⚔", tagX, cy + 7, fade(RED, appear), false);
            tagX -= 3;
        }
        if (s.absurd()) {
            tagX -= this.font.width("✦");
            g.text(this.font, "✦", tagX, cy + 7, fade(PINK, appear), false);
            tagX -= 3;
        }
        String name = this.font.plainSubstrByWidth(s.displayName().getString(), tagX - (x + 32) - 2);
        g.text(this.font, name, x + 32, cy + 7, fade(lvl > 0 ? 0xFFFFFFFF : 0xFFB5B09E, appear), false);

        double pts = world ? kn.intelligence() : kn.points(s);
        int next = s.nextThreshold(pts);
        float f;
        if (next < 0) f = 1f;
        else {
            int prev = lvl == 0 ? 0 : s.thresholds()[lvl - 1];
            f = (float) Mth.clamp((pts - prev) / Math.max(1, next - prev), 0, 1);
        }
        for (int p = 1; p <= s.maxLevel(); p++) {
            int sx = x + 32 + (p - 1) * 12;
            g.fill(sx, cy + 20, sx + 10, cy + 23, fade(0x2CFFFFFF, appear));
            if (p <= lvl) g.fill(sx, cy + 20, sx + 10, cy + 23, fade(GOLD, appear));
            else if (p == lvl + 1 && f > 0) Ui.fill(g, sx, cy + 20, 10 * f * appear, 3, fade(0x90000000 | (edge & 0xFFFFFF), appear));
        }
        if (lvl > 0) g.text(this.font, Component.translatable("mobslearn.screen.lvl", lvl, s.maxLevel()), x + 32 + 3 * 12 + 3, cy + 18, fade(GOLD, appear), false);
        g.pose().popMatrix();
    }

    private void drawDetail(GuiGraphicsExtractor g, Knowledge kn, Skill s, float p) {
        g.nextStratum();
        g.fill(0, 0, this.width, this.height, fade(0xB4000000, p));
        int w = Math.min(310, this.width - 24);
        int h = detailBody(null, kn, s, 0, 0, w, p);
        int x = (this.width - w) / 2;
        int y = Math.max(6, (this.height - h) / 2);
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - p) * 14f);
        modalRect = new int[]{x, y, w, h};
        int edge = s.absurd() ? PINK : s.combat() ? RED : s.group().color;
        g.fill(x, y, x + w, y + h, fade(0xF4101610, p));
        Ui.border(g, x, y, w, h, fade(0x50000000 | (edge & 0xFFFFFF), p));
        g.fill(x, y, x + w, y + 2, fade(edge, p));
        detailBody(g, kn, s, x, y, w, p);
        g.pose().popMatrix();
    }

    private int detailBody(GuiGraphicsExtractor g, Knowledge kn, Skill s, int x, int y, int w, float p) {
        int lvl = kn.level(s);
        boolean world = s.group() == MobGroup.WORLD;
        int edge = s.absurd() ? PINK : s.combat() ? RED : s.group().color;
        int pad = 12, iw = w - 2 * pad;
        if (g != null) {
            g.fill(x + pad, y + pad, x + pad + 40, y + pad + 40, fade(0x50000000, p));
            Ui.border(g, x + pad, y + pad, 40, 40, fade(0x40FFFFFF, p));
            if (p > 0.5f) {
                g.pose().pushMatrix();
                g.pose().translate(x + pad + 4, y + pad + 4);
                g.pose().scale(2f, 2f);
                g.item(s.icon(), 0, 0);
                g.pose().popMatrix();
            }
            big(g, s.displayName(), x + pad + 50, y + pad + 2, 1.35f, fade(0xFFFFFFFF, p));
            int cx = x + pad + 50;
            Component grpName = s.group().displayName();
            g.text(this.font, grpName, cx, y + pad + 18, fade(s.group().color, p), false);
            cx += this.font.width(grpName) + 6;
            if (s.combat()) cx = chip(g, Component.translatable("mobslearn.wiki.tag.pvp"), cx, y + pad + 16, RED, p);
            if (s.absurd()) cx = chip(g, Component.translatable("mobslearn.wiki.tag.absurd"), cx, y + pad + 16, PINK, p);
            for (int i = 1; i <= s.maxLevel(); i++) {
                int sx = x + pad + 50 + (i - 1) * 16;
                g.fill(sx, y + pad + 31, sx + 13, y + pad + 35, fade(i <= lvl ? GOLD : 0x30FFFFFF, p));
            }
            Component lv = lvl > 0 ? Component.translatable("mobslearn.screen.lvl", lvl, s.maxLevel()) : Component.translatable("mobslearn.screen.not_learned");
            g.text(this.font, lv, x + pad + 50 + 3 * 16 + 4, y + pad + 29, fade(lvl > 0 ? GOLD : FAINT, p), false);
            boolean hc = mouseX >= x + w - 18 && mouseX < x + w - 4 && mouseY >= y + 4 && mouseY < y + 18;
            closeRect = new int[]{x + w - 18, y + 4, 14, 14};
            g.text(this.font, "✕", x + w - 14, y + 7, fade(hc ? 0xFFFFFFFF : DIM, p), false);
        }
        int cy = y + pad + 50;
        cy = para(g, s.description(), x + pad, cy, iw, TEXT, p);

        cy += 8;
        cy = kicker(g, Component.translatable("mobslearn.detail.how"), x + pad, cy, ACCENT, p);
        Component how = world ? Component.translatable("mobslearn.detail.how_world")
            : Component.translatable("mobslearn.detail.how_text", s.trigger().describe());
        cy = para(g, how, x + pad, cy, iw, TEAL, p);

        cy += 8;
        cy = kicker(g, Component.translatable("mobslearn.detail.levels"), x + pad, cy, ACCENT, p);
        double pts = world ? kn.intelligence() : kn.points(s);
        for (int i = 0; i < s.maxLevel(); i++) {
            int th = s.thresholds()[i];
            boolean done = lvl > i;
            boolean current = lvl == i;
            if (g != null) {
                if (current) g.fill(x + pad - 4, cy - 2, x + w - pad + 4, cy + 20, fade(0x10FFFFFF, p));
                g.text(this.font, done ? "●" : "○", x + pad, cy, fade(done ? GOLD : FAINT, p), false);
                g.text(this.font, Component.translatable("mobslearn.screen.lvl_n", i + 1), x + pad + 12, cy, fade(done ? 0xFFFFFFFF : DIM, p), false);
                Component need = world ? Component.translatable("mobslearn.detail.need_world", th) : Component.translatable("mobslearn.detail.need", th);
                Component right = done ? Component.literal("✔").withStyle(ChatFormatting.GREEN) : need;
                int rw = this.font.width(right);
                if (done) g.text(this.font, need, x + w - pad - rw - 6 - this.font.width(need), cy, fade(FAINT, p), false);
                g.text(this.font, right, x + w - pad - rw, cy, fade(done ? ACCENT : DIM, p), false);
                if (current) {
                    int prev = i == 0 ? 0 : s.thresholds()[i - 1];
                    float f = (float) Mth.clamp((pts - prev) / Math.max(1, th - prev), 0, 1);
                    int bx = x + pad + 12, bw = iw - 12 - 70;
                    g.fill(bx, cy + 12, bx + bw, cy + 15, fade(0x26FFFFFF, p));
                    Ui.fill(g, bx, cy + 12, bw * f * p, 3, fade(edge, p));
                    Component now = world ? Component.translatable("mobslearn.detail.now_world", (int) pts, th)
                        : Component.translatable("mobslearn.detail.now", (int) Math.floor(pts), th);
                    g.text(this.font, now, x + w - pad - this.font.width(now), cy + 10, fade(FAINT, p), false);
                }
            }
            cy += current ? 22 : 13;
        }

        cy += 6;
        cy = kicker(g, Component.translatable("mobslearn.detail.status"), x + pad, cy, ACCENT, p);
        Component status;
        if (lvl == 0) status = Component.translatable("mobslearn.detail.status.none");
        else if (world) status = Component.translatable("mobslearn.detail.status.world", lvl);
        else status = Component.translatable("mobslearn.detail.status.on", lvl, Math.round(Skill.SPAWN_CHANCE[lvl] * 100));
        cy = para(g, status, x + pad, cy, iw, lvl > 0 ? 0xFFFFFFFF : DIM, p);
        if (s.combat()) cy = para(g, Component.translatable("mobslearn.detail.combat"), x + pad, cy + 4, iw, 0xFFFF9A8A, p);
        if (s.absurd()) cy = para(g, Component.translatable("mobslearn.detail.absurd"), x + pad, cy + 4, iw, 0xFFFFA8E6, p);

        cy += 10;
        if (g != null) {
            Component close = Component.translatable("mobslearn.detail.close");
            g.text(this.font, close, x + (w - this.font.width(close)) / 2, cy, fade(FAINT, p), false);
        }
        cy += 10 + pad - 2;
        return cy - y;
    }

    private void drawHelp(GuiGraphicsExtractor g, float p, long now) {
        g.nextStratum();
        g.fill(0, 0, this.width, this.height, fade(0xC0000000, p));
        int w = Math.min(360, this.width - 24);
        int h = helpBody(null, 0, 0, w, p, now);
        int x = (this.width - w) / 2;
        int y = Math.max(6, (this.height - h) / 2);
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - p) * 14f);
        modalRect = new int[]{x, y, w, h};
        g.fill(x, y, x + w, y + h, fade(0xF6101610, p));
        Ui.border(g, x, y, w, h, fade(0x30FFFFFF, p));
        g.fill(x, y, x + w, y + 2, fade(ACCENT, p));
        helpBody(g, x, y, w, p, now);
        g.pose().popMatrix();
    }

    private int helpBody(GuiGraphicsExtractor g, int x, int y, int w, float p, long now) {
        int pad = 14, iw = w - 2 * pad;
        int cy = y + pad;
        if (g != null) big(g, Component.translatable("mobslearn.help.title"), x + w / 2, cy, 1.4f, fade(0xFFFFFFFF, p), true);
        cy += 16;
        cy = paraCentered(g, Component.translatable("mobslearn.help.sub"), x + pad, cy, iw, DIM, p) + 8;
        String[] icons = {"spyglass", "experience_bottle", "zombie_head", "enchanted_book"};
        for (int i = 0; i < 4; i++) {
            float local = ease(Mth.clamp(p * 1.4f - i * 0.12f, 0f, 1f));
            int top = cy;
            int textX = x + pad + 30, textW = iw - 30;
            int ty = top + 1;
            if (g != null) {
                g.fill(x + pad, top, x + pad + 22, top + 22, fade(0x248BE03A, local));
                Ui.border(g, x + pad, top, 22, 22, fade(0x608BE03A, local));
                if (local > 0.5f) g.item(WikiIcons.stack(icons[i]), x + pad + 3, top + 3);
                g.text(this.font, Component.literal(String.valueOf(i + 1)), x + pad + 23 - 6, top + 15, fade(ACCENT, local), true);
                g.text(this.font, Component.translatable("mobslearn.help." + (i + 1) + ".title"), textX, ty, fade(0xFFFFFFFF, local), false);
            }
            ty += 11;
            ty = para(g, Component.translatable("mobslearn.help." + (i + 1) + ".text"), textX, ty, textW, DIM, local);
            cy = Math.max(top + 24, ty) + 6;
        }
        cy += 2;
        if (g != null) g.fill(x + pad, cy, x + w - pad, cy + 1, fade(0x18FFFFFF, p));
        cy += 7;
        cy = kicker(g, Component.translatable("mobslearn.help.legend.title"), x + pad, cy, ACCENT, p);
        String[] sym = {"●●○", "⚔", "✦", "▸"};
        int[] symCol = {GOLD, RED, PINK, TEAL};
        String[] keys = {"levels", "pvp", "absurd", "how"};
        int colW = iw / 2;
        for (int i = 0; i < 4; i++) {
            int lx = x + pad + (i % 2) * colW, ly = cy + (i / 2) * 12;
            if (g != null) {
                g.text(this.font, sym[i], lx, ly, fade(symCol[i], p), false);
                Component d = Component.translatable("mobslearn.help.legend." + keys[i]);
                g.text(this.font, this.font.plainSubstrByWidth(d.getString(), colW - 26), lx + 22, ly, fade(DIM, p), false);
            }
        }
        cy += 26;
        int bw = 110, bh = 20, bx = x + (w - bw) / 2;
        if (g != null) {
            boolean hot = mouseX >= bx && mouseX < bx + bw && mouseY >= cy && mouseY < cy + bh;
            float hv = hoverAnim("helpOk", hot);
            g.fill(bx, cy, bx + bw, cy + bh, fade(lerpColor(0xFF4E9A1E, 0xFF62B828, hv), p));
            Component ok = Component.translatable("mobslearn.help.ok");
            g.text(this.font, ok, bx + (bw - this.font.width(ok)) / 2, cy + 6, fade(0xFFFFFFFF, p), false);
            helpOkRect = new int[]{bx, cy, bw, bh};
        }
        cy += bh + pad;
        return cy - y;
    }

    private int chip(GuiGraphicsExtractor g, Component c, int x, int y, int color, float p) {
        int w = this.font.width(c) + 6;
        g.fill(x, y, x + w, y + 11, fade(0x40000000 | (color & 0xFFFFFF), p));
        g.text(this.font, c, x + 3, y + 2, fade(color, p), false);
        return x + w + 4;
    }

    private int kicker(GuiGraphicsExtractor g, Component c, int x, int y, int color, float p) {
        if (g != null) {
            g.fill(x, y + 3, x + 2, y + 8, fade(color, p));
            g.text(this.font, c, x + 6, y + 1, fade(color, p), false);
        }
        return y + 13;
    }

    private int para(GuiGraphicsExtractor g, Component c, int x, int y, int w, int color, float p) {
        for (FormattedCharSequence line : this.font.split(c, w)) {
            if (g != null) g.text(this.font, line, x, y, fade(color, p), false);
            y += 10;
        }
        return y;
    }

    private int paraCentered(GuiGraphicsExtractor g, Component c, int x, int y, int w, int color, float p) {
        for (FormattedCharSequence line : this.font.split(c, w)) {
            if (g != null) g.text(this.font, line, x + (w - this.font.width(line)) / 2, y, fade(color, p), false);
            y += 10;
        }
        return y;
    }

    private void big(GuiGraphicsExtractor g, Component c, int x, int y, float scale, int color) {
        big(g, c, x, y, scale, color, false);
    }

    private void big(GuiGraphicsExtractor g, Component c, int x, int y, float scale, int color, boolean center) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(this.font, c, center ? -this.font.width(c) / 2 : 0, 0, color, false);
        g.pose().popMatrix();
    }

    private float hoverAnim(Object key, boolean hot) {
        float v = hover.getOrDefault(key, 0f);
        v += ((hot ? 1f : 0f) - v) * (1f - (float) Math.exp(-dt * 12f));
        hover.put(key, v);
        return v;
    }

    private static float ease(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    private static int fade(int argb, float alpha) {
        int base = (argb >>> 24) == 0 ? 0xFF : (argb >>> 24);
        int al = Math.round(base * Mth.clamp(alpha, 0f, 1f));
        if (al < 8) al = 8;
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(a1 + (a2 - a1) * t) << 24) | (Math.round(r1 + (r2 - r1) * t) << 16)
            | (Math.round(g1 + (g2 - g1) * t) << 8) | Math.round(b1 + (b2 - b1) * t);
    }

    private static final class WikiIcons {
        static net.minecraft.world.item.ItemStack stack(String id) {
            return com.rrm.mobslearn.client.wiki.WikiScreen.stack(id);
        }
    }
}
