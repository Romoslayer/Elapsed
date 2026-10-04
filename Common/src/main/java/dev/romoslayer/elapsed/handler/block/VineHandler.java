package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.Growth;
import dev.romoslayer.elapsed.mixin.access.GrowingPlantBlockAccessor;
import dev.romoslayer.elapsed.mixin.access.GrowingPlantHeadBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Kelp, weeping vines, twisting vines, cave vines and other plants built on the same game class: the tip of the plant
 * grows one block on a random tick with a fixed chance (14% for kelp, 10% for the vines), until its age reaches 25.
 * Each new block is placed by the game's own rules, into space checked as it is now.
 */
public final class VineHandler implements BlockHandler<VineHandler.Vine> {
	private static final int MAX_AGE = GrowingPlantHeadBlock.MAX_AGE;

	public record Vine(BlockPos pos, BlockState state, Direction direction, int room, double chance, int steps) {
	}

	@Override
	public String category() {
		return CatchupCategory.CROPS;
	}

	@Override
	public boolean handles(BlockState state) {
		return state.getBlock() instanceof GrowingPlantHeadBlock && !Growth.overridesRandomTick(state.getBlock(), GrowingPlantHeadBlock.class)
				&& state.getValue(GrowingPlantHeadBlock.AGE) < MAX_AGE;
	}

	@Override
	public @Nullable Vine captureState(BlockTarget target, CatchupContext context) {
		if (!ElapsedConfig.get().crops.tallPlants) {
			return null;
		}
		BlockState state = target.state();
		GrowingPlantHeadBlock head = (GrowingPlantHeadBlock) state.getBlock();
		GrowingPlantHeadBlockAccessor access = (GrowingPlantHeadBlockAccessor) head;
		Direction direction = ((GrowingPlantBlockAccessor) head).elapsed$growthDirection();
		int ageRoom = MAX_AGE - state.getValue(GrowingPlantHeadBlock.AGE);
		int room = 0;
		BlockPos cursor = target.pos();
		while (room < ageRoom) {
			cursor = cursor.relative(direction);
			BlockState space = context.loadedBlockState(cursor);
			if (space == null || !access.elapsed$canGrowInto(space)) {
				break;
			}
			room++;
		}
		if (room == 0 || !Elapsed.platform().mayGrow(target.level(), target.pos().relative(direction), state)) {
			return null;
		}
		return new Vine(target.pos(), state, direction, room, access.elapsed$growPerTickProbability(), 0);
	}

	@Override
	public @Nullable Vine calculateProgress(Vine vine, long elapsedTicks, CatchupContext context) {
		double rate = Growth.plantRate(context, vine.pos, vine.state, elapsedTicks, vine.chance);
		int steps = Growth.sampleEvents(context.random(), rate, elapsedTicks, vine.room);
		return steps == 0 ? null : new Vine(vine.pos, vine.state, vine.direction, vine.room, vine.chance, steps);
	}

	@Override
	public void applyResult(BlockTarget target, Vine result, CatchupContext context) {
		if (!target.isUnchanged()) {
			return;
		}
		ServerLevel level = target.level();
		BlockPos tip = target.pos();
		int grown = 0;
		for (int i = 0; i < result.steps; i++) {
			BlockState tipState = level.getBlockState(tip);
			if (!(tipState.getBlock() instanceof GrowingPlantHeadBlock head) || tipState.getValue(GrowingPlantHeadBlock.AGE) >= MAX_AGE) {
				break;
			}
			GrowingPlantHeadBlockAccessor access = (GrowingPlantHeadBlockAccessor) head;
			BlockPos next = tip.relative(result.direction);
			BlockState space = context.loadedBlockState(next);
			if (space == null || !access.elapsed$canGrowInto(space)) {
				break;
			}
			level.setBlockAndUpdate(next, access.elapsed$getGrowIntoState(tipState, context.random()));
			tip = next;
			grown++;
		}
		if (context.isDebug()) {
			context.note(AgePlantHandler.name(target.state()) + " at " + target.pos().toShortString() + ": grew " + grown + " block(s)");
		}
	}
}
