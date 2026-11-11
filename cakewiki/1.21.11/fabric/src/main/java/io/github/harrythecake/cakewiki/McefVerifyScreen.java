package io.github.harrythecake.cakewiki;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.harrythecake.cakewiki.net.HostRouter;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

/**
 * MCEF Modern's in-game browser at the blocked page, for the player to pass Cloudflare's check themselves; Done sends
 * that host's requests through this browser ({@link McefFetcher}) and the reader retries. Esc or Cancel goes back.
 * Every mouse and key input over the browser goes to it. Only loaded when MCEF Modern is installed ({@link Mcef}).
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

	static boolean open(MCEFApi api, Screen parent, String url, HostRouter router, Runnable after) {
		String host = URI.create(url).getHost();
		McefFetcher f = McefFetcher.forHost(api, host, url);
		if (!f.onHost()) {
			f.navigate(url);
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
		if (!fetcher.textureRegistered) {
			Minecraft.getInstance().getTextureManager().register(fetcher.texture, new BrowserTexture(fetcher.browser));
			fetcher.textureRegistered = true;
		}
	}

	@Override
	public void tick() {
		fetcher.tick();
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
		if (fetcher.browser.getTextureView() != null) {
			ctx.blit(fetcher.texture, x0, y0, x1, y1, 0f, 1f, 0f, 1f);
		}
		if (over(mouseX, mouseY)) {
			ctx.requestCursor(fetcher.browser.getCursorType());
		}
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

	/** The event at the browser's own pixel position. */
	private MouseButtonEvent inBrowser(MouseButtonEvent e) {
		return new MouseButtonEvent(bx(e.x()), by(e.y()), e.buttonInfo());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
		if (over(e.x(), e.y())) {
			fetcher.browser.setFocus(true);
			fetcher.browser.onMouseClicked(inBrowser(e), doubled);
			return true;
		}
		return super.mouseClicked(e, doubled);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		fetcher.browser.onMouseReleased(inBrowser(e));
		return super.mouseReleased(e);
	}

	@Override
	public void mouseMoved(double mx, double my) {
		fetcher.browser.onMouseMoved(bx(mx), by(my));
		super.mouseMoved(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (over(mx, my)) {
			fetcher.browser.onMouseScrolled(bx(mx), by(my), vertical);
			return true;
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		fetcher.browser.onKeyPressed(e);
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent e) {
		fetcher.browser.onKeyReleased(e);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		fetcher.browser.onCharTyped(e);
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The browser's own texture, under a name the GUI can draw; the browser owns it (closing this closes nothing). */
	private static final class BrowserTexture extends AbstractTexture {
		private final MCEFBrowser browser;

		BrowserTexture(MCEFBrowser browser) {
			this.browser = browser;
		}

		@Override
		public GpuTexture getTexture() {
			return browser.getTexture();
		}

		@Override
		public GpuTextureView getTextureView() {
			return browser.getTextureView();
		}

		@Override
		public GpuSampler getSampler() {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		@Override
		public void close() {
			// MCEF Modern's browser keeps and closes its own texture
		}
	}
}
