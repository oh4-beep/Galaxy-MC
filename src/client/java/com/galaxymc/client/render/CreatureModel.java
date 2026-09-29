package com.galaxymc.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Animates a generated creature. Each part carries a role from the body plan (leg, wing, tail, jaw,
 * tentacle...) and the model drives it procedurally from the walk cycle, age and head rotation, always
 * relative to the part's rest pose so nothing drifts.
 */
public class CreatureModel extends EntityModel<CreatureRenderState> {
    private record Animated(ModelPart part, CreatureModelData.Anim anim, float x, float y, float z, float px, float py, float pz,
                            boolean topLevel) {}

    private final List<Animated> animated = new ArrayList<>();
    private final boolean worm;

    public CreatureModel(ModelPart root, CreatureModelData data, boolean worm) {
        super(root);
        this.worm = worm;
        Map<String, ModelPart> parts = new HashMap<>();
        for (CreatureModelData.Part p : data.parts()) {
            ModelPart parent = "root".equals(p.parent()) ? root : parts.getOrDefault(p.parent(), root);
            ModelPart part = parent.getChild(p.name());
            parts.put(p.name(), part);
            boolean top = "root".equals(p.parent());
            if (p.anim() != null || worm && top) {
                animated.add(new Animated(part, p.anim(), p.rx(), p.ry(), p.rz(), p.px(), p.py(), p.pz(), top));
            }
        }
    }

    @Override
    public void setupAnim(CreatureRenderState s) {
        super.setupAnim(s);
        float limb = s.limbPos;
        float speed = Math.min(1.0F, s.limbSpeed);
        float age = s.ageInTicks;
        for (Animated a : animated) {
            ModelPart p = a.part();
            p.xRot = a.x();
            p.yRot = a.y();
            p.zRot = a.z();
            p.x = a.px();
            p.y = a.py();
            p.z = a.pz();
            if (worm && a.topLevel()) {
                p.xRot = a.x() + s.bodyPitch * Mth.DEG_TO_RAD;
            }
            CreatureModelData.Anim anim = a.anim();
            if (anim == null || s.disguised) {
                continue;
            }
            float amp = anim.amp();
            float ph = anim.phase();
            float sp = anim.speed();
            switch (anim.role()) {
                case "leg" -> p.xRot = a.x() + Mth.cos(limb * 0.6662F + ph) * 1.2F * speed * amp;
                case "leg_side" -> {
                    p.yRot = a.y() + Mth.cos(limb * 0.9F + ph) * 0.45F * speed * amp;
                    p.zRot = a.z() + Math.abs(Mth.sin(limb * 0.9F + ph)) * 0.25F * speed * Math.signum(a.px() == 0 ? 1 : a.px());
                }
                case "arm" -> p.xRot = a.x() + Mth.cos(limb * 0.6662F + ph) * 0.8F * speed * amp - s.attack * 1.8F;
                case "head" -> {
                    p.yRot = a.y() + s.headYaw * Mth.DEG_TO_RAD;
                    p.xRot = a.x() + s.headPitch * Mth.DEG_TO_RAD;
                }
                case "head_hover" -> {
                    p.yRot = a.y() + s.headYaw * Mth.DEG_TO_RAD;
                    p.xRot = a.x() + s.headPitch * Mth.DEG_TO_RAD;
                    p.y = a.py() + Mth.sin(age * 0.08F + ph) * 1.5F * amp;
                }
                case "jaw" -> p.xRot = a.x() + 0.12F + Mth.sin(age * 0.22F + ph) * 0.07F * amp + s.attack * 0.8F;
                case "tail" -> p.yRot = a.y() + Mth.sin(age * 0.12F * sp + ph) * 0.35F * amp + Mth.cos(limb * 0.6662F) * 0.25F * speed;
                case "wing" -> p.zRot = a.z() + Mth.sin(age * 0.45F * sp + ph) * 0.65F * amp;
                case "tentacle" -> {
                    p.xRot = a.x() + Mth.sin(age * 0.1F * sp + ph) * amp;
                    p.zRot = a.z() + Mth.cos(age * 0.08F * sp + ph) * amp * 0.5F;
                }
                case "antenna" -> p.zRot = a.z() + Mth.sin(age * 0.15F + ph) * 0.15F;
                case "bounce" -> p.y = a.py() - Math.abs(Mth.sin(limb * 0.35F)) * 2.0F * amp * speed + Mth.sin(age * 0.2F) * 0.3F;
                case "spin" -> p.yRot = a.y() + age * 0.05F * sp;
                case "hover" -> p.y = a.py() + Mth.sin(age * 0.08F * sp + ph) * 1.2F * amp;
                case "sway" -> {
                    p.zRot = a.z() + Mth.sin(age * 0.05F + ph) * amp * 0.5F;
                    p.xRot = a.x() + Mth.cos(age * 0.04F + ph) * amp * 0.3F;
                }
                case "body" -> p.y = a.py() + Mth.sin(age * 0.1F + ph) * 0.3F * amp;
                default -> {
                }
            }
        }
    }
}
