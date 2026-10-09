package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.mixin.access.CampfireBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * Campfires and soul campfires. Each of the four slots cooks on its own timer while the fire is lit; when it is done
 * the cooked food pops off onto the ground, exactly once. While the fire is out, part-cooked food slowly loses its
 * progress, as it does in the game.
 */
public final class CampfireHandler implements BlockEntityHandler<CampfireHandler.Campfire> {
	/** How long a dropped item lies on the ground before it despawns. */
	private static final int ITEM_LIFETIME = 6000;

	/** A campfire's contents (copies) and, after the catch-up, what came off it and how long ago. */
	public record Campfire(boolean lit, NonNullList<ItemStack> items, int[] progress, int[] time, ItemStack[] cooked, long[] cookedTicksAgo) {
	}

	@Override
	public String category() {
		return CatchupCategory.CAMPFIRES;
	}

	@Override
	public boolean canHandle(BlockEntity target) {
		// Only the vanilla campfire (soul campfires use the same class): a modded one may cook by rules of its own
		return ElapsedConfig.get().campfires.enabled && target.getClass() == CampfireBlockEntity.class;
	}

	@Override
	public @Nullable Campfire captureState(BlockEntity target, CatchupContext context) {
		CampfireBlockEntity campfire = (CampfireBlockEntity) target;
		CampfireBlockEntityAccessor access = (CampfireBlockEntityAccessor) campfire;
		NonNullList<ItemStack> live = campfire.getItems();
		int slots = Math.min(live.size(), Math.min(access.elapsed$cookingProgress().length, access.elapsed$cookingTime().length));
		boolean anything = false;
		NonNullList<ItemStack> items = NonNullList.withSize(slots, ItemStack.EMPTY);
		for (int i = 0; i < slots; i++) {
			items.set(i, live.get(i).copy());
			anything |= !live.get(i).isEmpty() || access.elapsed$cookingProgress()[i] > 0;
		}
		if (!anything) {
			return null;
		}
		BlockState state = target.getBlockState();
		boolean lit = state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
		return new Campfire(lit, items, access.elapsed$cookingProgress().clone(), access.elapsed$cookingTime().clone(), new ItemStack[slots],
				new long[slots]);
	}

	@Override
	public @Nullable Campfire calculateProgress(Campfire campfire, long elapsedTicks, CatchupContext context) {
		ServerLevel level = context.level();
		int[] progress = campfire.progress.clone();
		ItemStack[] cooked = new ItemStack[progress.length];
		long[] ticksAgo = new long[progress.length];
		boolean changed = false;
		for (int slot = 0; slot < progress.length; slot++) {
			ItemStack item = campfire.items.get(slot);
			if (campfire.lit) {
				if (item.isEmpty()) {
					continue;
				}
				// CampfireBlockEntity.cookTick: the slot is done on the tick its progress reaches its cooking time
				long ticksToDone = Math.max(1L, (long) campfire.time[slot] - progress[slot]);
				if (ticksToDone <= elapsedTicks) {
					ItemStack result = Cooking.campfireResult(level, item);
					if (result.isItemEnabled(level.enabledFeatures())) {
						cooked[slot] = result;
						ticksAgo[slot] = elapsedTicks - ticksToDone;
						progress[slot] = campfire.time[slot];
					} else {
						progress[slot] = (int) Math.min(Integer.MAX_VALUE, progress[slot] + elapsedTicks);
					}
				} else {
					progress[slot] += (int) elapsedTicks;
				}
				changed = true;
			} else if (progress[slot] > 0) {
				// cooldownTick: two ticks of progress lost per tick
				progress[slot] = (int) Mth.clamp(progress[slot] - 2L * elapsedTicks, 0L, campfire.time[slot]);
				changed = true;
			}
		}
		return changed ? new Campfire(campfire.lit, campfire.items, progress, campfire.time, cooked, ticksAgo) : null;
	}

	@Override
	public void applyResult(BlockEntity target, Campfire result, CatchupContext context) {
		CampfireBlockEntity campfire = (CampfireBlockEntity) target;
		CampfireBlockEntityAccessor access = (CampfireBlockEntityAccessor) campfire;
		ServerLevel level = context.level();
		BlockPos pos = target.getBlockPos();
		NonNullList<ItemStack> live = campfire.getItems();
		int[] progress = access.elapsed$cookingProgress();
		boolean caught = level.getBlockEntity(pos.below()) instanceof Hopper;
		boolean despawn = ElapsedConfig.get().campfires.expiredFoodDespawns && !caught;
		int dropped = 0;
		int expired = 0;
		boolean removed = false;
		for (int slot = 0; slot < result.progress.length && slot < live.size(); slot++) {
			progress[slot] = result.progress[slot];
			ItemStack cooked = result.cooked[slot];
			if (cooked == null) {
				continue;
			}
			live.set(slot, ItemStack.EMPTY);
			removed = true;
			if (despawn && result.cookedTicksAgo[slot] >= ITEM_LIFETIME) {
				expired++;
			} else {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cooked);
				dropped++;
			}
		}
		if (removed) {
			BlockState state = target.getBlockState();
			level.sendBlockUpdated(pos, state, state, 3);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
		}
		target.setChanged();
		if (context.isDebug()) {
			context.note(BlockEntityHandlers.name(target) + " at " + pos.toShortString() + ": " + dropped + " item(s) cooked"
				+ (expired > 0 ? ", " + expired + " long since despawned" : ""));
		}
	}
}
