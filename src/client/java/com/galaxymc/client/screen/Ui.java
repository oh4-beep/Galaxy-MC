package com.galaxymc.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Drawing helpers for Galaxy MC's texture-free, sci-fi styled screens. */
public final class Ui {
    private Ui() {}

    public static final int PANEL = 0xF0101820;
    public static final int PANEL_EDGE = 0xFF3A6AA8;
    public static final int PANEL_EDGE_DIM = 0xFF1E3050;
    public static final int INSET = 0xFF070B10;
    public static final int TEXT = 0xFFE0E8F0;
    public static final int TEXT_DIM = 0xFF8FA0B0;
    public static final int ACCENT = 0xFFFFD27A;
    public static final int GOOD = 0xFF7AF0A0;
    public static final int BAD = 0xFFFF7A6A;
    public static final int COLD = 0xFF7AC8FF;
    public static final int HOT = 0xFFFF9A5A;

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE_DIM);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE_DIM);
        // Corner brackets.
        g.fill(x, y, x + 6, y + 2, PANEL_EDGE);
        g.fill(x, y, x + 2, y + 6, PANEL_EDGE);
        g.fill(x + w - 6, y + h - 2, x + w, y + h, PANEL_EDGE);
        g.fill(x + w - 2, y + h - 6, x + w, y + h, PANEL_EDGE);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET);
        g.fill(x, y, x + w, y + 1, 0xFF000000);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF26384A);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF26384A);
        g.fill(x, y, x + 16, y + 16, 0xFF0C1218);
    }

    /** Frames every slot of a menu so vanilla slot rendering has a background to sit on. */
    public static void slots(GuiGraphicsExtractor g, AbstractContainerMenu menu, int left, int top) {
        for (Slot s : menu.slots) {
            slot(g, left + s.x, top + s.y);
        }
    }

    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int colour) {
        inset(g, x, y, w, h);
        int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, colour);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
        }
    }

    public static int tempColour(double t) {
        if (t < -40) {
            return COLD;
        }
        if (t > 60) {
            return HOT;
        }
        return GOOD;
    }
}
