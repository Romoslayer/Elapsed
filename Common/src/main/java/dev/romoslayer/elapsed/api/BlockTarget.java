package dev.romoslayer.elapsed.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** A block being caught up: where it is and the state it had when its chunk was looked at. */
public record BlockTarget(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state) {
	/** Whether the block is still exactly as it was captured. */
	public boolean isUnchanged() {
		return this.level.getBlockState(this.pos) == this.state;
	}
}
