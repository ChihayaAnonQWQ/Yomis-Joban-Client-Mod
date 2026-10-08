package com.jsblock.script;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Objects;

/**
 * The utility objects JCM 2.x puts in every script's global scope: {@code Timing},
 * {@code StateTracker} and {@code CycleTracker}.
 *
 * <p>All three are documented under the scripting docs' Common APIs, and the packs use them for
 * ordinary reasons -- a clock, a "has this changed since last frame" test, a panel that alternates
 * between two views every few seconds. A script that reaches for one that is not defined dies on
 * the spot, so they travel with the port.</p>
 */
public final class ScriptTrackers {

	private ScriptTrackers() {
	}

	// ==================================================================
	// Timing
	// ==================================================================

	/** {@code Timing} -- clock readings, as the docs describe them. */
	public static final class Timing {

		private static final long START_NANOS = System.nanoTime();
		/** Per-thread, because scripts run on their own execution threads. */
		private static final ThreadLocal<Long> LAST_CALL_NANOS = new ThreadLocal<>();

		private Timing() {
		}

		/** {@code Timing.elapsed()} -- seconds the game has been running, always increasing. */
		public static double elapsed() {
			return (System.nanoTime() - START_NANOS) / 1_000_000_000.0;
		}

		/**
		 * {@code Timing.delta()} -- seconds since the previous call on this thread.
		 *
		 * <p>The docs define it as the gap between one render call and the next, which is what a
		 * script uses to advance an animation by time rather than by frame count.</p>
		 */
		public static double delta() {
			final long now = System.nanoTime();
			final Long last = LAST_CALL_NANOS.get();
			LAST_CALL_NANOS.set(now);
			return last == null ? 0.0 : (now - last) / 1_000_000_000.0;
		}

		/** {@code Timing.currentTimeMillis()} -- exactly {@code System.currentTimeMillis()}. */
		public static long currentTimeMillis() {
			return System.currentTimeMillis();
		}

		/** {@code Timing.nanoTime()} -- exactly {@code System.nanoTime()}. */
		public static long nanoTime() {
			return System.nanoTime();
		}
	}

	// ==================================================================
	// StateTracker
	// ==================================================================

	/**
	 * {@code StateTracker} -- remembers what a value was, so a script can act on the change.
	 *
	 * <p>The docs' example is the reason it exists: {@code if (distance < 300) play()} fires every
	 * frame once the condition holds, and a tracker turns that into one firing per change.</p>
	 */
	public static class StateTracker {

		private Object now;
		private Object last;
		private boolean hasLast;
		private boolean firstThisLoop;
		private long changedAtMillis = System.currentTimeMillis();

		/** {@code setState(value)} -- records a new state and whether it differs from the old one. */
		public void setState(Object value) {
			if (!Objects.equals(now, value)) {
				last = now;
				hasLast = true;
				now = value;
				firstThisLoop = true;
				changedAtMillis = System.currentTimeMillis();
			} else {
				firstThisLoop = false;
			}
		}

		/** {@code stateNow()} */
		public Object stateNow() {
			return now;
		}

		/** {@code stateLast()} -- null when there is no previous state. */
		public Object stateLast() {
			return hasLast ? last : null;
		}

		/** {@code stateNowDuration()} -- seconds the current state has lasted. */
		public double stateNowDuration() {
			return (System.currentTimeMillis() - changedAtMillis) / 1000.0;
		}

		/** {@code stateNowFirst()} -- did {@code setState} change the state just now? */
		public boolean stateNowFirst() {
			return firstThisLoop;
		}

		/** {@code changedTo(value)} */
		public boolean changedTo(Object value) {
			return firstThisLoop && Objects.equals(now, value);
		}

		/** {@code changedFromTo(oldValue, value)} */
		public boolean changedFromTo(Object oldValue, Object value) {
			return firstThisLoop && hasLast && Objects.equals(last, oldValue) && Objects.equals(now, value);
		}
	}

	// ==================================================================
	// CycleTracker
	// ==================================================================

	/**
	 * {@code CycleTracker} -- a StateTracker that moves on by itself after a set time.
	 *
	 * <p>Constructed from a JavaScript array of alternating state and duration in seconds, exactly
	 * as the docs show it: {@code new CycleTracker(["route", 5, "nextStation", 5])}. The array is
	 * read as a {@link Scriptable} rather than an {@code Object[]} because Rhino hands the script's
	 * own array through.</p>
	 */
	public static class CycleTracker extends StateTracker {

		private final Object[] states;
		private final double[] durationsSeconds;
		private long enteredAtMillis = System.currentTimeMillis();
		private int index;

		public CycleTracker(Scriptable params) {
			final int length = params == null ? 0 : (int) ScriptableObject.getProperty(params, "length");
			final int pairs = Math.max(0, length / 2);
			states = new Object[pairs];
			durationsSeconds = new double[pairs];
			for (int i = 0; i < pairs; i++) {
				states[i] = ScriptableObject.getProperty(params, i * 2);
				final Object duration = ScriptableObject.getProperty(params, i * 2 + 1);
				durationsSeconds[i] = duration instanceof Number ? ((Number) duration).doubleValue() : 0.0;
			}
			if (pairs > 0) {
				setState(states[0]);
			}
		}

		/** {@code tick()} -- advance if the current state has lasted long enough. */
		public void tick() {
			if (states.length == 0) {
				return;
			}
			final double held = (System.currentTimeMillis() - enteredAtMillis) / 1000.0;
			if (held >= durationsSeconds[index]) {
				index = (index + 1) % states.length;
				enteredAtMillis = System.currentTimeMillis();
				setState(states[index]);
			}
		}

		/** {@code stateNow()} -- the current state, as the docs type it: a String. */
		@Override
		public String stateNow() {
			final Object value = super.stateNow();
			return value == null ? "" : value.toString();
		}

		/** {@code stateLast()} -- the previous state, or null. */
		@Override
		public String stateLast() {
			final Object value = super.stateLast();
			return value == null ? null : value.toString();
		}
	}
}
