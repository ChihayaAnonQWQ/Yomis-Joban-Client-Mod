package com.jsblock.script;

import com.jsblock.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws what the PIDS scripts are doing, in the corner of the screen.
 *
 * <p>Port of JCM 2.x's {@code com.lx862.mtrscripting.mod.gui.hud.MTRScriptDebugOverlay},
 * reduced to the one script source this branch has (PIDS). JCM 2.x also lists vehicle, lift
 * and eye-candy scripts and offers a key to page between those sources; with a single source
 * there is nothing to page through, so the source selector is gone.</p>
 *
 * <p>What it answers that the log cannot: which panels are currently scripted, where they
 * are, how long each one takes, and which of them is throwing. A failing script is drawn
 * sorted to the top of its group and shows the exception message, because the failure is
 * the reason the overlay was opened.</p>
 */
public final class ScriptDebugOverlay {

	/** Above this many milliseconds a script is drawn red: at 60 fps a frame is 16.7 ms. */
	private static final double SLOW_MS = 1000.0 / 30.0;
	/** Between half a frame and a whole frame it is drawn yellow. */
	private static final double WARN_MS = 1000.0 / 60.0;

	private static final int COLOR_HEADER = 0xFFFFD479;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFAAAAAA;
	private static final int COLOR_OK = 0xFF88FF88;
	private static final int COLOR_WARN = 0xFFFFFF55;
	private static final int COLOR_BAD = 0xFFFF8888;

	/** Instances drawn before the rest are summarised as a count. */
	private static final int MAX_ROWS = 10;

	private ScriptDebugOverlay() {
	}

	public static void render(GuiGraphics graphics) {
		if (!ClientConfig.getScriptDebugMode()) {
			return;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.font == null || minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (minecraft.options != null && minecraft.options.hideGui) {
			return;
		}

		final List<ScriptEngine.Program> programs = new ArrayList<>(ScriptEngine.programs());
		if (programs.isEmpty()) {
			return;
		}

		final Vec3 eye = minecraft.player.position();
		programs.sort(Comparator
				/* Failures first: they are what the overlay is usually opened to find. */
				.comparing((ScriptEngine.Program program) -> program.getLastError() == null)
				.thenComparingDouble(program -> distanceSquared(program, eye)));

		int x = 6;
		int y = 6;
		final int lineHeight = 10;

		graphics.drawString(minecraft.font,
				"PIDS scripts: " + programs.size() + " active"
						+ "  |  restrictions " + (ScriptEngine.shutter().isEnabled() ? "on" : "OFF")
						+ (ScriptEngine.ERROR_NOTIFIER.pendingCount() > 0 ? "  |  " + ScriptEngine.ERROR_NOTIFIER.pendingCount() + " queued" : ""),
				x, y, COLOR_HEADER, true);
		y += lineHeight;

		int drawn = 0;
		for (ScriptEngine.Program program : programs) {
			if (drawn >= MAX_ROWS) {
				graphics.drawString(minecraft.font,
						"... and " + (programs.size() - drawn) + " more", x, y, COLOR_DIM, true);
				break;
			}
			drawn++;

			final BlockPos pos = program.getBlockPos();
			final String where = pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
			final String label = program.getDisplayName() + "  @" + where;

			if (program.getLastError() != null) {
				graphics.drawString(minecraft.font, label + "  FAILED", x, y, COLOR_BAD, true);
				y += lineHeight;
				graphics.drawString(minecraft.font, "    " + program.getLastError(), x, y, COLOR_BAD, true);
			} else {
				final double ms = program.getLastExecutionMs();
				graphics.drawString(minecraft.font,
						String.format("%s  %.2f ms", label, ms),
						x, y, colorFor(ms), true);
			}
			y += lineHeight;
		}
	}

	private static double distanceSquared(ScriptEngine.Program program, Vec3 eye) {
		final BlockPos pos = program.getBlockPos();
		if (pos == null) {
			return Double.MAX_VALUE;
		}
		return pos.distToCenterSqr(eye.x, eye.y, eye.z);
	}

	private static int colorFor(double milliseconds) {
		if (milliseconds > SLOW_MS) {
			return COLOR_BAD;
		}
		if (milliseconds > WARN_MS) {
			return COLOR_WARN;
		}
		return COLOR_OK;
	}
}
