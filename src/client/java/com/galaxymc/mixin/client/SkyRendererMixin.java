package com.galaxymc.mixin.client;

import com.galaxymc.GalaxyMC;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Space is darker and clearer than Earth's sky: on Galaxy MC worlds the vanilla 1,500 stars are swapped
 * for a field of about 9,000, bunched into a glowing galactic band across the sky, with a few bright
 * stars among many faint ones. Earth keeps its own sky.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique
    private static final int GALAXY_MC_STARS = 9000;

    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Shadow
    @Final
    private RenderSystem.AutoStorageIndexBuffer quadIndices;

    @Unique
    private GpuBuffer galaxy_mc$stars;

    @Unique
    private int galaxy_mc$starIndexCount;

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void galaxy_mc$denseStars(float starBrightness, PoseStack poseStack, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !GalaxyMC.MOD_ID.equals(mc.level.dimension().identifier().getNamespace())) {
            return;
        }
        if (galaxy_mc$stars == null) {
            galaxy_mc$stars = galaxy_mc$build();
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(poseStack.last().pose());
        GpuBuffer indexBuffer = quadIndices.getBuffer(galaxy_mc$starIndexCount);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
                new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness));
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Galaxy MC stars",
                renderTarget.getColorTextureView(), Optional.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.STARS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, galaxy_mc$stars.slice());
            pass.setIndexBuffer(indexBuffer, quadIndices.type());
            pass.drawIndexed(galaxy_mc$starIndexCount, 1, 0, 0, 0);
        }
        modelView.popMatrix();
        ci.cancel();
    }

    @Unique
    private GpuBuffer galaxy_mc$build() {
        RandomSource random = RandomSource.createThreadLocalInstance(0x6A1AC7L);
        // The galactic plane: stars within the band are picked from a thin gaussian around it.
        Vector3f pole = new Vector3f(0.35F, 0.55F, 0.76F).normalize();
        Vector3f u = new Vector3f(pole).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f v = new Vector3f(pole).cross(u).normalize();
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * GALAXY_MC_STARS * 4)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            for (int i = 0; i < GALAXY_MC_STARS; i++) {
                Vector3f dir;
                boolean band = random.nextFloat() < 0.45F;
                if (band) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    float spread = (float) (random.nextGaussian() * 0.12);
                    dir = new Vector3f(u).mul((float) Math.cos(a)).add(new Vector3f(v).mul((float) Math.sin(a))).add(new Vector3f(pole).mul(spread));
                } else {
                    dir = new Vector3f(random.nextFloat() * 2.0F - 1.0F, random.nextFloat() * 2.0F - 1.0F, random.nextFloat() * 2.0F - 1.0F);
                }
                float len = dir.lengthSquared();
                if (len <= 0.0001F || !band && len > 1.0F) {
                    continue;
                }
                Vector3f centre = dir.normalize(100.0F);
                // Mostly faint pinpricks, a few bright stars.
                float f = random.nextFloat();
                float size = 0.07F + f * f * f * 0.33F;
                float spin = (float) (random.nextDouble() * Math.PI * 2.0);
                Matrix3f rotation = new Matrix3f().rotateTowards(new Vector3f(centre).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-spin);
                builder.addVertex(new Vector3f(size, -size, 0.0F).mul(rotation).add(centre));
                builder.addVertex(new Vector3f(size, size, 0.0F).mul(rotation).add(centre));
                builder.addVertex(new Vector3f(-size, size, 0.0F).mul(rotation).add(centre));
                builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(rotation).add(centre));
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                galaxy_mc$starIndexCount = mesh.drawState().indexCount();
                return RenderSystem.getDevice().createBuffer(() -> "Galaxy MC stars", 40, mesh.vertexBuffer());
            }
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void galaxy_mc$closeStars(CallbackInfo ci) {
        if (galaxy_mc$stars != null) {
            galaxy_mc$stars.close();
            galaxy_mc$stars = null;
        }
    }
}
