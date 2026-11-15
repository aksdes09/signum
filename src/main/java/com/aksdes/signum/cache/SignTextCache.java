package com.aksdes.signum.cache;

import com.aksdes.signum.Signum;
import com.aksdes.signum.SignumConfig;
import com.aksdes.signum.atlas.AtlasPage;
import com.aksdes.signum.atlas.ShelfPacker;
import com.aksdes.signum.compat.Compat;
import com.aksdes.signum.mixin.GameRendererAccessor;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.SignText;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Сердце мода. Каждая сторона таблички один раз рисуется ванильным кодом в ячейку атласа, после чего
 * каждый кадр выводится одним прямоугольником (4 вершины) вместо раскладки строк и 2+ треугольников
 * на символ. Все методы вызываются только из render thread.
 */
public final class SignTextCache {

	/** Поля вокруг текста в пикселях шрифта: курсив, жирный и обводка светящегося текста вылезают за ширину строки. */
	private static final int PADDING = 2;
	private static final long SECOND_NS = 1_000_000_000L;
	private static final long FULL_ATLAS_BACKOFF_NS = SECOND_NS;
	private static final long PRESSURE_EVICT_NS = SECOND_NS;

	private final int scale;
	private final int atlasSize;
	private final int maxPages;
	private final long idleNs;
	private final double bakesPerNs;
	private final int bakeBurst;

	private final AtlasPage[] pages;
	private final HashMap<TextKey, Cell> cells = new HashMap<>();
	private GpuBufferSlice projection;

	/** Растёт при каждом полном сбросе — так SignHolder'ы узнают, что их данные устарели. */
	private int epoch = 1;
	private boolean baking;
	private double bakeTokens;
	private long lastRefill;
	private long nextHousekeeping;
	private long atlasFullUntil;

	// Статистика для F3.
	private int bakesThisSecond;
	private int bakesLastSecond;
	private int quadsThisSecond;
	private int quadsLastSecond;

	public SignTextCache(final SignumConfig config) {
		this.scale = config.textureScale;
		this.atlasSize = config.atlasSize;
		this.maxPages = config.maxAtlasPages;
		this.idleNs = config.idleSeconds * SECOND_NS;
		this.bakesPerNs = config.bakesPerSecond / (double) SECOND_NS;
		this.bakeBurst = config.bakeBurst;
		this.pages = new AtlasPage[this.maxPages];
		this.bakeTokens = this.bakeBurst;
		this.lastRefill = System.nanoTime();
	}

	/**
	 * Пытается вывести сторону таблички из атласа. false — пусть рисует ваниль
	 * (шейдерпак, §k, атлас переполнен, лимит запеканий в этом кадре и т.п.).
	 */
	public boolean submit(final VanillaSignTextSubmitter renderer, final Font font, final SignRenderState state,
			final PoseStack poseStack, final SubmitNodeCollector collector, final SignText text) {
		if (this.baking) {
			return false; // это наш собственный вызов ванили при запекании
		}
		final long now = System.nanoTime();
		if (Compat.useVanilla(now)) {
			return false;
		}
		if (now - this.nextHousekeeping >= 0) {
			this.housekeeping(now);
		}

		final SignHolder holder = ((SignTextAccess) (Object) text).signum$holder();
		final boolean filtered = state.isTextFilteringEnabled;
		if (holder.epoch != this.epoch || holder.filtered != filtered) {
			holder.reset(this.epoch, filtered);
		}
		if (holder.status == SignHolder.VANILLA) {
			return false;
		}
		final boolean glowing = text.hasGlowingText();
		final boolean outline = glowing && state.drawOutline;

		Cell cell = outline ? holder.outline : holder.plain;
		if (cell == null || !cell.alive) {
			cell = this.resolve(renderer, font, state, text, holder, glowing, outline, now);
			if (cell == null) {
				return false;
			}
			if (outline) {
				holder.outline = cell;
			} else {
				holder.plain = cell;
			}
		}
		cell.lastUsed = now;
		this.quadsThisSecond++;
		emitQuad(cell, glowing ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords, poseStack, collector);
		return true;
	}

	private static void emitQuad(final Cell cell, final int light, final PoseStack poseStack, final SubmitNodeCollector collector) {
		final float l = -cell.halfWidth;
		final float r = cell.halfWidth;
		final float t = -cell.halfHeight;
		final float b = cell.halfHeight;
		final float u0 = cell.u0;
		final float u1 = cell.u1;
		final float v0 = cell.v0;
		final float v1 = cell.v1;
		collector.submitCustomGeometry(poseStack, cell.page.renderType, (pose, consumer) -> {
			consumer.addVertex(pose, l, b, 0F).setColor(255, 255, 255, 255).setUv(u0, v1).setLight(light);
			consumer.addVertex(pose, r, b, 0F).setColor(255, 255, 255, 255).setUv(u1, v1).setLight(light);
			consumer.addVertex(pose, r, t, 0F).setColor(255, 255, 255, 255).setUv(u1, v0).setLight(light);
			consumer.addVertex(pose, l, t, 0F).setColor(255, 255, 255, 255).setUv(u0, v0).setLight(light);
		});
	}

	private Cell resolve(final VanillaSignTextSubmitter renderer, final Font font, final SignRenderState state,
			final SignText text, final SignHolder holder, final boolean glowing, final boolean outline, final long now) {
		final int maxLineWidth = state.maxTextLineWidth;
		FormattedCharSequence[] lines = null;

		if (holder.status == SignHolder.UNKNOWN) {
			lines = renderLines(font, text, holder.filtered, maxLineWidth);
			int width = 0;
			boolean obfuscated = false;
			for (FormattedCharSequence line : lines) {
				width = Math.max(width, font.width(line));
				obfuscated |= !line.accept((index, style, codePoint) -> !style.isObfuscated());
			}
			holder.maxWidth = width;
			holder.status = obfuscated || width <= 0 ? SignHolder.VANILLA : SignHolder.CACHEABLE;
			if (holder.status == SignHolder.VANILLA) {
				return null;
			}
		}

		TextKey key = outline ? holder.outlineKey : holder.plainKey;
		if (key == null) {
			if (lines == null) {
				lines = renderLines(font, text, holder.filtered, maxLineWidth);
			}
			key = TextKey.of(lines, text.getColor(), glowing, outline, maxLineWidth, state.textLineHeight);
			if (outline) {
				holder.outlineKey = key;
			} else {
				holder.plainKey = key;
			}
		}
		final Cell shared = this.cells.get(key);
		if (shared != null) {
			return shared;
		}
		if (!this.mayBake(now)) {
			return null;
		}
		final Cell baked = this.bake(renderer, state, text, key, holder.maxWidth, outline, now);
		if (baked != null) {
			this.cells.put(key, baked);
		}
		return baked;
	}

	/** Тот же разбиватель строк, что у ванили; результат кешируется внутри SignText и ванилью же переиспользуется. */
	private static FormattedCharSequence[] renderLines(final Font font, final SignText text, final boolean filtered, final int maxLineWidth) {
		return text.getRenderMessages(filtered, component -> {
			final List<FormattedCharSequence> split = font.split(component, maxLineWidth);
			return split.isEmpty() ? FormattedCharSequence.EMPTY : split.getFirst();
		});
	}

	private boolean mayBake(final long now) {
		if (now - this.atlasFullUntil < 0) {
			return false;
		}
		// Эффект «Тьма» пульсирует лайтмапой — не запекаем текст в «тёмную» фазу.
		final Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.hasEffect(MobEffects.DARKNESS)) {
			return false;
		}
		this.bakeTokens = Math.min(this.bakeBurst, this.bakeTokens + (now - this.lastRefill) * this.bakesPerNs);
		this.lastRefill = now;
		if (this.bakeTokens < 1D) {
			return false;
		}
		this.bakeTokens -= 1D;
		return true;
	}

	private Cell bake(final VanillaSignTextSubmitter renderer, final SignRenderState state, final SignText text,
			final TextKey key, final int maxWidth, final boolean outline, final long now) {
		int textWidth = maxWidth;
		if ((this.scale & 1) == 1 && (textWidth & 1) == 1) {
			textWidth++; // при нечётном масштабе центр ячейки должен попадать на целый пиксель
		}
		final int textHeight = 4 * state.textLineHeight;
		final int cellWidth = (textWidth + 2 * PADDING) * this.scale;
		final int cellHeight = (textHeight + 2 * PADDING) * this.scale;

		ShelfPacker.Region region = null;
		// При включённом шейдерпаке Iris на это время перестаёт подменять шейдеры, формат вершин и глубину.
		Compat.enterVanillaRendering();
		try {
			final Placement placement = this.allocate(cellWidth, cellHeight, now);
			if (placement == null) {
				this.atlasFullUntil = now + FULL_ATLAS_BACKOFF_NS;
				Signum.LOGGER.debug("Sign atlas is full ({} cells); drawing vanilla for a while", this.cells.size());
				return null;
			}
			region = placement.region;
			placement.page.clearRegion(region);
			this.renderIntoCell(renderer, state, text, placement.page, region, outline);
			this.bakesThisSecond++;
			return new Cell(key, placement.page, region, this.scale, now);
		} catch (RuntimeException e) {
			if (region != null) {
				region.free();
			}
			Signum.LOGGER.error("Failed to bake sign text; Signum disables itself for this session", e);
			Signum.cache = null;
			this.clearAll("bake failure");
			return null;
		} finally {
			Compat.exitVanillaRendering();
		}
	}

	private void renderIntoCell(final VanillaSignTextSubmitter renderer, final SignRenderState state, final SignText text,
			final AtlasPage page, final ShelfPacker.Region region, final boolean outline) {
		if (this.projection == null) {
			final Projection ortho = new Projection();
			ortho.setupOrtho(-1000F, 1000F, this.atlasSize, this.atlasSize, true);
			this.projection = new ProjectionMatrixBuffer("Signum sign atlas").getBuffer(ortho);
		}

		final Minecraft mc = Minecraft.getInstance();
		final boolean savedOutline = state.drawOutline;
		final int savedLight = state.lightCoords;
		final GpuBufferSlice savedFog = RenderSystem.getShaderFog();
		final Matrix4fStack modelView = RenderSystem.getModelViewStack();
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(this.projection, ProjectionType.ORTHOGRAPHIC);
		modelView.pushMatrix().identity();
		RenderSystem.setShaderFog(((GameRendererAccessor) mc.gameRenderer).signum$fogRenderer().getBuffer(FogRenderer.FogMode.NONE));
		this.baking = true;
		try {
			// Свет применяется уже к готовому кваду, поэтому печём при полной яркости.
			state.drawOutline = outline;
			state.lightCoords = LightCoordsUtil.FULL_BRIGHT;

			final PoseStack bakePose = new PoseStack();
			bakePose.translate(region.x + region.width / 2F, region.y + region.height / 2F, 0F);
			bakePose.scale(this.scale, this.scale, 1F);
			final SubmitNodeStorage storage = new SubmitNodeStorage();
			renderer.signum$submitVanilla(state, bakePose, storage, text);

			try (FeatureRenderDispatcher.PreparedFrame frame = mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage);
					RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
							() -> "Signum sign bake",
							page.target().getColorTextureView(), Optional.empty(),
							page.target().getDepthTextureView(), OptionalDouble.empty())) {
				RenderSystem.bindDefaultUniforms(pass);
				FeatureRenderDispatcher.renderAllFeatures(pass, frame);
			}
		} finally {
			this.baking = false;
			state.drawOutline = savedOutline;
			state.lightCoords = savedLight;
			RenderSystem.setShaderFog(savedFog);
			modelView.popMatrix();
			RenderSystem.restoreProjectionMatrix();
		}
	}

	private record Placement(AtlasPage page, ShelfPacker.Region region) {
	}

	private Placement allocate(final int width, final int height, final long now) {
		Placement placement = this.tryExistingPages(width, height);
		if (placement != null) {
			return placement;
		}
		for (int i = 0; i < this.pages.length; i++) {
			if (this.pages[i] == null) {
				this.pages[i] = new AtlasPage(i, this.atlasSize, now);
				final ShelfPacker.Region region = this.pages[i].packer.allocate(width, height);
				return region == null ? null : new Placement(this.pages[i], region);
			}
		}
		// Все страницы заняты: выселяем то, что не рисовалось последнюю секунду (вне поля зрения).
		this.evictOlderThan(now - PRESSURE_EVICT_NS);
		return this.tryExistingPages(width, height);
	}

	private Placement tryExistingPages(final int width, final int height) {
		for (AtlasPage page : this.pages) {
			if (page != null) {
				final ShelfPacker.Region region = page.packer.allocate(width, height);
				if (region != null) {
					return new Placement(page, region);
				}
			}
		}
		return null;
	}

	private void housekeeping(final long now) {
		this.nextHousekeeping = now + SECOND_NS;
		this.bakesLastSecond = this.bakesThisSecond;
		this.quadsLastSecond = this.quadsThisSecond;
		this.bakesThisSecond = 0;
		this.quadsThisSecond = 0;

		this.evictOlderThan(now - this.idleNs);

		// Лишние пустые страницы (кроме первой) отдают видеопамять.
		for (int i = 1; i < this.pages.length; i++) {
			final AtlasPage page = this.pages[i];
			if (page == null) {
				continue;
			}
			if (!page.packer.isEmpty()) {
				page.emptySince = now;
			} else if (now - page.emptySince > this.idleNs) {
				page.close();
				this.pages[i] = null;
			}
		}
	}

	private void evictOlderThan(final long threshold) {
		final Iterator<Cell> it = this.cells.values().iterator();
		while (it.hasNext()) {
			final Cell cell = it.next();
			if (cell.lastUsed - threshold < 0) {
				cell.release();
				it.remove();
			}
		}
	}

	/** Сбрасывает все ячейки и освобождает текстуры (смена мира, перезагрузка ресурсов). */
	public void clearAll(final String reason) {
		RenderSystem.assertOnRenderThread();
		for (Cell cell : this.cells.values()) {
			cell.alive = false;
		}
		this.cells.clear();
		this.epoch++;
		for (int i = 0; i < this.pages.length; i++) {
			if (this.pages[i] != null) {
				this.pages[i].close();
				this.pages[i] = null;
			}
		}
		this.atlasFullUntil = 0L;
		Signum.LOGGER.debug("Sign atlas cleared ({})", reason);
	}

	/** Строки для экрана отладки F3. */
	public String debugLine() {
		int pageCount = 0;
		for (AtlasPage page : this.pages) {
			if (page != null) {
				pageCount++;
			}
		}
		return "Signum: " + this.cells.size() + " cells, " + pageCount + "/" + this.maxPages + " pages "
				+ this.atlasSize + "px x" + this.scale + ", " + this.quadsLastSecond + " quads/s, "
				+ this.bakesLastSecond + " bakes/s" + Compat.shaderStatus();
	}
}
