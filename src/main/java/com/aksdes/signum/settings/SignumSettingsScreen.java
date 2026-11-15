package com.aksdes.signum.settings;

import com.aksdes.signum.Signum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Экран настроек в ванильном стиле (открывается из Mod Menu). */
public final class SignumSettingsScreen extends OptionsSubScreen {

	public SignumSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(SettingsKeys.TITLE));
	}

	@Override
	protected void addOptions() {
		if (this.list != null) {
			this.list.addSmall(
					toggle(SettingsKeys.OPTIMIZE, SettingsKeys.optimizeTooltip(), Signum.config.enabled, Signum::setOptimizationEnabled),
					toggle(SettingsKeys.DEBUG_INFO, Component.translatable(SettingsKeys.DEBUG_INFO_TOOLTIP), Signum.config.showDebugInfo, Signum::setDebugInfoShown));
		}
	}

	@Override
	public void removed() {
		Signum.config.save();
	}

	private static OptionInstance<Boolean> toggle(final String key, final Component tooltip, final boolean value, final Consumer<Boolean> onChange) {
		return new OptionInstance<>(
				key,
				OptionInstance.cachedConstantTooltip(tooltip),
				(caption, on) -> CommonComponents.optionNameValue(caption, Component.translatable(on ? "options.on" : "options.off")),
				OptionInstance.BOOLEAN_VALUES,
				value,
				onChange);
	}
}
