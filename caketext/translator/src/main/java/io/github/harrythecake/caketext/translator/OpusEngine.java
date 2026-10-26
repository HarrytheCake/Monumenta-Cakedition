package io.github.harrythecake.caketext.translator;

import io.github.harrythecake.caketext.translator.mt.OpusMt;
import net.minecraft.text.Text;

/** The built-in engine: opus-mt-en-zh int8, run in Java (see {@link ModelManager}). */
final class OpusEngine implements TranslationEngine {
	private final ModelManager models;

	OpusEngine(ModelManager models) {
		this.models = models;
	}

	@Override
	public String id() {
		return ModelManager.ID;
	}

	@Override
	public String label() {
		return ModelManager.LABEL;
	}

	@Override
	public boolean ready() {
		return models.model() != null;
	}

	@Override
	public Text describe() {
		return models.describe();
	}

	@Override
	public String translate(String text, TranslatorConfig config) {
		OpusMt model = models.model();
		if (model == null) {
			throw new IllegalStateException("model not loaded");
		}
		OpusMt.Quality quality = "fast".equals(config.machineQuality) ? OpusMt.Quality.FAST : OpusMt.Quality.BEST;
		return model.translate(text, config.languageToken(), quality);
	}
}
