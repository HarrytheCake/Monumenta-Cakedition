package io.github.harrythecake.cakebench;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Server side of the bench: {@code /cbbot <name> <pos>} adds a fake player that clients see like any other player
 * (armor, nameplate, default skin). It is created on the server thread with an offline profile and a connection that
 * goes nowhere, so there is no Mojang lookup and no login on another thread (what made Carpet's fake players crash
 * the 1.20.4 server). Fake players never tick; they only stand there. The loader parts call {@link #register} from
 * their server command event (Fabric's CommandRegistrationCallback, NeoForge's RegisterCommandsEvent).
 */
public final class BenchServer {
	private BenchServer() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				literal("cbbot").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word())
								.then(argument("pos", Vec3Argument.vec3())
										.executes(ctx -> spawn(ctx.getSource().getServer(), ctx.getSource().getLevel(),
												StringArgumentType.getString(ctx, "name"),
												Vec3Argument.getVec3(ctx, "pos"))))));
	}

	private static int spawn(MinecraftServer server, ServerLevel world, String name, Vec3 pos) {
		if (server.getPlayerList().getPlayerByName(name) != null) {
			return 0;
		}
		GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
		ServerPlayer player = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
		server.getPlayerList().placeNewPlayer(new NowhereConnection(), player,
				new CommonListenerCookie(profile, 0, player.clientInformation(), false));
		player.teleportTo(world, pos.x, pos.y, pos.z, 180, 0);
		player.setGameMode(GameType.CREATIVE);
		return 1;
	}

	/** A connection with an in-memory channel that drops every packet. */
	private static final class NowhereConnection extends Connection {
		NowhereConnection() {
			super(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(this); // fires channelActive, which gives the connection its channel and address
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {}

		@Override
		public boolean isConnected() {
			return true;
		}
	}
}
