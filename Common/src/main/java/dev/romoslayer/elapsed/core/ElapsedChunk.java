package dev.romoslayer.elapsed.core;

/**
 * Added to every loaded chunk: the time it spent unloaded that has not been caught up on yet. While this is above
 * zero the chunk is saved with a timestamp moved back by the same amount, so the time is never lost and never
 * counted twice, whether or not the catch-up has run when the chunk is saved.
 */
public interface ElapsedChunk {
	long elapsed$debt();

	void elapsed$setDebt(long ticks);
}
