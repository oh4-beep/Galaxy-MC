package com.galaxymc.mineral;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What a procedural mineral is good for. Thermal traits feed the Thermal Armor Table, VOLATILE burns as
 * starship fuel, and the rest are infused into gear at the Stellar Forge.
 */
public enum MineralTrait implements StringRepresentable {
    WARMTH("warmth", "Insulating", 0xff8a3a),
    COOLING("cooling", "Cryogenic", 0x7ad8ff),
    VOLATILE("volatile", "Volatile", 0xffd23a),
    CONDUCTIVE("conductive", "Conductive", 0x9aff6a),
    SHARP("sharp", "Keen", 0xff5a5a),
    DENSE("dense", "Dense", 0xb0b0c8),
    VITAL("vital", "Vital", 0xff7ad0),
    SWIFT("swift", "Swift", 0x6affd8);

    public static final Codec<MineralTrait> CODEC = StringRepresentable.fromEnum(MineralTrait::values);

    public final String id;
    public final String adjective;
    public final int color;

    MineralTrait(String id, String adjective, int color) {
        this.id = id;
        this.adjective = adjective;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Human description of what {@code power} means for this trait. */
    public String effect(int power) {
        return switch (this) {
            case WARMTH -> "+" + power + "°C cold protection per unit";
            case COOLING -> "+" + power + "°C heat protection per unit";
            case VOLATILE -> power + " coal of fuel per unit";
            case CONDUCTIVE -> "+" + power + " mining speed when infused";
            case SHARP -> "+" + String.format("%.1f", power / 10.0) + " attack damage when infused";
            case DENSE -> "+" + String.format("%.1f", power / 10.0) + " armor when infused";
            case VITAL -> "+" + power / 10 + " max health when infused";
            case SWIFT -> "+" + power + "% speed when infused";
        };
    }
}
