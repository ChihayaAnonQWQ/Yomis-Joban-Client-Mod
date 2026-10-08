package com.jsblock.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

/**
 * {@code BackgroundWorker} and {@code Networking} -- the two Common-API globals a pack needs to do
 * anything that must not block a frame.
 *
 * <p>Both are documented, and both are load-bearing for a real pack: 琼岭's PIDS preset calls
 * {@code BackgroundWorker.submit(task)} at module level, so without it the whole script fails to
 * evaluate and the panel stays black. Inside that task it fetches a weather API and reads the reply
 * through {@code response.ok()} / {@code getData().asString()}.</p>
 *
 * <p>The HTTP work is Java's own; scripts never see a socket. Only the two things that pack uses are
 * implemented -- {@code fetch}, {@code fetchString} and {@code fetchImage}, with GET or the method
 * named in the options object -- because the rest of that page is for other script types.</p>
 */
public final class ScriptNetwork {

	private ScriptNetwork() {
	}

	// ==================================================================
	// BackgroundWorker
	// ==================================================================

	/** {@code BackgroundWorker.submit(task)} -- runs a script function off the render thread. */
	public static final class BackgroundWorker {

		/**
		 * Two threads, as JCM 2.x runs four for the whole engine; a PIDS preset rarely submits more
		 * than one task, and a runaway pack should not be able to spawn unbounded threads.
		 */
		private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
			final Thread thread = new Thread(runnable, "PIDS script worker");
			thread.setDaemon(true);
			return thread;
		});

		private BackgroundWorker() {
		}

		public static void submit(Function task) {
			if (task == null) {
				return;
			}
			final Scriptable scope = task.getParentScope();
			POOL.submit(() -> {
				final Context cx = Context.enter();
				try {
					cx.setLanguageVersion(Context.VERSION_ES6);
					cx.setOptimizationLevel(-1);
					ScriptEngine.installShutter(cx);
					task.call(cx, scope, scope, new Object[0]);
				} catch (Throwable t) {
					com.jsblock.Joban.LOGGER.warn("[Joban Client] PIDS background task failed: {}", t.toString());
				} finally {
					Context.exit();
				}
			});
		}
	}

	// ==================================================================
	// Networking
	// ==================================================================

	/** {@code Networking} -- HTTP, synchronously; the docs' examples call it inside a worker. */
	public static final class Networking {

		private static final HttpClient CLIENT = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();

		private Networking() {
		}

		/** {@code Networking.fetch(url)} -- the reply body wrapped for {@code asString()}. */
		public static NetworkResponse fetch(String url) {
			return fetch(url, null);
		}

		/** {@code Networking.fetch(url, { method: "GET" })} */
		public static NetworkResponse fetch(String url, Scriptable options) {
			final Reply reply = request(url, options);
			return new NetworkResponse(reply.code, new DataReader(reply.body));
		}

		/** {@code Networking.fetchString(url)} -- the reply body as a String. */
		public static NetworkResponse fetchString(String url) {
			return fetchString(url, null);
		}

		public static NetworkResponse fetchString(String url, Scriptable options) {
			final Reply reply = request(url, options);
			return new NetworkResponse(reply.code, new String(reply.body, StandardCharsets.UTF_8));
		}

		/** {@code Networking.fetchImage(url)} -- the reply decoded as an image, or null. */
		public static NetworkResponse fetchImage(String url) {
			return fetchImage(url, null);
		}

		public static NetworkResponse fetchImage(String url, Scriptable options) {
			final Reply reply = request(url, options);
			Object image = null;
			try {
				image = ImageIO.read(new ByteArrayInputStream(reply.body));
			} catch (Exception e) {
				com.jsblock.Joban.LOGGER.warn("[Joban Client] PIDS script could not decode an image from {}: {}",
						url, e.toString());
			}
			return new NetworkResponse(reply.code, image);
		}

		/** One request, with the pieces both callers need. */
		private static Reply request(String url, Scriptable options) {
			try {
				final String method = optionString(options, "method", "GET").toUpperCase();
				final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
						.timeout(Duration.ofSeconds(20))
						.header("User-Agent", "JobanClientMod-PIDS");
				if ("POST".equals(method)) {
					builder.POST(HttpRequest.BodyPublishers.ofString(optionString(options, "body", "")));
				} else {
					builder.GET();
				}
				final HttpResponse<byte[]> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
				return new Reply(response.statusCode(), response.body() == null ? new byte[0] : response.body());
			} catch (Exception e) {
				com.jsblock.Joban.LOGGER.warn("[Joban Client] PIDS script request to {} failed: {}", url, e.toString());
				return new Reply(0, new byte[0]);
			}
		}

		private static String optionString(Scriptable options, String key, String fallback) {
			if (options == null) {
				return fallback;
			}
			final Object value = ScriptableObject.getProperty(options, key);
			if (value == null || value == Scriptable.NOT_FOUND || value == Context.getUndefinedValue()) {
				return fallback;
			}
			return Context.toString(value);
		}

		/** The raw outcome of one request. */
		private static final class Reply {
			private final int code;
			private final byte[] body;

			private Reply(int code, byte[] body) {
				this.code = code;
				this.body = body;
			}
		}
	}

	/** {@code NetworkResponse} -- {@code ok()}, {@code getResponseCode()} and {@code getData()}. */
	public static final class NetworkResponse {

		private final int code;
		private final Object data;

		NetworkResponse(int code, Object data) {
			this.code = code;
			this.data = data;
		}

		public boolean ok() {
			return code >= 200 && code < 300;
		}

		public int getResponseCode() {
			return code;
		}

		public Object getData() {
			return data;
		}
	}

	/** {@code DataReader} -- the reply body, read as text or bytes. */
	public static final class DataReader {

		private final byte[] bytes;

		DataReader(byte[] bytes) {
			this.bytes = bytes;
		}

		public String asString() {
			return new String(bytes, StandardCharsets.UTF_8);
		}

		public int size() {
			return bytes.length;
		}
	}
}
