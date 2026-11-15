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
	/** null, пока клиент не инициализирован или мод выключен. */
	public static SignTextCache cache;

	private static boolean started;

	@Override
	public void onInitializeClient() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		config = SignumConfig.load(FabricLoader.getInstance().getConfigDir().resolve("signum.properties"));
		if (!config.enabled) {
			LOGGER.info("Signum {} is disabled in config/signum.properties", version);
			return;
		}
		final String conflict = Compat.findHardConflict();
		if (conflict != null) {
			LOGGER.warn("Signum {} disabled: {}", version, conflict);
			config.enabled = false;
		}
	}

	/** Вызывается в конце конструктора Minecraft (render thread, устройство уже создано). */
	public static void onClientStarted() {
		if (started) {
			return;
		}
		started = true;
		if (!config.enabled) {
			return;
		}
		cache = new SignTextCache(config);
		final ResourceManagerReloadListener listener = manager -> {
			if (cache != null) {
				cache.clearAll("resource reload");
			}
		};
		if (Minecraft.getInstance().getResourceManager() instanceof ReloadableResourceManager reloadable) {
			reloadable.registerReloadListener(listener);
		}
		LOGGER.info("Signum {} active: atlas {}x{} px, text scale x{}, up to {} page(s)",
				version, config.atlasSize, config.atlasSize, config.textureScale, config.maxAtlasPages);
	}

	/** Смена мира/выход в меню: освобождаем всю видеопамять атласа. */
	public static void onLevelChange() {
		if (cache != null) {
			cache.clearAll("level change");
		}
	}

}
