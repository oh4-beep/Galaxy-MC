package com.galaxymc.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Creature voices borrowed from vanilla, looked up by sound id rather than by {@code SoundEvents}
 * field so the mapping survives field renames between game versions. A missing sound is simply
 * silence.
 */
public final class Voices {
    private Voices() {}

    public record Voice(String ambient, String hurt, String death) {}

    private static final Map<String, Voice> VOICES = new HashMap<>();
    private static final Map<String, SoundEvent> CACHE = new HashMap<>();

    static {
        voice("zoglin", "entity.zoglin.ambient", "entity.zoglin.hurt", "entity.zoglin.death");
        voice("hoglin", "entity.hoglin.ambient", "entity.hoglin.hurt", "entity.hoglin.death");
        voice("camel", "entity.camel.ambient", "entity.camel.hurt", "entity.camel.death");
        voice("goat", "entity.goat.ambient", "entity.goat.hurt", "entity.goat.death");
        voice("ravager", "entity.ravager.ambient", "entity.ravager.hurt", "entity.ravager.death");
        voice("ender_dragon", "entity.ender_dragon.growl", "entity.ender_dragon.hurt", "entity.ender_dragon.death");
        voice("armadillo", "entity.armadillo.ambient", "entity.armadillo.hurt", "entity.armadillo.death");
        voice("silverfish", "entity.silverfish.ambient", "entity.silverfish.hurt", "entity.silverfish.death");
        voice("endermite", "entity.endermite.ambient", "entity.endermite.hurt", "entity.endermite.death");
        voice("spider", "entity.spider.ambient", "entity.spider.hurt", "entity.spider.death");
        voice("vex", "entity.vex.ambient", "entity.vex.hurt", "entity.vex.death");
        voice("warden", "entity.warden.ambient", "entity.warden.hurt", "entity.warden.death");
        voice("sniffer", "entity.sniffer.idle", "entity.sniffer.hurt", "entity.sniffer.death");
        voice("polar_bear", "entity.polar_bear.ambient", "entity.polar_bear.hurt", "entity.polar_bear.death");
        voice("turtle", "entity.turtle.ambient_land", "entity.turtle.hurt", "entity.turtle.death");
        voice("slime", "entity.slime.squish", "entity.slime.hurt", "entity.slime.death");
        voice("bat", "entity.bat.ambient", "entity.bat.hurt", "entity.bat.death");
        voice("allay", "entity.allay.ambient_without_item", "entity.allay.hurt", "entity.allay.death");
        voice("phantom", "entity.phantom.ambient", "entity.phantom.hurt", "entity.phantom.death");
        voice("squid", "entity.squid.ambient", "entity.squid.hurt", "entity.squid.death");
        voice("ghast", "entity.ghast.ambient", "entity.ghast.hurt", "entity.ghast.death");
        voice("guardian", "entity.guardian.ambient", "entity.guardian.hurt", "entity.guardian.death");
        voice("elder_guardian", "entity.elder_guardian.ambient", "entity.elder_guardian.hurt", "entity.elder_guardian.death");
        voice("creaking", "entity.creaking.ambient", "entity.creaking.sway", "entity.creaking.death");
        voice("frog", "entity.frog.ambient", "entity.frog.hurt", "entity.frog.death");
        voice("shulker", "entity.shulker.ambient", "entity.shulker.hurt", "entity.shulker.death");
        voice("llama", "entity.llama.ambient", "entity.llama.hurt", "entity.llama.death");
        voice("wither", "entity.wither.ambient", "entity.wither.hurt", "entity.wither.death");
    }

    private static void voice(String id, String ambient, String hurt, String death) {
        VOICES.put(id, new Voice(ambient, hurt, death));
    }

    public static Voice of(String id) {
        return VOICES.getOrDefault(id, VOICES.get("silverfish"));
    }

    public static synchronized SoundEvent sound(String id) {
        if (id == null) {
            return null;
        }
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
        CACHE.put(id, e);
        return e;
    }
}
