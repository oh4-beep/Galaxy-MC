package com.galaxymc.registry;

import com.galaxymc.entity.AlienFlyer;
import com.galaxymc.entity.AlienMob;
import com.galaxymc.entity.Species;
import com.galaxymc.entity.SpeciesRegistry;
import com.galaxymc.entity.WormHead;
import com.galaxymc.entity.WormSegment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Registers an entity type, default attributes and a spawn egg for every species in the bestiary, plus
 * the shared worm segment type. Species are data (data/galaxy_mc/species.json), so adding a creature is
 * a matter of adding a line to tools/mobs/species.py and regenerating.
 */
public final class ModEntities {
    private ModEntities() {}

    public static final List<EntityType<? extends Mob>> CREATURES = new ArrayList<>();

    public static final EntityType<WormSegment> WORM_SEGMENT = registerSegment();

    private static EntityType<WormSegment> registerSegment() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reg.id("worm_segment"));
        EntityType<WormSegment> type = EntityType.Builder.of(WormSegment::new, MobCategory.MISC)
                .sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        FabricDefaultAttributeRegistry.register(type, Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0));
        return type;
    }

    public static void init() {
        for (Species s : SpeciesRegistry.all()) {
            switch (s.kind) {
                case FLYER, FLOATER -> register(s, AlienFlyer::new);
                case WORM -> register(s, WormHead::new);
                default -> register(s, AlienMob::new);
            }
        }
    }

    private static <T extends Mob> void register(Species s, EntityType.EntityFactory<T> factory) {
        MobCategory category = s.hostile() ? MobCategory.MONSTER : MobCategory.CREATURE;
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reg.id(s.id));
        EntityType.Builder<T> builder = EntityType.Builder.of(factory, category)
                .sized(s.width, s.height)
                .clientTrackingRange(s.giant ? 16 : 10)
                .updateInterval(s.kind == Species.Kind.WORM ? 1 : 3);
        if (s.fireImmune) {
            builder = builder.fireImmune();
        }
        EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
        SpeciesRegistry.bind(s, type);
        CREATURES.add(type);
        FabricDefaultAttributeRegistry.register(type, attributes(s));
        Reg.item(s.id + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
    }

    private static AttributeSupplier.Builder attributes(Species s) {
        AttributeSupplier.Builder b = Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, s.health)
                .add(Attributes.MOVEMENT_SPEED, s.movementSpeed())
                .add(Attributes.ATTACK_DAMAGE, Math.max(0.0, s.damage))
                .add(Attributes.ARMOR, s.armor)
                .add(Attributes.KNOCKBACK_RESISTANCE, s.knockbackResistance)
                .add(Attributes.FOLLOW_RANGE, s.followRange)
                .add(Attributes.STEP_HEIGHT, s.stepHeight)
                .add(Attributes.SCALE, s.scale);
        if (s.flies()) {
            b = b.add(Attributes.FLYING_SPEED, s.flySpeed * (s.giant ? Math.pow(s.scale, 0.3) : 1.0));
        }
        if (s.giant) {
            b = b.add(Attributes.ATTACK_KNOCKBACK, 1.5);
        }
        return b;
    }
}
