package io.github.harrythecake.cakewiki;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.internal.MCEFDownloadListener;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.harrythecake.cakewiki.net.HostRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

/**
 * MCEF's in-game browser at the blocked page, for the player to pass Cloudflare's check themselves; Done sends that
 * host's requests through this browser ({@link McefFetcher}) and the reader retries. Esc or Cancel goes back. Every
 * mouse and key input over the browser goes to it. Only loaded when MCEF is installed ({@link Mcef}).
 */
final class McefVerifyScreen extends Screen {
	private static final int TOP = 26;
	private static final int SIDE = 6;

	private final Screen parent;
	private final McefFetcher fetcher;
	private final HostRouter router;
	private final Runnable after;

	private McefVerifyScreen(Screen parent, McefFetcher fetcher, HostRouter router, Runnable after) {
		super(Component.translatable("cakewiki.mcef.title", fetcher.host));
		this.parent = parent;
		this.fetcher = fetcher;
		this.router = router;
		this.after = after;
	}

	/** False (and a chat-free toast in the parent's status) when MCEF hasn't set up its browser yet. */
	static boolean open(Screen parent, String url, HostRouter router, Runnable after) {
		if (!MCEF.isInitialized()) {
			return false; // its first start downloads Chromium; the caller says so
		}
		String host = URI.create(url).getHost();
		McefFetcher f = McefFetcher.forHost(host, url);
		if (!f.onHost()) {
			f.browser.loadURL(url);
		}
		Minecraft.getInstance().setScreen(new McefVerifyScreen(parent, f, router, after));
		return true;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("cakewiki.mcef.done"), b -> done())
				.bounds(width - SIDE - 124, 3, 60, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(width - SIDE - 60, 3, 60, 20).build());
		resizeBrowser();
		fetcher.browser.setFocus(true);
	}

	private void resizeBrowser() {
		double scale = Minecraft.getInstance().getWindow().getGuiScale();
		fetcher.browser.resize((int) Math.max(1, (width - 2 * SIDE) * scale), (int) Math.max(1, (height - TOP - SIDE) * scale));
	}

	private void done() {
		router.route(fetcher.host, fetcher);
		Minecraft.getInstance().setScreen(parent);
		after.run();
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xFF101114);
		ctx.drawString(font, font.plainSubstrByWidth(title.getString(), width - 2 * SIDE - 132), SIDE, 9, 0xFFFFFFFF);
		int x0 = SIDE, y0 = TOP, x1 = width - SIDE, y1 = height - SIDE;
		ctx.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFFFFFFF);
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, fetcher.browser.getRenderer().getTextureID());
		Matrix4f m = ctx.pose().last().pose();
		BufferBuilder buf = Tesselator.getInstance().getBuilder();
		buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		buf.vertex(m, x0, y1, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
		buf.vertex(m, x1, y1, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
		buf.vertex(m, x1, y0, 0).uv(1, 0).color(255, 255, 255, 255).endVertex();
		buf.vertex(m, x0, y0, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
		Tesselator.getInstance().end();
		RenderSystem.enableDepthTest();
		super.render(ctx, mouseX, mouseY, delta); // the buttons
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		// the browser covers the screen
	}

	private boolean over(double mx, double my) {
		return mx >= SIDE && mx < width - SIDE && my >= TOP && my < height - SIDE;
	}

	private int bx(double mx) {
		return (int) ((mx - SIDE) * Minecraft.getInstance().getWindow().getGuiScale());
	}

	private int by(double my) {
		return (int) ((my - TOP) * Minecraft.getInstance().getWindow().getGuiScale());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (over(mx, my)) {
			fetcher.browser.setFocus(true);
			fetcher.browser.sendMousePress(bx(mx), by(my), button);
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		fetcher.browser.sendMouseRelease(bx(mx), by(my), button);
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public void mouseMoved(double mx, double my) {
		fetcher.browser.sendMouseMove(bx(mx), by(my));
		super.mouseMoved(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (over(mx, my)) {
			fetcher.browser.sendMouseWheel(bx(mx), by(my), vertical, 0);
			return true;
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		fetcher.browser.sendKeyPress(key, scancode, modifiers);
		return true;
	}

	@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		fetcher.browser.sendKeyRelease(key, scancode, modifiers);
		return true;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		fetcher.browser.sendKeyTyped(c, modifiers);
		return true;
	}

	/** Development: Done, as the player presses it ({@link McefTest}). */
	void doneForTest() {
		done();
	}

	/** Development: whether the browser has reached the page's host. */
	boolean onHostForTest() {
		return fetcher.onHost();
	}

	static boolean ready() {
		return MCEF.isInitialized();
	}

	/**
	 * MCEF's own setup state (its download listener and download site), for the blocked screen. Its listener is in an
	 * {@code internal} package, so another MCEF version may not have it: then {@link McefSetup#UNKNOWN}.
	 */
	static McefSetup setup() {
		try {
			MCEFDownloadListener l = MCEFDownloadListener.INSTANCE;
			String mirror = null;
			try {
				mirror = MCEF.getSettings().getDownloadMirror();
			} catch (RuntimeException | LinkageError e) {
				// keep the default host
			}
			return McefSetup.of(MCEF.isInitialized(), l.isDone(), l.isFailed(), l.getProgress(), mirror);
		} catch (RuntimeException | LinkageError e) {
			return MCEF.isInitialized() ? McefSetup.of(true, true, false, 1f, null) : McefSetup.UNKNOWN;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
