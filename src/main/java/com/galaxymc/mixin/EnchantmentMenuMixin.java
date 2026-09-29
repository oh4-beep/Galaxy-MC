package com.galaxymc.mixin;

import com.galaxymc.menu.FuelStackSlot;
import com.galaxymc.ship.ShipFuel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the enchanting table hold and enchant a full stack of ship fuel (see {@link FuelStackSlot}). */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @ModifyArg(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/EnchantmentMenu;addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;",
                    ordinal = 0))
    private Slot galaxy_mc$fuelStackSlot(Slot original) {
        return new FuelStackSlot(original.container, original.getContainerSlot(), original.x, original.y);
    }

    /** Shift-clicking fuel moves the whole stack in, instead of vanilla's single item. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void galaxy_mc$quickMoveFuel(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        EnchantmentMenu self = (EnchantmentMenu) (Object) this;
        if (index < 2 || index >= self.slots.size()) {
            return;
        }
        Slot from = self.slots.get(index);
        Slot input = self.slots.get(0);
        ItemStack stack = from.getItem();
        if (stack.isEmpty() || !stack.is(ShipFuel.FUEL_ENCHANTABLE) || input.hasItem() || !input.mayPlace(stack)) {
            return;
        }
        input.setByPlayer(stack.copy());
        from.setByPlayer(ItemStack.EMPTY);
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
