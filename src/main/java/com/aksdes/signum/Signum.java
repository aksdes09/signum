package com.aksdes.signum;

import com.aksdes.signum.cache.SignTextCache;
import com.aksdes.signum.compat.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Signum implements ClientModInitializer {

	public static final String MOD_ID = "signum";
	public static final Logger LOGGER = LoggerFactory.getLogger("Signum");

	public static String version = "?";
	public static SignumConfig config = new SignumConfig();
	/** null, пока клиент не инициализирован или Signum несовместим с окружением (см. {@link #unavailableReason}). */
	public static SignTextCache cache;
	/** Почему оптимизация недоступна вообще (конфликт модов, сбой), или null. */
	public static String unavailableReason;

	private static boolean started;

	@Override
	public void onInitializeClient() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		config = SignumConfig.load(FabricLoader.getInstance().getConfigDir().resolve("signum.properties"));
		unavailableReason = Compat.findHardConflict();
		if (unavailableReason != null) {
			LOGGER.warn("Signum {} unavailable: {}", version, unavailableReason);
		}
	}

	/** Вызывается в конце конструктора Minecraft (render thread, устройство уже создано). */
	public static void onClientStarted() {
		if (started) {
			return;
		}
		started = true;
		if (unavailableReason != null) {
			return;
		}
		// Атлас создаётся лениво при первом запекании, так что выключенная оптимизация ничего не стоит.
		cache = new SignTextCache(config);
		final ResourceManagerReloadListener listener = manager -> {
			if (cache != null) {
				cache.clearAll("resource reload");
			}
		};
		if (Minecraft.getInstance().getResourceManager() instanceof ReloadableResourceManager reloadable) {
			reloadable.registerReloadListener(listener);
		}
		LOGGER.info("Signum {} ready: optimization {}, atlas {}x{} px, text scale x{}, up to {} page(s)",
				version, config.enabled ? "on" : "off", config.atlasSize, config.atlasSize, config.textureScale, config.maxAtlasPages);
	}

	/** Смена мира/выход в меню: освобождаем всю видеопамять атласа. */
	public static void onLevelChange() {
		if (cache != null) {
			cache.clearAll("level change");
		}
	}

	/** Переключатель из экранов настроек (render thread). Изменение действует сразу. */
	public static void setOptimizationEnabled(final boolean enabled) {
		if (config.enabled == enabled) {
			return;
		}
		config.enabled = enabled;
		if (!enabled && cache != null) {
			cache.clearAll("optimization turned off"); // сразу отдаём видеопамять атласа
		}
	}

	public static void setDebugInfoShown(final boolean shown) {
		config.showDebugInfo = shown;
	}

	/** Вызов при сбое запекания: дальше в этой сессии — только ваниль. */
	public static void markBroken(final String reason) {
		unavailableReason = reason;
		cache = null;
	}
}
