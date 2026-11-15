package com.terraformersmc.modmenu.api;

/** Заглушка для компиляции (не попадает в jar). */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> null;
	}
}
