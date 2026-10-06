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

	protected double x;
	protected double y;
	protected double w;
	protected double h;
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
		ctx.matrices.pushPose();
		ctx.matrices.scale(ctx.scriptScale, ctx.scriptScale, ctx.scriptScale);
		ctx.matrices.translate((float) x, (float) y, z);
	}

	final void popTransform(ScriptRenderContext ctx) {
		ctx.matrices.popPose();
	}
}
