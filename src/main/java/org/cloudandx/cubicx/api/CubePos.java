package org.cloudandx.cubicx.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.cloudandx.cubicx.util.CubicMath;

public record CubePos(int x, int y, int z) {
    public static CubePos of(int x, int y, int z) {
        return new CubePos(x, y, z);
    }

    public static CubePos fromBlock(int blockX, int blockY, int blockZ) {
        return new CubePos(
            CubicMath.blockToCube(blockX),
            CubicMath.blockToCube(blockY),
            CubicMath.blockToCube(blockZ)
        );
    }

    public static CubePos from(BlockPos pos) {
        return fromBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static CubePos from(SectionPos pos) {
        return new CubePos(pos.x(), pos.y(), pos.z());
    }

    public int minBlockX() {
        return CubicMath.cubeToMinBlock(this.x);
    }

    public int minBlockY() {
        return CubicMath.cubeToMinBlock(this.y);
    }

    public int minBlockZ() {
        return CubicMath.cubeToMinBlock(this.z);
    }

    public int maxBlockX() {
        return CubicMath.cubeToMaxBlock(this.x);
    }

    public int maxBlockY() {
        return CubicMath.cubeToMaxBlock(this.y);
    }

    public int maxBlockZ() {
        return CubicMath.cubeToMaxBlock(this.z);
    }

    public int blockX(int localX) {
        return CubicMath.cubeToBlock(this.x, localX);
    }

    public int blockY(int localY) {
        return CubicMath.cubeToBlock(this.y, localY);
    }

    public int blockZ(int localZ) {
        return CubicMath.cubeToBlock(this.z, localZ);
    }

    public BlockPos originBlockPos() {
        return new BlockPos(this.minBlockX(), this.minBlockY(), this.minBlockZ());
    }

    public SectionPos toSectionPos() {
        return SectionPos.of(this.x, this.y, this.z);
    }

    public ChunkPos toColumnPos() {
        return new ChunkPos(this.x, this.z);
    }

    public long toHashKey() {
        return CubicMath.hashCubePos(this.x, this.y, this.z);
    }

    @Override
    public int hashCode() {
        long h = this.toHashKey();
        return (int) (h ^ (h >>> 32));
    }
}