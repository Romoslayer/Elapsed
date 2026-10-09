package dev.romoslayer.elapsed.api;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.core.CatchupManager;
import dev.romoslayer.elapsed.core.Registrations;
import java.util.OptionalLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The supported way for other mods to add their own systems to Elapsed. Everything here must be called on the server
 * thread (registering during mod setup is fine too).
 *
 * <p>To stay independent of Elapsed, guard calls with your loader's "is mod loaded" check (mod id {@code elapsed})
 * and keep them in a class that is only loaded when it is present.
 *
 * <p>Handlers registered here are tried before Elapsed's own, so a mod can take over its own blocks. Server owners
 * can switch any handler off by its id in config/elapsed.toml.
 */
public final class ElapsedApi {
	private ElapsedApi() {
	}

	public static void registerBlockHandler(ResourceLocation id, BlockHandler<?> handler) {
		Registrations.registerBlockHandler(id.toString(), handler);
	}

	public static void registerBlockEntityHandler(ResourceLocation id, BlockEntityHandler<?> handler) {
		Registrations.registerBlockEntityHandler(id.toString(), handler);
	}

	public static void registerEntityHandler(ResourceLocation id, EntityHandler<?> handler) {
		Registrations.registerEntityHandler(id.toString(), handler);
	}

	/**
	 * The simplest way to support a crop: it grows one stage whenever a random tick succeeds with the given chance,
	 * needs at least {@code minLight} (0 for none) and must be able to survive where it is.
	 */
	public static void registerAgeProperty(Block block, IntegerProperty property, double growthChance, int minLight) {
		Registrations.registerAgeProperty(block, property, growthChance, minLight);
	}

	public static void registerGrowthRateProvider(ResourceLocation id, GrowthRateProvider provider) {
		Registrations.registerGrowthRateProvider(id.toString(), provider);
	}

	/** Removes whatever handler or growth provider was registered under this id. */
	public static void unregister(ResourceLocation id) {
		Registrations.unregister(id.toString());
	}

	/** Whether a growth provider from the given mod (by id namespace) is registered. */
	public static boolean hasGrowthRateProviderFrom(String namespace) {
		return Registrations.hasGrowthRateProviderFrom(namespace);
	}

	/** Time a loaded chunk is still waiting to catch up on. Empty when nothing is pending. */
	public static OptionalLong pendingTicks(LevelChunk chunk) {
		CatchupManager manager = Elapsed.manager();
		long debt = manager == null ? 0 : CatchupManager.pendingTicks(chunk);
		return debt > 0 ? OptionalLong.of(debt) : OptionalLong.empty();
	}

	/** Time a loaded entity is still waiting to catch up on. Empty when nothing is pending. */
	public static OptionalLong pendingTicks(Entity entity) {
		CatchupManager manager = Elapsed.manager();
		long debt = manager == null ? 0 : CatchupManager.pendingTicks(entity);
		return debt > 0 ? OptionalLong.of(debt) : OptionalLong.empty();
	}
}
