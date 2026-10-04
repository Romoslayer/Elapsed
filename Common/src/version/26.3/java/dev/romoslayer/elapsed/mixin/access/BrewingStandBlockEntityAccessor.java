package dev.romoslayer.elapsed.mixin.access;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
	@Accessor("items")
	NonNullList<ItemStack> elapsed$items();

	@Accessor("brewTime")
	int elapsed$brewTime();

	@Accessor("brewTime")
	void elapsed$setBrewTime(int value);

	@Accessor("totalBrewTime")
	int elapsed$totalBrewTime();

	@Accessor("totalBrewTime")
	void elapsed$setTotalBrewTime(int value);

	@Accessor("ingredient")
	Item elapsed$ingredient();

	@Accessor("ingredient")
	void elapsed$setIngredient(Item value);

	@Accessor("fuel")
	int elapsed$fuel();

	@Accessor("fuel")
	void elapsed$setFuel(int value);

	@Accessor("totalFuel")
	int elapsed$totalFuel();

	@Accessor("totalFuel")
	void elapsed$setTotalFuel(int value);

	@Accessor("speedMultiplier")
	float elapsed$speedMultiplier();

	@Accessor("speedMultiplier")
	void elapsed$setSpeedMultiplier(float value);

	@Invoker("getUses")
	int elapsed$getUses(ServerLevel level, BrewingFuel fuel);

	@Invoker("getSpeedMultiplier")
	float elapsed$getSpeedMultiplier(ServerLevel level, BrewingFuel fuel);
}
