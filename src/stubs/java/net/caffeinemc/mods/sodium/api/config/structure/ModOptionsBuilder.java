package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.resources.Identifier;

/** Заглушка для компиляции (не попадает в jar). */
public interface ModOptionsBuilder {
	ModOptionsBuilder setColorTheme(ColorThemeBuilder colorTheme);

	ModOptionsBuilder setNonTintedIcon(Identifier texture);

	ModOptionsBuilder addPage(PageBuilder page);
}
