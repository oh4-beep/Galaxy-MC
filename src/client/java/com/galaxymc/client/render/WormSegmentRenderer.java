package com.galaxymc.client.render;

import com.galaxymc.entity.Species;
import com.galaxymc.entity.SpeciesRegistry;
import com.galaxymc.entity.WormSegment;
import com.galaxymc.registry.Reg;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * One renderer for every worm's body. The segment's synced species index picks which baked segment
 * model and texture to draw; size and taper come from the segment's scale attribute.
 */
public class WormSegmentRenderer extends MobRenderer<WormSegment, CreatureRenderState, CreatureModel> {
    private final Map<Integer, CreatureModel> models = new HashMap<>();
    private final Map<Integer, Identifier> textures = new HashMap<>();
    private final Map<Integer, Identifier> markings = new HashMap<>();
    private final CreatureModel fallback;

    public WormSegmentRenderer(EntityRendererProvider.Context context) {
        super(context, firstModel(context), 0.6F);
        this.fallback = this.model;
        for (Species s : SpeciesRegistry.all()) {
            if (s.kind != Species.Kind.WORM) {
                continue;
            }
            String name = s.id + "_segment";
            models.put(s.index, new CreatureModel(context.bakeLayer(CreatureRenderer.layer(name)), CreatureModelData.load(name), true));
            textures.put(s.index, Reg.id(s.segmentTexture + ".png"));
            markings.put(s.index, Reg.id(s.segmentTexture + "_markings.png"));
        }
        addLayer(new SegmentTint(this));
    }

    private static CreatureModel firstModel(EntityRendererProvider.Context context) {
        for (Species s : SpeciesRegistry.all()) {
            if (s.kind == Species.Kind.WORM) {
                String name = s.id + "_segment";
                return new CreatureModel(context.bakeLayer(CreatureRenderer.layer(name)), CreatureModelData.load(name), true);
            }
        }
        throw new IllegalStateException("no worm species");
    }

    @Override
    public void submit(CreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = models.getOrDefault(state.species, fallback);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(CreatureRenderState state) {
        return textures.getOrDefault(state.species, textures.values().iterator().next());
    }

    @Override
    public CreatureRenderState createRenderState() {
        return new CreatureRenderState();
    }

    @Override
    public void extractRenderState(WormSegment entity, CreatureRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.species = entity.species() == null ? -1 : entity.species().index;
        state.strainColor = entity.strainColor();
        state.bodyPitch = entity.getXRot(partialTick);
        state.limbPos = entity.tickCount + partialTick;
        state.limbSpeed = 0.6F;
    }

    private final class SegmentTint extends RenderLayer<CreatureRenderState, CreatureModel> {
        SegmentTint(RenderLayerParent<CreatureRenderState, CreatureModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state, float yRot, float xRot) {
            Identifier tex = markings.get(state.species);
            if (tex == null || state.strainColor == -1 || state.isInvisible) {
                return;
            }
            collector.submitModel(getParentModel(), state, poseStack, getParentModel().renderType(tex), light,
                    OverlayTexture.NO_OVERLAY, ARGB.opaque(state.strainColor), null);
        }
    }
}
