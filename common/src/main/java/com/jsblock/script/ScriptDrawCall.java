package com.jsblock.script;

/**
 * One positioned drawing operation produced by a PIDS script.
 *
 * <p>Port of JCM 2.x's {@code PIDSDrawCall}. Scripts chain
 * {@code Text.create("name").text(...).pos(x, y).size(w, h)....draw(ctx)}, so the builder
 * methods return the concrete type to keep chaining typed inside Rhino.</p>
 *
 * <p>{@code pos()} is in script units and {@code 1} script unit is {@code 1/96} of a block,
 * matching {@code PIDSPresetBase.BASE_SCALE}.</p>
 */
public abstract class ScriptDrawCall<T extends ScriptDrawCall<?>> {

	/** Script units per block, from JCM 2.x's {@code PIDSPresetBase.BASE_SCALE = 1/96F}. */
	public static final float BASE_SCALE = 1F / 96F;

	/** JCM 2.x's {@code RenderHelper.ARGB_BLACK}. */
	protected static final int ARGB_BLACK = 0xFF000000;

	/**
	 * Makes a draw call's colour opaque, which is what every JCM 2.x wrapper does to it.
	 *
	 * <p>A preset writes a plain RGB literal — {@code .color(0xFFFFFF)} for white,
	 * {@code .color(0x009944)} for a route colour — and JCM 2.x's {@code TextureWrapper} and
	 * {@code RectangleWrapper} both draw with {@code ARGB_BLACK + color}. MTR 3's
	 * {@code IDrawing.drawTexture} takes the alpha from {@code color >> 24} and does not add
	 * one, so without this the quad is drawn fully transparent and simply vanishes. That is
	 * what a route-number badge and a route-map line look like when their colour "does
	 * nothing": the text over them still shows, because vanilla's font renderer forces opaque
	 * on its own ({@code if ((color & 0xFC000000) == 0) color |= 0xFF000000}), and nothing in
	 * the texture path does.
	 *
	 * <p>JCM 2.x spells this {@code ARGB_BLACK + color}. {@code |} is the same operation for
	 * every RGB-only value a preset can write (anything below {@code 0x01000000}) and keeps
	 * the default {@code ARGB_WHITE} intact, where {@code +} would carry into the blue channel
	 * and produce {@code 0xFFFFFFFE}.
	 *
	 * <p>The JSON component path already does this — {@code PIDSAlign.color} turns a six-digit
	 * {@code RRGGBB} into {@code 0xFF000000 | value} — so this only brings the script path in
	 * line with the rest of the port.</p>
	 */
	protected static int opaque(int color) {
		return color | ARGB_BLACK;
	}

	protected double x;
	protected double y;
	protected double w;
	protected double h;
	/** Depth offset actually used for this call, recorded so describe() can report it. */
	protected float z;
	protected int zOrderOverride = -1;

	protected ScriptDrawCall(double defaultWidth, double defaultHeight) {
		this.w = defaultWidth;
		this.h = defaultHeight;
	}

	/** {@code .pos(x, y)} — top-left corner in script units. */
	@SuppressWarnings("unchecked")
	public T pos(double x, double y) {
		this.x = x;
		this.y = y;
		return (T) this;
	}

	/** {@code .size(w, h)} — size in script units. */
	@SuppressWarnings("unchecked")
	public T size(double w, double h) {
		this.w = w;
		this.h = h;
		return (T) this;
	}

	/** {@code .zOrder(n)} — pin this call to a depth instead of using declaration order. */
	@SuppressWarnings("unchecked")
	public T zOrder(int order) {
		if (order < 0) {
			throw new IllegalArgumentException("Z-Order must be 0 or above.");
		}
		this.zOrderOverride = order;
		return (T) this;
	}

	/** {@code .draw(ctx)} — hands the call to the context. */
	public void draw(ScriptRenderContext ctx) {
		ctx.draw(this);
	}

	/** @throws IllegalArgumentException when a required property was never set. */
	public void validate() {
	}

	/**
	 * @return a one-line description of this call, used by the headless dry-run check to show
	 * what a script asked for without rendering anything.
	 */
	public String describe() {
		return getClass().getSimpleName() + "(z=" + z + " pos=" + x + "," + y + " size=" + w + "x" + h + ")";
	}

	/**
	 * Draws the call with its transform already applied.
	 *
	 * @param z depth offset for this call, already in local units
	 */
	abstract void draw(ScriptRenderContext ctx, float z);

	/**
	 * Applies the call's transform: {@code scale(scriptScale)} then {@code translate(x, y, z)},
	 * so {@code pos()} is measured in script units from the panel origin. Mirrors the order
	 * JCM 2.x uses in {@code PIDSDrawCall.run}.
	 */
	final void pushTransform(ScriptRenderContext ctx, float z) {
		this.z = z;
		ctx.matrices.pushPose();
		ctx.matrices.scale(ctx.scriptScale, ctx.scriptScale, ctx.scriptScale);
		ctx.matrices.translate((float) x, (float) y, z);
	}

	final void popTransform(ScriptRenderContext ctx) {
		ctx.matrices.popPose();
	}
}
