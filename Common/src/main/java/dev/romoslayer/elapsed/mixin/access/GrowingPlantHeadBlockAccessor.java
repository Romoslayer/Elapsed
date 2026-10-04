package dev.romoslayer.elapsed.mixin.access;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GrowingPlantHeadBlock.class)
public interface GrowingPlantHeadBlockAccessor {
	@Accessor("growPerTickProbability")
	double elapsed$growPerTickProbability();

	@Invoker("canGrowInto")
	boolean elapsed$canGrowInto(BlockState state);

	@Invoker("getGrowIntoState")
	BlockState elapsed$getGrowIntoState(BlockState growFrom, RandomSource random);
}
