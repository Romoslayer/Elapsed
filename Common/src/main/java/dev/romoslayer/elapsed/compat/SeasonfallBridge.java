package dev.romoslayer.elapsed.compat;

import dev.romoslayer.elapsed.Elapsed;
import dev.romoslayer.elapsed.api.ElapsedApi;
import dev.romoslayer.elapsed.api.GrowthRateProvider;
import dev.romoslayer.elapsed.config.ElapsedConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Seasonal crop growth from Seasonfall, without depending on it. A crop that was unloaded through several seasons grows
 * by the seasons it actually spent unloaded, not by the season it is when it loads again. Seasonfall works that out
 * (it knows its own calendar, pauses and greenhouses) through
 * {@code SeasonfallApi.averageCropGrowthMultiplier(ServerLevel, BlockPos, BlockState, long elapsedTicks)}.
 *
 * <p>If Seasonfall's API does not have that method, or Seasonfall registers its own provider through
 * {@link ElapsedApi}, the bridge stands down and crops catch up at the normal rate.
 */
public final class SeasonfallBridge implements GrowthRateProvider {
	private static final String SEASONFALL = "seasonfall";
	private static final String API_CLASS = "dev.romoslayer.seasonfall.api.SeasonfallApi";

	private boolean resolved;
	private MethodHandle averageCropGrowthMultiplier;

	private SeasonfallBridge() {
	}

	public static void register() {
		if (Elapsed.platform().isModLoaded(SEASONFALL)) {
			ElapsedApi.registerGrowthRateProvider(Elapsed.id("seasonfall"), new SeasonfallBridge());
		}
	}

	@Override
	public double averageGrowthMultiplier(ServerLevel level, BlockPos pos, BlockState state, long elapsedTicks) {
		if (!ElapsedConfig.get().seasonfall.integrationEnabled || ElapsedApi.hasGrowthRateProviderFrom(SEASONFALL) || !this.resolve()) {
			return 1.0;
		}
		try {
			return Math.max(0.0F, (float) this.averageCropGrowthMultiplier.invoke(level, pos, state, elapsedTicks));
		} catch (Throwable e) {
			Elapsed.LOGGER.warn("Could not read seasonal growth from Seasonfall; crops catch up at the normal rate until restart", e);
			this.averageCropGrowthMultiplier = null;
			return 1.0;
		}
	}

	private boolean resolve() {
		if (this.resolved) {
			return this.averageCropGrowthMultiplier != null;
		}
		this.resolved = true;
		try {
			Class<?> api = Class.forName(API_CLASS);
			this.averageCropGrowthMultiplier = MethodHandles.publicLookup().findStatic(api, "averageCropGrowthMultiplier",
					MethodType.methodType(float.class, ServerLevel.class, BlockPos.class, BlockState.class, long.class));
			Elapsed.LOGGER.info("Seasonfall found: crops catch up by the seasons they spent unloaded");
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			Elapsed.LOGGER.warn("Seasonfall is installed but this version cannot report crop growth for past seasons "
					+ "(SeasonfallApi.averageCropGrowthMultiplier is missing); crops catch up at the normal rate");
			return false;
		}
	}
}
