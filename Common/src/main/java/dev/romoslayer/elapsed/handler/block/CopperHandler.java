package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.Growth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Copper oxidation. On a random tick an unwaxed copper block has a 5.7% chance to try to age one stage. The attempt
 * fails outright if any copper within 4 blocks is younger, and otherwise succeeds with a chance that falls the more
 * same-age copper is around (((older + 1) / (older + same + 1))^2, times 0.75 for fresh copper). Elapsed looks at the
 * neighbours once, then draws the time each stage would take with that chance - so a block that overtakes its
 * neighbours stops, as it does in the game. Waxed copper is a different block and is never touched.
 */
public final class CopperHandler implements BlockHandler<CopperHandler.Copper> {
	private static final float ATTEMPT_CHANCE = 0.05688889F;
	private static final int SCAN_DISTANCE = ChangeOverTimeBlock.SCAN_DISTANCE;
	private static final int AGES = WeatheringCopper.WeatherState.values().length;

	/**
	 * A copper block as captured: its stages from now on, and how many copper blocks of each age are near it.
	 * {@code stages.get(0)} is the current state.
	 */
	public record Copper(BlockPos pos, List<BlockState> stages, int age, int[] neighboursByAge, int newStage) {
	}

	@Override
	public String category() {
		return CatchupCategory.COPPER;
	}

	@Override
	public boolean handles(BlockState state) {
		// Copper chests and statues have block entities and rules of their own; doors age from their lower half
		return state.getBlock() instanceof WeatheringCopper copper && !state.hasBlockEntity() && copper.getNext(state).isPresent()
				&& state.isRandomlyTicking()
				&& (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
	}

	@Override
	public int cost() {
		return 4;
	}

	@Override
	public @Nullable Copper captureState(BlockTarget target, CatchupContext context) {
		BlockState state = target.state();
		WeatheringCopper copper = (WeatheringCopper) state.getBlock();
		int age = copper.getAge().ordinal();
		List<BlockState> stages = new ArrayList<>();
		stages.add(state);
		BlockState current = state;
		while (stages.size() < AGES) {
			if (!(current.getBlock() instanceof WeatheringCopper weathering)) {
				break;
			}
			Optional<BlockState> next = weathering.getNext(current);
			if (next.isEmpty()) {
				break;
			}
			current = next.get();
			stages.add(current);
		}
		int[] neighbours = new int[AGES];
		BlockPos pos = target.pos();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -SCAN_DISTANCE; dx <= SCAN_DISTANCE; dx++) {
			int restX = SCAN_DISTANCE - Math.abs(dx);
			for (int dy = -restX; dy <= restX; dy++) {
				int restY = restX - Math.abs(dy);
				for (int dz = -restY; dz <= restY; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockState neighbour = context.loadedBlockState(cursor.setWithOffset(pos, dx, dy, dz));
					// Only copper ages with copper (the same kind of ageing, as the game checks)
					if (neighbour != null && neighbour.getBlock() instanceof ChangeOverTimeBlock<?> other
							&& other.getAge().getClass() == copper.getAge().getClass()) {
						neighbours[Math.min(other.getAge().ordinal(), AGES - 1)]++;
					}
				}
			}
		}
		return stages.size() > 1 ? new Copper(pos, List.copyOf(stages), age, neighbours, 0) : null;
	}

	@Override
	public @Nullable Copper calculateProgress(Copper copper, long elapsedTicks, CatchupContext context) {
		double multiplier = ElapsedConfig.get().copper.oxidationMultiplier;
		double time = 0.0;
		int stage = 0;
		while (stage + 1 < copper.stages.size()) {
			int age = copper.age + stage;
			int younger = 0;
			int same = 0;
			int older = 0;
			for (int i = 0; i < AGES; i++) {
				if (i < age) {
					younger += copper.neighboursByAge[i];
				} else if (i == age) {
					same += copper.neighboursByAge[i];
				} else {
					older += copper.neighboursByAge[i];
				}
			}
			if (younger > 0) {
				// Every attempt would be abandoned
				break;
			}
			float chance = (float) (older + 1) / (older + same + 1);
			float modifier = ((WeatheringCopper) copper.stages.get(stage).getBlock()).getChanceModifier();
			double rate = context.randomTickChance() * ATTEMPT_CHANCE * chance * chance * modifier * multiplier;
			if (!(rate > 0.0)) {
				break;
			}
			time += Growth.waitingTime(context.random(), rate);
			if (time > elapsedTicks) {
				break;
			}
			stage++;
		}
		return stage == 0 ? null : new Copper(copper.pos, copper.stages, copper.age, copper.neighboursByAge, stage);
	}

	@Override
	public void applyResult(BlockTarget target, Copper result, CatchupContext context) {
		if (target.isUnchanged()) {
			BlockState aged = result.stages.get(result.newStage);
			target.level().setBlockAndUpdate(target.pos(), aged);
			if (context.isDebug()) {
				context.note(AgePlantHandler.name(target.state()) + " at " + target.pos().toShortString() + " oxidised to " + AgePlantHandler.name(aged));
			}
		}
	}
}
