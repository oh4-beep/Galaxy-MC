package com.galaxymc.world;

import com.galaxymc.entity.FaunaSpawner;
import com.galaxymc.entity.Species;
import com.galaxymc.entity.SpeciesRegistry;
import com.galaxymc.galaxy.Hazard;
import com.galaxymc.galaxy.PlanetProfile;
import com.galaxymc.galaxy.PlanetType;
import com.galaxymc.item.Relics;
import com.galaxymc.mineral.Minerals;
import com.galaxymc.registry.ModBlocks;
import com.galaxymc.registry.ModItems;
import com.galaxymc.util.Hash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Hand-built structures scattered across planet surfaces, each holding loot that scales with the
 * world's tier. Roughly one chunk in forty holds something:
 * <ul>
 *   <li>ruins, temples and watchtowers of whoever was here before (relics, minerals, treasure);</li>
 *   <li>crashed survey probes, wrecked starships and abandoned research outposts (fuel and metals);</li>
 *   <li>crystal obelisks and buried crystal geodes;</li>
 *   <li>vaults beneath the surface, guarded by spawners of the planet's own predators;</li>
 *   <li>abandoned colonies with farms and houses on Earth-like worlds;</li>
 *   <li>fresh meteorite craters on worlds under meteor showers, sunken ruins on sea floors and
 *   obsidian shrines on volcanic worlds.</li>
 * </ul>
 */
public final class Structures {
    private Structures() {}

    private static final int FLAGS = 2;
    /** Chance that a chunk holds a structure at all. */
    private static final double CHANCE = 1.0 / 40.0;

    private enum Kind { RUINS, PROBE, OBELISK, OUTPOST, TEMPLE, WRECK, WATCHTOWER, VAULT, GEODE, COLONY, METEORITE, SUNKEN, SHRINE }

    private enum Loot { RELIC, SALVAGE, SUPPLIES, TREASURE }

    private record Option(Kind kind, int weight) {}

    public static void maybePlace(WorldGenLevel level, Hash.Rng rng, ChunkAccess chunk, PlanetProfile p, TerrainShaper shaper,
                                  PlanetChunkGenerator gen) {
        if (p.type == PlanetType.GAS_GIANT || p.type == PlanetType.SHATTERED || p.type == PlanetType.STELLAR) {
            return;
        }
        if (rng.nextDouble() >= CHANCE) {
            return;
        }
        int x = chunk.getPos().getMinBlockX() + 8;
        int z = chunk.getPos().getMinBlockZ() + 8;
        Kind kind = pick(rng, options(p));
        switch (kind) {
            case RUINS -> ruins(level, rng, x, z, p);
            case PROBE -> probe(level, rng, x, z, p);
            case OBELISK -> obelisk(level, rng, x, z, p);
            case OUTPOST -> outpost(level, rng, x, z, p);
            case TEMPLE -> temple(level, rng, x, z, p);
            case WRECK -> wreck(level, rng, x, z, p);
            case WATCHTOWER -> watchtower(level, rng, x, z, p);
            case VAULT -> vault(level, rng, x, z, p);
            case GEODE -> geode(level, rng, x, z, p);
            case COLONY -> colony(level, rng, x, z, p);
            case METEORITE -> meteorite(level, rng, x, z, p);
            case SUNKEN -> sunken(level, rng, x, z, p);
            case SHRINE -> shrine(level, rng, x, z, p);
        }
    }

    private static List<Option> options(PlanetProfile p) {
        List<Option> o = new ArrayList<>();
        boolean earthlike = p.type == PlanetType.TERRAN || p.type == PlanetType.GRASSLAND || p.type == PlanetType.STORM
                || p.type == PlanetType.JUNGLE || p.type == PlanetType.TUNDRA;
        boolean volcanic = p.type == PlanetType.VOLCANIC || p.type == PlanetType.LAVA || p.type == PlanetType.ASH;
        boolean sea = p.palette.fluid() != null && p.palette.fluid().getBlock() == Blocks.WATER && p.seaLevel > PlanetColumns.MIN_Y + 30;
        o.add(new Option(Kind.RUINS, 10));
        o.add(new Option(Kind.PROBE, 8));
        o.add(new Option(Kind.OBELISK, 5));
        o.add(new Option(Kind.OUTPOST, 7));
        o.add(new Option(Kind.TEMPLE, 5));
        o.add(new Option(Kind.WRECK, 5));
        o.add(new Option(Kind.WATCHTOWER, 5));
        o.add(new Option(Kind.VAULT, 7));
        o.add(new Option(Kind.GEODE, p.type == PlanetType.CRYSTAL ? 14 : p.type == PlanetType.TERRAN ? 2 : 5));
        if (earthlike) {
            o.add(new Option(Kind.COLONY, p.type == PlanetType.TERRAN ? 14 : 7));
        }
        o.add(new Option(Kind.METEORITE, p.has(Hazard.METEORS) ? 14 : 2));
        if (sea) {
            o.add(new Option(Kind.SUNKEN, p.type == PlanetType.OCEAN ? 16 : 6));
        }
        if (volcanic || p.volcanism > 0) {
            o.add(new Option(Kind.SHRINE, 7));
        }
        return o;
    }

    private static Kind pick(Hash.Rng rng, List<Option> options) {
        int total = 0;
        for (Option o : options) {
            total += o.weight();
        }
        int r = rng.nextInt(total);
        for (Option o : options) {
            r -= o.weight();
            if (r < 0) {
                return o.kind();
            }
        }
        return Kind.RUINS;
    }

    // ------------------------------------------------------------------ helpers

    /** The top of solid, dry ground at a column: skips trees, plants and snow, stops at fluids. */
    private static int ground(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SurfaceDecorator.groundY(level, x, z) - 1, z);
        while (pos.getY() > level.getMinY() + 1) {
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.getFluidState().isEmpty() && st.canBeReplaced()) {
                pos.move(Direction.DOWN);
            } else {
                break;
            }
        }
        return pos.getY() + 1;
    }

    private static boolean dry(WorldGenLevel level, int x, int y, int z) {
        BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
        return below.getFluidState().isEmpty() && !below.isAir();
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    private static void box(WorldGenLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(level, x, y, z, state);
                }
            }
        }
    }

    /** Walls, floor and roof of a box, with its inside cleared. */
    private static void room(WorldGenLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall, BlockState floor,
                             BlockState roof) {
        box(level, x1, y1, z1, x2, y2, z2, wall);
        box(level, x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1, Blocks.AIR.defaultBlockState());
        box(level, x1, y1, z1, x2, y1, z2, floor);
        box(level, x1, y2, z1, x2, y2, z2, roof);
    }

    /** Stone under a footprint down to the ground, so buildings never float over dips. */
    private static void foundation(WorldGenLevel level, int x1, int z1, int x2, int z2, int y, BlockState state) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int d = 1; d <= 8; d++) {
                    BlockPos pos = new BlockPos(x, y - d, z);
                    BlockState st = level.getBlockState(pos);
                    if (!st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced()) {
                        break;
                    }
                    set(level, pos, state);
                }
            }
        }
    }

    private static void door(WorldGenLevel level, int x, int y, int z, Direction facing, BlockState door) {
        set(level, x, y, z, door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, x, y + 1, z, door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void chest(WorldGenLevel level, BlockPos pos, Hash.Rng rng, PlanetProfile p, Loot loot) {
        chest(level, pos, rng, p, loot, null);
    }

    /**
     * A chest of Galaxy MC loot. With a vanilla loot table as well, the table fills the remaining slots the
     * first time the chest is opened.
     */
    private static void chest(WorldGenLevel level, BlockPos pos, Hash.Rng rng, PlanetProfile p, Loot loot, ResourceKey<LootTable> table) {
        chest(level, pos, rng, p, loot, table, Blocks.CHEST.defaultBlockState());
    }

    private static void chest(WorldGenLevel level, BlockPos pos, Hash.Rng rng, PlanetProfile p, Loot loot, ResourceKey<LootTable> table,
                              BlockState state) {
        set(level, pos, state);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        int tier = p.tier;
        int minerals = rng.nextInt(1, 3);
        for (int i = 0; i < minerals; i++) {
            chest.setItem(rng.nextInt(27), Minerals.stack(Minerals.forPlanet(p, rng.nextInt(4)), rng.nextInt(2, 6)));
        }
        switch (loot) {
            case RELIC -> {
                chest.setItem(rng.nextInt(27), Relics.random(rng.nextLong(), tier));
                if (rng.chance(0.4)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(Items.GOLD_INGOT, rng.nextInt(2, 7)));
                }
                if (rng.chance(0.25)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(Items.EMERALD, rng.nextInt(1, 5)));
                }
            }
            case SALVAGE -> {
                chest.setItem(rng.nextInt(27), new ItemStack(ModItems.ROCKET_FUEL_CANISTER, rng.nextInt(1, 4)));
                chest.setItem(rng.nextInt(27), new ItemStack(rng.pick(new net.minecraft.world.item.Item[]{ModItems.TITANIUM_INGOT,
                        ModItems.COBALT_INGOT, ModItems.IRIDIUM_INGOT}), rng.nextInt(2, 7)));
                if (rng.chance(0.5)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(Items.REDSTONE, rng.nextInt(4, 12)));
                }
                if (rng.chance(0.08)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(ModBlocks.FUEL_BLOCK.asItem()));
                }
                if (rng.chance(0.15)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(ModItems.THERMOMETER));
                }
            }
            case SUPPLIES -> {
                chest.setItem(rng.nextInt(27), new ItemStack(ModItems.COOKED_XENO_MEAT, rng.nextInt(3, 9)));
                chest.setItem(rng.nextInt(27), new ItemStack(ModItems.THERMAL_FIBER, rng.nextInt(2, 8)));
                chest.setItem(rng.nextInt(27), new ItemStack(Items.TORCH, rng.nextInt(6, 17)));
                if (rng.chance(0.6)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(ModItems.ROCKET_FUEL_CANISTER, rng.nextInt(1, 3)));
                }
                if (rng.chance(0.12)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(rng.pick(new net.minecraft.world.item.Item[]{ModItems.SPACE_HELMET,
                            ModItems.SPACE_CHESTPLATE, ModItems.SPACE_LEGGINGS, ModItems.SPACE_BOOTS})));
                }
            }
            case TREASURE -> {
                chest.setItem(rng.nextInt(27), Relics.random(rng.nextLong(), tier + 1));
                if (rng.chance(0.35)) {
                    chest.setItem(rng.nextInt(27), Relics.random(rng.nextLong(), tier));
                }
                chest.setItem(rng.nextInt(27), new ItemStack(Items.DIAMOND, rng.nextInt(1, 3 + Math.min(4, tier / 2))));
                chest.setItem(rng.nextInt(27), new ItemStack(Items.EMERALD, rng.nextInt(2, 8)));
                if (rng.chance(0.3)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(ModItems.PLUTONITE, rng.nextInt(1, 4)));
                }
                if (rng.chance(0.04)) {
                    chest.setItem(rng.nextInt(27), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
                }
            }
        }
        if (rng.chance(0.4)) {
            chest.setItem(rng.nextInt(27), new ItemStack(ModItems.XENO_MEAT, rng.nextInt(2, 6)));
        }
        if (table != null) {
            chest.setLootTable(table, rng.nextLong());
        }
    }

    // ------------------------------------------------------------------ original three

    /** A ring of broken pillars around an altar, half-buried in the regolith. */
    private static void ruins(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState brick = ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        int radius = rng.nextInt(5, 9);
        int pillars = rng.nextInt(6, 12);
        for (int i = 0; i < pillars; i++) {
            double a = i * Math.PI * 2.0 / pillars;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int pz = cz + (int) Math.round(Math.sin(a) * radius);
            int py = ground(level, px, pz) - 1;
            int height = rng.chance(0.3) ? rng.nextInt(1, 3) : rng.nextInt(3, 9);
            for (int h = 0; h < height; h++) {
                set(level, new BlockPos(px, py + h, pz), brick);
            }
            if (height >= 5 && rng.chance(0.5)) {
                set(level, new BlockPos(px, py + height, pz), glow);
            }
        }
        // Flagstone floor and altar.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (rng.chance(0.8)) {
                    set(level, new BlockPos(cx + dx, cy - 1, cz + dz), brick);
                }
            }
        }
        set(level, new BlockPos(cx, cy, cz), brick);
        chest(level, new BlockPos(cx, cy + 1, cz), rng, p, Loot.RELIC);
    }

    /** A survey probe that came down hard: a scorched crater with hull debris and a salvage crate. */
    private static void probe(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        int r = rng.nextInt(3, 5);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) {
                    continue;
                }
                int depth = (int) Math.round((1.0 - d / r) * 2.5);
                for (int k = 0; k < depth; k++) {
                    set(level, new BlockPos(cx + dx, cy - 1 - k, cz + dz), Blocks.AIR.defaultBlockState());
                }
                if (rng.chance(0.35)) {
                    set(level, new BlockPos(cx + dx, cy - 1 - depth, cz + dz), Blocks.BLACKSTONE.defaultBlockState());
                }
            }
        }
        BlockState[] debris = {ModBlocks.HULL_PLATING.defaultBlockState(), ModBlocks.HULL_PLATING_DARK.defaultBlockState(),
                ModBlocks.HULL_STRIPE.defaultBlockState()};
        int pieces = rng.nextInt(8, 16);
        for (int i = 0; i < pieces; i++) {
            int dx = rng.nextInt(-r - 3, r + 3);
            int dz = rng.nextInt(-r - 3, r + 3);
            int y = ground(level, cx + dx, cz + dz);
            set(level, new BlockPos(cx + dx, y, cz + dz), rng.pick(debris));
        }
        int floor = cy - 3;
        set(level, new BlockPos(cx, floor, cz), ModBlocks.HULL_PLATING_DARK.defaultBlockState());
        set(level, new BlockPos(cx + 1, floor + 1, cz), ModBlocks.SHIP_LIGHT.defaultBlockState());
        set(level, new BlockPos(cx - 1, floor + 1, cz), ModBlocks.THRUSTER.defaultBlockState());
        chest(level, new BlockPos(cx, floor + 1, cz), rng, p, Loot.SALVAGE);
    }

    /** A tall crystal obelisk: a landmark, with a relic sealed in its base on richer worlds. */
    private static void obelisk(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        int height = rng.nextInt(16, 40);
        for (int h = -2; h < height; h++) {
            int w = h < height / 3 ? 1 : 0;
            for (int dx = -w; dx <= w + 1; dx++) {
                for (int dz = -w; dz <= w + 1; dz++) {
                    boolean edge = dx == -w || dz == -w || dx == w + 1 || dz == w + 1;
                    set(level, new BlockPos(cx + dx, cy + h, cz + dz), h % 5 == 0 && edge ? glow : crystal);
                }
            }
        }
        set(level, new BlockPos(cx, cy + height, cz), glow);
        set(level, new BlockPos(cx + 1, cy + height, cz + 1), glow);
        if (p.tier >= 2 && rng.chance(0.6)) {
            chest(level, new BlockPos(cx, cy - 1, cz), rng, p, Loot.RELIC);
        }
    }

    // ------------------------------------------------------------------ outposts and wrecks

    /** An abandoned research outpost: a sealed hull-plated hab with an airlock, lab bench and stores. */
    private static void outpost(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState hull = ModBlocks.HULL_PLATING.defaultBlockState();
        BlockState dark = ModBlocks.HULL_PLATING_DARK.defaultBlockState();
        BlockState floor = ModBlocks.SHIP_FLOOR.defaultBlockState();
        BlockState glass = ModBlocks.REINFORCED_GLASS.defaultBlockState();
        int w = rng.nextInt(4, 6);
        int d = rng.nextInt(3, 5);
        int h = 5;
        int x1 = cx - w;
        int x2 = cx + w;
        int z1 = cz - d;
        int z2 = cz + d;
        foundation(level, x1, z1, x2, z2, cy, dark);
        room(level, x1, cy, z1, x2, cy + h, z2, hull, floor, dark);
        // Stripe band and windows.
        for (int x = x1; x <= x2; x++) {
            set(level, x, cy + 1, z1, ModBlocks.HULL_STRIPE.defaultBlockState());
            set(level, x, cy + 1, z2, ModBlocks.HULL_STRIPE.defaultBlockState());
            if ((x - x1) % 3 == 1) {
                set(level, x, cy + 3, z1, glass);
                set(level, x, cy + 3, z2, glass);
            }
        }
        door(level, x1, cy + 1, cz, Direction.WEST, ModBlocks.AIRLOCK_DOOR.defaultBlockState());
        set(level, cx, cy + h - 1, cz, ModBlocks.SHIP_LIGHT.defaultBlockState());
        set(level, x2 - 1, cy + 1, z1 + 1, ModBlocks.LIFE_SUPPORT.defaultBlockState());
        set(level, x2 - 1, cy + 1, z2 - 1, ModBlocks.THERMAL_ARMOR_TABLE.defaultBlockState());
        set(level, x1 + 2, cy + 1, z1 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, x1 + 3, cy + 1, z1 + 1, Blocks.FURNACE.defaultBlockState());
        chest(level, new BlockPos(x1 + 1, cy + 1, z2 - 1), rng, p, Loot.SUPPLIES);
        chest(level, new BlockPos(x2 - 2, cy + 1, z2 - 1), rng, p, Loot.SALVAGE);
        // A comms mast on the roof.
        int mast = rng.nextInt(4, 9);
        for (int i = 1; i <= mast; i++) {
            set(level, x2 - 1, cy + h + i, z1 + 1, Blocks.IRON_BARS.defaultBlockState());
        }
        set(level, x2 - 1, cy + h + mast + 1, z1 + 1, ModBlocks.SHIP_LIGHT.defaultBlockState());
    }

    /** A starship that never took off again: a long hull buried nose-first, torn open along its side. */
    private static void wreck(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        boolean alongX = rng.chance(0.5);
        int half = rng.nextInt(9, 15);
        double tilt = rng.range(0.15, 0.35);
        BlockState[] plates = {ModBlocks.HULL_PLATING.defaultBlockState(), ModBlocks.HULL_PLATING_DARK.defaultBlockState(),
                ModBlocks.HULL_STRIPE.defaultBlockState()};
        for (int i = -half; i <= half; i++) {
            double t = (i + half) / (double) (2 * half);
            double radius = 2.5 + Math.sin(t * Math.PI) * 1.8;
            int yc = cy + (int) Math.round(i * tilt) + 1;
            int ri = (int) Math.ceil(radius);
            for (int a = -ri; a <= ri; a++) {
                for (int b = -ri; b <= ri; b++) {
                    double r2 = a * a + b * b * 1.3;
                    if (r2 > radius * radius) {
                        continue;
                    }
                    int x = alongX ? cx + i : cx + a;
                    int z = alongX ? cz + a : cz + i;
                    int y = yc + b;
                    boolean shell = r2 > (radius - 1.1) * (radius - 1.1);
                    boolean torn = a > 0 && Math.abs(i) < half / 2 && rng.chance(0.55);
                    if (shell && !torn) {
                        set(level, x, y, z, rng.pick(plates));
                    } else {
                        set(level, x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            if (i == half) {
                int x = alongX ? cx + i + 1 : cx;
                int z = alongX ? cz : cz + i + 1;
                set(level, x, yc, z, ModBlocks.THRUSTER.defaultBlockState());
            }
        }
        // Cargo spilled through the hull.
        int crates = rng.nextInt(2, 4);
        for (int k = 0; k < crates; k++) {
            int i = rng.nextInt(-half / 2, half / 2 + 1);
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            int y = cy + (int) Math.round(i * tilt) - 1;
            set(level, x, y - 1, z, ModBlocks.SHIP_FLOOR.defaultBlockState());
            chest(level, new BlockPos(x, y, z), rng, p, k == 0 ? Loot.SALVAGE : Loot.SUPPLIES,
                    k == 0 ? BuiltInLootTables.SHIPWRECK_SUPPLY : null);
        }
        for (int k = 0; k < 10; k++) {
            int dx = rng.nextInt(-half, half + 1);
            int dz = rng.nextInt(-6, 7);
            int x = alongX ? cx + dx : cx + dz;
            int z = alongX ? cz + dz : cz + dx;
            set(level, x, ground(level, x, z), z, rng.pick(plates));
        }
    }

    // ------------------------------------------------------------------ the old ones' buildings

    /** A stepped temple of alien stone with a glowing crown and a treasure chamber at its heart. */
    private static void temple(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState brick = ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        int base = rng.nextInt(7, 11);
        foundation(level, cx - base, cz - base, cx + base, cz + base, cy, brick);
        int steps = base - 1;
        for (int s = 0; s < steps; s++) {
            int r = base - s;
            int y = cy + s * 2;
            box(level, cx - r, y, cz - r, cx + r, y + 1, cz + r, brick);
            if (s % 2 == 0) {
                set(level, cx - r, y + 2, cz - r, glow);
                set(level, cx + r, y + 2, cz - r, glow);
                set(level, cx - r, y + 2, cz + r, glow);
                set(level, cx + r, y + 2, cz + r, glow);
            }
        }
        int top = cy + steps * 2;
        set(level, cx, top, cz, crystal);
        set(level, cx, top + 1, cz, crystal);
        set(level, cx, top + 2, cz, glow);
        // Hollow chamber in the middle, reached by a corridor from the south face.
        box(level, cx - 2, cy + 1, cz - 2, cx + 2, cy + 4, cz + 2, Blocks.AIR.defaultBlockState());
        box(level, cx, cy + 1, cz + 2, cx, cy + 2, cz + base, Blocks.AIR.defaultBlockState());
        set(level, cx - 2, cy + 4, cz - 2, glow);
        set(level, cx + 2, cy + 4, cz + 2, glow);
        chest(level, new BlockPos(cx, cy + 1, cz - 1), rng, p, Loot.TREASURE,
                p.type == PlanetType.JUNGLE || p.type == PlanetType.TERRAN ? BuiltInLootTables.JUNGLE_TEMPLE : BuiltInLootTables.DESERT_PYRAMID);
        chest(level, new BlockPos(cx - 1, cy + 1, cz - 1), rng, p, Loot.RELIC);
    }

    /** A lonely watchtower with a ladder up its hollow core and a lookout chest at the top. */
    private static void watchtower(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState brick = ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        int height = rng.nextInt(16, 28);
        foundation(level, cx - 2, cz - 2, cx + 2, cz + 2, cy, brick);
        room(level, cx - 2, cy, cz - 2, cx + 2, cy + height, cz + 2, brick, brick, brick);
        for (int y = cy + 1; y < cy + height; y++) {
            set(level, cx, y, cz - 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            if (y % 4 == 0) {
                set(level, cx + 2, y, cz, Blocks.AIR.defaultBlockState());
                set(level, cx - 2, y, cz, Blocks.AIR.defaultBlockState());
            }
        }
        box(level, cx, cy + 1, cz + 2, cx, cy + 2, cz + 2, Blocks.AIR.defaultBlockState());
        set(level, cx, cy + height, cz - 1, Blocks.AIR.defaultBlockState());
        // Lookout platform with battlements.
        int top = cy + height;
        box(level, cx - 3, top, cz - 3, cx + 3, top, cz + 3, brick);
        set(level, cx, top, cz - 1, Blocks.AIR.defaultBlockState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if ((Math.abs(dx) == 3 || Math.abs(dz) == 3) && (dx + dz) % 2 == 0) {
                    set(level, cx + dx, top + 1, cz + dz, brick);
                }
            }
        }
        set(level, cx + 2, top + 1, cz + 2, ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState());
        chest(level, new BlockPos(cx + 1, top + 1, cz + 1), rng, p, Loot.RELIC);
    }

    /**
     * A vault sunk into the rock: a sealed chamber holding a spawner of the planet's own predators,
     * guarding two treasure chests. A shaft with a glowing marker leads down to it.
     */
    private static void vault(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int surface = ground(level, cx, cz);
        if (!dry(level, cx, surface, cz)) {
            return;
        }
        int cy = surface - rng.nextInt(14, 26);
        if (cy < PlanetColumns.MIN_Y + 10) {
            return;
        }
        BlockState brick = ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        room(level, cx - 4, cy, cz - 4, cx + 4, cy + 5, cz + 4, brick, brick, brick);
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -3; dz <= 3; dz += 6) {
                set(level, cx + dx, cy + 4, cz + dz, ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState());
            }
        }
        Species guard = guardian(p, rng);
        if (guard != null) {
            set(level, cx, cy + 1, cz, Blocks.SPAWNER.defaultBlockState());
            if (level.getBlockEntity(new BlockPos(cx, cy + 1, cz)) instanceof SpawnerBlockEntity spawner) {
                spawner.setEntityId(SpeciesRegistry.typeOf(guard), RandomSource.create(rng.nextLong()));
            }
        }
        chest(level, new BlockPos(cx - 3, cy + 1, cz), rng, p, Loot.TREASURE, BuiltInLootTables.SIMPLE_DUNGEON);
        chest(level, new BlockPos(cx + 3, cy + 1, cz), rng, p, Loot.RELIC);
        // Access shaft from the surface, capped with a crystal marker.
        for (int y = cy + 1; y <= surface + 1; y++) {
            set(level, cx + 3, y, cz + 3, Blocks.AIR.defaultBlockState());
            set(level, cx + 3, y, cz + 2, y <= surface ? brick : Blocks.AIR.defaultBlockState());
            if (y <= surface) {
                set(level, cx + 3, y, cz + 3, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            }
        }
        set(level, cx + 4, surface, cz + 4, ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState());
    }

    /** A hostile, land-bound, normal-sized species from the planet's fauna to guard a vault. */
    private static Species guardian(PlanetProfile p, Hash.Rng rng) {
        List<Species> options = new ArrayList<>();
        for (FaunaSpawner.Entry e : FaunaSpawner.fauna(p)) {
            Species s = e.species();
            if (s.hostile() && !s.giant && s.kind == Species.Kind.GROUND && SpeciesRegistry.typeOf(s) != null) {
                options.add(s);
            }
        }
        return options.isEmpty() ? null : options.get(rng.nextInt(options.size()));
    }

    /** A hollow sphere of crystal buried in the rock, lit from within and lined with shards. */
    private static void geode(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int surface = ground(level, cx, cz);
        int r = rng.nextInt(5, 9);
        int cy = surface - r - rng.nextInt(2, 12);
        if (cy - r < PlanetColumns.MIN_Y + 6) {
            return;
        }
        BlockState crystal = ModBlocks.CRYSTAL_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOWING_CRYSTAL_BLOCK.defaultBlockState();
        BlockState calcite = Blocks.CALCITE.defaultBlockState();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dy = -r - 1; dy <= r + 1; dy++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r + 1) {
                        continue;
                    }
                    BlockState st = d > r ? calcite : d > r - 1 ? (rng.chance(0.12) ? glow : crystal) : Blocks.AIR.defaultBlockState();
                    set(level, cx + dx, cy + dy, cz + dz, st);
                }
            }
        }
        BlockState shard = ModBlocks.CRYSTAL_SHARD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        for (int i = 0; i < r * 3; i++) {
            int dx = rng.nextInt(-r + 1, r);
            int dz = rng.nextInt(-r + 1, r);
            for (int y = cy - r + 1; y < cy; y++) {
                if (level.getBlockState(new BlockPos(cx + dx, y, cz + dz)).isAir()) {
                    set(level, cx + dx, y, cz + dz, shard);
                    break;
                }
            }
        }
        chest(level, new BlockPos(cx, cy - r + 2, cz), rng, p, Loot.RELIC);
        // A crack in the ground above hints at what lies below.
        for (int y = cy + r - 1; y <= surface; y++) {
            set(level, cx + r / 2, y, cz, Blocks.AIR.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ settlements

    /** An abandoned colony: a few timber houses around a well, with fenced crop fields. */
    private static void colony(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        // Well.
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        foundation(level, cx - 1, cz - 1, cx + 1, cz + 1, cy, cobble);
        box(level, cx - 1, cy - 1, cz - 1, cx + 1, cy, cz + 1, cobble);
        set(level, cx, cy - 1, cz, Blocks.WATER.defaultBlockState());
        set(level, cx, cy, cz, Blocks.WATER.defaultBlockState());
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                set(level, cx + dx, cy + 1, cz + dz, Blocks.OAK_FENCE.defaultBlockState());
                set(level, cx + dx, cy + 2, cz + dz, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        box(level, cx - 1, cy + 3, cz - 1, cx + 1, cy + 3, cz + 1, Blocks.OAK_SLAB.defaultBlockState());
        int houses = rng.nextInt(2, 5);
        for (int i = 0; i < houses; i++) {
            double a = (i + rng.nextDouble() * 0.5) * Math.PI * 2.0 / houses;
            int hx = cx + (int) Math.round(Math.cos(a) * rng.nextInt(9, 13));
            int hz = cz + (int) Math.round(Math.sin(a) * rng.nextInt(9, 13));
            house(level, rng, hx, hz, p, i == 0);
        }
        // Crop field with an irrigation channel.
        int fx = cx + (rng.chance(0.5) ? -8 : 4);
        int fz = cz + (rng.chance(0.5) ? -8 : 4);
        int fy = ground(level, fx + 2, fz + 2) - 1;
        BlockState[] crops = {Blocks.WHEAT.defaultBlockState(), Blocks.CARROTS.defaultBlockState(), Blocks.POTATOES.defaultBlockState(),
                Blocks.BEETROOTS.defaultBlockState()};
        BlockState crop = rng.pick(crops);
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                if (dx == 2) {
                    set(level, fx + dx, fy, fz + dz, Blocks.WATER.defaultBlockState());
                    set(level, fx + dx, fy + 1, fz + dz, Blocks.AIR.defaultBlockState());
                    continue;
                }
                set(level, fx + dx, fy, fz + dz, Blocks.FARMLAND.defaultBlockState());
                BlockState grown = crop.getBlock() == Blocks.BEETROOTS ? crop.setValue(BlockStateProperties.AGE_3, rng.nextInt(4))
                        : crop.setValue(BlockStateProperties.AGE_7, rng.nextInt(8));
                set(level, fx + dx, fy + 1, fz + dz, grown);
            }
        }
    }

    private static void house(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p, boolean big) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        int w = big ? 3 : 2;
        int d = big ? 3 : 2;
        BlockState planks = rng.pick(new BlockState[]{Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.BIRCH_PLANKS.defaultBlockState()});
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        foundation(level, cx - w, cz - d, cx + w, cz + d, cy, Blocks.COBBLESTONE.defaultBlockState());
        room(level, cx - w, cy, cz - d, cx + w, cy + 4, cz + d, planks, Blocks.COBBLESTONE.defaultBlockState(), planks);
        for (int y = cy + 1; y <= cy + 3; y++) {
            set(level, cx - w, y, cz - d, log);
            set(level, cx + w, y, cz - d, log);
            set(level, cx - w, y, cz + d, log);
            set(level, cx + w, y, cz + d, log);
        }
        // Gable roof.
        for (int k = 0; k <= w + 1; k++) {
            box(level, cx - w - 1 + k, cy + 4 + k, cz - d - 1, cx + w + 1 - k, cy + 4 + k, cz + d + 1,
                    Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        set(level, cx - w, cy + 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        set(level, cx + w, cy + 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        door(level, cx, cy + 1, cz + d, Direction.SOUTH, Blocks.OAK_DOOR.defaultBlockState());
        set(level, cx, cy + 3, cz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        set(level, cx - w + 1, cy + 1, cz - d + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        if (big) {
            set(level, cx + w - 1, cy + 1, cz - d + 1, Blocks.FURNACE.defaultBlockState());
        }
        chest(level, new BlockPos(cx + w - 1, cy + 1, cz + d - 1), rng, p, Loot.SUPPLIES,
                big ? BuiltInLootTables.VILLAGE_TOOLSMITH : BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
    }

    // ------------------------------------------------------------------ hazards made solid

    /** A fresh meteorite crater: a glassy bowl around a hot, ore-rich core. */
    private static void meteorite(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        int r = rng.nextInt(5, 9);
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 2) {
                    continue;
                }
                int y = ground(level, cx + dx, cz + dz);
                if (d <= r) {
                    int depth = (int) Math.round((1.0 - (d / r) * (d / r)) * r * 0.6);
                    for (int k = 1; k <= depth; k++) {
                        set(level, cx + dx, y - k, cz + dz, Blocks.AIR.defaultBlockState());
                    }
                    set(level, cx + dx, y - 1 - depth, cz + dz, rng.chance(0.3) ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState());
                } else if (rng.chance(0.5)) {
                    set(level, cx + dx, y, cz + dz, Blocks.BLACKSTONE.defaultBlockState());
                }
            }
        }
        int floor = ground(level, cx, cz);
        BlockState[] ores = {ModBlocks.EXOTIC_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(),
                ModBlocks.LUNAR_TITANIUM_ORE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
                p.tier >= 3 ? ModBlocks.PLUTONITE_ORE.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState()};
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy - 1) + Math.abs(dz) <= 2) {
                        set(level, cx + dx, floor + dy, cz + dz, rng.pick(ores));
                    }
                }
            }
        }
    }

    /** Sunken ruins on a sea floor: toppled columns around a flooded treasure chest. */
    private static void sunken(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx, cz);
        if (floor >= p.seaLevel - 4 || level.getBlockState(new BlockPos(cx, floor, cz)).getFluidState().isEmpty()) {
            return;
        }
        BlockState brick = rng.chance(0.5) ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : ModBlocks.ALIEN_STONE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
        int r = rng.nextInt(4, 7);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (rng.chance(0.7)) {
                    int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx + dx, cz + dz) - 1;
                    set(level, cx + dx, y, cz + dz, (dx + dz) % 2 == 0 ? brick : dark);
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int pz = cz + (int) Math.round(Math.sin(a) * r);
            int py = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, px, pz);
            int height = rng.nextInt(1, 6);
            for (int h = 0; h < height; h++) {
                set(level, px, py + h, pz, brick);
            }
            if (height > 3) {
                set(level, px, py + height, pz, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        chest(level, new BlockPos(cx, floor, cz), rng, p, Loot.TREASURE, BuiltInLootTables.UNDERWATER_RUIN_BIG,
                Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
    }

    /** An obsidian shrine near the fire: crying obsidian pillars around a basin of lava and a chest. */
    private static void shrine(WorldGenLevel level, Hash.Rng rng, int cx, int cz, PlanetProfile p) {
        int cy = ground(level, cx, cz);
        if (!dry(level, cx, cy, cz)) {
            return;
        }
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState black = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        foundation(level, cx - 4, cz - 4, cx + 4, cz + 4, cy, black);
        box(level, cx - 4, cy - 1, cz - 4, cx + 4, cy - 1, cz + 4, black);
        // Sealed lava basin.
        box(level, cx - 1, cy - 2, cz - 1, cx + 1, cy - 2, cz + 1, obsidian);
        box(level, cx - 1, cy - 1, cz - 1, cx + 1, cy - 1, cz + 1, Blocks.LAVA.defaultBlockState());
        for (int dx = -4; dx <= 4; dx += 8) {
            for (int dz = -4; dz <= 4; dz += 8) {
                int h = rng.nextInt(4, 8);
                for (int y = 0; y < h; y++) {
                    set(level, cx + dx, cy + y, cz + dz, y % 3 == 1 ? crying : obsidian);
                }
                set(level, cx + dx, cy + h, cz + dz, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
        }
        chest(level, new BlockPos(cx, cy, cz + 3), rng, p, Loot.TREASURE, BuiltInLootTables.RUINED_PORTAL);
    }
}
