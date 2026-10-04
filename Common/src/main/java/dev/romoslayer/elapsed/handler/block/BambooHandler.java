package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.Growth;
import dev.romoslayer.elapsed.mixin.access.BambooStalkBlockInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Bamboo: the top of a stalk that is still growing adds a block on one random tick in three, up to 16 high (each stalk
 * picks where it stops). The number of growth steps is drawn for the elapsed time, then each is done by the game's own
 * growBamboo, checked first against the world as it is now.
 */
public final class BambooHandler implements BlockHandler<BambooHandler.Stalk> {
	private static final int MAX_HEIGHT = BambooStalkBlock.MAX_HEIGHT;

	public record Stalk(BlockPos pos, BlockState state, int height, int steps) {
	}

	@Override
	public String category() {
		return CatchupCategory.CROPS;
	}

	@Override
	public boolean handles(BlockState state) {
		return state.getBlock() instanceof BambooStalkBlock && !Growth.overridesRandomTick(state.getBlock(), BambooStalkBlock.class)
				&& state.getValue(BambooStalkBlock.STAGE) == BambooStalkBlock.STAGE_GROWING;
	}

	@Override
	public @Nullable Stalk captureState(BlockTarget target, CatchupContext context) {
		if (!ElapsedConfig.get().crops.tallPlants) {
			return null;
		}
		Stalk stalk = this.growable(target.level(), target.pos(), target.state(), context);
		return stalk != null && Elapsed.platform().mayGrow(target.level(), target.pos(), target.state()) ? stalk : null;
	}

	private @Nullable Stalk growable(ServerLevel level, BlockPos pos, BlockState state, CatchupContext context) {
		if (!(state.getBlock() instanceof BambooStalkBlock bamboo) || state.getValue(BambooStalkBlock.STAGE) != BambooStalkBlock.STAGE_GROWING) {
			return null;
		}
		BlockState above = context.loadedBlockState(pos.above());
		if (above == null || !above.isAir() || level.getRawBrightness(pos.above(), 0) < 9) {
			return null;
		}
		int height = ((BambooStalkBlockInvoker) bamboo).elapsed$getHeightBelowUpToMax(level, pos) + 1;
		return height < MAX_HEIGHT ? new Stalk(pos, state, height, 0) : null;
	}

	@Override
	public @Nullable Stalk calculateProgress(Stalk stalk, long elapsedTicks, CatchupContext context) {
		double rate = Growth.plantRate(context, stalk.pos, stalk.state, elapsedTicks, 1.0 / 3.0);
		int steps = Growth.sampleEvents(context.random(), rate, elapsedTicks, MAX_HEIGHT - stalk.height);
		return steps == 0 ? null : new Stalk(stalk.pos, stalk.state, stalk.height, steps);
	}

	@Override
	public void applyResult(BlockTarget target, Stalk result, CatchupContext context) {
		if (!target.isUnchanged()) {
			return;
		}
		ServerLevel level = target.level();
		BlockPos top = target.pos();
		int grown = 0;
		for (int i = 0; i < result.steps; i++) {
			BlockState state = level.getBlockState(top);
			Stalk now = this.growable(level, top, state, context);
			if (now == null) {
				break;
			}
			((BambooStalkBlockInvoker) state.getBlock()).elapsed$growBamboo(state, level, top, context.random(), now.height);
			top = top.above();
			grown++;
		}
		if (context.isDebug()) {
			context.note("bamboo at " + target.pos().toShortString() + ": grew " + grown + " block(s)");
		}
	}
}
