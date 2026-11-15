package com.aksdes.signum.integration;

import com.aksdes.signum.Signum;
import com.aksdes.signum.settings.SettingsKeys;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Страница Signum в видеонастройках Sodium (Sodium заменяет ванильный экран видеонастроек).
 * Загружается только самим Sodium через точку входа {@code sodium:config_api_user}.
 */
public final class SignumSodiumConfig implements ConfigEntryPoint {

	/** Иконка мода для списка в видеонастройках Sodium. */
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(Signum.MOD_ID, "textures/gui/sodium_icon.png");
	/** Зелёный, как на иконке мода (#1BFF00); без своей темы Sodium выбирает цвет мода случайно. */
	private static final int THEME_RGB = 0x1BFF00;

	@Override
	public void registerConfigLate(final ConfigBuilder builder) {
		final OptionPageBuilder page;
		try {
			// Сначала собираем всё; регистрируемся у Sodium только если сборка прошла,
			// иначе при несовместимой версии API Sodium упал бы с «пустой» страницей.
			final BooleanOptionBuilder optimize = builder.createBooleanOption(Identifier.fromNamespaceAndPath(Signum.MOD_ID, "optimize"))
					.setName(Component.translatable(SettingsKeys.OPTIMIZE))
					.setTooltip(SettingsKeys.optimizeTooltip())
					.setStorageHandler(() -> Signum.config.save())
					.setDefaultValue(Boolean.TRUE)
					.setBinding(value -> Signum.setOptimizationEnabled(value), () -> Signum.config.enabled);
			final BooleanOptionBuilder debugInfo = builder.createBooleanOption(Identifier.fromNamespaceAndPath(Signum.MOD_ID, "debug_info"))
					.setName(Component.translatable(SettingsKeys.DEBUG_INFO))
					.setTooltip(Component.translatable(SettingsKeys.DEBUG_INFO_TOOLTIP))
					.setStorageHandler(() -> Signum.config.save())
					.setDefaultValue(Boolean.TRUE)
					.setBinding(value -> Signum.setDebugInfoShown(value), () -> Signum.config.showDebugInfo);
			final OptionGroupBuilder group = builder.createOptionGroup()
					.addOption(optimize)
					.addOption(debugInfo);
			page = builder.createOptionPage()
					.setName(Component.translatable(SettingsKeys.PAGE))
					.addOptionGroup(group);
		} catch (Throwable t) {
			Signum.LOGGER.warn("Could not build the Signum page for Sodium's video settings (incompatible Sodium config API); use config/signum.properties", t);
			return;
		}
		final ModOptionsBuilder options = builder.registerOwnModOptions().addPage(page);
		try {
			// Оформление — после страницы: если в какой-то версии Sodium этих методов нет,
			// страница с настройками всё равно останется (просто без иконки/цвета).
			options.setNonTintedIcon(ICON);
			options.setColorTheme(builder.createColorTheme().setBaseThemeRGB(THEME_RGB));
		} catch (Throwable t) {
			Signum.LOGGER.debug("Sodium does not support mod icons/themes here", t);
		}
	}
}
