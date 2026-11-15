package com.aksdes.signum.cache;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Содержимое стороны таблички, по которому ячейки атласа переиспользуются: десять табличек «Сундук»
 * на складе занимают одну ячейку. Строится только при промахе, не каждый кадр.
 */
record TextKey(String text, List<Style> styles, DyeColor color, boolean glowing, boolean outline,
		int maxLineWidth, int lineHeight) {

	private static final char LINE_BREAK = '\n';
	private static final char STYLE_CHANGE = '\u0000';

	static TextKey of(final FormattedCharSequence[] lines, final DyeColor color, final boolean glowing,
			final boolean outline, final int maxLineWidth, final int lineHeight) {
		final StringBuilder text = new StringBuilder(64);
		final List<Style> styles = new ArrayList<>(4);
		final Style[] current = {null};
		for (FormattedCharSequence line : lines) {
			line.accept((index, style, codePoint) -> {
				if (style != current[0] && !style.equals(current[0])) {
					current[0] = style;
					styles.add(style);
					text.append(STYLE_CHANGE);
				}
				text.appendCodePoint(codePoint);
				return true;
			});
			text.append(LINE_BREAK);
		}
		return new TextKey(text.toString(), List.copyOf(styles), color, glowing, outline, maxLineWidth, lineHeight);
	}
}
