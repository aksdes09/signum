package com.aksdes.signum.atlas;

import java.util.ArrayList;

/**
 * Упаковщик «полками». Все ячейки табличек почти одной высоты (стоячая/висячая), поэтому полки
 * по высоте ячейки дают плотную упаковку, а освобождение — это просто возврат отрезка на полку
 * со слиянием соседних отрезков. Чистая логика, без OpenGL/Vulkan — тестируется отдельно.
 */
public final class ShelfPacker {

	private final int width;
	private final int height;
	private final ArrayList<Shelf> shelves = new ArrayList<>();
	private int nextY;
	private int regionCount;

	public ShelfPacker(final int width, final int height) {
		this.width = width;
		this.height = height;
	}

	/** Свободное место под ячейку w×h или null. */
	public Region allocate(final int w, final int h) {
		if (w <= 0 || h <= 0 || w > this.width || h > this.height) {
			return null;
		}
		final int slack = Math.max(4, h / 4);
		Shelf best = null;
		int bestX = -1;
		for (Shelf shelf : this.shelves) {
			final boolean fits = shelf.height >= h && (shelf.height - h <= slack || shelf.isEmpty());
			if (!fits || (best != null && shelf.height >= best.height)) {
				continue;
			}
			final int x = shelf.findSpan(w);
			if (x >= 0) {
				best = shelf;
				bestX = x;
			}
		}
		if (best == null) {
			if (this.nextY + h > this.height) {
				return null;
			}
			best = new Shelf(this.nextY, h, this.width);
			this.shelves.add(best);
			this.nextY += h;
			bestX = 0;
		}
		best.take(bestX, w);
		this.regionCount++;
		return new Region(this, best, bestX, best.y, w, h);
	}

	void free(final Region region) {
		region.shelf.give(region.x, region.width);
		this.regionCount--;
		// Пустые полки сверху стопки возвращают высоту целиком — туда влезет полка другой высоты.
		while (!this.shelves.isEmpty()) {
			final Shelf last = this.shelves.get(this.shelves.size() - 1);
			if (!last.isEmpty()) {
				break;
			}
			this.shelves.remove(this.shelves.size() - 1);
			this.nextY = last.y;
		}
	}

	public int regionCount() {
		return this.regionCount;
	}

	public boolean isEmpty() {
		return this.regionCount == 0;
	}

	/** Сброс без освобождения отдельных регионов (старые Region после этого недействительны). */
	public void reset() {
		this.shelves.clear();
		this.nextY = 0;
		this.regionCount = 0;
	}

	public static final class Region {
		private final ShelfPacker owner;
		private final Shelf shelf;
		public final int x;
		public final int y;
		public final int width;
		public final int height;
		private boolean freed;

		Region(final ShelfPacker owner, final Shelf shelf, final int x, final int y, final int width, final int height) {
			this.owner = owner;
			this.shelf = shelf;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public void free() {
			if (!this.freed) {
				this.freed = true;
				this.owner.free(this);
			}
		}
	}

	/** Полка: отсортированный список свободных отрезков [start, start+len). */
	static final class Shelf {
		final int y;
		final int height;
		private final int width;
		private int[] starts = new int[4];
		private int[] lengths = new int[4];
		private int spans;

		Shelf(final int y, final int height, final int width) {
			this.y = y;
			this.height = height;
			this.width = width;
			this.starts[0] = 0;
			this.lengths[0] = width;
			this.spans = 1;
		}

		boolean isEmpty() {
			return this.spans == 1 && this.starts[0] == 0 && this.lengths[0] == this.width;
		}

		/** Начало лучшего (самого узкого подходящего) свободного отрезка или -1. */
		int findSpan(final int w) {
			int best = -1;
			int bestLen = Integer.MAX_VALUE;
			for (int i = 0; i < this.spans; i++) {
				final int len = this.lengths[i];
				if (len >= w && len < bestLen) {
					best = this.starts[i];
					bestLen = len;
					if (len == w) {
						break;
					}
				}
			}
			return best;
		}

		void take(final int x, final int w) {
			for (int i = 0; i < this.spans; i++) {
				if (this.starts[i] == x) {
					if (this.lengths[i] == w) {
						remove(i);
					} else {
						this.starts[i] += w;
						this.lengths[i] -= w;
					}
					return;
				}
			}
			throw new IllegalStateException("span " + x + " not free");
		}

		void give(final int x, final int w) {
			int i = 0;
			while (i < this.spans && this.starts[i] < x) {
				i++;
			}
			final boolean joinPrev = i > 0 && this.starts[i - 1] + this.lengths[i - 1] == x;
			final boolean joinNext = i < this.spans && x + w == this.starts[i];
			if (joinPrev && joinNext) {
				this.lengths[i - 1] += w + this.lengths[i];
				remove(i);
			} else if (joinPrev) {
				this.lengths[i - 1] += w;
			} else if (joinNext) {
				this.starts[i] = x;
				this.lengths[i] += w;
			} else {
				insert(i, x, w);
			}
		}

		private void remove(final int i) {
			System.arraycopy(this.starts, i + 1, this.starts, i, this.spans - i - 1);
			System.arraycopy(this.lengths, i + 1, this.lengths, i, this.spans - i - 1);
			this.spans--;
		}

		private void insert(final int i, final int x, final int w) {
			if (this.spans == this.starts.length) {
				this.starts = java.util.Arrays.copyOf(this.starts, this.spans * 2);
				this.lengths = java.util.Arrays.copyOf(this.lengths, this.spans * 2);
			}
			System.arraycopy(this.starts, i, this.starts, i + 1, this.spans - i);
			System.arraycopy(this.lengths, i, this.lengths, i + 1, this.spans - i);
			this.starts[i] = x;
			this.lengths[i] = w;
			this.spans++;
		}
	}
}
