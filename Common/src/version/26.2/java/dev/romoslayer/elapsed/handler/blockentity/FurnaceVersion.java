package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** How furnaces differ in Minecraft 26.2: fuel has no speed multiplier, and a used-up fuel item's remainder is never dropped. */
final class FurnaceVersion {
	/** Whether part-cooked progress is rescaled when the furnace relights (only needed when fuel changes cooking speed). */
	static final boolean RESCALES_ON_RELIGHT = false;
	/** Whether a fuel item's remainder pops out when there is more fuel left in the stack. */
	static final boolean DROPS_REMAINDER_FROM_STACK = false;
	/** Whether a burning furnace with an input it has no recipe for drops that input's cooking progress. */
	static final boolean RESETS_PROGRESS_WITHOUT_RECIPE = false;

	private FurnaceVersion() {
	}

	/** Whether lighting up swaps the whole fuel stack for its remainder (only Forge and NeoForge before 26.x did). */
	static boolean swapsFuelForRemainder() {
		return false;
	}

	static int burnDuration(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack fuel) {
		return access.elapsed$getBurnDuration(level.fuelValues(), fuel);
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
