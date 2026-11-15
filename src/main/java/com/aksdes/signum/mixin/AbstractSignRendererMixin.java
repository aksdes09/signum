package com.aksdes.signum.mixin;

import com.aksdes.signum.Signum;
import com.aksdes.signum.cache.SignTextCache;
import com.aksdes.signum.cache.VanillaSignTextSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class AbstractSignRendererMixin implements VanillaSignTextSubmitter {

	@Shadow
	@Final
	private Font font;

	@Shadow
	protected abstract void submitSignText(SignRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, SignText signText);

	@Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
	private void signum$submitFromAtlas(final SignRenderState state, final PoseStack poseStack,
			final SubmitNodeCollector collector, final SignText signText, final CallbackInfo ci) {
		final SignTextCache cache = Signum.cache;
		if (cache != null && cache.submit(this, this.font, state, poseStack, collector, signText)) {
			ci.cancel();
		}
	}

	@Override
	public void signum$submitVanilla(final SignRenderState state, final PoseStack poseStack,
			final SubmitNodeCollector collector, final SignText text) {
		this.submitSignText(state, poseStack, collector, text);
	}
}
