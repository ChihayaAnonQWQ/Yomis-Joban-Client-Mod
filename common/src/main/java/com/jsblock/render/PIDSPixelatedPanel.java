package com.jsblock.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * One offscreen target per (canvas size, pixel scale), for the whole-screen pixelation feature.
 *
 * <h2>What it is for</h2>
 * <p>A PIDS script draws at whatever resolution the texture it names happens to have, and its text
 * is drawn by the vanilla font renderer, so the panel comes out smooth — thin lines stay thin and
 * glyph edges are antialiased. Packs drawn for a dot-matrix or LCD screen instead want every
 * element to land on one coarse grid, which is what {@code 整屏画完再像素化} describes and what
 * such a pack looks like in the mod it was written for.</p>
 *
 * <p>Reproducing that needs an offscreen pass: draw the script's whole output into a small target,
 * then magnify it with nearest-neighbour filtering so the target's pixels become visible squares.
 * There is no way to do it from inside a script — the scripting API can only name textures that a
 * resource pack provides, and cannot compose one in memory.</p>
 *
 * <h2>How the pass is set up</h2>
 * <p>Vertex positions are transformed when they are written (the script's draws go through
 * {@code IDrawing.drawTexture(ctx.matrices, ...)}, which applies the pose stack at that moment) and
 * the flush afterwards applies {@link RenderSystem#getModelViewMatrix()} and the projection. So a
 * pass that wants canvas coordinates written straight into a target needs exactly two things: an
 * identity modelview, and an orthographic projection over the target's pixels. Nothing else in the
 * script path has to know — the same draw calls, the same call order, the same layers.</p>
 *
 * <h2>Targets are shared and cached</h2>
 * <p>Keyed by size and scale, not by block: two panels of the same shape and the same scale can
 * share one target, and a panel whose preset changes to a different scale picks up a different
 * target rather than reallocating. Targets are small (a 136x76 canvas at scale 4 is 34x19), so a
 * handful of them costs less than one chunk texture.</p>
 *
 * <h2>What can still go wrong</h2>
 * <p>Binding another framebuffer in the middle of the block entity pass is the risky part, and it
 * is risk that cannot be removed by care — a shader pack (Iris) that keeps its own targets, or a
 * mod that caches pipeline state across the pass, can both be upset by it. The caller therefore
 * restores the main target explicitly, and anything thrown here is caught and falls back to drawing
 * straight to the world: a preset set to pixelate and unable to is a panel that looks as it did
 * before, not a crash.</p>
 */
public final class PIDSPixelatedPanel {

    /** Keyed by canvas width, canvas height and scale. */
    private static final Map<Long, PIDSPixelatedPanel> CACHE = new HashMap<>();

    private final RenderTarget target;
    private final ResourceLocation location;
    private final int scale;

    /** The pose stack the offscreen pass writes through; one canvas unit is one target pixel / scale. */
    private final PoseStack offscreenStack = new PoseStack();

    private Matrix4f savedProjection;
    private VertexSorting savedSorting;
    /** The framebuffer that was bound when the pass started, read back rather than assumed. */
    private int savedFramebuffer = -1;
    /** Whether back-face culling was on, read back for the same reason. */
    private boolean savedCull = true;
    /** Whether the pass actually pushed anything, so {@link #end()} can pop exactly as much. */
    private boolean modelViewPushed = false;
    private boolean stackPushed = false;

    private PIDSPixelatedPanel(int canvasWidth, int canvasHeight, int scale) {
        this.scale = scale;
        final int width = Math.max(1, canvasWidth / scale);
        final int height = Math.max(1, canvasHeight / scale);

        this.target = new TextureTarget(width, height, false, true);
        /* Transparent, so a preset that leaves part of its canvas empty shows the world through it
           exactly as it does when drawn directly. Red was used while bringing this up: it separated
           "nothing reached the target" from "the target is fine and the composite does not sample
           it" in a single run, which is otherwise indistinguishable on screen. */
        this.target.setClearColor(0F, 0F, 0F, 0F);
        /* Nearest, not linear: the whole point is that the target's pixels stay square when it is
           magnified onto the panel. */
        this.target.setFilterMode(org.lwjgl.opengl.GL11.GL_NEAREST);

        this.location = new ResourceLocation("jsblock", "pids_pixelated/" + width + "x" + height);
        Minecraft.getInstance().getTextureManager().register(this.location, new TargetTexture(this.target));
    }

    /** @return the shared panel for this shape and scale, creating it on first use. */
    public static PIDSPixelatedPanel of(int canvasWidth, int canvasHeight, int scale) {
        final long key = ((long) canvasWidth << 40) | ((long) canvasHeight << 8) | (scale & 0xFF);
        return CACHE.computeIfAbsent(key, ignored -> new PIDSPixelatedPanel(canvasWidth, canvasHeight, scale));
    }

    public ResourceLocation location() {
        return location;
    }

    /** The target's size in canvas units, which is what the composite quad has to cover. */
    public float canvasWidth() {
        return target.width * (float) scale;
    }

    /** The target's size in pixels; logged when the pass first runs. */
    public int targetWidth() {
        return target.width;
    }

    /** @see #targetWidth() */
    public int targetHeight() {
        return target.height;
    }

    /**
     * Reads the target's centre pixel back. Must be called while the pass is still open.
     *
     * <p>A one-shot diagnostic, and the one that would have answered the first two attempts
     * immediately: a black panel can mean "nothing was drawn into the target" or "the target was
     * drawn into but the composite does not sample it", and those need opposite fixes. Reading four
     * bytes out of a 34x19 target once costs one pipeline stall, which is nothing next to another
     * round of guessing.</p>
     */
    public int[] readCentrePixel() {
        final java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(4);
        org.lwjgl.opengl.GL11.glReadPixels(this.target.width / 2, this.target.height / 2, 1, 1,
                org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
        return new int[]{buffer.get(0) & 0xFF, buffer.get(1) & 0xFF, buffer.get(2) & 0xFF, buffer.get(3) & 0xFF};
    }

    /** Any GL error left over from the pass, for the diagnostic line. */
    public String bindingReport() {
        final int error = org.lwjgl.opengl.GL11.glGetError();
        return "glError=0x" + Integer.toHexString(error);
    }

    /**
     * Uploads one finished layer into this target, in the only order that works.
     *
     * <p>{@code RenderType.setupRenderState} ends by running the layer's output state, and the output
     * Minecraft builds for every layer is {@code MAIN_TARGET} — so the setup binds the world's
     * framebuffer, and an upload straight after it lands in the world no matter what was bound
     * before. This is what the offscreen pass got wrong three times over without saying so: the
     * clear reached the target (a clear is a plain GL call, with no layer involved) while every
     * quad and every run of text went to the screen instead.</p>
     *
     * <p>Hence the split: setup, then put this target back, then upload.</p>
     */
    public void upload(RenderType layer, com.mojang.blaze3d.vertex.BufferBuilder builder) {
        if (!builder.building()) {
            return;
        }
        final com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer rendered = builder.end();
        layer.setupRenderState();
        this.target.bindWrite(true);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(rendered);
        layer.clearRenderState();
    }

    /**
     * Where the script's text goes, when the panel is drawn offscreen.
     *
     * <p>The vanilla font renderer takes a {@code MultiBufferSource.BufferSource} and the caller
     * normally hands it the immediate one, whose {@code endBatch} goes through {@code RenderType.end}
     * — the path that binds the main framebuffer and drops the text into the world. This subclass
     * keeps one builder per layer of its own and flushes them through
     * {@link PIDSPixelatedPanel#upload}, so text reaches the target like everything else.</p>
     *
     * <p>It extends rather than implements because MTR's font helper asks for the concrete type;
     * the inherited builders are never written to, so the base class's own flush is a no-op here.</p>
     */
    public static final class OffscreenSource extends net.minecraft.client.renderer.MultiBufferSource.BufferSource {
        private final PIDSPixelatedPanel panel;
        private final Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> builders = new java.util.LinkedHashMap<>();

        public OffscreenSource(PIDSPixelatedPanel panel) {
            super(new com.mojang.blaze3d.vertex.BufferBuilder(256), new java.util.HashMap<>());
            this.panel = panel;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType layer) {
            return this.builders.computeIfAbsent(layer, key -> {
                final com.mojang.blaze3d.vertex.BufferBuilder builder = new com.mojang.blaze3d.vertex.BufferBuilder(1536);
                builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, key.format());
                return builder;
            });
        }

        /** Uploads everything drawn so far, in the order the layers were first asked for. */
        public void flush() {
            for (final Map.Entry<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> entry : this.builders.entrySet()) {
                this.panel.upload(entry.getKey(), entry.getValue());
            }
            this.builders.clear();
        }
    }

    /** @see #canvasWidth() */
    public float canvasHeight() {
        return target.height * (float) scale;
    }

    /**
     * Binds the target and sets up the pass. Pair every call with {@link #end()}.
     *
     * @return the pose stack the script's draws must use; it already carries the scale down to the
     *         target's pixels, so canvas coordinates can be written as they are.
     */
    public PoseStack begin() {
        final Minecraft minecraft = Minecraft.getInstance();

        this.savedProjection = RenderSystem.getProjectionMatrix();
        this.savedSorting = RenderSystem.getVertexSorting();
        /* Read the bound framebuffer back instead of assuming it is the main target: this runs in
           the middle of the block entity pass, where a shader pack may well have bound one of its
           own, and binding the wrong one afterwards corrupts everything drawn after -- the held
           item and the player model, which is exactly what happened the first time this was tried. */
        this.savedFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        this.savedCull = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_CULL_FACE) != 0;

        this.target.bindWrite(true);
        this.target.clear(Minecraft.ON_OSX);

        /* Identity modelview: the vertices already carry their own transform. */
        final PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        this.modelViewPushed = true;
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        /* Orthographic, built by hand rather than through a helper: the mapping wanted here is
           narrow enough to write out, and writing it out makes both unusual parts explicit.
           
           x: [0, width]  -> [-1, 1]
           y: [0, height] -> [ 1, -1]   (y down: the script's origin is the panel's top left, and
                                         that is where the orthographic pass has to put it)
           z: divided by 1000, so the per-call depth step stays ordered without a depth range to
              fight over -- the layers do not write depth anyway, the call order decides.
           
           COLUMN-major order: JOML's set(...) lays the sixteen floats out column by column, so the
           translation belongs in the last four, not at the end of each row. Written row-major (the
           obvious reading) the matrix comes out transposed, the projection maps the canvas to
           (0..2, 0..-2) and every vertex is clipped -- which shows up as a panel that clears
           correctly and then draws nothing at all, because a clear does not use the projection.
           The two candidate layouts were checked numerically outside the game: only this one maps
           (0,0) to (-1,1) and (width,height) to (1,-1). */
        final float width = this.target.width;
        final float height = this.target.height;
        final Matrix4f orthographic = new Matrix4f().set(
                2F / width, 0F, 0F, 0F,
                0F, -2F / height, 0F, 0F,
                0F, 0F, 1F / 1000F, 0F,
                -1F, 1F, 0F, 1F);
        RenderSystem.setProjectionMatrix(orthographic, VertexSorting.ORTHOGRAPHIC_Z);

        /* Culling off for the pass.
           
           The script's quads are emitted for a world-space facing, which is authored in a Y-up
           frame; this pass is Y-down, so the winding comes out reversed and every quad is a back
           face. Left on, the target ends up empty and the panel draws as a black rectangle -- which
           is what the first version did. */
        RenderSystem.disableCull();

        this.offscreenStack.pushPose();
        this.stackPushed = true;
        this.offscreenStack.scale(1F / this.scale, 1F / this.scale, 1F);

        return this.offscreenStack;
    }

    /** Ends the pass and puts the framebuffer, viewport, modelview, projection and cull back. */
    public void end() {
        if (this.stackPushed) {
            this.offscreenStack.popPose();
            this.stackPushed = false;
        }

        if (this.savedCull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }

        if (this.modelViewPushed) {
            RenderSystem.getModelViewStack().popPose();
            this.modelViewPushed = false;
            RenderSystem.applyModelViewMatrix();
        }

        if (this.savedProjection != null) {
            RenderSystem.setProjectionMatrix(this.savedProjection, this.savedSorting);
            this.savedProjection = null;
        }

        /* bindWrite on the target set the viewport to the target's size, so put the full-window one
           back before anything else draws. */
        final com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());

        if (this.savedFramebuffer >= 0) {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, this.savedFramebuffer);
        }

        /* Read the state back and say so if it did not take.
           
           This is the check the first version should have had: the failure it shipped with -- a
           framebuffer left bound that nothing said anything about -- showed up as the player's own
           model vanishing, which is a bad way to find out. One line in the log is a much better
           way, and it is written once rather than once per panel per frame. */
        final int frameBufferNow = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        if (frameBufferNow != this.savedFramebuffer) {
            reportLeakOnce("framebuffer", this.savedFramebuffer, frameBufferNow);
        }
        final boolean cullNow = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_CULL_FACE) != 0;
        if (cullNow != this.savedCull) {
            reportLeakOnce("back-face culling", this.savedCull ? 1 : 0, cullNow ? 1 : 0);
        }
    }

    /** Set once anything has been reported, so the log gets one line and not one per frame. */
    private static boolean leakReported = false;

    private static void reportLeakOnce(String what, int expected, int actual) {
        if (leakReported) {
            return;
        }
        leakReported = true;
        com.jsblock.Joban.LOGGER.warn("[PIDS pixelation] " + what + " did not restore: expected "
                + expected + ", found " + actual + ". Something drawn after the panel may look wrong;"
                + " remove the preset's entry from pixelScaleByPreset to turn the feature off."
                + " Reported once.");
    }

    /**
     * The target's colour texture, exposed to the texture manager.
     *
     * <p>{@code RenderType} names textures by {@link ResourceLocation}, and resolves them through
     * the texture manager to a GL id. Registering a texture whose id <em>is</em> the render target's
     * colour attachment is what lets an ordinary layer sample the offscreen pass; the alternative,
     * copying pixels back to the CPU once a frame, would cost far more than the feature is worth.</p>
     */
    private static final class TargetTexture extends AbstractTexture {
        private TargetTexture(RenderTarget target) {
            this.id = target.getColorTextureId();
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
            /* Nothing to load: the id belongs to a framebuffer, not to a file. */
        }
    }
}
