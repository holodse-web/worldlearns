package com.rrm.mobslearn.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rrm.mobslearn.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ChiefTradeScreen extends Screen {
    private record Offer(int index, ItemStack a, ItemStack b, ItemStack result, boolean locked, int tier) {
    }

    private static final int ROW = 30;
    private final int chief;
    private final String kind;
    private final List<Offer> offers = new ArrayList<>();
    private final Map<Integer, Float> hover = new HashMap<>();
    private final Map<Integer, Long> flash = new HashMap<>();
    private long openedAt, lastFrame;
    private float dt;
    private int px, py, pw, ph, extra;
    private List<net.minecraft.util.FormattedCharSequence> sub = new ArrayList<>();

    public ChiefTradeScreen(int chief, String kind, String json) {
        super(Component.translatable("mobslearn.trade.title." + kind));
        this.chief = chief;
        this.kind = kind;
        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            offers.add(new Offer(o.get("i").getAsInt(), Anim.stack(o.get("a").getAsString(), o.get("ac").getAsInt()),
                Anim.stack(o.get("b").getAsString(), o.get("bc").getAsInt()), Anim.stack(o.get("r").getAsString(), o.get("rc").getAsInt()),
                o.get("locked").getAsBoolean(), o.get("tier").getAsInt()));
        }
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        pw = Math.min(300, width - 20);
        sub = font.split(Component.translatable("mobslearn.trade.sub." + kind), pw - 52);
        extra = Math.max(0, sub.size() - 1) * 10;
        ph = Math.min(60 + extra + offers.size() * (ROW + 4) + 30, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        addRenderableWidget(new MlButton(width / 2 - 50, py + ph - 24, 100, 18, Component.translatable("mobslearn.gui.done"), b -> onClose()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int count(ItemStack want) {
        if (minecraft.player == null || want.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
            ItemStack s = minecraft.player.getInventory().getItem(i);
            if (s.is(want.getItem())) n += s.getCount();
        }
        return n;
    }

    private boolean affordable(Offer o) {
        return count(o.a) >= o.a.getCount() && (o.b.isEmpty() || count(o.b) >= o.b.getCount());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int top = py + 44 + extra;
        for (int i = 0; i < offers.size(); i++) {
            int ry = top + i * (ROW + 4);
            if (mouseX >= px + 10 && mouseX < px + pw - 10 && mouseY >= ry && mouseY < ry + ROW) {
                Offer o = offers.get(i);
                if (o.locked || !affordable(o)) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f));
                    return true;
                }
                if (com.rrm.mobslearn.client.CNet.canSend(Net.TradePayload.TYPE)) com.rrm.mobslearn.client.CNet.send(new Net.TradePayload(chief, o.index));
                flash.put(i, Util.getMillis());
                Ui.click(1.3f);
                return true;
            }
        }
        return false;
    }

    public void mlBackground(GuiGraphics g, int mx, int my, float a) {
        g.fill(0, 0, width, height, Anim.fade(0xB0000000, Anim.ease((Util.getMillis() - openedAt) / 250f)));
    }

    private void stackAt(GuiGraphics g, ItemStack s, int x, int y, boolean enough) {
        if (s.isEmpty()) return;
        g.renderItem(s, x, y);
        String c = String.valueOf(s.getCount());
        if (s.getCount() > 1) com.rrm.mobslearn.client.Ui.text(g, font, c, x + 17 - font.width(c), y + 9, enough ? 0xFFFFFFFF : Ui.RED, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float a) {
        mlBackground(g, mx, my, a);
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float p = Anim.ease((now - openedAt) / 300f);
        int y0 = py + Math.round((1f - p) * 12f);
        g.fill(px, y0, px + pw, y0 + ph, Anim.fade(0xF4120F0D, p));
        Ui.border(g, px, y0, pw, ph, Anim.fade(0x40FFFFFF, p));
        g.fill(px, y0, px + pw, y0 + 2, Anim.fade(kind.equals("skeleton") ? 0xFFD8D8D0 : kind.equals("creeper") ? 0xFF4FD15A : 0xFF6FA84A, p));
        if (p > 0.5f) {
            g.pose().pushPose();
            g.pose().translate(px + 8, y0 + 6, 0);
            com.rrm.mobslearn.client.Ui.blitUV(g,com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + (kind.equals("skeleton") ? "skeleton_skull" : kind.equals("creeper") ? "creeper_head" : "zombie_head") + ".png"), 0, 0, 32, 32, 0f, 1f, 0f, 1f);
            g.pose().popPose();
        }
        Ui.scaledText(g, font, title, px + 46, y0 + 10, 1.3f, Anim.fade(0xFFFFFFFF, p), false, false);
        for (int li = 0; li < sub.size(); li++) com.rrm.mobslearn.client.Ui.text(g, font, sub.get(li), px + 46, y0 + 25 + li * 10, Anim.fade(Ui.MUTED, p), false);

        int top = y0 + 44 + extra;
        for (int i = 0; i < offers.size(); i++) {
            Offer o = offers.get(i);
            float local = Anim.ease((now - openedAt - 80 - i * 45L) / 260f);
            int ry = top + i * (ROW + 4) + Math.round((1f - local) * 8f);
            boolean hot = mx >= px + 10 && mx < px + pw - 10 && my >= ry && my < ry + ROW;
            float hv = Anim.approach(hover.getOrDefault(i, 0f), hot ? 1f : 0f, dt, 12f);
            hover.put(i, hv);
            boolean ok = !o.locked && affordable(o);
            Long f = flash.get(i);
            float fl = f == null ? 0f : Math.max(0f, 1f - (now - f) / 500f);
            int base = Anim.lerp(0xFF16130F, 0xFF221C15, hv);
            if (fl > 0) base = Anim.lerp(base, 0xFF2E6B16, fl);
            g.fill(px + 10, ry, px + pw - 10, ry + ROW, Anim.fade(base, local));
            Ui.border(g, px + 10, ry, pw - 20, ROW, Anim.fade(Anim.lerp(0x20FFFFFF, ok ? Ui.GREEN : Ui.RED, hv), local));
            if (local < 0.5f) continue;
            int ix = px + 18, iy = ry + 7;
            stackAt(g, o.a, ix, iy, count(o.a) >= o.a.getCount());
            if (!o.b.isEmpty()) {
                com.rrm.mobslearn.client.Ui.text(g, font, "+", ix + 21, iy + 4, Ui.MUTED, false);
                stackAt(g, o.b, ix + 30, iy, count(o.b) >= o.b.getCount());
            }
            com.rrm.mobslearn.client.Ui.text(g, font, "→", ix + 58, iy + 4, ok ? Ui.GREEN : Ui.MUTED, false);
            stackAt(g, o.result, ix + 74, iy, true);
            Component name = o.result.getHoverName();
            String n = font.plainSubstrByWidth(name.getString(), pw - 130);
            com.rrm.mobslearn.client.Ui.text(g, font, n, ix + 98, iy + 1, o.locked ? 0xFF7A7466 : 0xFFFFFFFF, false);
            Component sub = o.locked ? Component.translatable("mobslearn.trade.locked", o.tier)
                : ok ? Component.translatable("mobslearn.trade.click") : Component.translatable("mobslearn.trade.need");
            com.rrm.mobslearn.client.Ui.text(g, font, sub, ix + 98, iy + 11, o.locked ? 0xFF6E6A5C : ok ? Ui.GREEN : Ui.RED, false);
            if (o.locked) g.fill(px + 11, ry + 1, px + pw - 11, ry + ROW - 1, 0x70000000);
        }
        super.render(g, mx, my, a);
    }
}
