package net.kronoz.odyssey.mixin.shadows;

import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(VoxelShadowGrid.class)
public abstract class VoxelShadowGridMixin {

    private static final int SHAPE_SAMPLE_AXIS = 16;
    private static final int SHAPE_SAMPLE_VOLUME = SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS;
    private static final float MAX_OCCLUSION_DENSITY = 0.99f;
    private static final float MIN_OCCLUSION_DENSITY = 0.02f;
    private static final Int2ByteMap OCCUPANCY_CACHE = new Int2ByteOpenHashMap(4096);

    @Inject(method = "voxelOccupancy", at = @At("HEAD"), cancellable = true)
    private static void veil_shadows$shapeAwareOccupancy(ClientWorld level, BlockPos pos, BlockState state, CallbackInfoReturnable<Byte> cir) {
        if (state.isAir()) {
            cir.setReturnValue((byte) 0);
            return;
        }

        if (!state.getFluidState().isEmpty()) {
            cir.setReturnValue((byte) 0);
            return;
        }

        if (state.isOpaqueFullCube(level, pos)) {
            cir.setReturnValue((byte) 0xFF);
            return;
        }

        int stateId = Block.getRawIdFromState(state);
        if (stateId != 0 && OCCUPANCY_CACHE.containsKey(stateId)) {
            cir.setReturnValue(OCCUPANCY_CACHE.get(stateId));
            return;
        }

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            shape = state.getOutlineShape(level, pos);
        }
        if (shape.isEmpty()) {
            cir.setReturnValue((byte) 0);
            return;
        }

        float shapeFill = computeShapeFill(shape);
        if (shapeFill <= MIN_OCCLUSION_DENSITY) {
            cir.setReturnValue((byte) 0);
            return;
        }

        float materialFactor = computeMaterialFactor(level, pos, state, shapeFill);
        float density = smoothDensity(shapeFill * materialFactor);
        byte occupancy = (byte) Math.round(density * 255.0f);
        if (stateId != 0) {
            OCCUPANCY_CACHE.put(stateId, occupancy);
        }
        cir.setReturnValue(occupancy);
    }

    private static float computeShapeFill(VoxelShape shape) {
        List<Box> boxes = shape.getBoundingBoxes();
        if (boxes.isEmpty()) {
            return 0.0f;
        }
        if (boxes.size() == 1) {
            Box box = boxes.getFirst();
            return clamp((float) ((box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ)), 0.0f, 1.0f);
        }

        // Rasterize shape boxes into a small bitset once, then count bits for a stable fill ratio.
        long[] occupancyBits = new long[(SHAPE_SAMPLE_VOLUME + 63) >> 6];
        for (Box box : boxes) {
            int minX = sampleMin(box.minX);
            int maxX = sampleMax(box.maxX);
            int minY = sampleMin(box.minY);
            int maxY = sampleMax(box.maxY);
            int minZ = sampleMin(box.minZ);
            int maxZ = sampleMax(box.maxZ);

            if (minX > maxX || minY > maxY || minZ > maxZ) {
                continue;
            }

            for (int z = minZ; z <= maxZ; z++) {
                int zOffset = z * SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS;
                for (int y = minY; y <= maxY; y++) {
                    int rowOffset = zOffset + y * SHAPE_SAMPLE_AXIS;
                    for (int x = minX; x <= maxX; x++) {
                        int index = rowOffset + x;
                        occupancyBits[index >> 6] |= 1L << (index & 63);
                    }
                }
            }
        }

        int filled = 0;
        for (long bits : occupancyBits) {
            filled += Long.bitCount(bits);
        }
        return (float) filled / (float) SHAPE_SAMPLE_VOLUME;
    }

    private static int sampleMin(double value) {
        return clampInt((int) Math.floor(value * SHAPE_SAMPLE_AXIS), 0, SHAPE_SAMPLE_AXIS - 1);
    }

    private static int sampleMax(double value) {
        return clampInt((int) Math.ceil(value * SHAPE_SAMPLE_AXIS) - 1, 0, SHAPE_SAMPLE_AXIS - 1);
    }

    private static float computeMaterialFactor(ClientWorld level, BlockPos pos, BlockState state, float shapeFill) {
        float factor = 1.0f;
        BlockRenderType renderType = state.getRenderType();

        int opacity = state.getOpacity(level, pos);
        if (opacity <= 0) {
            factor *= 0.18f;
        } else if (opacity <= 2) {
            factor *= 0.28f;
        } else if (opacity <= 7) {
            factor *= 0.48f;
        }

        if (state.isIn(BlockTags.LEAVES)) {
            factor *= 0.60f;
        } else if (state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SAPLINGS)) {
            factor *= 0.35f;
        } else if (shapeFill < 0.55f) {
            factor *= 0.92f;
        }

        if (!state.getCollisionShape(level, pos).isEmpty() && shapeFill > 0.65f) {
            factor *= 1.05f;
        }
        if (renderType != BlockRenderType.INVISIBLE && state.getCollisionShape(level, pos).isEmpty() && !state.getOutlineShape(level, pos).isEmpty()) {
            // Custom non-cube model blocks still need to contribute to occlusion.
            factor *= 0.78f;
        }
        if (state.isOpaque()) {
            factor *= 1.08f;
        }

        return clamp(factor, 0.0f, 1.0f);
    }

    private static float smoothDensity(float density) {
        float normalized = clamp(
                (density - MIN_OCCLUSION_DENSITY) / (MAX_OCCLUSION_DENSITY - MIN_OCCLUSION_DENSITY),
                0.0f,
                1.0f
        );
        float smooth = normalized * normalized * (3.0f - 2.0f * normalized);
        return MIN_OCCLUSION_DENSITY + smooth * (MAX_OCCLUSION_DENSITY - MIN_OCCLUSION_DENSITY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private static void veil_shadows$clearCache(CallbackInfo ci) {
        OCCUPANCY_CACHE.clear();
    }
}
