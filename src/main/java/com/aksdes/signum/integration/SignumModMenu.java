package com.aksdes.signum.integration;

import com.aksdes.signum.settings.SignumSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Кнопка «Настройки» у Signum в списке Mod Menu. Загружается только самим Mod Menu. */
public final class SignumModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SignumSettingsScreen::new;
	}
}
