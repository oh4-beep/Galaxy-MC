package com.galaxymc.client.screen;

import com.galaxymc.menu.StellarForgeMenu;
import com.galaxymc.mineral.MineralData;
import com.galaxymc.registry.ModComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Stellar Forge: gear and a procedural mineral in, infused gear out, at one XP level per mineral tier. */
public class StellarForgeScreen extends AbstractContainerScreen<StellarForgeMenu> {
    public StellarForgeScreen(StellarForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelY = -10000;
        this.inventoryLabelY = -10000;
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
        g.text(font, Component.literal("+"), x + 58, y + 51, Ui.TEXT, false);
        g.fill(x + 100, y + 51, x + 128, y + 53, Ui.PANEL_EDGE);
        g.fill(x + 126, y + 49, x + 128, y + 55, Ui.PANEL_EDGE);
        MineralData m = menu.getSlot(StellarForgeMenu.MINERAL).getItem().get(ModComponents.MINERAL);
        if (m != null) {
            g.text(font, Component.literal(m.trait().adjective + ": " + m.trait().effect(m.power())), x + 8, y + 20,
                    0xFF000000 | m.trait().color, false);
            int cost = menu.levelCost();
            boolean ok = minecraft != null && minecraft.player != null
                    && (minecraft.player.hasInfiniteMaterials() || minecraft.player.experienceLevel >= cost);
            g.text(font, Component.translatable("screen.galaxy_mc.forge.cost", cost), x + 8, y + 66, ok ? Ui.GOOD : Ui.BAD, false);
        }
        String problem = menu.problem();
        if (problem != null) {
            g.text(font, Component.translatable(problem), x + 8, y + 32, Ui.BAD, false);
        }
    }
}
