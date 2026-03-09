package net.kronoz.odyssey.world;

import com.mojang.logging.LogUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class FixedStructurePlacerOverworld {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier[] BASES = new Identifier[] {
            Identifier.of("odyssey", "city")
    };

    private static final BlockPos ORIGIN = new BlockPos(-120, 50, -120);
    public static final BlockPos FORCED_SPAWN = new BlockPos(63, 60, 36);
    public static final int SPAWN_SANCTUARY_RADIUS = 2;
    public static final int SPAWN_SANCTUARY_Y_ABOVE = 3;
    private static final RegistryKey<World> OVERWORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));

    private static final ArrayDeque<Runnable> JOBS = new ArrayDeque<>();
    private static final int TASKS_PER_TICK = Integer.getInteger("odyssey.structure.tasks_per_tick", 128);
    private static final int BLOCKS_PER_TICK = Integer.getInteger("odyssey.structure.blocks_per_tick", 300000);
    private static final int GRID_PROBE_MAX = Integer.getInteger("odyssey.structure.grid_max", 48);
    private static final int CARVE_MARGIN = 1;
    private static final boolean VERBOSE_PLACEMENT_LOGS = Boolean.getBoolean("odyssey.structure.verbose");

    private enum Phase { IDLE, CARVING, PLACING, DONE }
    private static Phase PHASE = Phase.IDLE;
    private static int CARVES_LEFT = 0;
    private static int PLACES_LEFT = 0;
    private static long ENQUEUE_NANOS = 0L;

    private static List<Pair<StructureTemplate, BlockPos>> TILES = List.of();

    private FixedStructurePlacerOverworld() {}

    public static void onWorldLoaded(ServerWorld world) {
        if (!world.getRegistryKey().equals(OVERWORLD)) return;

        StructuresPlacedState state = getState(world);
        if (state.alreadyPlaced) {
            ensureForcedSpawn(world, state, true);
            if (!state.hasProtectedBounds()) {
                rebuildProtectedBoundsFromTemplates(world, state);
            }
            LOGGER.info("[Odyssey] city already placed");
            return;
        }

        enqueue(world);
    }

    public static void tick(MinecraftServer server) {
        int steps = TASKS_PER_TICK;
        while (steps-- > 0) {
            Runnable r = JOBS.poll();
            if (r == null) break;
            r.run();
        }
    }

    private static void enqueue(ServerWorld world) {
        if (PHASE != Phase.IDLE) return;

        ENQUEUE_NANOS = System.nanoTime();
        StructureTemplateManager stm = world.getStructureTemplateManager();
        List<Pair<StructureTemplate, BlockPos>> tiles = findGrid3D(stm, BASES, ORIGIN);
        if (tiles.isEmpty()) {
            LOGGER.error("[Odyssey] No city grid tiles found (expected cityX_Y_Z.nbt or city_X_Y_Z.nbt)");
            return;
        }
        assertUniformTileSize(tiles);
        forceChunks(world, tiles);

        TILES = tiles;
        PHASE = Phase.CARVING;
        Bounds carveBounds = computeBounds(tiles, CARVE_MARGIN);
        if (shouldCarve(world, carveBounds)) {
            CARVES_LEFT = 1;
            JOBS.add(new CarveTask(world, carveBounds));
            LOGGER.info("[Odyssey] queued carve bounds ({}, {}, {}) -> ({}, {}, {})",
                    carveBounds.minX, carveBounds.minY, carveBounds.minZ,
                    carveBounds.maxX, carveBounds.maxY, carveBounds.maxZ);
        } else {
            CARVES_LEFT = 0;
            LOGGER.info("[Odyssey] carve skipped (area already mostly air)");
            enqueueCleanupThenPlacements(world);
        }
    }

    private static void enqueueCleanupThenPlacements(ServerWorld world) {
        Box full = computeTilesAABB(TILES);
        JOBS.add(new CleanupDroppedItemsTask(world, full));
    }

    private static void enqueuePlacements(ServerWorld world) {
        if (PHASE != Phase.CARVING) return;
        PHASE = Phase.PLACING;
        PLACES_LEFT = TILES.size();

        for (var p : TILES) {
            JOBS.add(() -> {
                place(world, p.getLeft(), p.getRight());
                if (--PLACES_LEFT == 0) {
                    updateProtectionBoundsFromTiles(world);
                    computeAndStoreCenterSpawn(world);
                    teleportOnlinePlayersToSpawn(world);
                    PHASE = Phase.DONE;
                    markPlaced(world);
                    long elapsedMs = (System.nanoTime() - ENQUEUE_NANOS) / 1_000_000L;
                    LOGGER.info("[Odyssey] placement phase done in {} ms", elapsedMs);
                }
            });
        }
        LOGGER.info("[Odyssey] queued {} placement jobs", TILES.size());
    }

    private static List<Pair<StructureTemplate, BlockPos>> findGrid3D(StructureTemplateManager stm, Identifier[] bases, BlockPos origin) {
        for (Identifier base : bases) {
            var grid = collectGrid3D(stm, base, origin, false);
            if (!grid.isEmpty()) {
                LOGGER.info("[Odyssey] Using 3D grid w/o underscore for {}", base);
                return grid;
            }
            grid = collectGrid3D(stm, base, origin, true);
            if (!grid.isEmpty()) {
                LOGGER.info("[Odyssey] Using 3D grid with underscore for {}", base);
                return grid;
            }
        }
        return List.of();
    }

    private static StructureTemplate getTemplate(StructureTemplateManager stm, Identifier id) {
        StructureTemplate t = stm.getTemplate(id).orElse(null);
        if (t != null) return t;
        Identifier s1 = Identifier.of(id.getNamespace(), "structure/" + id.getPath());
        t = stm.getTemplate(s1).orElse(null);
        if (t != null) return t;
        Identifier s2 = Identifier.of(id.getNamespace(), "structures/" + id.getPath());
        return stm.getTemplate(s2).orElse(null);
    }

    private static List<Pair<StructureTemplate, BlockPos>> collectGrid3D(StructureTemplateManager stm, Identifier base, BlockPos origin, boolean underscore) {
        List<Pair<StructureTemplate, BlockPos>> out = new ArrayList<>();
        Vec3i tile = null;
        java.util.Map<Identifier, StructureTemplate> cache = new java.util.HashMap<>();
        for (int gx = 0; gx < GRID_PROBE_MAX; gx++) {
            boolean anyX = false;
            for (int gy = 0; gy < GRID_PROBE_MAX; gy++) {
                boolean anyY = false;
                for (int gz = 0; gz < GRID_PROBE_MAX; gz++) {
                    String name = underscore
                            ? base.getPath() + "_" + gx + "_" + gy + "_" + gz
                            : base.getPath() + gx + "_" + gy + "_" + gz;
                    Identifier id = Identifier.of(base.getNamespace(), name);
                    StructureTemplate t = cachedTemplate(stm, cache, id);
                    if (t == null) { if (gz == 0) break; else continue; }
                    if (tile == null) tile = t.getSize();
                    BlockPos pos = origin.add(tile.getX()*gx, tile.getY()*gy, tile.getZ()*gz);
                    out.add(new Pair<>(t, pos));
                    anyY = true;
                }
                if (anyY) anyX = true; else break;
            }
            if (!anyX) break;
        }
        return out;
    }

    private static StructureTemplate cachedTemplate(StructureTemplateManager stm, java.util.Map<Identifier, StructureTemplate> cache, Identifier id) {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        StructureTemplate template = getTemplate(stm, id);
        cache.put(id, template);
        return template;
    }

    private static void assertUniformTileSize(List<Pair<StructureTemplate, BlockPos>> tiles) {
        if (tiles.isEmpty()) return;
        Vec3i s0 = tiles.get(0).getLeft().getSize();
        for (var p : tiles) {
            Vec3i s = p.getLeft().getSize();
            if (!s.equals(s0)) {
                throw new IllegalStateException("[Odyssey] Tile size mismatch: expected " + s0 + " but found " + s +
                        " at " + p.getRight());
            }
        }
    }

    private static void place(ServerWorld world, StructureTemplate template, BlockPos origin) {
        StructurePlacementData data = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(false)
                .addProcessor(WaterlogSanitizerProcessor.INSTANCE)
                .addProcessor(RemoveStructureBlocksProcessor.INSTANCE);

        Random rng = world.getRandom();
        boolean ok = template.place(world, origin, origin, data, rng, 2);
        if (VERBOSE_PLACEMENT_LOGS) {
            LOGGER.info("[Odyssey] placed {} at {} -> {}", template, origin, ok);
        }
    }

    private static void markPlaced(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        StructuresPlacedState state = psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
        state.alreadyPlaced = true;
        state.markDirty();
        psm.save();
        LOGGER.info("[Odyssey] marked as placed");
    }

    private static void forceChunks(ServerWorld world, List<Pair<StructureTemplate, BlockPos>> tiles) {
        java.util.Set<Long> requested = new java.util.HashSet<>();
        for (var p : tiles) {
            Vec3i s = p.getLeft().getSize();
            BlockPos o = p.getRight();
            int minX = o.getX() - 1, minZ = o.getZ() - 1, maxX = o.getX() + s.getX(), maxZ = o.getZ() + s.getZ();
            for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
                for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
                    long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                    if (requested.add(key)) {
                        world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, true);
                    }
                }
            }
        }
    }

    private static Bounds computeBounds(List<Pair<StructureTemplate, BlockPos>> tiles, int margin) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var p : tiles) {
            Vec3i size = p.getLeft().getSize();
            BlockPos origin = p.getRight();
            minX = Math.min(minX, origin.getX() - margin);
            minY = Math.min(minY, origin.getY() - margin);
            minZ = Math.min(minZ, origin.getZ() - margin);
            maxX = Math.max(maxX, origin.getX() + size.getX() + margin - 1);
            maxY = Math.max(maxY, origin.getY() + size.getY() + margin - 1);
            maxZ = Math.max(maxZ, origin.getZ() + size.getZ() + margin - 1);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean shouldCarve(ServerWorld world, Bounds bounds) {
        if (bounds.isEmpty()) return false;

        final int step = 8;
        final int sampleCap = 4096;
        int samples = 0;
        int solids = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int y = bounds.minY; y <= bounds.maxY && samples < sampleCap; y += step) {
            for (int x = bounds.minX; x <= bounds.maxX && samples < sampleCap; x += step) {
                for (int z = bounds.minZ; z <= bounds.maxZ && samples < sampleCap; z += step) {
                    cursor.set(x, y, z);
                    if (!world.isAir(cursor)) solids++;
                    samples++;
                }
            }
        }

        if (samples == 0) return false;
        return ((float) solids / (float) samples) > 0.05f;
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean isEmpty() {
            return minX > maxX || minY > maxY || minZ > maxZ;
        }
    }

    private static final class CarveTask implements Runnable {
        private final ServerWorld world;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private int x, y, z;

        CarveTask(ServerWorld world, Bounds bounds) {
            this.world = world;
            this.minX = bounds.minX;
            this.minY = bounds.minY;
            this.minZ = bounds.minZ;
            this.maxX = bounds.maxX;
            this.maxY = bounds.maxY;
            this.maxZ = bounds.maxZ;
            this.x = minX; this.y = minY; this.z = minZ;
        }

        @Override public void run() {
            int left = BLOCKS_PER_TICK;
            while (left-- > 0 && y <= maxY) {
                cursor.set(x, y, z);
                if (!world.isAir(cursor)) world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 2);
                x++; if (x > maxX) { x = minX; z++; if (z > maxZ) { z = minZ; y++; } }
            }
            if (y <= maxY) {
                JOBS.add(this);
            } else {
                if (--CARVES_LEFT == 0) {
                    enqueueCleanupThenPlacements(world);
                }
            }
        }
    }

    private static Box computeTilesAABB(List<Pair<StructureTemplate, BlockPos>> tiles) {
        if (tiles.isEmpty()) return new Box(0,0,0,0,0,0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var p : tiles) {
            Vec3i s = p.getLeft().getSize();
            BlockPos o = p.getRight();
            minX = Math.min(minX, o.getX());
            minY = Math.min(minY, o.getY());
            minZ = Math.min(minZ, o.getZ());
            maxX = Math.max(maxX, o.getX() + s.getX() - 1);
            maxY = Math.max(maxY, o.getY() + s.getY() - 1);
            maxZ = Math.max(maxZ, o.getZ() + s.getZ() - 1);
        }
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static final class CleanupDroppedItemsTask implements Runnable {
        private final ServerWorld world;
        private final Box box;
        private boolean done;

        CleanupDroppedItemsTask(ServerWorld world, Box box) {
            this.world = world;
            this.box = box.expand(1.0);
        }

        @Override public void run() {
            if (done) return;
            var items = world.getEntitiesByClass(ItemEntity.class, box, e -> true);
            int count = 0;
            for (ItemEntity it : items) {
                it.discard();
                count++;
            }
            LOGGER.info("[Odyssey] removed {} dropped item entities in {}", count, box);
            done = true;
            enqueuePlacements(world);
        }
    }

    private static void computeAndStoreCenterSpawn(ServerWorld world) {
        StructuresPlacedState state = getState(world);
        state.spawnX = FORCED_SPAWN.getX();
        state.spawnY = FORCED_SPAWN.getY();
        state.spawnZ = FORCED_SPAWN.getZ();
        state.markDirty();
        world.getPersistentStateManager().save();

        world.getChunkManager().getChunk(FORCED_SPAWN.getX() >> 4, FORCED_SPAWN.getZ() >> 4, ChunkStatus.FULL, true);
        world.setSpawnPos(FORCED_SPAWN, 0.0f);
        LOGGER.info("[Odyssey] forced spawn at {}", FORCED_SPAWN);
    }

    private static void teleportOnlinePlayersToSpawn(ServerWorld overworld) {
        var state = getState(overworld);
        if (!state.hasSpawn()) return;

        int sx = state.spawnX;
        int sy = state.spawnY;
        int sz = state.spawnZ;

        overworld.getChunkManager().getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, true);

        double tx = sx + 0.5;
        double ty = sy + 0.1;
        double tz = sz + 0.5;

        var server = overworld.getServer();
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey() != OVERWORLD) continue;
            player.fallDistance = 0.0f;
            player.teleport(overworld, tx, ty, tz, player.getYaw(), player.getPitch());
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0, false, false));
        }
    }

    public static StructuresPlacedState getState(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
    }

    public static boolean isProtectedStructurePos(ServerWorld world, BlockPos pos) {
        if (world == null || world.getRegistryKey() != OVERWORLD) return false;
        StructuresPlacedState state = getState(world);
        return state.containsProtected(pos);
    }

    public static boolean isSpawnSanctuaryPos(ServerWorld world, BlockPos pos) {
        if (world == null || world.getRegistryKey() != OVERWORLD || pos == null) return false;
        StructuresPlacedState state = getState(world);
        if (!state.hasSpawn()) return false;
        int dx = Math.abs(pos.getX() - state.spawnX);
        int dz = Math.abs(pos.getZ() - state.spawnZ);
        int dy = pos.getY() - state.spawnY;
        return dx <= SPAWN_SANCTUARY_RADIUS
                && dz <= SPAWN_SANCTUARY_RADIUS
                && dy >= 0
                && dy <= SPAWN_SANCTUARY_Y_ABOVE;
    }

    private static void ensureForcedSpawn(ServerWorld world, StructuresPlacedState state, boolean persist) {
        boolean changed = !state.hasSpawn()
                || state.spawnX != FORCED_SPAWN.getX()
                || state.spawnY != FORCED_SPAWN.getY()
                || state.spawnZ != FORCED_SPAWN.getZ();
        if (changed) {
            state.spawnX = FORCED_SPAWN.getX();
            state.spawnY = FORCED_SPAWN.getY();
            state.spawnZ = FORCED_SPAWN.getZ();
            state.markDirty();
            if (persist) {
                world.getPersistentStateManager().save();
            }
            LOGGER.info("[Odyssey] forced spawn updated to {}", FORCED_SPAWN);
        }
        world.setSpawnPos(FORCED_SPAWN, 0.0f);
    }

    private static void updateProtectionBoundsFromTiles(ServerWorld world) {
        if (TILES.isEmpty()) return;
        StructuresPlacedState state = getState(world);
        Box aabb = computeTilesAABB(TILES);
        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.ceil(aabb.maxX) - 1;
        int maxY = (int) Math.ceil(aabb.maxY) - 1;
        int maxZ = (int) Math.ceil(aabb.maxZ) - 1;
        if (state.setProtectedBounds(minX, minY, minZ, maxX, maxY, maxZ)) {
            state.markDirty();
            world.getPersistentStateManager().save();
            LOGGER.info("[Odyssey] protected bounds set to ({}, {}, {}) -> ({}, {}, {})", minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static void rebuildProtectedBoundsFromTemplates(ServerWorld world, StructuresPlacedState state) {
        StructureTemplateManager stm = world.getStructureTemplateManager();
        List<Pair<StructureTemplate, BlockPos>> tiles = findGrid3D(stm, BASES, ORIGIN);
        if (tiles.isEmpty()) {
            LOGGER.warn("[Odyssey] unable to rebuild protected bounds: city templates not found");
            return;
        }

        Box aabb = computeTilesAABB(tiles);
        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.ceil(aabb.maxX) - 1;
        int maxY = (int) Math.ceil(aabb.maxY) - 1;
        int maxZ = (int) Math.ceil(aabb.maxZ) - 1;
        if (state.setProtectedBounds(minX, minY, minZ, maxX, maxY, maxZ)) {
            state.markDirty();
            world.getPersistentStateManager().save();
            LOGGER.info("[Odyssey] rebuilt protected bounds to ({}, {}, {}) -> ({}, {}, {})", minX, minY, minZ, maxX, maxY, maxZ);
        }
    }


    // ---- Persistent state

    public static final class StructuresPlacedState extends PersistentState {
        public static final String KEY = "odyssey_fixed_structures";
        public static final Type<StructuresPlacedState> TYPE =
                new Type<>(StructuresPlacedState::new, StructuresPlacedState::fromNbt, null);

        public boolean alreadyPlaced = false;
        public int spawnX = Integer.MIN_VALUE;
        public int spawnY = Integer.MIN_VALUE;
        public int spawnZ = Integer.MIN_VALUE;
        public int protectedMinX = Integer.MIN_VALUE;
        public int protectedMinY = Integer.MIN_VALUE;
        public int protectedMinZ = Integer.MIN_VALUE;
        public int protectedMaxX = Integer.MIN_VALUE;
        public int protectedMaxY = Integer.MIN_VALUE;
        public int protectedMaxZ = Integer.MIN_VALUE;

        public StructuresPlacedState() {}

        public boolean hasSpawn() {
            return spawnX != Integer.MIN_VALUE;
        }

        public boolean hasProtectedBounds() {
            return protectedMinX != Integer.MIN_VALUE;
        }

        public boolean containsProtected(BlockPos pos) {
            if (!hasProtectedBounds() || pos == null) return false;
            return pos.getX() >= protectedMinX && pos.getX() <= protectedMaxX
                    && pos.getY() >= protectedMinY && pos.getY() <= protectedMaxY
                    && pos.getZ() >= protectedMinZ && pos.getZ() <= protectedMaxZ;
        }

        public Box protectedBoundsBox() {
            if (!hasProtectedBounds()) return new Box(0, 0, 0, 0, 0, 0);
            return new Box(protectedMinX, protectedMinY, protectedMinZ, protectedMaxX + 1, protectedMaxY + 1, protectedMaxZ + 1);
        }

        public boolean setProtectedBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            boolean changed = protectedMinX != minX
                    || protectedMinY != minY
                    || protectedMinZ != minZ
                    || protectedMaxX != maxX
                    || protectedMaxY != maxY
                    || protectedMaxZ != maxZ;
            protectedMinX = minX;
            protectedMinY = minY;
            protectedMinZ = minZ;
            protectedMaxX = maxX;
            protectedMaxY = maxY;
            protectedMaxZ = maxZ;
            return changed;
        }

        public static StructuresPlacedState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            StructuresPlacedState s = new StructuresPlacedState();
            s.alreadyPlaced = nbt.getBoolean("alreadyPlaced");
            if (nbt.contains("spawnX")) {
                s.spawnX = nbt.getInt("spawnX");
                s.spawnY = nbt.getInt("spawnY");
                s.spawnZ = nbt.getInt("spawnZ");
            }
            if (nbt.contains("protectedMinX")) {
                s.protectedMinX = nbt.getInt("protectedMinX");
                s.protectedMinY = nbt.getInt("protectedMinY");
                s.protectedMinZ = nbt.getInt("protectedMinZ");
                s.protectedMaxX = nbt.getInt("protectedMaxX");
                s.protectedMaxY = nbt.getInt("protectedMaxY");
                s.protectedMaxZ = nbt.getInt("protectedMaxZ");
            }
            return s;
        }

        @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            nbt.putBoolean("alreadyPlaced", alreadyPlaced);
            if (hasSpawn()) {
                nbt.putInt("spawnX", spawnX);
                nbt.putInt("spawnY", spawnY);
                nbt.putInt("spawnZ", spawnZ);
            }
            if (hasProtectedBounds()) {
                nbt.putInt("protectedMinX", protectedMinX);
                nbt.putInt("protectedMinY", protectedMinY);
                nbt.putInt("protectedMinZ", protectedMinZ);
                nbt.putInt("protectedMaxX", protectedMaxX);
                nbt.putInt("protectedMaxY", protectedMaxY);
                nbt.putInt("protectedMaxZ", protectedMaxZ);
            }
            return nbt;
        }
    }

    // ---- Processors

    private static final class WaterlogSanitizerProcessor extends StructureProcessor {
        static final WaterlogSanitizerProcessor INSTANCE = new WaterlogSanitizerProcessor();
        private WaterlogSanitizerProcessor() {}
        @Override
        public StructureTemplate.StructureBlockInfo process(
                WorldView world, BlockPos pos, BlockPos pivot,
                StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo current,
                StructurePlacementData data) {
            var st = current.state();
            if (st.getProperties().contains(Properties.WATERLOGGED)) {
                st = st.with(Properties.WATERLOGGED, false);
                return new StructureTemplate.StructureBlockInfo(current.pos(), st, current.nbt());
            }
            return current;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.NOP; }
    }

    private static final class RemoveStructureBlocksProcessor extends StructureProcessor {
        static final RemoveStructureBlocksProcessor INSTANCE = new RemoveStructureBlocksProcessor();
        private RemoveStructureBlocksProcessor() {}
        @Override
        public StructureTemplate.StructureBlockInfo process(
                WorldView world, BlockPos pos, BlockPos pivot,
                StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo current,
                StructurePlacementData data) {
            if (current.state().isOf(Blocks.STRUCTURE_BLOCK)) {
                return null;
            }
            return current;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.NOP; }
    }
}
