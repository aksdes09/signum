package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.resources.Identifier;

/** Заглушка для компиляции (не попадает в jar). */
public interface ConfigBuilder {
	ModOptionsBuilder registerOwnModOptions();

	OptionPageBuilder createOptionPage();

	OptionGroupBuilder createOptionGroup();

	BooleanOptionBuilder createBooleanOption(Identifier id);
}
