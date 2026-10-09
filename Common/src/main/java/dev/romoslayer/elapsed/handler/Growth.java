package dev.romoslayer.elapsed.handler;

import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.mc.Versioned;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The maths behind plant catch-up. Random ticks reach a block as a Poisson process (each game tick a block has a
 * small, fixed chance of being picked), so the successful growth steps of a plant over a stretch of time are a
 * Poisson process too, with the rate scaled by the chance that a tick makes it grow. Elapsed draws from that
 * distribution directly - a handful of random numbers per plant - instead of replaying the ticks.
 */
public final class Growth {
	private static final Map<Class<?>, Boolean> OVERRIDES = new ConcurrentHashMap<>();

	private Growth() {
	}

	/**
	 * How many events of a process with the given rate (per tick) happen within {@code ticks}, stopping at
	 * {@code limit}. Costs at most {@code limit} + 1 random numbers whatever the time span.
	 */
	public static int sampleEvents(RandomSource random, double ratePerTick, long ticks, int limit) {
		if (!(ratePerTick > 0.0) || ticks <= 0 || limit <= 0) {
			return 0;
		}
		double time = 0.0;
		int events = 0;
		while (events < limit) {
			time += waitingTime(random, ratePerTick);
			if (time > ticks) {
				break;
			}
			events++;
		}
		return events;
	}

	/** Time until the next event of a process with the given rate (exponentially distributed). */
	public static double waitingTime(RandomSource random, double ratePerTick) {
		return -Math.log(1.0 - random.nextDouble()) / ratePerTick;
	}

	/** The rate (per tick) at which random ticks make a plant grow, including the config and season multipliers. */
	public static double plantRate(CatchupContext context, BlockPos pos, BlockState state, long elapsedTicks, double chancePerRandomTick) {
		double base = context.randomTickChance() * chancePerRandomTick * ElapsedConfig.get().crops.growthMultiplier;
		if (base <= 0.0) {
			return 0.0;
		}
		return base * context.growthMultiplier(pos, state, elapsedTicks);
	}

	/**
	 * The vanilla crop growth speed (CropBlock.getGrowthSpeed): better on moist farmland, worse when planted in
	 * unbroken rows of the same crop. Neighbours in unloaded chunks count as missing.
	 */
	public static float cropGrowthSpeed(CatchupContext context, Block block, BlockPos pos) {
		float speed = 1.0F;
		BlockPos below = pos.below();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				float soilSpeed = 0.0F;
				BlockState soil = context.loadedBlockState(below.offset(dx, 0, dz));
				if (soil != null && Versioned.growsCrops(soil)) {
					soilSpeed = 1.0F;
					if (soil.hasProperty(BlockStateProperties.MOISTURE) && soil.getValue(BlockStateProperties.MOISTURE) > 0) {
						soilSpeed = 3.0F;
					}
				}
				if (dx != 0 || dz != 0) {
					soilSpeed /= 4.0F;
				}
				speed += soilSpeed;
			}
		}
		boolean westOrEast = is(context, pos.west(), block) || is(context, pos.east(), block);
		boolean northOrSouth = is(context, pos.north(), block) || is(context, pos.south(), block);
		if (westOrEast && northOrSouth) {
			speed /= 2.0F;
		} else if (is(context, pos.west().north(), block) || is(context, pos.east().north(), block) || is(context, pos.east().south(), block)
				|| is(context, pos.west().south(), block)) {
			speed /= 2.0F;
		}
		return speed;
	}

	/** Chance that one random tick grows a crop with the given growth speed (vanilla: 1 in 25 / speed + 1). */
	public static double cropChance(float growthSpeed) {
		return 1.0 / ((int) (25.0F / growthSpeed) + 1);
	}

	private static boolean is(CatchupContext context, BlockPos pos, Block block) {
		BlockState state = context.loadedBlockState(pos);
		return state != null && state.is(block);
	}

	/**
	 * Whether a block class changes how random ticks work compared with {@code base}. A modded crop that does is left
	 * alone (its own rules are unknown) unless a handler for it is registered.
	 */
	public static boolean overridesRandomTick(Block block, Class<? extends Block> base) {
		return OVERRIDES.computeIfAbsent(block.getClass(), type -> {
			for (Class<?> current = type; current != null && current != base; current = current.getSuperclass()) {
				for (Method method : current.getDeclaredMethods()) {
					if (method.getName().equals("randomTick") && method.getParameterCount() == 4) {
						return true;
					}
				}
			}
			return false;
		});
	}
}
