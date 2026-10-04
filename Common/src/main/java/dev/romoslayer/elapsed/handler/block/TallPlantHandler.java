package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Sugar cane and cactus. Only the top block of a column grows: every random tick it counts its age up, and at the
 * sixteenth it puts a new block on top, until the column is three high. Cactus can grow a flower instead when it is
 * half way there. The growth steps are drawn for the whole elapsed time and then placed one by one, each placement
 * checked against the world as it is now.
 */
public final class TallPlantHandler implements BlockHandler<TallPlantHandler.Column> {
	private static final int MAX_HEIGHT = 3;
	private static final int MAX_AGE = 15;
	private static final int CACTUS_FLOWER_AGE = 8;
	/** Enough random ticks to take a fresh column to full height. */
	private static final int MAX_STEPS = (MAX_AGE + 1) * MAX_HEIGHT;

	/**
	 * A column as captured, and what to do to it: {@code newBlocks} to stack on top, then the top block's age, and
	 * whether a cactus flower crowns it.
	 */
	public record Column(BlockPos pos, Block block, int height, int age, int room, int newBlocks, int topAge, boolean flower) {
	}

	@Override
	public String category() {
		return CatchupCategory.CROPS;
	}

	@Override
	public boolean handles(BlockState state) {
		Block block = state.getBlock();
		return block instanceof SugarCaneBlock && !Growth.overridesRandomTick(block, SugarCaneBlock.class)
				|| block instanceof CactusBlock && !Growth.overridesRandomTick(block, CactusBlock.class);
	}

	@Override
	public @Nullable Column captureState(BlockTarget target, CatchupContext context) {
		if (!ElapsedConfig.get().crops.tallPlants) {
			return null;
		}
		ServerLevel level = target.level();
		BlockPos pos = target.pos();
		Block block = target.state().getBlock();
		BlockState above = context.loadedBlockState(pos.above());
		// Only the top of a column grows
		if (above == null || !above.isAir() || !target.state().canSurvive(level, pos)) {
			return null;
		}
		int height = 1;
		while (height < MAX_HEIGHT) {
			BlockState below = context.loadedBlockState(pos.below(height));
			if (below == null || !below.is(block)) {
				break;
			}
			height++;
		}
		int age = target.state().getValue(BlockStateProperties.AGE_15);
		boolean cactus = block instanceof CactusBlock;
		// A full-grown cactus at full height stops; sugar cane at full height does nothing at all
		if (height >= MAX_HEIGHT && (!cactus || age == MAX_AGE)) {
			return null;
		}
		// Free space above the top, one more than the column can grow (a cactus at full height can still flower)
		int room = 0;
		while (room < MAX_HEIGHT - height + 1) {
			BlockState space = context.loadedBlockState(pos.above(room + 1));
			if (space == null || !space.isAir()) {
				break;
			}
			room++;
		}
		if (!Elapsed.platform().mayGrow(level, cactus ? pos.above() : pos, target.state())) {
			return null;
		}
		return new Column(pos, block, height, age, room, 0, age, false);
	}

	@Override
	public @Nullable Column calculateProgress(Column column, long elapsedTicks, CatchupContext context) {
		boolean cactus = column.block instanceof CactusBlock;
		double rate = Growth.plantRate(context, column.pos, column.block.defaultBlockState(), elapsedTicks, 1.0);
		int steps = Growth.sampleEvents(context.random(), rate, elapsedTicks, MAX_STEPS);
		int height = column.height;
		int age = column.age;
		int newBlocks = 0;
		boolean flower = false;
		boolean flowers = ElapsedConfig.get().crops.cactusFlowers;
		for (int i = 0; i < steps; i++) {
			if (newBlocks >= column.room) {
				// The top has something on it now; its random ticks do nothing
				break;
			}
			if (cactus) {
				// CactusBlock.randomTick, for the current top of the column
				if (height >= MAX_HEIGHT && age == MAX_AGE) {
					break;
				}
				if (age == CACTUS_FLOWER_AGE && flowers) {
					if (context.random().nextDouble() <= (height >= MAX_HEIGHT ? 0.25 : 0.1)) {
						flower = true;
					}
				} else if (age == MAX_AGE && height < MAX_HEIGHT) {
					newBlocks++;
					height++;
					// The new top starts at 0 (and the one below it is reset to 0)
					age = 0;
					continue;
				}
				if (age < MAX_AGE) {
					age++;
				}
				if (flower) {
					// Nothing grows on top of a flower
					break;
				}
			} else {
				// SugarCaneBlock.randomTick
				if (height >= MAX_HEIGHT) {
					break;
				}
				if (age == MAX_AGE) {
					newBlocks++;
					height++;
					age = 0;
				} else {
					age++;
				}
			}
		}
		if (newBlocks == 0 && age == column.age && !flower) {
			return null;
		}
		return new Column(column.pos, column.block, column.height, column.age, column.room, newBlocks, age, flower);
	}

	@Override
	public void applyResult(BlockTarget target, Column result, CatchupContext context) {
		if (!target.isUnchanged()) {
			return;
		}
		ServerLevel level = target.level();
		BlockPos top = target.pos();
		int placed = 0;
		for (int i = 0; i < result.newBlocks; i++) {
			BlockPos above = top.above();
			BlockState space = context.loadedBlockState(above);
			BlockState grown = result.block.defaultBlockState();
			if (space == null || !space.isAir() || !grown.canSurvive(level, above)) {
				break;
			}
			level.setBlock(top, level.getBlockState(top).setValue(BlockStateProperties.AGE_15, 0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlockAndUpdate(above, grown);
			top = above;
			placed++;
		}
		// If a placement failed the top block keeps waiting at full age, as it would have
		int topAge = placed == result.newBlocks ? result.topAge : MAX_AGE;
		BlockState topState = level.getBlockState(top);
		if (topState.is(result.block) && topState.getValue(BlockStateProperties.AGE_15) != topAge) {
			level.setBlock(top, topState.setValue(BlockStateProperties.AGE_15, topAge), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		boolean flowered = false;
		if (result.flower && placed == result.newBlocks) {
			BlockPos above = top.above();
			BlockState space = context.loadedBlockState(above);
			if (space != null && space.isAir() && result.block.defaultBlockState().canSurvive(level, above)) {
				level.setBlockAndUpdate(above, Blocks.CACTUS_FLOWER.defaultBlockState());
				flowered = true;
			}
		}
		if (context.isDebug()) {
			context.note(AgePlantHandler.name(target.state()) + " at " + target.pos().toShortString() + ": " + placed + " new block(s), top age "
				+ result.age + " -> " + topAge + (flowered ? ", flowered" : ""));
		}
	}
}
