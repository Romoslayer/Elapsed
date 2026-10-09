package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

/**
 * Cooking recipes for furnaces and campfires. Their classes and calls differ between Minecraft versions, so each version
 * folder (Common/src/mc20, mc21, mc26) has its own copy of this class with the same methods; this one is for 1.20.x,
 * where recipes read their input from a container.
 */
final class Cooking {
	private Cooking() {
	}

	/** A furnace's recipe for one input. */
	record FurnaceRecipe(AbstractCookingRecipe recipe) {
	}

	/** The recipe a furnace uses for this input (through its own lookup, which mods may change), or null if none. */
	static @Nullable FurnaceRecipe furnaceRecipe(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack input) {
		return access.elapsed$quickCheck().getRecipeFor(new SimpleContainer(input), level).map(FurnaceRecipe::new).orElse(null);
	}

	/** What the recipe makes from this input. */
	static ItemStack result(FurnaceRecipe recipe, ItemStack input, ServerLevel level) {
		return recipe.recipe().assemble(new SimpleContainer(input), level.registryAccess());
	}

	static int cookingTime(FurnaceRecipe recipe) {
		return recipe.recipe().getCookingTime();
	}

	/** Counts uses of a recipe in the furnace, which pays out their experience when the output is taken. */
	static void addUsed(AbstractFurnaceBlockEntityAccessor access, FurnaceRecipe recipe, int count) {
		access.elapsed$recipesUsed().addTo(recipe.recipe().getId(), count);
	}

	/** Whether a furnace's output slot can take more of this result (1.20.x compares only the item, not its tags). */
	static boolean stacksWith(ItemStack output, ItemStack result) {
		return ItemStack.isSameItem(output, result);
	}

	/** What a campfire turns this item into when it is done (the item itself when there is no recipe). */
	static ItemStack campfireResult(ServerLevel level, ItemStack item) {
		Container input = new SimpleContainer(item);
		return level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
				.map(recipe -> recipe.assemble(input, level.registryAccess()))
				.orElse(item);
	}
}
