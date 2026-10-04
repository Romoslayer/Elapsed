package dev.romoslayer.elapsed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.core.CatchupManager;
import dev.romoslayer.elapsed.core.Timestamps;
import dev.romoslayer.elapsed.time.ElapsedClock;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** /elapsed, for server operators: how catching up is going, the config, and debug output. */
public final class ElapsedCommand {
	private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.literal("Elapsed is not running"));

	private ElapsedCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(Elapsed.MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("status").executes(ElapsedCommand::status))
				.then(Commands.literal("chunk").executes(ElapsedCommand::chunk))
				.then(Commands.literal("reload").executes(ElapsedCommand::reload))
				.then(Commands.literal("debug")
						.executes(context -> debug(context, null))
						.then(Commands.argument("enabled", BoolArgumentType.bool())
								.executes(context -> debug(context, BoolArgumentType.getBool(context, "enabled"))))));
	}

	private static CatchupManager manager() throws CommandSyntaxException {
		CatchupManager manager = Elapsed.manager();
		if (manager == null) {
			throw NOT_RUNNING.create();
		}
		return manager;
	}

	private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		CatchupManager manager = manager();
		ElapsedConfig config = ElapsedConfig.get();
		ElapsedClock clock = manager.clock();
		source.sendSuccess(() -> Component.literal("Elapsed " + (config.general.enabled ? "is on" : "is OFF"))
				.withStyle(config.general.enabled ? ChatFormatting.AQUA : ChatFormatting.RED), false);
		source.sendSuccess(() -> line("Counting", config.general.useRealTime ? "game time and server downtime" : "game time only"), false);
		source.sendSuccess(() -> line("Longest catch-up", hours(config.globalCapTicks())), false);
		if (clock.offsetTicks() > 0) {
			source.sendSuccess(() -> line("Downtime counted", hours(clock.offsetTicks()) + " in total, " + hours(clock.downtimeAddedAtStart())
					+ " at the last start"), false);
		}
		source.sendSuccess(() -> line("Waiting", manager.queuedChunks() + " chunk(s), " + manager.queuedEntities() + " entity(s)"), false);
		source.sendSuccess(() -> line("Caught up since start", manager.chunksCaughtUp() + " chunk(s): " + manager.blockEntitiesCaughtUp()
				+ " block entity(s), " + manager.blocksCaughtUp() + " block(s); " + manager.entitiesCaughtUp() + " entity(s)"), false);
		source.sendSuccess(() -> line("Slowest tick of catching up", String.format(Locale.ROOT, "%.2f ms", manager.worstTickNanos() / 1.0e6)), false);
		source.sendSuccess(() -> line("Longest wait once ready", manager.longestWaitTicks() + " tick(s)"), false);
		CatchupManager.Burst burst = manager.lastBurst();
		if (burst != null) {
			source.sendSuccess(() -> line("Last burst", burst.toString()), false);
		}
		source.sendSuccess(() -> line("Handlers", manager.handlers().handlerCount() + " active"), false);
		if (!manager.failedHandlers().isEmpty()) {
			source.sendSuccess(() -> Component.literal("Switched off after an error: " + manager.failedHandlers()).withStyle(ChatFormatting.RED), false);
		}
		return 1;
	}

	private static int chunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		manager();
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		LevelChunk chunk = level.getChunkAt(pos);
		long pending = CatchupManager.pendingTicks(chunk);
		boolean active = ElapsedConfig.get().general.enabled && Timestamps.isDimensionActive(level);
		source.sendSuccess(() -> Component.literal("Chunk [" + chunk.getPos().x() + ", " + chunk.getPos().z() + "] in " + level.dimension().identifier())
				.withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> line("This dimension", active ? "catches up" : "does not catch up"), false);
		source.sendSuccess(() -> line("Waiting to catch up", pending > 0 ? hours(pending) : "nothing"), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		List<String> problems = ElapsedConfig.reload();
		CatchupManager manager = Elapsed.manager();
		if (manager != null) {
			manager.rebuildHandlers();
		}
		if (problems.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Reloaded elapsed.toml").withStyle(ChatFormatting.GREEN), true);
		} else {
			source.sendSuccess(() -> Component.literal("Reloaded elapsed.toml with " + problems.size() + " problem(s):").withStyle(ChatFormatting.YELLOW), true);
			for (String problem : problems) {
				source.sendSuccess(() -> Component.literal(" - " + problem).withStyle(ChatFormatting.GRAY), false);
			}
		}
		return 1;
	}

	private static int debug(CommandContext<CommandSourceStack> context, Boolean enabled) throws CommandSyntaxException {
		CatchupManager manager = manager();
		boolean on = enabled == null ? !manager.isDebug() : enabled;
		manager.setDebug(on);
		context.getSource().sendSuccess(() -> Component.literal("Elapsed debug logging " + (on ? "on (see the server log)" : "off")
				+ (!on && ElapsedConfig.get().general.debugLogging ? "; it stays on while debugLogging is true in elapsed.toml" : ""))
				.withStyle(ChatFormatting.AQUA), true);
		return 1;
	}

	private static Component line(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static String hours(long ticks) {
		double hours = ticks / (double) ElapsedConfig.TICKS_PER_HOUR;
		if (hours >= 1.0) {
			return String.format(Locale.ROOT, "%.1f h (%d ticks)", hours, ticks);
		}
		return String.format(Locale.ROOT, "%.1f min (%d ticks)", hours * 60.0, ticks);
	}
}
