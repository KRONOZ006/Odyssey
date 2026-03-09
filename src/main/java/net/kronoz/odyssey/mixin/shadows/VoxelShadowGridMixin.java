package net.kronoz.odyssey.mixin.shadows;

import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(VoxelShadowGrid.class)
public abstract class VoxelShadowGridMixin {

    private static final int SHAPE_SAMPLE_AXIS = 16;
    private static final int SHAPE_SAMPLE_VOLUME = SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS;
    private static final Direction[] MODEL_DIRECTIONS = Direction.values();
    private static final double MODEL_QUAD_MIN_THICKNESS = 1.0 / 28.0;
    private static final float MAX_OCCLUSION_DENSITY = 0.99f;
    private static final float MIN_OCCLUSION_DENSITY = 0.02f;
    private static final Int2ByteMap OCCUPANCY_CACHE = new Int2ByteOpenHashMap(4096);
    private static final long MODEL_FACE_SEED = 0x7F4A7C15L;
    private static final long MODEL_UNCULLED_SEED = 0x2F0B49F3L;
    private static int CACHE_CONFIG_STAMP = Integer.MIN_VALUE;

    @Inject(method = "voxelOccupancy", at = @At("HEAD"), cancellable = true)
    private static void veil_shadows$shapeAwareOccupancy(ClientWorld level, BlockPos pos, BlockState state, CallbackInfoReturnable<Byte> cir) {
        int configStamp = (OdysseyConfig.occlusionEnabled ? 1 : 0)
                | (OdysseyConfig.occludeWithModelGeometry ? 2 : 0);
        if (configStamp != CACHE_CONFIG_STAMP) {
            CACHE_CONFIG_STAMP = configStamp;
            OCCUPANCY_CACHE.clear();
        }

        if (!OdysseyConfig.occlusionEnabled) {
            return;
        }

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

        VoxelShape collisionShape = state.getCollisionShape(level, pos);
        VoxelShape outlineShape = state.getOutlineShape(level, pos);

        float collisionFill = computeShapeFill(collisionShape);
        float outlineFill = computeShapeFill(outlineShape);
        float shapeFill = Math.max(collisionFill, outlineFill * (collisionShape.isEmpty() ? 1.0f : 0.90f));

        float modelFill = 0.0f;
        if (OdysseyConfig.occludeWithModelGeometry && (shapeFill < 0.72f || collisionShape.isEmpty())) {
            modelFill = computeModelFill(state);
        }

        float fill = Math.max(shapeFill, modelFill);
        if (fill <= MIN_OCCLUSION_DENSITY) {
            cir.setReturnValue((byte) 0);
            return;
        }

        float materialFactor = computeMaterialFactor(level, pos, state, fill, modelFill, collisionShape.isEmpty());
        float density = smoothDensity(fill * materialFactor);
        byte occupancy = (byte) Math.round(density * 255.0f);
        if (stateId != 0) {
            OCCUPANCY_CACHE.put(stateId, occupancy);
        }
        cir.setReturnValue(occupancy);
    }

    @Unique
    private static float computeShapeFill(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) {
            return 0.0f;
        }
        List<Box> boxes = shape.getBoundingBoxes();
        if (boxes.isEmpty()) {
            return 0.0f;
        }
        if (boxes.size() == 1) {
            Box box = boxes.get(0);
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

    @Unique
    private static float computeModelFill(BlockState state) {
        if (state.getRenderType() != BlockRenderType.MODEL) {
            return 0.0f;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0.0f;
        }

        BlockRenderManager blockRenderManager = client.getBlockRenderManager();
        if (blockRenderManager == null) {
            return 0.0f;
        }

        BakedModel model = blockRenderManager.getModel(state);
        if (model == null || model.isBuiltin()) {
            return 0.0f;
        }

        Random random = Random.create(MODEL_FACE_SEED);
        long[] occupancyBits = new long[(SHAPE_SAMPLE_VOLUME + 63) >> 6];
        boolean hasQuads = false;

        for (Direction direction : MODEL_DIRECTIONS) {
            random.setSeed(MODEL_FACE_SEED + direction.ordinal() * 977L);
            hasQuads |= rasterizeQuads(occupancyBits, model.getQuads(state, direction, random));
        }

        random.setSeed(MODEL_UNCULLED_SEED);
        hasQuads |= rasterizeQuads(occupancyBits, model.getQuads(state, null, random));

        if (!hasQuads) {
            return 0.0f;
        }

        int filled = 0;
        for (long bits : occupancyBits) {
            filled += Long.bitCount(bits);
        }
        return (float) filled / (float) SHAPE_SAMPLE_VOLUME;
    }

    @Unique
    private static boolean rasterizeQuads(long[] occupancyBits, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return false;
        }

        boolean hasGeometry = false;
        for (BakedQuad quad : quads) {
            if (quad == null) {
                continue;
            }
            int[] vertexData = quad.getVertexData();
            if (vertexData == null || vertexData.length < 12) {
                continue;
            }

            int stride = vertexData.length / 4;
            if (stride < 3) {
                continue;
            }

            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * stride;
                if (base + 2 >= vertexData.length) {
                    continue;
                }
                float x = Float.intBitsToFloat(vertexData[base]);
                float y = Float.intBitsToFloat(vertexData[base + 1]);
                float z = Float.intBitsToFloat(vertexData[base + 2]);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }

            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
                continue;
            }

            minX = clampDouble(minX, 0.0, 1.0);
            minY = clampDouble(minY, 0.0, 1.0);
            minZ = clampDouble(minZ, 0.0, 1.0);
            maxX = clampDouble(maxX, 0.0, 1.0);
            maxY = clampDouble(maxY, 0.0, 1.0);
            maxZ = clampDouble(maxZ, 0.0, 1.0);

            double minXInflated = inflateMin(minX, maxX);
            double maxXInflated = inflateMax(minX, maxX);
            double minYInflated = inflateMin(minY, maxY);
            double maxYInflated = inflateMax(minY, maxY);
            double minZInflated = inflateMin(minZ, maxZ);
            double maxZInflated = inflateMax(minZ, maxZ);

            int minXi = sampleMin(minXInflated);
            int maxXi = sampleMax(maxXInflated);
            int minYi = sampleMin(minYInflated);
            int maxYi = sampleMax(maxYInflated);
            int minZi = sampleMin(minZInflated);
            int maxZi = sampleMax(maxZInflated);

            if (minXi > maxXi || minYi > maxYi || minZi > maxZi) {
                continue;
            }

            for (int z = minZi; z <= maxZi; z++) {
                int zOffset = z * SHAPE_SAMPLE_AXIS * SHAPE_SAMPLE_AXIS;
                for (int y = minYi; y <= maxYi; y++) {
                    int rowOffset = zOffset + y * SHAPE_SAMPLE_AXIS;
                    for (int x = minXi; x <= maxXi; x++) {
                        int index = rowOffset + x;
                        occupancyBits[index >> 6] |= 1L << (index & 63);
                    }
                }
            }
            hasGeometry = true;
        }

        return hasGeometry;
    }

    @Unique
    private static double inflateMin(double min, double max) {
        if (max < min) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        double extent = max - min;
        if (extent >= MODEL_QUAD_MIN_THICKNESS) {
            return min;
        }
        double center = (min + max) * 0.5;
        double half = MODEL_QUAD_MIN_THICKNESS * 0.5;
        return clampDouble(center - half, 0.0, 1.0);
    }

    @Unique
    private static double inflateMax(double min, double max) {
        if (max < min) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        double extent = max - min;
        if (extent >= MODEL_QUAD_MIN_THICKNESS) {
            return max;
        }
        double center = (min + max) * 0.5;
        double half = MODEL_QUAD_MIN_THICKNESS * 0.5;
        return clampDouble(center + half, 0.0, 1.0);
    }

    @Unique
    private static int sampleMin(double value) {
        return clampInt((int) Math.floor(value * SHAPE_SAMPLE_AXIS), 0, SHAPE_SAMPLE_AXIS - 1);
    }

    @Unique
    private static int sampleMax(double value) {
        return clampInt((int) Math.ceil(value * SHAPE_SAMPLE_AXIS) - 1, 0, SHAPE_SAMPLE_AXIS - 1);
    }

    @Unique
    private static float computeMaterialFactor(ClientWorld level,
                                               BlockPos pos,
                                               BlockState state,
                                               float shapeFill,
                                               float modelFill,
                                               boolean collisionEmpty) {
        float factor = 1.0f;
        BlockRenderType renderType = state.getRenderType();
        RenderLayer renderLayer = RenderLayers.getBlockLayer(state);

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

        if (renderLayer == RenderLayer.getCutout() || renderLayer == RenderLayer.getCutoutMipped()) {
            factor *= 0.82f;
        } else if (renderLayer == RenderLayer.getTranslucent()) {
            factor *= 0.52f;
        } else if (renderLayer == RenderLayer.getTripwire()) {
            factor *= 0.40f;
        }

        if (!state.getCollisionShape(level, pos).isEmpty() && shapeFill > 0.65f) {
            factor *= 1.05f;
        }
        if (renderType != BlockRenderType.INVISIBLE && state.getCollisionShape(level, pos).isEmpty() && !state.getOutlineShape(level, pos).isEmpty()) {
            // Custom non-cube model blocks still need to contribute to occlusion.
            factor *= 0.78f;
        }
        if (collisionEmpty && modelFill > 0.04f) {
            factor *= 0.88f;
        }
        if (state.isOpaque()) {
            factor *= 1.08f;
        }

        return clamp(factor, 0.0f, 1.0f);
    }

    @Unique
    private static float smoothDensity(float density) {
        float normalized = clamp(
                (density - MIN_OCCLUSION_DENSITY) / (MAX_OCCLUSION_DENSITY - MIN_OCCLUSION_DENSITY),
                0.0f,
                1.0f
        );
        float smooth = normalized * normalized * (3.0f - 2.0f * normalized);
        return MIN_OCCLUSION_DENSITY + smooth * (MAX_OCCLUSION_DENSITY - MIN_OCCLUSION_DENSITY);
    }

    @Unique
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique
    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique
    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private static void veil_shadows$clearCache(CallbackInfo ci) {
        OCCUPANCY_CACHE.clear();
        CACHE_CONFIG_STAMP = Integer.MIN_VALUE;
    }
}
