package dev.romoslayer.elapsed.core;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.EntityHandler;
import dev.romoslayer.elapsed.api.GrowthRateProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Everything registered through {@link dev.romoslayer.elapsed.api.ElapsedApi}, which is only a front for this class
 * (its ids are Minecraft id objects, whose class is named differently in each Minecraft version). Ids are kept here as
 * "namespace:path".
 */
public final class Registrations {
	private static final Map<String, BlockHandler<?>> BLOCK_HANDLERS = new LinkedHashMap<>();
	private static final Map<String, BlockEntityHandler<?>> BLOCK_ENTITY_HANDLERS = new LinkedHashMap<>();
	private static final Map<String, EntityHandler<?>> ENTITY_HANDLERS = new LinkedHashMap<>();
	private static final Map<Block, AgeRule> AGE_RULES = new LinkedHashMap<>();
	private static final Map<String, GrowthRateProvider> GROWTH_PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Map.Entry<String, GrowthRateProvider>> growthProviderList = List.of();
	// Providers that threw or returned nonsense; switched off until registered again
	private static final Set<String> FAILED_PROVIDERS = ConcurrentHashMap.newKeySet();
	/** Highest combined growth speed accepted from providers (beyond it every plant would be grown at once). */
	private static final double MAX_GROWTH_MULTIPLIER = 100.0;
	private static volatile int generation;

	private Registrations() {
	}

	/** A block that grows by counting up an age property on random ticks. */
	public record AgeRule(IntegerProperty property, double growthChance, int minLight) {
	}

	public static synchronized void registerBlockHandler(String id, BlockHandler<?> handler) {
		BLOCK_HANDLERS.put(id, handler);
		changed();
	}

	public static synchronized void registerBlockEntityHandler(String id, BlockEntityHandler<?> handler) {
		BLOCK_ENTITY_HANDLERS.put(id, handler);
		changed();
	}

	public static synchronized void registerEntityHandler(String id, EntityHandler<?> handler) {
		ENTITY_HANDLERS.put(id, handler);
		changed();
	}

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

	public static synchronized void registerGrowthRateProvider(String id, GrowthRateProvider provider) {
		GROWTH_PROVIDERS.put(id, provider);
		FAILED_PROVIDERS.remove(id);
		growthProviderList = providerSnapshot();
	}

	public static synchronized void unregister(String id) {
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
		return GROWTH_PROVIDERS.keySet().stream().anyMatch(id -> id.startsWith(namespace + ":"));
	}

	public static synchronized Map<String, BlockHandler<?>> blockHandlers() {
		return new LinkedHashMap<>(BLOCK_HANDLERS);
	}

	public static synchronized Map<String, BlockEntityHandler<?>> blockEntityHandlers() {
		return new LinkedHashMap<>(BLOCK_ENTITY_HANDLERS);
	}

	public static synchronized Map<String, EntityHandler<?>> entityHandlers() {
		return new LinkedHashMap<>(ENTITY_HANDLERS);
	}

	public static synchronized Map<Block, AgeRule> ageRules() {
		return new LinkedHashMap<>(AGE_RULES);
	}

	/** Goes up every time something is registered, so cached lookups know to rebuild. */
	public static int generation() {
		return generation;
	}

	/** The growth speed every registered provider agrees on (their results multiplied), 1 when there are none. */
	public static double combinedGrowthMultiplier(ServerLevel level, BlockPos pos, BlockState state, long elapsedTicks) {
		double multiplier = 1.0;
		for (Map.Entry<String, GrowthRateProvider> entry : growthProviderList) {
			String id = entry.getKey();
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

	private static List<Map.Entry<String, GrowthRateProvider>> providerSnapshot() {
		return GROWTH_PROVIDERS.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
	}

	private static synchronized void changed() {
		generation++;
	}
}
