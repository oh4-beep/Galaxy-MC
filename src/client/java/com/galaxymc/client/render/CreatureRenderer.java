package com.galaxymc.client.render;

import com.galaxymc.entity.AlienMob;
import com.galaxymc.entity.Species;
import com.galaxymc.registry.Reg;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Renders any ground, flying or worm-head creature. Three passes: the species' hide, its markings
 * tinted in the local planet strain's colour, and its eyes at full brightness so they glow in the dark.
 */
public class CreatureRenderer extends MobRenderer<AlienMob, CreatureRenderState, CreatureModel> {
    private static final int FULL_BRIGHT = 0xF000F0;

    private final Identifier texture;

    public CreatureRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new CreatureModel(context.bakeLayer(layer(species.id)), CreatureModelData.load(species.id), species.kind == Species.Kind.WORM),
                Math.min(3.0F, species.width * 0.5F));
        this.texture = Reg.id(species.texture + ".png");
        addLayer(new TintLayer(this, Reg.id(species.texture + "_markings.png")));
        addLayer(new GlowLayer(this, Reg.id(species.texture + "_eyes.png")));
    }

    public static ModelLayerLocation layer(String modelName) {
        return new ModelLayerLocation(Reg.id(modelName), "main");
    }

    @Override
    public Identifier getTextureLocation(CreatureRenderState state) {
        return texture;
    }

    @Override
    public CreatureRenderState createRenderState() {
        return new CreatureRenderState();
    }

    @Override
    public void extractRenderState(AlienMob entity, CreatureRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.strainColor = entity.strainColor();
        state.disguised = entity.disguised();
        state.limbPos = entity.walkAnimation.position(partialTick);
        state.limbSpeed = entity.walkAnimation.speed(partialTick);
        float body = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float head = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        state.headYaw = Mth.clamp(Mth.wrapDegrees(head - body), -75.0F, 75.0F);
        state.headPitch = entity.getXRot(partialTick);
        state.bodyPitch = entity.getXRot(partialTick);
        state.attack = entity.getAttackAnim(partialTick);
        state.species = entity.species() == null ? -1 : entity.species().index;
    }

    /** Markings, tinted per planet strain. */
    static final class TintLayer extends RenderLayer<CreatureRenderState, CreatureModel> {
        private final Identifier texture;

        TintLayer(RenderLayerParent<CreatureRenderState, CreatureModel> parent, Identifier texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state, float yRot, float xRot) {
            if (state.strainColor == -1 || state.isInvisible) {
                return;
            }
            collector.submitModel(getParentModel(), state, poseStack, getParentModel().renderType(texture), light,
                    OverlayTexture.NO_OVERLAY, ARGB.opaque(state.strainColor), null);
        }
    }

    /** Eyes (and other glowing bits) drawn at full brightness. */
    static final class GlowLayer extends RenderLayer<CreatureRenderState, CreatureModel> {
        private final Identifier texture;

        GlowLayer(RenderLayerParent<CreatureRenderState, CreatureModel> parent, Identifier texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state, float yRot, float xRot) {
            if (state.isInvisible || state.disguised) {
                return;
            }
            collector.submitModel(getParentModel(), state, poseStack, getParentModel().renderType(texture), FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, -1, null);
        }
    }
}
