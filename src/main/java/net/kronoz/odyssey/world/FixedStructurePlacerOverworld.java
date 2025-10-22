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
import net.minecraft.world.Heightmap;
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
    private static final RegistryKey<World> OVERWORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));

    private static final ArrayDeque<Runnable> JOBS = new ArrayDeque<>();
    private static final int BLOCKS_PER_TICK = 40000;

    private enum Phase { IDLE, CARVING, PLACING, DONE }
    private static Phase PHASE = Phase.IDLE;
    private static int CARVES_LEFT = 0;
    private static int PLACES_LEFT = 0;

    private static List<Pair<StructureTemplate, BlockPos>> TILES = List.of();

    private FixedStructurePlacerOverworld() {}

    public static void onWorldLoaded(ServerWorld world) {
        if (!world.getRegistryKey().equals(OVERWORLD)) return;

        PersistentStateManager psm = world.getPersistentStateManager();
        StructuresPlacedState state = psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
        if (state.alreadyPlaced) {
            if (state.hasSpawn()) {
                world.setSpawnPos(new BlockPos(state.spawnX, state.spawnY, state.spawnZ), 0.0f);
            }
            LOGGER.info("[Odyssey] city already placed");
            return;
        }

        enqueue(world);
    }

    public static void tick(MinecraftServer server) {
        int steps = 64;
        while (steps-- > 0) {
            Runnable r = JOBS.poll();
            if (r == null) break;
            r.run();
        }
    }

    private static void enqueue(ServerWorld world) {
        if (PHASE != Phase.IDLE) return;

        StructureTemplateManager stm = world.getStructureTemplateManager();
        List<Pair<StructureTemplate, BlockPos>> tiles = findGrid3D(stm, BASES, ORIGIN);
        if (tiles.isEmpty()) {
            LOGGER.error("[Odyssey] No city grid tiles found (expected cityX_Y_Z.nbt or city_X_Y_Z.nbt)");
            return;
        }
        assertUniformTileSize(tiles);
        forceChunks(world, tiles);

        TILES = tiles;

        CARVES_LEFT = tiles.size();
        PHASE = Phase.CARVING;

        for (var p : tiles) {
            JOBS.add(new CarveTask(world, p.getRight(), p.getLeft().getSize(), 1));
        }

        LOGGER.info("[Odyssey] queued {} carve jobs", tiles.size());
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
                    computeAndStoreCenterSpawn(world);
                    teleportOnlinePlayersUp(world, 10); // bump players +10 after gen
                    PHASE = Phase.DONE;
                    markPlaced(world);
                    LOGGER.info("[Odyssey] placement phase done");
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
        final int MAX = 128;
        for (int gx = 0; gx < MAX; gx++) {
            boolean anyX = false;
            for (int gy = 0; gy < MAX; gy++) {
                boolean anyY = false;
                for (int gz = 0; gz < MAX; gz++) {
                    String name = underscore
                            ? base.getPath() + "_" + gx + "_" + gy + "_" + gz
                            : base.getPath() + gx + "_" + gy + "_" + gz;
                    Identifier id = Identifier.of(base.getNamespace(), name);
                    StructureTemplate t = getTemplate(stm, id);
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
        LOGGER.info("[Odyssey] placed {} at {} -> {}", template, origin, ok);
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
        for (var p : tiles) {
            Vec3i s = p.getLeft().getSize();
            BlockPos o = p.getRight();
            int minX = o.getX() - 1, minZ = o.getZ() - 1, maxX = o.getX() + s.getX(), maxZ = o.getZ() + s.getZ();
            for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
                for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
                    world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }
        }
    }

    private static final class CarveTask implements Runnable {
        private final ServerWorld world;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private int x, y, z;
        private boolean started;

        CarveTask(ServerWorld world, BlockPos origin, Vec3i size, int margin) {
            this.world = world;
            this.minX = origin.getX() - margin;
            this.minY = origin.getY() - margin;
            this.minZ = origin.getZ() - margin;
            this.maxX = origin.getX() + size.getX() + margin - 1;
            this.maxY = origin.getY() + size.getY() + margin - 1;
            this.maxZ = origin.getZ() + size.getZ() + margin - 1;
            this.x = minX; this.y = minY; this.z = minZ;
        }

        @Override public void run() {
            if (!started) { started = true; }
            int left = BLOCKS_PER_TICK;
            while (left-- > 0 && y <= maxY) {
                BlockPos p = new BlockPos(x, y, z);
                if (!world.isAir(p)) world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
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
        Box aabb = computeTilesAABB(TILES);
        int centerX = (int)Math.floor((aabb.minX + aabb.maxX) * 0.5);
        int centerZ = (int)Math.floor((aabb.minZ + aabb.maxZ) * 0.5);

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
        int minSafeY = (int)aabb.minY + 2;
        if (topY < minSafeY) topY = minSafeY;
        BlockPos spawn = new BlockPos(centerX, topY, centerZ);

        PersistentStateManager psm = world.getPersistentStateManager();
        StructuresPlacedState state = psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
        state.spawnX = spawn.getX();
        state.spawnY = spawn.getY();
        state.spawnZ = spawn.getZ();
        state.markDirty();
        psm.save();

        world.setSpawnPos(spawn, 0.0f);
        LOGGER.info("[Odyssey] computed center spawn at {}", spawn);
    }

    private static void teleportOnlinePlayersUp(ServerWorld overworld, int dy) {
        var psm = overworld.getPersistentStateManager();
        var state = psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
        if (!state.hasSpawn()) return;

        int sx = state.spawnX;
        int sz = state.spawnZ;

        overworld.getChunkManager().getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, true);

        int baseY = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
        int y = Math.max(baseY, state.spawnY) + 5;

        double tx = sx + 10.5;
        double ty = y + 0.1;
        double tz = sz + 10.5;

        var server = overworld.getServer();
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey() != OVERWORLD) continue;
            player.fallDistance = 0.0f;
            player.teleport(overworld, tx, ty, tz, player.getYaw(), player.getPitch());
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

        public StructuresPlacedState() {}

        public boolean hasSpawn() {
            return spawnX != Integer.MIN_VALUE;
        }

        public static StructuresPlacedState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            StructuresPlacedState s = new StructuresPlacedState();
            s.alreadyPlaced = nbt.getBoolean("alreadyPlaced");
            if (nbt.contains("spawnX")) {
                s.spawnX = nbt.getInt("spawnX");
                s.spawnY = nbt.getInt("spawnY");
                s.spawnZ = nbt.getInt("spawnZ");
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
