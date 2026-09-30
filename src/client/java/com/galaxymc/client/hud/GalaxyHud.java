package com.galaxymc.client.hud;

import com.galaxymc.client.ClientState;
import com.galaxymc.client.hazard.HazardEffects;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.network.ClimatePayload;
import com.galaxymc.network.HazardPayload;
import com.galaxymc.util.Hash;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The spacefarer's HUD: a climate panel (where you are, how hot it is, what your suit can take and how
 * close you are to freezing or cooking), frost and heat vignettes that creep in as stress builds, and
 * the hyperspace streaks of a jump in progress.
 */
public final class GalaxyHud {
    private GalaxyHud() {}

    public static void climate(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ClimatePayload c = ClientState.climate;
        if (!c.active() || mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }
        Font font = mc.font;
        int x = 6;
        int y = 6;
        int w = 132;
        HazardPayload.Entry hazard = HazardEffects.nearest(mc.player.getX(), mc.player.getZ(), 450);
        int h = (c.lifeSupport() ? 58 : 48) + (hazard != null ? 11 : 0);
        g.fill(x - 2, y - 2, x + w, y + h, 0x90000000);
        g.fill(x - 2, y - 2, x + w, y - 1, 0xFF3A6AA8);
        g.text(font, Component.literal(c.planet()), x + 2, y + 1, 0xFFFFE08A, true);
        int ambient = Math.round(c.ambient());
        int col = ambient < c.low() ? 0xFF7AC8FF : ambient > c.high() ? 0xFFFF7A4A : 0xFF9AFF9A;
        g.text(font, Component.literal(ambient + "°C"), x + 2, y + 12, col, true);
        String range = c.low() <= -9000 ? "suit: n/a" : "suit " + c.low() + ".." + c.high() + "°C";
        g.text(font, Component.literal(range), x + 44, y + 12, 0xFFC8C8C8, true);
        // Stress bar: centre is comfortable, left freezing, right cooking.
        int bx = x + 2;
        int by = y + 25;
        int bw = w - 8;
        g.fill(bx, by, bx + bw, by + 6, 0xFF202020);
        g.fill(bx, by, bx + bw / 2, by + 6, 0xFF1A3050);
        g.fill(bx + bw / 2, by, bx + bw, by + 6, 0xFF502A1A);
        float stress = Mth.clamp(c.stress(), -100, 100);
        int mid = bx + bw / 2;
        int end = mid + (int) (stress / 100.0F * (bw / 2.0F));
        int sc = stress < 0 ? 0xFF6AB8FF : 0xFFFF6A2A;
        if (Math.abs(stress) >= 75 && (mc.player.tickCount / 5) % 2 == 0) {
            sc = 0xFFFFFFFF;
        }
        g.fill(Math.min(mid, end), by + 1, Math.max(mid, end) + 1, by + 5, sc);
        g.fill(mid, by - 1, mid + 1, by + 7, 0xFFFFFFFF);
        g.text(font, Component.literal(String.format("%.2fg", c.gravity())), x + 2, y + 34, 0xFFB0B0FF, true);
        String state = Math.abs(stress) < 20 ? "stable" : stress < 0 ? (stress < -75 ? "HYPOTHERMIA" : "freezing")
                : (stress > 75 ? "HEATSTROKE" : "overheating");
        g.text(font, Component.literal(state), x + 44, y + 34, Math.abs(stress) >= 75 ? 0xFFFF5050 : 0xFFD0D0D0, true);
        int line = y + 45;
        if (c.lifeSupport()) {
            g.text(font, Component.translatable("hud.galaxy_mc.life_support"), x + 2, line, 0xFF60F0E0, true);
            line += 11;
        }
        if (hazard != null) {
            String where = "";
            double[] pos = HazardEffects.position(hazard.id());
            if (pos != null && hazard.type() != Hazard.METEORS && hazard.type() != Hazard.LIGHTNING) {
                double dx = pos[0] - mc.player.getX();
                double dz = pos[1] - mc.player.getZ();
                String[] dirs = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
                where = " " + Math.round(Math.hypot(dx, dz)) + "m " + dirs[Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0), 8)];
            }
            boolean blink = (mc.player.tickCount / 8) % 2 == 0;
            g.text(font, Component.literal("\u26A0 " + hazard.name() + where), x + 2, line, blink ? 0xFFFF5040 : 0xFFFFB040, true);
        }
    }

    public static void vignette(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ClimatePayload c = ClientState.climate;
        if (!c.active() || mc.player == null) {
            return;
        }
        float s = c.stress();
        float t = Mth.clamp((Math.abs(s) - 35.0F) / 65.0F, 0.0F, 1.0F);
        if (t <= 0) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int rgb = s < 0 ? 0xA8D8FF : 0xFF5A10;
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            float k = 1.0F - i / (float) bands;
            int a = (int) (t * 140 * k * k);
            int inset = (int) (i * Math.min(w, h) * 0.035F);
            int color = ARGB.color(a, rgb);
            int thick = (int) Math.max(2, Math.min(w, h) * 0.035F);
            g.fill(inset, inset, w - inset, inset + thick, color);
            g.fill(inset, h - inset - thick, w - inset, h - inset, color);
            g.fill(inset, inset + thick, inset + thick, h - inset - thick, color);
            g.fill(w - inset - thick, inset + thick, w - inset, h - inset - thick, color);
        }
        if (s > 0 && t > 0.3F) {
            // Heat shimmer: a few drifting translucent bands.
            int time = mc.player.tickCount;
            for (int i = 0; i < 6; i++) {
                int yy = (int) ((time * 1.7 + i * h / 6.0) % h);
                g.fill(0, yy, w, yy + 2, ARGB.color((int) (30 * t), 0xFFA040));
            }
        }
    }

    public static void warp(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (ClientState.warpTicks <= 0) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float age = ClientState.warpTotal - ClientState.warpTicks + partial;
        float fadeIn = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(ClientState.warpTicks / 10.0F, 0.0F, 1.0F);
        float alpha = Math.min(fadeIn, fadeOut);
        g.fill(0, 0, w, h, ARGB.color((int) (alpha * 215), 0x02030A));
        float cx = w / 2.0F;
        float cy = h / 2.0F;
        int streaks = ClientState.warpInterstellar ? 160 : 90;
        int tint = ClientState.warpInterstellar ? 0xB8D8FF : 0xFFE8C0;
        for (int i = 0; i < streaks; i++) {
            long hsh = Hash.of(1234, i);
            float angle = (float) (Hash.unit(hsh) * Math.PI * 2.0);
            float speed = 0.6F + (float) Hash.unit(hsh, 1) * 1.4F;
            float phase = (float) Hash.unit(hsh, 2);
            float r = ((age * 0.025F * speed + phase) % 1.0F);
            float start = r * r * Math.max(w, h) * 0.9F;
            float len = 6 + r * r * 140 * speed;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate(angle);
            int a = (int) (alpha * 255 * Math.min(1.0F, r * 3.0F));
            g.fill((int) start, 0, (int) (start + len), 1 + (int) (r * 2), ARGB.color(a, tint));
            g.pose().popMatrix();
        }
        Minecraft mc = Minecraft.getInstance();
        Component caption = Component.translatable(ClientState.warpInterstellar ? "hud.galaxy_mc.warp_interstellar" : "hud.galaxy_mc.warp",
                ClientState.warpDestination);
        g.centeredText(mc.font, caption, w / 2, h - 60, ARGB.color((int) (alpha * 255), 0xFFFFFF));
    }
}
