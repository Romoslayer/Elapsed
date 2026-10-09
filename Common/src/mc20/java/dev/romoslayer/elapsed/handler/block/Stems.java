package dev.romoslayer.elapsed.handler.block;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Melon and pumpkin stems: what they grow and where. Each version folder (Common/src/mc20, mc21, mc26) has its own copy
 * of this class with the same methods; this one is for 1.20.x, where a stem knows its fruit block directly.
 */
final class Stems {
	private Stems() {
	}

	/** Whether fruit can grow on this ground: farmland or dirt. */
	static boolean supportsFruit(StemBlock stem, BlockState ground) {
		return ground.is(Blocks.FARMLAND) || ground.is(BlockTags.DIRT);
	}

	/** The fruit block (a melon or pumpkin). */
	static Optional<Block> fruit(StemBlock stem, ServerLevel level) {
		return Optional.of(stem.getFruit());
	}

	/** The stem the plant turns into once it has grown its fruit. */
	static Optional<Block> attachedStem(StemBlock stem, ServerLevel level) {
		return Optional.of(stem.getFruit().getAttachedStem());
	}
}
