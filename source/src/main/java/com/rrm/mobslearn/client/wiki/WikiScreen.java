package com.rrm.mobslearn.client.wiki;

import com.rrm.mobslearn.client.ClientKnowledge;
import com.rrm.mobslearn.client.LanguageButton;
import com.rrm.mobslearn.client.LanguageScreen;
import com.rrm.mobslearn.client.Ui;
import com.rrm.mobslearn.learn.Knowledge;
import com.rrm.mobslearn.learn.MobGroup;
import com.rrm.mobslearn.learn.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
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
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

public class WikiScreen extends Screen {
    static final int BG = 0xFF0A0E0A, BG2 = 0xFF0E150D, CARD = 0xFF121A11, CARD_HOT = 0xFF172316;
    static final int TEXT = 0xFFF6E7C1, DIM = 0xFFA8A08A, FAINT = 0xFF6E6A5C, ACCENT = 0xFF8BE03A, PINK = 0xFFFF7AD9, GOLD = 0xFFFFD25A;
    static final int NAV_H = 28;

    private final Screen parent;
    private MobGroup page;
    private final List<Block> blocks = new ArrayList<>();
    private final Map<String, Float> anchors = new LinkedHashMap<>();
    private final Map<Object, Float> hover = new HashMap<>();
    private float scroll, targetScroll;
    private float contentH;
    private int cw, cx0;
    private long lastFrame, pageAt;
    private final List<float[]> pixels = new ArrayList<>();
    private String pendingAnchor;
    private String copiedLine;
    private long copiedAt;
    private int mouseX, mouseY;
    private float dt;

    public WikiScreen(Screen parent) {
        super(Component.translatable("mobslearn.wiki.title"));
        this.parent = parent;
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 70; i++) {
            pixels.add(new float[]{r.nextFloat(), r.nextFloat(), 0.004f + r.nextFloat() * 0.012f, 1 + r.nextInt(3), r.nextFloat()});
        }
    }

    @Override
    protected void init() {
        cw = Math.min(this.width - 32, 470);
        cx0 = (this.width - cw) / 2;
        build();
        addRenderableWidget(new LanguageButton(this.width - 36, 4, 28, 20,
            () -> this.minecraft.gui.setScreen(new LanguageScreen(this))));
    }

    private void build() {
        blocks.clear();
        anchors.clear();
        if (page == null) buildHome();
        else buildMob(page);
        float y = NAV_H;
        for (Block b : blocks) {
            b.y = y;
            b.h = b.layout(cw);
            if (b.anchor != null) anchors.put(b.anchor, y);
            y += b.dynH();
        }
        contentH = y;
        if (pendingAnchor != null) {
            Float a = anchors.get(pendingAnchor);
            if (a != null) targetScroll = Mth.clamp(a - NAV_H - 8, 0, maxScroll());
            pendingAnchor = null;
        }
    }

    private void relayout() {
        float y = NAV_H;
        for (Block b : blocks) {
            b.animate(dt);
            b.y = y;
            if (b.anchor != null) anchors.put(b.anchor, y);
            y += b.dynH();
        }
        contentH = y;
        targetScroll = Mth.clamp(targetScroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (int) Math.ceil(contentH - this.height + 10));
    }

    private void openPage(MobGroup g) {
        page = g;
        pageAt = Util.getMillis();
        scroll = targetScroll = 0;
        build();
        clickSound(1.2f);
    }

    private void goAnchor(String anchor) {
        if (page != null) {
            page = null;
            pageAt = Util.getMillis();
            scroll = targetScroll = 0;
            pendingAnchor = anchor;
            build();
        } else {
            Float a = anchors.get(anchor);
            if (a != null) targetScroll = Mth.clamp(a - NAV_H - 8, 0, maxScroll());
        }
        clickSound(1.0f);
    }

    private void clickSound(float pitch) {
        Ui.click(pitch);
    }

    private void buildHome() {
        Knowledge k = ClientKnowledge.get();
        blocks.add(new Hero());
        blocks.add(new SectionHead("how", "mobslearn.wiki.how.kicker", "mobslearn.wiki.how.title", "mobslearn.wiki.how.sub"));
        blocks.add(new Steps());
        blocks.add(new LearningNotes());
        blocks.add(new Legend());
        blocks.add(new SectionHead("tips", "mobslearn.wiki.tips.kicker", "mobslearn.wiki.tips.title", "mobslearn.wiki.tips.sub"));
        blocks.add(new Tips());
        blocks.add(new SectionHead("mobs", "mobslearn.wiki.mobs.kicker", "mobslearn.wiki.mobs.title", "mobslearn.wiki.mobs.sub"));
        blocks.add(new MobGrid());
        blocks.add(new SectionHead("settle", "mobslearn.wiki.settle.kicker", "mobslearn.wiki.settle.title", "mobslearn.wiki.settle.sub"));
        blocks.add(new InfoCards("settle", new String[]{"bell", "hay_block", "lantern", "compass", "clock", "emerald"}, 0xFF6FBF4A));
        blocks.add(new SectionHead("villagers", "mobslearn.wiki.vill.kicker", "mobslearn.wiki.vill.title", "mobslearn.wiki.vill.sub"));
        blocks.add(new InfoCards("vill", new String[]{"emerald", "totem_of_undying", "iron_pickaxe", "jukebox", "goat_horn", "iron_block", "iron_sword", "iron_axe", "writable_book"}, 0xFFB3866A));
        blocks.add(new SectionHead("absurd", "mobslearn.wiki.absurd.kicker", "mobslearn.wiki.absurd.title", "mobslearn.wiki.absurd.sub"));
        blocks.add(new Marquee());
        blocks.add(new AbsurdCards());
        blocks.add(new SectionHead("intel", "mobslearn.wiki.intel.kicker", "mobslearn.wiki.intel.title", "mobslearn.wiki.intel.sub"));
        blocks.add(new IntelTimeline());
        blocks.add(new SectionHead("commands", "mobslearn.wiki.cmd.kicker", "mobslearn.wiki.cmd.title", "mobslearn.wiki.cmd.sub"));
        blocks.add(new Commands());
        blocks.add(new SectionHead("faq", "mobslearn.wiki.faq.kicker", "mobslearn.wiki.faq.title", null));
        for (int i = 0; i < 12; i++) blocks.add(new Faq(i));
        blocks.add(new Footer());
    }

    private void section(List<Skill> list, String key, int color) {
        if (list.isEmpty()) return;
        blocks.add(new SectionTitle(key, list.size(), color));
        for (int i = 0; i < list.size(); i += 2) {
            blocks.add(new SkillRow(i + 1 < list.size() ? List.of(list.get(i), list.get(i + 1)) : List.of(list.get(i)), i));
        }
    }

    private List<MobGroup> order() {
        List<MobGroup> l = new ArrayList<>();
        for (MobGroup g : MobGroup.values()) if (g.hostile) l.add(g);
        for (MobGroup g : MobGroup.values()) if (!g.hostile) l.add(g);
        return l;
    }

    private void buildMob(MobGroup g) {
        blocks.add(new MobHeader(g));
        List<Skill> core = new ArrayList<>(), combat = new ArrayList<>(), absurd = new ArrayList<>();
        for (Skill s : Skill.of(g)) {
            if (s.absurd()) absurd.add(s);
            else if (s.combat()) combat.add(s);
            else core.add(s);
        }
        section(core, "core", ACCENT);
        section(combat, "combat", 0xFFFF6A5A);
        section(absurd, "absurd", PINK);
        blocks.add(new Footer());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        targetScroll = Mth.clamp(targetScroll - (float) sy * 36f, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_SPACE) {
            targetScroll = Mth.clamp(targetScroll + this.height * 0.8f, 0, maxScroll());
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP) {
            targetScroll = Mth.clamp(targetScroll - this.height * 0.8f, 0, maxScroll());
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            targetScroll = 0;
            return true;
        }
        if (key == InputConstants.KEY_END) {
            targetScroll = maxScroll();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE && page != null) {
            goAnchor("mobs");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int x = (int) event.x(), y = (int) event.y();
        if (y < NAV_H) {
            for (NavLink l : navLinks) {
                if (x >= l.x && x < l.x + l.w) {
                    goAnchor(l.anchor);
                    return true;
                }
            }
            if (x < 80) {
                goAnchor("top");
                return true;
            }
            return false;
        }
        for (Block b : blocks) {
            int sy = (int) Math.floor(b.y - scroll);
            if (y >= sy && y < sy + b.dynH() && b.click(x, y, cx0, sy, cw)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fillGradient(0, 0, this.width, this.height, BG2, BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        mouseX = mx;
        mouseY = my;
        scroll += (targetScroll - scroll) * (1f - (float) Math.exp(-dt * 12f));
        if (Math.abs(targetScroll - scroll) < 0.001f) scroll = targetScroll;

        drawPixels(g, now);

        relayout();
        float pageFade = Mth.clamp((now - pageAt) / 260f, 0f, 1f);
        g.enableScissor(0, NAV_H, this.width, this.height);
        for (Block b : blocks) {
            int sy = (int) Math.floor(b.y - scroll);
            if (sy > this.height || sy + b.dynH() < NAV_H) continue;
            if (b.revealAt < 0) b.revealAt = now + (long) Math.max(0, (sy - NAV_H) * 0.25f) % 200;
            float p = ease(Mth.clamp((now - b.revealAt) / 480f, 0f, 1f)) * pageFade;
            if (p < 0.04f) continue;
            g.pose().pushMatrix();
            g.pose().translate(0, b.y - scroll - sy + (1f - p) * 16f);
            b.draw(g, cx0, sy, cw, p, now);
            g.pose().popMatrix();
        }
        g.disableScissor();

        drawNav(g, now);
        int ms = maxScroll();
        if (ms > 0) {
            int track = this.height - NAV_H - 8;
            float bar = Math.max(20, track * this.height / contentH);
            float by = NAV_H + 4 + (track - bar) * (scroll / ms);
            Ui.fill(g, this.width - 4, by, 2, bar, 0x50FFFFFF);
        }
        super.extractRenderState(g, mx, my, a);
        fancy.draw(g, this.font, pendingTooltip, mx, my, this.width, this.height, (gg, id, x, y) -> icon(gg, id, x, y, 1f, 1f));
        pendingTooltip = null;
    }

    private com.rrm.mobslearn.client.FancyTooltip.Content pendingTooltip;
    private final com.rrm.mobslearn.client.FancyTooltip fancy = new com.rrm.mobslearn.client.FancyTooltip();

    private void drawPixels(GuiGraphicsExtractor g, long now) {
        float t = (now % 1_000_000L) / 1000f;
        for (float[] p : pixels) {
            float y = (p[1] - t * p[2] * 0.6f - scroll / Math.max(1f, contentH) * 0.5f) % 1f;
            if (y < 0) y += 1f;
            float px = p[0] * this.width, py = y * this.height;
            int size = (int) p[3];
            int alpha = Math.round(0x18 + 0x30 * (0.5f + 0.5f * Mth.sin(t * 1.3f + p[4] * 6.28f)));
            int col = p[4] > 0.7f ? 0xF6E7C1 : 0x8BE03A;
            Ui.fill(g, px, py, size, size, (alpha << 24) | col);
        }
    }

    private record NavLink(String anchor, int x, int w) {
    }

    private final List<NavLink> navLinks = new ArrayList<>();
    private float navUnderX = -1, navUnderW;

    private void drawNav(GuiGraphicsExtractor g, long now) {
        float solid = Mth.clamp(scroll / 60f, 0f, 1f);
        g.fill(0, 0, this.width, NAV_H, (Math.round(0x90 + 0x60 * solid) << 24) | 0x080B08);
        g.fill(0, NAV_H - 1, this.width, NAV_H, Math.round(0x20 + 0x20 * solid) << 24 | 0xFFFFFF);
        int lh = 18, lw = Math.round(lh * (Ui.LOGO_W / (float) Ui.LOGO_H));
        Ui.logoAt(g, 10, 5, lw, lh);

        String[] ids = {"top", "how", "mobs", "settle", "villagers", "absurd", "intel", "commands", "faq"};
        int available = this.width - 46 - (lw + 20);
        int allWidth = 0;
        for (String id : ids) allWidth += font.width(Component.translatable("mobslearn.wiki.nav." + id)) + 14;
        if (allWidth > available) ids = new String[]{"how", "mobs", "faq"};
        int compactWidth = 0;
        for (String id : ids) compactWidth += font.width(Component.translatable("mobslearn.wiki.nav." + id)) + 14;
        if (compactWidth > available) ids = new String[]{"mobs", "faq"};
        navLinks.clear();
        int total = 0;
        List<Component> labels = new ArrayList<>();
        for (String id : ids) {
            Component c = Component.translatable("mobslearn.wiki.nav." + id);
            labels.add(c);
            total += this.font.width(c) + 14;
        }
        int x = this.width - 46 - total;
        if (x < lw + 20) x = lw + 20;
        String active = activeAnchor();
        for (int i = 0; i < ids.length; i++) {
            Component c = labels.get(i);
            int w = this.font.width(c);
            boolean hot = mouseY < NAV_H && mouseX >= x && mouseX < x + w + 14;
            boolean act = ids[i].equals(active);
            navLinks.add(new NavLink(ids[i], x, w + 14));
            g.text(this.font, c, x + 7, 10, act ? 0xFFFFFFFF : (hot ? TEXT : DIM), false);
            if (act) {
                float tx = x + 7, tw = w;
                if (navUnderX < 0) {
                    navUnderX = tx;
                    navUnderW = tw;
                }
                float k = 1f - (float) Math.exp(-dt * 14f);
                navUnderX += (tx - navUnderX) * k;
                navUnderW += (tw - navUnderW) * k;
            }
            x += w + 14;
        }
        if (navUnderX >= 0) Ui.fill(g, navUnderX, NAV_H - 4, navUnderW, 2, ACCENT);
    }

    private String activeAnchor() {
        if (page != null) return "mobs";
        String best = "top";
        for (Map.Entry<String, Float> e : anchors.entrySet()) {
            if (e.getValue() - NAV_H - 40 <= scroll) best = e.getKey();
        }
        return best;
    }

    static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static int a(int argb, float alpha) {
        int base = (argb >>> 24) == 0 ? 0xFF : (argb >>> 24);
        int al = Math.round(base * Mth.clamp(alpha, 0f, 1f));
        if (al < 8) al = 8;
        return (al << 24) | (argb & 0xFFFFFF);
    }

    float hoverAnim(Object key, boolean hot) {
        float v = hover.getOrDefault(key, 0f);
        v += ((hot ? 1f : 0f) - v) * (1f - (float) Math.exp(-dt * 10f));
        hover.put(key, v);
        return v;
    }

    boolean in(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= NAV_H;
    }

    void text(GuiGraphicsExtractor g, Component c, int x, int y, int color, float alpha) {
        g.text(this.font, c, x, y, a(color, alpha), false);
    }

    void textCentered(GuiGraphicsExtractor g, Component c, int cx, int y, int color, float alpha) {
        g.text(this.font, c, cx - this.font.width(c) / 2, y, a(color, alpha), false);
    }

    int paragraph(GuiGraphicsExtractor g, Component c, int x, int y, int w, int color, float alpha, boolean center) {
        for (FormattedCharSequence line : this.font.split(c, w)) {
            if (g != null) {
                int lx = center ? x + (w - this.font.width(line)) / 2 : x;
                g.text(this.font, line, lx, y, a(color, alpha), false);
            }
            y += 11;
        }
        return y;
    }

    int paragraphHeight(Component c, int w) {
        return this.font.split(c, w).size() * 11;
    }

    void big(GuiGraphicsExtractor g, Component c, int x, int y, float scale, int color, float alpha, boolean center) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int dx = center ? -this.font.width(c) / 2 : 0;
        g.text(this.font, c, dx, 0, a(color, alpha), false);
        g.pose().popMatrix();
    }

    void card(GuiGraphicsExtractor g, int x, int y, int w, int h, float hot, int edge, float alpha) {
        int fill = lerpColor(CARD, CARD_HOT, hot);
        g.fill(x, y, x + w, y + h, a(fill, alpha * 0.95f));
        int border = lerpColor(0x24FFFFFF, edge, hot);
        Ui.border(g, x, y, w, h, a(border, alpha));
    }

    private record IconTexture(net.minecraft.resources.Identifier id, float u0, float u1, float v0, float v1) {}
    private final Map<String, java.util.Optional<IconTexture>> texCache = new HashMap<>();

    private final Map<String, java.util.Optional<net.minecraft.resources.Identifier>> bakedCache = new HashMap<>();

    private java.util.Optional<net.minecraft.resources.Identifier> baked(String id) {
        return bakedCache.computeIfAbsent(id, k -> {
            net.minecraft.resources.Identifier ident = com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + k + ".png");
            return this.minecraft.getResourceManager().getResource(ident).isPresent() ? java.util.Optional.of(ident) : java.util.Optional.empty();
        });
    }

    void icon(GuiGraphicsExtractor g, String id, float x, float y, float scale, float alpha) {
        if (alpha < 0.55f || id == null) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        java.util.Optional<net.minecraft.resources.Identifier> b = baked(id);
        if (b.isPresent()) {
            g.blit(b.get(), 0, 0, 16, 16, 0f, 1f, 0f, 1f);
        } else if (this.minecraft.level != null) {
            ItemStack st = stack(id);
            if (!st.isEmpty()) g.item(st, 0, 0);
        } else {
            texture(id).ifPresent(tex -> g.blit(tex.id(), 0, 0, 16, 16, tex.u0(), tex.u1(), tex.v0(), tex.v1()));
        }
        g.pose().popMatrix();
    }

    private java.util.Optional<IconTexture> texture(String id) {
        return texCache.computeIfAbsent(id, k -> {
            String[] candidates = {"textures/item/" + k + ".png", "textures/block/" + k + ".png", "textures/block/" + k + "_side.png",
                "textures/block/" + k + "_front.png", "textures/block/" + k + "_top.png"};
            for (String c : candidates) {
                net.minecraft.resources.Identifier ident = net.minecraft.resources.Identifier.withDefaultNamespace(c);
                var resource = this.minecraft.getResourceManager().getResource(ident);
                if (resource.isEmpty()) continue;
                try (var input = resource.get().open(); var image = com.mojang.blaze3d.platform.NativeImage.read(input)) {
                    var animation = resource.get().metadata().getSection(net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.TYPE);
                    if (animation.isEmpty()) return java.util.Optional.of(new IconTexture(ident, 0f, 1f, 0f, 1f));
                    var metadata = animation.get();
                    var size = metadata.calculateFrameSize(image.getWidth(), image.getHeight());
                    int columns = Math.max(1, image.getWidth() / size.width());
                    int first = metadata.frames().filter(frames -> !frames.isEmpty()).map(frames -> frames.getFirst().index()).orElse(0);
                    float u0 = (first % columns) * size.width() / (float) image.getWidth();
                    float v0 = (first / columns) * size.height() / (float) image.getHeight();
                    return java.util.Optional.of(new IconTexture(ident, u0, u0 + size.width() / (float) image.getWidth(),
                        v0, v0 + size.height() / (float) image.getHeight()));
                } catch (Exception ignored) {
                }
            }
            return java.util.Optional.empty();
        });
    }

    static int lerpColor(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(a1 + (a2 - a1) * t) << 24) | (Math.round(r1 + (r2 - r1) * t) << 16)
            | (Math.round(g1 + (g2 - g1) * t) << 8) | Math.round(b1 + (b2 - b1) * t);
    }

    static String pips(int lvl) {
        return "●".repeat(Math.max(0, lvl)) + "○".repeat(Math.max(0, Skill.MAX_LEVEL - lvl));
    }

    abstract static class Block {
        float y;
        int h;
        long revealAt = -1;
        String anchor;

        abstract int layout(int w);

        abstract void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now);

        float dynH() {
            return h;
        }

        void animate(float dt) {
        }

        boolean click(int mx, int my, int x, int y, int w) {
            return false;
        }
    }

    private class Hero extends Block {
        private int btn1x, btn2x, btnY, btnW = 120, btnH = 20;

        Hero() {
            anchor = "top";
        }

        @Override
        int layout(int w) {
            return Math.max(230, Math.min(300, WikiScreen.this.height - NAV_H));
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            float t = now / 1000f;
            int cx = x + w / 2;
            for (int i = 0; i < 6; i++) {
                int gw = 120 + i * 40, gh = 50 + i * 16;
                g.fill(cx - gw / 2, y + 62 - gh / 2, cx + gw / 2, y + 62 + gh / 2, a(0x0C8BE03A, alpha));
            }
            int logoW = Math.min(300, w - 20);
            int logoH = Math.round(logoW * (Ui.LOGO_H / (float) Ui.LOGO_W));
            float bob = Mth.sin(t * 1.6f) * 3f;
            boolean hot = in(cx - logoW / 2, y + 14, logoW, logoH);
            float hv = hoverAnim("heroLogo", hot);
            float s = 1f + hv * 0.04f;
            if (alpha > 0.3f) {
                g.pose().pushMatrix();
                g.pose().translate(cx, y + 14 + logoH / 2f + bob);
                g.pose().scale(s, s);
                g.pose().translate(-logoW / 2f, -logoH / 2f);
                Ui.logoAt(g, 0, 0, logoW, logoH);
                g.pose().popMatrix();
            }
            int ty = y + 22 + logoH;
            big(g, Component.translatable("mobslearn.wiki.hero.tagline"), cx, ty, 1.4f, 0xFFFFFFFF, alpha, true);
            ty += 20;
            ty = paragraph(g, Component.translatable("mobslearn.wiki.hero.sub"), x + 30, ty, w - 60, DIM, alpha, true) + 8;

            btnY = ty;
            btn1x = cx - btnW - 4;
            btn2x = cx + 4;
            boolean h1 = in(btn1x, btnY, btnW, btnH), h2 = in(btn2x, btnY, btnW, btnH);
            float a1 = hoverAnim("hb1", h1), a2 = hoverAnim("hb2", h2);
            g.fill(btn1x, btnY, btn1x + btnW, btnY + btnH, a(lerpColor(0xFF4E9A1E, 0xFF62B828, a1), alpha));
            textCentered(g, Component.translatable("mobslearn.wiki.hero.cta"), btn1x + btnW / 2, btnY + 6, 0xFFFFFFFF, alpha);
            g.fill(btn2x, btnY, btn2x + btnW, btnY + btnH, a(lerpColor(0x30FFFFFF, 0x50FFFFFF, a2), alpha * 0.6f));
            Ui.border(g, btn2x, btnY, btnW, btnH, a(lerpColor(0x40FFFFFF, ACCENT, a2), alpha));
            textCentered(g, Component.translatable("mobslearn.wiki.hero.cta2"), btn2x + btnW / 2, btnY + 6, TEXT, alpha);

            int sy = btnY + btnH + 16;
            float c = ease(Mth.clamp((now - revealAt) / 1400f, 0f, 1f));
            int absurd = 0;
            for (Skill sk : Skill.ALL) if (sk.absurd()) absurd++;
            int[] values = {Skill.ALL.size(), MobGroup.values().length - 1, Skill.MAX_LEVEL, absurd};
            String[] keys = {"skills", "mobs", "levels", "absurd"};
            int colW = Math.min(100, (w - 20) / 4);
            int sx = cx - colW * 2;
            for (int i = 0; i < 4; i++) {
                int vx = sx + i * colW + colW / 2;
                big(g, Component.literal(String.valueOf(Math.round(values[i] * c))), vx, sy, 1.6f, i == 3 ? PINK : ACCENT, alpha, true);
                textCentered(g, Component.translatable("mobslearn.wiki.stat." + keys[i]), vx, sy + 16, DIM, alpha);
            }
            int by = y + h - 14;
            g.pose().pushMatrix();
            g.pose().translate(0, Mth.sin(t * 3f) * 2f);
            textCentered(g, Component.literal("⌄"), cx, by, a(0x80FFFFFF, 1f), alpha * (1f - Mth.clamp(scroll / 80f, 0f, 1f)));
            g.pose().popMatrix();
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            if (my >= btnY && my < btnY + btnH) {
                if (mx >= btn1x && mx < btn1x + btnW) {
                    goAnchor("how");
                    return true;
                }
                if (mx >= btn2x && mx < btn2x + btnW) {
                    goAnchor("mobs");
                    return true;
                }
            }
            return false;
        }
    }

    private class SectionHead extends Block {
        final String kicker, title, sub;

        SectionHead(String anchor, String kicker, String title, String sub) {
            this.anchor = anchor;
            this.kicker = kicker;
            this.title = title;
            this.sub = sub;
        }

        @Override
        int layout(int w) {
            return 52 + (sub == null ? 0 : paragraphHeight(Component.translatable(sub), w - 40)) + 6;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int cx = x + w / 2;
            Component k = Component.translatable(kicker);
            int kw = font.width(k) + 12;
            g.fill(cx - kw / 2, y + 16, cx + kw / 2, y + 28, a(0x248BE03A, alpha));
            textCentered(g, k, cx, y + 18, ACCENT, alpha);
            big(g, Component.translatable(title), cx, y + 34, 1.5f, 0xFFFFFFFF, alpha, true);
            if (sub != null) paragraph(g, Component.translatable(sub), x + 20, y + 52, w - 40, DIM, alpha, true);
        }
    }

    private class Steps extends Block {
        private final String[] icons = {"spyglass", "experience_bottle", "zombie_head", "enchanted_book"};
        private int cardH;

        @Override
        int layout(int w) {
            int cwid = (w - 8) / 2;
            cardH = 0;
            for (int i = 1; i <= 4; i++) cardH = Math.max(cardH, 52 + paragraphHeight(Component.translatable("mobslearn.help." + i + ".text"), cwid - 20));
            return 2 * (cardH + 8) + 4;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int gap = 8, cwid = (w - gap) / 2;
            for (int i = 0; i < 4; i++) {
                int cx = x + (i % 2) * (cwid + gap), top = y + 4 + (i / 2) * (cardH + gap);
                float local = Mth.clamp(alpha * 1.25f - i * 0.08f, 0f, 1f);
                boolean hot = in(cx, top, cwid, cardH);
                float hv = hoverAnim("step" + i, hot);
                int cy = top;
                g.pose().pushMatrix();
                g.pose().translate(0, -hv * 3f);
                card(g, cx, cy, cwid, cardH, hv, ACCENT, local);
                big(g, Component.literal("0" + (i + 1)), cx + 10, cy + 10, 1.6f, a(0x608BE03A, 1f), local, false);
                icon(g, icons[i], cx + cwid - 26, cy + 10, 1f, local);
                text(g, Component.translatable("mobslearn.help." + (i + 1) + ".title"), cx + 10, cy + 30, 0xFFFFFFFF, local);
                paragraph(g, Component.translatable("mobslearn.help." + (i + 1) + ".text"), cx + 10, cy + 43, cwid - 20, DIM, local, false);
                g.pose().popMatrix();
            }
        }
    }

    private class InfoCards extends Block {
        private final String prefix;
        private final String[] icons;
        private final int color;
        private int cardW, cardH, cols;
        private final int gap = 8;

        InfoCards(String prefix, String[] icons, int color) {
            this.prefix = prefix;
            this.icons = icons;
            this.color = color;
        }

        @Override
        int layout(int w) {
            cols = w >= 380 ? 2 : 1;
            cardW = (w - (cols - 1) * gap) / cols;
            cardH = 0;
            for (int i = 1; i <= icons.length; i++) {
                cardH = Math.max(cardH, 52 + paragraphHeight(Component.translatable("mobslearn.wiki." + prefix + "." + i + ".text"), cardW - 20));
            }
            int rows = (icons.length + cols - 1) / cols;
            return rows * (cardH + gap) + 4;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            for (int i = 0; i < icons.length; i++) {
                int cx = x + (i % cols) * (cardW + gap), cy = y + 4 + (i / cols) * (cardH + gap);
                float local = Mth.clamp(alpha * 1.25f - i * 0.08f, 0f, 1f);
                boolean hot = in(cx, cy, cardW, cardH);
                float hv = hoverAnim(prefix + i, hot);
                g.pose().pushMatrix();
                g.pose().translate(0, -Math.round(hv * 2f));
                card(g, cx, cy, cardW, cardH, hv, color, local);
                Ui.fill(g, cx, cy, cardW * (0.3f + 0.7f * hv), 2, a(color, local));
                g.fill(cx + 10, cy + 10, cx + 42, cy + 42, a(0xFF0B100B, local));
                Ui.border(g, cx + 10, cy + 10, 32, 32, a((0x70 << 24) | (color & 0xFFFFFF), local));
                icon(g, icons[i], cx + 10, cy + 10, 2f, local);
                text(g, Component.translatable("mobslearn.wiki." + prefix + "." + (i + 1) + ".title"), cx + 50, cy + 22, 0xFFFFFFFF, local);
                paragraph(g, Component.translatable("mobslearn.wiki." + prefix + "." + (i + 1) + ".text"), cx + 10, cy + 48, cardW - 20, DIM, local, false);
                g.pose().popMatrix();
            }
        }
    }

    private class LearningNotes extends Block {
        private final String[] keys = {"local", "practice", "weakness"};

        @Override
        int layout(int w) {
            int height = 34;
            for (String key : keys) height += paragraphHeight(Component.translatable("mobslearn.wiki.learning." + key), w - 35) + 8;
            return height + 8;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            card(g, x, y, w, h - 8, 0f, ACCENT, alpha);
            text(g, Component.translatable("mobslearn.wiki.learning.title"), x + 12, y + 12, ACCENT, alpha);
            int cy = y + 29;
            for (String key : keys) {
                text(g, Component.literal("•"), x + 12, cy, GOLD, alpha);
                cy = paragraph(g, Component.translatable("mobslearn.wiki.learning." + key), x + 23, cy, w - 35, DIM, alpha, false) + 8;
            }
        }
    }

    private class Legend extends Block {
        private final String[] sym = {"●●○", "⚔", "✦", "▸"};
        private final int[] col = {GOLD, 0xFFFF6A5A, PINK, 0xFF7FC8BE};
        private final String[] keys = {"levels", "pvp", "absurd", "how"};

        @Override
        int layout(int w) {
            return 24 + 2 * 14 + 10 + 3 * 13 + 8 + paragraphHeight(Component.translatable("mobslearn.wiki.legend.cap", Skill.MAX_PER_MOB, Skill.MAX_COMBAT_PER_MOB), w - 24) + 16;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int ch = h - 8;
            card(g, x, y + 4, w, ch, 0f, ACCENT, alpha);
            text(g, Component.translatable("mobslearn.wiki.legend.title"), x + 12, y + 14, ACCENT, alpha);
            int colW = (w - 24) / 2;
            for (int i = 0; i < 4; i++) {
                int lx = x + 12 + (i % 2) * colW, ly = y + 30 + (i / 2) * 14;
                text(g, Component.literal(sym[i]), lx, ly, col[i], alpha);
                text(g, Component.literal(font.plainSubstrByWidth(Component.translatable("mobslearn.help.legend." + keys[i]).getString(), colW - 30)), lx + 24, ly, DIM, alpha);
            }
            int ly = y + 30 + 2 * 14 + 8;
            g.fill(x + 12, ly - 4, x + w - 12, ly - 3, a(0x18FFFFFF, alpha));
            for (int l = 1; l <= Skill.MAX_LEVEL; l++) {
                int lx = x + 12;
                for (int p = 1; p <= Skill.MAX_LEVEL; p++) g.fill(lx + (p - 1) * 12, ly + 3, lx + (p - 1) * 12 + 10, ly + 6, a(p <= l ? GOLD : 0x2CFFFFFF, alpha));
                text(g, Component.translatable("mobslearn.wiki.legend.lvl", l, Math.round(Skill.SPAWN_CHANCE[l] * 100)), lx + 44, ly, TEXT, alpha);
                ly += 13;
            }
            paragraph(g, Component.translatable("mobslearn.wiki.legend.cap", Skill.MAX_PER_MOB, Skill.MAX_COMBAT_PER_MOB), x + 12, ly + 4, w - 24, FAINT, alpha, false);
        }
    }

    private class Tips extends Block {
        private final String[] icons = {"iron_sword", "iron_axe", "golden_apple", "command_block"};
        private int cardH;

        @Override
        int layout(int w) {
            int cwid = (w - 8) / 2;
            cardH = 0;
            for (int i = 0; i < 4; i++) cardH = Math.max(cardH, 42 + paragraphHeight(Component.translatable("mobslearn.wiki.tips." + i + ".text"), cwid - 20));
            return 2 * (cardH + 8) + 4;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int gap = 8, cwid = (w - gap) / 2;
            for (int i = 0; i < 4; i++) {
                int cx = x + (i % 2) * (cwid + gap), top = y + 4 + (i / 2) * (cardH + gap);
                float local = Mth.clamp(alpha * 1.25f - i * 0.08f, 0f, 1f);
                boolean hot = in(cx, top, cwid, cardH);
                float hv = hoverAnim("tip" + i, hot);
                int cy = top;
                g.pose().pushMatrix();
                g.pose().translate(0, -hv * 3f);
                card(g, cx, cy, cwid, cardH, hv, GOLD, local);
                g.fill(cx + 8, cy + 8, cx + 28, cy + 28, a(0x30000000, local));
                icon(g, icons[i], cx + 10, cy + 10, 1f, local);
                text(g, Component.translatable("mobslearn.wiki.tips." + i + ".title"), cx + 34, cy + 14, 0xFFFFFFFF, local);
                paragraph(g, Component.translatable("mobslearn.wiki.tips." + i + ".text"), cx + 10, cy + 33, cwid - 20, DIM, local, false);
                g.pose().popMatrix();
            }
        }
    }

    public static ItemStack stack(String id) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
        return new ItemStack(item);
    }

    private class MobGrid extends Block {
        private final List<MobGroup> groups = new ArrayList<>();
        private int cols, cellW, cellH = 58, gap = 8;

        MobGrid() {
            for (MobGroup g : MobGroup.values()) if (g.hostile) groups.add(g);
            for (MobGroup g : MobGroup.values()) if (!g.hostile) groups.add(g);
        }

        @Override
        int layout(int w) {
            cols = Math.max(2, Math.min(5, (w + gap) / (88 + gap)));
            cellW = (w - (cols - 1) * gap) / cols;
            int rows = (groups.size() + cols - 1) / cols;
            return rows * (cellH + gap) + 8;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            Knowledge k = ClientKnowledge.get();
            boolean synced = ClientKnowledge.synced();
            for (int i = 0; i < groups.size(); i++) {
                MobGroup grp = groups.get(i);
                int gx = x + (i % cols) * (cellW + gap), gy = y + (i / cols) * (cellH + gap);
                float local = Mth.clamp(alpha * 1.3f - (i % cols) * 0.06f, 0f, 1f);
                boolean hot = in(gx, gy, cellW, cellH);
                float hv = hoverAnim(grp, hot);
                if (hot && hv > 0.5f) pendingTooltip = new com.rrm.mobslearn.client.FancyTooltip.Content(grp, grp.displayName(), grp.color, grp.iconId)
                    .line(Component.translatable("mobslearn.wiki.group." + grp.key()), DIM).line(Component.translatable("mobslearn.tip.open_page"), FAINT);
                int ly = gy;
                g.pose().pushMatrix();
                g.pose().translate(0, -hv * 3f);
                card(g, gx, ly, cellW, cellH, hv, grp.color, local);
                Ui.fill(g, gx, ly, cellW * (0.25f + 0.75f * hv), 2, a(grp.color, local));
                icon(g, grp.iconId, gx + 8, ly + 8, 2f, local);
                Component name = grp.displayName();
                text(g, Component.literal(font.plainSubstrByWidth(name.getString(), cellW - 52)), gx + 46, ly + 10, 0xFFFFFFFF, local);
                int n = Skill.of(grp).size();
                text(g, Component.translatable("mobslearn.wiki.mobs.count", n), gx + 46, ly + 22, DIM, local);
                if (synced) {
                    int learned = k.groupLevels(grp), max = n * Skill.MAX_LEVEL;
                    g.fill(gx + 8, ly + cellH - 10, gx + cellW - 8, ly + cellH - 8, a(0x30FFFFFF, local));
                    g.fill(gx + 8, ly + cellH - 10, gx + 8 + Math.round((cellW - 16) * (max == 0 ? 0 : learned / (float) max)), ly + cellH - 8, a(grp.color, local));
                }
                g.pose().pushMatrix();
                g.pose().translate(hv * 3f, 0);
                text(g, Component.literal("→"), gx + cellW - 14, ly + 36, grp.color, local * hv);
                g.pose().popMatrix();
                g.pose().popMatrix();
            }
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            for (int i = 0; i < groups.size(); i++) {
                int gx = x + (i % cols) * (cellW + gap), gy = y + (i / cols) * (cellH + gap);
                if (mx >= gx && mx < gx + cellW && my >= gy && my < gy + cellH) {
                    openPage(groups.get(i));
                    return true;
                }
            }
            return false;
        }
    }

    private class Marquee extends Block {
        private final List<Skill> skills = new ArrayList<>();
        private float offset;

        Marquee() {
            for (Skill s : Skill.ALL) if (s.absurd()) skills.add(s);
        }

        @Override
        int layout(int w) {
            return 34;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            boolean hot = mouseY >= y && mouseY < y + 26 && mouseY >= NAV_H;
            if (!hot) offset += dt * 28f;
            int total = 0;
            List<Integer> widths = new ArrayList<>();
            for (Skill s : skills) {
                int cw2 = font.width(s.displayName()) + 34;
                widths.add(cw2);
                total += cw2 + 6;
            }
            if (total <= 0) return;
            offset %= total;
            g.enableScissor(0, Math.max(NAV_H, y), WikiScreen.this.width, y + 26);
            float startX = -offset;
            for (int rep = 0; rep < 3 && startX < WikiScreen.this.width; rep++) {
                for (int i = 0; i < skills.size(); i++) {
                    Skill s = skills.get(i);
                    int cx = (int) Math.floor(startX);
                    int cw2 = widths.get(i);
                    if (cx + cw2 > 0 && cx < WikiScreen.this.width) {
                        boolean h = in(cx, y, cw2, 24);
                        g.pose().pushMatrix();
                        g.pose().translate(startX - cx, 0);
                        g.fill(cx, y, cx + cw2, y + 24, a(h ? 0xFF241A22 : 0xFF1A1418, alpha));
                        Ui.border(g, cx, y, cw2, 24, a(h ? PINK : 0x40FF7AD9, alpha));
                        icon(g, s.iconId(), cx + 5, y + 4, 1f, alpha);
                        text(g, s.displayName(), cx + 25, y + 8, 0xFFFFFFFF, alpha);
                        if (h) pendingTooltip = new com.rrm.mobslearn.client.FancyTooltip.Content(s, s.displayName(), PINK, s.iconId())
                            .badge(s.group().displayName(), s.group().color).line(s.description(), DIM);
                        g.pose().popMatrix();
                    }
                    startX += cw2 + 6;
                }
            }
            g.disableScissor();
        }
    }

    private class AbsurdCards extends Block {
        private final String[] ids = {"creeper_angel", "sheep_balloon", "parrot_mimic"};
        private int cardH = 96;

        @Override
        int layout(int w) {
            int cwid = (w - 16) / 3;
            int ph = 0;
            for (String id : ids) {
                Skill s = Skill.byId(id);
                if (s != null) ph = Math.max(ph, paragraphHeight(s.description(), cwid - 20));
            }
            cardH = 64 + ph + 8;
            return cardH + 16;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int gap = 8, cwid = (w - 2 * gap) / 3;
            for (int i = 0; i < ids.length; i++) {
                Skill s = Skill.byId(ids[i]);
                if (s == null) continue;
                int cx = x + i * (cwid + gap);
                float local = Mth.clamp(alpha * 1.2f - i * 0.1f, 0f, 1f);
                boolean hot = in(cx, y + 6, cwid, cardH + 2);
                float hv = hoverAnim("abs" + i, hot);
                int cy = y + 8;
                g.pose().pushMatrix();
                g.pose().translate(0, -hv * 3f);
                card(g, cx, cy, cwid, cardH, hv, PINK, local);
                icon(g, s.iconId(), cx + 10, cy + 8, 2f, local);
                text(g, Component.literal("✦"), cx + cwid - 14, cy + 8, PINK, local);
                text(g, s.displayName(), cx + 10, cy + 40, 0xFFFFFFFF, local);
                text(g, s.group().displayName(), cx + 10, cy + 51, s.group().color, local);
                paragraph(g, s.description(), cx + 10, cy + 64, cwid - 20, DIM, local, false);
                g.pose().popMatrix();
            }
        }
    }

    private class IntelTimeline extends Block {
        @Override
        int layout(int w) {
            int bx = 10, bw = w - 20, lx = bx, rows = 1;
            for (Skill s : Skill.of(MobGroup.WORLD)) {
                lx += Math.max(90, font.width(s.displayName()) + 34);
                if (lx > bx + bw - 80) {
                    lx = bx;
                    rows++;
                }
            }
            return 34 + 16 + rows * 30 + 10;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            Knowledge k = ClientKnowledge.get();
            boolean synced = ClientKnowledge.synced();
            int barY = y + 34, bx = x + 10, bw = w - 20;
            g.fill(bx, barY, bx + bw, barY + 4, a(0x30FFFFFF, alpha));
            float fill = synced ? k.intelligence() / 100f : 0f;
            float anim = ease(Mth.clamp((now - revealAt) / 1500f, 0f, 1f));
            Ui.fill(g, bx, barY, bw * fill * anim, 4, a(ACCENT, alpha));
            text(g, synced ? Component.translatable("mobslearn.wiki.intel.yours", k.intelligence())
                : Component.translatable("mobslearn.wiki.intel.none"), bx, y + 18, synced ? ACCENT : FAINT, alpha);
            for (Skill s : Skill.of(MobGroup.WORLD)) {
                for (int lvl = 0; lvl < s.maxLevel(); lvl++) {
                    int pct = s.thresholds()[lvl];
                    int mx = bx + Math.round(bw * pct / 100f);
                    boolean reached = synced && k.intelligence() >= pct;
                    g.fill(mx - 1, barY - 3, mx + 1, barY + 7, a(reached ? GOLD : 0x60FFFFFF, alpha));
                    if (in(mx - 4, barY - 6, 8, 16)) {
                        pendingTooltip = new com.rrm.mobslearn.client.FancyTooltip.Content(s.id() + lvl, s.displayName(), GOLD, s.iconId())
                            .badge(Component.translatable("mobslearn.screen.intel_tip", lvl + 1, pct), GOLD)
                            .line(s.description(), DIM);
                    }
                }
            }
            int lx = bx, ly = barY + 16;
            for (Skill s : Skill.of(MobGroup.WORLD)) {
                icon(g, s.iconId(), lx, ly, 1f, alpha);
                text(g, s.displayName(), lx + 20, ly + 4, 0xFFFFFFFF, alpha);
                String th = s.maxLevel() == 1 ? s.thresholds()[0] + "%" : s.thresholds()[0] + "/" + s.thresholds()[1] + "/" + s.thresholds()[2] + "%";
                text(g, Component.literal(th), lx + 20, ly + 15, FAINT, alpha);
                lx += Math.max(90, font.width(s.displayName()) + 34);
                if (lx > bx + bw - 80) {
                    lx = bx;
                    ly += 30;
                }
            }
        }
    }

    private class Commands extends Block {
        private final String[] lines = {"/mobslearn info", "/mobslearn set zombie_digger 3", "/mobslearn maxall", "/mobslearn speed 10", "/mobslearn settlement zombie", "/mobslearn timewarp 100", "/mobslearn reset"};
        private final String[] keys = {"info", "set", "maxall", "speed", "settlement", "timewarp", "reset"};

        @Override
        int layout(int w) {
            return lines.length * 24 + 10;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            for (int i = 0; i < lines.length; i++) {
                int ly = y + i * 24;
                boolean hot = in(x, ly, w, 20);
                float hv = hoverAnim("cmd" + i, hot);
                g.fill(x, ly, x + w, ly + 20, a(lerpColor(0xFF070907, 0xFF0D130C, hv), alpha));
                g.fill(x, ly, x + 2, ly + 20, a(ACCENT, alpha));
                text(g, Component.literal("$"), x + 8, ly + 6, FAINT, alpha);
                text(g, Component.literal(lines[i]), x + 18, ly + 6, 0xFFB8F27A, alpha);
                Component d = lines[i].equals(copiedLine) && now - copiedAt < 1500
                    ? Component.translatable("mobslearn.wiki.cmd.copied") : Component.translatable("mobslearn.wiki.cmd." + keys[i]);
                text(g, d, x + w - 8 - font.width(d), ly + 6, lines[i].equals(copiedLine) && now - copiedAt < 1500 ? ACCENT : DIM, alpha);
                if (hot) pendingTooltip = new com.rrm.mobslearn.client.FancyTooltip.Content("cmd" + i, Component.literal(lines[i]), ACCENT, "command_block")
                    .line(Component.translatable("mobslearn.wiki.cmd." + keys[i]), TEXT).line(Component.translatable("mobslearn.wiki.cmd.copy"), FAINT);
            }
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            int i = (my - y) / 24;
            if (i >= 0 && i < lines.length && (my - y) % 24 < 20) {
                minecraft.keyboardHandler.setClipboard(lines[i]);
                copiedLine = lines[i];
                copiedAt = Util.getMillis();
                clickSound(1.6f);
                return true;
            }
            return false;
        }
    }

    private class Faq extends Block {
        final int idx;
        boolean open;
        float openAnim;

        Faq(int idx) {
            this.idx = idx;
        }

        @Override
        int layout(int w) {
            return 26 + 6 + paragraphHeight(Component.translatable("mobslearn.wiki.faq." + idx + ".a"), w - 24) + 8;
        }

        @Override
        void animate(float dt) {
            openAnim += ((open ? 1f : 0f) - openAnim) * (1f - (float) Math.exp(-dt * 12f));
            if (Math.abs((open ? 1f : 0f) - openAnim) < 0.002f) openAnim = open ? 1f : 0f;
        }

        @Override
        float dynH() {
            return 30 + (h - 30) * WikiScreen.ease(openAnim);
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int full = h - 6;
            float curH = 24 + (full - 24) * WikiScreen.ease(openAnim);
            boolean hot = in(x, y, w, 24);
            float hv = hoverAnim("faq" + idx, hot);
            int fill = lerpColor(CARD, CARD_HOT, Math.max(hv, openAnim * 0.5f));
            Ui.fill(g, x, y, w, curH, a(fill, alpha * 0.95f));
            Ui.fill(g, x, y, w, 1, a(0x24FFFFFF, alpha));
            Ui.fill(g, x, y + curH - 1, w, 1, a(0x24FFFFFF, alpha));
            text(g, Component.translatable("mobslearn.wiki.faq." + idx + ".q"), x + 12, y + 8, 0xFFFFFFFF, alpha);
            text(g, Component.literal(open ? "−" : "+"), x + w - 16, y + 8, ACCENT, alpha);
            if (openAnim > 0.05f) {
                g.enableScissor(x, Math.max(NAV_H, y + 24), x + w, (int) Math.ceil(y + curH));
                paragraph(g, Component.translatable("mobslearn.wiki.faq." + idx + ".a"), x + 12, y + 28, w - 24, DIM, alpha * openAnim, false);
                g.disableScissor();
            }
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            if (my < y + 24) {
                open = !open;
                clickSound(open ? 1.3f : 1.0f);
                return true;
            }
            return false;
        }
    }

    private class Footer extends Block {
        @Override
        int layout(int w) {
            return 70;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            g.fill(x, y + 14, x + w, y + 15, a(0x20FFFFFF, alpha));
            int cx = x + w / 2;
            int lw = 70, lh = Math.round(lw * (Ui.LOGO_H / (float) Ui.LOGO_W));
            if (alpha > 0.3f) Ui.logoAt(g, cx - lw / 2, y + 22, lw, lh);
            textCentered(g, Component.translatable("mobslearn.credit"), cx, y + 26 + lh, ACCENT, alpha);
            textCentered(g, Component.translatable("mobslearn.wiki.footer"), cx, y + 38 + lh, FAINT, alpha);
        }
    }

    private class MobHeader extends Block {
        final MobGroup grp;
        private int backW, prevW, nextW;
        private MobGroup prev, next;

        MobHeader(MobGroup grp) {
            this.grp = grp;
            anchor = "top";
            List<MobGroup> all = order();
            int i = all.indexOf(grp);
            prev = all.get((i + all.size() - 1) % all.size());
            next = all.get((i + 1) % all.size());
        }

        @Override
        int layout(int w) {
            return 34 + 86 + paragraphHeight(Component.translatable("mobslearn.wiki.group." + grp.key()), w - 28) + 20;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            Component back = Component.translatable("mobslearn.wiki.mob.back");
            backW = font.width(back) + 4;
            boolean hb = in(x, y + 10, backW, 12);
            g.pose().pushMatrix();
            g.pose().translate(Math.round(hoverAnim("back", hb) * -3f), 0);
            text(g, back, x, y + 12, hb ? ACCENT : DIM, alpha);
            g.pose().popMatrix();
            Component pn = Component.literal("‹ " + prev.displayName().getString());
            Component nn = Component.literal(next.displayName().getString() + " ›");
            prevW = font.width(pn);
            nextW = font.width(nn);
            int nx = x + w - nextW, px = nx - 14 - prevW;
            boolean hp = in(px, y + 10, prevW, 12), hn = in(nx, y + 10, nextW, 12);
            text(g, pn, px, y + 12, hp ? prev.color : FAINT, alpha);
            text(g, nn, nx, y + 12, hn ? next.color : FAINT, alpha);

            int top = y + 30, hh = 86 + paragraphHeight(Component.translatable("mobslearn.wiki.group." + grp.key()), w - 28) + 8;
            int tint = grp.color & 0xFFFFFF;
            g.fill(x, top, x + w, top + hh, a(0xFF101710, alpha));
            g.fillGradient(x, top, x + w, top + hh, a((0x38 << 24) | tint, alpha), a(0x06000000 | tint, alpha));
            Ui.border(g, x, top, w, hh, a((0x60 << 24) | tint, alpha));
            g.fill(x, top, x + w, top + 2, a(grp.color, alpha));
            for (int i = 0; i < 6; i++) {
                float t = ((now / 40f) + i * 53f) % 200f / 200f;
                int sx = x + 90 + Math.round(t * (w - 100)), sy = top + hh - 8 - ((i * 17) % (hh - 20));
                g.fill(sx, sy, sx + 2, sy + 2, a((0x50 << 24) | tint, alpha * (1f - t)));
            }
            int fx = x + 14, fy = top + 14;
            g.fill(fx, fy, fx + 56, fy + 56, a(0xFF0B100B, alpha));
            Ui.border(g, fx, fy, 56, 56, a(grp.color, alpha));
            int bob = Math.round(Mth.sin(now / 600f) * 1.2f);
            icon(g, grp.iconId, fx + 4, fy + 4 + bob, 3f, alpha);
            big(g, Component.literal(grp.displayName().getString().toUpperCase(java.util.Locale.ROOT)), x + 84, fy + 2, 2f, grp.color, alpha, false);

            List<Skill> list = Skill.of(grp);
            int absurd = 0, combat = 0;
            for (Skill sk : list) {
                if (sk.absurd()) absurd++;
                else if (sk.combat()) combat++;
            }
            int cx = x + 84, cy = fy + 24;
            cx = chip(g, Component.translatable("mobslearn.wiki.chip.skills", list.size()), cx, cy, 0xFFCFC6AC, alpha);
            if (combat > 0) cx = chip(g, Component.literal("⚔ " + combat), cx, cy, 0xFFFF7A6A, alpha);
            if (absurd > 0) cx = chip(g, Component.literal("✦ " + absurd), cx, cy, PINK, alpha);
            if (ClientKnowledge.synced()) {
                Knowledge k = ClientKnowledge.get();
                int done = 0;
                for (Skill sk : list) if (k.level(sk) > 0) done++;
                chip(g, Component.translatable("mobslearn.wiki.chip.learned", done, list.size()), cx, cy, GOLD, alpha);
                int lv = k.groupLevels(grp), max = list.size() * Skill.MAX_LEVEL;
                int bx = x + 84, bw = w - 98;
                g.fill(bx, fy + 42, bx + bw, fy + 45, a(0x30FFFFFF, alpha));
                Ui.fill(g, bx, fy + 42, bw * (max == 0 ? 0 : lv / (float) max) * alpha, 3, a(grp.color, alpha));
            }
            paragraph(g, Component.translatable("mobslearn.wiki.group." + grp.key()), x + 14, top + 76, w - 28, TEXT, alpha, false);
        }

        private int chip(GuiGraphicsExtractor g, Component c, int x, int y, int color, float alpha) {
            int tw = font.width(c) + 10;
            g.fill(x, y, x + tw, y + 13, a((0x30 << 24) | (color & 0xFFFFFF), alpha));
            Ui.border(g, x, y, tw, 13, a((0x50 << 24) | (color & 0xFFFFFF), alpha));
            text(g, c, x + 5, y + 3, color, alpha);
            return x + tw + 4;
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            if (my >= y + 8 && my < y + 24) {
                if (mx >= x && mx < x + backW) {
                    goAnchor("mobs");
                    return true;
                }
                int nx = x + w - nextW, px = nx - 14 - prevW;
                if (mx >= nx && mx < nx + nextW) {
                    openPage(next);
                    return true;
                }
                if (mx >= px && mx < px + prevW) {
                    openPage(prev);
                    return true;
                }
            }
            return false;
        }
    }

    private class SectionTitle extends Block {
        final String key;
        final int count, color;

        SectionTitle(String key, int count, int color) {
            this.key = key;
            this.count = count;
            this.color = color;
        }

        @Override
        int layout(int w) {
            return 30;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            Component label = Component.translatable("mobslearn.wiki.sec." + key);
            g.fill(x, y + 12, x + 4, y + 22, a(color, alpha));
            text(g, Component.literal(label.getString().toUpperCase(java.util.Locale.ROOT)), x + 10, y + 13, color, alpha);
            Component n = Component.literal(String.valueOf(count));
            int lx = x + 10 + font.width(label.getString().toUpperCase(java.util.Locale.ROOT)) + 8;
            g.fill(lx, y + 17, x + w - font.width(n) - 8, y + 18, a(0x26FFFFFF, alpha));
            text(g, n, x + w - font.width(n), y + 13, DIM, alpha);
        }
    }

    private class SkillRow extends Block {
        final List<Skill> skills;
        final int base;
        private int cardW, gap = 8;

        SkillRow(List<Skill> skills, int base) {
            this.skills = skills;
            this.base = base;
        }

        private int cols(int w) {
            return w >= 380 ? 2 : 1;
        }

        private int heightOf(Skill s, int cw) {
            return 58 + paragraphHeight(s.description(), cw - 20) + 6 + paragraphHeight(how(s), cw - 20) + 8 + 38;
        }

        private Component how(Skill s) {
            if (s.group() == MobGroup.WORLD && s.maxLevel() == 1) return Component.translatable("mobslearn.screen.how_world", s.thresholds()[0]);
            return s.group() == MobGroup.WORLD
                ? Component.translatable("mobslearn.wiki.mob.world_thresholds2", s.thresholds()[0], s.thresholds()[1], s.thresholds()[2])
                : Component.translatable("mobslearn.screen.how", s.trigger().describe());
        }

        @Override
        int layout(int w) {
            int cols = cols(w);
            cardW = (w - (cols - 1) * gap) / cols;
            int hmax = 0;
            for (Skill s : skills) hmax = Math.max(hmax, heightOf(s, cardW));
            return hmax + gap;
        }

        @Override
        void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, long now) {
            int cols = cols(w);
            cardW = (w - (cols - 1) * gap) / cols;
            int ch = h - gap;
            for (int i = 0; i < skills.size(); i++) {
                int cx = x + (i % cols) * (cardW + gap);
                int cy = y + (i / cols) * (ch + gap);
                float local = Mth.clamp(alpha * 1.3f - i * 0.12f, 0f, 1f);
                drawCard(g, skills.get(i), cx, cy, cardW, ch, local, now);
            }
        }

        private void drawCard(GuiGraphicsExtractor g, Skill s, int x, int y, int w, int ch, float alpha, long now) {
            Knowledge k = ClientKnowledge.get();
            boolean synced = ClientKnowledge.synced();
            int lvl = synced ? k.level(s) : 0;
            boolean hot = in(x, y, w, ch);
            float hv = hoverAnim(s, hot);
            int edge = s.absurd() ? PINK : s.combat() ? 0xFFFF5A4A : s.group().color;
            int lift = Math.round(hv * 2f);
            g.pose().pushMatrix();
            g.pose().translate(0, -lift);
            card(g, x, y, w, ch, hv, edge, alpha);
            if (lvl > 0) g.fill(x + 1, y + 1, x + w - 1, y + ch - 1, a((0x12 << 24) | (edge & 0xFFFFFF), alpha));
            Ui.fill(g, x, y, w * (0.3f + 0.7f * hv), 2, a(edge, alpha));

            int fx = x + 10, fy = y + 12;
            g.fill(fx, fy, fx + 36, fy + 36, a(0xFF0B100B, alpha));
            Ui.border(g, fx, fy, 36, 36, a((0x70 << 24) | (edge & 0xFFFFFF), alpha));
            icon(g, s.iconId(), fx + 2, fy + 2, 2f, alpha);
            if (synced && lvl == 0) g.fill(fx + 1, fy + 1, fx + 35, fy + 35, a(0x88101810, alpha));

            int tx = x + 54, tw = w - 64;
            text(g, Component.literal(font.plainSubstrByWidth(s.displayName().getString(), tw)), tx, y + 14, 0xFFFFFFFF, alpha);
            int cx = tx;
            if (s.absurd()) cx = tag(g, Component.translatable("mobslearn.wiki.tag.absurd"), cx, y + 28, PINK, alpha);
            if (s.combat()) cx = tag(g, Component.translatable("mobslearn.wiki.tag.pvp"), cx, y + 28, 0xFFFF7A6A, alpha);
            if (synced) {
                Component yours = Component.translatable("mobslearn.wiki.mob.yours", "●".repeat(Math.max(0, lvl)) + "○".repeat(Math.max(0, s.maxLevel() - lvl)));
                text(g, yours, x + w - 10 - font.width(yours), y + 29, lvl > 0 ? GOLD : FAINT, alpha);
            }

            int py = paragraph(g, s.description(), x + 10, y + 56, w - 20, 0xFFB9B29C, alpha, false);
            g.fill(x + 10, py + 2, x + w - 10, py + 3, a(0x1EFFFFFF, alpha));
            py = paragraph(g, how(s), x + 10, py + 6, w - 20, 0xFF6FC0B4, alpha, false);

            drawTrack(g, s, k, synced, lvl, edge, x, py + 8, w, alpha);
            g.pose().popMatrix();
        }

        private int tag(GuiGraphicsExtractor g, Component c, int x, int y, int color, float alpha) {
            int tw = font.width(c) + 8;
            g.fill(x, y, x + tw, y + 11, a((0x38 << 24) | (color & 0xFFFFFF), alpha));
            text(g, c, x + 4, y + 2, color, alpha);
            return x + tw + 4;
        }

        private void drawTrack(GuiGraphicsExtractor g, Skill s, Knowledge k, boolean synced, int lvl, int edge, int x, int y, int w, float alpha) {
            boolean world = s.group() == MobGroup.WORLD;
            double pts = !synced ? 0 : world ? k.intelligence() : k.points(s);
            int left = x + 26, right = x + w - 26, span = right - left;
            int ly = y + 13;
            if (s.maxLevel() > 1) g.fill(left, ly, right, ly + 1, a(0x30FFFFFF, alpha));
            int maxL = s.maxLevel();
            for (int i = 0; i < maxL; i++) {
                int nx = maxL == 1 ? left + span / 2 : left + span * i / (maxL - 1);
                boolean done = lvl > i;
                if (i < maxL - 1) {
                    int nx2 = left + span * (i + 1) / (maxL - 1);
                    float f = lvl > i + 1 ? 1f : lvl == i + 1 ? (float) Mth.clamp((pts - s.thresholds()[i]) / Math.max(1, s.thresholds()[i + 1] - s.thresholds()[i]), 0, 1) : 0f;
                    if (i == 0 && lvl == 0) f = 0f;
                    Ui.fill(g, nx, ly, (nx2 - nx) * f, 1, a(edge, alpha));
                }
                int c = done ? GOLD : lvl == i && synced ? edge : 0xFF5A5848;
                g.fill(nx - 4, ly - 4, nx + 5, ly + 5, a(0xFF0B100B, alpha));
                Ui.border(g, nx - 4, ly - 4, 9, 9, a(c, alpha));
                if (done) g.fill(nx - 2, ly - 2, nx + 3, ly + 3, a(GOLD, alpha));
                String roman = i == 0 ? "I" : i == 1 ? "II" : "III";
                g.text(font, roman, nx - font.width(roman) / 2, y - 3, a(done ? GOLD : FAINT, alpha), false);
                String need = world ? s.thresholds()[i] + "%" : "×" + s.thresholds()[i];
                g.text(font, need, nx - font.width(need) / 2, ly + 8, a(done ? 0xFFE8D9A8 : DIM, alpha), false);
            }
        }

        @Override
        boolean click(int mx, int my, int x, int y, int w) {
            return false;
        }
    }
}
