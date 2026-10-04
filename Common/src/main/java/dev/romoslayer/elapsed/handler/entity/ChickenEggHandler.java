package dev.romoslayer.elapsed.handler.entity;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.CatchupCategory;
import dev.romoslayer.elapsed.api.CatchupContext;
import dev.romoslayer.elapsed.api.EntityHandler;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import dev.romoslayer.elapsed.core.CatchupManager;
import dev.romoslayer.elapsed.core.HandlerRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

/**
 * Adult chickens lay an egg whenever their egg timer runs out, then wait another 5 to 10 minutes. The timer runs on
 * through the elapsed time (a chick only starts once it has grown up), and the eggs that would have been laid are
 * dropped next to the chicken - but never more than the configured handful, so a long absence does not bury a coop in
 * eggs.
 */
public final class ChickenEggHandler implements EntityHandler<ChickenEggHandler.Eggs> {
	private static final int MIN_INTERVAL = 6000;
	private static final int INTERVAL_SPREAD = 6000;
	private static final int MAX_INTERVAL = MIN_INTERVAL + INTERVAL_SPREAD;
	private static final long AVERAGE_INTERVAL = MIN_INTERVAL + INTERVAL_SPREAD / 2;

	public record Eggs(int eggTime, long babyTicksLeft, int newEggTime, int eggs) {
	}

	@Override
	public String category() {
		return CatchupCategory.CHICKENS;
	}

	@Override
	public boolean canHandle(Entity target) {
		return ElapsedConfig.get().chickens.eggTimers && target instanceof Chicken && !AgingHandler.isExcluded(target);
	}

	@Override
	public @Nullable Eggs captureState(Entity target, CatchupContext context) {
		Chicken chicken = (Chicken) target;
		if (!chicken.isAlive() || chicken.isChickenJockey()) {
			return null;
		}
		long babyTicksLeft = 0L;
		if (chicken.isBaby()) {
			// Only lays once grown up: that needs the aging handler in use, the chick allowed to grow, and enough of the
			// animals' own (possibly shorter) catch-up time for it to get there
			CatchupManager manager = Elapsed.manager();
			boolean aging = manager != null && manager.handlers().isActive(Elapsed.id("aging")) && ElapsedConfig.get().animals.aging;
			babyTicksLeft = -(long) chicken.getAge();
			long agingTime = Math.min(context.rawElapsedTicks(), HandlerRegistry.capTicks(CatchupCategory.ANIMALS));
			if (!aging || !chicken.canAgeUp() || babyTicksLeft > agingTime) {
				return null;
			}
		}
		return new Eggs(chicken.eggTime, babyTicksLeft, chicken.eggTime, 0);
	}

	@Override
	public @Nullable Eggs calculateProgress(Eggs eggs, long elapsedTicks, CatchupContext context) {
		long window = elapsedTicks - eggs.babyTicksLeft;
		if (window <= 0) {
			return null;
		}
		if (eggs.eggTime > window) {
			return new Eggs(eggs.eggTime, eggs.babyTicksLeft, (int) (eggs.eggTime - window), 0);
		}
		Laying laying = lay(Math.max(0, eggs.eggTime), window, ElapsedConfig.get().chickens.maxEggsPerCatchup, context.random());
		return new Eggs(eggs.eggTime, eggs.babyTicksLeft, laying.nextEggTime, laying.eggs);
	}

	/** Eggs laid (up to the cap) and the timer left at the end. */
	record Laying(int eggs, int nextEggTime) {
	}

	/**
	 * The first egg comes when the timer runs out ({@code firstEgg} ticks in), then one every 5 to 10 minutes until
	 * {@code window}. Only {@code cap} eggs are handed out, but the timer runs on to the end whatever the cap, so the
	 * next egg comes when it would have. Over a very long window the bulk of the intervals is skipped at their average
	 * length, which leaves the timer where a renewal process settles (uniform over an interval), at a fixed cost.
	 */
	static Laying lay(long firstEgg, long window, int cap, RandomSource random) {
		long time = firstEgg;
		int laid = 1;
		while (true) {
			long remaining = window - time;
			if (laid >= cap && remaining > 4L * MAX_INTERVAL) {
				time += (remaining / AVERAGE_INTERVAL - 2) * AVERAGE_INTERVAL;
				laid = Math.max(laid, cap);
				continue;
			}
			int interval = MIN_INTERVAL + random.nextInt(INTERVAL_SPREAD);
			if (time + interval > window) {
				return new Laying(Math.min(laid, Math.max(0, cap)), (int) Math.max(1L, time + interval - window));
			}
			time += interval;
			laid++;
		}
	}

	@Override
	public void applyResult(Entity target, Eggs result, CatchupContext context) {
		Chicken chicken = (Chicken) target;
		// Babies never lay: if it did not actually grow up (aging switched off, failed, or done by another mod), nothing happens
		if (!chicken.isAlive() || chicken.isBaby() || chicken.eggTime != result.eggTime || !(chicken.level() instanceof ServerLevel level)) {
			return;
		}
		chicken.eggTime = result.newEggTime;
		int dropped = 0;
		for (int i = 0; i < result.eggs; i++) {
			if (chicken.dropFromGiftLootTable(level, BuiltInLootTables.CHICKEN_LAY, chicken::spawnAtLocation)) {
				dropped++;
			}
		}
		if (context.isDebug()) {
			context.note(BuiltInRegistries.ENTITY_TYPE.getKey(chicken.getType()) + " at " + chicken.blockPosition().toShortString() + ": " + dropped
					+ " egg(s), next in " + result.newEggTime + " ticks");
		}
	}
}
