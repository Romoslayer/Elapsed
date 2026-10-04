package dev.romoslayer.elapsed.api;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Catches up a block entity, such as a furnace. */
public interface BlockEntityHandler<S> extends OfflineProgressHandler<BlockEntity, S> {
}
