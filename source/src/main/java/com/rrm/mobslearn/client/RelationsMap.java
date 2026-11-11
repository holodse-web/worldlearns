package com.rrm.mobslearn.client;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draws known settlements as a little map with coloured lines for their relations (war, friendship, alliance, peace). */
public final class RelationsMap {
    public record Node(String id, String kind, String icon, int x, int z, int accent, boolean known) {
    }

    public record Link(String a, String b, String rel) {
    }

    private RelationsMap() {
    }

    public static int color(String rel) {
        return switch (rel) {
            case "war" -> 0xFFE5534B;
            case "friend" -> 0xFF8BE03A;
            case "ally" -> 0xFFFFD25A;
            default -> 0xFF8A8778;
        };
    }

    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, boolean dashed) {
        double dx = x1 - x0, dy = y1 - y0, len = Math.sqrt(dx * dx + dy * dy);
        int steps = (int) Math.max(1, len / 2);
        for (int i = 0; i <= steps; i++) {
            if (dashed && (i / 3) % 2 == 1) continue;
            int x = (int) Math.round(x0 + dx * i / steps), y = (int) Math.round(y0 + dy * i / steps);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    /** @return content height used */
    public static int draw(GuiGraphicsExtractor g, Font font, List<Node> nodes, List<Link> links, double px, double pz,
                           int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, 0x60000000);
        Ui.border(g, x, y, w, h, 0x22FFFFFF);
        double minX = px, maxX = px, minZ = pz, maxZ = pz;
        int shown = 0;
        for (Node n : nodes) {
            if (!n.known()) continue;
            minX = Math.min(minX, n.x());
            maxX = Math.max(maxX, n.x());
            minZ = Math.min(minZ, n.z());
            maxZ = Math.max(maxZ, n.z());
            shown++;
        }
        int pad = 24;
        double span = Math.max(64, Math.max(maxX - minX, maxZ - minZ));
        double scale = Math.min((w - pad * 2) / span, (h - pad * 2 - 14) / span);
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        int ox = x + w / 2, oy = y + (h - 14) / 2;
        Map<String, int[]> at = new java.util.HashMap<>();
        for (Node n : nodes) {
            if (!n.known()) continue;
            at.put(n.id(), new int[]{ox + (int) Math.round((n.x() - cx) * scale), oy + (int) Math.round((n.z() - cz) * scale)});
        }
        for (Link l : links) {
            int[] a = at.get(l.a()), b = at.get(l.b());
            if (a == null || b == null) continue;
            line(g, a[0], a[1], b[0], b[1], color(l.rel()), l.rel().equals("peace"));
        }
        int ppx = ox + (int) Math.round((px - cx) * scale), ppz = oy + (int) Math.round((pz - cz) * scale);
        g.fill(ppx - 2, ppz - 2, ppx + 3, ppz + 3, 0xFFFFFFFF);
        g.text(font, Component.translatable("mobslearn.map.you"), ppx + 5, ppz - 4, 0xFFFFFFFF, true);
        Component hoverName = null;
        int hx = 0, hy = 0;
        for (Node n : nodes) {
            int[] p = at.get(n.id());
            if (p == null) continue;
            g.fill(p[0] - 9, p[1] - 9, p[0] + 9, p[1] + 9, 0xA0000000);
            Ui.border(g, p[0] - 9, p[1] - 9, 18, 18, n.accent());
            g.blit(com.rrm.mobslearn.MobsLearn.id("textures/gui/icon/" + n.icon() + ".png"), p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7, 0f, 1f, 0f, 1f);
            if (mx >= p[0] - 9 && mx < p[0] + 9 && my >= p[1] - 9 && my < p[1] + 9) {
                hoverName = Component.translatable("mobslearn.settlements.kind." + n.kind()).copy().append(" " + n.x() + ", " + n.z());
                hx = p[0];
                hy = p[1];
            }
        }
        if (hoverName != null) {
            int tw = font.width(hoverName) + 8;
            g.fill(hx - tw / 2, hy - 24, hx + tw / 2, hy - 12, 0xE0101810);
            g.text(font, hoverName, hx - tw / 2 + 4, hy - 22, 0xFFFFFFFF, false);
        }
        if (shown == 0) Ui.center(g, font, Component.translatable("mobslearn.map.empty"), x + w / 2, oy - 4, Ui.MUTED);
        String[] rels = {"war", "friend", "ally", "peace"};
        int lx = x + 8, ly = y + h - 12;
        for (String r : rels) {
            Component label = Component.translatable("mobslearn.map.rel." + r);
            g.fill(lx, ly + 3, lx + 10, ly + 5, color(r));
            g.text(font, label, lx + 13, ly, Ui.CREAM, false);
            lx += font.width(label) + 24;
        }
        return h;
    }
}
