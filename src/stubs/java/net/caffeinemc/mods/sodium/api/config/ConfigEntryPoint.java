package net.caffeinemc.mods.sodium.api.config;

import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;

/** Заглушка для компиляции (не попадает в jar). */
public interface ConfigEntryPoint {
	default void registerConfigEarly(ConfigBuilder builder) {
	}

	void registerConfigLate(ConfigBuilder builder);
}
