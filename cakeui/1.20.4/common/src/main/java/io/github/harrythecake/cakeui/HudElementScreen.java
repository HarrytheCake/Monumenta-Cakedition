package io.github.harrythecake.cakeui;

import com.google.gson.JsonObject;
import io.github.harrythecake.cakeui.edit.AddedElements;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.model.Problems;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The HUD editor's properties panel (E3) for an element it added: the fields of its type (text, size,
 * colour, bar value, image, sizes), an "Advanced" condition for when it shows, and a picker of the game
 * values its text or expressions can use. Every change shows on the live HUD at once; problems with the
 * definition are listed underneath. Done keeps the changes, Cancel puts the element back as it was.
 */
public final class HudElementScreen extends Screen {
	private static final int ROW = 21;
	private static final int PICKS = 4;

	private final HudEditorScreen parent;
	private final String key;
	private final PortEdit edit;
	private final JsonObject before;
	private final String type;
	private boolean undoStepTaken;

	/** The field values go in here as the player types. */
	private final List<Field> fields = new ArrayList<>();
	@Nullable
	private EditBox lastExpressionField;
	/** The property of the field last typed in (kept when the value list is open and the fields are rebuilt). */
	@Nullable
	private String lastProp;
	private boolean lastIsTemplate;
	private EditBox filter;
	private final List<Button> picks = new ArrayList<>();
	private List<Problems.Problem> problems = List.of();

	/** One editable property: its key in the definition, whether it holds a template ({…}) or an expression, and its label. */
	private record Field(String prop, EditBox box, boolean template, boolean expression, boolean labelled) {
	}

	HudElementScreen(HudEditorScreen parent, String key, PortEdit edit) {
		super(Component.translatable("cakeui.element.title"));
		this.parent = parent;
		this.key = key;
		this.edit = edit;
		this.before = edit.element.deepCopy();
		this.type = AddedElements.type(edit.element);
	}

	/** The first PNG in config/cakeui/images (a new image element starts with it), or "". */
	static String firstImage() {
		List<String> all = images(HudAdded.imagesDir());
		return all.isEmpty() ? "" : all.get(0);
	}

	/** PNG files under a folder, as paths relative to it. */
	static List<String> images(Path dir) {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.walk(dir, 4)) {
			for (Path p : files.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList()) {
				out.add(dir.relativize(p).toString().replace('\\', '/'));
			}
		} catch (IOException e) {
			CakeUI.LOGGER.warn("CakeUI: could not list {}", dir, e);
		}
		return out;
	}

	private Path imagesDir() {
		return HudAdded.rendererFor(key).layout.assets;
	}

	private String str(String prop) {
		JsonObject def = edit.element;
		return def.has(prop) && def.get(prop).isJsonPrimitive() ? def.get(prop).getAsString() : "";
	}

	@Override
	protected void init() {
		fields.clear();
		picks.clear();
		filter = null;
		lastExpressionField = null;
		int left = width / 2 - 150;
		int x = left + 100;
		int w = 200;
		int y = 30;
		switch (type) {
			case "text" -> {
				y = field("text", "cakeui.element.text", x, y, w, true, false);
				y = field("size", "cakeui.element.size", x, y, 60, false, false);
				y = field("color", "cakeui.element.color", x, y, 80, false, false);
				addRenderableWidget(CycleButton.onOffBuilder(!str("shadow").equals("false"))
						.create(x, y, 80, 18, Component.translatable("cakeui.element.shadow"), (b, v) -> set("shadow", v)));
				y += ROW;
			}
			case "rect" -> {
				y = sizeRow(x, y);
				y = field("color", "cakeui.element.color", x, y, 80, false, false);
				y = field("outline", "cakeui.element.outline", x, y, 80, false, false);
			}
			case "bar" -> {
				y = field("value", "cakeui.element.value", x, y, w, false, true);
				y = sizeRow(x, y);
				y = field("color", "cakeui.element.color", x, y, 80, false, false);
				y = field("background", "cakeui.element.background", x, y, 80, false, false);
			}
			case "crosshair" -> {
				String shape = str("shape");
				if (shape.equals("image")) {
					List<String> files = images(imagesDir());
					String src = str("src");
					if (!files.contains(src)) {
						files.add(0, src);
					}
					addRenderableWidget(CycleButton.<String>builder(s -> Component.literal(s.isEmpty() ? "—" : s))
							.withValues(files)
							.withInitialValue(src)
							.displayOnlyValue()
							.create(x, y, w - 64, 18, Component.translatable("cakeui.element.image"), (b, v) -> set("src", v)));
					addRenderableWidget(Button.builder(Component.translatable("cakeui.element.openFolder"), b -> openImages())
							.bounds(x + w - 60, y, 60, 18).build());
					y += ROW;
				}
				y = field("size", "cakeui.element.crosshairSize", x, y, 40, false, false);
				if (shape.equals("cross")) {
					y = field("gap", "cakeui.element.gap", x, y, 40, false, false);
				}
				if (!shape.equals("dot") && !shape.equals("image")) {
					y = field("thickness", "cakeui.element.thickness", x, y, 40, false, false);
				}
				y = field("color", "cakeui.element.color", x, y, 80, false, false);
				addRenderableWidget(CycleButton.onOffBuilder(!str("invert").equals("false"))
						.withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cakeui.element.invert.tooltip")))
						.create(x, y, 120, 18, Component.translatable("cakeui.element.invert"), (b, v) -> set("invert", v)));
				y += ROW;
			}
			case "item" -> {
				y = field("item", "cakeui.element.item", x, y, w, false, false);
				y = sizeRow(x, y);
				y = toggles(x, y);
			}
			case "inventory" -> {
				y = field("slots", "cakeui.element.slots", x, y, w, false, false);
				y = field("columns", "cakeui.element.columns", x, y, 40, false, false);
				y = field("gap", "cakeui.element.gap", x, y, 40, false, false);
				addRenderableWidget(CycleButton.<String>builder(s -> Component.translatable("cakeui.element.slotBackground." + s))
						.withValues(List.of("vanilla", "rect", "none"))
						.withInitialValue(str("slotBackground").isEmpty() ? "vanilla" : str("slotBackground"))
						.create(x, y, w, 18, Component.translatable("cakeui.element.slotBackground"), (b, v) -> set("slotBackground", v)));
				y += ROW;
				y = toggles(x, y);
			}
			case "image" -> {
				List<String> files = images(imagesDir());
				String src = str("src");
				if (!files.contains(src)) {
					files.add(0, src);
				}
				addRenderableWidget(CycleButton.<String>builder(s -> Component.literal(s.isEmpty() ? "—" : s))
						.withValues(files)
						.withInitialValue(src)
						.displayOnlyValue()
						.create(x, y, w - 64, 18, Component.translatable("cakeui.element.image"), (b, v) -> set("src", v)));
				addRenderableWidget(Button.builder(Component.translatable("cakeui.element.openFolder"), b -> openImages())
						.bounds(x + w - 60, y, 60, 18).build());
				y += ROW;
				y = sizeRow(x, y);
			}
			default -> {
			}
		}
		if (type.equals("crosshair")) {
			addDoneCancel();
			return;
		}
		// Advanced: when it shows
		y += 6;
		EditBox visible = new EditBox(font, x, y, w, 18, Component.translatable("cakeui.element.visible"));
		visible.setMaxLength(300);
		String v = str("visible");
		visible.setValue(v.startsWith("=") ? v.substring(1).trim() : v);
		visible.setResponder(s -> setVisible(s.trim()));
		addRenderableWidget(visible);
		fields.add(new Field("visible", visible, false, true, true));
		y += ROW + 4;
		// the game values picker: type to filter, click to insert into the last text/expression field
		filter = new EditBox(font, x, y, w - 70, 18, Component.translatable("cakeui.element.values"));
		filter.setResponder(s -> refreshPicks());
		addRenderableWidget(filter);
		addRenderableWidget(Button.builder(Component.translatable("cakeui.element.allValues"),
				b -> minecraft.setScreen(new HudValuesScreen(this, this::insertByProp))).bounds(x + w - 66, y, 66, 18).build());
		y += ROW;
		for (int i = 0; i < PICKS; i++) {
			int col = i % 2;
			int row = i / 2;
			Button b = Button.builder(Component.empty(), btn -> insert(btn.getMessage().getString()))
					.bounds(x + col * (w / 2 + 2), y + row * 20, w / 2 - 2, 18).build();
			picks.add(addRenderableWidget(b));
		}
		refreshPicks();
		addDoneCancel();
	}

	/** Done / Cancel at the panel's top right, so the panel fits small windows. */
	private void addDoneCancel() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.setScreen(parent)).bounds(width / 2 + 100, 6, 54, 16).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> {
			edit.element = before.deepCopy();
			minecraft.setScreen(parent);
		}).bounds(width / 2 + 44, 6, 54, 16).build());
		problems = type.equals("crosshair") ? List.of() : HudAdded.check(key, edit.element);
	}

	/** A text field for a property; returns the next row's y. */
	private int field(String prop, String label, int x, int y, int w, boolean template, boolean expression) {
		EditBox box = new EditBox(font, x, y, w, 18, Component.translatable(label));
		box.setMaxLength(300);
		box.setValue(str(prop));
		box.setResponder(s -> set(prop, s));
		addRenderableWidget(box);
		fields.add(new Field(prop, box, template, expression, true));
		return y + ROW;
	}

	/** Count, durability and cooldown overlays on/off, side by side. */
	private int toggles(int x, int y) {
		String[] props = {"counts", "durability", "cooldown"};
		for (int i = 0; i < 3; i++) {
			String prop = props[i];
			addRenderableWidget(CycleButton.onOffBuilder(!str(prop).equals("false"))
					.create(x + i * 67, y, 65, 18, Component.translatable("cakeui.element." + prop), (b, v) -> set(prop, v)));
		}
		return y + ROW;
	}

	/** Width and height side by side (GUI pixels, e.g. 40px). */
	private int sizeRow(int x, int y) {
		for (int i = 0; i < 2; i++) {
			String prop = i == 0 ? "width" : "height";
			EditBox box = new EditBox(font, x + i * 66, y, 60, 18, Component.translatable("cakeui.element.sizeWH"));
			box.setMaxLength(40);
			box.setValue(str(prop));
			box.setResponder(s -> set(prop, s));
			addRenderableWidget(box);
			fields.add(new Field(prop, box, false, false, i == 0));
		}
		return y + ROW;
	}

	private void change() {
		if (!undoStepTaken) {
			parent.beforeElementChange();
			undoStepTaken = true;
		}
	}

	private void set(String prop, String value) {
		change();
		if (value.isEmpty() && !prop.equals("text") && !prop.equals("src")) {
			edit.element.remove(prop);
		} else if ((prop.equals("columns") || type.equals("crosshair")) && value.trim().matches("\\d+(\\.\\d+)?")) {
			edit.element.addProperty(prop, Double.parseDouble(value.trim()));
		} else if (prop.equals("columns") && value.trim().matches("\\d+")) {
			edit.element.addProperty(prop, Integer.parseInt(value.trim()));
		} else {
			edit.element.addProperty(prop, value);
		}
		problems = type.equals("crosshair") ? List.of() : HudAdded.check(key, edit.element);
	}

	private void set(String prop, boolean value) {
		change();
		edit.element.addProperty(prop, value);
		problems = type.equals("crosshair") ? List.of() : HudAdded.check(key, edit.element);
	}

	private void setVisible(String expr) {
		change();
		if (expr.isEmpty()) {
			edit.element.remove("visible");
		} else {
			edit.element.addProperty("visible", "= " + expr);
		}
		problems = HudAdded.check(key, edit.element);
	}

	private void refreshPicks() {
		String q = filter.getValue().trim().toLowerCase(Locale.ROOT);
		List<String> names = GameValues.names().stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q)).sorted().toList();
		for (int i = 0; i < picks.size(); i++) {
			Button b = picks.get(i);
			b.visible = i < names.size();
			b.setMessage(Component.literal(i < names.size() ? names.get(i) : ""));
		}
	}

	/** From the value list (the fields were rebuilt meanwhile): adds the value to the end of the last field typed in. */
	private void insertByProp(String name) {
		String prop = lastProp;
		boolean template = lastIsTemplate;
		if (prop == null) {
			prop = type.equals("text") ? "text" : type.equals("bar") ? "value" : "visible";
			template = type.equals("text");
		}
		String piece = template ? "{" + name + "}" : name;
		if (prop.equals("visible")) {
			String v = str("visible");
			String expr = v.startsWith("=") ? v.substring(1).trim() : v;
			setVisible((expr.isEmpty() ? "" : expr + " ") + piece);
		} else {
			set(prop, str(prop) + piece);
		}
	}

	/** Puts a game value into the field last typed in: {name} in a text, the bare name in an expression. */
	private void insert(String name) {
		EditBox target = lastExpressionField;
		if (target == null) {
			for (Field f : fields) {
				if (f.template || f.expression) {
					target = f.box;
					lastProp = f.prop;
					lastIsTemplate = f.template;
					break;
				}
			}
		}
		if (target == null) {
			return;
		}
		target.insertText(lastIsTemplate ? "{" + name + "}" : name);
		setFocused(target);
	}

	@Override
	public void setFocused(@Nullable net.minecraft.client.gui.components.events.GuiEventListener focused) {
		super.setFocused(focused);
		for (Field f : fields) {
			if (f.box == focused && (f.template || f.expression)) {
				lastExpressionField = f.box;
				lastProp = f.prop;
				lastIsTemplate = f.template;
			}
		}
	}

	private void openImages() {
		Path dir = imagesDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			CakeUI.LOGGER.warn("CakeUI: could not create {}", dir, e);
		}
		Util.getPlatform().openFile(dir.toFile());
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// the live HUD stays visible; a dark panel behind the fields only
		g.fill(width / 2 - 160, 2, width / 2 + 160, Math.min(height - 2, 222), 0xC0101010);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int left = width / 2 - 150;
		Component kind = type.equals("crosshair") ? Component.translatable("cakeui.hud.port.crosshair") : Component.translatable("cakeui.editor.add." + type);
		g.drawString(font, Component.translatable("cakeui.element.title.type", kind), left, 10, 0xFFFFD000);
		for (Field f : fields) {
			if (f.labelled) {
				g.drawString(font, f.box.getMessage(), left, f.box.getY() + 5, 0xFFE0E0E0);
			}
		}
		if (filter != null && !type.equals("crosshair")) {
			g.drawString(font, Component.translatable("cakeui.element.visible.hint"), left, fields.get(fields.size() - 1).box.getY() - 10, 0xFF909090);
			g.drawString(font, filter.getMessage(), left, filter.getY() + 5, 0xFFE0E0E0);
		}
		if (!problems.isEmpty()) {
			String msg = problems.get(0).message();
			g.drawString(font, Component.literal(font.plainSubstrByWidth(msg, 300)), left, 20, 0xFFFF6060);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
