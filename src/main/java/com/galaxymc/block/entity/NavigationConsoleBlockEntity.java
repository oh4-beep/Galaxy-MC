package com.galaxymc.block.entity;

import com.galaxymc.menu.NavigationMenu;
import com.galaxymc.registry.ModBlockEntities;
import com.galaxymc.ship.ShipFuel;
import com.galaxymc.ship.ShipType;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The ship's brain. Holds the fuel tank, the ship's identity and orientation, and the remembered
 * landing site on every world the ship has visited. It travels with the ship, so all of this survives
 * every jump. Fuel poured into the intake slot (by hand or hopper) is burned into the tank at once.
 */
public class NavigationConsoleBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<NavigationMenu.OpenData> {
    private static final int[] SLOTS = {0};
    private static final Codec<Map<String, Long>> LANDINGS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG);

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private ShipType shipType;
    private UUID shipId;
    private BlockPos anchor = BlockPos.ZERO;
    private Rotation rotation = Rotation.NONE;
    private double fuel;
    private final Map<String, Long> landings = new HashMap<>();
    private boolean launching;

    /** Fuel split into two 16-bit halves: the vanilla data-slot packet only carries shorts. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            long f = (long) Math.min(fuel, 0xFFFFFFFFL);
            return switch (index) {
                case 0 -> (int) (f & 0xFFFF);
                case 1 -> (int) ((f >> 16) & 0xFFFF);
                case 2 -> launching ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public NavigationConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAVIGATION_CONSOLE, pos, state);
    }

    // ------------------------------------------------------------------ ship data

    public boolean isShip() {
        return shipType != null;
    }

    public ShipType shipType() {
        return shipType;
    }

    public UUID shipId() {
        return shipId;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public Rotation rotation() {
        return rotation;
    }

    public double fuel() {
        return fuel;
    }

    public boolean launching() {
        return launching;
    }

    public void setLaunching(boolean v) {
        launching = v;
        setChanged();
    }

    public void initShip(ShipType type, BlockPos anchor, Rotation rotation) {
        this.shipType = type;
        this.shipId = UUID.randomUUID();
        this.anchor = anchor;
        this.rotation = rotation;
        setChanged();
    }

    public void moved(BlockPos newAnchor) {
        this.anchor = newAnchor;
        this.launching = false;
        setChanged();
    }

    public void spendFuel(double amount) {
        fuel = Math.max(0, fuel - amount);
        setChanged();
    }

    public void addFuel(double amount) {
        fuel = Math.min(capacity(), fuel + amount);
        setChanged();
    }

    public double capacity() {
        return shipType == null ? 0 : shipType.tankCapacity;
    }

    public BlockPos landing(String destination) {
        Long l = landings.get(destination);
        return l == null ? null : BlockPos.of(l);
    }

    public void rememberLanding(String destination, BlockPos anchorPos) {
        landings.put(destination, anchorPos.asLong());
        setChanged();
    }

    public ContainerData data() {
        return data;
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, NavigationConsoleBlockEntity be) {
        ItemStack stack = be.items.get(0);
        if (stack.isEmpty() || !be.isShip() || level.getGameTime() % 5 != 0) {
            return;
        }
        double perItem = ShipFuel.value(stack.copyWithCount(1), level);
        if (perItem <= 0) {
            return;
        }
        double room = be.capacity() - be.fuel;
        int fit = (int) Math.min(stack.getCount(), Math.floor(room / perItem));
        if (fit <= 0) {
            return;
        }
        be.addFuel(perItem * fit);
        if (stack.is(Items.LAVA_BUCKET)) {
            be.items.set(0, new ItemStack(Items.BUCKET));
        } else {
            stack.shrink(fit);
        }
        be.setChanged();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (shipType != null) {
            output.putString("ship_type", shipType.id);
            output.putString("ship_id", shipId.toString());
            output.putLong("anchor", anchor.asLong());
            output.putInt("rotation", rotation.ordinal());
        }
        output.putDouble("fuel", fuel);
        output.store("landings", LANDINGS_CODEC, landings);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        String type = input.getStringOr("ship_type", "");
        if (!type.isEmpty()) {
            shipType = ShipType.byId(type);
            shipId = UUID.fromString(input.getStringOr("ship_id", UUID.randomUUID().toString()));
            anchor = BlockPos.of(input.getLongOr("anchor", 0L));
            rotation = Rotation.values()[Math.floorMod(input.getIntOr("rotation", 0), 4)];
        }
        fuel = input.getDoubleOr("fuel", 0);
        landings.clear();
        input.read("landings", LANDINGS_CODEC).ifPresent(landings::putAll);
        launching = false;
    }

    // ------------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack r = ContainerHelper.removeItem(items, slot, count);
        setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isWithinBlockInteractionRange(worldPosition, 4.0) && !isRemoved();
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return level != null && ShipFuel.isFuel(stack, level);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return stack.is(Items.BUCKET);
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable(shipType == ShipType.STARSHIP ? "container.galaxy_mc.starship" : "container.galaxy_mc.rocket");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NavigationMenu(containerId, inventory, this, data, worldPosition);
    }

    @Override
    public NavigationMenu.OpenData getScreenOpeningData(ServerPlayer player) {
        return new NavigationMenu.OpenData(worldPosition, shipType == null ? -1 : shipType.ordinal());
    }
}
