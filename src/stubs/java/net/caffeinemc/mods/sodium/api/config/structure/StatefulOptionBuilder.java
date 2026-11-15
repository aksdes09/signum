package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Заглушка для компиляции (не попадает в jar). */
public interface StatefulOptionBuilder<V> extends OptionBuilder {
	@Override
	StatefulOptionBuilder<V> setName(Component name);

	@Override
	StatefulOptionBuilder<V> setTooltip(Component tooltip);

	StatefulOptionBuilder<V> setStorageHandler(StorageEventHandler storage);

	StatefulOptionBuilder<V> setDefaultValue(V value);

	StatefulOptionBuilder<V> setBinding(Consumer<V> save, Supplier<V> load);
}
