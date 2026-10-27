package io.github.harrythecake.cakeupdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adoption: the first update of an install made from a pack file (.mrpack), which packwiz-installer has never seen.
 *
 * <p>Without its {@code packwiz.json}, packwiz-installer doesn't know which files the pack file installed. A jar the
 * pack has since renamed (a mod update) would stay next to the new one, and Fabric refuses to start with two copies of
 * a mod; the optional-mod window would also open with the pack's defaults and delete an optional mod the player had on.
 *
 * <p>So the pack file ships {@code config/cakeupdater/adoption.json}: every file it installed, in packwiz.json's own
 * form ({@code tools/mrpack.py} writes it). Before the first update this writes {@code packwiz.json} from it, with each
 * optional mod on if its file is present and off if not. {@code packFileHash} is left out, so the install still counts
 * as not updated ({@link UpdateCheck#installedIdentity}) until packwiz-installer finishes and writes it.
 */
final class Adoption {
	// its own logger, like Backups: no Minecraft classes, so it can be tested outside the game
	private static final Logger LOG = LoggerFactory.getLogger(CakeUpdater.MOD_ID);
	static final String LIST = "config/" + CakeUpdater.MOD_ID + "/adoption.json";
	static final String MANIFEST = "packwiz.json";

	private Adoption() {}

	/** Writes packwiz.json from the pack file's list if this install has none yet. True if it did. */
	static boolean adopt(Path gameDir) throws IOException {
		Path manifest = gameDir.resolve(MANIFEST);
		Path list = gameDir.resolve(LIST);
		if (Files.exists(manifest) || !Files.exists(list)) {
			return false;
		}
		JsonObject listed = JsonParser.parseString(Files.readString(list)).getAsJsonObject().getAsJsonObject("files");
		JsonObject cached = new JsonObject();
		int off = 0;
		for (Map.Entry<String, JsonElement> e : listed.entrySet()) {
			JsonObject file = e.getValue().getAsJsonObject().deepCopy();
			boolean optional = file.has("isOptional") && file.get("isOptional").getAsBoolean();
			if (optional && !Files.exists(gameDir.resolve(file.get("cachedLocation").getAsString()))) {
				// what packwiz-installer itself keeps for an optional file that is off
				file = new JsonObject();
				file.addProperty("isOptional", true);
				file.addProperty("optionValue", false);
				off++;
			} else {
				file.addProperty("optionValue", true);
			}
			cached.add(e.getKey(), file);
		}
		JsonObject out = new JsonObject();
		out.add("cachedFiles", cached);
		out.addProperty("cachedSide", "client");
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		Files.writeString(manifest, gson.toJson(out));
		LOG.info("Adopted the pack file install for packwiz-installer: {} files, {} optional off",
				cached.size(), off);
		return true;
	}
}
