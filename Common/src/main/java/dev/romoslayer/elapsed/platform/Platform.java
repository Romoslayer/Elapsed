package dev.romoslayer.elapsed.platform;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The few things the shared code needs from whichever loader is running it. */
public interface Platform {
	/** The folder config files go in. */
	Path configDir();

	boolean isModLoaded(String modId);

	/**
	 * Whether other mods allow this plant to grow now. NeoForge asks its crop growth event (so mods that stop crops
	 * growing, in winter for example, are respected); Fabric has no such event and always says yes.
	 */
	boolean mayGrow(ServerLevel level, BlockPos pos, BlockState state);

	/** What is left behind when one of these items is used up as fuel or an ingredient (a bucket from a lava bucket). */
	@Nullable ItemStackTemplate craftingRemainder(ItemStack stack);
}
