package com.aksdes.signum.cache;

import com.aksdes.signum.atlas.AtlasPage;
import com.aksdes.signum.atlas.ShelfPacker;

/** Запечённый текст одной стороны таблички: место в атласе + готовые UV и размер квада. */
public final class Cell {

	final TextKey key;
	final AtlasPage page;
	final ShelfPacker.Region region;
	final float u0;
	final float u1;
	final float v0;
	final float v1;
	/** Половина ширины/высоты квада в единицах текста (1 = пиксель шрифта). */
	final float halfWidth;
	final float halfHeight;
	long lastUsed;
	/** false — ячейка освобождена; ссылки из SignText на неё надо перевыбрать. */
	boolean alive = true;

	Cell(final TextKey key, final AtlasPage page, final ShelfPacker.Region region, final int scale, final long now) {
		this.key = key;
		this.page = page;
		this.region = region;
		final float size = page.size;
		this.u0 = region.x / size;
		this.u1 = (region.x + region.width) / size;
		this.v0 = 1F - region.y / size;
		this.v1 = 1F - (region.y + region.height) / size;
		this.halfWidth = region.width / (2F * scale);
		this.halfHeight = region.height / (2F * scale);
		this.lastUsed = now;
	}

	void release() {
		if (this.alive) {
			this.alive = false;
			this.region.free();
		}
	}
}
