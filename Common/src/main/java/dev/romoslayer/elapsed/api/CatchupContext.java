package dev.romoslayer.elapsed.api;

import dev.romoslayer.elapsed.core.Registrations;
import dev.romoslayer.elapsed.mc.Versioned;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** What a handler may know about the catch-up it is part of. One context covers one chunk or one entity. */
public final class CatchupContext {
	private final ServerLevel level;
	private final long rawElapsedTicks;
	private final RandomSource random;
	private final @Nullable List<String> notes;
	private double randomTickChance = -1.0;

	public CatchupContext(ServerLevel level, long rawElapsedTicks, RandomSource random, boolean debug) {
		this.level = level;
		this.rawElapsedTicks = rawElapsedTicks;
		this.random = random;
		this.notes = debug ? new ArrayList<>() : null;
	}

	public ServerLevel level() {
		return this.level;
	}

	/** The whole time spent unloaded, before any handler's cap. */
	public long rawElapsedTicks() {
		return this.rawElapsedTicks;
	}

	public RandomSource random() {
		return this.random;
	}

	/** The chance that a given block receives a random tick in one game tick (random_tick_speed / 4096). */
	public double randomTickChance() {
		if (this.randomTickChance < 0.0) {
			int speed = Versioned.randomTickSpeed(this.level);
			this.randomTickChance = Math.max(0, speed) / 4096.0;
		}
		return this.randomTickChance;
	}

	/**
	 * How fast this plant grew on average over the given time according to every registered {@link GrowthRateProvider}
	 * (seasons and the like), 1 when none has an opinion.
	 */
	public double growthMultiplier(BlockPos pos, BlockState state, long elapsedTicks) {
		return Registrations.combinedGrowthMultiplier(this.level, pos, state, elapsedTicks);
	}

	/** The block at a position if its chunk is loaded, otherwise null. Never loads or generates a chunk. */
	public @Nullable BlockState loadedBlockState(BlockPos pos) {
		if (this.level.isOutsideBuildHeight(pos)) {
			return null;
		}
		LevelChunk chunk = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		return chunk == null ? null : chunk.getBlockState(pos);
	}

	/** Whether the block at a position can be read or changed without loading a chunk. */
	public boolean isLoaded(BlockPos pos) {
		return !this.level.isOutsideBuildHeight(pos)
				&& this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
	}

	public boolean isDebug() {
		return this.notes != null;
	}

	/** Adds a line to the debug report (ignored unless debugging is on). */
	public void note(String message) {
		if (this.notes != null) {
			this.notes.add(message);
		}
	}

	public List<String> notes() {
		return this.notes == null ? List.of() : this.notes;
	}
}
