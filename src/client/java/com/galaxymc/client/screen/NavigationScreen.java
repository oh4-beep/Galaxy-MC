package com.galaxymc.client.screen;

import com.galaxymc.client.ClientState;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.menu.NavigationMenu;
import com.galaxymc.network.LaunchPayload;
import com.galaxymc.ship.ShipFuel;
import com.galaxymc.ship.ShipType;
import com.galaxymc.ship.Travel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The navigation console. Search any world by name, star or catalogue designation, narrow the list with
 * range and world-type filters, sort it, and see exactly how much coal each jump costs against what is
 * in the tank. Pour fuel into the intake slot - coal, coal blocks, lava buckets, fuel canisters, or coal
 * enchanted with Fuel Efficiency for up to 64x the value - and launch.
 */
public class NavigationScreen extends AbstractContainerScreen<NavigationMenu> {
    private enum Range { SYSTEM(0, "screen.galaxy_mc.nav.range.system"), NEAR(2, "screen.galaxy_mc.nav.range.near"),
        FAR(5, "screen.galaxy_mc.nav.range.far");

        final int sectors;
        final String key;

        Range(int sectors, String key) {
            this.sectors = sectors;
            this.key = key;
        }
    }

    private enum Filter { ALL, HABITABLE, HOT, COLD, WATER, ROCKY, GIANTS, STARS, RICH }

    private enum Sort { FUEL, DISTANCE, DANGER, NAME }

    private static final int LIST_X = 8;
    private static final int LIST_Y = 36;
    private static final int LIST_W = 190;
    private static final int LIST_H = 110;
    private static final int ROW = 11;
    private static final int INFO_X = 202;

    private static Range range = Range.NEAR;
    private static Filter filter = Filter.ALL;
    private static Sort sort = Sort.FUEL;

    private EditBox search;
    private Button rangeButton;
    private Button filterButton;
    private Button sortButton;
    private Button launchButton;
    private List<Travel.Destination> all = List.of();
    private List<Travel.Destination> shown = List.of();
    private Travel.Destination selected;
    private int scroll;
    private String lastQuery = "";

    public NavigationScreen(NavigationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        this.imageHeight = 232;
        // Titles are drawn by this screen in its own colours; park the vanilla labels off-screen.
        this.titleLabelY = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        search = new EditBox(font, x + 8, y + 17, 124, 14, Component.translatable("screen.galaxy_mc.nav.search"));
        search.setMaxLength(48);
        search.setHint(Component.translatable("screen.galaxy_mc.nav.search_hint"));
        search.setResponder(q -> refresh(false));
        addRenderableWidget(search);
        rangeButton = addRenderableWidget(Button.builder(rangeLabel(), b -> {
            range = Range.values()[(range.ordinal() + 1) % Range.values().length];
            b.setMessage(rangeLabel());
            refresh(true);
        }).bounds(x + 136, y + 16, 58, 16).build());
        filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            b.setMessage(filterLabel());
            refresh(false);
        }).bounds(x + 196, y + 16, 62, 16).build());
        sortButton = addRenderableWidget(Button.builder(sortLabel(), b -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            b.setMessage(sortLabel());
            refresh(false);
        }).bounds(x + 260, y + 16, 52, 16).build());
        launchButton = addRenderableWidget(Button.builder(Component.translatable("screen.galaxy_mc.nav.launch"), b -> launch())
                .bounds(x + INFO_X + 4, y + LIST_Y + LIST_H - 20, 102, 18).build());
        refresh(true);
    }

    private Component rangeLabel() {
        return Component.translatable(range.key);
    }

    private Component filterLabel() {
        return Component.translatable("screen.galaxy_mc.nav.filter." + filter.name().toLowerCase(Locale.ROOT));
    }

    private Component sortLabel() {
        return Component.translatable("screen.galaxy_mc.nav.sort." + sort.name().toLowerCase(Locale.ROOT));
    }

    private ShipType ship() {
        return menu.shipType() == null ? ShipType.ROCKET : menu.shipType();
    }

    /** Recomputes destinations when the range changes; re-filters and re-sorts otherwise. */
    private void refresh(boolean recompute) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        String query = search == null ? "" : search.getValue().trim();
        if (recompute || !query.equals(lastQuery)) {
            lastQuery = query;
            Travel.Location from = Travel.locate(ClientState.galaxySeed, minecraft.level.dimension(), menu.pos().getX(), menu.pos().getZ());
            int sectors = ship().interstellar ? range.sectors : 0;
            all = Travel.destinations(ClientState.galaxySeed, from, sectors, query);
        }
        List<Travel.Destination> list = new ArrayList<>();
        for (Travel.Destination d : all) {
            if (range == Range.SYSTEM && !d.sameSystem()) {
                continue;
            }
            if (!ship().interstellar && !d.sameSystem()) {
                continue;
            }
            if (accepts(d)) {
                list.add(d);
            }
        }
        Comparator<Travel.Destination> cmp = switch (sort) {
            case FUEL -> Comparator.comparingDouble(Travel.Destination::fuel);
            case DISTANCE -> Comparator.comparingDouble(Travel.Destination::distanceLy).thenComparingDouble(Travel.Destination::au);
            case DANGER -> Comparator.comparingInt(Travel.Destination::danger);
            case NAME -> Comparator.comparing(Travel.Destination::name);
        };
        list.sort(cmp);
        shown = list;
        if (selected != null && !shown.contains(selected)) {
            selected = null;
        }
        if (selected == null && !shown.isEmpty()) {
            selected = shown.get(0);
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - LIST_H / ROW));
    }

    private boolean accepts(Travel.Destination d) {
        PlanetType t = d.type();
        return switch (filter) {
            case ALL -> true;
            case HABITABLE -> d.temp() > -25 && d.temp() < 55 && t != PlanetType.GAS_GIANT && t != PlanetType.STELLAR;
            case HOT -> d.temp() >= 60;
            case COLD -> d.temp() <= -20;
            case WATER -> t == PlanetType.OCEAN || t == PlanetType.JUNGLE || t == PlanetType.GRASSLAND || t == PlanetType.TUNDRA
                    || t == PlanetType.TOXIC || t == PlanetType.FUNGAL;
            case ROCKY -> t == PlanetType.BARREN_ROCK || t == PlanetType.CRATERED || t == PlanetType.CANYON || t == PlanetType.CRYSTAL
                    || t == PlanetType.SHATTERED;
            case GIANTS -> t == PlanetType.GAS_GIANT;
            case STARS -> t == PlanetType.STELLAR;
            case RICH -> d.tier() >= 3;
        };
    }

    private boolean affordable(Travel.Destination d) {
        return minecraft != null && minecraft.player != null && minecraft.player.hasInfiniteMaterials() || menu.fuel() >= d.fuel();
    }

    private void launch() {
        if (selected != null && !menu.launching()) {
            ClientPlayNetworking.send(new LaunchPayload(menu.pos(), selected.id()));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && !event.isEscape()) {
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inList(mx, my)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(sy) * 2, 0, Math.max(0, shown.size() - LIST_H / ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && inList(event.x(), event.y())) {
            int row = (int) (event.y() - (topPos + LIST_Y + 1)) / ROW + scroll;
            if (row >= 0 && row < shown.size()) {
                selected = shown.get(row);
                if (doubleClick) {
                    launch();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean inList(double mx, double my) {
        return mx >= leftPos + LIST_X && mx < leftPos + LIST_X + LIST_W && my >= topPos + LIST_Y && my < topPos + LIST_Y + LIST_H;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (launchButton != null) {
            launchButton.active = selected != null && !menu.launching() && affordable(selected)
                    && (ship().interstellar || selected.sameSystem());
            launchButton.setMessage(Component.translatable(menu.launching() ? "screen.galaxy_mc.nav.launching" : "screen.galaxy_mc.nav.launch"));
        }
        extractBackground(g, mouseX, mouseY, delta);
        super.extractRenderState(g, mouseX, mouseY, delta);
        extractTooltip(g, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, x, y, imageWidth, imageHeight);
        Ui.inset(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        Ui.inset(g, x + INFO_X, y + LIST_Y, imageWidth - INFO_X - 8, LIST_H);
        Ui.slots(g, menu, x, y);
        drawList(g, x, y, mouseX, mouseY);
        drawInfo(g, x, y);
        drawTank(g, x, y);
        g.text(font, title, x + 8, y + 5, Ui.ACCENT, false);
        g.text(font, playerInventoryTitle, x + NavigationMenu.INV_X, y + NavigationMenu.INV_Y - 11, Ui.TEXT_DIM, false);
        String count = shown.size() + " / " + all.size();
        g.text(font, Component.literal(count), x + imageWidth - 8 - font.width(count), y + 5, Ui.TEXT_DIM, false);
    }

    private void drawList(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int visible = LIST_H / ROW;
        if (shown.isEmpty()) {
            g.text(font, Component.translatable("screen.galaxy_mc.nav.nothing"), x + LIST_X + 6, y + LIST_Y + 6, Ui.TEXT_DIM, false);
            return;
        }
        for (int i = 0; i < visible && i + scroll < shown.size(); i++) {
            Travel.Destination d = shown.get(i + scroll);
            int ry = y + LIST_Y + 1 + i * ROW;
            boolean hover = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W && mouseY >= ry && mouseY < ry + ROW;
            if (d == selected) {
                g.fill(x + LIST_X + 1, ry, x + LIST_X + LIST_W - 1, ry + ROW, 0xFF1E3A5A);
            } else if (hover) {
                g.fill(x + LIST_X + 1, ry, x + LIST_X + LIST_W - 1, ry + ROW, 0xFF142434);
            }
            int typeColour = 0xFF000000 | (d.type() == null ? 0x4a90e2 : d.type().mapColor);
            g.fill(x + LIST_X + 3, ry + 3, x + LIST_X + 8, ry + 8, typeColour);
            String name = d.name();
            String fuel = ShipFuel.format(d.fuel());
            int maxName = LIST_W - 22 - font.width(fuel);
            while (font.width(name) > maxName && name.length() > 3) {
                name = name.substring(0, name.length() - 2);
            }
            if (!name.equals(d.name())) {
                name = name + "…";
            }
            g.text(font, Component.literal(name), x + LIST_X + 11, ry + 2, d.sameSystem() ? Ui.TEXT : 0xFFB8C8FF, false);
            g.text(font, Component.literal(fuel), x + LIST_X + LIST_W - 4 - font.width(fuel), ry + 2, affordable(d) ? Ui.GOOD : Ui.BAD, false);
        }
        if (shown.size() > visible) {
            int track = LIST_H - 2;
            int thumb = Math.max(8, track * visible / shown.size());
            int ty = (int) ((track - thumb) * (scroll / (double) Math.max(1, shown.size() - visible)));
            g.fill(x + LIST_X + LIST_W - 2, y + LIST_Y + 1 + ty, x + LIST_X + LIST_W - 1, y + LIST_Y + 1 + ty + thumb, Ui.PANEL_EDGE);
        }
    }

    private void drawInfo(GuiGraphicsExtractor g, int x, int y) {
        Travel.Destination d = selected;
        int ix = x + INFO_X + 4;
        int iy = y + LIST_Y + 4;
        if (d == null) {
            return;
        }
        g.text(font, Component.literal(d.name()), ix, iy, Ui.ACCENT, false);
        iy += 10;
        g.text(font, Component.literal(d.typeName()), ix, iy, Ui.TEXT_DIM, false);
        iy += 10;
        g.text(font, Component.literal(d.star().name() + (d.sameSystem() ? "" : String.format(" (%.1f ly)", d.distanceLy()))), ix, iy,
                Ui.TEXT_DIM, false);
        iy += 12;
        g.text(font, Component.literal(String.format("%d°C ±%d", Math.round(d.temp()), Math.round(d.swing()))), ix, iy,
                Ui.tempColour(d.temp()), false);
        g.text(font, Component.literal(String.format("%.2fg", d.gravity())), ix + 62, iy, Ui.TEXT, false);
        iy += 10;
        int danger = d.danger();
        g.text(font, Component.translatable("screen.galaxy_mc.nav.danger", danger), ix, iy, danger >= 7 ? Ui.BAD : danger >= 4 ? Ui.HOT : Ui.GOOD, false);
        g.text(font, Component.translatable("screen.galaxy_mc.nav.tier", d.tier()), ix + 62, iy, Ui.TEXT, false);
        iy += 12;
        boolean ok = affordable(d);
        g.text(font, Component.translatable("screen.galaxy_mc.nav.fuel_needed", ShipFuel.format(d.fuel())), ix, iy, ok ? Ui.GOOD : Ui.BAD, false);
        iy += 10;
        if (!ship().interstellar && !d.sameSystem()) {
            g.text(font, Component.translatable("screen.galaxy_mc.nav.need_starship"), ix, iy, Ui.BAD, false);
        } else if (!ok) {
            g.text(font, Component.translatable("screen.galaxy_mc.nav.short", ShipFuel.format(d.fuel() - menu.fuel())), ix, iy, Ui.BAD, false);
        }
    }

    private void drawTank(GuiGraphicsExtractor g, int x, int y) {
        double cap = ship().tankCapacity;
        double fuel = menu.fuel();
        int bx = x + 180;
        int by = y + NavigationMenu.INV_Y + 4;
        g.text(font, Component.translatable("screen.galaxy_mc.nav.tank"), bx, by - 10, Ui.TEXT_DIM, false);
        Ui.bar(g, bx, by, 110, 8, fuel / cap, 0xFFE0A030);
        g.text(font, Component.literal(ShipFuel.format(fuel) + " / " + ShipFuel.format(cap)), bx, by + 11, Ui.TEXT, false);
        g.text(font, Component.translatable("screen.galaxy_mc.nav.intake"), bx, by + 26, Ui.TEXT_DIM, false);
        ItemStack stack = menu.getSlot(0).getItem();
        if (!stack.isEmpty() && minecraft != null && minecraft.level != null) {
            double v = ShipFuel.value(stack, minecraft.level);
            g.text(font, Component.translatable("screen.galaxy_mc.nav.intake_value", ShipFuel.format(v)), bx, by + 38, Ui.ACCENT, false);
        }
        int level = ShipFuel.efficiencyLevel(stack);
        if (level > 0) {
            g.text(font, Component.literal("x" + (int) ShipFuel.multiplier(level)), bx + 90, by + 38, Ui.GOOD, false);
        }
    }
}
