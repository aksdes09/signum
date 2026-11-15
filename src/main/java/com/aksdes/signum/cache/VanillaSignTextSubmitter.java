package com.aksdes.signum.cache;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;

/** Добавляется в AbstractSignRenderer миксином: ванильная отрисовка текста, которой запекается ячейка. */
public interface VanillaSignTextSubmitter {

	void signum$submitVanilla(SignRenderState state, PoseStack poseStack, SubmitNodeCollector collector, SignText text);
}
