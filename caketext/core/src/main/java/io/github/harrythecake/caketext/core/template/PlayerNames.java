package io.github.harrythecake.caketext.core.template;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.jetbrains.annotations.Nullable;

/**
 * Names of the players currently online, so templates can replace them with {@code {n}}.
 * Player names are personal data: they must never end up in a collected or exported file.
 */
public final class PlayerNames {
	/** Real Minecraft names; tab-list filler entries (spaces, symbols) are skipped. */
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static volatile Set<String> names = Set.of();
	private static volatile @Nullable Pattern pattern;

	private PlayerNames() {
	}

	/** Reads the tab list and the own name. Call every second or so on the client thread. */
	public static void refresh(MinecraftClient client) {
		Set<String> found = new TreeSet<>();
		// the own name first: known from the login, before the tab list or the player entity exist
		found.add(client.getSession().getUsername());
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		if (handler != null) {
			for (PlayerListEntry entry : handler.getPlayerList()) {
				found.add(entry.getProfile().getName());
			}
		}
		if (client.player != null) {
			found.add(client.player.getGameProfile().getName());
		}
		update(found);
	}

	public static void update(Collection<String> candidates) {
		Set<String> valid = candidates.stream().filter(PlayerNames::valid)
				.collect(Collectors.toCollection(TreeSet::new));
		if (valid.equals(names)) {
			return;
		}
		names = Set.copyOf(valid);
		pattern = valid.isEmpty() ? null : Pattern.compile("(?<![A-Za-z0-9_])(?:"
				+ valid.stream().sorted(Comparator.comparingInt(String::length).reversed())
						.map(Pattern::quote).collect(Collectors.joining("|"))
				+ ")(?![A-Za-z0-9_])");
	}

	/** True for a string shaped like a Minecraft name. */
	public static boolean valid(@Nullable String name) {
		return name != null && VALID.matcher(name).matches();
	}

	public static @Nullable Pattern pattern() {
		return pattern;
	}
}
