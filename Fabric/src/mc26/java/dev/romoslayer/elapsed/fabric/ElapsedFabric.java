package dev.romoslayer.elapsed.fabric;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;

public final class ElapsedFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Elapsed.init(new FabricPlatform());
		ServerLifecycleEvents.SERVER_STARTED.register(Elapsed::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(Elapsed::onServerStopped);
		ServerTickEvents.END_SERVER_TICK.register(Elapsed::onServerTickEnd);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Elapsed.onChunkLoad(level, chunk));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Elapsed.onEntityLoad(level, entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Elapsed.registerCommands(dispatcher));
	}

	private static final class FabricPlatform implements Platform {
		@Override
		public Path configDir() {
			return FabricLoader.getInstance().getConfigDir();
		}

		@Override
		public boolean isModLoaded(String modId) {
			return FabricLoader.getInstance().isModLoaded(modId);
		}

		@Override
		public boolean mayGrow(ServerLevel level, BlockPos pos, BlockState state) {
			return true;
		}

		@Override
		public ItemStackTemplate craftingRemainder(ItemStack stack) {
			return stack.getItem().getCraftingRemainder();
		}
	}
}
