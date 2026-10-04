package dev.romoslayer.elapsed.handler.blockentity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BlockEntityHandlers {
	private BlockEntityHandlers() {
	}

	/** The block id, for debug output. */
	public static String name(BlockEntity entity) {
		return BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString();
	}
}
