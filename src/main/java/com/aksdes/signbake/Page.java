package com.aksdes.signbake;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * One atlas texture, cut into equal cells. Pages are created only when the
 * previous ones are full, so a world with few signs costs a single small texture.
 */
public final class Page {

	final Target target;
	final int pixels;
	public final RenderType renderType;

	/** Stack of free cell indices. */
	final int[] free = new int[SignBake.CELLS_PER_PAGE];
	int freeCount = SignBake.CELLS_PER_PAGE;

	Page(final int index, final int pixels) {
		this.pixels = pixels;
		this.target = new Target(pixels);
		final Identifier id = Identifier.fromNamespaceAndPath("signbake", "atlas/" + index);
		Minecraft.getInstance().getTextureManager().register(id, new View(this.target));
		this.renderType = RenderTypes.text(id);
		for (int i = 0; i < this.free.length; i++) {
			this.free[i] = this.free.length - 1 - i;
		}
	}

	static final class Target extends TextureTarget {

		Target(final int pixels) {
			super("SignBake atlas", pixels, pixels, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		}

	}

	/** Lets the texture manager hand the render target's colour texture to a render type. */
	private static final class View extends AbstractTexture {

		View(final RenderTarget target) {
			this.texture = target.getColorTexture();
			this.textureView = target.getColorTextureView();
			this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
		}

		@Override
		public void close() {
			// The render target owns the texture.
		}

	}

}
