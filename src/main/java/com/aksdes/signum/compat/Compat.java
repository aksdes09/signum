package com.aksdes.signum.compat;

import com.aksdes.signum.Signum;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Совместимость с другими модами. Iris вызывается только через рефлексию — зависимости нет. */
public final class Compat {

	private static final long IRIS_POLL_NS = 500_000_000L;

	private static Object irisApi;
	private static Method isShaderPackInUse;
	private static boolean irisResolved;
	private static boolean shaderPackActive;
	private static long nextIrisPoll;

	private Compat() {
	}

	/** Причина, по которой Signum нельзя включать вообще, или null. */
	public static String findHardConflict() {
		final FabricLoader loader = FabricLoader.getInstance();
		if (Signum.config.disableWithIris && loader.isModLoaded("iris")) {
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

	/** true, если в Iris сейчас включён шейдерпак. Опрашивается не чаще двух раз в секунду. */
	public static boolean shaderPackActive(final long now) {
		if (!Signum.config.disableWithShaderPack) {
			return false;
		}
		if (now - nextIrisPoll < 0) {
			return shaderPackActive;
		}
		nextIrisPoll = now + IRIS_POLL_NS;
		if (!irisResolved) {
			irisResolved = true;
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					irisApi = api.getMethod("getInstance").invoke(null);
					isShaderPackInUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException | LinkageError e) {
					Signum.LOGGER.warn("Iris is installed but its API could not be reached; assuming a shader pack is always active", e);
					shaderPackActive = true;
					return true;
				}
			}
		}
		if (irisApi == null) {
			return shaderPackActive;
		}
		try {
			shaderPackActive = (Boolean) isShaderPackInUse.invoke(irisApi);
		} catch (ReflectiveOperationException | RuntimeException e) {
			shaderPackActive = true;
		}
		return shaderPackActive;
	}
}
