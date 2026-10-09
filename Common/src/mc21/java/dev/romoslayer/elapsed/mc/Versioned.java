package dev.romoslayer.elapsed.mc;

import dev.romoslayer.elapsed.Elapsed;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The few Minecraft calls whose names or shapes differ between the Minecraft versions Elapsed supports. Each version
 * folder (Common/src/mc20, mc21, mc26) has its own copy of this class with the same methods; this one is for 1.21.x.
 */
public final class Versioned {
	private Versioned() {
	}

	/** A dimension's id as "namespace:path". */
	public static String dimensionId(Level level) {
		return level.dimension().location().toString();
	}

	public static int chunkX(ChunkPos pos) {
		return pos.x;
	}

	public static int chunkZ(ChunkPos pos) {
		return pos.z;
	}

	/** The chunk position packed into one number, as the game's chunk maps key them. */
	public static long packChunk(ChunkPos pos) {
		return pos.toLong();
	}

	/** Makes the game save the chunk again, even if no block in it changed. */
	public static void markUnsaved(LevelChunk chunk) {
		chunk.setUnsaved(true);
	}

	/** The randomTickSpeed game rule. */
	public static int randomTickSpeed(ServerLevel level) {
		return level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
	}

	/** Who may use operator commands such as /elapsed. */
	public static Predicate<CommandSourceStack> gameMasters() {
		return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	/** The block with this id ("namespace:path"), if it is a valid id of a registered block. */
	public static Optional<Block> block(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		return parsed == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(parsed);
	}

	/** Whether crops grow faster on this block (farmland). */
	public static boolean growsCrops(BlockState soil) {
		return soil.is(Blocks.FARMLAND);
	}

	/** Whether a baby may grow up (always, before 26.x added age-locked babies). */
	public static boolean canAgeUp(AgeableMob mob) {
		return true;
	}

	/** A new stack of what is left behind when one of these items is used up (a bucket from a lava bucket), or null. */
	public static @Nullable ItemStack craftingRemainder(ItemStack stack) {
		return Elapsed.platform().craftingRemainder(stack);
	}

	/** The flower a cactus can grow on top, or null in versions without cactus flowers (all before 1.21.5). */
	public static @Nullable BlockState cactusFlower() {
		return null;
	}
}
