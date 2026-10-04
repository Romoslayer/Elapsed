package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.handler.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Saplings have two stages: a successful random tick (one in seven, with enough light) moves a stage-0 sapling to
 * stage 1, and the next one grows the tree. Elapsed only does the first step. Growing a tree places many blocks and
 * needs room that may have changed, so that is left to the game, which will do it on one of the sapling's next random
 * ticks now that the chunk is loaded.
 */
public final class SaplingHandler implements BlockHandler<SaplingHandler.Sapling> {
	/** Share of the day with enough sky light for a sapling lit only by the sky. */
	private static final double DAYLIGHT_SHARE = 0.5;

	public record Sapling(BlockPos pos, BlockState state, double lightShare) {
	}

	@Override
	public String category() {
		return CatchupCategory.SAPLINGS;
	}

	@Override
	public boolean handles(BlockState state) {
		return state.getBlock() instanceof SaplingBlock && !Growth.overridesRandomTick(state.getBlock(), SaplingBlock.class)
				&& state.getValue(SaplingBlock.STAGE) == 0;
	}

	@Override
	public @Nullable Sapling captureState(BlockTarget target, CatchupContext context) {
		BlockPos above = target.pos().above();
		double lightShare;
		if (target.level().getBrightness(LightLayer.BLOCK, above) >= 9) {
			lightShare = 1.0;
		} else if (target.level().getRawBrightness(above, 0) >= 9) {
			lightShare = DAYLIGHT_SHARE;
		} else {
			return null;
		}
		if (!target.state().canSurvive(target.level(), target.pos()) || !Elapsed.platform().mayGrow(target.level(), target.pos(), target.state())) {
			return null;
		}
		return new Sapling(target.pos(), target.state(), lightShare);
	}

	@Override
	public @Nullable Sapling calculateProgress(Sapling sapling, long elapsedTicks, CatchupContext context) {
		double rate = context.randomTickChance() / 7.0 * sapling.lightShare * context.growthMultiplier(sapling.pos, sapling.state, elapsedTicks);
		return Growth.sampleEvents(context.random(), rate, elapsedTicks, 1) == 1 ? sapling : null;
	}

	@Override
	public void applyResult(BlockTarget target, Sapling result, CatchupContext context) {
		if (target.isUnchanged()) {
			target.level().setBlock(target.pos(), target.state().setValue(SaplingBlock.STAGE, 1), Block.UPDATE_INVISIBLE);
			if (context.isDebug()) {
				context.note(AgePlantHandler.name(target.state()) + " at " + target.pos().toShortString() + ": ready to grow");
			}
		}
	}
}
