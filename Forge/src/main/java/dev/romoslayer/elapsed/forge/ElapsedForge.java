package dev.romoslayer.elapsed.forge;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.platform.Platform;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(Elapsed.MOD_ID)
public final class ElapsedForge {
	public ElapsedForge(FMLJavaModLoadingContext context) {
		Elapsed.init(new ForgePlatform());
		// Forge 26.x events carry their own bus; listeners are added rather than annotated
		ServerStartedEvent.BUS.addListener(event -> Elapsed.onServerStarted(event.getServer()));
		ServerStoppedEvent.BUS.addListener(event -> Elapsed.onServerStopped(event.getServer()));
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> Elapsed.onServerTickEnd(event.server()));
		ChunkEvent.Load.BUS.addListener(event -> {
			if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
				Elapsed.onChunkLoad(level, chunk);
			}
		});
		EntityJoinLevelEvent.BUS.addListener(event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				Elapsed.onEntityLoad(level, event.getEntity());
			}
		});
		RegisterCommandsEvent.BUS.addListener(event -> Elapsed.registerCommands(event.getDispatcher()));
	}

	private static final class ForgePlatform implements Platform {
		@Override
		public Path configDir() {
			return FMLPaths.CONFIGDIR.get();
		}

		@Override
		public boolean isModLoaded(String modId) {
			return ModList.isLoaded(modId);
		}

		@Override
		public boolean mayGrow(ServerLevel level, BlockPos pos, BlockState state) {
			return ForgeHooks.onCropsGrowPre(level, pos, state, true);
		}

		@Override
		public ItemStackTemplate craftingRemainder(ItemStack stack) {
			return stack.getCraftingRemainder();
		}
	}
}
