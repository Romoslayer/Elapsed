package dev.romoslayer.elapsed.api;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.core.CatchupManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
	private static final Map<Identifier, BlockHandler<?>> BLOCK_HANDLERS = new LinkedHashMap<>();
	private static final Map<Identifier, BlockEntityHandler<?>> BLOCK_ENTITY_HANDLERS = new LinkedHashMap<>();
	private static final Map<Identifier, EntityHandler<?>> ENTITY_HANDLERS = new LinkedHashMap<>();
	private static final Map<Block, AgeRule> AGE_RULES = new LinkedHashMap<>();
	private static final Map<Identifier, GrowthRateProvider> GROWTH_PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Map.Entry<Identifier, GrowthRateProvider>> growthProviderList = List.of();
	// Providers that threw or returned nonsense; switched off until registered again
	private static final Set<Identifier> FAILED_PROVIDERS = ConcurrentHashMap.newKeySet();
	/** Highest combined growth speed accepted from providers (beyond it every plant would be grown at once). */
	private static final double MAX_GROWTH_MULTIPLIER = 100.0;
	private static volatile int generation;

	private ElapsedApi() {
	}

	/** A block that grows by counting up an age property on random ticks. */
	public record AgeRule(IntegerProperty property, double growthChance, int minLight) {
	}

	public static synchronized void registerBlockHandler(Identifier id, BlockHandler<?> handler) {
		BLOCK_HANDLERS.put(id, handler);
		changed();
	}

	public static synchronized void registerBlockEntityHandler(Identifier id, BlockEntityHandler<?> handler) {
		BLOCK_ENTITY_HANDLERS.put(id, handler);
		changed();
	}

	public static synchronized void registerEntityHandler(Identifier id, EntityHandler<?> handler) {
		ENTITY_HANDLERS.put(id, handler);
		changed();
	}

	/**
	 * The simplest way to support a crop: it grows one stage whenever a random tick succeeds with the given chance,
	 * needs at least {@code minLight} (0 for none) and must be able to survive where it is.
	 */
	public static synchronized void registerAgeProperty(Block block, IntegerProperty property, double growthChance, int minLight) {
		if (!block.getStateDefinition().getProperties().contains(property)) {
			throw new IllegalArgumentException(property.getName() + " is not a property of " + block);
		}
		if (!(growthChance >= 0.0 && growthChance <= 1.0)) {
			throw new IllegalArgumentException("growthChance must be between 0 and 1, not " + growthChance);
		}
		if (minLight < 0 || minLight > 15) {
			throw new IllegalArgumentException("minLight must be between 0 and 15, not " + minLight);
		}
		AGE_RULES.put(block, new AgeRule(property, growthChance, minLight));
		changed();
	}

	public static synchronized void registerGrowthRateProvider(Identifier id, GrowthRateProvider provider) {
		GROWTH_PROVIDERS.put(id, provider);
		FAILED_PROVIDERS.remove(id);
		growthProviderList = providerSnapshot();
	}

	/** Removes whatever handler or growth provider was registered under this id. */
	public static synchronized void unregister(Identifier id) {
		BLOCK_HANDLERS.remove(id);
		BLOCK_ENTITY_HANDLERS.remove(id);
		ENTITY_HANDLERS.remove(id);
		if (GROWTH_PROVIDERS.remove(id) != null) {
			growthProviderList = providerSnapshot();
		}
		changed();
	}

	/** Whether a growth provider from the given mod (by id namespace) is registered. */
	public static synchronized boolean hasGrowthRateProviderFrom(String namespace) {
		return GROWTH_PROVIDERS.keySet().stream().anyMatch(id -> id.getNamespace().equals(namespace));
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

	// ---- For Elapsed itself

	public static synchronized Map<Identifier, BlockHandler<?>> blockHandlers() {
		return new LinkedHashMap<>(BLOCK_HANDLERS);
	}

	public static synchronized Map<Identifier, BlockEntityHandler<?>> blockEntityHandlers() {
		return new LinkedHashMap<>(BLOCK_ENTITY_HANDLERS);
	}

	public static synchronized Map<Identifier, EntityHandler<?>> entityHandlers() {
		return new LinkedHashMap<>(ENTITY_HANDLERS);
	}

	public static synchronized Map<Block, AgeRule> ageRules() {
		return new LinkedHashMap<>(AGE_RULES);
	}

	/** Goes up every time something is registered, so cached lookups know to rebuild. */
	public static int generation() {
		return generation;
	}

	static double combinedGrowthMultiplier(ServerLevel level, BlockPos pos, BlockState state, long elapsedTicks) {
		double multiplier = 1.0;
		for (Map.Entry<Identifier, GrowthRateProvider> entry : growthProviderList) {
			Identifier id = entry.getKey();
			if (FAILED_PROVIDERS.contains(id)) {
				continue;
			}
			double value;
			try {
				value = entry.getValue().averageGrowthMultiplier(level, pos, state, elapsedTicks);
			} catch (RuntimeException | LinkageError e) {
				if (FAILED_PROVIDERS.add(id)) {
					Elapsed.LOGGER.error("Growth rate provider {} failed and is ignored until it is registered again", id, e);
				}
				continue;
			}
			if (!Double.isFinite(value) || value < 0.0) {
				if (FAILED_PROVIDERS.add(id)) {
					Elapsed.LOGGER.error("Growth rate provider {} returned {}, which is not a growth speed; it is ignored until it is registered again",
							id, value);
				}
				continue;
			}
			multiplier *= value;
		}
		return Math.min(multiplier, MAX_GROWTH_MULTIPLIER);
	}

	private static List<Map.Entry<Identifier, GrowthRateProvider>> providerSnapshot() {
		return GROWTH_PROVIDERS.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
	}

	private static synchronized void changed() {
		generation++;
	}
}
