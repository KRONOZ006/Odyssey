package net.kronoz.odyssey.world;

import com.mojang.logging.LogUtils;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;

import java.util.*;

public final class FixedStructurePlacer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier[] FIRST_IDS = new Identifier[] {
            Identifier.of("odyssey", "test1"),
            Identifier.of("odyssey", "test")
    };
    private static final Identifier SECOND_ID = Identifier.of("odyssey", "test2");

    private static final BlockPos ORIGIN = new BlockPos(0, 12, 0);
    private static final RegistryKey<World> VOID_DIM = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("odyssey", "void"));

    private static final ArrayDeque<Runnable> JOBS = new ArrayDeque<>();
    private static final int BLOCKS_PER_TICK = 40000;

    private FixedStructurePlacer() {}

    public static void onWorldLoaded(ServerWorld world) {
        if (!world.getRegistryKey().equals(VOID_DIM)) return;

        var rm = world.getServer().getResourceManager();
        LOGGER.info("[Odyssey] namespaces={}", rm.getAllNamespaces());
        LOGGER.info("[Odyssey] expect odyssey:structure/test1.nbt present={}", rm.getResource(Identifier.of("odyssey","structure/test1.nbt")).isPresent());
        LOGGER.info("[Odyssey] expect odyssey:structure/test.nbt present={}",  rm.getResource(Identifier.of("odyssey","structure/test.nbt")).isPresent());
        LOGGER.info("[Odyssey] expect odyssey:structure/test2.nbt present={}", rm.getResource(Identifier.of("odyssey","structure/test2.nbt")).isPresent());

        PersistentStateManager psm = world.getPersistentStateManager();
        StructuresPlacedState state = psm.getOrCreate(StructuresPlacedState.TYPE, StructuresPlacedState.KEY);
        if (state.alreadyPlaced) { LOGGER.info("[Odyssey] already placed"); return; }

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
        StructureTemplateManager stm = world.getStructureTemplateManager();

        List<Pair<StructureTemplate, BlockPos>> tiles1 = findTiles(stm, FIRST_IDS, ORIGIN);
        if (tiles1.isEmpty()) {
            LOGGER.error("[Odyssey] cannot find FIRST structure (tried odyssey:test1 then odyssey:test) under data/odyssey/structure/");
            return;
        }
        Vec3i total1 = totalBounds(tiles1, ORIGIN);

        BlockPos secondOrigin = ORIGIN.add(total1.getX(), 0, 0);
        List<Pair<StructureTemplate, BlockPos>> tiles2 = findTiles(stm, new Identifier[]{ SECOND_ID }, secondOrigin);
        if (tiles2.isEmpty()) {
            LOGGER.error("[Odyssey] cannot find SECOND structure (odyssey:test2) under data/odyssey/structure/");
            return;
        }

        forceChunks(world, tiles1);
        forceChunks(world, tiles2);

        for (var p : tiles1) JOBS.add(new CarveTask(world, p.getRight(), p.getLeft().getSize(), 1));
        for (var p : tiles2) JOBS.add(new CarveTask(world, p.getRight(), p.getLeft().getSize(), 1));
        for (var p : tiles1) JOBS.add(() -> place(world, p.getLeft(), p.getRight()));
        for (var p : tiles2) JOBS.add(() -> place(world, p.getLeft(), p.getRight()));
        JOBS.add(() -> markPlaced(world));

        LOGGER.info("[Odyssey] scheduled: tiles1={} tiles2={} secondOrigin={}", tiles1.size(), tiles2.size(), secondOrigin);
    }

    private static List<Pair<StructureTemplate, BlockPos>> findTiles(StructureTemplateManager stm, Identifier[] ids, BlockPos origin) {
        // 1) try single file for the first id that exists
        for (Identifier id : ids) {
            StructureTemplate single = getStrict(stm, id);
            if (single != null) {
                LOGGER.info("[Odyssey] using single template {}", id);
                return List.of(new Pair<>(single, origin));
            }
        }
        // 2) try 2D grid: <idPath>_x_z
        for (Identifier id : ids) {
            var grid2 = collectGrid2D(stm, id, origin);
            if (!grid2.isEmpty()) {
                LOGGER.info("[Odyssey] using 2D grid for {}", id);
                return grid2;
            }
        }
        // 3) try 3D grid: <idPath>_x_y_z
        for (Identifier id : ids) {
            var grid3 = collectGrid3D(stm, id, origin);
            if (!grid3.isEmpty()) {
                LOGGER.info("[Odyssey] using 3D grid for {}", id);
                return grid3;
            }
        }
        return List.of();
    }

    private static StructureTemplate getStrict(StructureTemplateManager stm, Identifier id) {
        StructureTemplate t = stm.getTemplate(id).orElse(null);
        if (t != null) return t;
        Identifier underStructure = Identifier.of(id.getNamespace(), "structure/" + id.getPath());
        return stm.getTemplate(underStructure).orElse(null);
    }

    private static List<Pair<StructureTemplate, BlockPos>> collectGrid2D(StructureTemplateManager stm, Identifier base, BlockPos origin) {
        List<Pair<StructureTemplate, BlockPos>> out = new ArrayList<>();
        Vec3i tile = null;
        final int MAX = 256;
        for (int gx = 0; gx < MAX; gx++) {
            boolean anyInRow = false;
            for (int gz = 0; gz < MAX; gz++) {
                Identifier id = Identifier.of(base.getNamespace(), "structure/" + base.getPath() + "_" + gx + "_" + gz);
                StructureTemplate t = stm.getTemplate(id).orElse(null);
                if (t == null) { if (gz == 0) break; else continue; }
                if (tile == null) tile = t.getSize();
                BlockPos pos = origin.add(tile.getX()*gx, 0, tile.getZ()*gz);
                out.add(new Pair<>(t, pos));
                anyInRow = true;
            }
            if (!anyInRow) break;
        }
        return out;
    }

    private static List<Pair<StructureTemplate, BlockPos>> collectGrid3D(StructureTemplateManager stm, Identifier base, BlockPos origin) {
        List<Pair<StructureTemplate, BlockPos>> out = new ArrayList<>();
        Vec3i tile = null;
        final int MAX = 64; // 64^3 is déjà massif
        for (int gx = 0; gx < MAX; gx++) {
            boolean anyX = false;
            for (int gy = 0; gy < MAX; gy++) {
                boolean anyY = false;
                for (int gz = 0; gz < MAX; gz++) {
                    Identifier id = Identifier.of(base.getNamespace(), "structure/" + base.getPath() + "_" + gx + "_" + gy + "_" + gz);
                    StructureTemplate t = stm.getTemplate(id).orElse(null);
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

    private static Vec3i totalBounds(List<Pair<StructureTemplate, BlockPos>> tiles, BlockPos origin) {
        int maxX=0,maxY=0,maxZ=0;
        for (var p : tiles) {
            Vec3i s = p.getLeft().getSize();
            BlockPos rel = p.getRight().subtract(origin);
            maxX = Math.max(maxX, rel.getX()+s.getX());
            maxY = Math.max(maxY, rel.getY()+s.getY());
            maxZ = Math.max(maxZ, rel.getZ()+s.getZ());
        }
        return new Vec3i(maxX, maxY, maxZ);
    }

    private static void place(ServerWorld world, StructureTemplate template, BlockPos origin) {
        StructurePlacementData data = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(false)
                .addProcessor(WaterlogSanitizerProcessor.INSTANCE);
        Random rng = world.getRandom();
        template.place(world, origin, origin, data, rng, 2);
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
            int left = BLOCKS_PER_TICK;
            while (left-- > 0 && y <= maxY) {
                BlockPos p = new BlockPos(x, y, z);
                if (!world.isAir(p)) world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                x++; if (x > maxX) { x = minX; z++; if (z > maxZ) { z = minZ; y++; } }
            }
            if (y <= maxY) JOBS.add(this);
        }
    }

    public static final class StructuresPlacedState extends PersistentState {
        public static final String KEY = "odyssey_fixed_structures";
        public static final PersistentState.Type<StructuresPlacedState> TYPE =
                new PersistentState.Type<>(StructuresPlacedState::new, StructuresPlacedState::fromNbt, null);
        public boolean alreadyPlaced = false;
        public StructuresPlacedState() {}
        public static StructuresPlacedState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            StructuresPlacedState s = new StructuresPlacedState();
            s.alreadyPlaced = nbt.getBoolean("alreadyPlaced");
            return s;
        }
        @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            nbt.putBoolean("alreadyPlaced", alreadyPlaced);
            return nbt;
        }
    }

    private static final class WaterlogSanitizerProcessor extends StructureProcessor {
        static final WaterlogSanitizerProcessor INSTANCE = new WaterlogSanitizerProcessor();
        private WaterlogSanitizerProcessor() {}
        @Override
        public StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot,
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
}
