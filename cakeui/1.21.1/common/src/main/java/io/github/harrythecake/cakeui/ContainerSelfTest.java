package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.mixin.ContainerScreenAccessor;
import io.github.harrythecake.cakeui.mixin.MouseInvoker;
import io.github.harrythecake.cakeui.model.GameValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The self-test's container screens (part of -Dcakeui.selftest=world, after the inventory): a furnace laid out by
 * the active layout (the example's container/minecraft/furnace) with fixed synced numbers, and a stonecutter laid
 * out by a stand-in mod's default layout (added through the API) whose button sends a menu-button click. Each
 * check logs PASS or FAIL.
 */
final class ContainerSelfTest {
	private static final String MOD_LAYOUT = """
			// written by CakeUI's self-test: a stand-in mod's default layout for the stonecutter
			{
			  ports: {
			    panel: { elements: {
			      cut: { type: "button", x: -30, y: 4, width: 24, height: 24, action: { menuButton: 0 }, tooltip: "Cut",
			             background: { type: "rect", color: "#3060ff", outline: "#ffffff", outlineWidth: 1 } },
			    } },
			    slots: { "0": { skin: { mark: { type: "rect", color: "#40ff4060" } } } },
			  },
			}
			""";
	private static int t;

	private ContainerSelfTest() {
	}

	/** Before the world: the stand-in mod's layout folder, added through the API, then a reload reads it. */
	static void init(Minecraft client) {
		Path dir = client.gameDirectory.toPath().resolve("cakeui-selftest-modlayout");
		try {
			Path file = dir.resolve("container/minecraft/stonecutter/layout.json5");
			Files.createDirectories(file.getParent());
			Files.writeString(file, MOD_LAYOUT);
		} catch (java.io.IOException e) {
			CakeUI.LOGGER.error("CakeUI self-test: could not write the stand-in mod layout (FAIL)", e);
			return;
		}
		CakeUIApi.get().addContainerLayouts("cakeui-selftest", dir);
		CakeUIApi.get().reload();
	}

	private static void log(String what, boolean ok, Object... args) {
		CakeUI.LOGGER.info("CakeUI self-test: container: " + what + " ({})", append(args, ok ? "PASS" : "FAIL"));
	}

	private static Object[] append(Object[] args, Object last) {
		Object[] out = java.util.Arrays.copyOf(args, args.length + 1);
		out[args.length] = last;
		return out;
	}

	private static void mouseAt(Minecraft client, double guiX, double guiY) {
		var w = client.getWindow();
		((MouseInvoker) client.mouseHandler).cakeui$onCursorPos(w.getWindow(), guiX * w.getGuiScale(), guiY * w.getGuiScale());
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
	}

	private static void onServer(Minecraft client, java.util.function.Consumer<ServerPlayer> task) {
		var server = client.getSingleplayerServer();
		UUID id = client.player.getUUID();
		if (server != null) {
			server.execute(() -> {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					task.accept(p);
				}
			});
		}
	}

	private static Object value(String name) {
		return GameValues.resolve(name).eval(null);
	}

	/** One step per client tick; true when done. */
	static boolean tick(Minecraft client) {
		t++;
		AbstractContainerScreen<?> screen = client.screen instanceof AbstractContainerScreen<?> s ? s : null;
		int[] b = screen == null ? new int[4] : ContainerPart.panel(screen);
		if (t == 1) {
			// a furnace menu with fixed numbers: fuel 50 of 100, cooking 100 of 200 (synced like a real furnace's)
			onServer(client, p -> {
				SimpleContainerData data = new SimpleContainerData(4);
				data.set(0, 50);
				data.set(1, 100);
				data.set(2, 100);
				data.set(3, 200);
				SimpleContainer items = new SimpleContainer(3);
				items.setItem(0, new ItemStack(Items.RAW_IRON, 5));
				items.setItem(1, new ItemStack(Items.COAL, 3));
				p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new FurnaceMenu(id, inv, items, data), Component.literal("Self-test furnace")));
			});
		} else if (t == 30) {
			boolean laid = screen != null && ContainerPart.ports(screen) != null;
			log("furnace laid out by the active layout: {}", laid, screen == null ? "no screen" : ContainerPart.menuId(screen.getMenu()));
			log("menu.data.2 = {}, menu.data.3 = {}, menu.id = {}", Double.valueOf(100).equals(value("menu.data.2")) && Double.valueOf(200).equals(value("menu.data.3")),
					value("menu.data.2"), value("menu.data.3"), value("menu.id"));
			Slot result = screen == null ? null : screen.getMenu().getSlot(2);
			log("result slot moved to x {} (layout: 120, vanilla 116)", result != null && result.x == 120, result == null ? -1 : result.x);
			log("JEI areas outside the panel: {}", screen != null && !ContainerPart.extraAreas(screen).isEmpty(),
					screen == null ? "" : ContainerPart.extraAreas(screen).stream().map(java.util.Arrays::toString).toList());
			if (result != null) {
				mouseAt(client, b[0] + result.x + 8, b[1] + result.y + 8);
			}
		} else if (t == 34) {
			Slot hovered = screen == null ? null : ((ContainerScreenAccessor) screen).cakeui$hoveredSlot();
			log("hover follows the moved slot: {}", hovered != null && hovered.index == 2, hovered == null ? "none" : hovered.index);
			shot(client, "cakeui-selftest-furnace.png");
			mouseAt(client, b[0] + 79 + 12, b[1] + 38 + 4); // the layout's progress bar
		} else if (t == 38) {
			ScreenPorts p = screen == null ? null : ContainerPart.ports(screen);
			var tip = p == null ? null : p.renderer().hoverTooltip();
			log("progress bar tooltip: {}", tip != null && tip.get(0).getString().contains("50%"), tip == null ? "none" : tip.get(0).getString());
			shot(client, "cakeui-selftest-furnace-tooltip.png");
		} else if (t == 42) {
			client.player.closeContainer();
			// the stonecutter: the example layout doesn't mention it, so the stand-in mod's layout draws it
			onServer(client, p -> {
				p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StonecutterMenu(id, inv, ContainerLevelAccess.NULL), Component.literal("Self-test stonecutter")));
				p.containerMenu.getSlot(0).set(new ItemStack(Items.STONE, 4));
			});
		} else if (t == 70) {
			ScreenPorts p = screen == null ? null : ContainerPart.ports(screen);
			log("stonecutter laid out by the mod's default layout: {}", p != null && p.renderer() != Ui.renderer(),
					screen == null ? "no screen" : ContainerPart.menuId(screen.getMenu()));
			mouseAt(client, b[0] - 30 + 12, b[1] + 4 + 12); // the layout's button
		} else if (t == 74) {
			boolean taken = screen != null && CakeUIClient.screenMouseClicked(screen, 0);
			boolean release = screen != null && CakeUIClient.screenMouseReleaseTaken(screen);
			log("the layout's button took the click and its release: {} {}", taken && release, taken, release);
		} else if (t == 90) {
			StonecutterMenu m = screen != null && screen.getMenu() instanceof StonecutterMenu sm ? sm : null;
			boolean ok = m != null && m.getSelectedRecipeIndex() == 0 && m.getSlot(1).hasItem();
			log("menuButton 0 reached the server: recipe {} selected, result {}", ok, m == null ? -1 : m.getSelectedRecipeIndex(),
					m == null ? "" : m.getSlot(1).getItem());
			shot(client, "cakeui-selftest-stonecutter.png");
			onServer(client, p -> p.containerMenu.getSlot(0).set(ItemStack.EMPTY)); // nothing goes back to the player
		} else if (t == 96) {
			client.player.closeContainer();
			// a chest: nobody lays out minecraft:generic_9x3, so it must stay exactly vanilla
			onServer(client, p -> p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv), Component.literal("Self-test chest"))));
		} else if (t == 120) {
			log("an unmentioned chest stays vanilla: {}", screen != null && ContainerPart.ports(screen) == null && ScreenPorts.of(screen) == null,
					screen == null ? "no screen" : ContainerPart.menuId(screen.getMenu()));
			shot(client, "cakeui-selftest-chest.png");
		} else if (t == 124) {
			client.player.closeContainer();
			return true;
		}
		return false;
	}
}
