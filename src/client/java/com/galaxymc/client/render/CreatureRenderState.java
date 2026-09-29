package com.galaxymc.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Everything a creature model needs for one frame, extracted from the entity on the render thread. */
public class CreatureRenderState extends LivingEntityRenderState {
    public int strainColor = -1;
    public float limbPos;
    public float limbSpeed;
    public float headYaw;
    public float headPitch;
    public float attack;
    public float bodyPitch;
    public boolean disguised;
    public int species = -1;
}
