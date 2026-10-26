package io.github.harrythecake.caketext.translator.ui;

import io.github.harrythecake.caketext.core.template.ItemFilter;
import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import io.github.harrythecake.caketext.translator.TranslatorConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Which places get translated. */
final class SurfacesScreen extends OptionsScreen {
	private final TranslatorConfig config;

	SurfacesScreen(Screen parent, TranslatorConfig config) {
		super(Text.translatable("caketext_translator.surfaces.title"), parent);
		this.config = config;
	}

	@Override
	protected void build() {
		for (String surface : TranslatorConfig.SURFACES) {
			add(toggle(Text.translatable("caketext.surface." + surface), config.surface(surface),
					v -> config.surfaces.put(surface, v)));
		}
		add(tip(toggle(Text.translatable("caketext_translator.option.item_names"), config.translateItemNames,
				v -> config.translateItemNames = v), Text.translatable("caketext_translator.option.item_names.tip")));
		addWide(label(Text.translatable("caketext.ignored_items")));
		addWide(tip(textField(Text.translatable("caketext.ignored_items"), String.join(", ", config.ignoredItems),
				v -> config.ignoredItems = new java.util.ArrayList<>(ItemFilter.parse(v))), Text.translatable("caketext.ignored_items.tip")));
	}
}
