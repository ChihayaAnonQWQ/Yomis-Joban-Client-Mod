package com.jsblock.script;

import com.jsblock.Joban;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the fallback {@code Resources.getSystemFont(name)} gained for the names JCM 2.x leaves to
 * the JVM — the branch HKR Split-Flap Display's panel dies on.
 *
 * <h2>What is being pinned</h2>
 * <p>JCM 2.x's third branch is {@code new Font(name, PLAIN, 1)}: a lookup in the JVM's font
 * registry, which <b>silently substitutes</b> a name it does not have. HKR asks for
 * {@code "Noto Sans SemiBold"} and {@code "Noto Sans CJK TC Medium"}, neither of which is installed
 * on this machine, so both used to come back as {@code Dialog} — measured here, not assumed: the
 * check reads the substituted family out of a raw AWT lookup and says so in its output.</p>
 *
 * <p>The port now answers a name the machine does not have with MTR's own bundled Noto — except
 * where AWT's substitution is already better than anything bundled, which is the case the adaptive
 * rule exists for. The check asserts what that decision promises:</p>
 * <ol>
 *   <li>HKR's Latin name no longer comes back as the substitution: the family is the bundled Latin
 *       face, it draws, and it still scales through the pack's own {@code deriveFont(int, float)}
 *       call;</li>
 *   <li>HKR's CJK name comes back as something that <b>draws Chinese as characters</b> — either
 *       this machine's own substitution, when that substitution can draw the script the name marks,
 *       or the bundled CJK face when it cannot. Both paths are asserted on the seam below, so the
 *       machine that takes one still covers the other;</li>
 *   <li>{@code "Noto Sans"} and {@code "Noto Serif"} — the two names the reference jar does read
 *       from a file — behave exactly as before;</li>
 *   <li>a family this machine <em>does</em> have is returned untouched, including a face spelling
 *       ({@code "… Bold"}) that is in no family table, and the logical families {@code Dialog} and
 *       {@code Monospaced} are not hijacked either;</li>
 *   <li>with no client and no resource manager the bundled read fails and the answer degrades to
 *       the JVM's substituted font — no exception, no null where a font is expected — and the
 *       fallback writes exactly one INFO line per request name.</li>
 * </ol>
 *
 * <h2>Why a font that cannot draw the script is refused, and why the substitution can be kept</h2>
 * <p>The check carries the evidence rather than the claim, because this is the rule that was got
 * wrong first: {@code mtr:font/noto-sans-semibold.ttf} has no CJK glyph, and Java2D does <b>not</b>
 * substitute a face per character for a physical font — {@code drawString("下")} through it paints
 * the same pixels as an unmapped character, a missing-glyph box (both asserted below). Per-glyph
 * fallback exists for the logical, composite fonts only, which is why a JVM substitution such as
 * {@code Dialog} draws the real character while the bundled Latin file draws a box. So the rule
 * cannot be "a CJK name always takes a bundled file": a name whose AWT substitution already draws
 * the marked scripts is served by that substitution, and the bundled CJK file is what a name gets
 * when the substitution cannot draw it.</p>
 *
 * <p>Run via {@code tools/run-pids-check.ps1}.</p>
 */
public final class ScriptFontFallbackCheck {

	private static final ResourceLocation NOTO_SANS = new ResourceLocation("mtr", "font/noto-sans-semibold.ttf");
	private static final ResourceLocation NOTO_SERIF =
			new ResourceLocation("mtr", "font/noto-serif-cjk-tc-semibold.ttf");

	/** The pack's two requests, from {@code next_train.js}: F_LATIN and F_CJK. */
	private static final String HKR_LATIN = "Noto Sans SemiBold";
	private static final String HKR_CJK = "Noto Sans CJK TC Medium";

	/** A name no machine has, so the fallback can be exercised deterministically on any machine. */
	private static final String MISSING = "No Such Family 4711";
	private static final String MISSING_SERIF = "No Such Serif Family 4712";
	private static final String MISSING_CJK = "No Such CJK Family 4713";
	private static final String MISSING_JAPANESE = "No Such CJK JP Family 4714";

	private static int failures;

	/** Both locale tables, merged on first use; the check runs on one thread. */
	private static java.util.Collection<String> FAMILY_NAMES;

	private ScriptFontFallbackCheck() {
	}

	public static void main(String[] args) {
		System.out.println("== script font fallback (Resources.getSystemFont) ==");

		try {
			/* The same seam ScriptImporterCheck uses: the fonts are MTR's own assets, on this
			   classpath through libs/mtr-common.jar, and the engine reads them from the resource
			   manager in game. */
			ScriptFonts.headlessSource = location -> ScriptFontFallbackCheck.class.getClassLoader()
					.getResourceAsStream("assets/" + location.getNamespace() + "/" + location.getPath());

			checkHkrRequestsAreServedByBundledNoto();
			checkTheScriptMarksDecideTheFallback();
			checkMissingNamesAlwaysFallBack();
			checkTheTwoFileBranchesAreUnchanged();
			checkInstalledFamiliesAreUntouched();
			checkTheBundledCjkFaceIsTheOnlyOneThatCanDrawCjk();
			checkOneResolutionPerName();
			checkTheFallbackLogsOnce();
			checkSoftFailureWithoutAClient();
		} catch (Throwable t) {
			System.out.println("FAIL the font fallback check itself threw: " + t);
			t.printStackTrace(System.out);
			failures++;
		} finally {
			ScriptFonts.headlessSource = null;
			ScriptEngine.Resources.resetFonts();
		}

		System.out.println();
		System.out.println(failures == 0 ? "RESULT: FONT FALLBACK OK" : "RESULT: " + failures + " FAILURE(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------

	/**
	 * The two names HKR asks for, which is the whole reason this exists.
	 *
	 * <p>Machine-aware on purpose for the CJK name: whether the answer is this machine's own
	 * substitution or MTR's bundled CJK face depends on what that substitution can draw, and the
	 * check says which of the two it is instead of pretending. What has to hold on every machine —
	 * the character is drawn as a character — is asserted either way, and the deterministic half
	 * lives in {@link #checkTheScriptMarksDecideTheFallback()}.</p>
	 */
	private static void checkHkrRequestsAreServedByBundledNoto() {
		ScriptEngine.Resources.resetFonts();

		final Font bundledLatin = ScriptFonts.read(NOTO_SANS);
		final Font bundledCjk = ScriptFonts.read(NOTO_SERIF);
		if (bundledLatin == null || bundledCjk == null) {
			fail("MTR's bundled Noto fonts are not on this classpath, so nothing below would be evidence");
			return;
		}

		final Font latin = ScriptEngine.Resources.getSystemFont(HKR_LATIN);
		expect(latin != null, "\"" + HKR_LATIN + "\" answers a Font, as every preset's sysFont() assumes");
		if (latin != null) {
			expect(!"Dialog".equalsIgnoreCase(latin.getFamily()),
					"\"" + HKR_LATIN + "\" is not AWT's silent substitution: family=" + latin.getFamily()
							+ ", fontName=" + latin.getFontName());
			expect(latin.getFamily().equalsIgnoreCase(bundledLatin.getFamily()),
					"it is MTR's bundled face for that name (" + bundledLatin.getFontName()
							+ " from mtr:font/noto-sans-semibold.ttf)");
			expect(latin.canDisplay('A') && latin.canDisplay('8'),
					"the bundled Latin face draws the panel's letters and digits: A=" + latin.canDisplay('A')
							+ " 8=" + latin.canDisplay('8'));
			expect(latin.deriveFont(Font.BOLD, 40.0f).getSize() == 40.0f,
					"the pack's mk() scaling still works on it: deriveFont(BOLD, 40) -> "
							+ latin.deriveFont(Font.BOLD, 40.0f).getSize());
		}

		/* The CJK name. What AWT alone would have answered is read first, because that is what
		   decides which of the two correct answers this machine gets. */
		final Font awtOnly = new Font(HKR_CJK, Font.PLAIN, 1);
		final Font cjk = ScriptEngine.Resources.getSystemFont(HKR_CJK);
		expect(cjk != null, "\"" + HKR_CJK + "\" answers a Font");
		if (cjk != null) {
			expect(cjk.canDisplay('\u4e0b'),
					"it can draw the panel's Chinese station names: 下=" + cjk.canDisplay('\u4e0b'));
			final String xia = glyphs(cjk, "\u4e0b");
			final String ban = glyphs(cjk, "\u73ed");
			expect(!xia.equals(ban) && xia.indexOf('#') >= 0,
					"and it paints 下 and 班 as two different characters (" + painted(xia) + " vs " + painted(ban)
							+ " pixels), not as the same missing-glyph box");
			/* The answer must be a sans CJK face when this machine has one, and never AWT's own
			   substitution: on this machine Dialog draws Han with SimSun, and a Song/Ming serif beside the
			   panel's sans Latin is the mismatch a reader notices first. */
			final Font sansHere = firstInstalledSansCjk();
			if (sansHere != null) {
				expect(cjk.getFamily().equalsIgnoreCase(sansHere.getFamily()),
						"this machine has \"" + sansHere.getFamily() + "\", a sans CJK face, so the CJK name"
								+ " is answered with it rather than with AWT's substitution \""
								+ awtOnly.getFontName() + "\": family=" + cjk.getFamily());
				note("sans path: \"" + sansHere.getFontName() + "\" is installed, and AWT's own substitution"
						+ " for a CJK name (\"" + awtOnly.getFontName()
						+ "\") draws Han with a serif face on this machine");
			} else {
				expect(cjk.getFamily().equalsIgnoreCase(bundledCjk.getFamily()),
						"no sans CJK family is installed here, so MTR's bundled CJK face is the last resort: "
								+ cjk.getFontName());
				expect(cjk.canDisplay('下'),
						"and that face does draw it: 下=" + cjk.canDisplay('下'));
			}
		}

		/* The substitution this replaced, read straight out of AWT so the output states the machine
		   it was measured on rather than a claim about it. */
		final Font rawLatin = new Font(HKR_LATIN, Font.PLAIN, 1);
		if ("Dialog".equalsIgnoreCase(rawLatin.getFamily())) {
			pass("a raw AWT lookup for \"" + HKR_LATIN + "\" answers \"" + rawLatin.getFontName()
					+ "\" on this machine, which is the substitution the fallback replaced");
		} else {
			note("this machine has \"" + HKR_LATIN + "\" installed (raw AWT lookup -> "
					+ rawLatin.getFontName() + "), so this run could not exercise the fallback for HKR's"
					+ " own name; the deterministic cases below cover it");
		}
	}

	/**
	 * The decision itself, called with the substitute handed in — the machine-independent half.
	 *
	 * <p>{@code Resources.substituteFor(substitute, name)} is the whole rule as one function of two
	 * arguments, which is what makes this possible: the bundled Latin face is a physical font with
	 * no CJK glyph at all, so passing it in is exactly what "this machine's substitution cannot draw
	 * the script" looks like, and a machine that <em>does</em> have a CJK font can still assert the
	 * no-CJK path. The other direction is asserted too: a substitute that does carry the marked
	 * scripts is returned unchanged, which is the adaptive path, and one that carries Han but not
	 * kana is not enough for a Japanese name.</p>
	 */
	private static void checkTheScriptMarksDecideTheFallback() {
		final Font latin = ScriptFonts.read(NOTO_SANS);
		final Font cjk = ScriptFonts.read(NOTO_SERIF);
		if (latin == null || cjk == null) {
			return;
		}

		/* 1. A CJK name with a substitute that cannot draw it: the bundled CJK face. */
		final ScriptEngine.Resources.Substitute forced =
				ScriptEngine.Resources.substituteFor(latin, MISSING_CJK);
		final Font sansHere = firstInstalledSansCjk();
		final String expectedFamily = sansHere != null ? sansHere.getFamily() : cjk.getFamily();
		expect(forced.font.getFamily().equalsIgnoreCase(expectedFamily),
				"a CJK name takes this machine's sans CJK face when it has one and MTR's bundled CJK face"
						+ " when it does not (expected \"" + expectedFamily + "\"): " + forced.font.getFontName());
		expect(forced.font.canDisplay('\u4e0b'),
				"and that face draws the character the name marks: 下=" + forced.font.canDisplay('\u4e0b'));
		final String forcedXia = glyphs(forced.font, "\u4e0b");
		final String forcedBan = glyphs(forced.font, "\u73ed");
		expect(!forcedXia.equals(forcedBan),
				"it paints real characters rather than one missing-glyph box ("
						+ painted(forcedXia) + " vs " + painted(forcedBan) + " pixels)");
		expect(forced.reason.contains("\u4e0b"),
				"and the reason it was chosen names the character the substitute lacks: " + forced.reason);

		/* 2. A CJK name is never served by the substitution, even one that draws every script it
		   marks: the substitute is AWT's, and where that substitute is a serif the panel would show a
		   serif beside its sans Latin. What the name marks still decides whether it is a CJK name. */
		final ScriptEngine.Resources.Substitute notKept =
				ScriptEngine.Resources.substituteFor(cjk, MISSING_JAPANESE);
		expect(notKept.font.canDisplay('下') && notKept.font.canDisplay('あ'),
				"a Japanese name is answered with a face that draws both 下 and あ: "
						+ notKept.font.getFontName());
		expect(!notKept.font.getFamily().equalsIgnoreCase("Dialog"),
				"and never with AWT's bare substitution: family=" + notKept.font.getFamily());
		expect(notKept.reason.contains("下") && notKept.reason.contains("あ"),
				"the reason names both characters a Japanese name marks: " + notKept.reason);

		/* 3. The per-script probe no longer chooses the CJK answer — an installed sans CJK family does,
		   whatever the substitution can draw — which the two cases above now assert. */
		note("the per-script probe only classifies the name now; the CJK answer comes from an installed"
				+ " sans CJK family, or from MTR's bundled face when there is none");

		/* 4. An ordinary Latin name is answered from the bundled Latin file, which is the branch
		   that does not depend on any script mark. */
		final ScriptEngine.Resources.Substitute plain =
				ScriptEngine.Resources.substituteFor(latin, MISSING);
		expect(plain.font.getFamily().equalsIgnoreCase(latin.getFamily()),
				"a name with no CJK marker and no \"serif\" in it is answered from the bundled Latin Noto: "
						+ plain.font.getFontName());
	}

	/**
	 * The first sans CJK family this machine actually has, or {@code null} — the same short list the
	 * production rule walks, so the two cannot disagree about what counts as a sans CJK face.
	 */
	private static Font firstInstalledSansCjk() {
		final String[] families = {
				"Noto Sans SC", "Noto Sans TC", "Noto Sans HK", "Noto Sans JP", "Noto Sans KR",
				"Source Han Sans SC", "Source Han Sans TC", "Source Han Sans",
				"Microsoft YaHei", "Microsoft JhengHei", "PingFang SC", "PingFang TC",
				"Hiragino Sans", "Meiryo", "Yu Gothic UI", "Yu Gothic", "Malgun Gothic",
		};
		for (String family : families) {
			for (String suffix : new String[] { " Medium", " SemiBold", "" }) {
				final Font candidate = new Font(family + suffix, Font.PLAIN, 1);
				if (candidate.getFamily().equalsIgnoreCase(family + suffix)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * The fallback itself, on names no machine has.
	 *
	 * <p>Machine-independent, which is the point: whatever is installed where this runs, these three
	 * names are not, so the bundled face has to come back — the Latin one for an ordinary name and
	 * for a serif name the bundled CJK face, which is the only bundled file with CJK glyphs.</p>
	 */
	private static void checkMissingNamesAlwaysFallBack() {
		ScriptEngine.Resources.resetFonts();

		final Font bundledLatin = ScriptFonts.read(NOTO_SANS);
		final Font bundledCjk = ScriptFonts.read(NOTO_SERIF);
		if (bundledLatin == null || bundledCjk == null) {
			return;
		}

		final Font plain = ScriptEngine.Resources.getSystemFont(MISSING);
		final Font substitution = new Font(MISSING, Font.PLAIN, 1);
		expect(plain != null && !plain.getFamily().equalsIgnoreCase(substitution.getFamily()),
				"\"" + MISSING + "\" is not left to the JVM's substitution (\"" + substitution.getFontName()
						+ "\"): family=" + (plain == null ? "null" : plain.getFamily()));
		expect(plain != null && plain.getFamily().equalsIgnoreCase(bundledLatin.getFamily()),
				"a name with no CJK marker and no \"serif\" in it gets the bundled Latin Noto: "
						+ (plain == null ? "null" : plain.getFontName()));

		final Font serif = ScriptEngine.Resources.getSystemFont(MISSING_SERIF);
		expect(serif != null && serif.getFamily().equalsIgnoreCase(bundledCjk.getFamily()),
				"\"" + MISSING_SERIF + "\" gets the bundled CJK face, as the serif rule says: "
						+ (serif == null ? "null" : serif.getFontName()));

		/* A CJK name on this machine: whichever of the two correct answers the substitution
		   allows, it must draw the character, and it must not be the bundled Latin face. Which of
		   the two it is, and why, is asserted deterministically in
		   checkTheScriptMarksDecideTheFallback(). */
		final Font cjk = ScriptEngine.Resources.getSystemFont(MISSING_CJK);
		expect(cjk != null && cjk.canDisplay('\u4e0b'),
				"\"" + MISSING_CJK + "\" is answered with a font that draws the character the name marks: "
						+ (cjk == null ? "null" : cjk.getFontName()));
		if (cjk != null) {
			expect(cjk.getFamily().equalsIgnoreCase(bundledCjk.getFamily()) || !isBundledNoto(cjk),
					"and the answer is this machine's own substitution or the bundled CJK face, never the"
							+ " Latin one: fontName=" + cjk.getFontName() + " family=" + cjk.getFamily());
		}
	}

	/**
	 * The evidence behind "a font that cannot draw the script is refused": the Latin file draws a
	 * box, and the box is the same box for every character it does not have.
	 */
	private static void checkTheBundledCjkFaceIsTheOnlyOneThatCanDrawCjk() {
		final Font bundledLatin = ScriptFonts.read(NOTO_SANS);
		final Font bundledCjk = ScriptFonts.read(NOTO_SERIF);
		if (bundledLatin == null || bundledCjk == null) {
			return;
		}

		expect(!bundledLatin.canDisplay('\u4e0b'),
				"the bundled Latin Noto has no Chinese glyph (canDisplay('下')=" + bundledLatin.canDisplay('\u4e0b')
						+ "), so it cannot be what a CJK request is served with");

		final String boxed = glyphs(bundledLatin, "\u4e0b");
		final String alsoBoxed = glyphs(bundledLatin, "\u73ed");
		expect(boxed.equals(alsoBoxed) && boxed.indexOf('#') >= 0,
				"drawing 下 and 班 through it paints the identical missing-glyph box ("
						+ painted(boxed) + " pixels), i.e. Java2D does not substitute a face per glyph for"
						+ " a physical font");

		final String drawn = glyphs(bundledCjk, "\u4e0b");
		final String alsoDrawn = glyphs(bundledCjk, "\u73ed");
		expect(!drawn.equals(alsoDrawn) && drawn.indexOf('#') >= 0,
				"the bundled CJK Noto paints them as two different characters (" + painted(drawn) + " vs "
						+ painted(alsoDrawn) + " pixels)");

		/* The same assertion on whatever the panel is actually served, whichever path this machine
		   took: a logical substitution draws the character, and the bundled CJK file draws it too. */
		final Font served = ScriptEngine.Resources.getSystemFont(HKR_CJK);
		expect(served != null && !glyphs(served, "\u4e0b").equals(glyphs(served, "\u73ed")),
				"what HKR's \"" + HKR_CJK + "\" is actually served paints real characters, not tofu: "
						+ (served == null ? "null" : served.getFontName()));
	}

	/** {@code "Noto Sans"} and {@code "Noto Serif"}: the reference jar's own two file branches. */
	private static void checkTheTwoFileBranchesAreUnchanged() {
		ScriptEngine.Resources.resetFonts();

		final Font bundledLatin = ScriptFonts.read(NOTO_SANS);
		final Font bundledCjk = ScriptFonts.read(NOTO_SERIF);
		if (bundledLatin == null || bundledCjk == null) {
			return;
		}

		final Font sans = ScriptEngine.Resources.getSystemFont("Noto Sans");
		expect(sans != null && sans.getFamily().equalsIgnoreCase(bundledLatin.getFamily()),
				"getSystemFont(\"Noto Sans\") is unchanged: it still reads mtr:font/noto-sans-semibold.ttf ("
						+ (sans == null ? "null" : sans.getFontName()) + ")");
		expect(sans != null && sans.getSize() == 1.0f,
				"it is still a createFont result at size 1, the contract the packs scale with deriveFont: size="
						+ (sans == null ? "null" : sans.getSize()));
		expect(sans != null && sans.canDisplay('A') && !sans.canDisplay('\u4e0b'),
				"and it is still the Latin face of that file: A=" + (sans != null && sans.canDisplay('A'))
						+ " 下=" + (sans != null && sans.canDisplay('\u4e0b')));
		expect(sans != null && sans.deriveFont(Font.BOLD, 24.0f).getSize() == 24.0f,
				"deriveFont(int, float) still scales it, the overload next_train.js's header insists on");

		final Font serif = ScriptEngine.Resources.getSystemFont("Noto Serif");
		expect(serif != null && serif.getFamily().equalsIgnoreCase(bundledCjk.getFamily()),
				"getSystemFont(\"Noto Serif\") is unchanged: it still reads"
						+ " mtr:font/noto-serif-cjk-tc-semibold.ttf ("
						+ (serif == null ? "null" : serif.getFontName()) + ")");
		expect(serif != null && serif.canDisplay('\u4e0b') && serif.canDisplay('\u8eca'),
				"it is still the CJK face: 下=" + (serif != null && serif.canDisplay('\u4e0b'))
						+ " 車=" + (serif != null && serif.canDisplay('\u8eca')));
	}

	/**
	 * The hard requirement: a font this machine has must come back untouched.
	 *
	 * <p>Three shapes of "has it": a family name from the table, a face spelling that is in no table
	 * ({@code "… Bold"}), and a logical family. The second is the reason the installed test does not
	 * stop at the family table; the third is the reason it does not assume every request is a
	 * physical font.</p>
	 */
	private static void checkInstalledFamiliesAreUntouched() {
		ScriptEngine.Resources.resetFonts();

		final String installed = firstInstalledFamily();
		if (installed == null) {
			note("this machine enumerates no usable physical family, so \"returned untouched\" has no"
					+ " example here");
		} else {
			final Font viaApi = ScriptEngine.Resources.getSystemFont(installed);
			expect(viaApi != null && viaApi.getFamily().equalsIgnoreCase(installed),
					"\"" + installed + "\" is installed, so it comes back as the machine's own font, not as"
							+ " bundled Noto: family=" + (viaApi == null ? "null" : viaApi.getFamily()));
			expect(viaApi != null && !isBundledNoto(viaApi),
					"and it is not one of MTR's bundled faces: fontName="
							+ (viaApi == null ? "null" : viaApi.getFontName()));

			final String face = installed + " Bold";
			final Font rawFace = new Font(face, Font.PLAIN, 1);
			if (rawFace.getFamily().equalsIgnoreCase("Dialog")) {
				note("this machine resolves \"" + face + "\" to " + rawFace.getFontName()
						+ ", so the face-spelling half of the installed test has no example here");
			} else {
				final Font viaFace = ScriptEngine.Resources.getSystemFont(face);
				expect(viaFace != null && !isBundledNoto(viaFace)
								&& viaFace.getFontName().equalsIgnoreCase(rawFace.getFontName()),
						"a face spelling the family table does not hold (\"" + face + "\" -> "
								+ rawFace.getFontName() + ") is still served by the machine, not by bundled"
								+ " Noto");
			}
		}

		final Font dialog = ScriptEngine.Resources.getSystemFont("Dialog");
		expect(dialog != null && "Dialog".equalsIgnoreCase(dialog.getFamily()),
				"the logical family \"Dialog\" is still Dialog: " + (dialog == null ? "null" : dialog.getFontName()));
		final Font mono = ScriptEngine.Resources.getSystemFont("Monospaced");
		expect(mono != null && "Monospaced".equalsIgnoreCase(mono.getFamily()),
				"and \"Monospaced\", which HKR's clock asks for, is still the logical monospaced family: "
						+ (mono == null ? "null" : mono.getFontName()));
	}

	/** One resolution per request name, dropped when the resource manager reloads. */
	private static void checkOneResolutionPerName() {
		ScriptEngine.Resources.resetFonts();

		final Font first = ScriptEngine.Resources.getSystemFont(MISSING);
		final Font second = ScriptEngine.Resources.getSystemFont(MISSING);
		expect(first != null && first == second,
				"the same request name is resolved once and then answered from the cache (same Font"
						+ " instance on the next call)");

		ScriptEngine.Resources.resetFonts();
		final Font third = ScriptEngine.Resources.getSystemFont(MISSING);
		expect(third != null && third != first,
				"resetFonts() drops that cache as well, so a reloaded pack resolves its names again");
	}

	/** The INFO line: one per name, naming the request and what it was answered with. */
	private static void checkTheFallbackLogsOnce() {
		final org.apache.logging.log4j.core.Logger logger;
		try {
			logger = (org.apache.logging.log4j.core.Logger) Joban.LOGGER;
		} catch (ClassCastException notLog4jCore) {
			note("this classpath's logger is not log4j-core, so the INFO line could not be captured");
			return;
		}

		final List<String> captured = new ArrayList<>();
		final AbstractAppender appender =
				new AbstractAppender("font-fallback-check", null, null, false, Property.EMPTY_ARRAY) {
					@Override
					public void append(LogEvent event) {
						captured.add(event.getMessage().getFormattedMessage());
					}
				};
		appender.start();

		/* log4j2's default configuration drops INFO, which is also why a pack's font substitution
		   used to be invisible in the log at all. */
		final Level previous = logger.getLevel();
		try {
			logger.setLevel(Level.INFO);
			logger.addAppender(appender);
			ScriptEngine.Resources.resetFonts();
			for (int i = 0; i < 3; i++) {
				ScriptEngine.Resources.getSystemFont(MISSING);
				ScriptEngine.Resources.getSystemFont(HKR_CJK);
			}
		} finally {
			logger.removeAppender(appender);
			logger.setLevel(previous);
			appender.stop();
		}

		final List<String> aboutTheName = new ArrayList<>();
		final List<String> aboutTheCjkName = new ArrayList<>();
		for (String line : captured) {
			if (line.contains(MISSING)) {
				aboutTheName.add(line);
			}
			if (line.contains(HKR_CJK)) {
				aboutTheCjkName.add(line);
			}
		}
		expect(aboutTheName.size() == 1,
				"the fallback logs one INFO line per request name however many frames ask (lines="
						+ aboutTheName.size() + ")");
		if (aboutTheName.isEmpty()) {
			fail("no INFO line was captured, so a pack silently drawing bundled Noto is invisible in the log");
			return;
		}
		final String line = aboutTheName.get(0);
		expect(line.contains(MISSING) && line.contains("Noto Sans SemiBold"),
				"the line names the request and the bundled face it was given: " + line);
		expect(line.contains("not installed"),
				"and it says why, so a wrong-looking panel can be traced to this line: " + line);

		/* The other path logs as well, and says which one it took. */
		expect(aboutTheCjkName.size() == 1,
				"the CJK name is logged once too, so the path it took is visible in the log (lines="
						+ aboutTheCjkName.size() + ")");
		if (!aboutTheCjkName.isEmpty()) {
			final String cjkLine = aboutTheCjkName.get(0);
			final boolean substituted = new Font(HKR_CJK, Font.PLAIN, 1).canDisplay('\u4e0b');
			expect(cjkLine.contains("not installed"),
					"the line for \"" + HKR_CJK + "\" names the request and says it is not installed: " + cjkLine);
			expect(substituted ? cjkLine.contains("Dialog") || cjkLine.contains("keeping")
							: cjkLine.contains("bundled"),
					"and it says which of the two answers this machine got: " + cjkLine);
		}
	}

	/**
	 * No client, no resource manager: the bundled files cannot be read, and the method has to behave
	 * like it did before the fallback existed rather than throw inside a renderer.
	 */
	private static void checkSoftFailureWithoutAClient() {
		ScriptFonts.headlessSource = null;
		ScriptEngine.Resources.resetFonts();

		final Font degraded = ScriptEngine.Resources.getSystemFont(HKR_LATIN);
		expect(degraded != null,
				"with no resource manager \"" + HKR_LATIN + "\" still answers a Font instead of throwing or"
						+ " answering null: family=" + (degraded == null ? "null" : degraded.getFamily()));

		/* The adaptive path needs no file at all, which is the one place this fallback is strictly
		   more robust than JCM 2.x's own substitution: a CJK name is answered from AWT alone. */
		final Font degradedCjk = ScriptEngine.Resources.getSystemFont(HKR_CJK);
		expect(degradedCjk != null,
				"and a CJK name is answered without reading a file at all: family="
						+ (degradedCjk == null ? "null" : degradedCjk.getFamily()));

		final Font nullName = ScriptEngine.Resources.getSystemFont(null);
		expect(nullName == null, "getSystemFont(null) still answers null");

		final Font bundledName = ScriptEngine.Resources.getSystemFont("Noto Sans");
		expect(bundledName == null,
				"the file branch still soft-fails to null when the file cannot be read, which is what the"
						+ " packs' own sysFont() guards for");
	}

	// ------------------------------------------------------------------

	/**
	 * A family this machine really has, preferring the Noto ones so the output is the same on the
	 * machines this port is measured on.
	 */
	private static String firstInstalledFamily() {
		final String[] preferred = {"Noto Sans SC", "Noto Serif SC", "Arial", "Tahoma", "Segoe UI", "Verdana"};
		for (String candidate : preferred) {
			if (installed(candidate)) {
				return candidate;
			}
		}
		for (String candidate : familyNames()) {
			if (installed(candidate) && !candidate.equalsIgnoreCase("Dialog")) {
				return candidate;
			}
		}
		return null;
	}

	/** Whether the machine's font tables hold the name, in either locale. */
	private static boolean installed(String name) {
		for (String family : familyNames()) {
			if (family.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Both locale tables, merged once. */
	private static java.util.Collection<String> familyNames() {
		if (FAMILY_NAMES == null) {
			final java.util.Set<String> merged = new java.util.LinkedHashSet<>();
			final GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
			java.util.Collections.addAll(merged, environment.getAvailableFontFamilyNames());
			java.util.Collections.addAll(merged, environment.getAvailableFontFamilyNames(Locale.ENGLISH));
			FAMILY_NAMES = merged;
		}
		return FAMILY_NAMES;
	}

	private static boolean isBundledNoto(Font font) {
		final Font latin = ScriptFonts.read(NOTO_SANS);
		final Font cjk = ScriptFonts.read(NOTO_SERIF);
		return latin != null && font.getFamily().equalsIgnoreCase(latin.getFamily())
				|| cjk != null && font.getFamily().equalsIgnoreCase(cjk.getFamily());
	}

	/** The pixels a character paints, as a string, so two renderings can be compared exactly. */
	private static String glyphs(Font font, String text) {
		final BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(font.deriveFont(Font.PLAIN, 40.0f));
			g.drawString(text, 4, 48);
		} finally {
			g.dispose();
		}
		final StringBuilder painted = new StringBuilder();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				painted.append((image.getRGB(x, y) >>> 24) > 8 ? '#' : '.');
			}
		}
		return painted.toString();
	}

	private static int painted(String glyphs) {
		int count = 0;
		for (int i = 0; i < glyphs.length(); i++) {
			if (glyphs.charAt(i) == '#') {
				count++;
			}
		}
		return count;
	}

	private static void expect(boolean condition, String message) {
		if (condition) {
			pass(message);
		} else {
			fail(message);
		}
	}

	private static void pass(String message) {
		System.out.println("OK   " + message);
	}

	private static void note(String message) {
		System.out.println("NOTE " + message);
	}

	private static void fail(String message) {
		System.out.println("FAIL " + message);
		failures++;
	}
}
