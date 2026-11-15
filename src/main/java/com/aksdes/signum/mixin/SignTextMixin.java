package com.aksdes.signum.mixin;

import com.aksdes.signum.cache.SignHolder;
import com.aksdes.signum.cache.SignTextAccess;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SignText.class)
public abstract class SignTextMixin implements SignTextAccess {

	@Unique
	private SignHolder signum$holder;

	@Override
	public SignHolder signum$holder() {
		SignHolder holder = this.signum$holder;
		if (holder == null) {
			holder = new SignHolder();
			this.signum$holder = holder;
		}
		return holder;
	}
}
