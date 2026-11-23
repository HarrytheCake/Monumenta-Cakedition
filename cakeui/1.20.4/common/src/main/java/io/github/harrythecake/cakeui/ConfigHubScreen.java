package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.configmap.ConfigHub;
import io.github.harrythecake.cakeui.configmap.HubModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The config hub (ADR 0011): one button per loaded mod that has a config map, opening that mod's options
 * ({@link ConfigHubModScreen}). Mods CakeUI can't edit (unsupported version, file not written yet) are greyed out with
 * the reason as a tooltip.
 */
public final class ConfigHubScreen extends Screen {
	private static final int ROW = 24;
	private final Screen parent;
	final HubModel model;
	private int scroll;

	public ConfigHubScreen(Screen parent) {
		super(Component.translatable("cakeui.hub.title"));
		this.parent = parent;
		this.model = ConfigHub.buildHub(Ui.config().layoutFolder());
	}

	static Component text(String keyOrText) {
		return Component.translatableWithFallback(keyOrText, keyOrText);
	}

	private int visibleRows() {
		return Math.max(1, (height - 40 - 36) / ROW);
	}

	@Override
	protected void init() {
		scroll = Math.max(0, Math.min(scroll, model.mods.size() - visibleRows()));
		int x = width / 2 - 155;
		for (int i = scroll; i < Math.min(model.mods.size(), scroll + visibleRows()); i++) {
			HubModel.Mod mod = model.mods.get(i);
			Button b = addRenderableWidget(Button.builder(text(mod.map.title), btn -> minecraft.setScreen(new ConfigHubModScreen(this, model, mod)))
					.bounds(x, 40 + (i - scroll) * ROW, 310, 20).build());
			if (!mod.editable()) {
				b.active = false;
				b.setTooltip(Tooltip.create("version".equals(mod.unavailable)
						? Component.translatable("cakeui.hub.unsupported", mod.modId, String.valueOf(mod.version), mod.map.versions.toString())
						: Component.literal(mod.unavailable)));
			}
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 27, 200, 20).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, model.mods.size() - visibleRows());
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		if (next != scroll) {
			scroll = next;
			rebuildWidgets();
		}
		return true;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
		ConfigHubModScreen.scrollbar(context, width / 2 + 159, 40, visibleRows() * ROW - 4, scroll, visibleRows(), model.mods.size());
		if (model.mods.isEmpty()) {
			context.drawCenteredString(font, Component.translatable("cakeui.hub.none"), width / 2, 48, 0xA0A0A0);
		}
	}

	@Override
	public void onClose() {
		model.discard();
		minecraft.setScreen(parent);
	}
}
