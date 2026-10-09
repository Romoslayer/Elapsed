package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.mc.Versioned;
import dev.romoslayer.elapsed.mixin.access.BrewingStandBlockEntityAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Brewing stands (Minecraft 1.20.x, where brewing is built into the game rather than data-driven; Forge brews through
 * its own brewing recipe registry, which the loader answers for). The stand's own tick is followed on a copy of its
 * contents, skipping the stretches where only the brewing timer counts down. A stand brews as long as it has fuel
 * (blaze powder), an ingredient and bottles that ingredient can turn into something; when any of those runs out it
 * stops, as it would have.
 *
 * <p>NeoForge's and Forge's potion brewing events are not fired for brews done this way.
 */
public final class BrewingHandler implements BlockEntityHandler<BrewingHandler.Stand> {
	private static final int MAX_STEPS = 10_000;
	private static final int SLOT_INGREDIENT = 3;
	private static final int SLOT_FUEL = 4;
	private static final int BOTTLES = 3;
	private static final int FUEL_USES = 20;
	private static final int BREW_TIME = 400;

	/** A stand's contents and timers (copies). */
	public static final class Stand {
		final NonNullList<ItemStack> items;
		int brewTime;
		@Nullable Item ingredient;
		int fuel;
		final List<ItemStack> drops = new ArrayList<>();
		int brews;

		Stand(NonNullList<ItemStack> items, int brewTime, @Nullable Item ingredient, int fuel) {
			this.items = items;
			this.brewTime = brewTime;
			this.ingredient = ingredient;
			this.fuel = fuel;
		}
	}

	@Override
	public String category() {
		return CatchupCategory.BREWING;
	}

	@Override
	public boolean canHandle(BlockEntity target) {
		// Only the vanilla stand: a modded one may brew by rules of its own
		return ElapsedConfig.get().brewing.enabled && target.getClass() == BrewingStandBlockEntity.class;
	}

	@Override
	public @Nullable Stand captureState(BlockEntity target, CatchupContext context) {
		BrewingStandBlockEntityAccessor access = (BrewingStandBlockEntityAccessor) target;
		NonNullList<ItemStack> live = access.elapsed$items();
		if (live.size() <= SLOT_FUEL || live.get(SLOT_INGREDIENT).isEmpty() && access.elapsed$brewTime() == 0) {
			return null;
		}
		NonNullList<ItemStack> items = NonNullList.withSize(live.size(), ItemStack.EMPTY);
		for (int i = 0; i < live.size(); i++) {
			items.set(i, live.get(i).copy());
		}
		return new Stand(items, access.elapsed$brewTime(), access.elapsed$ingredient(), access.elapsed$fuel());
	}

	@Override
	public @Nullable Stand calculateProgress(Stand s, long elapsedTicks, CatchupContext context) {
		long remaining = elapsedTicks;
		int steps = 0;
		boolean changed = false;
		while (remaining > 0 && steps++ < MAX_STEPS) {
			boolean refuel = s.fuel <= 0 && s.items.get(SLOT_FUEL).is(Items.BLAZE_POWDER);
			if (!refuel) {
				ItemStack ingredient = s.items.get(SLOT_INGREDIENT);
				boolean brewable = isBrewable(s.items);
				if (s.brewTime >= 2 && brewable && s.ingredient != null && ingredient.is(s.ingredient)) {
					// Just the timer counting down: jump to its last tick
					long jump = Math.min(remaining, s.brewTime - 1);
					s.brewTime -= (int) jump;
					remaining -= jump;
					changed = true;
					continue;
				}
				if (s.brewTime == 0 && !(brewable && s.fuel > 0)) {
					// Nothing to brew, or nothing to brew with: this is how it stays
					break;
				}
			}
			tick(s);
			remaining--;
			changed = true;
		}
		return changed ? s : null;
	}

	/** One tick of BrewingStandBlockEntity.serverTick, on the copy. */
	private static void tick(Stand s) {
		ItemStack fuelStack = s.items.get(SLOT_FUEL);
		if (s.fuel <= 0 && fuelStack.is(Items.BLAZE_POWDER)) {
			s.fuel = FUEL_USES;
			fuelStack.shrink(1);
		}
		boolean brewable = isBrewable(s.items);
		ItemStack ingredient = s.items.get(SLOT_INGREDIENT);
		if (s.brewTime > 0) {
			s.brewTime--;
			if (s.brewTime == 0 && brewable) {
				brew(s);
			} else if (!brewable || s.ingredient == null || !ingredient.is(s.ingredient)) {
				s.brewTime = 0;
			}
		} else if (brewable && s.fuel > 0) {
			s.fuel--;
			s.brewTime = BREW_TIME;
			s.ingredient = ingredient.getItem();
		}
	}

	private static boolean isBrewable(NonNullList<ItemStack> items) {
		return !items.get(SLOT_INGREDIENT).isEmpty() && Elapsed.platform().canBrew(items);
	}

	private static void brew(Stand s) {
		ItemStack ingredient = s.items.get(SLOT_INGREDIENT);
		Elapsed.platform().brew(s.items);
		ItemStack remainder = Versioned.craftingRemainder(ingredient);
		ingredient.shrink(1);
		if (remainder != null) {
			if (ingredient.isEmpty()) {
				ingredient = remainder;
			} else {
				s.drops.add(remainder);
			}
		}
		s.items.set(SLOT_INGREDIENT, ingredient);
		s.brews++;
	}

	@Override
	public void applyResult(BlockEntity target, Stand result, CatchupContext context) {
		BrewingStandBlockEntityAccessor access = (BrewingStandBlockEntityAccessor) target;
		NonNullList<ItemStack> live = access.elapsed$items();
		for (int i = 0; i < live.size() && i < result.items.size(); i++) {
			live.set(i, result.items.get(i));
		}
		access.elapsed$setBrewTime(result.brewTime);
		if (result.ingredient != null) {
			access.elapsed$setIngredient(result.ingredient);
		}
		access.elapsed$setFuel(result.fuel);
		ServerLevel level = context.level();
		BlockPos pos = target.getBlockPos();
		for (ItemStack drop : result.drops) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
		}
		BlockState state = target.getBlockState();
		if (state.getBlock() instanceof BrewingStandBlock) {
			BlockState bottles = state;
			for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length && i < BOTTLES; i++) {
				bottles = bottles.setValue(BrewingStandBlock.HAS_BOTTLE[i], !result.items.get(i).isEmpty());
			}
			if (bottles != state) {
				level.setBlock(pos, bottles, Block.UPDATE_CLIENTS);
			}
		}
		target.setChanged();
		if (context.isDebug()) {
			context.note(BlockEntityHandlers.name(target) + " at " + pos.toShortString() + ": " + result.brews + " brew(s) finished");
		}
	}
}
