package com.rrm.mobslearn.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AskScreen extends Screen {
    private static final int SLOT = 18;
    private final int villager;
    private final String topic;
    private final List<List<ItemStack>> grids = new ArrayList<>();
    private final ItemStack result;
    private final Map<Integer, Float> hover = new HashMap<>();
    private final int[][] cards = new int[3][4];
    private long openedAt, lastFrame, answeredAt;
    private float dt;
    private int chosen = -1;
    private Boolean correct;

    public AskScreen(int villager, String topic, String json) {
        super(Component.translatable("mobslearn.ask.title"));
        this.villager = villager;
        this.topic = topic;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement e : root.getAsJsonArray("options")) {
            List<ItemStack> grid = new ArrayList<>();
            JsonArray arr = e.getAsJsonObject().getAsJsonArray("grid");
            for (JsonElement id : arr) grid.add(Anim.stack(id.getAsString(), 1));
            grids.add(grid);
        }
        result = Anim.stack(root.get("result").getAsString(), 1);
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMillis();
        int pw = panelW();
        int bx = (width - 120) / 2, by = (height + panelH()) / 2 - 28;
        addRenderableWidget(new MlButton(bx, by, 120, 18, Component.translatable("mobslearn.ask.skip"), b -> decline()));
    }

    private int panelW() {
        return Math.min(400, width - 20);
    }

    private int panelH() {
        return Math.min(240, height - 20);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void result(boolean ok) {
        correct = ok;
        answeredAt = Util.getMillis();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ok ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, ok ? 1.4f : 1f));
    }

    private void decline() {
        if (chosen < 0 && com.rrm.mobslearn.client.CNet.canSend(Net.AnswerPayload.TYPE)) com.rrm.mobslearn.client.CNet.send(new Net.AnswerPayload(villager, -1));
        chosen = 99;
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        if (chosen < 0) decline();
        else minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        int k = keyCode;
        if (chosen < 0 && k >= InputConstants.KEY_1 && k <= InputConstants.KEY_3) {
            choose(k - InputConstants.KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (chosen >= 0) return false;
        for (int i = 0; i < 3; i++) {
            int[] c = cards[i];
            if (mouseX >= c[0] && mouseX < c[0] + c[2] && mouseY >= c[1] && mouseY < c[1] + c[3]) {
                choose(i);
                return true;
            }
        }
        return false;
    }

    private void choose(int i) {
        if (i >= grids.size()) return;
        chosen = i;
        Ui.click(1.2f);
        if (com.rrm.mobslearn.client.CNet.canSend(Net.AnswerPayload.TYPE)) com.rrm.mobslearn.client.CNet.send(new Net.AnswerPayload(villager, i));
    }

    public void mlBackground(GuiGraphics g, int mx, int my, float a) {
        float p = Anim.ease((Util.getMillis() - openedAt) / 250f);
        g.fill(0, 0, width, height, Anim.fade(0xB0000000, p));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float a) {
        mlBackground(g, mx, my, a);
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (correct != null && now - answeredAt > 1800) {
            minecraft.setScreen(null);
            return;
        }
        float p = Anim.ease((now - openedAt) / 320f);
        int pw = panelW(), ph = panelH();
        int x = (width - pw) / 2, y = (height - ph) / 2 + Math.round((1f - p) * 14f);
        g.fill(x, y, x + pw, y + ph, Anim.fade(0xF4101610, p));
        Ui.border(g, x, y, pw, ph, Anim.fade(0x40FFFFFF, p));
        g.fill(x, y, x + pw, y + 2, Anim.fade(Ui.GOLD, p));

        g.fill(x + 10, y + 10, x + 42, y + 42, Anim.fade(0x40000000, p));
        if (p > 0.5f) {
            g.pose().pushPose();
            g.pose().translate(x + 10, y + 10, 0);
            g.pose().scale(2f, 2f, 1);
            g.renderItem(new ItemStack(Items.VILLAGER_SPAWN_EGG), 0, 0);
            g.pose().popPose();
        }
        com.rrm.mobslearn.client.Ui.text(g, font, Component.translatable("mobslearn.ask.kicker"), x + 48, y + 13, Anim.fade(Ui.GOLD, p), false);
        Ui.scaledText(g, font, Component.translatable("mobslearn.ask.question." + topic), x + 48, y + 25, 1.3f, Anim.fade(0xFFFFFFFF, p), false, false);
        int ty = y + 48;
        for (FormattedCharSequence line : font.split(Component.translatable("mobslearn.ask.hint"), pw - 24)) {
            com.rrm.mobslearn.client.Ui.text(g, font, line, x + 12, ty, Anim.fade(Ui.MUTED, p), false);
            ty += 10;
        }

        int gap = 8, top = ty + 6;
        int cw = (pw - 24 - gap * 2) / 3, chh = SLOT * 3 + 40;
        for (int i = 0; i < grids.size() && i < 3; i++) {
            float local = Anim.ease((now - openedAt - 120 - i * 70L) / 300f);
            int cx = x + 12 + i * (cw + gap);
            int cy = top + Math.round((1f - local) * 10f);
            boolean hot = chosen < 0 && mx >= cx && mx < cx + cw && my >= cy && my < cy + chh;
            float hv = hover.getOrDefault(i, 0f);
            hv = Anim.approach(hv, hot ? 1f : 0f, dt, 12f);
            hover.put(i, hv);
            cy -= Math.round(hv * 3f);
            cards[i] = new int[]{cx, cy, cw, chh};
            int edge = Ui.GREEN;
            if (chosen == i && correct != null) edge = correct ? Ui.GREEN : Ui.RED;
            else if (chosen == i) edge = Ui.GOLD;
            float sel = chosen == i ? 1f : hv;
            g.fill(cx, cy, cx + cw, cy + chh, Anim.fade(Anim.lerp(0xFF121A11, 0xFF1B2819, sel), local));
            Ui.border(g, cx, cy, cw, chh, Anim.fade(Anim.lerp(0x30FFFFFF, edge, sel), local));
            if (chosen >= 0 && chosen != i) g.fill(cx + 1, cy + 1, cx + cw - 1, cy + chh - 1, Anim.fade(0x90000000, local));
            Component label = Component.translatable("mobslearn.ask.option", (char) ('A' + i));
            com.rrm.mobslearn.client.Ui.text(g, font, label, cx + 8, cy + 7, Anim.fade(Ui.CREAM, local), false);
            com.rrm.mobslearn.client.Ui.text(g, font, Component.literal(String.valueOf(i + 1)), cx + cw - 12, cy + 7, Anim.fade(0x60FFFFFF, local), false);
            int gx = cx + 8, gy = cy + 22;
            List<ItemStack> grid = grids.get(i);
            for (int s = 0; s < 9; s++) {
                int sx = gx + (s % 3) * SLOT, sy = gy + (s / 3) * SLOT;
                g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, Anim.fade(0x50000000, local));
                Ui.border(g, sx, sy, SLOT - 1, SLOT - 1, Anim.fade(0x18FFFFFF, local));
                if (local > 0.5f && s < grid.size() && !grid.get(s).isEmpty()) g.renderItem(grid.get(s), sx, sy);
            }
            int rx = gx + SLOT * 3 + 6;
            if (cw - (rx - cx) >= 34) {
                com.rrm.mobslearn.client.Ui.text(g, font, Component.literal("→"), rx, gy + SLOT + 4, Anim.fade(Ui.MUTED, local), false);
                g.fill(rx + 12, gy + SLOT - 2, rx + 34, gy + SLOT * 2 + 2, Anim.fade(0x50000000, local));
                if (local > 0.5f) g.renderItem(result, rx + 15, gy + SLOT + 1);
            }
            if (chosen == i && correct != null) {
                Component verdict = Component.translatable(correct ? "mobslearn.ask.right" : "mobslearn.ask.wrong");
                int vw = font.width(verdict) + 10;
                int vx = cx + (cw - vw) / 2, vy = cy + chh - 14;
                g.fill(vx, vy, vx + vw, vy + 12, correct ? 0xE02E6B16 : 0xE07A2420);
                com.rrm.mobslearn.client.Ui.text(g, font, verdict, vx + 5, vy + 2, 0xFFFFFFFF, false);
            }
        }
        if (correct != null) {
            Component msg = Component.translatable(correct ? "mobslearn.ask.thanks." + topic : "mobslearn.ask.again");
            Ui.center(g, font, msg, width / 2, top + chh + 10, correct ? Ui.GREEN : Ui.GOLD);
        } else if (chosen >= 0) {
            Ui.center(g, font, Component.translatable("mobslearn.ask.waiting"), width / 2, top + chh + 10, Ui.MUTED);
        }
        super.render(g, mx, my, a);
    }
}
