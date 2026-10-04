package dev.romoslayer.elapsed.mixin.access;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
	@Accessor("items")
	NonNullList<ItemStack> elapsed$items();

	@Accessor("litTimeRemaining")
	int elapsed$litTimeRemaining();

	@Accessor("litTimeRemaining")
	void elapsed$setLitTimeRemaining(int value);

	@Accessor("litTotalTime")
	int elapsed$litTotalTime();

	@Accessor("litTotalTime")
	void elapsed$setLitTotalTime(int value);

	@Accessor("cookingTimer")
	int elapsed$cookingTimer();

	@Accessor("cookingTimer")
	void elapsed$setCookingTimer(int value);

	@Accessor("cookingTotalTime")
	int elapsed$cookingTotalTime();

	@Accessor("cookingTotalTime")
	void elapsed$setCookingTotalTime(int value);

	@Accessor("recipesUsed")
	Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> elapsed$recipesUsed();

	@Accessor("quickCheck")
	RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> elapsed$quickCheck();

	@Invoker("getBurnDuration")
	int elapsed$getBurnDuration(FuelValues fuelValues, ItemStack fuel);
}
