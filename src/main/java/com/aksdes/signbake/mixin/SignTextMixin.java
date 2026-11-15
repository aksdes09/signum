package com.aksdes.signbake.mixin;

import com.aksdes.signbake.BakedText;
import com.aksdes.signbake.BakedTextHolder;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * A SignText never changes (editing a sign creates a new one), so the baked
 * texture can simply live in a field next to it.
 */
@Mixin(SignText.class)
public abstract class SignTextMixin implements BakedTextHolder {

	@Unique
	private BakedText signbake$baked;

	@Override
	public BakedText signbake$get() {
		return this.signbake$baked;
	}

	@Override
	public void signbake$set(final BakedText baked) {
		this.signbake$baked = baked;
	}

}
