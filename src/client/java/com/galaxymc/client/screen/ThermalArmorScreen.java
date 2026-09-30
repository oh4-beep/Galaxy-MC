package com.galaxymc.client.screen;

import com.galaxymc.climate.ThermalData;
import com.galaxymc.climate.ThermalMaterials;
import com.galaxymc.menu.ThermalArmorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Thermal Armor Table: armour on the left, warming material (wool, thermal fibre, pyrocite...) in the
 * top slot, cooling material (ice, packed ice, cryonite...) in the bottom slot. Each wool adds a degree
 * of cold protection and each ice a degree of heat protection - the colder or hotter the world, the more
 * you stitch in. The screen shows what the piece will get and what your whole suit can then take.
 */
public class ThermalArmorScreen extends AbstractContainerScreen<ThermalArmorMenu> {
    public ThermalArmorScreen(ThermalArmorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelY = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.galaxy_mc.thermal.strip"), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ThermalArmorMenu.BUTTON_STRIP);
            }
        }).bounds(leftPos + 104, topPos + 66, 64, 14).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, x, y, imageWidth, imageHeight);
        Ui.slots(g, menu, x, y);
        g.text(font, title, x + 8, y + 6, Ui.ACCENT, false);
        g.text(font, playerInventoryTitle, x + 8, y + 73, Ui.TEXT_DIM, false);
        g.text(font, Component.translatable("screen.galaxy_mc.thermal.warm"), x + 96, y + 28, Ui.HOT, false);
        g.text(font, Component.translatable("screen.galaxy_mc.thermal.cool"), x + 96, y + 64 - 10, Ui.COLD, false);
        // Arrow into the result slot.
        g.fill(x + 100, y + 51, x + 128, y + 53, Ui.PANEL_EDGE);
        g.fill(x + 126, y + 49, x + 128, y + 55, Ui.PANEL_EDGE);
        ItemStack armor = menu.armor();
        if (!armor.isEmpty()) {
            ThermalData now = ThermalMaterials.total(armor);
            int warm = menu.addedWarmth();
            int cool = menu.addedCooling();
            g.text(font, Component.literal("❄ " + now.warmth() + (warm > 0 ? " +" + warm : "")), x + 8, y + 18, Ui.HOT, false);
            g.text(font, Component.literal("☀ " + now.cooling() + (cool > 0 ? " +" + cool : "")), x + 8, y + 66, Ui.COLD, false);
        }
        if (minecraft != null && minecraft.player != null) {
            int[] range = ThermalMaterials.suitRange(minecraft.player);
            String suit = range[0] + ".." + range[1] + "°C";
            g.text(font, Component.translatable("screen.galaxy_mc.thermal.suit", suit), x + 170 - font.width("Suit: " + suit) - 4, y + 6,
                    Ui.TEXT_DIM, false);
        }
    }
}
