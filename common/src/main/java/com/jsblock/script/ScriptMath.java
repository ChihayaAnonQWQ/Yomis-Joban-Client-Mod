package com.jsblock.script;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/**
 * The math objects JCM 2.x exposes to every script: {@code Vector3f} and {@code Matrices}.
 *
 * <p>Both are documented under the scripting docs' Common APIs, which is where a script looks for
 * them. A resource pack written against JCM 2.x reaches for them without any ceremony -- the
 * World PIDS-Pack -- the pack that prompted this -- uses {@code new Matrices()} to rotate a clock hand -- and
 * touching a global that is not there ends the script on the spot.</p>
 *
 * <p>The Java classes are named {@code Vector3f} and {@code Matrices}, matching the documentation,
 * so LiveConnect error messages name the same thing the docs do. The game's own JOML
 * {@link Vector3f} is kept as {@link Vec} for the vector the script-visible one wraps.</p>
 */
public final class ScriptMath {

	private ScriptMath() {
	}

	// ==================================================================
	// Vector3f
	// ==================================================================

	/**
	 * A three-dimensional vector, as scripts see it.
	 *
	 * <p>The docs are explicit that {@code x}, {@code y} and {@code z} are <em>methods</em>, and
	 * that is what packs call: {@code pids.blockPos().x()}. Every operation returns the vector
	 * itself so calls can be chained, which is the one place JCM 2.x deliberately differs from
	 * NTE's version of the same class.</p>
	 */
	public static final class Vector3f {

		public static final Vector3f ZERO = new Vector3f(0, 0, 0);
		public static final Vector3f XP = new Vector3f(1, 0, 0);
		public static final Vector3f YP = new Vector3f(0, 1, 0);
		public static final Vector3f ZP = new Vector3f(0, 0, 1);

		private float x;
		private float y;
		private float z;

		public Vector3f(double x, double y, double z) {
			this.x = (float) x;
			this.y = (float) y;
			this.z = (float) z;
		}

		public Vector3f(Vector3f other) {
			this(other.x, other.y, other.z);
		}

		public float x() {
			return x;
		}

		public float y() {
			return y;
		}

		public float z() {
			return z;
		}

		public Vector3f copy() {
			return new Vector3f(this);
		}

		public Vector3f normalize() {
			final float length = (float) Math.sqrt(x * x + y * y + z * z);
			if (length > 0) {
				x /= length;
				y /= length;
				z /= length;
			}
			return this;
		}

		public Vector3f add(double dx, double dy, double dz) {
			x += (float) dx;
			y += (float) dy;
			z += (float) dz;
			return this;
		}

		public Vector3f add(Vector3f other) {
			return add(other.x, other.y, other.z);
		}

		public Vector3f sub(Vector3f other) {
			return add(-other.x, -other.y, -other.z);
		}

		public Vector3f mul(double dx, double dy, double dz) {
			x *= (float) dx;
			y *= (float) dy;
			z *= (float) dz;
			return this;
		}

		public Vector3f mul(double n) {
			return mul(n, n, n);
		}

		public Vector3f rotX(double radians) {
			final float cos = (float) Math.cos(radians);
			final float sin = (float) Math.sin(radians);
			final float ny = y * cos - z * sin;
			final float nz = y * sin + z * cos;
			y = ny;
			z = nz;
			return this;
		}

		public Vector3f rotY(double radians) {
			final float cos = (float) Math.cos(radians);
			final float sin = (float) Math.sin(radians);
			final float nx = x * cos + z * sin;
			final float nz = -x * sin + z * cos;
			x = nx;
			z = nz;
			return this;
		}

		public Vector3f rotZ(double radians) {
			final float cos = (float) Math.cos(radians);
			final float sin = (float) Math.sin(radians);
			final float nx = x * cos - y * sin;
			final float ny = x * sin + y * cos;
			x = nx;
			y = ny;
			return this;
		}

		public Vector3f cross(Vector3f other) {
			final float nx = y * other.z - z * other.y;
			final float ny = z * other.x - x * other.z;
			final float nz = x * other.y - y * other.x;
			x = nx;
			y = ny;
			z = nz;
			return this;
		}

		public float distance(Vector3f other) {
			return (float) Math.sqrt(distanceSq(other));
		}

		public float distanceSq(Vector3f other) {
			final float dx = x - other.x;
			final float dy = y - other.y;
			final float dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}

		public BlockPos rawBlockPos() {
			return BlockPos.containing(x, y, z);
		}

		@Override
		public String toString() {
			return "(" + x + ", " + y + ", " + z + ")";
		}
	}


	// ==================================================================
	// Matrices
	// ==================================================================

	/**
	 * A transformation stack, as scripts see it: {@code new Matrices()}, then push, rotate, pop.
	 *
	 * <p>Applied to a draw call with {@code .matrices(m)}, which is the pairing the docs describe.
	 * Backed by a real {@link PoseStack} so the arithmetic is Minecraft's own, and a script that
	 * pushes without popping gets the same "not empty" complaint from the game as the engine's own
	 * transforms do rather than silently drifting.</p>
	 */
	public static final class Matrices {

		private final PoseStack poseStack = new PoseStack();
		/** How many pushes are outstanding; a script that pops more than it pushes is a no-op. */
		private int depth;

		public void translate(double x, double y, double z) {
			poseStack.translate((float) x, (float) y, (float) z);
		}

		public void rotateX(double radians) {
			poseStack.mulPose(com.mojang.math.Axis.XP.rotation((float) radians));
		}

		public void rotateY(double radians) {
			poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) radians));
		}

		public void rotateZ(double radians) {
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotation((float) radians));
		}

		public void rotateXDegrees(double degrees) {
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) degrees));
		}

		public void rotateYDegrees(double degrees) {
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) degrees));
		}

		public void rotateZDegrees(double degrees) {
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) degrees));
		}

		public void pushPose() {
			poseStack.pushPose();
			depth++;
		}

		public void popPose() {
			/* Balanced push/pop is the script's job, but popping an empty stack would throw out of
			   the whole render; ignoring the extra pop costs nothing and keeps the panel drawing. */
			if (depth > 0) {
				poseStack.popPose();
				depth--;
			}
		}

		public void popPushPose() {
			popPose();
			poseStack.pushPose();
		}

		/** @return the matrix this object currently holds, for a draw call to adopt. */
		public Matrix4f last() {
			return poseStack.last().pose();
		}
	}
}
