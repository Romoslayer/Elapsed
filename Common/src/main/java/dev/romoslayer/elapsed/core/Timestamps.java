package dev.romoslayer.elapsed.core;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

	/** How much a chunk or entity saved with the given timestamp has to catch up on. */
	public static long debtFromStamp(ServerLevel level, long stamp) {
		CatchupManager manager = Elapsed.manager(level.getServer());
		// Also while catching up is switched off: the clock stands still then, so this is only time from before
		if (manager == null || !isDimensionActive(level)) {
			return 0L;
		}
		return manager.clock().elapsedSince(stamp);
	}

	public static void writeEntityStamp(Entity entity, ValueOutput output) {
		if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof ElapsedEntity elapsedEntity)) {
			return;
		}
		CatchupManager manager = Elapsed.manager(level.getServer());
		if (manager != null && manager.handlers().tracks(entity)) {
			output.putLong(ENTITY_KEY, manager.clock().now() - elapsedEntity.elapsed$debt());
		}
	}

	public static void readEntityStamp(Entity entity, ValueInput input) {
		if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof ElapsedEntity elapsedEntity)) {
			return;
		}
		Optional<Long> stamp = input.getLong(ENTITY_KEY);
		if (stamp.isPresent()) {
			elapsedEntity.elapsed$setDebt(debtFromStamp(level, stamp.get()));
		}
	}

	/** Whether this dimension catches up at all (chunks loading in other dimensions start counting afresh). */
	public static boolean isDimensionActive(ServerLevel level) {
		return ElapsedConfig.get().isDimensionEnabled(level.dimension().identifier().toString());
	}
}
