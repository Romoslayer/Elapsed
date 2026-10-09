package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * How furnaces differ in Minecraft 1.20.x: fuel has no speed multiplier, a used-up fuel item's remainder is never
 * dropped, and an input without a recipe loses its cooking progress.
 */
final class FurnaceVersion {
	/** Whether part-cooked progress is rescaled when the furnace relights (only needed when fuel changes cooking speed). */
	static final boolean RESCALES_ON_RELIGHT = false;
	/** Whether a fuel item's remainder pops out when there is more fuel left in the stack. */
	static final boolean DROPS_REMAINDER_FROM_STACK = false;
	/** Whether a burning furnace with an input it has no recipe for drops that input's cooking progress. */
	static final boolean RESETS_PROGRESS_WITHOUT_RECIPE = true;

	private FurnaceVersion() {
	}

	/** Whether lighting up swaps the whole fuel stack for its remainder (Forge does; the game does not). */
	static boolean swapsFuelForRemainder() {
		return Elapsed.platform().furnaceSwapsFuelForRemainder();
	}

	static int burnDuration(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack fuel) {
		return access.elapsed$getBurnDuration(fuel);
	}

	static float speedMultiplier(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack fuel) {
		return 1.0F;
	}

	static float storedSpeed(AbstractFurnaceBlockEntityAccessor access) {
		return 1.0F;
	}

	static void setStoredSpeed(AbstractFurnaceBlockEntityAccessor access, float speed) {
	}
}
