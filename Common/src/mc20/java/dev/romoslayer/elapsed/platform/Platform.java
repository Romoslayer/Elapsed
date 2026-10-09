package dev.romoslayer.elapsed.platform;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The few things the shared code needs from whichever loader is running it. */
public interface Platform {
	/** The folder config files go in. */
	Path configDir();

	boolean isModLoaded(String modId);

	/**
	 * Whether other mods allow this plant to grow now. Forge (and NeoForge, its fork for 1.20.1) ask their crop growth
	 * event (so mods that stop crops growing, in winter for example, are respected); Fabric has no such event and always
	 * says yes.
	 */
	boolean mayGrow(ServerLevel level, BlockPos pos, BlockState state);

	/**
	 * A new stack of what is left behind when one of these items is used up as fuel or an ingredient (a bucket from a
	 * lava bucket), or null if nothing is.
	 */
	@Nullable ItemStack craftingRemainder(ItemStack stack);

	/**
	 * Whether a furnace lighting up swaps its whole fuel stack for the fuel's remainder (Forge), rather than using up one
	 * item and leaving the remainder only once the stack is gone (the game itself).
	 */
	boolean furnaceSwapsFuelForRemainder();

	/**
	 * Whether the ingredient in a brewing stand's slot 3 turns any of the bottles in slots 0 to 2 into something: the
	 * game's own potion brewing, or Forge's brewing recipe registry (which mods add their recipes to).
	 */
	boolean canBrew(NonNullList<ItemStack> items);

	/** Brews the bottles in slots 0 to 2 with the ingredient in slot 3, in place (the ingredient is not used up here). */
	void brew(NonNullList<ItemStack> items);
}
