package com.aksdes.signum.cache;

/**
 * Хранится прямо в объекте SignText (он неизменяем: правка таблички создаёт новый SignText),
 * поэтому в горячем пути не нужно ни хешировать текст, ни искать в map.
 */
public final class SignHolder {

	static final byte UNKNOWN = 0;
	static final byte CACHEABLE = 1;
	/** Обфусцированный (§k) или пустой текст — всегда ванильный путь. */
	static final byte VANILLA = 2;

	int epoch;
	byte status = UNKNOWN;
	boolean filtered;
	int maxWidth;
	Cell plain;
	Cell outline;
	/** Ключи кешируются, чтобы при ожидании запекания (лимит/переполнение) не строить их каждый кадр. */
	TextKey plainKey;
	TextKey outlineKey;

	void reset(final int epoch, final boolean filtered) {
		this.epoch = epoch;
		this.status = UNKNOWN;
		this.filtered = filtered;
		this.maxWidth = 0;
		this.plain = null;
		this.outline = null;
		this.plainKey = null;
		this.outlineKey = null;
	}
}
