package com.jsblock.script;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * {@code SoundManager} -- what {@code ctx.getSoundManager()} hands a script.
 *
 * <p>Documented under the scripting docs' Common APIs, and used in the wild: the 琼岭 PIDS pack calls
 * {@code ctx.getSoundManager()} and then {@code sound.playLocalSound(...)} for its announcements, so
 * without it that whole pack stops at the first render.</p>
 *
 * <p>All four documented methods are implemented, including {@code play} and {@code stop} for a
 * {@link TickableSoundInstance} -- the 琼岭 pack uses one to play a video's audio track while it
 * swaps frames.</p>
 */
public final class ScriptSound {

	private ScriptSound() {
	}

	/** One manager per script call, as the docs describe. */
	public static final class SoundManager {

		/**
		 * Plays a sound for this client with no position: constant volume, unaffected by distance.
		 *
		 * <p>{@code soundCategory} defaults to MASTER, as documented.</p>
		 */
		public void playLocalSound(Object id, double volume, double pitch, String soundCategory) {
			play(id, volume, pitch, soundCategory, 0.0, 0.0, 0.0, true);
		}

		/**
		 * {@code SoundManager.play(instance)} -- starts a script-steerable sound.
		 *
		 * <p>One instance plays once; the docs say to stop it before playing it again, and
		 * Minecraft's own sound engine enforces the same thing.</p>
		 */
		public void play(Object soundInstance) {
			if (!(soundInstance instanceof TickableSoundInstance)) {
				return;
			}
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null) {
				((TickableSoundInstance) soundInstance).attach(minecraft);
			}
		}

		/** {@code SoundManager.stop(instance)} -- stops it, leaving the script free to play it again. */
		public void stop(Object soundInstance) {
			if (soundInstance instanceof TickableSoundInstance) {
				((TickableSoundInstance) soundInstance).detach();
			}
		}

		/** Plays a sound at a position relative to where the script's block is. */
		public void playSound(Object id, Object pos, double volume, double pitch, String soundCategory) {
			double x = 0;
			double y = 0;
			double z = 0;
			if (pos instanceof ScriptMath.Vector3f) {
				final ScriptMath.Vector3f vector = (ScriptMath.Vector3f) pos;
				x = vector.x();
				y = vector.y();
				z = vector.z();
			}
			play(id, volume, pitch, soundCategory, x, y, z, false);
		}

		/**
		 * The shared body of both methods.
		 *
		 * <p>A sound identifier that no pack provides simply plays nothing: Minecraft's sound engine
		 * ignores an event with no sounds bound to it, which is the right outcome for a preset whose
		 * pack is not installed.</p>
		 */
		private void play(Object id, double volume, double pitch, String soundCategory,
						  double x, double y, double z, boolean relative) {
			final Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || id == null) {
				return;
			}
			final ResourceLocation location = toLocation(id);
			if (location == null) {
				return;
			}
			final SoundSource source = toCategory(soundCategory);
			try {
				/* createVariableRangeEvent is how a sound that is not in sounds.json as a fixed-range
				   entry still plays; pack sounds are declared exactly this way. */
				SoundEvent.createVariableRangeEvent(location);
				final SimpleSoundInstance instance = new SimpleSoundInstance(
						location, source, (float) volume, (float) pitch,
						SoundInstance.createUnseededRandom(), false, 0,
						relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR,
						x, y, z, relative);
				minecraft.getSoundManager().play(instance);
			} catch (Exception e) {
				com.jsblock.Joban.LOGGER.warn("[Joban Client] PIDS script could not play sound {}: {}",
						location, e.toString());
			}
		}

		/** Accepts the identifier a script hands over: our ResourceLocation, or its string form. */
		private static ResourceLocation toLocation(Object id) {
			if (id instanceof ResourceLocation) {
				return (ResourceLocation) id;
			}
			final String text = String.valueOf(id);
			if (text.isEmpty()) {
				return null;
			}
			try {
				return text.indexOf(':') < 0
						? new ResourceLocation("minecraft", text)
						: new ResourceLocation(text);
			} catch (Exception e) {
				return null;
			}
		}

		/** The documented category names, with MASTER as the fallback. */
		private static SoundSource toCategory(String name) {
			if (name != null) {
				for (SoundSource source : SoundSource.values()) {
					if (source.getName().equalsIgnoreCase(name)) {
						return source;
					}
				}
			}
			return SoundSource.MASTER;
		}
	}

	/**
	 * {@code TickableSoundInstance} -- a sound a script keeps hold of and adjusts while it plays.
	 *
	 * <p>Minecraft's own {@code AbstractTickableSoundInstance} does the playing and the ticking; this
	 * is the handle a script holds, re-reading its values into the live instance every tick so that
	 * {@code setSoundVolume} and {@code setPos} take effect mid-playback, as the docs describe.</p>
	 */
	public static final class TickableSoundInstance {

		private final ResourceLocation id;
		private final SoundSource category;
		private float volume = 1F;
		private float pitch = 1F;
		private boolean loopable;
		private int loopDelayTicks;
		private boolean relative = true;
		private double x;
		private double y;
		private double z;

		private Live live;

		private TickableSoundInstance(ResourceLocation id, SoundSource category) {
			this.id = id;
			this.category = category;
		}

		/** {@code TickableSoundInstance.create(id, soundCategory)} */
		public static TickableSoundInstance create(Object id, String soundCategory) {
			final ResourceLocation location = SoundManager.toLocation(id);
			return location == null ? null : new TickableSoundInstance(location, SoundManager.toCategory(soundCategory));
		}

		public TickableSoundInstance setSoundVolume(double volume) {
			this.volume = (float) volume;
			return this;
		}

		public TickableSoundInstance setSoundPitch(double pitch) {
			this.pitch = (float) pitch;
			return this;
		}

		public TickableSoundInstance setLoopable(boolean loopable) {
			this.loopable = loopable;
			return this;
		}

		public TickableSoundInstance setLoopDelay(double delayTicks) {
			this.loopDelayTicks = (int) delayTicks;
			return this;
		}

		public TickableSoundInstance setRelative(boolean relative) {
			this.relative = relative;
			return this;
		}

		public TickableSoundInstance setPos(Object pos) {
			if (pos instanceof ScriptMath.Vector3f) {
				final ScriptMath.Vector3f vector = (ScriptMath.Vector3f) pos;
				this.x = vector.x();
				this.y = vector.y();
				this.z = vector.z();
			}
			return this;
		}

		public boolean isPlaying() {
			return live != null && !live.isStopped();
		}

		/** Hands the instance to Minecraft's sound engine and keeps the handle. */
		private void attach(Minecraft minecraft) {
			detach();
			live = new Live(this);
			minecraft.getSoundManager().play(live);
		}

		private void detach() {
			if (live != null) {
				live.stopNow();
				live = null;
			}
		}

		/** The playing half: Minecraft's tickable sound, fed from this handle every tick. */
		private static final class Live extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {

			private final TickableSoundInstance owner;

			private Live(TickableSoundInstance owner) {
				super(SoundEvent.createVariableRangeEvent(owner.id), owner.category,
						SoundInstance.createUnseededRandom());
				this.owner = owner;
				update();
			}

			@Override
			public void tick() {
				update();
			}

			private void update() {
				this.volume = owner.volume;
				this.pitch = owner.pitch;
				this.looping = owner.loopable;
				this.delay = owner.loopDelayTicks;
				this.relative = owner.relative;
				this.attenuation = owner.relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
				this.x = owner.x;
				this.y = owner.y;
				this.z = owner.z;
			}

			private void stopNow() {
				stop();
			}
		}
	}

}
