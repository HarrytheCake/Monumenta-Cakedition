package io.github.harrythecake.cakebench;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Server side of the bench: {@code /cbbot <name> <pos>} adds a fake player that clients see like any other player
 * (armor, nameplate, default skin). It is created on the server thread with an offline profile and a connection that
 * goes nowhere, so there is no Mojang lookup and no login on another thread (what made Carpet's fake players crash
 * the 1.20.4 server). Fake players never tick; they only stand there.
 */
public final class BenchServer implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				literal("cbbot").requires(source -> source.hasPermissionLevel(2))
						.then(argument("name", StringArgumentType.word())
								.then(argument("pos", Vec3ArgumentType.vec3())
										.executes(ctx -> spawn(ctx.getSource().getServer(), ctx.getSource().getWorld(),
												StringArgumentType.getString(ctx, "name"),
												Vec3ArgumentType.getVec3(ctx, "pos")))))));
	}

	private static int spawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos) {
		if (server.getPlayerManager().getPlayer(name) != null) {
			return 0;
		}
		GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(name), name);
		ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
		server.getPlayerManager().onPlayerConnect(new NowhereConnection(), player,
				new ConnectedClientData(profile, 0, player.getClientOptions()));
		player.teleport(world, pos.x, pos.y, pos.z, 180, 0);
		player.changeGameMode(GameMode.CREATIVE);
		return 1;
	}

	/** A connection with an in-memory channel that drops every packet. */
	private static final class NowhereConnection extends ClientConnection {
		NowhereConnection() {
			super(NetworkSide.SERVERBOUND);
			new EmbeddedChannel(this); // fires channelActive, which gives the connection its channel and address
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush) {}

		@Override
		public void setPacketListener(PacketListener listener) {}

		@Override
		public boolean isOpen() {
			return true;
		}
	}
}
