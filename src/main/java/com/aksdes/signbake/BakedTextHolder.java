package com.aksdes.signbake;

/**
 * Implemented by {@code SignText} through a mixin: every sign side carries its
 * own baked text, so the per-frame lookup is a single field read.
 */
public interface BakedTextHolder {

	BakedText signbake$get();

	void signbake$set(BakedText baked);

}
