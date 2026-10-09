package dev.romoslayer.elapsed.core;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.mc.Versioned;
import java.util.OptionalLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

/** Reading and writing the "last saved at" times kept with chunks and entities. Called from the mixins. */
public final class Timestamps {
	/** Key of an entity's timestamp in its saved data. */
	public static final String ENTITY_KEY = "elapsed:saved_at";

	private Timestamps() {
	}

	/** The value to save as a chunk's LastUpdate. */
	public static long chunkStamp(ServerLevel level, ChunkAccess chunk, long gameTime) {
		CatchupManager manager = Elapsed.manager(level.getServer());
		if (manager == null) {
			return gameTime;
		}
		ChunkAccess real = chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
		long debt = real instanceof ElapsedChunk elapsedChunk ? elapsedChunk.elapsed$debt() : 0L;
		return manager.clock().now() - debt;
	}

	/** Gives a chunk that has just been read from disk the time it has to catch up on. */
	public static void readChunkStamp(ServerLevel level, ChunkAccess chunk, long stamp) {
		if (chunk instanceof ImposterProtoChunk imposter && imposter.getWrapped() instanceof ElapsedChunk elapsedChunk) {
			elapsedChunk.elapsed$setDebt(debtFromStamp(level, stamp));
		}
	}

	/** How much a chunk or entity saved with the given timestamp has to catch up on. */
	public static long debtFromStamp(ServerLevel level, long stamp) {
		CatchupManager manager = Elapsed.manager(level.getServer());
		// Also while catching up is switched off: the clock stands still then, so this is only time from before
		if (manager == null || !isDimensionActive(level)) {
			return 0L;
		}
		return manager.clock().elapsedSince(stamp);
	}

	/** The timestamp to save with an entity under {@link #ENTITY_KEY}; empty for entities Elapsed does not catch up. */
	public static OptionalLong entityStamp(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof ElapsedEntity elapsedEntity)) {
			return OptionalLong.empty();
		}
		CatchupManager manager = Elapsed.manager(level.getServer());
		if (manager != null && manager.handlers().tracks(entity)) {
			return OptionalLong.of(manager.clock().now() - elapsedEntity.elapsed$debt());
		}
		return OptionalLong.empty();
	}

	/** Gives an entity that has just been loaded with a timestamp the time it has to catch up on. */
	public static void readEntityStamp(Entity entity, long stamp) {
		if (entity.level() instanceof ServerLevel level && entity instanceof ElapsedEntity elapsedEntity) {
			elapsedEntity.elapsed$setDebt(debtFromStamp(level, stamp));
		}
	}

	/** Whether this dimension catches up at all (chunks loading in other dimensions start counting afresh). */
	public static boolean isDimensionActive(ServerLevel level) {
		return ElapsedConfig.get().isDimensionEnabled(Versioned.dimensionId(level));
	}
}
