package net.kronoz.odyssey.block.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.*;

public final class SimpleBlockLightManager {
    private SimpleBlockLightManager() {}

    private static final float AL_BRIGHTNESS = 1.0f;
    private static final float AL_ANGLE = (float) Math.toRadians(40.0);
    private static final float AL_DISTANCE = 25.0f;
    private static final double AL_SIZE_X = 0.20;
    private static final double AL_SIZE_Y = 0.20;

    private static final float PL_BRIGHTNESS = 3.0f;
    private static final float PL_RADIUS = 3.0f;

    private static final float CLR_R = 1f, CLR_G = 0.796f, CLR_B = 0.494f;

    private static final Quaternionf ORIENTATION = new Quaternionf().rotateX((float)(-Math.PI/2.0));

    // ---- State ----
    private static final Map<BlockPos, LightRenderHandle<AreaLightData>> AREA_HANDLES = new HashMap<>();
    private static final Map<BlockPos, AreaLightData> AREA_DATA = new HashMap<>();

    private static final Map<BlockPos, LightRenderHandle<PointLightData>> POINT_HANDLES = new HashMap<>();
    private static final Map<BlockPos, PointLightData> POINT_DATA = new HashMap<>();

    private static final Set<BlockPos> PENDING_ADD = new HashSet<>();
    private static final Set<BlockPos> PENDING_REMOVE = new HashSet<>();
    private static boolean initialScanPending = true;

    private static boolean matches(BlockState st) {
        return st.getBlock() instanceof net.kronoz.odyssey.block.custom.LightBlock;
    }

    public static void initClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetForNewWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetForNewWorld());

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world == null || chunk == null) return;
            scanChunkForLights(world, chunk.getPos());
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (world == null || chunk == null) return;
            removeLightsInChunk(chunk.getPos());
        });

        WorldRenderEvents.START.register(ctx -> {
            if (!RenderSystem.isOnRenderThread()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;

            if (initialScanPending) {
                doInitialScan(mc.world);
                initialScanPending = false;
            }

            if (!PENDING_REMOVE.isEmpty()) {
                for (BlockPos pos : PENDING_REMOVE) removeNow(pos);
                PENDING_REMOVE.clear();
            }
            if (!PENDING_ADD.isEmpty()) {
                for (BlockPos pos : PENDING_ADD) {
                    BlockState st = mc.world.getBlockState(pos);
                    if (!st.isAir() && matches(st)) addNow(pos);
                }
                PENDING_ADD.clear();
            }

            Iterator<Map.Entry<BlockPos, LightRenderHandle<AreaLightData>>> it = AREA_HANDLES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, LightRenderHandle<AreaLightData>> e = it.next();
                BlockPos pos = e.getKey();
                BlockState st = mc.world.getBlockState(pos);
                if (st.isAir() || !matches(st)) {
                    LightRenderHandle<AreaLightData> ah = e.getValue();
                    if (ah != null && ah.isValid()) ah.close();
                    AREA_DATA.remove(pos);
                    LightRenderHandle<PointLightData> ph = POINT_HANDLES.remove(pos);
                    if (ph != null && ph.isValid()) ph.close();
                    POINT_DATA.remove(pos);
                    it.remove();
                }
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            for (BlockPos pos : AREA_DATA.keySet()) {
                Vec3d c = blockCenter(pos);

                AreaLightData al = AREA_DATA.get(pos);
                if (al != null) {
                    al.getPosition().set(c.x, c.y, c.z);
                    al.getOrientation().set(ORIENTATION);
                    LightRenderHandle<AreaLightData> ah = AREA_HANDLES.get(pos);
                    if (ah != null && ah.isValid()) ah.markDirty();
                }

                PointLightData pl = POINT_DATA.get(pos);
                if (pl != null) {
                    pl.setPosition((float)c.x, (float)c.y, (float)c.z);
                    LightRenderHandle<PointLightData> ph = POINT_HANDLES.get(pos);
                    if (ph != null && ph.isValid()) ph.markDirty();
                }
            }
        });
    }

    public static void requestAdd(BlockPos pos) {
        PENDING_REMOVE.remove(pos);
        PENDING_ADD.add(pos);
    }
    public static void requestRemove(BlockPos pos) {
        PENDING_ADD.remove(pos);
        PENDING_REMOVE.add(pos);
    }

    private static void resetForNewWorld() {
        for (LightRenderHandle<AreaLightData> h : AREA_HANDLES.values()) {
            if (h != null && h.isValid()) h.close();
        }
        AREA_HANDLES.clear();
        AREA_DATA.clear();

        for (LightRenderHandle<PointLightData> h : POINT_HANDLES.values()) {
            if (h != null && h.isValid()) h.close();
        }
        POINT_HANDLES.clear();
        POINT_DATA.clear();

        PENDING_ADD.clear();
        PENDING_REMOVE.clear();
        initialScanPending = true;
    }

    private static void doInitialScan(ClientWorld world) {
        if (world == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos p = (mc != null && mc.player != null) ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        int r = 4;
        int pcx = p.getX() >> 4, pcz = p.getZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (world.getChunkManager().getChunk(cx, cz) != null) {
                    scanChunkForLights(world, new ChunkPos(cx, cz));
                }
            }
        }
    }

    private static void scanChunkForLights(ClientWorld world, ChunkPos cpos) {
        int minY = world.getBottomY();
        int maxY = world.getTopY();
        int startX = cpos.getStartX();
        int startZ = cpos.getStartZ();

        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    BlockState st = world.getBlockState(pos);
                    if (!st.isAir() && matches(st)) {
                        requestAdd(pos);
                    }
                }
            }
        }
    }

    private static void removeLightsInChunk(ChunkPos cpos) {
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : AREA_HANDLES.keySet()) {
            if ((pos.getX() >> 4) == cpos.x && (pos.getZ() >> 4) == cpos.z) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : toRemove) requestRemove(pos);
    }

    private static void addNow(BlockPos pos) {
        if (AREA_HANDLES.containsKey(pos)) return;

        AreaLightData al = new AreaLightData()
                .setBrightness(AL_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setSize(AL_SIZE_X, AL_SIZE_Y)
                .setAngle(AL_ANGLE)
                .setDistance(AL_DISTANCE);

        Vec3d c = blockCenter(pos);
        al.getPosition().set(c.x, c.y, c.z);
        al.getOrientation().set(ORIENTATION);

        PointLightData pl = new PointLightData()
                .setBrightness(PL_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(PL_RADIUS);
        pl.setPosition((float)c.x, (float)c.y, (float)c.z);

        if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) {
            PENDING_ADD.add(pos);
            return;
        }

        LightRenderHandle<AreaLightData> ah =
                VeilRenderSystem.renderer().getLightRenderer().addLight(al);
        LightRenderHandle<PointLightData> ph =
                VeilRenderSystem.renderer().getLightRenderer().addLight(pl);

        AREA_DATA.put(pos, al);
        AREA_HANDLES.put(pos, ah);
        POINT_DATA.put(pos, pl);
        POINT_HANDLES.put(pos, ph);
    }

    private static void removeNow(BlockPos pos) {
        LightRenderHandle<AreaLightData> ah = AREA_HANDLES.remove(pos);
        if (ah != null && ah.isValid()) ah.close();
        AREA_DATA.remove(pos);

        LightRenderHandle<PointLightData> ph = POINT_HANDLES.remove(pos);
        if (ph != null && ph.isValid()) ph.close();
        POINT_DATA.remove(pos);
    }

    private static Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
    }
}
