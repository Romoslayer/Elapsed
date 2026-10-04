package dev.romoslayer.elapsed.mixin;

import dev.romoslayer.elapsed.core.ElapsedChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ElapsedChunk {
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
}
