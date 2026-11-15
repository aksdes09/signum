package com.aksdes.signbake.mixin;

import com.aksdes.signbake.BakedText;
import com.aksdes.signbake.BakedTextHolder;
import com.aksdes.signbake.SignBake;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Priority above the default so that mods which cancel sign text (Iris in its shadow pass) go first.
@Mixin(value = AbstractSignRenderer.class, priority = 1100)
public abstract class AbstractSignRendererMixin {

	@Shadow
	@Final
	private Font font;

	@Shadow
	private void submitSignText(final SignRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final SignText signText) {
		throw new AssertionError();
	}

	@Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
	private void signbake$drawBaked(final SignRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final SignText signText, final CallbackInfo ci) {
		if (!SignBake.enabled || SignBake.baking || signText == null) {
			return;
		}
		SignBake.count();

		BakedText baked = ((BakedTextHolder) (Object) signText).signbake$get();
		if (baked == null || baked.isStale(state)) {
			baked = this.signbake$bake(state, signText, baked);
			if (baked == null) {
				return;
			}
		}

		if (baked.kind == BakedText.VANILLA) {
			return;
		}
		if (baked.kind == BakedText.QUAD) {
			baked.lastUsed = SignBake.epoch;
			baked.light = baked.glowing ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
			submitNodeCollector.submitCustomGeometry(poseStack, baked.page.renderType, baked.renderer);
		}
		ci.cancel();
	}

	/**
	 * Works out how this sign side should be drawn and, if it has plain visible text,
	 * lets the vanilla code draw it into an atlas cell. Returns null when the text has to
	 * stay vanilla for this frame (bake budget used up, atlas full, shader pack active).
	 */
	@Unique
	private BakedText signbake$bake(final SignRenderState state, final SignText signText, final BakedText previous) {
		if (SignBake.blocked()) {
			return null;
		}
		try {
			final boolean filtered = state.isTextFilteringEnabled;
			final int maxLineWidth = state.maxTextLineWidth;
			final int lineHeight = state.textLineHeight;
			// The same call the vanilla renderer makes, so the wrapped lines are computed once and shared.
			final FormattedCharSequence[] lines = signText.getRenderMessages(filtered, component -> {
				final List<FormattedCharSequence> split = this.font.split(component, maxLineWidth);
				return split.isEmpty() ? FormattedCharSequence.EMPTY : split.getFirst();
			});

			final byte kind = SignBake.classify(lines, maxLineWidth, lineHeight);
			final BakedText baked = previous != null ? previous : new BakedText(signText);

			if (kind != BakedText.QUAD) {
				SignBake.release(baked);
			} else {
				if (!SignBake.takeBudget()) {
					return null;
				}
				if (baked.page == null && !SignBake.allocate(baked, maxLineWidth, lineHeight)) {
					return null;
				}

				final PoseStack atlasPose = new PoseStack();
				atlasPose.translate(baked.centerX, baked.centerY, 0.0F);
				final SubmitNodeStorage storage = new SubmitNodeStorage();
				// Bake at full brightness; the real light is applied to the quad every frame.
				final int light = state.lightCoords;
				SignBake.beginBake(baked);
				try {
					state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
					this.submitSignText(state, atlasPose, storage, signText);
					SignBake.flushBake(baked, storage);
				} finally {
					SignBake.endBake();
					state.lightCoords = light;
				}
			}

			baked.kind = kind;
			baked.filtered = filtered;
			baked.glowing = signText.hasGlowingText();
			baked.outline = state.drawOutline;
			baked.generation = SignBake.generation;
			baked.lastUsed = SignBake.epoch;
			((BakedTextHolder) (Object) signText).signbake$set(baked);
			return baked;
		} catch (final Throwable t) {
			SignBake.fail(t);
			return null;
		}
	}

}
