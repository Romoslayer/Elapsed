package dev.romoslayer.elapsed.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Catches up a plain block (no block entity needed) such as a crop. Elapsed only looks for these in chunk sections
 * whose palette contains a state the handler {@link #handles}, so that check must be cheap and depend on the block
 * state alone.
 */
public interface BlockHandler<S> extends OfflineProgressHandler<BlockTarget, S> {
	boolean handles(BlockState state);

	@Override
	default boolean canHandle(BlockTarget target) {
		return this.handles(target.state());
	}

	/** Rough cost of one catch-up, counted against the per-tick work limit. */
	default int cost() {
		return 1;
	}
}
