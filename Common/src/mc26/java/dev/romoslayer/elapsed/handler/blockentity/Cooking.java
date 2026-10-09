package dev.romoslayer.elapsed.handler.blockentity;

import dev.romoslayer.elapsed.mixin.access.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.jspecify.annotations.Nullable;

/**
 * Cooking recipes for furnaces and campfires. Their classes and calls differ between Minecraft versions, so each version
 * folder (Common/src/mc20, mc21, mc26) has its own copy of this class with the same methods; this one is for 26.x.
 */
final class Cooking {
	private Cooking() {
	}

	/** A furnace's recipe for one input. */
	record FurnaceRecipe(RecipeHolder<? extends AbstractCookingRecipe> holder) {
	}

	/** The recipe a furnace uses for this input (through its own lookup, which mods may change), or null if none. */
	static @Nullable FurnaceRecipe furnaceRecipe(AbstractFurnaceBlockEntityAccessor access, ServerLevel level, ItemStack input) {
		return access.elapsed$quickCheck().getRecipeFor(new SingleRecipeInput(input), level).map(FurnaceRecipe::new).orElse(null);
	}

	/** What the recipe makes from this input. */
	static ItemStack result(FurnaceRecipe recipe, ItemStack input, ServerLevel level) {
		return recipe.holder().value().assemble(new SingleRecipeInput(input));
	}

	static int cookingTime(FurnaceRecipe recipe) {
		return recipe.holder().value().cookingTime();
	}

	/** Counts uses of a recipe in the furnace, which pays out their experience when the output is taken. */
	static void addUsed(AbstractFurnaceBlockEntityAccessor access, FurnaceRecipe recipe, int count) {
		access.elapsed$recipesUsed().addTo(recipe.holder().id(), count);
	}

	/** Whether a furnace's output slot can take more of this result (same item, same components). */
	static boolean stacksWith(ItemStack output, ItemStack result) {
		return ItemStack.isSameItemSameComponents(output, result);
	}

	/** What a campfire turns this item into when it is done (the item itself when there is no recipe). */
	static ItemStack campfireResult(ServerLevel level, ItemStack item) {
		SingleRecipeInput input = new SingleRecipeInput(item);
		return level.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
				.map(recipe -> recipe.value().assemble(input))
				.orElse(item);
	}
}
