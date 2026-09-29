package com.galaxymc.ship;

import com.galaxymc.GalaxyMC;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A ship's shape: every hull block and every interior air cell, in blueprint space (x right, y up,
 * front towards -z), relative to an anchor at the centre of the bottom layer.
 *
 * <p>The blueprints are produced by {@code tools/blender/build_ships.py}, which models the ships in
 * Blender and voxelises them; the JSON lives in {@code data/galaxy_mc/ships}. Together the blocks and
 * the interior cells are the ship's volume - exactly what gets carried when it flies.
 */
public final class ShipBlueprint {
    public record Cell(BlockPos pos, BlockState state) {}

    public final String id;
    public final List<Cell> blocks;
    public final List<BlockPos> interior;
    public final BlockPos console;
    public final int radius;
    public final int height;

    private static final Map<ShipType, ShipBlueprint> CACHE = new EnumMap<>(ShipType.class);

    private ShipBlueprint(String id, List<Cell> blocks, List<BlockPos> interior, BlockPos console, int radius, int height) {
        this.id = id;
        this.blocks = blocks;
        this.interior = interior;
        this.console = console;
        this.radius = radius;
        this.height = height;
    }

    public static synchronized ShipBlueprint get(ShipType type) {
        return CACHE.computeIfAbsent(type, t -> load(t.id));
    }

    private static ShipBlueprint load(String id) {
        String path = "/data/galaxy_mc/ships/" + id + ".json";
        try (InputStream in = ShipBlueprint.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing ship blueprint " + path);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray anchor = root.getAsJsonArray("anchor");
            int ax = anchor.get(0).getAsInt();
            int ay = anchor.get(1).getAsInt();
            int az = anchor.get(2).getAsInt();
            List<BlockState> palette = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("palette")) {
                palette.add(parseState(e.getAsString()));
            }
            List<Cell> blocks = new ArrayList<>();
            int radius = 0;
            int height = 0;
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                JsonArray a = e.getAsJsonArray();
                BlockPos p = new BlockPos(a.get(0).getAsInt() - ax, a.get(1).getAsInt() - ay, a.get(2).getAsInt() - az);
                blocks.add(new Cell(p, palette.get(a.get(3).getAsInt())));
                radius = Math.max(radius, Math.max(Math.abs(p.getX()), Math.abs(p.getZ())));
                height = Math.max(height, p.getY() + 1);
            }
            List<BlockPos> interior = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("interior")) {
                JsonArray a = e.getAsJsonArray();
                interior.add(new BlockPos(a.get(0).getAsInt() - ax, a.get(1).getAsInt() - ay, a.get(2).getAsInt() - az));
            }
            JsonArray c = root.getAsJsonArray("console");
            BlockPos console = new BlockPos(c.get(0).getAsInt() - ax, c.get(1).getAsInt() - ay, c.get(2).getAsInt() - az);
            GalaxyMC.LOG.info("Loaded ship blueprint {}: {} blocks, {} interior cells", id, blocks.size(), interior.size());
            return new ShipBlueprint(id, List.copyOf(blocks), List.copyOf(interior), console, radius, height);
        } catch (Exception e) {
            throw new IllegalStateException("could not load ship blueprint " + id, e);
        }
    }

    /** Parses "namespace:block[prop=value,...]". Unknown properties are ignored. */
    static BlockState parseState(String text) {
        String name = text;
        String props = "";
        int b = text.indexOf('[');
        if (b >= 0) {
            name = text.substring(0, b);
            props = text.substring(b + 1, text.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(name));
        BlockState state = block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] parts = kv.split("=");
                if (parts.length == 2) {
                    state = with(state, parts[0].trim(), parts[1].trim());
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, String name, String value) {
        @SuppressWarnings("unchecked")
        Property<T> prop = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (prop == null) {
            return state;
        }
        Optional<T> v = prop.getValue(value);
        return v.map(t -> state.setValue(prop, t)).orElse(state);
    }

    /** Rotation that turns a north-facing blueprint to face {@code facing}. */
    public static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static BlockPos place(BlockPos anchor, BlockPos local, Rotation rotation) {
        return anchor.offset(local.rotate(rotation));
    }

    /** Every world position of the ship volume (hull plus interior). */
    public List<BlockPos> volume(BlockPos anchor, Rotation rotation) {
        List<BlockPos> out = new ArrayList<>(blocks.size() + interior.size());
        for (Cell c : blocks) {
            out.add(place(anchor, c.pos(), rotation));
        }
        for (BlockPos p : interior) {
            out.add(place(anchor, p, rotation));
        }
        return out;
    }

    public BlockPos consoleWorld(BlockPos anchor, Rotation rotation) {
        return place(anchor, console, rotation);
    }

    /** Recovers the anchor from the console's world position. */
    public BlockPos anchorFromConsole(BlockPos consolePos, Rotation rotation) {
        return consolePos.subtract(console.rotate(rotation));
    }
}
