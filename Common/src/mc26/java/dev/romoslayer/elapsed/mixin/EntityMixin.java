package dev.romoslayer.elapsed.mixin;

import dev.romoslayer.elapsed.core.ElapsedEntity;
import dev.romoslayer.elapsed.core.Timestamps;
import java.util.OptionalLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entities are stored apart from their chunk, so the ones Elapsed catches up carry a timestamp of their own, written
 * and read together with the rest of their data.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements ElapsedEntity {
	@Unique
	private long elapsed$debt;

	@Override
	public long elapsed$debt() {
		return this.elapsed$debt;
	}

	@Override
	public void elapsed$setDebt(long ticks) {
		this.elapsed$debt = Math.max(0L, ticks);
	}

	@Inject(method = "saveWithoutId", at = @At("TAIL"))
	private void elapsed$writeStamp(ValueOutput output, CallbackInfo ci) {
		OptionalLong stamp = Timestamps.entityStamp((Entity) (Object) this);
		if (stamp.isPresent()) {
			output.putLong(Timestamps.ENTITY_KEY, stamp.getAsLong());
		}
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void elapsed$readStamp(ValueInput input, CallbackInfo ci) {
		input.getLong(Timestamps.ENTITY_KEY).ifPresent(stamp -> Timestamps.readEntityStamp((Entity) (Object) this, stamp));
	}
}
