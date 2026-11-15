package com.aksdes.signum;

import com.aksdes.signum.cache.SignTextCache;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public final class SignumDebugEntry implements DebugScreenEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(Signum.MOD_ID, "atlas");

	@Override
	public void display(final DebugScreenDisplayer displayer, final Level level, final LevelChunk clientChunk, final LevelChunk serverChunk) {
		if (!Signum.config.showDebugInfo) {
			return;
		}
		final SignTextCache cache = Signum.cache;
		final String line;
		if (cache == null) {
			line = "Signum: unavailable" + (Signum.unavailableReason != null ? " (" + Signum.unavailableReason + ")" : "");
		} else if (!Signum.config.enabled) {
			line = "Signum: optimization off";
		} else {
			line = cache.debugLine();
		}
		displayer.addToGroup(ID, List.of(line));
	}

	@Override
	public boolean isAllowed(final boolean reducedDebugInfo) {
		return true;
	}
}
