package com.galaxymc.item;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The ability an alien relic unleashes when used. */
public enum RelicPower implements StringRepresentable {
    BLINK("blink", "Blinking", "Teleports you forward", 60),
    NOVA("nova", "the Nova", "Blasts nearby creatures away", 200),
    MENDING("mending", "Mending", "Heals you over a few seconds", 400),
    ASCENT("ascent", "Ascent", "Lifts you into the air", 160),
    FROST("frost", "Frost", "Freezes nearby creatures in place", 240),
    FLARE("flare", "the Flare", "Sets nearby creatures alight", 240),
    PHASE("phase", "Phasing", "Turns you invisible for a while", 600),
    SURGE("surge", "the Surge", "Grants a burst of speed and haste", 400),
    INSIGHT("insight", "Insight", "Reveals every creature around you", 300),
    STARFALL("starfall", "Starfall", "Calls lightning onto your foes", 500);

    public static final Codec<RelicPower> CODEC = StringRepresentable.fromEnum(RelicPower::values);

    public final String id;
    public final String title;
    public final String description;
    public final int cooldown;

    RelicPower(String id, String title, String description, int cooldown) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.cooldown = cooldown;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
