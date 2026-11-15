package com.aksdes.signum.settings;

import com.aksdes.signum.Signum;
import net.minecraft.network.chat.Component;

/** Ключи перевода (assets/signum/lang). */
public final class SettingsKeys {

	public static final String TITLE = "signum.options.title";
	public static final String PAGE = "signum.options.page";
	public static final String OPTIMIZE = "signum.option.optimize";
	public static final String OPTIMIZE_TOOLTIP = "signum.option.optimize.tooltip";
	public static final String UNAVAILABLE = "signum.option.unavailable";
	public static final String DEBUG_INFO = "signum.option.debug_info";
	public static final String DEBUG_INFO_TOOLTIP = "signum.option.debug_info.tooltip";

	private SettingsKeys() {
	}

	/** Подсказка к переключателю оптимизации; если Signum недоступен — с причиной. */
	public static Component optimizeTooltip() {
		final Component base = Component.translatable(OPTIMIZE_TOOLTIP);
		if (Signum.unavailableReason == null) {
			return base;
		}
		return Component.empty().append(base).append("\n\n")
				.append(Component.translatable(UNAVAILABLE, Signum.unavailableReason));
	}
}
