package dev.romoslayer.elapsed.mixin;

import dev.romoslayer.elapsed.core.ElapsedEntity;
import dev.romoslayer.elapsed.core.Timestamps;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	private void elapsed$writeStamp(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
		OptionalLong stamp = Timestamps.entityStamp((Entity) (Object) this);
		if (stamp.isPresent()) {
			tag.putLong(Timestamps.ENTITY_KEY, stamp.getAsLong());
		}
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void elapsed$readStamp(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains(Timestamps.ENTITY_KEY, Tag.TAG_LONG)) {
			Timestamps.readEntityStamp((Entity) (Object) this, tag.getLong(Timestamps.ENTITY_KEY));
		}
	}
}
