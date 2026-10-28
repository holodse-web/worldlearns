package com.rrm.mobslearn.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rrm.mobslearn.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SettlementsScreen extends Screen {
    private record Row(String kind, String state, int tier, int x, int y, int z, int pop, boolean neutral, boolean raiding, int projects,
                       boolean known, boolean chief, int peace, int houses, long days, boolean natural, boolean camp, int maxTier, int next, String id, int wood, int grudge) {
    }

    private static final List<Row> rows = new ArrayList<>();
    private static int warp = 1;
    private static boolean received;
    private static long receivedAt;

    private final Screen parent;
    private final Map<Integer, Float> hover = new HashMap<>();
    private final Map<Integer, Float> open = new HashMap<>();
    private final List<int[]> hits = new ArrayList<>();
    private final List<int[]> forgetHits = new ArrayList<>();
    private int expanded = -1;
    private float scroll, target, dt;
    private int contentH;
    private long openedAt, lastFrame;

    public SettlementsScreen(Screen parent) {
        super(Component.translatable("mobslearn.settlements.title"));
        this.parent = parent;
    }

    private static int num(JsonObject o, String k, int d) {
        return o.has(k) ? o.get(k).getAsInt() : d;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    public static void update(String json) {
        rows.clear();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        warp = num(root, "warp", 1);
        for (JsonElement e : root.getAsJsonArray("colonies")) {
            JsonObject o = e.getAsJsonObject();
            rows.add(new Row(o.get("kind").getAsString(), o.get("state").getAsString(), num(o, "tier", 1), num(o, "x", 0), num(o, "y", 0), num(o, "z", 0),
                num(o, "pop", 0), bool(o, "neutral"), bool(o, "raiding"), num(o, "projects", 0), bool(o, "known"), bool(o, "chief"),
                num(o, "peace", -1), num(o, "houses", 0), o.has("days") ? o.get("days").getAsLong() : 0, bool(o, "natural"), bool(o, "camp"),
                num(o, "maxTier", 1), num(o, "next", -1), o.has("id") ? o.get("id").getAsString() : "", num(o, "wood", 0), num(o, "grudge", 0)));
        }
        received = true;
        receivedAt = Util.getMillis();
    }

    private static void request() {
        if (ClientPlayNetworking.canSend(Net.ColoniesRequestPayload.TYPE)) ClientPlayNetworking.send(new Net.ColoniesRequestPayload(0));
    }

    @Override
    protected void init() {
        if (openedAt == 0) {
            openedAt = Util.getMillis();
            request();
        }
        addRenderableWidget(new MlButton(width / 2 - 55, height - 27, 110, 20, Component.translatable("mobslearn.gui.back"), b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        target = Mth.clamp(target - (float) sy * 30f, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        for (int[] h : forgetHits) {
            if (mouseX >= h[1] && mouseX < h[1] + h[3] && mouseY >= h[2] && mouseY < h[2] + h[4] && h[0] < rows.size()) {
                String id = rows.get(h[0]).id();
                if (!id.isEmpty() && ClientPlayNetworking.canSend(Net.ForgetPayload.TYPE)) ClientPlayNetworking.send(new Net.ForgetPayload(id));
                rows.remove(h[0]);
                expanded = -1;
                open.clear();
                hover.clear();
                Ui.click(0.8f);
                return true;
            }
        }
        for (int[] h : hits) {
            if (mouseX >= h[1] && mouseX < h[1] + h[3] && mouseY >= h[2] && mouseY < h[2] + h[4]) {
                expanded = expanded == h[0] ? -1 : h[0];
                Ui.click(expanded >= 0 ? 1.3f : 1f);
                return true;
            }
        }
        return false;
    }

    private int maxScroll() {
        return Math.max(0, contentH - (height - 80));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    }

    public void mlBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00E150D, 0xFA080C08);
    }

    private static String icon(Row r) {
        if (!r.state.equals("active")) return "cobweb";
        return switch (r.kind) {
            case "village" -> r.camp ? "campfire" : "bell";
            case "skeleton" -> "skeleton_skull";
            case "creeper" -> "creeper_head";
            default -> "zombie_head";
        };
    }

    private List<Component> details(Row r) {
        List<Component> out = new ArrayList<>();
        boolean hostile = !r.kind.equals("village");
        out.add(Component.translatable(r.natural ? "mobslearn.settlements.d.natural" : "mobslearn.settlements.d.command", r.days));
        if (r.camp) out.add(Component.translatable("mobslearn.settlements.d.camp"));
        if (hostile && r.state.equals("active")) {
            out.add(Component.translatable(r.chief ? "mobslearn.settlements.d.chief" : "mobslearn.settlements.d.nochief"));
            out.add(r.neutral ? Component.translatable("mobslearn.settlements.d.neutral")
                : Component.translatable("mobslearn.settlements.d.peace", Math.max(0, r.peace)));
        }
        out.add(Component.translatable("mobslearn.settlements.d.houses", r.houses, r.projects));
        if (!hostile && r.state.equals("active")) out.add(Component.translatable("mobslearn.settlements.d.wood", r.wood));
        if (r.grudge >= 20) out.add(Component.translatable("mobslearn.settlements.d.banned", (r.grudge + 1) / 2));
        else if (r.grudge > 0) out.add(Component.translatable("mobslearn.settlements.d.grudge", r.grudge));
        if (r.state.equals("active")) {
            out.add(r.next < 0 ? Component.translatable("mobslearn.settlements.d.max", r.maxTier)
                : Component.translatable("mobslearn.settlements.d.next", r.next, r.projects > 0 ? Component.translatable("mobslearn.settlements.d.wait")
                : Component.empty()));
        }
        out.add(Component.translatable("mobslearn.settlements.d.tip." + (r.state.equals("active") ? r.kind : "ruins")));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        mlBackground(g, mx, my, partial);
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (now - receivedAt > 3000 && now - openedAt > 500) {
            receivedAt = now;
            request();
        }
        scroll = Anim.approach(scroll, target, dt, 13f);
        int w = Math.min(470, width - 24), x = (width - w) / 2, top = 44, bottom = height - 36;
        Ui.scaledText(g, font, title, width / 2, 12, 1.3f, Ui.GREEN, true, false);
        Component sub = warp > 1 ? Component.translatable("mobslearn.settlements.sub_warp", warp) : Component.translatable("mobslearn.settlements.sub");
        Ui.center(g, font, sub, width / 2, 29, Ui.MUTED);
        Ui.panel(g, x, top, w, bottom - top);
        g.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        hits.clear();
        forgetHits.clear();
        int y = top + 8 - Math.round(scroll);
        if (rows.isEmpty()) {
            Component empty = Component.translatable(received ? "mobslearn.settlements.empty" : "mobslearn.settlements.loading");
            for (FormattedCharSequence line : font.split(empty, w - 28)) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, x + 14, y + 4, Ui.MUTED, false);
                y += 11;
            }
        }
        double px = minecraft.player == null ? 0 : minecraft.player.getX(), pz = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        int baseH = 46;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float local = Anim.ease((now - openedAt - i * 40L) / 300f);
            float op = Anim.approach(open.getOrDefault(i, 0f), expanded == i ? 1f : 0f, dt, 12f);
            open.put(i, op);
            List<Component> info = details(r);
            int detailH = 0;
            for (Component c : info) detailH += font.split(c, w - 80).size() * 10 + 2;
            int cardH = baseH + Math.round((detailH + 26) * Anim.ease(op));
            int cy = y + Math.round((1f - local) * 8f);
            boolean hot = mx >= x + 8 && mx < x + w - 8 && my >= cy && my < cy + baseH && my >= top && my < bottom;
            float hv = Anim.approach(hover.getOrDefault(i, 0f), hot ? 1f : 0f, dt, 12f);
            hover.put(i, hv);
            hits.add(new int[]{i, x + 8, cy, w - 16, baseH});
            boolean active = r.state.equals("active");
            int accent = r.kind.equals("village") ? 0xFF7FC8BE : !active ? 0xFF8A8778 : r.neutral ? Ui.GOLD : Ui.RED;
            g.fill(x + 8, cy, x + w - 8, cy + cardH, Anim.fade(Anim.lerp(0xFF121A11, 0xFF1A261A, Math.max(hv, op * 0.6f)), local));
            Ui.border(g, x + 8, cy, w - 16, cardH, Anim.fade(Anim.lerp(0x22FFFFFF, accent, Math.max(hv, op)), local));
            g.fill(x + 8, cy, x + 10, cy + cardH, Anim.fade(accent, local));
            g.fill(x + 16, cy + 7, x + 48, cy + 39, Anim.fade(0x40000000, local));
            if (local > 0.5f) {
                com.rrm.mobslearn.client.Ui.blitUV(g,com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + icon(r) + ".png"), x + 16, cy + 7, x + 48, cy + 39, 0f, 1f, 0f, 1f);
            }
            Component name = Component.translatable(r.camp ? "mobslearn.settlements.kind.camp" : "mobslearn.settlements.kind." + r.kind);
            com.rrm.mobslearn.client.Ui.text(g, font, name, x + 56, cy + 7, Anim.fade(0xFFFFFFFF, local), false);
            int chipX = x + 56 + font.width(name) + 6;
            String stateKey = r.raiding ? "raiding" : !active ? r.state : !r.kind.equals("village") && r.neutral ? "neutral" : "active";
            Component chip = Component.translatable("mobslearn.settlements.state." + stateKey);
            int cw = font.width(chip) + 6;
            g.fill(chipX, cy + 5, chipX + cw, cy + 16, Anim.fade(0x40000000 | (accent & 0xFFFFFF), local));
            com.rrm.mobslearn.client.Ui.text(g, font, chip, chipX + 3, cy + 7, Anim.fade(accent, local), false);
            Component line = Component.translatable("mobslearn.settlements.info", r.tier, r.pop);
            if (r.projects > 0) line = line.copy().append(Component.translatable("mobslearn.settlements.building", r.projects));
            com.rrm.mobslearn.client.Ui.text(g, font, line, x + 56, cy + 19, Anim.fade(Ui.MUTED, local), false);
            Component where = r.known ? Component.translatable("mobslearn.settlements.where", r.x, r.y, r.z)
                : Component.translatable("mobslearn.settlements.where", "***", "***", "***");
            com.rrm.mobslearn.client.Ui.text(g, font, where, x + 56, cy + 31, Anim.fade(r.known ? 0xFF6E6A5C : 0xFF55524A, local), false);
            if (r.known) {
                double dx = r.x + 0.5 - px, dz = r.z + 0.5 - pz;
                String d = Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
                com.rrm.mobslearn.client.Ui.text(g, font, d, x + w - 16 - font.width(d), cy + 31, Anim.fade(Ui.CREAM, local), false);
                double angle = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
                String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
                int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
                Ui.scaledText(g, font, Component.literal(arrows[idx]), x + w - 24, cy + 9, 1.6f, Anim.fade(accent, local), true, false);
            } else {
                Component unknown = Component.translatable("mobslearn.settlements.unknown");
                com.rrm.mobslearn.client.Ui.text(g, font, unknown, x + w - 16 - font.width(unknown), cy + 31, Anim.fade(0xFF6E6A5C, local), false);
                Ui.scaledText(g, font, Component.literal("?"), x + w - 24, cy + 9, 1.6f, Anim.fade(0xFF6E6A5C, local), true, false);
            }
            if (op > 0.05f) {
                int dy = cy + baseH;
                g.fill(x + 56, dy - 2, x + w - 20, dy - 1, Anim.fade(0x18FFFFFF, op));
                for (Component c : info) {
                    for (FormattedCharSequence s : font.split(c, w - 80)) {
                        if (dy + 10 > cy + cardH) break;
                        com.rrm.mobslearn.client.Ui.text(g, font, s, x + 56, dy + 2, Anim.fade(Ui.CREAM, op * local), false);
                        dy += 10;
                    }
                    dy += 2;
                }
                if (op > 0.6f) {
                    Component forget = Component.translatable("mobslearn.settlements.forget");
                    int fw = font.width(forget) + 12, fx = x + w - 20 - fw, fy = cy + cardH - 18;
                    boolean fh = mx >= fx && mx < fx + fw && my >= fy && my < fy + 13;
                    g.fill(fx, fy, fx + fw, fy + 13, Anim.fade(fh ? 0x60E5534B : 0x30E5534B, op));
                    Ui.border(g, fx, fy, fw, 13, Anim.fade(fh ? Ui.RED : 0x60E5534B, op));
                    com.rrm.mobslearn.client.Ui.text(g, font, forget, fx + 6, fy + 3, Anim.fade(fh ? 0xFFFFFFFF : 0xFFF0A6A0, op), false);
                    forgetHits.add(new int[]{i, fx, fy, fw, 13});
                }
            }
            y += cardH + 6;
        }
        contentH = y + Math.round(scroll) - top + 4;
        target = Mth.clamp(target, 0, maxScroll());
        g.disableScissor();
        super.render(g, mx, my, partial);
    }
}
