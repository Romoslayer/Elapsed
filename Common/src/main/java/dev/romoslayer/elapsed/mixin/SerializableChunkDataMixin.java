package dev.romoslayer.elapsed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.romoslayer.elapsed.core.ElapsedChunk;
import dev.romoslayer.elapsed.core.Timestamps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunks already store the game time at which they were saved ("LastUpdate"), captured together with everything
 * else in the chunk. Elapsed uses that value as its timestamp: it writes its own clock there (the game time, plus any
 * real downtime when that option is on, minus time the chunk still has to catch up on) and reads it back when the
 * chunk loads. Because the timestamp is taken at the same moment as the blocks and block entities, the state on disk
 * and the time it describes can never disagree.
 */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
	@Shadow
	public abstract long lastUpdateTime();

	// The first getGameTime() call is for the scheduled ticks; the second is the LastUpdate value
	@ModifyExpressionValue(method = "copyOf", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getGameTime()J", ordinal = 1))
	private static long elapsed$stampChunk(long gameTime, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) ChunkAccess chunk) {
		return Timestamps.chunkStamp(level, chunk, gameTime);
	}

	@Inject(method = "read", at = @At("RETURN"))
	private void elapsed$readStamp(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionInfo, ChunkPos pos,
			CallbackInfoReturnable<ProtoChunk> cir) {
		if (cir.getReturnValue() instanceof ImposterProtoChunk imposter && imposter.getWrapped() instanceof ElapsedChunk chunk) {
			chunk.elapsed$setDebt(Timestamps.debtFromStamp(level, this.lastUpdateTime()));
		}
	}
}
