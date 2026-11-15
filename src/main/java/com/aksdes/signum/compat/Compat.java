package com.aksdes.signum.compat;

import com.aksdes.signum.Signum;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Совместимость с другими модами. Iris вызывается только через рефлексию (см. {@link IrisBridge}). */
public final class Compat {

	private static final long IRIS_POLL_NS = 250_000_000L;

	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

	private static boolean shaderPackActive;
	private static long nextIrisPoll;

	private Compat() {
	}

	/** Причина, по которой Signum нельзя включать вообще, или null. */
	public static String findHardConflict() {
		final FabricLoader loader = FabricLoader.getInstance();
		if (Signum.config.disableWithIris && IRIS) {
			return "Iris is installed and disableWithIris=true";
		}
		if (loader.isModLoaded("immediatelyfast") && immediatelyFastBuffersSigns(loader.getConfigDir())) {
			return "ImmediatelyFast's experimental_sign_text_buffering is enabled (it does the same job; turn one of them off)";
		}
		return null;
	}

	private static boolean immediatelyFastBuffersSigns(final Path configDir) {
		final Path file = configDir.resolve("immediatelyfast.json");
		if (!Files.isRegularFile(file)) {
			return false;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final JsonElement root = JsonParser.parseReader(r);
			if (root instanceof JsonObject obj && obj.get("experimental_sign_text_buffering") instanceof JsonElement e) {
				return e.isJsonPrimitive() && e.getAsBoolean();
			}
		} catch (Exception e) {
			Signum.LOGGER.debug("Could not read {}", file, e);
		}
		return false;
	}

	/** true, если в Iris сейчас включён шейдерпак. Опрашивается не чаще четырёх раз в секунду. */
	public static boolean shaderPackActive(final long now) {
		if (!IRIS) {
			return false;
		}
		if (now - nextIrisPoll >= 0) {
			nextIrisPoll = now + IRIS_POLL_NS;
			shaderPackActive = IrisBridge.READY ? IrisBridge.isShaderPackInUse() : true;
		}
		return shaderPackActive;
	}

	/**
	 * true — этот вызов надо отдать ванили: шейдеры включены, а работать с ними нельзя
	 * (запрещено конфигом или Iris несовместимой версии), либо идёт теневой проход Iris
	 * (сам Iris в нём текст табличек вообще не рисует).
	 */
	public static boolean useVanilla(final long now) {
		if (!shaderPackActive(now)) {
			return false;
		}
		if (Signum.config.disableWithShaderPack || !IrisBridge.READY) {
			return true;
		}
		return IrisBridge.isRenderingShadowPass();
	}

	/**
	 * Вся работа Signum с GPU-ресурсами атласа (создание и очистка страниц, проекция, запекание)
	 * идёт внутри этой пары вызовов: при шейдерах Iris на это время отключает свои подмены.
	 */
	public static void enterVanillaRendering() {
		if (IRIS && IrisBridge.READY) {
			IrisBridge.enterVanilla();
		}
	}

	public static void exitVanillaRendering() {
		if (IRIS && IrisBridge.READY) {
			IrisBridge.exitVanilla();
		}
	}

	/** Для строки F3. */
	public static String shaderStatus() {
		if (!IRIS) {
			return "";
		}
		if (!shaderPackActive) {
			return ", shaders off";
		}
		return Signum.config.disableWithShaderPack || !IrisBridge.READY ? ", shaders on (vanilla fallback)" : ", shaders on";
	}
}
