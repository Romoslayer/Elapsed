package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.BlockTarget;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.api.ElapsedApi;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.core.Registrations;
import dev.romoslayer.elapsed.handler.Growth;
import dev.romoslayer.elapsed.mc.Versioned;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Plants that grow by counting up an age: wheat and the other crops, melon and pumpkin stems, nether wart, cocoa,
 * sweet berries, and blocks added through the config or {@link ElapsedApi#registerAgeProperty}. The number of growth
 * steps is drawn from the plant's vanilla growth chance over the elapsed time; the plant is only grown if it could
 * grow where it is now (light, ground, other mods allowing it).
 */
public final class AgePlantHandler implements BlockHandler<AgePlantHandler.Plant> {
	private static final double TWO_THIRDS = 2.0 / 3.0;

	private final Map<Block, Optional<Rule>> rules = Collections.synchronizedMap(new IdentityHashMap<>());
	private final Map<Block, Registrations.AgeRule> extraRules;

	private enum Light {
		NONE, AT, ABOVE
	}

	/**
	 * How one kind of plant grows. {@code fixedChance} is the chance per random tick; crops (negative) work theirs
	 * out from the farmland around them.
	 */
	private record Rule(@Nullable CropBlock crop, @Nullable IntegerProperty age, int maxAge, double fixedChance, double chanceFactor, Light light,
			int minLight, boolean stem) {
		int ageOf(BlockState state) {
			return this.crop != null ? this.crop.getAge(state) : state.getValue(this.age);
		}

		/** Crops go through their own getStateForAge, as in vanilla: the last stage of a torchflower is a different block. */
		BlockState withAge(BlockState state, int newAge) {
			return this.crop != null ? this.crop.getStateForAge(newAge) : state.setValue(this.age, newAge);
		}
	}

	/** The plant as captured. */
	public record Plant(BlockPos pos, BlockState state, Rule rule, int age, double chancePerRandomTick, int freeSides, int newAge, boolean fruit) {
	}

	public AgePlantHandler(Map<Block, Registrations.AgeRule> extraRules) {
		this.extraRules = extraRules;
	}

	/** Builds the extra rules from the API registrations and the config, the config winning. */
	public static Map<Block, Registrations.AgeRule> extraRules() {
		Map<Block, Registrations.AgeRule> rules = new IdentityHashMap<>(Registrations.ageRules());
		for (Map.Entry<String, ElapsedConfig.AgeBlock> entry : ElapsedConfig.get().ageBasedBlocks.entrySet()) {
			Optional<Block> block = Versioned.block(entry.getKey());
			if (block.isEmpty()) {
				// Probably a mod that is not installed
				continue;
			}
			Property<?> property = block.get().getStateDefinition().getProperty(entry.getValue().property);
			if (property instanceof IntegerProperty integer) {
				rules.put(block.get(), new Registrations.AgeRule(integer, entry.getValue().growthChance, entry.getValue().minLight));
			} else {
				Elapsed.LOGGER.warn("ageBasedBlocks: {} has no number property called \"{}\"; skipping it", entry.getKey(), entry.getValue().property);
			}
		}
		return rules;
	}

	@Override
	public String category() {
		return CatchupCategory.CROPS;
	}

	@Override
	public boolean handles(BlockState state) {
		return this.rule(state.getBlock()) != null;
	}

	private @Nullable Rule rule(Block block) {
		return this.rules.computeIfAbsent(block, this::findRule).orElse(null);
	}

	private Optional<Rule> findRule(Block block) {
		Registrations.AgeRule extra = this.extraRules.get(block);
		if (extra != null) {
			int max = Collections.max(extra.property().getPossibleValues());
			return Optional.of(new Rule(null, extra.property(), max, extra.growthChance(), 1.0, extra.minLight() > 0 ? Light.AT : Light.NONE, extra.minLight(), false));
		}
		if (block instanceof CropBlock crop) {
			double factor;
			if (block.getClass() == BeetrootBlock.class || block.getClass() == TorchflowerCropBlock.class) {
				// Both skip two random ticks in three
				factor = TWO_THIRDS;
			} else if (!Growth.overridesRandomTick(block, CropBlock.class)) {
				factor = 1.0;
			} else {
				return Optional.empty();
			}
			return Optional.of(new Rule(crop, null, crop.getMaxAge(), -1.0, factor, Light.AT, 9, false));
		}
		if (block instanceof StemBlock && !Growth.overridesRandomTick(block, StemBlock.class)) {
			return Optional.of(new Rule(null, StemBlock.AGE, StemBlock.MAX_AGE, -1.0, 1.0, Light.AT, 9, true));
		}
		if (block instanceof NetherWartBlock && !Growth.overridesRandomTick(block, NetherWartBlock.class)) {
			return Optional.of(new Rule(null, NetherWartBlock.AGE, NetherWartBlock.MAX_AGE, 0.1, 1.0, Light.NONE, 0, false));
		}
		if (block instanceof CocoaBlock && !Growth.overridesRandomTick(block, CocoaBlock.class)) {
			return Optional.of(new Rule(null, CocoaBlock.AGE, CocoaBlock.MAX_AGE, 0.2, 1.0, Light.NONE, 0, false));
		}
		if (block instanceof SweetBerryBushBlock && !Growth.overridesRandomTick(block, SweetBerryBushBlock.class)) {
			return Optional.of(new Rule(null, SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE, 0.2, 1.0, Light.ABOVE, 9, false));
		}
		return Optional.empty();
	}

	@Override
	public @Nullable Plant captureState(BlockTarget target, CatchupContext context) {
		Rule rule = this.rule(target.state().getBlock());
		if (rule == null) {
			return null;
		}
		int age = rule.ageOf(target.state());
		boolean fruit = rule.stem && ElapsedConfig.get().crops.stemFruit;
		if (age >= rule.maxAge && !fruit) {
			return null;
		}
		ServerLevel level = target.level();
		BlockPos pos = target.pos();
		if (!target.state().canSurvive(level, pos)) {
			return null;
		}
		if (rule.light == Light.AT && level.getRawBrightness(pos, 0) < rule.minLight
				|| rule.light == Light.ABOVE && level.getRawBrightness(pos.above(), 0) < rule.minLight) {
			return null;
		}
		if (!Elapsed.platform().mayGrow(level, pos, target.state())) {
			return null;
		}
		double chance = rule.fixedChance >= 0.0
				? rule.fixedChance
				: Growth.cropChance(Growth.cropGrowthSpeed(context, target.state().getBlock(), pos)) * rule.chanceFactor;
		int freeSides = fruit && target.state().getBlock() instanceof StemBlock stem ? freeSides(pos, stem, context).size() : 0;
		if (age >= rule.maxAge && freeSides == 0) {
			return null;
		}
		return new Plant(pos, target.state(), rule, age, chance, freeSides, age, false);
	}

	@Override
	public @Nullable Plant calculateProgress(Plant plant, long elapsedTicks, CatchupContext context) {
		Rule rule = plant.rule;
		double rate = Growth.plantRate(context, plant.pos, plant.state, elapsedTicks, plant.chancePerRandomTick);
		if (!(rate > 0.0)) {
			return null;
		}
		// One growth step after another, each after its own random wait, until the time runs out or the plant is grown
		double time = 0.0;
		int age = plant.age;
		while (age < rule.maxAge) {
			time += Growth.waitingTime(context.random(), rate);
			if (time > elapsedTicks) {
				break;
			}
			age++;
		}
		// A grown stem tries a random side on every successful tick, so fruit comes at the same rate times the share of
		// sides with room for it
		boolean fruit = age >= rule.maxAge && plant.freeSides > 0
				&& time + Growth.waitingTime(context.random(), rate * plant.freeSides / 4.0) <= elapsedTicks;
		if (age == plant.age && !fruit) {
			return null;
		}
		return new Plant(plant.pos, plant.state, rule, plant.age, plant.chancePerRandomTick, plant.freeSides, age, fruit);
	}

	@Override
	public void applyResult(BlockTarget target, Plant result, CatchupContext context) {
		if (!target.isUnchanged()) {
			return;
		}
		ServerLevel level = target.level();
		if (result.newAge != result.age) {
			level.setBlock(target.pos(), result.rule.withAge(target.state(), result.newAge), Block.UPDATE_CLIENTS);
			if (context.isDebug()) {
				context.note(name(target.state()) + " at " + target.pos().toShortString() + ": age " + result.age + " -> " + result.newAge);
			}
		}
		if (result.fruit && target.state().getBlock() instanceof StemBlock stem) {
			this.growFruit(level, target.pos(), stem, context);
		}
	}

	/** Sides of a stem where fruit can grow: air, on ground that carries it, all in loaded chunks. */
	private static List<Direction> freeSides(BlockPos pos, StemBlock stem, CatchupContext context) {
		List<Direction> free = new ArrayList<>(4);
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			BlockState sideState = context.loadedBlockState(side);
			BlockState ground = context.loadedBlockState(side.below());
			if (sideState != null && ground != null && sideState.isAir() && Stems.supportsFruit(stem, ground)) {
				free.add(direction);
			}
		}
		return free;
	}

	/** Grows fruit on one of the sides that still has room for it, chosen at random as the game does. */
	private void growFruit(ServerLevel level, BlockPos pos, StemBlock stem, CatchupContext context) {
		Optional<Block> fruit = Stems.fruit(stem, level);
		Optional<Block> attached = Stems.attachedStem(stem, level);
		List<Direction> free = freeSides(pos, stem, context);
		if (fruit.isEmpty() || attached.isEmpty() || free.isEmpty()) {
			return;
		}
		Direction direction = free.get(context.random().nextInt(free.size()));
		level.setBlockAndUpdate(pos.relative(direction), fruit.get().defaultBlockState());
		level.setBlockAndUpdate(pos, attached.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
		if (context.isDebug()) {
			context.note(name(stem.defaultBlockState()) + " at " + pos.toShortString() + " grew its fruit");
		}
	}

	static String name(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
