package net.kronoz.odyssey.block.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.kronoz.odyssey.init.ModBlocks;
import net.kronoz.odyssey.light.VeilNativeOcclusionMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SimpleBlockLightManager {
    private static final float EPSILON = 0.001f;
    private static final double SURFACE_OFFSET = 0.045;
    private static final int FALLBACK_SCAN_RADIUS = 8;

    private static final Map<Block, Definition> DEFINITIONS = new IdentityHashMap<>();
    private static final Set<Long> LOADED_CHUNKS = new HashSet<>();
    private static final Map<Long, Set<BlockPos>> CHUNK_EMITTERS = new HashMap<>();
    private static final Set<BlockPos> EMITTERS = new HashSet<>();
    private static final Set<BlockPos> DIRTY_POSITIONS = new HashSet<>();
    private static final Map<String, ActiveLight> ACTIVE = new LinkedHashMap<>();

    private static boolean initialized;
    private static boolean rendererWasReady;
    private static boolean fullRebuildPending = true;
    private static boolean chunkSeeded;
    private static long validationTick = Long.MIN_VALUE;

    private SimpleBlockLightManager() {
    }

    public static void initClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerDefinitions();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (chunk == null) {
                return;
            }
            LOADED_CHUNKS.add(chunkKey(chunk.getPos()));
            fullRebuildPending = true;
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (chunk == null) {
                return;
            }
            long key = chunkKey(chunk.getPos());
            LOADED_CHUNKS.remove(key);
            Set<BlockPos> removed = CHUNK_EMITTERS.remove(key);
            if (removed != null) {
                EMITTERS.removeAll(removed);
            }
            fullRebuildPending = true;
        });

        WorldRenderEvents.START.register(ctx -> {
            if (!RenderSystem.isOnRenderThread()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                reset();
                return;
            }
            boolean rendererReady = rendererReady();
            if (!rendererReady) {
                rendererWasReady = false;
                return;
            }
            if (!rendererWasReady) {
                rendererWasReady = true;
                fullRebuildPending = true;
            }
            seedLoadedChunks(client.world, client);
            processDirtyState(client.world);
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || !rendererReady()) {
                return;
            }
            updateDynamicLights(client.world, client);
        });
    }

    public static void requestAdd(BlockPos pos) {
        if (pos != null) {
            DIRTY_POSITIONS.add(pos.toImmutable());
        }
    }

    public static void requestRemove(BlockPos pos) {
        if (pos != null) {
            DIRTY_POSITIONS.add(pos.toImmutable());
        }
    }

    private static void registerDefinitions() {
        if (!DEFINITIONS.isEmpty()) {
            return;
        }
        register(ModBlocks.LIGHT1, new Definition(
                FlickerMode.NONE,
                false,
                1,
                OrientationMode.SUPPORT_OPPOSITE,
                1.0f,
                3.0f,
                25.0f,
                3.0f,
                0.20f,
                0.20f,
                degrees(40.0),
                1.0f, 0.796f, 0.494f,
                AnchorMode.SHAPE_FACE,
                new Vec3d(0.5, 0.84, 0.5)
        ));
        register(ModBlocks.LIGHT2, new Definition(
                FlickerMode.SOFT,
                false,
                1,
                OrientationMode.SUPPORT_OPPOSITE,
                1.0f,
                1.0f,
                25.0f,
                5.0f,
                0.20f,
                0.20f,
                degrees(40.0),
                1.0f, 0.87f, 0.95f,
                AnchorMode.SHAPE_FACE,
                new Vec3d(0.5, 0.84, 0.5)
        ));
        register(ModBlocks.ALARM, new Definition(
                FlickerMode.NONE,
                false,
                1,
                OrientationMode.SUPPORT_OPPOSITE,
                5.0f,
                5.0f,
                25.0f,
                5.0f,
                0.20f,
                0.20f,
                degrees(40.0),
                1.0f, 0.0f, 0.0f,
                AnchorMode.SHAPE_FACE,
                new Vec3d(0.5, 0.5, 0.3)
        ));
        register(ModBlocks.SPEDBLOCK, new Definition(
                FlickerMode.NONE,
                false,
                1,
                OrientationMode.SUPPORT_OPPOSITE,
                4.0f,
                2.0f,
                96.0f,
                3.0f,
                0.10f,
                0.10f,
                degrees(35.0),
                1.0f, 1.0f, 1.0f,
                AnchorMode.SHAPE_FACE,
                new Vec3d(0.5, 0.84, 0.5)
        ));
        register(ModBlocks.TERMINAL, new Definition(
                FlickerMode.SOFT,
                false,
                1,
                OrientationMode.FACING_FORWARD,
                1.0f,
                1.0f,
                20.0f,
                5.0f,
                0.30f,
                0.22f,
                degrees(50.0),
                0.48f, 0.85f, 1.0f,
                AnchorMode.TERMINAL_SCREEN,
                new Vec3d(0.5, 0.62, 0.5)
        ));
    }

    private static void register(Block block, Definition definition) {
        if (block != null && definition != null) {
            DEFINITIONS.put(block, definition);
        }
    }

    private static void reset() {
        closeAllLights();
        LOADED_CHUNKS.clear();
        CHUNK_EMITTERS.clear();
        EMITTERS.clear();
        DIRTY_POSITIONS.clear();
        rendererWasReady = false;
        fullRebuildPending = true;
        chunkSeeded = false;
        validationTick = Long.MIN_VALUE;
    }

    private static void processDirtyState(ClientWorld world) {
        boolean changed = false;
        if (fullRebuildPending) {
            rebuildEmitterIndex(world);
            changed = true;
            fullRebuildPending = false;
        }
        if (!DIRTY_POSITIONS.isEmpty()) {
            for (BlockPos pos : List.copyOf(DIRTY_POSITIONS)) {
                applyDirtyPosition(world, pos);
            }
            DIRTY_POSITIONS.clear();
            changed = true;
        }
        if (changed) {
            rebuildLightHandles(world);
        }
    }

    private static void rebuildEmitterIndex(ClientWorld world) {
        Set<BlockPos> rebuiltEmitters = new HashSet<>();
        Map<Long, Set<BlockPos>> rebuiltByChunk = new HashMap<>();
        for (long chunkKey : LOADED_CHUNKS) {
            scanChunk(world, chunkPos(chunkKey), rebuiltEmitters, rebuiltByChunk);
        }

        // Avoid wiping all lights if this pass raced an in-flight chunk update and found nothing.
        if (rebuiltEmitters.isEmpty() && !EMITTERS.isEmpty() && !LOADED_CHUNKS.isEmpty()) {
            return;
        }

        EMITTERS.clear();
        EMITTERS.addAll(rebuiltEmitters);
        CHUNK_EMITTERS.clear();
        CHUNK_EMITTERS.putAll(rebuiltByChunk);
    }

    private static void applyDirtyPosition(ClientWorld world, BlockPos pos) {
        removeEmitter(pos);
        BlockState state = world.getBlockState(pos);
        if (definition(state) != null) {
            addEmitter(pos);
        }
    }

    private static void addEmitter(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        EMITTERS.add(immutable);
        CHUNK_EMITTERS.computeIfAbsent(chunkKey(immutable), key -> new HashSet<>()).add(immutable);
    }

    private static void scanChunk(ClientWorld world,
                                  ChunkPos chunkPos,
                                  Set<BlockPos> emitterSink,
                                  Map<Long, Set<BlockPos>> byChunkSink) {
        ClientBlockScanHelper.scanChunk(world, chunkPos, state -> definition(state) != null, (pos, state) -> {
            BlockPos immutable = pos.toImmutable();
            emitterSink.add(immutable);
            byChunkSink.computeIfAbsent(chunkKey(immutable), key -> new HashSet<>()).add(immutable);
        });
    }

    private static void removeEmitter(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        EMITTERS.remove(immutable);
        long key = chunkKey(immutable);
        Set<BlockPos> inChunk = CHUNK_EMITTERS.get(key);
        if (inChunk == null) {
            return;
        }
        inChunk.remove(immutable);
        if (inChunk.isEmpty()) {
            CHUNK_EMITTERS.remove(key);
        }
    }

    private static void rebuildLightHandles(ClientWorld world) {
        if (!rendererReady()) {
            closeAllLights();
            return;
        }
        if (EMITTERS.isEmpty()) {
            closeAllLights();
            return;
        }

        Set<String> desiredIds = new HashSet<>();
        List<BlockPos> ordered = new ArrayList<>(EMITTERS);
        ordered.sort(Comparator.comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos origin : ordered) {
            if (!visited.add(origin)) {
                continue;
            }
            BlockState state = world.getBlockState(origin);
            Definition definition = definition(state);
            if (definition == null) {
                continue;
            }

            // One block emitter = one Veil light group. No merge/cluster aggregation.
            String id = groupId(definition, List.of(origin), Block.getRawIdFromState(state));
            desiredIds.add(id);
            ActiveLight existing = ACTIVE.get(id);
            if (existing != null && existing.isHandleStateValid()) {
                continue;
            }
            if (existing != null) {
                closeLight(existing);
                ACTIVE.remove(id);
            }
            createActiveLight(world, definition, state, List.of(origin), id);
        }

        Iterator<Map.Entry<String, ActiveLight>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveLight> entry = iterator.next();
            if (desiredIds.contains(entry.getKey())) {
                continue;
            }
            closeLight(entry.getValue());
            iterator.remove();
        }
    }

    private static List<BlockPos> collectComponent(ClientWorld world, BlockPos origin, BlockState seedState, Set<BlockPos> visited) {
        Definition definition = definition(seedState);
        if (definition == null) {
            return List.of(origin);
        }
        int mergeState = Block.getRawIdFromState(seedState);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> out = new ArrayList<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            out.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.offset(direction);
                if (!EMITTERS.contains(next) || visited.contains(next)) {
                    continue;
                }
                BlockState nextState = world.getBlockState(next);
                if (definition(nextState) != definition || Block.getRawIdFromState(nextState) != mergeState) {
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }
        return out;
    }

    private static List<List<BlockPos>> partitionComponent(List<BlockPos> component, Definition definition) {
        if (component.size() <= 1 || !definition.clusterable) {
            return List.of(component);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : component) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int spanX = maxX - minX + 1;
        int spanY = maxY - minY + 1;
        int spanZ = maxZ - minZ + 1;
        int dominantSpan = Math.max(spanX, Math.max(spanY, spanZ));
        int secondarySpan = spanX + spanY + spanZ - dominantSpan - Math.min(spanX, Math.min(spanY, spanZ));

        if (component.size() <= definition.maxClusterSize && secondarySpan <= 2) {
            return List.of(component);
        }

        Direction.Axis axis = dominantAxis(spanX, spanY, spanZ);
        List<BlockPos> ordered = new ArrayList<>(component);
        ordered.sort(Comparator.comparingInt((BlockPos pos) -> axisValue(pos, axis))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        List<List<BlockPos>> out = new ArrayList<>();
        List<BlockPos> current = new ArrayList<>();
        int previousAxisValue = Integer.MIN_VALUE;
        for (BlockPos pos : ordered) {
            int axisValue = axisValue(pos, axis);
            if (!current.isEmpty() && (current.size() >= definition.maxClusterSize || axisValue - previousAxisValue > 1)) {
                out.add(List.copyOf(current));
                current.clear();
            }
            current.add(pos);
            previousAxisValue = axisValue;
        }
        if (!current.isEmpty()) {
            out.add(List.copyOf(current));
        }
        return out;
    }

    private static void createActiveLight(ClientWorld world,
                                          Definition definition,
                                          BlockState state,
                                          List<BlockPos> positions,
                                          String id) {
        List<Anchor> anchors = new ArrayList<>(positions.size());
        Direction emission = emissionDirection(state, definition);
        for (BlockPos pos : positions) {
            anchors.add(definition.computeAnchor(world, pos, world.getBlockState(pos), emission));
        }

        Vec3d centroid = Vec3d.ZERO;
        for (Anchor anchor : anchors) {
            centroid = centroid.add(anchor.position);
        }
        centroid = centroid.multiply(1.0 / anchors.size());

        float clusterScale = positions.size() <= 1 ? 1.0f : (float) Math.sqrt(positions.size());
        float distanceScale = positions.size() <= 1 ? 1.0f : 1.0f + (0.18f * (clusterScale - 1.0f));
        float radiusScale = positions.size() <= 1 ? 1.0f : 1.0f + (0.26f * (clusterScale - 1.0f));

        boolean veilOcclusionEnabled = OdysseyConfig.occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled();
        AreaLightData area = null;
        PointLightData point = null;
        LightRenderHandle<AreaLightData> areaHandle = null;
        LightRenderHandle<PointLightData> pointHandle = null;

        if (definition.areaBrightness > EPSILON) {
            area = new AreaLightData()
                    .setBrightness(Math.min(OdysseyConfig.effectiveMaxLightBrightness(), definition.areaBrightness * clusterScale))
                    .setColor(definition.r, definition.g, definition.b)
                    .setSize(definition.areaSizeX, definition.areaSizeY)
                    .setAngle(definition.areaAngle)
                    .setDistance(Math.min(OdysseyConfig.effectiveMaxAreaDistance(), definition.areaDistance * distanceScale))
                    .setOcclusionEnabled(veilOcclusionEnabled);
            area.getPosition().set(centroid.x, centroid.y, centroid.z);
            area.getOrientation().set(orientationFor(emission));
            areaHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(area);
        }

        if (definition.pointBrightness > EPSILON) {
            point = new PointLightData()
                    .setBrightness(Math.min(OdysseyConfig.effectiveMaxLightBrightness(), definition.pointBrightness * clusterScale))
                    .setColor(definition.r, definition.g, definition.b)
                    .setRadius(Math.min(OdysseyConfig.effectiveMaxPointRadius(), definition.pointRadius * radiusScale))
                    .setOcclusionEnabled(veilOcclusionEnabled);
            point.setPosition(centroid.x, centroid.y, centroid.z);
            pointHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(point);
        }

        ACTIVE.put(id, new ActiveLight(
                definition,
                area,
                point,
                areaHandle,
                pointHandle,
                new FlickerState(definition.flickerMode),
                clusterScale
        ));
    }

    private static void updateDynamicLights(ClientWorld world, MinecraftClient client) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long worldTime = world.getTime();
        if (worldTime == validationTick) {
            return;
        }
        validationTick = worldTime;

        float dt = client.getRenderTickCounter().getLastFrameDuration();
        boolean veilOcclusionEnabled = OdysseyConfig.occlusionEnabled && VeilNativeOcclusionMode.isNativeEnabled();
        boolean needsRebuild = false;

        for (ActiveLight light : ACTIVE.values()) {
            if (!light.isHandleStateValid()) {
                needsRebuild = true;
                break;
            }

            float brightnessFactor = light.flicker.advance(dt);
            if (light.area != null) {
                float targetBrightness = Math.min(
                        OdysseyConfig.effectiveMaxLightBrightness(),
                        light.definition.areaBrightness * light.clusterScale * brightnessFactor
                );
                if (Math.abs(light.area.getBrightness() - targetBrightness) > EPSILON) {
                    light.area.setBrightness(targetBrightness);
                    if (light.areaHandle != null && light.areaHandle.isValid()) {
                        light.areaHandle.markDirty();
                    }
                }
                if (light.area.isOcclusionEnabled() != veilOcclusionEnabled) {
                    light.area.setOcclusionEnabled(veilOcclusionEnabled);
                    if (light.areaHandle != null && light.areaHandle.isValid()) {
                        light.areaHandle.markDirty();
                    }
                }
            }

            if (light.point != null) {
                float targetBrightness = Math.min(
                        OdysseyConfig.effectiveMaxLightBrightness(),
                        light.definition.pointBrightness * light.clusterScale * brightnessFactor
                );
                if (Math.abs(light.point.getBrightness() - targetBrightness) > EPSILON) {
                    light.point.setBrightness(targetBrightness);
                    if (light.pointHandle != null && light.pointHandle.isValid()) {
                        light.pointHandle.markDirty();
                    }
                }
                if (light.point.isOcclusionEnabled() != veilOcclusionEnabled) {
                    light.point.setOcclusionEnabled(veilOcclusionEnabled);
                    if (light.pointHandle != null && light.pointHandle.isValid()) {
                        light.pointHandle.markDirty();
                    }
                }
            }
        }

        if (needsRebuild) {
            fullRebuildPending = true;
        }
    }

    private static void closeAllLights() {
        for (ActiveLight light : ACTIVE.values()) {
            closeLight(light);
        }
        ACTIVE.clear();
    }

    private static void closeLight(ActiveLight light) {
        if (light == null) {
            return;
        }
        if (light.areaHandle != null && light.areaHandle.isValid()) {
            light.areaHandle.close();
        }
        if (light.pointHandle != null && light.pointHandle.isValid()) {
            light.pointHandle.close();
        }
    }

    private static void seedLoadedChunks(ClientWorld world, MinecraftClient client) {
        if (chunkSeeded) {
            return;
        }
        if (!LOADED_CHUNKS.isEmpty()) {
            chunkSeeded = true;
            return;
        }
        BlockPos center = client.player != null ? client.player.getBlockPos() : BlockPos.ORIGIN;
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int x = chunkX - FALLBACK_SCAN_RADIUS; x <= chunkX + FALLBACK_SCAN_RADIUS; x++) {
            for (int z = chunkZ - FALLBACK_SCAN_RADIUS; z <= chunkZ + FALLBACK_SCAN_RADIUS; z++) {
                if (world.getChunkManager().getChunk(x, z) != null) {
                    LOADED_CHUNKS.add(chunkKey(new ChunkPos(x, z)));
                }
            }
        }
        chunkSeeded = true;
        fullRebuildPending = true;
    }

    private static boolean rendererReady() {
        return VeilRenderSystem.renderer() != null && VeilRenderSystem.renderer().getLightRenderer() != null;
    }

    private static Definition definition(BlockState state) {
        return state == null ? null : DEFINITIONS.get(state.getBlock());
    }

    private static Direction emissionDirection(BlockState state, Definition definition) {
        if (definition.orientationMode == OrientationMode.FACING_FORWARD) {
            DirectionProperty property = state.contains(Properties.HORIZONTAL_FACING) ? Properties.HORIZONTAL_FACING : Properties.FACING;
            if (state.contains(property)) {
                return state.get(property);
            }
            return Direction.NORTH;
        }

        if (state.contains(Properties.FACING)) {
            return state.get(Properties.FACING).getOpposite();
        }
        return Direction.DOWN;
    }

    private static Quaternionf orientationFor(Direction direction) {
        return switch (direction) {
            case SOUTH -> new Quaternionf();
            case NORTH -> new Quaternionf().rotateY((float) Math.PI);
            case EAST -> new Quaternionf().rotateY((float) (-Math.PI / 2.0));
            case WEST -> new Quaternionf().rotateY((float) (Math.PI / 2.0));
            case UP -> new Quaternionf().rotateX((float) (-Math.PI / 2.0));
            case DOWN -> new Quaternionf().rotateX((float) (Math.PI / 2.0));
        };
    }

    private static String groupId(Definition definition, Collection<BlockPos> positions, int stateKey) {
        if (positions == null || positions.isEmpty()) {
            return definition.hash() + ":" + stateKey;
        }
        if (positions.size() == 1) {
            BlockPos only = positions.iterator().next();
            return definition.hash() + ":" + stateKey + ":" + only.getX() + "," + only.getY() + "," + only.getZ();
        }
        List<BlockPos> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        StringBuilder builder = new StringBuilder(estimateGroupIdCapacity(ordered.size()));
        builder.append(definition.hash()).append(':').append(stateKey).append(':');
        for (BlockPos pos : ordered) {
            builder.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append(';');
        }
        return builder.toString();
    }

    private static int estimateGroupIdCapacity(int positionCount) {
        if (positionCount <= 0) {
            return 32;
        }
        // Cap the eager allocation so very large groups don't attempt oversized buffers up front.
        long estimated = 32L + positionCount * 20L;
        return (int) Math.min(estimated, 4096L);
    }

    private static Direction.Axis dominantAxis(int spanX, int spanY, int spanZ) {
        if (spanY >= spanX && spanY >= spanZ) {
            return Direction.Axis.Y;
        }
        return spanX >= spanZ ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static int axisValue(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static long chunkKey(ChunkPos pos) {
        return (((long) pos.x) << 32) ^ (pos.z & 0xffffffffL);
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(new ChunkPos(pos));
    }

    private static ChunkPos chunkPos(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }

    private static float degrees(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private enum OrientationMode {
        SUPPORT_OPPOSITE,
        FACING_FORWARD
    }

    private enum AnchorMode {
        SHAPE_FACE,
        TERMINAL_SCREEN
    }

    private enum FlickerMode {
        NONE,
        SOFT
    }

    private record Definition(
            FlickerMode flickerMode,
            boolean clusterable,
            int maxClusterSize,
            OrientationMode orientationMode,
            float areaBrightness,
            float pointBrightness,
            float areaDistance,
            float pointRadius,
            float areaSizeX,
            float areaSizeY,
            float areaAngle,
            float r,
            float g,
            float b,
            AnchorMode anchorMode,
            Vec3d localFallback
    ) {
        Anchor computeAnchor(ClientWorld world, BlockPos pos, BlockState state, Direction emissionDirection) {
            return switch (anchorMode) {
                case TERMINAL_SCREEN -> terminalAnchor(pos, emissionDirection, localFallback);
                case SHAPE_FACE -> shapeFaceAnchor(world, pos, state, emissionDirection, localFallback);
            };
        }

        int hash() {
            return Objects.hash(
                    flickerMode,
                    clusterable,
                    maxClusterSize,
                    orientationMode,
                    areaBrightness,
                    pointBrightness,
                    areaDistance,
                    pointRadius,
                    areaSizeX,
                    areaSizeY,
                    areaAngle,
                    r,
                    g,
                    b,
                    anchorMode,
                    localFallback
            );
        }
    }

    private record Anchor(Vec3d position) {
    }

    private static Anchor shapeFaceAnchor(ClientWorld world, BlockPos pos, BlockState state, Direction face, Vec3d fallback) {
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) {
            shape = state.getCollisionShape(world, pos, ShapeContext.absent());
        }
        Box bounds = shape.isEmpty() ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) : shape.getBoundingBox();
        double x = pos.getX() + (bounds.minX + bounds.maxX) * 0.5;
        double y = pos.getY() + (bounds.minY + bounds.maxY) * 0.5;
        double z = pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5;

        switch (face) {
            case DOWN -> y = pos.getY() + bounds.minY - SURFACE_OFFSET;
            case UP -> y = pos.getY() + bounds.maxY + SURFACE_OFFSET;
            case NORTH -> z = pos.getZ() + bounds.minZ - SURFACE_OFFSET;
            case SOUTH -> z = pos.getZ() + bounds.maxZ + SURFACE_OFFSET;
            case WEST -> x = pos.getX() + bounds.minX - SURFACE_OFFSET;
            case EAST -> x = pos.getX() + bounds.maxX + SURFACE_OFFSET;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return new Anchor(new Vec3d(pos.getX() + fallback.x, pos.getY() + fallback.y, pos.getZ() + fallback.z));
        }
        return new Anchor(new Vec3d(x, y, z));
    }

    private static Anchor terminalAnchor(BlockPos pos, Direction face, Vec3d fallback) {
        double x = pos.getX() + fallback.x;
        double y = pos.getY() + fallback.y;
        double z = pos.getZ() + fallback.z;
        switch (face) {
            case NORTH -> z = pos.getZ() - SURFACE_OFFSET;
            case SOUTH -> z = pos.getZ() + 1.0 + SURFACE_OFFSET;
            case WEST -> x = pos.getX() - SURFACE_OFFSET;
            case EAST -> x = pos.getX() + 1.0 + SURFACE_OFFSET;
            default -> {
            }
        }
        return new Anchor(new Vec3d(x, y, z));
    }

    private static final class ActiveLight {
        private final Definition definition;
        private final AreaLightData area;
        private final PointLightData point;
        private final LightRenderHandle<AreaLightData> areaHandle;
        private final LightRenderHandle<PointLightData> pointHandle;
        private final FlickerState flicker;
        private final float clusterScale;

        private ActiveLight(Definition definition,
                            AreaLightData area,
                            PointLightData point,
                            LightRenderHandle<AreaLightData> areaHandle,
                            LightRenderHandle<PointLightData> pointHandle,
                            FlickerState flicker,
                            float clusterScale) {
            this.definition = definition;
            this.area = area;
            this.point = point;
            this.areaHandle = areaHandle;
            this.pointHandle = pointHandle;
            this.flicker = flicker;
            this.clusterScale = clusterScale;
        }

        private boolean isHandleStateValid() {
            if (area != null && (areaHandle == null || !areaHandle.isValid())) {
                return false;
            }
            if (point != null && (pointHandle == null || !pointHandle.isValid())) {
                return false;
            }
            return true;
        }
    }

    private static final class FlickerState {
        private final FlickerMode mode;
        private float timer;
        private float cooldown;
        private float duration;

        private FlickerState(FlickerMode mode) {
            this.mode = mode;
            this.cooldown = nextCooldown(mode);
        }

        private float advance(float dt) {
            if (mode == FlickerMode.NONE) {
                return 1.0f;
            }
            if (cooldown > 0.0f) {
                cooldown -= dt;
                return 1.0f;
            }
            timer += dt;
            if (duration <= 0.0f) {
                duration = 0.08f + (float) (Math.random() * 0.16f);
            }
            if (timer >= duration) {
                timer = 0.0f;
                duration = 0.0f;
                cooldown = nextCooldown(mode);
                return 1.0f;
            }
            return 0.0f;
        }

        private static float nextCooldown(FlickerMode mode) {
            if (mode == FlickerMode.NONE) {
                return Float.MAX_VALUE;
            }
            return 5.0f + (float) (Math.random() * 25.0f);
        }
    }
}
