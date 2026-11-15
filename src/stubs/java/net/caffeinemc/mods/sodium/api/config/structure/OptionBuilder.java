package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

/** Заглушка для компиляции (не попадает в jar). */
public interface OptionBuilder {
	OptionBuilder setName(Component name);

	OptionBuilder setTooltip(Component tooltip);
}
