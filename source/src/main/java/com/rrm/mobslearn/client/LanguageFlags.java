package com.rrm.mobslearn.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class LanguageFlags {
    private LanguageFlags() {}

    static void draw(GuiGraphicsExtractor g, String code, float x, float y, float width, float height) {
        Ui.fill(g, x - 1, y - 1, width + 2, height + 2, 0x60000000);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(width / 24f, height / 16f);
        switch (code) {
            case "ru_ru" -> horizontal(g, new int[]{0xFFF9F9F4, 0xFF2153AF, 0xFFCB343D});
            case "uk_ua" -> horizontal(g, new int[]{0xFF2677C9, 0xFFF5D446});
            case "de_de" -> horizontal(g, new int[]{0xFF202324, 0xFFC9343E, 0xFFF2C544});
            case "fr_fr" -> {
                Ui.fill(g, 0, 0, 8, 16, 0xFF26489B);
                Ui.fill(g, 8, 0, 8, 16, 0xFFF9F9F4);
                Ui.fill(g, 16, 0, 8, 16, 0xFFD7434D);
            }
            case "es_es" -> {
                Ui.fill(g, 0, 0, 24, 16, 0xFFC92E3E);
                Ui.fill(g, 0, 4, 24, 8, 0xFFF4C748);
                Ui.fill(g, 6, 6, 3, 5, 0xFFB83C3D);
                Ui.fill(g, 6, 6, 3, 1, 0xFFF4E1A2);
            }
            case ModLanguage.AUTO -> {
                Ui.fill(g, 0, 0, 24, 16, 0xFF263E35);
                Ui.border(g, 5, 1, 14, 14, 0xFFAFD69F);
                Ui.fill(g, 5, 5, 14, 1, 0xFFAFD69F);
                Ui.fill(g, 5, 10, 14, 1, 0xFFAFD69F);
                Ui.fill(g, 11, 1, 2, 14, 0xFFAFD69F);
                Ui.fill(g, 8, 2, 1, 12, 0xFF6C9967);
                Ui.fill(g, 15, 2, 1, 12, 0xFF6C9967);
            }
            default -> {
                for (int i = 0; i < 13; i++) Ui.fill(g, 0, i * 16f / 13f, 24, 16f / 13f, i % 2 == 0 ? 0xFFC7434E : 0xFFF9F9F4);
                Ui.fill(g, 0, 0, 11, 9, 0xFF2E497F);
                for (int row = 0; row < 3; row++) for (int col = 0; col < 4; col++)
                    Ui.fill(g, 1.5f + col * 2.5f, 1.5f + row * 2.6f, 1, 1, 0xFFF9F9F4);
            }
        }
        Ui.fill(g, 0, 0, 24, 1, 0x24FFFFFF);
        Ui.fill(g, 0, 15, 24, 1, 0x22000000);
        g.pose().popMatrix();
    }

    private static void horizontal(GuiGraphicsExtractor g, int[] colors) {
        for (int i = 0; i < colors.length; i++) Ui.fill(g, 0, i * 16f / colors.length, 24, 16f / colors.length, colors[i]);
    }
}
