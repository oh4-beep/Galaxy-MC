package com.galaxymc.command;

import com.galaxymc.world.PlanetChunkGenerator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Developer tool: renders a hill-shaded top-down map of a planet's terrain to PNG, straight from the
 * generator maths. Handy for tuning terrain without flying around.
 */
public final class TerrainPreview {
    private TerrainPreview() {}

    public static Path render(ServerLevel level, int cx, int cz, int radius, int size) throws IOException {
        if (!(level.getChunkSource().getGenerator() instanceof PlanetChunkGenerator gen)) {
            throw new IOException("not a Galaxy MC planet dimension");
        }
        double step = (radius * 2.0) / size;
        int[] heights = new int[size * size];
        int[] colors = new int[size * size];
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int x = (int) Math.floor(cx - radius + px * step);
                int z = (int) Math.floor(cz - radius + py * step);
                Object[] col = gen.previewColumn(x, z);
                int i = py * size + px;
                if (col == null) {
                    heights[i] = Integer.MIN_VALUE;
                    colors[i] = 0x05050a;
                    continue;
                }
                BlockState st = (BlockState) col[0];
                int y = (Integer) col[1];
                heights[i] = y;
                MapColor mc = st.getMapColor(level, BlockPos.ZERO);
                int rgb = st.getFluidState().isEmpty() ? mc.col : (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? 0xff6010 : 0x3a64c8);
                if (rgb == 0) {
                    rgb = 0x808080;
                }
                colors[i] = rgb;
            }
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int i = py * size + px;
                int h = heights[i];
                int rgb = colors[i];
                if (h != Integer.MIN_VALUE) {
                    int hl = px > 0 && heights[i - 1] != Integer.MIN_VALUE ? heights[i - 1] : h;
                    int hu = py > 0 && heights[i - size] != Integer.MIN_VALUE ? heights[i - size] : h;
                    double shade = 1.0 + ((h - hl) + (h - hu)) * 0.06 / Math.max(1.0, step * 0.5);
                    shade *= 0.75 + 0.25 * Math.max(0, Math.min(1, (h + 20) / 200.0));
                    rgb = scale(rgb, Math.max(0.35, Math.min(1.5, shade)));
                }
                img.setRGB(px, py, rgb);
            }
        }
        Path dir = level.getServer().getServerDirectory().resolve("galaxy_previews");
        Files.createDirectories(dir);
        String name = level.dimension().identifier().getPath() + "_" + cx + "_" + cz + "_r" + radius + ".png";
        Path out = dir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private static int scale(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
        int b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
