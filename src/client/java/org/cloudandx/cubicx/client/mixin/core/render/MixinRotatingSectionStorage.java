package org.cloudandx.cubicx.client.mixin.core.render;

import java.util.function.Consumer;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.RotatingSectionStorage.Value;
import net.minecraft.client.RotatingSectionStorage.ValueCreator;
import net.minecraft.core.SectionPos;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RotatingSectionStorage.class)
public abstract class MixinRotatingSectionStorage<T extends Value> {
    @Shadow
    @Final
    private int radius;

    @Shadow
    @Final
    private int sectionGridSizeY;

    @Shadow
    @Final
    private int sectionGridSizeXZ;

    @Shadow
    private SectionPos centerSectionPos;

    @Unique
    private Object[] cubicx$flatValues;

    @Unique
    private volatile int cubicx$dynamicMinY;

    @Unique
    private volatile int cubicx$dynamicMaxY;

    @Unique
    private volatile boolean cubicx$initialized;

    @Shadow
    private int getSectionIndex(final int x, final int y, final int z) {
        return 0;
    }

    @Shadow
    public abstract void forEach(final Consumer<? super T> action);

    @Shadow
    public abstract int size();

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, name = "minY")
    private static int cubicx$boundInitMinY(int minY, int radius) {
        return -Math.max(radius, 16);
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, name = "maxY")
    private static int cubicx$boundInitMaxY(int maxY, int radius) {
        return Math.max(radius, 16);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$captureFlatValues(int radius, int minY, int maxY, ValueCreator<T> valueCreator, CallbackInfo ci) {
        int verticalRadius = Math.max(radius, 16);
        this.centerSectionPos = SectionPos.of(0, 0, 0);
        this.cubicx$dynamicMinY = -verticalRadius;
        this.cubicx$dynamicMaxY = verticalRadius;
        this.cubicx$initialized = false;
        Object[] array = new Object[this.size()];
        final int[] idx = new int[]{0};
        this.forEach(val -> array[idx[0]++] = val);
        this.cubicx$flatValues = array;
    }

    /**
     * @author cloudandx
     * @reason Rotate render sections across all three axes (X, Y, Z) using 3D toroidal modulo indexing across full 32-bit coordinates.
     */
    @Overwrite
    public boolean repositionCenter(final SectionPos newCenterSectionPos) {
        if (this.cubicx$initialized && newCenterSectionPos.equals(this.centerSectionPos)) {
            return false;
        }
        this.cubicx$initialized = true;
        int verticalRadius = this.sectionGridSizeY / 2;
        int lowestX = newCenterSectionPos.x() - this.radius;
        int lowestY = Math.clamp(
            (long) newCenterSectionPos.y() - (long) verticalRadius,
            CubicMath.MIN_CUBE,
            CubicMath.MAX_CUBE - this.sectionGridSizeY + 1
        );
        int lowestZ = newCenterSectionPos.z() - this.radius;
        this.cubicx$dynamicMinY = lowestY;
        this.cubicx$dynamicMaxY = lowestY + this.sectionGridSizeY - 1;
        this.centerSectionPos = newCenterSectionPos;
        for (int gridX = 0; gridX < this.sectionGridSizeXZ; gridX++) {
            int newSectionX = lowestX + Math.floorMod(gridX - lowestX, this.sectionGridSizeXZ);
            for (int gridZ = 0; gridZ < this.sectionGridSizeXZ; gridZ++) {
                int newSectionZ = lowestZ + Math.floorMod(gridZ - lowestZ, this.sectionGridSizeXZ);
                for (int gridY = 0; gridY < this.sectionGridSizeY; gridY++) {
                    int newSectionY = lowestY + Math.floorMod(gridY - lowestY, this.sectionGridSizeY);
                    @SuppressWarnings("unchecked")
                    T value = (T) this.cubicx$flatValues[this.getSectionIndex(gridX, gridY, gridZ)];
                    long targetNode = SectionPos.asLong(newSectionX, newSectionY, newSectionZ);
                    if (value.getSectionNode() != targetNode) {
                        value.setSectionNode(targetNode);
                    }
                }
            }
        }
        return true;
    }

    /**
     * @author cloudandx
     * @reason Return current dynamic minimum section Y of the 3D camera cube window.
     */
    @Overwrite
    public int minY() {
        return this.cubicx$dynamicMinY;
    }

    /**
     * @author cloudandx
     * @reason Return current dynamic maximum section Y of the 3D camera cube window.
     */
    @Overwrite
    public int maxY() {
        return this.cubicx$dynamicMaxY;
    }

    /**
     * @author cloudandx
     * @reason Look up 16x16x16 section value using 3D toroidal modulo indexing on X, Y, and Z.
     */
    @Overwrite
    public @Nullable T getValue(final int sectionX, final int sectionY, final int sectionZ) {
        if (!this.containsSection(sectionX, sectionY, sectionZ)) {
            return null;
        }
        int x = Math.floorMod(sectionX, this.sectionGridSizeXZ);
        int y = Math.floorMod(sectionY, this.sectionGridSizeY);
        int z = Math.floorMod(sectionZ, this.sectionGridSizeXZ);
        @SuppressWarnings("unchecked")
        T value = (T) this.cubicx$flatValues[this.getSectionIndex(x, y, z)];
        return value;
    }

    /**
     * @author cloudandx
     * @reason Check 3D cubic bounds around centerSectionPos safely using 64-bit long comparisons.
     */
    @Overwrite
    private boolean containsSection(final int sectionX, final int sectionY, final int sectionZ) {
        if (sectionY < this.cubicx$dynamicMinY || sectionY > this.cubicx$dynamicMaxY) {
            return false;
        }
        long dx = Math.abs((long) sectionX - (long) this.centerSectionPos.x());
        long dz = Math.abs((long) sectionZ - (long) this.centerSectionPos.z());
        return dx <= this.radius && dz <= this.radius;
    }
}