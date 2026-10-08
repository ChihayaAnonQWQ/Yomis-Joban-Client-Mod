package com.jsblock.script;

import com.jsblock.pids.PIDSComponent;
import com.jsblock.pids.PIDSGraphics;

/**
 * The object {@code ctx.parseComponent(jsonString)} hands back to a script.
 *
 * <h2>What JCM 2.x does</h2>
 * <p>{@code PIDSScriptContext.parseComponent(String)} is
 * {@code JsonParser.parseString(s).getAsJsonObject()} into
 * {@code PIDSComponent.parse(JsonObject)} and nothing else, so a v2 script receives the raw
 * component. Its public surface is {@code canRender(PIDSContext)} and
 * {@code render(PoseStack, MultiBufferSource, Direction, PIDSContext)}.</p>
 *
 * <p>That signature cannot be called from JavaScript: a script has no {@code PIDSContext} and no
 * {@code PoseStack} — they belong to the frame the engine is drawing, not to the script. It is
 * also not reachable through {@code ctx.draw(...)}, which v2 restricts to {@code PIDSDrawCall} (its
 * {@code draw} casts and throws {@code "1st parameter is not a DrawCall!"} for anything else), and
 * {@code PIDSComponent} is not one. So this wrapper changes the parameter list and nothing else:
 * the same component, the same parse, the same {@code canRender} rule, with the frame's render
 * state supplied by the engine the script is running inside.</p>
 *
 * <h2>The surface</h2>
 * <ul>
 *   <li>{@code render(ctx)} — draws the component where its JSON placed it, in script units. This
 *       is v2's {@code render(...)} with the frame's four arguments already bound.</li>
 *   <li>{@code canRender()} — v2's {@code canRender(PIDSContext)}, evaluated against the frame's
 *       data. A script that wants the JSON path's own behaviour — a clock with no world, an
 *       arrival row with no train — asks this before drawing.</li>
 *   <li>{@code x()} {@code y()} {@code width()} {@code height()} — v2's four fields, which are
 *       {@code protected} there and therefore invisible to a script; here they can be read, which
 *       is what lets a script lay one component out against another.</li>
 *   <li>{@code type()} — the {@code component} name it was parsed from, for diagnostics.</li>
 * </ul>
 *
 * @see ScriptRenderContext#parseComponent(String)
 */
public final class ScriptComponent {

	/** The parsed component this wrapper draws. */
	private final PIDSComponent component;
	/** The {@code component} key it was declared with, kept for {@link #type()}. */
	private final String type;
	/**
	 * The frame's panel data, which is what v2's {@code canRender(PIDSContext)} takes as its
	 * argument. Bound at parse time because a script has no way to build one.
	 */
	private final com.jsblock.pids.PIDSContext context;

	ScriptComponent(PIDSComponent component, String type, com.jsblock.pids.PIDSContext context) {
		this.component = component;
		this.type = type;
		this.context = context;
	}

	/** {@code component.render(ctx)} — draws it, in the frame the script is drawing. */
	public void render(ScriptRenderContext ctx) {
		if (ctx == null) {
			throw new IllegalArgumentException(
					"component.render(ctx) needs the ctx the script was called with, not null");
		}
		ctx.renderComponent(this);
	}

	/**
	 * {@code component.canRender()} — whether the JSON layout path would draw this component in
	 * this frame.
	 *
	 * <p>Always {@code true} while no panel data is available, which is the case in the headless
	 * check: there is nothing to consult, so the component is not withheld.</p>
	 */
	public boolean canRender() {
		return context == null || component.canRender(context);
	}

	/** {@code component.x()} — left edge, in script units. */
	public double x() {
		return component.getX();
	}

	/** {@code component.y()} — top edge, in script units. */
	public double y() {
		return component.getY();
	}

	/** {@code component.width()} — width, in script units. */
	public double width() {
		return component.getWidth();
	}

	/** {@code component.height()} — height, in script units. */
	public double height() {
		return component.getHeight();
	}

	/** {@code component.type()} — the {@code component} name from the JSON, e.g. {@code clock}. */
	public String type() {
		return type;
	}

	/**
	 * Draws the component through the frame's graphics.
	 *
	 * @param graphics the panel's render state; {@code matrices} must already be in script units,
	 *                 which is what {@link ScriptRenderContext#renderComponent} guarantees
	 */
	void draw(com.jsblock.pids.PIDSContext context, PIDSGraphics graphics) {
		component.render(context, graphics,
				(float) component.getX(), (float) component.getY(),
				(float) component.getWidth(), (float) component.getHeight());
	}

	/** @return a one-line description, used by the headless check and the draw-call trace. */
	public String describe() {
		return String.format("Component(type=%s pos=%s,%s size=%sx%s)",
				type, x(), y(), width(), height());
	}
}
