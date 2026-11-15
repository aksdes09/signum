package com.aksdes.signum.atlas;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/**
 * Делает цветовой буфер render target'а обычной текстурой TextureManager'а, чтобы на неё можно было
 * сослаться из RenderType. NEAREST — как у ванильного шрифта. Видна в дампе текстур (F3 + S).
 */
final class TargetTexture extends AbstractTexture implements Dumpable {

	TargetTexture(final RenderTarget target) {
		this.texture = target.getColorTexture();
		this.textureView = target.getColorTextureView();
		this.sampler = RenderSystem.getSamplerCache().getSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
	}

	@Override
	public void close() {
		// Буферы принадлежат render target'у и освобождаются в AtlasPage.close().
	}

	@Override
	public void dumpContents(final Identifier selfId, final Path dir) {
		TextureUtil.writeAsPNG(dir, selfId.toDebugFileName(), this.getTexture(), 0, argb -> argb);
	}
}
