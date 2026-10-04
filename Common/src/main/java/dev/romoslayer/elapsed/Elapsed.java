package dev.romoslayer.elapsed;

import com.mojang.brigadier.CommandDispatcher;
import dev.romoslayer.elapsed.command.ElapsedCommand;
import dev.romoslayer.elapsed.compat.SeasonfallBridge;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.core.CatchupManager;
import dev.romoslayer.elapsed.platform.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything the loader entrypoints call into. Each loader turns its own events into these calls, so the catch-up
 * logic itself is written once.
 */
public final class Elapsed {
	public static final String MOD_ID = "elapsed";
	public static final Logger LOGGER = LoggerFactory.getLogger("Elapsed");

	private static @Nullable Platform platform;
	private static @Nullable CatchupManager manager;
	// A server that has stopped must not get a new manager from a late save
	private static @Nullable MinecraftServer stoppedServer;

	private Elapsed() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
		ElapsedConfig.load(loaderPlatform.configDir());
		SeasonfallBridge.register();
	}

	public static Platform platform() {
		if (platform == null) {
			throw new IllegalStateException("Elapsed was used before its loader entrypoint ran");
		}
		return platform;
	}

	/** The running server's catch-up work, or null while no server is running. */
	public static @Nullable CatchupManager manager() {
		return manager;
	}

	/**
	 * The catch-up work of this server, started if need be. Chunks and entities load before the server reports that it
	 * has started, so this is what the load hooks use.
	 */
	public static synchronized @Nullable CatchupManager manager(@Nullable MinecraftServer server) {
		if (server == null || platform == null || server == stoppedServer) {
			return null;
		}
		if (manager == null || manager.server() != server) {
			manager = new CatchupManager(server);
		}
		return manager;
	}

	public static void onServerStarted(MinecraftServer server) {
		manager(server);
	}

	/** After the final save, so every chunk was written with its timestamp. */
	public static synchronized void onServerStopped(MinecraftServer server) {
		if (manager != null && manager.server() == server) {
			manager.shutdown();
			manager = null;
		}
		stoppedServer = server;
	}

	public static void onServerTickEnd(MinecraftServer server) {
		CatchupManager current = manager;
		if (current != null && current.server() == server) {
			current.tick();
		}
	}

	public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		CatchupManager current = manager(level.getServer());
		if (current != null) {
			current.onChunkLoad(level, chunk);
		}
	}

	public static void onEntityLoad(ServerLevel level, Entity entity) {
		CatchupManager current = manager(level.getServer());
		if (current != null) {
			current.onEntityLoad(level, entity);
		}
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		ElapsedCommand.register(dispatcher);
	}
}
