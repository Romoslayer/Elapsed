package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.blockentity.Cooking.FurnaceRecipe;
import dev.romoslayer.elapsed.mc.Versioned;
import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Furnaces, smokers and blast furnaces (and furnaces from other mods built on the same game class). The furnace's own
 * tick is followed exactly on a copy of its contents, but the long stretches in which nothing happens except a timer
 * counting (a fuel item burning down, an item part-way through cooking) are skipped in one step. So a furnace that
 * smelted 64 items in the elapsed time costs a few hundred steps, not hundreds of thousands of ticks, and stops for good
 * at the same moment the real one would have: out of fuel, out of input, or with a full output slot.
 */
public final class FurnaceHandler implements BlockEntityHandler<FurnaceHandler.Furnace> {
	/** A furnace's own logic, step by step, never needs more than a few steps per item cooked; this is a safety net. */
	private static final int MAX_STEPS = 100_000;
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_RESULT = 2;

	/** A furnace's contents and timers (copies; changing them does not change the furnace). */
	public static final class Furnace {
		// Only read from: recipe and fuel lookups go through it, as mods may change them
		final AbstractFurnaceBlockEntity source;
		final NonNullList<ItemStack> items;
		int litTimeRemaining;
		int litTotalTime;
		int cookingTimer;
		int cookingTotalTime;
		float speedMultiplier;
		final int maxStackSize;
		final Map<FurnaceRecipe, Integer> recipesUsed = new HashMap<>();
		final List<ItemStack> drops = new ArrayList<>();
		int cooked;
		int fuelUsed;

		Furnace(AbstractFurnaceBlockEntity source, NonNullList<ItemStack> items, int litTimeRemaining, int litTotalTime, int cookingTimer, int cookingTotalTime, float speedMultiplier,
				int maxStackSize) {
			this.source = source;
			this.items = items;
			this.litTimeRemaining = litTimeRemaining;
			this.litTotalTime = litTotalTime;
			this.cookingTimer = cookingTimer;
			this.cookingTotalTime = cookingTotalTime;
			this.speedMultiplier = speedMultiplier;
			this.maxStackSize = maxStackSize;
		}
	}

	@Override
	public String category() {
		return CatchupCategory.FURNACES;
	}

	@Override
	public boolean canHandle(BlockEntity target) {
		ElapsedConfig.Furnaces config = ElapsedConfig.get().furnaces;
		if (!(target instanceof AbstractFurnaceBlockEntity)) {
			return false;
		}
		Class<?> type = target.getClass();
		if (type == FurnaceBlockEntity.class) {
			return config.furnaces;
		}
		if (type == SmokerBlockEntity.class) {
			return config.smokers;
		}
		if (type == BlastFurnaceBlockEntity.class) {
			return config.blastFurnaces;
		}
		return config.otherFurnaces;
	}

	@Override
	public @Nullable Furnace captureState(BlockEntity target, CatchupContext context) {
		AbstractFurnaceBlockEntityAccessor access = (AbstractFurnaceBlockEntityAccessor) target;
		NonNullList<ItemStack> live = access.elapsed$items();
		if (live.size() < 3) {
			return null;
		}
		boolean lit = access.elapsed$litTimeRemaining() > 0;
		// An unlit furnace with nothing to cook and nothing part-cooked has nothing to catch up on
		if (!lit && access.elapsed$cookingTimer() == 0 && (live.get(SLOT_INPUT).isEmpty() || live.get(SLOT_FUEL).isEmpty())) {
			return null;
		}
		NonNullList<ItemStack> items = NonNullList.withSize(live.size(), ItemStack.EMPTY);
		for (int i = 0; i < live.size(); i++) {
			items.set(i, live.get(i).copy());
		}
		return new Furnace((AbstractFurnaceBlockEntity) target, items, access.elapsed$litTimeRemaining(), access.elapsed$litTotalTime(), access.elapsed$cookingTimer(),
				access.elapsed$cookingTotalTime(), FurnaceVersion.storedSpeed(access), ((AbstractFurnaceBlockEntity) target).getMaxStackSize());
	}

	@Override
	public @Nullable Furnace calculateProgress(Furnace f, long elapsedTicks, CatchupContext context) {
		AbstractFurnaceBlockEntityAccessor access = (AbstractFurnaceBlockEntityAccessor) f.source;
		ServerLevel level = context.level();
		long remaining = elapsedTicks;
		int steps = 0;
		boolean changed = false;
		while (remaining > 0 && steps++ < MAX_STEPS) {
			ItemStack input = f.items.get(SLOT_INPUT);
			ItemStack fuel = f.items.get(SLOT_FUEL);
			FurnaceRecipe recipe = input.isEmpty() ? null : Cooking.furnaceRecipe(access, level, input);
			ItemStack result = recipe == null ? ItemStack.EMPTY : Cooking.result(recipe, input, level);
			boolean canCook = recipe != null && !result.isEmpty() && canBurn(f, result);
			if (f.litTimeRemaining >= 2) {
				// Burning, and still burning after the next tick: jump to just before the fuel runs out or an item finishes
				long jump;
				if (canCook) {
					jump = Math.min(f.litTimeRemaining - 1, f.cookingTotalTime - 1 - f.cookingTimer);
				} else {
					jump = f.litTimeRemaining - 1;
				}
				jump = Math.min(jump, remaining);
				if (jump > 0) {
					f.litTimeRemaining -= (int) jump;
					if (canCook) {
						f.cookingTimer += (int) jump;
					} else if (input.isEmpty() || recipe != null || FurnaceVersion.RESETS_PROGRESS_WITHOUT_RECIPE) {
						// No input, or a recipe that cannot finish: progress is lost (26.x keeps it for an input without a recipe)
						f.cookingTimer = 0;
					}
					remaining -= jump;
					changed = true;
					continue;
				}
			} else if (f.litTimeRemaining == 0 && !(!fuel.isEmpty() && !input.isEmpty())) {
				// Out: cooking progress cools off two ticks at a time, then nothing more ever happens
				if (f.cookingTimer <= 0) {
					break;
				}
				long ticks = Math.min(remaining, (f.cookingTimer + 1) / 2);
				f.cookingTimer = (int) Math.max(0, f.cookingTimer - 2 * ticks);
				f.cookingTimer = Mth.clamp(f.cookingTimer, 0, Math.max(0, f.cookingTotalTime));
				remaining -= ticks;
				changed = true;
				continue;
			} else if (f.litTimeRemaining == 0 && !canCook) {
				// Fuel and input but nothing to make (or no room for it): it never lights
				if ((recipe != null || FurnaceVersion.RESETS_PROGRESS_WITHOUT_RECIPE) && f.cookingTimer != 0) {
					f.cookingTimer = 0;
					changed = true;
				}
				break;
			} else if (f.litTimeRemaining == 0 && FurnaceVersion.burnDuration(access, level, fuel) <= 0) {
				// Whatever is in the fuel slot (an empty bucket, say) does not burn: one tick settles it for good
				this.tick(access, level, f, input, recipe, result, canCook);
				changed = true;
				break;
			}
			this.tick(access, level, f, input, recipe, result, canCook);
			remaining--;
			changed = true;
		}
		return changed ? f : null;
	}

	/** One tick of AbstractFurnaceBlockEntity.serverTick, on the copy. */
	private void tick(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, Furnace f, ItemStack input,
			@Nullable FurnaceRecipe recipe, ItemStack result, boolean canCook) {
		boolean isLit;
		if (f.litTimeRemaining > 0) {
			f.litTimeRemaining--;
			isLit = f.litTimeRemaining > 0;
		} else {
			isLit = false;
		}
		ItemStack fuel = f.items.get(SLOT_FUEL);
		boolean hasIngredient = !input.isEmpty();
		boolean hasFuel = !fuel.isEmpty();
		if (isLit || hasFuel && hasIngredient) {
			if (hasIngredient) {
				if (recipe != null) {
					if (canCook) {
						if (!isLit) {
							int newLitTime = FurnaceVersion.burnDuration(access, level, fuel);
							float newSpeed = FurnaceVersion.speedMultiplier(access, level, fuel);
							f.litTimeRemaining = newLitTime;
							f.litTotalTime = newLitTime;
							f.speedMultiplier = newSpeed;
							if (FurnaceVersion.RESCALES_ON_RELIGHT && f.cookingTotalTime > 0 && f.cookingTimer < f.cookingTotalTime) {
								float completion = (float) f.cookingTimer / f.cookingTotalTime;
								f.cookingTotalTime = totalCookTime(recipe, f);
								f.cookingTimer = (int) Math.ceil(completion * f.cookingTotalTime);
							}
							if (newLitTime > 0) {
								consumeFuel(f, fuel);
								isLit = true;
							}
						}
						if (isLit) {
							f.cookingTimer++;
							if (f.cookingTimer >= f.cookingTotalTime) {
								f.cookingTimer = 0;
								f.cookingTotalTime = totalCookTime(recipe, f);
								burn(f, input, result);
								f.recipesUsed.merge(recipe, 1, Integer::sum);
								f.cooked++;
							}
						} else {
							f.cookingTimer = 0;
						}
					} else {
						f.cookingTimer = 0;
					}
				} else if (FurnaceVersion.RESETS_PROGRESS_WITHOUT_RECIPE) {
					f.cookingTimer = 0;
				}
			} else {
				f.cookingTimer = 0;
			}
		} else if (f.cookingTimer > 0) {
			f.cookingTimer = Mth.clamp(f.cookingTimer - 2, 0, f.cookingTotalTime);
		}
	}

	private static boolean canBurn(Furnace f, ItemStack result) {
		ItemStack output = f.items.get(SLOT_RESULT);
		if (output.isEmpty()) {
			return true;
		}
		if (!Cooking.stacksWith(output, result)) {
			return false;
		}
		return output.getCount() + result.getCount() <= Math.min(f.maxStackSize, result.getMaxStackSize());
	}

	private static int totalCookTime(FurnaceRecipe recipe, Furnace f) {
		int time = Cooking.cookingTime(recipe);
		return f.speedMultiplier > 0.0F ? (int) Math.ceil(time / f.speedMultiplier) : time;
	}

	private static void consumeFuel(Furnace f, ItemStack fuel) {
		ItemStack remainder = Versioned.craftingRemainder(fuel);
		ItemStack newFuel = fuel;
		if (remainder != null && FurnaceVersion.swapsFuelForRemainder()) {
			// The whole slot becomes the remainder, however many fuel items were in it
			newFuel = remainder;
		} else {
			fuel.shrink(1);
			if (remainder != null) {
				if (fuel.isEmpty()) {
					newFuel = remainder;
				} else if (FurnaceVersion.DROPS_REMAINDER_FROM_STACK) {
					f.drops.add(remainder);
				}
			}
		}
		f.items.set(SLOT_FUEL, newFuel);
		f.fuelUsed++;
	}

	private static void burn(Furnace f, ItemStack input, ItemStack result) {
		ItemStack output = f.items.get(SLOT_RESULT);
		if (output.isEmpty()) {
			f.items.set(SLOT_RESULT, result.copy());
		} else {
			output.grow(result.getCount());
		}
		if (input.is(Items.WET_SPONGE) && !f.items.get(SLOT_FUEL).isEmpty() && f.items.get(SLOT_FUEL).is(Items.BUCKET)) {
			f.items.set(SLOT_FUEL, new ItemStack(Items.WATER_BUCKET));
		}
		input.shrink(1);
	}

	@Override
	public void applyResult(BlockEntity target, Furnace result, CatchupContext context) {
		AbstractFurnaceBlockEntityAccessor access = (AbstractFurnaceBlockEntityAccessor) target;
		NonNullList<ItemStack> live = access.elapsed$items();
		for (int i = 0; i < live.size() && i < result.items.size(); i++) {
			live.set(i, result.items.get(i));
		}
		access.elapsed$setLitTimeRemaining(result.litTimeRemaining);
		access.elapsed$setLitTotalTime(result.litTotalTime);
		access.elapsed$setCookingTimer(result.cookingTimer);
		access.elapsed$setCookingTotalTime(result.cookingTotalTime);
		FurnaceVersion.setStoredSpeed(access, result.speedMultiplier);
		// Experience for the smelted items is waiting in the furnace, as usual
		result.recipesUsed.forEach((recipe, count) -> Cooking.addUsed(access, recipe, count));
		ServerLevel level = context.level();
		BlockPos pos = target.getBlockPos();
		for (ItemStack drop : result.drops) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
		}
		BlockState state = target.getBlockState();
		boolean lit = result.litTimeRemaining > 0;
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) != lit) {
			state = state.setValue(AbstractFurnaceBlock.LIT, lit);
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}
		target.setChanged();
		if (context.isDebug()) {
			context.note(BlockEntityHandlers.name(target) + " at " + pos.toShortString() + ": " + result.cooked + " item(s) cooked, " + result.fuelUsed
				+ " fuel item(s) used, " + (lit ? "still burning" : "out"));
		}
	}
}
