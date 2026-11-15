package com.aksdes.signum.compat;

import com.aksdes.signum.Signum;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Мост к внутренностям Iris (только рефлексия — Iris не нужен для сборки и запуска).
 *
 * <p>Пока рисуется мир с шейдерпаком, Iris:
 * <ul>
 *   <li>подменяет ванильные шейдеры своими gbuffers-программами (если не выставлен {@code ImmediateState.bypass});</li>
 *   <li>расширяет форматы вершин (если {@code ImmediateState.isRenderingLevel});</li>
 *   <li>переворачивает буфер глубины: очистка, сравнение и проекции (тоже по {@code isRenderingLevel});</li>
 *   <li>заворачивает рендер-типы глифов в «block entity»-обёртку (по {@code isRenderingBEs}).</li>
 * </ul>
 * Для запекания ячейки атласа нужна чистая ваниль, поэтому на время запекания эти флаги временно
 * сбрасываются — ровно так же Iris сам поступает при отрисовке GUI ({@code MixinGuiRenderer}).
 * Готовый квад выводится уже при восстановленных флагах и проходит через шейдерпак как обычный текст табличек.
 */
final class IrisBridge {

	static final boolean READY;

	private static final MethodHandle IS_PACK_IN_USE;     // ()boolean
	private static final MethodHandle IS_SHADOW_PASS;     // ()boolean
	private static final MethodHandle GET_BYPASS;         // ()boolean
	private static final MethodHandle SET_BYPASS;         // (boolean)void
	private static final MethodHandle GET_LEVEL;
	private static final MethodHandle SET_LEVEL;
	private static final MethodHandle GET_BES;
	private static final MethodHandle SET_BES;
	private static final ThreadLocal<Boolean> SKIP_EXTENSION;
	private static final MethodHandle GET_ENTITY;         // ()int
	private static final MethodHandle SET_ENTITY;         // (int)void
	private static final MethodHandle GET_BLOCK_ENTITY;
	private static final MethodHandle SET_BLOCK_ENTITY;
	private static final MethodHandle GET_ITEM;
	private static final MethodHandle SET_ITEM;

	static {
		MethodHandle packInUse = null, shadowPass = null;
		MethodHandle getBypass = null, setBypass = null, getLevel = null, setLevel = null, getBes = null, setBes = null;
		MethodHandle getEntity = null, setEntity = null, getBe = null, setBe = null, getItem = null, setItem = null;
		ThreadLocal<Boolean> skipExtension = null;
		boolean ready = false;
		try {
			final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object apiInstance = api.getMethod("getInstance").invoke(null);
			packInUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(apiInstance);
			shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(apiInstance);

			final Class<?> state = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
			getBypass = lookup.findStaticGetter(state, "bypass", boolean.class);
			setBypass = lookup.findStaticSetter(state, "bypass", boolean.class);
			getLevel = lookup.findStaticGetter(state, "isRenderingLevel", boolean.class);
			setLevel = lookup.findStaticSetter(state, "isRenderingLevel", boolean.class);
			getBes = lookup.findStaticGetter(state, "isRenderingBEs", boolean.class);
			setBes = lookup.findStaticSetter(state, "isRenderingBEs", boolean.class);
			@SuppressWarnings("unchecked")
			final ThreadLocal<Boolean> skip = (ThreadLocal<Boolean>) lookup.findStaticGetter(state, "skipExtension", ThreadLocal.class).invoke();
			skipExtension = skip;

			final Class<?> captured = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
			final Object capturedInstance = lookup.findStaticGetter(captured, "INSTANCE", captured).invoke();
			final MethodType getter = MethodType.methodType(int.class);
			final MethodType setter = MethodType.methodType(void.class, int.class);
			getEntity = lookup.findVirtual(captured, "getCurrentRenderedEntity", getter).bindTo(capturedInstance);
			setEntity = lookup.findVirtual(captured, "setCurrentEntity", setter).bindTo(capturedInstance);
			getBe = lookup.findVirtual(captured, "getCurrentRenderedBlockEntity", getter).bindTo(capturedInstance);
			setBe = lookup.findVirtual(captured, "setCurrentBlockEntity", setter).bindTo(capturedInstance);
			getItem = lookup.findVirtual(captured, "getCurrentRenderedItem", getter).bindTo(capturedInstance);
			setItem = lookup.findVirtual(captured, "setCurrentRenderedItem", setter).bindTo(capturedInstance);
			ready = true;
		} catch (Throwable t) {
			Signum.LOGGER.warn("Signum could not hook into this Iris version; sign text will be drawn the vanilla way while a shader pack is active", t);
		}
		READY = ready;
		IS_PACK_IN_USE = packInUse;
		IS_SHADOW_PASS = shadowPass;
		GET_BYPASS = getBypass;
		SET_BYPASS = setBypass;
		GET_LEVEL = getLevel;
		SET_LEVEL = setLevel;
		GET_BES = getBes;
		SET_BES = setBes;
		SKIP_EXTENSION = skipExtension;
		GET_ENTITY = getEntity;
		SET_ENTITY = setEntity;
		GET_BLOCK_ENTITY = getBe;
		SET_BLOCK_ENTITY = setBe;
		GET_ITEM = getItem;
		SET_ITEM = setItem;
	}

	// Сохранённое состояние Iris на время запекания (render thread, без вложенности).
	private static int depth;
	private static boolean savedBypass;
	private static boolean savedLevel;
	private static boolean savedBes;
	private static boolean savedSkip;
	private static int savedEntity;
	private static int savedBlockEntity;
	private static int savedItem;

	private IrisBridge() {
	}

	static boolean isShaderPackInUse() {
		try {
			return (boolean) IS_PACK_IN_USE.invokeExact();
		} catch (Throwable t) {
			return true;
		}
	}

	static boolean isRenderingShadowPass() {
		try {
			return (boolean) IS_SHADOW_PASS.invokeExact();
		} catch (Throwable t) {
			return true;
		}
	}

	/** Переводит Iris в «ванильный» режим. Обязательно парный вызов {@link #exitVanilla()} в finally. */
	static void enterVanilla() {
		if (depth++ > 0) {
			return;
		}
		try {
			savedBypass = (boolean) GET_BYPASS.invokeExact();
			savedLevel = (boolean) GET_LEVEL.invokeExact();
			savedBes = (boolean) GET_BES.invokeExact();
			savedSkip = SKIP_EXTENSION.get();
			savedEntity = (int) GET_ENTITY.invokeExact();
			savedBlockEntity = (int) GET_BLOCK_ENTITY.invokeExact();
			savedItem = (int) GET_ITEM.invokeExact();

			SET_BYPASS.invokeExact(true);   // ванильные программы вместо gbuffers
			SET_LEVEL.invokeExact(false);   // ванильный формат вершин и reverse-Z глубина
			SET_BES.invokeExact(false);     // без block-entity обёртки рендер-типов
			SKIP_EXTENSION.set(Boolean.TRUE);
		} catch (Throwable t) {
			try {
				exitVanilla(); // вернуть то, что успели поменять
			} catch (RuntimeException ignored) {
				depth = 0;
			}
			throw new IllegalStateException("Could not switch Iris to vanilla rendering", t);
		}
	}

	static void exitVanilla() {
		if (--depth > 0) {
			return;
		}
		try {
			SET_BYPASS.invokeExact(savedBypass);
			SET_LEVEL.invokeExact(savedLevel);
			SET_BES.invokeExact(savedBes);
			SKIP_EXTENSION.set(savedSkip);
			// Фичерендереры Iris обнуляют эти id после каждой группы — возвращаем id текущей таблички,
			// чтобы шейдерпак получил верный blockEntityId для нашего квада.
			SET_ENTITY.invokeExact(savedEntity);
			SET_BLOCK_ENTITY.invokeExact(savedBlockEntity);
			SET_ITEM.invokeExact(savedItem);
		} catch (Throwable t) {
			throw new IllegalStateException("Could not restore Iris rendering state", t);
		}
	}
}
