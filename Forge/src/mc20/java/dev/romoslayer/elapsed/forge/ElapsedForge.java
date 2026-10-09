package dev.romoslayer.elapsed.forge;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.platform.Platform;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Also the NeoForge 1.20.1 entry point: that NeoForge is a fork of Forge 47 with the same packages. */
@Mod(Elapsed.MOD_ID)
public final class ElapsedForge {
	public ElapsedForge() {
		Elapsed.init(new ForgePlatform());
		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Elapsed.onServerStarted(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Elapsed.onServerStopped(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				Elapsed.onServerTickEnd(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
			if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
				Elapsed.onChunkLoad(level, chunk);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				Elapsed.onEntityLoad(level, event.getEntity());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> Elapsed.registerCommands(event.getDispatcher()));
	}

	/** The bottle slots of a brewing stand, for Forge's brewing recipe registry. */
	private static final int[] BOTTLE_SLOTS = {0, 1, 2};

	private static final class ForgePlatform implements Platform {
		@Override
		public Path configDir() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public boolean isModLoaded(String modId) {
			return ModList.get().isLoaded(modId);
		}

		@Override
		public boolean mayGrow(ServerLevel level, BlockPos pos, BlockState state) {
			return ForgeHooks.onCropsGrowPre(level, pos, state, true);
		}

		@Override
		public ItemStack craftingRemainder(ItemStack stack) {
			return stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : null;
		}

		@Override
		public boolean furnaceSwapsFuelForRemainder() {
			return true;
		}

		@Override
		public boolean canBrew(NonNullList<ItemStack> items) {
			return BrewingRecipeRegistry.canBrew(items, items.get(3), BOTTLE_SLOTS);
		}

		@Override
		public void brew(NonNullList<ItemStack> items) {
			BrewingRecipeRegistry.brewPotions(items, items.get(3), BOTTLE_SLOTS);
		}
	}
}
