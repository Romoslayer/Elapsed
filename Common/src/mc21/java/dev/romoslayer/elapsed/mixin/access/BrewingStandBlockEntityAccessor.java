package dev.romoslayer.elapsed.mixin.access;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
	@Accessor("items")
	NonNullList<ItemStack> elapsed$items();

	@Accessor("brewTime")
	int elapsed$brewTime();

	@Accessor("brewTime")
	void elapsed$setBrewTime(int value);

	@Accessor("ingredient")
	Item elapsed$ingredient();

	@Accessor("ingredient")
	void elapsed$setIngredient(Item value);

	@Accessor("fuel")
	int elapsed$fuel();

	@Accessor("fuel")
	void elapsed$setFuel(int value);
}
