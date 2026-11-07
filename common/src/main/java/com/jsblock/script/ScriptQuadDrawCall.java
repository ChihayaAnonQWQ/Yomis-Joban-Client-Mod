package com.jsblock.script;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.IGui;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * {@code QuadDrawCall.create().renderType("EXTERIOR").texture(...).color(0x333333).normal(0,1,0)
 * .quad(V(x1,y1,z1), V(x2,y2,z2), V(x3,y3,z3), V(x4,y4,z4))} drawn through {@code ctx.renderManager()}.
 *
 * <p>JCM 2.x's shell effect — the grey box Japanese_PIDS wraps around its panel — is built from five
 * of these, one per face. The vertices are in <b>blocks</b>, not script units: the pack writes
 * {@code -32 / 96} and expects it to land 32 script units left of the panel origin, so the vertices
 * are multiplied by {@link #BASE_SCALE}'s reciprocal when they are drawn.</p>
 *
 * <p>{@code renderType} is accepted and ignored, like the same method on {@link ScriptDrawCalls.Texture}:
 * this port draws scripts through MTR's light layers and has no equivalent of JCM 2.x's named render
 * types. The normal is likewise accepted and used only as a hint for the vertex format.</p>
 */
public class ScriptQuadDrawCall extends ScriptDrawCall<ScriptQuadDrawCall> {

	private String renderType = "EXTERIOR";
	private ResourceLocation textureId;
	private int color = IGui.ARGB_WHITE;
	private ScriptMath.Vector3f normal = new ScriptMath.Vector3f(0, 0, 1);
	private final ScriptMath.Vector3f[] vertices = new ScriptMath.Vector3f[4];

	private ScriptQuadDrawCall() {
		super(0, 0);
	}

	/** {@code QuadDrawCall.create()} */
	public static ScriptQuadDrawCall create() {
		return new ScriptQuadDrawCall();
	}

	/** Accepted for JCM 2.x compatibility; see the class comment. */
	public ScriptQuadDrawCall renderType(String renderType) {
		this.renderType = renderType == null ? "" : renderType;
		return this;
	}

	/** {@code mtr:textures/block/white.png} and friends. */
	public ScriptQuadDrawCall texture(String id) {
		this.textureId = ScriptPaths.resource(id);
		return this;
	}

	/** A plain RGB literal, as JCM 2.x's wrappers take; alpha is forced opaque. */
	public ScriptQuadDrawCall color(int color) {
		this.color = color;
		return this;
	}

	/** {@code .normal(0, 1, 0)} — the face's outward direction. */
	public ScriptQuadDrawCall normal(double x, double y, double z) {
		this.normal = new ScriptMath.Vector3f(x, y, z);
		return this;
	}

	/** The four corners, in blocks, in the order the face is meant to be wound. */
	public ScriptQuadDrawCall quad(Object a, Object b, Object c, Object d) {
		this.vertices[0] = asVector(a);
		this.vertices[1] = asVector(b);
		this.vertices[2] = asVector(c);
		this.vertices[3] = asVector(d);
		return this;
	}

	private static ScriptMath.Vector3f asVector(Object value) {
		if (!(value instanceof ScriptMath.Vector3f)) {
			throw new IllegalArgumentException("quad() expects four Vector3f corners, got "
					+ (value == null ? "null" : value.getClass().getName()));
		}
		return (ScriptMath.Vector3f) value;
	}

	@Override
	public void validate() {
		if (textureId == null) {
			throw new IllegalStateException("QuadDrawCall has no texture");
		}
		for (ScriptMath.Vector3f vertex : vertices) {
			if (vertex == null) {
				throw new IllegalStateException("QuadDrawCall needs four corners via quad(...)");
			}
		}
	}

	@Override
	void draw(ScriptRenderContext ctx, float z) {
		ctx.drawQuad(this, z);
	}

	String renderType() {
		return renderType;
	}

	ResourceLocation textureId() {
		return textureId;
	}

	int color() {
		return color;
	}

	ScriptMath.Vector3f normal() {
		return normal;
	}

	ScriptMath.Vector3f vertex(int index) {
		return vertices[index];
	}

	@Override
	public String toString() {
		return "QuadDrawCall(texture=" + textureId + " color=" + String.format("%08X", color)
				+ " normal=" + normal.x() + "," + normal.y() + "," + normal.z() + ")";
	}
}
