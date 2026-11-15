package net.caffeinemc.mods.sodium.api.config;

/** Заглушка для компиляции (не попадает в jar). */
@FunctionalInterface
public interface StorageEventHandler {
	void afterSave();
}
