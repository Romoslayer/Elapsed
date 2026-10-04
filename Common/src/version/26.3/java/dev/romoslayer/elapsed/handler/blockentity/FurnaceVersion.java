package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** How furnaces work in Minecraft 26.3: fuel can speed cooking up, and a fuel item's remainder pops out if it has nowhere to go. */
final class FurnaceVersion {
	/** Whether part-cooked progress is rescaled when the furnace relights (only needed when fuel changes cooking speed). */
	static final boolean RESCALES_ON_RELIGHT = true;
	/** Whether a fuel item's remainder pops out when there is more fuel left in the stack. */
	static final boolean DROPS_REMAINDER_FROM_STACK = true;

	private FurnaceVersion() {
	}

	static int burnDuration(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack fuel) {
		return access.elapsed$getBurnDuration(level, fuel);
	}

	static float speedMultiplier(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack fuel) {
		return access.elapsed$getSpeedMultiplier(level, fuel);
	}

	static float storedSpeed(AbstractFurnaceBlockEntityAccessor access) {
		return access.elapsed$speedMultiplier();
	}

	static void setStoredSpeed(AbstractFurnaceBlockEntityAccessor access, float speed) {
		access.elapsed$setSpeedMultiplier(speed);
	}
}
