package com.jsblock.script;

/**
 * The object behind {@code ctx.renderManager()}.
 *
 * <p>JCM 2.x exposes a render manager with {@code draw}, {@code queue}, {@code drawModel} and
 * {@code drawDynamicModel}; packs use {@code draw} for the 3D shell around a panel and the other
 * three for vehicle models, which a PIDS script on MTR 3 has no access to. The model methods are
 * therefore accepted and ignored rather than missing — a pack that calls them should not throw.</p>
 */
public final class ScriptRenderManager {

	private final ScriptRenderContext context;

	ScriptRenderManager(ScriptRenderContext context) {
		this.context = context;
	}

	/** {@code rm.draw(call)} — draws a {@code QuadDrawCall} (or any other draw call) now. */
	public void draw(Object call) {
		context.draw(call);
	}

	/** JCM 2.x's queued spelling; this port draws immediately, so it is the same call. */
	public void queue(Object call) {
		context.draw(call);
	}

	/** Accepted and ignored: a PIDS script has no vehicle model to draw. */
	public void drawModel(Object... args) {
	}

	/** Accepted and ignored; see {@link #drawModel}. */
	public void drawDynamicModel(Object... args) {
	}
}
