package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Icons;
import net.minecraft.resource.InputSupplier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The window part: the game window's title, icon and (Windows only) title bar. Applied on the first
 * tick and after every reload; title and title bar are checked again twice a second, since they
 * may use game values. Anything the layout doesn't set stays vanilla, and what CakeUI set is put
 * back when a layout without it is loaded.
 */
public final class WindowPart {
	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
	private static final int DWMWA_BORDER_COLOR = 34;
	private static final int DWMWA_CAPTION_COLOR = 35;
	private static final int DWMWA_TEXT_COLOR = 36;
	/** DWMWA_COLOR_DEFAULT: Windows' own colour. */
	private static final int DEFAULT_COLOR = 0xFFFFFFFF;

	private static boolean ready;
	private static int ticks;
	private static String vanillaTitle = "";
	private static String title;
	private static List<Path> iconsSet = List.of();
	/** Title bar values sent to Windows: dark, color, text, outline (null = never set). */
	private static Integer[] borderSet = new Integer[4];
	private static long dwmSetWindowAttribute = -1;

	private WindowPart() {
	}

	static String vanillaTitle() {
		return vanillaTitle;
	}

	/** The title for MinecraftClient.getWindowTitle: the layout's, or vanilla's (remembered for {window.title}). */
	public static String title(String vanilla) {
		vanillaTitle = vanilla;
		Node w = window();
		if (w == null || !w.has("title")) {
			return vanilla;
		}
		Renderer r = Ui.renderer();
		r.env.self = null;
		r.env.frame++; // evaluated outside drawing: computed variables must not reuse the last frame's value
		return r.word(w, "title", vanilla);
	}

	private static Node window() {
		Renderer r = Ui.renderer();
		return r == null ? null : r.layout.window;
	}

	/** After a reload (and once the window exists): icon, title and title bar. */
	static void apply() {
		if (!ready) {
			return;
		}
		Renderer r = Ui.renderer();
		List<Path> icons = r == null ? List.of() : r.layout.icons;
		if (!icons.equals(iconsSet)) {
			setIcons(icons);
		}
		refresh();
	}

	/** Every client tick: the first one makes the window ready; then a check twice a second. */
	static void tick(MinecraftClient client) {
		if (!ready) {
			ready = true;
			apply();
			return;
		}
		if (++ticks % 10 == 0) {
			refresh();
		}
	}

	private static void refresh() {
		MinecraftClient client = MinecraftClient.getInstance();
		Node w = window();
		String now = w != null && w.has("title") ? title(vanillaTitle) : null;
		if (!Objects.equals(now, title)) {
			title = now;
			client.updateWindowTitle();
		}
		applyBorder(w);
	}

	// ---- icon ----

	private static void setIcons(List<Path> icons) {
		MinecraftClient client = MinecraftClient.getInstance();
		int platform = GLFW.glfwGetPlatform();
		if (platform != GLFW.GLFW_PLATFORM_WIN32 && platform != GLFW.GLFW_PLATFORM_X11) {
			iconsSet = icons;
			return; // macOS and Wayland: no per-window icon
		}
		List<InputSupplier<InputStream>> sources = new ArrayList<>();
		try {
			if (icons.isEmpty()) {
				Icons vanilla = SharedConstants.getGameVersion().isStable() ? Icons.RELEASE : Icons.SNAPSHOT;
				sources.addAll(vanilla.getIcons(client.getDefaultResourcePack()));
			} else {
				for (Path p : icons) {
					sources.add(() -> Files.newInputStream(p));
				}
			}
			upload(client.getWindow().getHandle(), sources);
			iconsSet = icons;
		} catch (Exception e) {
			iconsSet = icons;
			CakeUI.LOGGER.warn("CakeUI: could not set the window icon", e);
			Renderer r = Ui.renderer();
			if (r != null && !icons.isEmpty()) {
				r.layout.problems.add("window.json5", 0, "the icon could not be set: " + e.getMessage());
			}
		}
	}

	/** Like vanilla's Window.setIcon, which only works while the game starts. */
	private static void upload(long handle, List<InputSupplier<InputStream>> sources) throws Exception {
		List<ByteBuffer> pixels = new ArrayList<>();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			GLFWImage.Buffer images = GLFWImage.malloc(sources.size(), stack);
			for (int i = 0; i < sources.size(); i++) {
				try (InputStream in = sources.get(i).get(); NativeImage image = NativeImage.read(in)) {
					ByteBuffer buffer = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
					pixels.add(buffer);
					buffer.asIntBuffer().put(image.copyPixelsRgba());
					images.position(i);
					images.width(image.getWidth());
					images.height(image.getHeight());
					images.pixels(buffer);
				}
			}
			GLFW.glfwSetWindowIcon(handle, images.position(0));
		} finally {
			pixels.forEach(MemoryUtil::memFree);
		}
	}

	// ---- title bar (Windows 10/11) ----

	private static void applyBorder(Node w) {
		if (Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS) {
			return;
		}
		Renderer r = Ui.renderer();
		Integer[] want = new Integer[4];
		if (w != null) {
			r.env.self = null;
			r.env.frame++;
			if (w.has("border.dark")) {
				want[0] = r.bool(w, "border.dark", false) ? 1 : 0;
			}
			String[] keys = {"border.color", "border.text", "border.outline"};
			for (int i = 0; i < 3; i++) {
				if (w.has(keys[i])) {
					want[i + 1] = colorRef(r.colorValue(w, keys[i], 0xFF000000));
				}
			}
		}
		int[] attrs = {DWMWA_USE_IMMERSIVE_DARK_MODE, DWMWA_CAPTION_COLOR, DWMWA_TEXT_COLOR, DWMWA_BORDER_COLOR};
		for (int i = 0; i < 4; i++) {
			if (want[i] != null && want[i].equals(borderSet[i])) {
				continue; // unchanged
			}
			if (want[i] == null && borderSet[i] == null) {
				continue; // never set, still not wanted: Windows' own
			}
			// wanted now, or set earlier and no longer wanted: back to Windows' default
			dwm(attrs[i], want[i] != null ? want[i] : i == 0 ? 0 : DEFAULT_COLOR);
			borderSet[i] = want[i];
		}
	}

	/** ARGB → Windows COLORREF (0x00BBGGRR). */
	private static int colorRef(int argb) {
		return ((argb & 0xFF) << 16) | (argb & 0xFF00) | ((argb >> 16) & 0xFF);
	}

	private static void dwm(int attribute, int value) {
		try {
			if (dwmSetWindowAttribute == -1) {
				SharedLibrary dwmapi = Library.loadNative(WindowPart.class, "org.lwjgl", "dwmapi");
				dwmSetWindowAttribute = dwmapi.getFunctionAddress("DwmSetWindowAttribute");
			}
			if (dwmSetWindowAttribute == 0) {
				return;
			}
			long hwnd = GLFWNativeWin32.glfwGetWin32Window(MinecraftClient.getInstance().getWindow().getHandle());
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer v = stack.ints(value);
				// fails quietly (an error code) on Windows versions without the attribute
				JNI.callPPI(hwnd, attribute, MemoryUtil.memAddress(v), 4, dwmSetWindowAttribute);
			}
		} catch (Throwable t) {
			dwmSetWindowAttribute = 0;
			CakeUI.LOGGER.warn("CakeUI: could not reach the Windows title bar", t);
		}
	}
}
