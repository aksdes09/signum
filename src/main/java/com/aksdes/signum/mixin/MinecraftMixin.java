package com.aksdes.signum.mixin;

import com.aksdes.signum.Signum;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void signum$clientStarted(final CallbackInfo ci) {
		Signum.onClientStarted();
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void signum$levelChange(final CallbackInfo ci) {
		Signum.onLevelChange();
	}
}
