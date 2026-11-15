package com.aksdes.signbake;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Properties;

/**
 * SignBake: sign text is drawn once into a cell of an atlas texture with the
 * game's own font renderer, and from then on shown as a single quad.
 *
 * <p>Everything here runs on the render thread. The class initialises itself
 * the first time a sign with text is rendered.
 *
 * <p>The render-to-atlas approach follows the experimental sign text buffering
 * of ImmediatelyFast (LGPL-3.0) by RK_01/RaphiMC.
 */
public final class SignBake {

	private static final Logger LOGGER = LoggerFactory.getLogger("SignBake");

	// Atlas layout, in sign text units (one unit is one pixel of the vanilla font).
	// A vanilla sign needs at most 90 x 40 units, plus one unit of glow outline on every side.
	static final int CELL_WIDTH = 96;
	static final int CELL_HEIGHT = 48;
	static final int PAGE_UNITS = 1024;
	static final int COLUMNS = PAGE_UNITS / CELL_WIDTH;
	static final int ROWS = PAGE_UNITS / CELL_HEIGHT;
	static final int CELLS_PER_PAGE = COLUMNS * ROWS;

	/** A cell is given back after its sign has not been drawn for this many epochs (about seconds). */
	private static final int KEEP_EPOCHS = 4;
	private static final long EPOCH_NANOS = 1_000_000_000L;
	/** At most this many texts are baked per time window; the rest stay vanilla a little longer. */
	private static final int BAKES_PER_WINDOW = 8;
	private static final long WINDOW_NANOS = 15_000_000L;

	private static final Vector4f TRANSPARENT = new Vector4f(0.0F);

	/** False when switched off in the config, or after an unexpected error. */
	public static boolean enabled;
	/** True while the vanilla renderer is drawing into the atlas for us. */
	public static boolean baking;
	/** Bumped whenever every baked text must be redrawn (resource reload, shader toggle). */
	public static int generation;
	public static int epoch;

	private static int scale = 1;
	private static int maxPages = 8;
	private static boolean bakeWithShaders;

	private static final ArrayList<Page> PAGES = new ArrayList<>();
	private static final ArrayList<BakedText> LIVE = new ArrayList<>();

	private static ProjectionMatrixBuffer projectionBuffer;
	private static GpuBufferSlice projection;
	private static GpuBufferSlice savedFog;
	private static MethodHandle irisShadersInUse;

	private static boolean shaderBlock;
	private static int blockedEpoch = -1;
	private static boolean warnedFull;
	private static int calls;
	private static long epochStart;
	private static long windowStart;
	private static int windowCount;

	private static int probeVisible;
	private static boolean probeDynamic;

	static {
		boolean on = loadConfig();
		if (on) {
			try {
				final Projection ortho = new Projection();
				ortho.setupOrtho(-1000.0F, 1000.0F, PAGE_UNITS, PAGE_UNITS, true);
				projectionBuffer = new ProjectionMatrixBuffer("SignBake");
				projection = projectionBuffer.getBuffer(ortho);

				((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
						.registerReloadListener((ResourceManagerReloadListener) manager -> generation++);

				irisShadersInUse = findIris();
				shaderBlock = !bakeWithShaders && shadersInUse();
				epochStart = System.nanoTime();
			} catch (final Throwable t) {
				LOGGER.error("SignBake could not start and stays off; signs render the vanilla way", t);
				on = false;
			}
		}
		enabled = on;
	}

	private SignBake() {
	}

	// ---------------------------------------------------------------- config

	private static boolean loadConfig() {
		boolean on = true;
		try {
			final Path file = FabricLoader.getInstance().getConfigDir().resolve("signbake.properties");
			final Properties properties = new Properties();
			if (Files.isRegularFile(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					properties.load(reader);
				}
			} else {
				Files.writeString(file, """
						# SignBake. Restart the game after changing anything here.

						# Master switch.
						enabled=true

						# Texture pixels per pixel of the vanilla font (1-4).
						# 1 is exact for the default font. Use 2 for HD or Unicode fonts;
						# every step multiplies the texture memory by four.
						scale=1

						# Each atlas page holds 210 sign sides and takes 8 MB of video memory at scale 1.
						# Pages are created only when needed. Signs that do not fit are drawn the vanilla way.
						maxPages=8

						# Sign text is not baked while an Iris shader pack is active, because the pack
						# replaces the text shader. Set to true to try it anyway.
						bakeWithShaders=false
						""");
			}
			on = Boolean.parseBoolean(properties.getProperty("enabled", "true").trim());
			scale = clamp(properties.getProperty("scale"), 1, 1, 4);
			maxPages = clamp(properties.getProperty("maxPages"), 8, 1, 64);
			bakeWithShaders = Boolean.parseBoolean(properties.getProperty("bakeWithShaders", "false").trim());
		} catch (final Throwable t) {
			LOGGER.warn("Could not read signbake.properties, using defaults", t);
		}
		return on;
	}

	private static int clamp(final String value, final int fallback, final int min, final int max) {
		if (value == null) {
			return fallback;
		}
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
		} catch (final NumberFormatException e) {
			return fallback;
		}
	}

	// ---------------------------------------------------------------- shaders

	private static MethodHandle findIris() {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return null;
		}
		try {
			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object instance = api.getMethod("getInstance").invoke(null);
			return MethodHandles.publicLookup()
					.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
					.bindTo(instance);
		} catch (final Throwable t) {
			LOGGER.warn("Iris is installed but its API was not found; shader packs will not be detected", t);
			return null;
		}
	}

	private static boolean shadersInUse() {
		if (irisShadersInUse == null) {
			return false;
		}
		try {
			return (boolean) irisShadersInUse.invoke();
		} catch (final Throwable t) {
			return false;
		}
	}

	// ---------------------------------------------------------------- per-frame bookkeeping

	/** Called for every sign side that is drawn; does real work only once in 256 calls. */
	public static void count() {
		if ((++calls & 0xFF) == 0) {
			tick();
		}
	}

	private static void tick() {
		final long now = System.nanoTime();
		if (now - epochStart < EPOCH_NANOS) {
			return;
		}
		epochStart = now;
		epoch++;

		final boolean block = !bakeWithShaders && shadersInUse();
		if (block != shaderBlock) {
			shaderBlock = block;
			generation++;
		}
		sweep(KEEP_EPOCHS);
	}

	/** Gives back the cells of texts that were not drawn for more than {@code keep} epochs. */
	private static void sweep(final int keep) {
		for (int i = LIVE.size() - 1; i >= 0; i--) {
			final BakedText baked = LIVE.get(i);
			if (epoch - baked.lastUsed > keep) {
				release(baked);
			}
		}
	}

	/** Frees the cell of a text and detaches it from its sign. */
	public static void release(final BakedText baked) {
		final Page page = baked.page;
		if (page == null) {
			return;
		}
		page.free[page.freeCount++] = baked.cell;
		baked.page = null;

		final int last = LIVE.size() - 1;
		final BakedText moved = LIVE.get(last);
		LIVE.set(baked.liveIndex, moved);
		moved.liveIndex = baked.liveIndex;
		LIVE.remove(last);

		((BakedTextHolder) (Object) baked.owner).signbake$set(null);
	}

	// ---------------------------------------------------------------- baking

	/** True while nothing new may be baked: a shader pack is active, or the atlas is full for now. */
	public static boolean blocked() {
		return shaderBlock || blockedEpoch == epoch;
	}

	public static boolean takeBudget() {
		final long now = System.nanoTime();
		if (now - windowStart > WINDOW_NANOS) {
			windowStart = now;
			windowCount = 0;
		}
		return windowCount++ < BAKES_PER_WINDOW;
	}

	/** Decides how the four lines of a sign side are going to be drawn. */
	public static byte classify(final FormattedCharSequence[] lines, final int maxLineWidth, final int lineHeight) {
		probeVisible = 0;
		probeDynamic = false;
		for (final FormattedCharSequence line : lines) {
			line.accept(SignBake::probe);
			if (probeDynamic) {
				return BakedText.VANILLA;
			}
		}
		if (probeVisible == 0) {
			return BakedText.EMPTY;
		}
		if (maxLineWidth + 2 > CELL_WIDTH || lineHeight * 4 + 2 > CELL_HEIGHT) {
			return BakedText.VANILLA;
		}
		return BakedText.QUAD;
	}

	private static boolean probe(final int index, final Style style, final int codePoint) {
		// Obfuscated text changes every frame. Private-use code points are what resource packs
		// map to images of any size, which could spill over into the neighbouring atlas cells.
		if (style.isObfuscated() || (codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint >= 0xF0000) {
			probeDynamic = true;
			return false;
		}
		if (codePoint != ' ') {
			probeVisible++;
		}
		return true;
	}

	/** Finds a free cell for the text. Returns false when the atlas is full. */
	public static boolean allocate(final BakedText baked, final int maxLineWidth, final int lineHeight) {
		Page page = pageWithRoom();
		if (page == null && PAGES.size() < maxPages) {
			page = new Page(PAGES.size(), PAGE_UNITS * scale);
			PAGES.add(page);
		}
		if (page == null) {
			// Take back everything that is not on screen right now and try once more.
			sweep(1);
			page = pageWithRoom();
		}
		if (page == null) {
			blockedEpoch = epoch;
			if (!warnedFull) {
				warnedFull = true;
				LOGGER.warn("The sign atlas is full ({} sign sides). The remaining signs are drawn the vanilla way; raise maxPages in signbake.properties if this happens often.", LIVE.size());
			}
			return false;
		}

		baked.page = page;
		baked.liveIndex = LIVE.size();
		LIVE.add(baked);
		baked.place(page.free[--page.freeCount], maxLineWidth, lineHeight);
		return true;
	}

	private static Page pageWithRoom() {
		for (int i = 0; i < PAGES.size(); i++) {
			final Page page = PAGES.get(i);
			if (page.freeCount > 0) {
				return page;
			}
		}
		return null;
	}

	/** Clears the cell of the text and points the render state at the atlas. */
	public static void beginBake(final BakedText baked) {
		final Page page = baked.page;
		final int column = baked.cell % COLUMNS;
		final int row = baked.cell / COLUMNS;
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				page.target.getColorTexture(), TRANSPARENT, page.target.getDepthTexture(), 0.0D,
				column * CELL_WIDTH * scale, page.pixels - (row + 1) * CELL_HEIGHT * scale,
				CELL_WIDTH * scale, CELL_HEIGHT * scale, 0);

		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(projection, ProjectionType.ORTHOGRAPHIC);
		RenderSystem.getModelViewStack().pushMatrix().identity();
		savedFog = RenderSystem.getShaderFog();
		RenderSystem.setShaderFog(Minecraft.getInstance().gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
		baking = true;
	}

	/** Draws what the vanilla sign renderer has just submitted into the atlas page of the text. */
	public static void flushBake(final BakedText baked, final SubmitNodeStorage storage) {
		final Page page = baked.page;
		try (
				var frame = Minecraft.getInstance().gameRenderer.featureRenderDispatcher().prepareFrame(storage);
				var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
						() -> "SignBake",
						page.target.getColorTextureView(), Optional.empty(),
						page.target.getDepthTextureView(), OptionalDouble.empty())
		) {
			RenderSystem.bindDefaultUniforms(pass);
			FeatureRenderDispatcher.renderAllFeatures(pass, frame);
		}
	}

	public static void endBake() {
		baking = false;
		RenderSystem.setShaderFog(savedFog);
		savedFog = null;
		RenderSystem.getModelViewStack().popMatrix();
		RenderSystem.restoreProjectionMatrix();
	}

	/** Something unexpected happened: step aside for the rest of the session instead of crashing. */
	public static void fail(final Throwable t) {
		enabled = false;
		LOGGER.error("SignBake hit an unexpected error and switched itself off; signs render the vanilla way", t);
	}

}
