package dev.romoslayer.elapsed.core;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.BlockEntityHandler;
import dev.romoslayer.elapsed.api.BlockHandler;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.ElapsedApi;
import dev.romoslayer.elapsed.api.EntityHandler;
import dev.romoslayer.elapsed.api.OfflineProgressHandler;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.handler.block.AgePlantHandler;
import dev.romoslayer.elapsed.handler.block.BambooHandler;
import dev.romoslayer.elapsed.handler.block.CopperHandler;
import dev.romoslayer.elapsed.handler.block.SaplingHandler;
import dev.romoslayer.elapsed.handler.block.TallPlantHandler;
import dev.romoslayer.elapsed.handler.block.VineHandler;
import dev.romoslayer.elapsed.handler.blockentity.BrewingHandler;
import dev.romoslayer.elapsed.handler.blockentity.CampfireHandler;
import dev.romoslayer.elapsed.handler.blockentity.FurnaceHandler;
import dev.romoslayer.elapsed.handler.entity.AgingHandler;
import dev.romoslayer.elapsed.handler.entity.ChickenEggHandler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Every handler in use, in the order they are tried: those registered by other mods first, then Elapsed's own. Built
 * again whenever the config is reloaded or a handler is registered.
 */
public final class HandlerRegistry {
	/** A handler with the id it is known by. */
	public record Entry<H extends OfflineProgressHandler<?, ?>>(Identifier id, H handler) {
	}

	private static final int MAX_COST = 4096;
	private static final Entry<BlockHandler<?>> NONE = new Entry<>(Elapsed.id("none"), null);

	private final List<Entry<BlockHandler<?>>> blockHandlers = new ArrayList<>();
	private final List<Entry<BlockEntityHandler<?>>> blockEntityHandlers = new ArrayList<>();
	private final List<Entry<EntityHandler<?>>> entityHandlers = new ArrayList<>();
	private final Map<BlockState, Entry<BlockHandler<?>>> byState = new Reference2ObjectOpenHashMap<>();
	private final int apiGeneration;
	private final Set<Identifier> failed = new HashSet<>();

	private HandlerRegistry(int apiGeneration) {
		this.apiGeneration = apiGeneration;
	}

	public static HandlerRegistry build() {
		int generation = ElapsedApi.generation();
		HandlerRegistry registry = new HandlerRegistry(generation);
		ElapsedConfig config = ElapsedConfig.get();
		Set<String> disabled = new HashSet<>(config.handlers.disabled);

		ElapsedApi.blockHandlers().forEach((id, handler) -> registry.add(registry.blockHandlers, id, handler, disabled));
		if (config.crops.enabled) {
			registry.add(registry.blockHandlers, Elapsed.id("crops"), new AgePlantHandler(AgePlantHandler.extraRules()), disabled);
			registry.add(registry.blockHandlers, Elapsed.id("tall_plants"), new TallPlantHandler(), disabled);
			registry.add(registry.blockHandlers, Elapsed.id("bamboo"), new BambooHandler(), disabled);
			registry.add(registry.blockHandlers, Elapsed.id("vines"), new VineHandler(), disabled);
		}
		if (config.saplings.enabled) {
			registry.add(registry.blockHandlers, Elapsed.id("saplings"), new SaplingHandler(), disabled);
		}
		if (config.copper.enabled) {
			registry.add(registry.blockHandlers, Elapsed.id("copper"), new CopperHandler(), disabled);
		}

		ElapsedApi.blockEntityHandlers().forEach((id, handler) -> registry.add(registry.blockEntityHandlers, id, handler, disabled));
		registry.add(registry.blockEntityHandlers, Elapsed.id("furnaces"), new FurnaceHandler(), disabled);
		registry.add(registry.blockEntityHandlers, Elapsed.id("brewing"), new BrewingHandler(), disabled);
		registry.add(registry.blockEntityHandlers, Elapsed.id("campfires"), new CampfireHandler(), disabled);

		ElapsedApi.entityHandlers().forEach((id, handler) -> registry.add(registry.entityHandlers, id, handler, disabled));
		registry.add(registry.entityHandlers, Elapsed.id("aging"), new AgingHandler(), disabled);
		registry.add(registry.entityHandlers, Elapsed.id("chicken_eggs"), new ChickenEggHandler(), disabled);
		return registry;
	}

	private <H extends OfflineProgressHandler<?, ?>> void add(List<Entry<H>> list, Identifier id, H handler, Set<String> disabled) {
		if (!disabled.contains(id.toString())) {
			list.add(new Entry<>(id, handler));
		}
	}

	/** Whether something was registered through the API since this was built. */
	public boolean isStale() {
		return this.apiGeneration != ElapsedApi.generation();
	}

	/** The handler for a block, or null. Cached per block state, so it is cheap enough to test whole chunk sections with. */
	public @Nullable Entry<BlockHandler<?>> forState(BlockState state) {
		Entry<BlockHandler<?>> entry = this.byState.get(state);
		if (entry == null) {
			entry = NONE;
			for (Entry<BlockHandler<?>> candidate : this.blockHandlers) {
				if (this.test(candidate.id(), () -> candidate.handler().handles(state))) {
					entry = candidate;
					break;
				}
			}
			this.byState.put(state, entry);
		}
		return entry == NONE || this.failed.contains(entry.id()) ? null : entry;
	}

	public boolean mayHandle(BlockState state) {
		return this.forState(state) != null;
	}

	/** What one catch-up by this handler counts as against the per-tick work limit (always 1 to 4096). */
	public int cost(Entry<BlockHandler<?>> entry) {
		try {
			return Math.clamp(entry.handler().cost(), 1, MAX_COST);
		} catch (RuntimeException | LinkageError e) {
			this.fail(entry.id(), e);
			return 1;
		}
	}

	public @Nullable Entry<BlockEntityHandler<?>> forBlockEntity(BlockEntity blockEntity) {
		for (Entry<BlockEntityHandler<?>> entry : this.blockEntityHandlers) {
			if (this.test(entry.id(), () -> entry.handler().canHandle(blockEntity))) {
				return entry;
			}
		}
		return null;
	}

	/** Every handler interested in an entity (an entity can have several timers). */
	public List<Entry<EntityHandler<?>>> forEntity(Entity entity) {
		List<Entry<EntityHandler<?>>> found = new ArrayList<>(2);
		for (Entry<EntityHandler<?>> entry : this.entityHandlers) {
			if (this.test(entry.id(), () -> entry.handler().canHandle(entity))) {
				found.add(entry);
			}
		}
		return found;
	}

	/** Whether an entity needs a timestamp in its saved data. Called while saving, so it must never throw. */
	public boolean tracks(Entity entity) {
		for (Entry<EntityHandler<?>> entry : this.entityHandlers) {
			if (this.test(entry.id(), () -> entry.handler().canHandle(entity))) {
				return true;
			}
		}
		return false;
	}

	/** Whether a handler is in use: registered, not switched off in the config, and not failed. */
	public boolean isActive(Identifier id) {
		if (this.failed.contains(id)) {
			return false;
		}
		return this.blockHandlers.stream().anyMatch(entry -> entry.id().equals(id))
				|| this.blockEntityHandlers.stream().anyMatch(entry -> entry.id().equals(id))
				|| this.entityHandlers.stream().anyMatch(entry -> entry.id().equals(id));
	}

	// ---- Failures

	public boolean isFailed(Identifier id) {
		return this.failed.contains(id);
	}

	/**
	 * Switches a handler off after it threw, until the next reload. The block cache is cleared so the blocks it had
	 * claimed go to the next handler that wants them (Elapsed's own, for a block another mod had taken over).
	 */
	public void fail(Identifier id, Throwable e) {
		if (this.failed.add(id)) {
			Elapsed.LOGGER.error("Catch-up handler {} failed and is switched off until /elapsed reload or a restart", id, e);
			this.byState.clear();
		}
	}

	public Set<Identifier> failedHandlers() {
		return Set.copyOf(this.failed);
	}

	/** Asks a handler a yes/no question, treating a failure as "no" and switching the handler off. */
	private boolean test(Identifier id, BooleanSupplier question) {
		if (this.failed.contains(id)) {
			return false;
		}
		try {
			return question.getAsBoolean();
		} catch (RuntimeException | LinkageError e) {
			this.fail(id, e);
			return false;
		}
	}

	public int handlerCount() {
		return this.blockHandlers.size() + this.blockEntityHandlers.size() + this.entityHandlers.size();
	}

	/** The most time a handler of this category catches up on: its own cap or the global one, whichever is smaller. */
	public static long capTicks(String category) {
		ElapsedConfig config = ElapsedConfig.get();
		double hours = switch (category) {
			case CatchupCategory.CROPS -> config.crops.maxCatchupHours;
			case CatchupCategory.SAPLINGS -> config.saplings.maxCatchupHours;
			case CatchupCategory.COPPER -> config.copper.maxCatchupHours;
			case CatchupCategory.FURNACES -> config.furnaces.maxCatchupHours;
			case CatchupCategory.BREWING -> config.brewing.maxCatchupHours;
			case CatchupCategory.CAMPFIRES -> config.campfires.maxCatchupHours;
			case CatchupCategory.ANIMALS -> config.animals.maxCatchupHours;
			case CatchupCategory.CHICKENS -> config.chickens.maxCatchupHours;
			default -> config.general.maxCatchupHours;
		};
		return Math.min(config.globalCapTicks(), ElapsedConfig.hoursToTicks(hours));
	}
}
