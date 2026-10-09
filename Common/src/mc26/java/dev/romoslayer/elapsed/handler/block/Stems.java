package dev.romoslayer.elapsed.handler.block;

import dev.romoslayer.elapsed.mixin.access.StemBlockAccessor;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Melon and pumpkin stems: what they grow and where. Each version folder (Common/src/mc20, mc21, mc26) has its own copy
 * of this class with the same methods; this one is for 26.x.
 */
final class Stems {
	private Stems() {
	}

	/** Whether fruit can grow on this ground. */
	static boolean supportsFruit(StemBlock stem, BlockState ground) {
		return ground.is(((StemBlockAccessor) stem).elapsed$fruitSupportBlocks());
	}

	/** The fruit block (a melon or pumpkin). */
	static Optional<Block> fruit(StemBlock stem, ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.BLOCK).getOptional(((StemBlockAccessor) stem).elapsed$fruit());
	}

	/** The stem the plant turns into once it has grown its fruit. */
	static Optional<Block> attachedStem(StemBlock stem, ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.BLOCK).getOptional(((StemBlockAccessor) stem).elapsed$attachedStem());
	}
}
