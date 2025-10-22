package net.kronoz.odyssey.world;

import com.mojang.logging.LogUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class HugeNbtPlacer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private HugeNbtPlacer() {}

    static final class Plan {
        final Vec3i size;
        final Map<ChunkPos, List<Pair<BlockPos, BlockState>>> byChunk = new HashMap<>();
        Plan(Vec3i size){ this.size = size; }
    }

    static Optional<NbtCompound> loadNbt(ServerWorld world, Identifier idBare) {
        var rm = world.getServer().getResourceManager();

        // dump once (on first call) to show all .nbt files under odyssey/(structure|structures)
        dumpOnce(rm);

        var candidates = List.of(
                Identifier.of(idBare.getNamespace(), "structure/"  + idBare.getPath() + ".nbt"),
                Identifier.of(idBare.getNamespace(), "structures/" + idBare.getPath() + ".nbt"),
                Identifier.of(idBare.getNamespace(), "structure/"  + idBare.getPath()),
                Identifier.of(idBare.getNamespace(), "structures/" + idBare.getPath())
        );

        for (var path : candidates) {
            try {
                var opt = rm.getResource(path);
                LogUtils.getLogger().info("[Odyssey] probe {} present={}", path, opt.isPresent());
                if (opt.isEmpty()) continue;

                // Fabric/Yarn 1.21.1: NbtIo.read(Path) — copy to temp, then read
                var tmp = java.nio.file.Files.createTempFile("odyssey_tmp_", ".nbt");
                try (var in = opt.get().getInputStream()) {
                    java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                var nbt = net.minecraft.nbt.NbtIo.read(tmp);
                java.nio.file.Files.deleteIfExists(tmp);
                return Optional.of(nbt);
            } catch (Exception e) {
                LogUtils.getLogger().warn("[Odyssey] read fail {}: {}", path, e.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean DUMPED = false;
    private static void dumpOnce(net.minecraft.resource.ResourceManager rm) {
        if (DUMPED) return;
        DUMPED = true;
        try {
            var found = rm.findResources("structure", id -> id.getPath().endsWith(".nbt"));
            var found2 = rm.findResources("structures", id -> id.getPath().endsWith(".nbt"));
            LogUtils.getLogger().info("[Odyssey] namespaces={}", rm.getAllNamespaces());
            LogUtils.getLogger().info("[Odyssey] list data/odyssey/structure/*.nbt -> {}", found.keySet());
            LogUtils.getLogger().info("[Odyssey] list data/odyssey/structures/*.nbt -> {}", found2.keySet());
        } catch (Exception e) {
            LogUtils.getLogger().warn("[Odyssey] dumpOnce error: {}", e.toString());
        }
    }




    static Optional<Plan> planFromNbt(NbtCompound nbt, BlockPos origin) {
        if (!nbt.contains("size", NbtElement.INT_ARRAY_TYPE)) return Optional.empty();
        int[] sz = nbt.getIntArray("size");
        if (sz.length < 3) return Optional.empty();
        Vec3i size = new Vec3i(sz[0], sz[1], sz[2]);

        if (!nbt.contains("palette", NbtElement.LIST_TYPE)) return Optional.empty();
        NbtList paletteList = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
        List<BlockState> palette = new ArrayList<>(paletteList.size());
        for (int i = 0; i < paletteList.size(); i++) {
            BlockState st = readState(paletteList.getCompound(i));
            if (st == null) st = Blocks.AIR.getDefaultState();
            palette.add(st);
        }

        if (!nbt.contains("blocks", NbtElement.LIST_TYPE)) return Optional.empty();
        NbtList blocks = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
        Plan plan = new Plan(size);

        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound be = blocks.getCompound(i);
            int[] pos = be.getIntArray("pos");
            if (pos.length < 3) continue;
            int idx = be.getInt("state");
            BlockState st = (idx >= 0 && idx < palette.size()) ? palette.get(idx) : Blocks.AIR.getDefaultState();
            BlockPos wp = origin.add(pos[0], pos[1], pos[2]);
            ChunkPos c = new ChunkPos(wp);
            plan.byChunk.computeIfAbsent(c, k -> new ArrayList<>()).add(new Pair<>(wp, st));
        }
        return Optional.of(plan);
    }

    private static BlockState readState(NbtCompound entry) {
        String name = entry.getString("Name");
        if (name == null || name.isEmpty()) return Blocks.AIR.getDefaultState();
        Block block = Registries.BLOCK.get(Identifier.tryParse(name));
        BlockState state = block.getDefaultState();
        if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
            NbtCompound props = entry.getCompound("Properties");
            for (String key : props.getKeys()) {
                Property<?> prop = state.getProperties().stream().filter(p -> p.getName().equals(key)).findFirst().orElse(null);
                if (prop == null) continue;
                String val = props.getString(key);
                state = withParsed(state, prop, val);
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static <T extends Comparable<T>> BlockState withParsed(BlockState state, Property<T> prop, String val) {
        Optional<T> parsed = ((Property)prop).parse(val);
        return parsed.map(t -> state.with(prop, t)).orElse(state);
    }

    static void forceChunks(ServerWorld world, Plan plan) {
        for (ChunkPos c : plan.byChunk.keySet()) {
            world.getChunkManager().getChunk(c.x, c.z, ChunkStatus.FULL, true);
        }
    }
}
