package dev.romoslayer.elapsed.mixin.access;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The furnace's fields, named as in 26.x (1.20.x calls them litTime, litDuration and cookingProgress). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
	@Accessor("items")
	NonNullList<ItemStack> elapsed$items();

	@Accessor("litTime")
	int elapsed$litTimeRemaining();

	@Accessor("litTime")
	void elapsed$setLitTimeRemaining(int value);

	@Accessor("litDuration")
	int elapsed$litTotalTime();

	@Accessor("litDuration")
	void elapsed$setLitTotalTime(int value);

	@Accessor("cookingProgress")
	int elapsed$cookingTimer();

	@Accessor("cookingProgress")
	void elapsed$setCookingTimer(int value);

	@Accessor("cookingTotalTime")
	int elapsed$cookingTotalTime();

	@Accessor("cookingTotalTime")
	void elapsed$setCookingTotalTime(int value);

	@Accessor("recipesUsed")
	Object2IntOpenHashMap<ResourceLocation> elapsed$recipesUsed();

	@Accessor("quickCheck")
	RecipeManager.CachedCheck<Container, ? extends AbstractCookingRecipe> elapsed$quickCheck();

	@Invoker("getBurnDuration")
	int elapsed$getBurnDuration(ItemStack fuel);
}
