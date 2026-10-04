package dev.romoslayer.elapsed.time;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The time Elapsed measures everything in: the world's game time (one step per tick, untouched by /time set or /time
 * add) plus an offset. The offset grows by the real time the server spent switched off (with useRealTime on), and
 * shrinks by the game time that passes while Elapsed is switched off, so that time is never caught up. The reading
 * itself never goes backwards. Every chunk and animal remembers the reading at which it was last saved; the difference
 * to the current reading is how long it was away.
 *
 * <p>Kept in elapsed.json in the world folder.
 */
public final class ElapsedClock {
	private static final String FILE_NAME = "elapsed.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long MILLIS_PER_TICK = 50L;
	private static final long UNSET = Long.MIN_VALUE;

	private final MinecraftServer server;
	private final Path file;
	private final State state;
	private long downtimeAddedAtStart;
	private long lastGameTime = UNSET;

	/** What is saved. */
	private static final class State {
		/** Added to the game time (negative after time spent switched off). */
		long offsetTicks;
		/** Clock reading when Elapsed first ran in this world. */
		long installedAt = UNSET;
		/** Real time of the last save, for measuring how long the server was off. */
		long lastRealTimeMillis;
		/** Game time of the last save: after a crash, game time saved later than this was running time, not downtime. */
		long lastGameTime = UNSET;
	}

	public ElapsedClock(MinecraftServer server) {
		this.server = server;
		this.file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
		State loaded = load(this.file);
		boolean fresh = loaded == null;
		this.state = fresh ? new State() : loaded;
		ElapsedConfig config = ElapsedConfig.get();
		long gameTime = this.gameTime();
		if (!fresh && config.general.enabled && config.general.useRealTime && this.state.lastRealTimeMillis > 0) {
			long recovered = this.state.lastGameTime == UNSET ? 0L : gameTime - this.state.lastGameTime;
			this.downtimeAddedAtStart = downtimeTicks(System.currentTimeMillis() - this.state.lastRealTimeMillis, recovered, config.globalCapTicks());
			this.state.offsetTicks += this.downtimeAddedAtStart;
		}
		if (this.state.installedAt == UNSET) {
			this.state.installedAt = this.now();
		}
		this.save();
		if (this.downtimeAddedAtStart > 0) {
			Elapsed.LOGGER.info("The server was off for {} ticks; that time will be caught up as chunks load", this.downtimeAddedAtStart);
		}
	}

	/**
	 * How much of the real time since the last clock save was spent switched off. Game time the world got through
	 * after that save and still has (it was saved before a crash) was the server running, so it is not counted again.
	 * Never negative, never more than the cap.
	 */
	public static long downtimeTicks(long realMillisSinceSave, long recoveredGameTicks, long capTicks) {
		long realTicks = Math.max(0L, realMillisSinceSave) / MILLIS_PER_TICK;
		return Math.clamp(realTicks - Math.max(0L, recoveredGameTicks), 0L, Math.max(0L, capTicks));
	}

	/** Called every server tick. While catching up is switched off, the clock stands still. */
	public void tick(boolean enabled) {
		long gameTime = this.gameTime();
		if (!enabled && this.lastGameTime != UNSET && gameTime > this.lastGameTime) {
			this.state.offsetTicks -= gameTime - this.lastGameTime;
		}
		this.lastGameTime = gameTime;
	}

	/** The current clock reading. */
	public long now() {
		return this.gameTime() + this.state.offsetTicks;
	}

	private long gameTime() {
		ServerLevel overworld = this.server.getLevel(Level.OVERWORLD);
		return overworld == null ? 0L : overworld.getGameTime();
	}

	public long installedAt() {
		return this.state.installedAt;
	}

	public long offsetTicks() {
		return this.state.offsetTicks;
	}

	public long downtimeAddedAtStart() {
		return this.downtimeAddedAtStart;
	}

	/**
	 * How long something last saved at clock reading {@code savedAt} has been away, already cut to the global cap.
	 * Readings of zero or less mean "never saved by anything that kept time"; readings from the future (a world
	 * restored from an older backup, a crash that lost the last save) are treated as no time at all.
	 */
	public long elapsedSince(long savedAt) {
		if (savedAt <= 0L) {
			return 0L;
		}
		ElapsedConfig config = ElapsedConfig.get();
		long from = config.general.countTimeBeforeInstall ? savedAt : Math.max(savedAt, this.state.installedAt);
		long elapsed = this.now() - from;
		return elapsed <= 0L ? 0L : Math.min(elapsed, config.globalCapTicks());
	}

	/** Called regularly and on shutdown, so the next start knows how long the server was off. */
	public void save() {
		this.state.lastRealTimeMillis = System.currentTimeMillis();
		this.state.lastGameTime = this.gameTime();
		try {
			Files.createDirectories(this.file.getParent());
			Path temp = this.file.resolveSibling(FILE_NAME + ".tmp");
			Files.writeString(temp, GSON.toJson(this.state), StandardCharsets.UTF_8);
			Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Elapsed.LOGGER.error("Could not write {}", this.file, e);
		}
	}

	private static State load(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), State.class);
		} catch (IOException | JsonParseException e) {
			Elapsed.LOGGER.error("Could not read {}; starting a new clock (nothing will catch up on time before now)", file, e);
			return null;
		}
	}
}
