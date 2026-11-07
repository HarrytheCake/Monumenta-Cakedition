package io.github.harrythecake.cakechat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * CakeUI's HUD editor (CakeUI 3+, optional): each chat window is a "movable" there, so it can be moved and
 * resized in the editor while CakeChat keeps storing its place in config.json; and the chat's "Edit HUD" button
 * opens the editor. Reached through reflection, so CakeChat needs CakeUI neither to build nor to run.
 */
final class CakeUIBridge {
	private static final String API = "io.github.harrythecake.cakeui.api.";
	private static boolean tried;
	@Nullable
	private static Object api;
	@Nullable
	private static Method openEditor;

	private CakeUIBridge() {
	}

	/** True once CakeUI 3+ took CakeChat's windows. */
	static boolean available() {
		return openEditor != null;
	}

	/** Once, at the first client tick (every mod is loaded by then). */
	static void init() {
		if (tried) {
			return;
		}
		tried = true;
		try {
			Class<?> apiClass = Class.forName(API + "CakeUIApi");
			if (apiClass.getField("VERSION").getInt(null) < 3) {
				return;
			}
			api = apiClass.getMethod("get").invoke(null);
			Class<?> sourceClass = Class.forName(API + "MovableSource");
			Class<?> movableClass = Class.forName(API + "Movable");
			Object source = Proxy.newProxyInstance(sourceClass.getClassLoader(), new Class<?>[] {sourceClass}, (proxy, m, args) -> switch (m.getName()) {
				case "movables" -> movables(movableClass);
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> "CakeChat windows";
			});
			apiClass.getMethod("addMovables", String.class, sourceClass).invoke(api, CakeChat.MOD_ID, source);
			openEditor = apiClass.getMethod("openHudEditor", Screen.class);
			CakeChat.LOGGER.info("CakeChat: chat windows can be moved in CakeUI's HUD editor");
		} catch (ClassNotFoundException e) {
			// no CakeUI: nothing to do
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			CakeChat.LOGGER.warn("CakeChat: could not join CakeUI's HUD editor: {}", e.toString());
		}
	}

	/** Opens CakeUI's HUD editor; false without it. */
	static boolean openEditor(@Nullable Screen parent) {
		if (openEditor == null) {
			return false;
		}
		try {
			return (Boolean) openEditor.invoke(api, parent);
		} catch (ReflectiveOperationException | RuntimeException e) {
			CakeChat.LOGGER.warn("CakeChat: could not open the HUD editor: {}", e.toString());
			return false;
		}
	}

	/** One movable per chat window. */
	private static List<Object> movables(Class<?> movableClass) {
		List<Object> out = new ArrayList<>();
		int n = CakeChat.windows().views().size();
		for (int i = 0; i < n; i++) {
			int index = i;
			out.add(Proxy.newProxyInstance(movableClass.getClassLoader(), new Class<?>[] {movableClass}, (proxy, m, args) -> switch (m.getName()) {
				case "id" -> "window" + (index + 1);
				case "label" -> Component.translatable(index == 0 ? "cakechat.movable.main" : "cakechat.movable.window", index + 1);
				case "box" -> box(index);
				case "place" -> {
					place(index, (Double) args[0], (Double) args[1], (Double) args[2], (Double) args[3], (Integer) args[5]);
					yield null;
				}
				case "resizable" -> true;
				case "save" -> {
					CakeChat.saveConfigFile();
					yield null;
				}
				case "cancel" -> null;
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> "CakeChat window " + (index + 1);
			}));
		}
		return out;
	}

	@Nullable
	private static double[] box(int index) {
		List<ChatView> views = CakeChat.windows().views();
		if (index >= views.size()) {
			return null;
		}
		ChatView v = views.get(index);
		return new double[] {v.boxX(), v.boxTop(), v.boxWidth(), v.boxHeight(true)};
	}

	private static void place(int index, double x, double y, double width, double height, int screenHeight) {
		List<ChatView> views = CakeChat.windows().views();
		if (index >= views.size()) {
			return;
		}
		ChatView v = views.get(index);
		v.resize((int) Math.round(width), (int) Math.round(height));
		v.move((int) Math.round(x), screenHeight - (int) Math.round(y + height));
	}
}
