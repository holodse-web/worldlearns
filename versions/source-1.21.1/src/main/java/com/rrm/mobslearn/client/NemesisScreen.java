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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public class NemesisScreen extends Screen {
    private record Foe(String id, String type, String trophy, String scar, String status, String heirOf, String dim, int first, int title,
                       int rank, int meetings, int kills, int x, int y, int z, long born, long seen) {
    }

    private static final List<Foe> foes = new ArrayList<>();
    private static boolean received, enabled = true;
    private static long receivedAt, day;

    private final Screen parent;
    private final Map<Integer, Float> hover = new HashMap<>();
    private final Map<Integer, Float> open = new HashMap<>();
    private final List<int[]> hits = new ArrayList<>();
    private int expanded = -1, contentH;
    private float scroll, target, dt;
    private long openedAt, lastFrame;

    public NemesisScreen(Screen parent) {
        super(Component.translatable("mobslearn.nemesis.title"));
        this.parent = parent;
    }

    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            foes.clear();
            day = root.has("day") ? root.get("day").getAsLong() : 0;
            enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            for (JsonElement e : root.getAsJsonArray("nemeses")) {
                JsonObject o = e.getAsJsonObject();
                foes.add(new Foe(s(o, "id"), s(o, "type"), s(o, "trophy"), s(o, "scar"), s(o, "status"), s(o, "heirOf"), s(o, "dim"),
                    n(o, "first"), n(o, "title"), n(o, "rank"), n(o, "meetings"), n(o, "kills"), n(o, "x"), n(o, "y"), n(o, "z"),
                    o.has("born") ? o.get("born").getAsLong() : 0, o.has("seen") ? o.get("seen").getAsLong() : 0));
            }
            received = true;
            receivedAt = Util.getMillis();
        } catch (RuntimeException ignored) {
        }
    }

    private static String s(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "";
    }

    private static int n(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsInt() : 0;
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
        addRenderableWidget(new MlButton(width / 2 - 50, height - 28, 100, 18, Component.translatable("mobslearn.gui.back"), b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        target = Mth.clamp(target - (float) sy * 30f, 0, Math.max(0, contentH - (height - 80)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        for (int[] h : hits) {
            if (mouseX >= h[1] && mouseX < h[1] + h[3] && mouseY >= h[2] && mouseY < h[2] + h[4]) {
                expanded = expanded == h[0] ? -1 : h[0];
                Ui.click(expanded >= 0 ? 1.3f : 1f);
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    }

    private static String icon(Foe f) {
        String path = f.type.contains(":") ? f.type.substring(f.type.indexOf(':') + 1) : f.type;
        return switch (path) {
            case "zombie", "husk", "drowned", "zombie_villager" -> "zombie_head";
            case "skeleton", "stray", "bogged" -> "skeleton_skull";
            case "spider", "cave_spider" -> "spider_spawn_egg";
            case "enderman" -> "enderman_spawn_egg";
            case "witch" -> "witch_spawn_egg";
            default -> "pillager_spawn_egg";
        };
    }

    private static int accent(Foe f) {
        return switch (f.status) {
            case "slain" -> Ui.GOLD;
            case "fallen" -> 0xFF8A8778;
            default -> f.rank >= 4 ? 0xFFB83A34 : Ui.RED;
        };
    }

    private static Component name(Foe f) {
        return Component.translatable("mobslearn.nemesis.name", Component.translatable("mobslearn.nemesis.first." + f.first),
            Component.translatable("mobslearn.nemesis.title." + f.title));
    }

    private List<Component> details(Foe f) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("mobslearn.nemesis.d.born", Math.max(0, day - f.born)));
        out.add(Component.translatable("mobslearn.nemesis.d.meetings", f.meetings, f.kills));
        if (!f.scar.isEmpty()) out.add(Component.translatable("mobslearn.nemesis.d.scar." + f.scar));
        if (!f.trophy.isEmpty()) out.add(Component.translatable("mobslearn.nemesis.d.trophy", Anim.stack(f.trophy, 1).getHoverName()));
        if (!f.heirOf.isEmpty()) out.add(Component.translatable("mobslearn.nemesis.d.heir"));
        out.add(Component.translatable("mobslearn.nemesis.d.tip." + (f.status.equals("alive") ? "alive" : f.status)));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0150D0D, 0xFA0C0808);
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (now - receivedAt > 4000 && now - openedAt > 500) {
            receivedAt = now;
            request();
        }
        scroll = Anim.approach(scroll, target, dt, 13f);
        int w = Math.min(470, width - 24), x = (width - w) / 2, top = 44, bottom = height - 36;
        float intro = Anim.ease((now - openedAt) / 300f);
        Ui.scaledText(g, font, title, width / 2, 12, 1.3f, Anim.fade(0xFFE5675F, intro), true, false);
        int alive = 0;
        for (Foe f : foes) if (f.status.equals("alive")) alive++;
        Component sub = !enabled ? Component.translatable("mobslearn.nemesis.sub_off") : Component.translatable("mobslearn.nemesis.sub", alive);
        Ui.center(g, font, sub, width / 2, 29, Ui.MUTED);
        Ui.panel(g, x, top, w, bottom - top, 0xD8120D0D, 0x30FFFFFF);
        g.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        hits.clear();
        int y = top + 8 - Math.round(scroll);
        if (foes.isEmpty()) {
            Component empty = Component.translatable(received ? "mobslearn.nemesis.empty" : "mobslearn.settlements.loading");
            for (FormattedCharSequence line : font.split(empty, w - 28)) {
                com.rrm.mobslearn.client.Ui.text(g, font, line, x + 14, y + 4, Ui.MUTED, false);
                y += 11;
            }
        }
        double px = minecraft.player == null ? 0 : minecraft.player.getX(), pz = minecraft.player == null ? 0 : minecraft.player.getZ();
        float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
        String here = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        int baseH = 50;
        for (int i = 0; i < foes.size(); i++) {
            Foe f = foes.get(i);
            float local = Anim.ease((now - openedAt - i * 45L) / 320f);
            float op = Anim.approach(open.getOrDefault(i, 0f), expanded == i ? 1f : 0f, dt, 12f);
            open.put(i, op);
            List<Component> info = details(f);
            int detailH = 0;
            for (Component c : info) detailH += font.split(c, w - 80).size() * 10 + 2;
            int cardH = baseH + Math.round((detailH + 8) * Anim.ease(op));
            int cy = y + Math.round((1f - local) * 10f);
            boolean hot = mx >= x + 8 && mx < x + w - 8 && my >= cy && my < cy + baseH && my >= top && my < bottom;
            float hv = Anim.approach(hover.getOrDefault(i, 0f), hot ? 1f : 0f, dt, 12f);
            hover.put(i, hv);
            hits.add(new int[]{i, x + 8, cy, w - 16, baseH});
            boolean living = f.status.equals("alive");
            int accent = accent(f);
            g.fill(x + 8, cy, x + w - 8, cy + cardH, Anim.fade(Anim.lerp(0xFF1A1111, 0xFF261818, Math.max(hv, op * 0.6f)), local));
            Ui.border(g, x + 8, cy, w - 16, cardH, Anim.fade(Anim.lerp(0x22FFFFFF, accent, Math.max(hv, op)), local));
            g.fill(x + 8, cy, x + 10, cy + cardH, Anim.fade(accent, local));
            g.fill(x + 16, cy + 9, x + 48, cy + 41, Anim.fade(0x40000000, local));
            if (local > 0.5f) {
                com.rrm.mobslearn.client.Ui.blitUV(g,com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + icon(f) + ".png"), x + 16, cy + 9, x + 48, cy + 41, 0f, 1f, 0f, 1f);
                if (!living) {
                    g.fill(x + 16, cy + 9, x + 48, cy + 41, 0x90000000);
                    Ui.scaledText(g, font, Component.literal(f.status.equals("slain") ? "✔" : "✝"), x + 32, cy + 19, 1.6f, accent, true, false);
                }
            }
            Component name = name(f);
            com.rrm.mobslearn.client.Ui.text(g, font, name, x + 56, cy + 8, Anim.fade(living ? 0xFFFFFFFF : 0xFFB8B0A0, local), false);
            int chipX = x + 56 + font.width(name) + 6;
            Component chip = Component.translatable("mobslearn.nemesis.state." + f.status);
            int cw = font.width(chip) + 6;
            g.fill(chipX, cy + 6, chipX + cw, cy + 17, Anim.fade(0x40000000 | (accent & 0xFFFFFF), local));
            com.rrm.mobslearn.client.Ui.text(g, font, chip, chipX + 3, cy + 8, Anim.fade(accent, local), false);
            String skulls = "☠".repeat(Math.max(1, Math.min(5, f.rank))) + "·".repeat(Math.max(0, 5 - f.rank));
            com.rrm.mobslearn.client.Ui.text(g, font, Component.translatable("mobslearn.nemesis.rank", skulls), x + 56, cy + 21, Anim.fade(0xFFE5675F, local), false);
            Component where = Component.translatable("mobslearn.nemesis.seen", f.x, f.y, f.z, Math.max(0, day - f.seen));
            com.rrm.mobslearn.client.Ui.text(g, font, where, x + 56, cy + 33, Anim.fade(0xFF8A8070, local), false);
            if (!f.trophy.isEmpty() && local > 0.5f) {
                ItemStack trophy = Anim.stack(f.trophy, 1);
                int tx = x + w - 64;
                g.fill(tx - 2, cy + 7, tx + 18, cy + 27, Anim.fade(0x40000000, local));
                Ui.border(g, tx - 2, cy + 7, 20, 20, Anim.fade(0x40FFD25A, local));
                g.renderItem(trophy, tx, cy + 9);
            }
            if (living && here.equals(f.dim)) {
                double ddx = f.x + 0.5 - px, ddz = f.z + 0.5 - pz;
                String d = Math.round(Math.sqrt(ddx * ddx + ddz * ddz)) + " m";
                com.rrm.mobslearn.client.Ui.text(g, font, d, x + w - 16 - font.width(d), cy + 33, Anim.fade(Ui.CREAM, local), false);
                double angle = Math.toDegrees(Math.atan2(-ddx, ddz)) - yaw;
                String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
                int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
                Ui.scaledText(g, font, Component.literal(arrows[idx]), x + w - 24, cy + 9, 1.6f, Anim.fade(accent, local), true, false);
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
            }
            y += cardH + 6;
        }
        contentH = y + Math.round(scroll) - top + 4;
        g.disableScissor();
        super.render(g, mx, my, partial);
    }
}
