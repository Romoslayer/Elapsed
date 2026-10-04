package dev.romoslayer.elapsed.core;

/** Added to every entity: the time it spent unloaded that has not been caught up on yet (see {@link ElapsedChunk}). */
public interface ElapsedEntity {
	long elapsed$debt();

	void elapsed$setDebt(long ticks);
}
