package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Заглушка для компиляции (не попадает в jar). */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
	S create(Screen parent);
}
