package dev.romoslayer.elapsed.mixin.access;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CampfireBlockEntity.class)
public interface CampfireBlockEntityAccessor {
	@Accessor("cookingProgress")
	int[] elapsed$cookingProgress();

	@Accessor("cookingTime")
	int[] elapsed$cookingTime();
}
