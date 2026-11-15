package com.aksdes.signum.mixin;

import com.aksdes.signum.SignumDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/** Строка «Signum: …» на экране F3 (во всех профилях отладочного экрана). */
@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {

	@Shadow
	@Final
	@Mutable
	public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

	@Shadow
	private static Identifier register(final Identifier id, final DebugScreenEntry entry) {
		throw new AssertionError();
	}

	@Inject(method = "<clinit>", at = @At("RETURN"), require = 0)
	private static void signum$addEntry(final CallbackInfo ci) {
		final Identifier id = register(SignumDebugEntry.ID, new SignumDebugEntry());
		final Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles = new HashMap<>();
		for (Map.Entry<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> e : PROFILES.entrySet()) {
			final Map<Identifier, DebugScreenEntryStatus> entries = new HashMap<>(e.getValue());
			entries.put(id, DebugScreenEntryStatus.IN_OVERLAY);
			profiles.put(e.getKey(), entries);
		}
		PROFILES = profiles;
	}
}
