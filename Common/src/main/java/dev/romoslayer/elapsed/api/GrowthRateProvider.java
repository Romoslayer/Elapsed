package dev.romoslayer.elapsed.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a mod that changes how fast plants grow (seasons, climate) say how fast a plant would have grown on average
 * over the time it spent unloaded. Elapsed multiplies the growth rate by the result of every registered provider.
 */
@FunctionalInterface
public interface GrowthRateProvider {
	/**
	 * The average growth speed (1 = normal, 0 = no growth) of this plant at this spot over the {@code elapsedTicks}
	 * game ticks leading up to now. Return 1 when the provider has no opinion. Must be cheap and must not load chunks.
	 */
	double averageGrowthMultiplier(ServerLevel level, BlockPos pos, BlockState state, long elapsedTicks);
}
