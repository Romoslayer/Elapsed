package dev.romoslayer.elapsed.neoforge;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.platform.Platform;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Elapsed.MOD_ID)
public final class ElapsedNeoForge {
	public ElapsedNeoForge() {
		Elapsed.init(new NeoForgePlatform());
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Elapsed.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Elapsed.onServerStopped(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Elapsed.onServerTickEnd(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
			if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
				Elapsed.onChunkLoad(level, chunk);
			}
		});
		NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				Elapsed.onEntityLoad(level, event.getEntity());
			}
		});
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> Elapsed.registerCommands(event.getDispatcher()));
	}

	private static final class NeoForgePlatform implements Platform {
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
			return CommonHooks.canCropGrow(level, pos, state, true);
		}

		@Override
		public ItemStackTemplate craftingRemainder(ItemStack stack) {
			return stack.getCraftingRemainder();
		}
	}
}
