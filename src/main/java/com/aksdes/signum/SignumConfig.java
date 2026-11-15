package com.aksdes.signum;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Простой конфиг в config/signum.properties — без сторонних библиотек. */
public final class SignumConfig {

	/** 2: появилась поддержка шейдеров Iris, disableWithShaderPack по умолчанию стал false. */
	private static final int CONFIG_VERSION = 2;

	public boolean enabled = true;
	/** Сколько пикселей атласа на один пиксель шрифта. 2 — без «сплющивания» строк нечётной ширины. */
	public int textureScale = 2;
	/** Сторона одной страницы атласа (степень двойки). */
	public int atlasSize = 1024;
	/** Максимум страниц атласа; новые создаются только по мере заполнения. */
	public int maxAtlasPages = 16;
	/** Через сколько секунд невидимая табличка освобождает свою ячейку. */
	public int idleSeconds = 30;
	/** Сколько новых ячеек можно запечь в секунду (защита от фризов при входе в мир). */
	public int bakesPerSecond = 480;
	/** Сколько ячеек можно запечь за один кадр при всплеске. */
	public int bakeBurst = 24;
	/** Не трогать таблички, пока в Iris включён шейдерпак (по умолчанию Signum работает и с шейдерами). */
	public boolean disableWithShaderPack = false;
	/** Полностью выключаться при наличии Iris (на случай проблем даже без шейдеров). */
	public boolean disableWithIris = false;

	public static SignumConfig load(final Path file) {
		final SignumConfig c = new SignumConfig();
		final Properties p = new Properties();
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException | IllegalArgumentException e) {
				Signum.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		c.enabled = bool(p, "enabled", c.enabled);
		c.textureScale = clamp(integer(p, "textureScale", c.textureScale), 1, 4);
		c.atlasSize = powerOfTwo(clamp(integer(p, "atlasSize", c.atlasSize), 512, 8192));
		c.maxAtlasPages = clamp(integer(p, "maxAtlasPages", c.maxAtlasPages), 1, 64);
		c.idleSeconds = clamp(integer(p, "idleSeconds", c.idleSeconds), 1, 3600);
		c.bakesPerSecond = clamp(integer(p, "bakesPerSecond", c.bakesPerSecond), 1, 100_000);
		c.bakeBurst = clamp(integer(p, "bakeBurst", c.bakeBurst), 1, 10_000);
		final int fileVersion = integer(p, "configVersion", 1);
		if (fileVersion >= 2) {
			// В версии 1 этот ключ был true по умолчанию только потому, что шейдеры не поддерживались.
			c.disableWithShaderPack = bool(p, "disableWithShaderPack", c.disableWithShaderPack);
		}
		c.disableWithIris = bool(p, "disableWithIris", c.disableWithIris);
		c.save(file);
		return c;
	}

	private void save(final Path file) {
		final String text = """
				# Signum - sign text atlas
				# Restart the game after editing.
				configVersion=%d

				# Master switch.
				enabled=%s

				# Atlas pixels per font pixel (1..4). Even values (2, 4) keep lines with odd width pixel-exact.
				# 1 = least VRAM, 2 = recommended, 4 = sharpest with smooth/TTF fonts.
				textureScale=%d

				# Side of one atlas page in pixels (power of two). One page costs size*size*8 bytes of VRAM
				# (color + depth): 1024 -> 8 MiB, 2048 -> 32 MiB.
				atlasSize=%d

				# Upper limit of atlas pages. Pages are created only when the previous ones are full.
				maxAtlasPages=%d

				# A sign side that was not drawn for this many seconds releases its atlas cell.
				idleSeconds=%d

				# Limit of new cells baked per second and per burst; signs over the limit are drawn
				# the vanilla way for a few frames. Prevents a stutter when a world with many signs loads.
				bakesPerSecond=%d
				bakeBurst=%d

				# Iris shader packs are supported: atlas cells are baked with vanilla shaders and the finished
				# quads go through the shader pack like normal sign text. Set to true to draw sign text the
				# vanilla way while a shader pack is active.
				disableWithShaderPack=%s

				# Turn Signum off completely whenever Iris is installed.
				disableWithIris=%s
				""".formatted(CONFIG_VERSION, this.enabled, this.textureScale, this.atlasSize, this.maxAtlasPages, this.idleSeconds,
				this.bakesPerSecond, this.bakeBurst, this.disableWithShaderPack, this.disableWithIris);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				w.write(text);
			}
		} catch (IOException e) {
			Signum.LOGGER.warn("Could not write {}", file, e);
		}
	}

	private static boolean bool(final Properties p, final String key, final boolean def) {
		final String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v.trim());
	}

	private static int integer(final Properties p, final String key, final int def) {
		final String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int powerOfTwo(final int v) {
		return Integer.highestOneBit(v - 1) << 1;
	}
}
