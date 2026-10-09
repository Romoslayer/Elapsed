package dev.romoslayer.elapsed.mixin;

import dev.romoslayer.elapsed.core.Timestamps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
	@Unique
	private static final String LAST_UPDATE = "LastUpdate";

	@Inject(method = "write", at = @At("RETURN"))
	private static void elapsed$stampChunk(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
		CompoundTag tag = cir.getReturnValue();
		tag.putLong(LAST_UPDATE, Timestamps.chunkStamp(level, chunk, tag.getLong(LAST_UPDATE)));
	}

	@Inject(method = "read", at = @At("RETURN"))
	private static void elapsed$readStamp(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionInfo, ChunkPos pos, CompoundTag tag,
			CallbackInfoReturnable<ProtoChunk> cir) {
		Timestamps.readChunkStamp(level, cir.getReturnValue(), tag.getLong(LAST_UPDATE));
	}
}
