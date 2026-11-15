package com.aksdes.signum.atlas;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

/**
 * Одна страница атласа: цвет RGBA8 + глубина (текст табличек рисуется с polygon offset,
 * обводка светящегося текста — поверх, поэтому глубина при запекании нужна).
 */
public final class AtlasPage implements AutoCloseable {

	private static final Vector4f TRANSPARENT = new Vector4f(0F);

	public final int index;
	public final int size;
	public final Identifier textureId;
	public final RenderType renderType;
	public final ShelfPacker packer;
	private final TextureTarget target;
	/** Момент, когда страница стала пустой (для выгрузки лишних страниц). */
	public long emptySince;

	public AtlasPage(final int index, final int size, final long now) {
		this.index = index;
		this.size = size;
		this.target = new TextureTarget("Signum sign atlas #" + index, size, size, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		this.textureId = Identifier.fromNamespaceAndPath("signum", "atlas/" + index);
		Minecraft.getInstance().getTextureManager().register(this.textureId, new TargetTexture(this.target));
		this.renderType = RenderTypes.text(this.textureId);
		this.packer = new ShelfPacker(size, size);
		this.emptySince = now;
		// Содержимое свежей текстуры не определено (особенно на Vulkan) — чистим целиком.
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				this.target.getColorTexture(), TRANSPARENT, this.target.getDepthTexture(), 0D);
	}

	public TextureTarget target() {
		return this.target;
	}

	/** Очищает прямоугольник перед запеканием новой ячейки (координаты — сверху вниз). */
	public void clearRegion(final ShelfPacker.Region r) {
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				this.target.getColorTexture(), TRANSPARENT, this.target.getDepthTexture(), 0D,
				r.x, this.size - r.y - r.height, r.width, r.height, 0);
	}

	@Override
	public void close() {
		Minecraft.getInstance().getTextureManager().release(this.textureId);
		this.target.destroyBuffers();
	}
}
