package com.galaxymc.entity;

import com.galaxymc.GalaxyMC;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;

/** Loads the bestiary from the mod jar and maps species to their registered entity types. */
public final class SpeciesRegistry {
    private SpeciesRegistry() {}

    private static final List<Species> ALL = new ArrayList<>();
    private static final Map<String, Species> BY_ID = new HashMap<>();
    private static final Map<EntityType<?>, Species> BY_TYPE = new IdentityHashMap<>();
    private static final Map<Species, EntityType<?>> TYPES = new IdentityHashMap<>();

    public static synchronized List<Species> all() {
        if (ALL.isEmpty()) {
            load();
        }
        return ALL;
    }

    private static void load() {
        String path = "/data/galaxy_mc/species.json";
        try (InputStream in = SpeciesRegistry.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int i = 0;
            for (JsonElement e : root.getAsJsonArray("species")) {
                Species s = new Species(i++, e.getAsJsonObject());
                ALL.add(s);
                BY_ID.put(s.id, s);
            }
            GalaxyMC.LOG.info("Loaded {} alien species", ALL.size());
        } catch (Exception e) {
            throw new IllegalStateException("could not load the bestiary", e);
        }
    }

    public static Species byId(String id) {
        all();
        return BY_ID.get(id);
    }

    public static Species byIndex(int index) {
        List<Species> list = all();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public static void bind(Species s, EntityType<?> type) {
        BY_TYPE.put(type, s);
        TYPES.put(s, type);
    }

    public static Species of(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    public static EntityType<?> typeOf(Species s) {
        return TYPES.get(s);
    }
}
