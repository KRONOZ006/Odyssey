package net.kronoz.odyssey.block.custom;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.CollisionShapeHelper;
import net.minecraft.block.*;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Light2Block extends Block implements Waterloggable {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    private static final Map<Direction, VoxelShape> DIR_SHAPES =
            CollisionShapeHelper.loadDirectionalCollisionFromModelJson(Odyssey.MODID, "light_2");

    private static final double AL_SIZE_X = 0.20;
    private static final double AL_SIZE_Y = 0.20;
    private static final float  AL_ANGLE  = (float) Math.toRadians(40.0);
    private static final float  AL_DIST   = 25.0f;
    private static final float  PL_RADIUS = 5.0f;
    private static final float CLR_R = 1f, CLR_G = 0.87f, CLR_B = 0.95f;
    private static final Quaternionf ORIENTATION_DOWN = new Quaternionf().rotateX((float)(-Math.PI/2.0));

    private static final Map<BlockPos, LightRenderHandle<AreaLightData>>  AREA_HANDLES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, LightRenderHandle<PointLightData>> POINT_HANDLES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, AreaLightData>  AREA_DATA  = new ConcurrentHashMap<>();
    private static final Map<BlockPos, PointLightData> POINT_DATA = new ConcurrentHashMap<>();

    private static final Vec3d BONE_LIGHT_OFFSET = new Vec3d(0.5, 0.8, 0.5);
    private static final float BASE_BRIGHTNESS = 1.0f;
    private static boolean hooks = false;
    private static boolean rescanPending = false;
    private static boolean rendererWasReady = false;

    private static final java.util.Random RNG = new java.util.Random();

    private enum Phase { IDLE, BURST_OFF, BURST_ON, TAIL_WAIT, TAIL_OFF, TAIL_ON, SETTLE }

    private static final class Flicker {
        Phase phase = Phase.IDLE;
        float cooldown;
        int   flashesLeft;
        float timer;
        float offDur;
        float onDur;
        int   tailLeft;
        float tailGap;
        float settleTime;
        float settleDur;
        float settleFrom;
    }

    private static final Map<BlockPos, Flicker> FLICKER = new ConcurrentHashMap<>();

    public Light2Block(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(WATERLOGGED, false));
        ensureHooks();
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        boolean water = ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER;
        return getDefaultState().with(FACING, ctx.getSide()).with(WATERLOGGED, water);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction dir, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, dir, neighborState, world, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean moved) {
        super.onBlockAdded(state, world, pos, oldState, moved);
        if (world.isClient) spawnLightsIfNeeded(world, pos);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         net.minecraft.entity.LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) spawnLightsIfNeeded(world, pos);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient && (!newState.isOf(this))) removeLightsIfAny(pos);
    }

    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        super.onBroken(world, pos, state);
        if (world.isClient()) removeLightsIfAny(pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape s = DIR_SHAPES.get(state.get(FACING));
        return s != null ? s : VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape s = DIR_SHAPES.get(state.get(FACING));
        return s != null ? s : VoxelShapes.fullCube();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    private static void spawnLightsIfNeeded(World world, BlockPos pos) {
        if (!world.isClient) return;
        if (AREA_HANDLES.containsKey(pos) || POINT_HANDLES.containsKey(pos)) return;
        if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) return;

        Vec3d c = new Vec3d(pos.getX(), pos.getY(), pos.getZ()).add(BONE_LIGHT_OFFSET);

        AreaLightData al = new AreaLightData()
                .setBrightness(BASE_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setSize(AL_SIZE_X, AL_SIZE_Y)
                .setAngle(AL_ANGLE)
                .setDistance(AL_DIST);
        al.getPosition().set(c.x, c.y, c.z);
        al.getOrientation().set(ORIENTATION_DOWN);

        PointLightData pl = new PointLightData()
                .setBrightness(BASE_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(PL_RADIUS);
        pl.setPosition((float) c.x, (float) c.y, (float) c.z);

        LightRenderHandle<AreaLightData> ah = VeilRenderSystem.renderer().getLightRenderer().addLight(al);
        LightRenderHandle<PointLightData> ph = VeilRenderSystem.renderer().getLightRenderer().addLight(pl);

        AREA_HANDLES.put(pos, ah);
        POINT_HANDLES.put(pos, ph);
        AREA_DATA.put(pos, al);
        POINT_DATA.put(pos, pl);

        Flicker f = new Flicker();
        f.phase = Phase.IDLE;
        f.cooldown = rand(5f, 30f);
        FLICKER.put(pos, f);
    }

    private static void removeLightsIfAny(BlockPos pos) {
        LightRenderHandle<AreaLightData> ah = AREA_HANDLES.remove(pos);
        if (ah != null && ah.isValid()) ah.close();
        AREA_DATA.remove(pos);
        LightRenderHandle<PointLightData> ph = POINT_HANDLES.remove(pos);
        if (ph != null && ph.isValid()) ph.close();
        POINT_DATA.remove(pos);
        FLICKER.remove(pos);
    }

    private static void ensureHooks() {
        if (hooks) return;
        hooks = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            rescanPending = true; rendererWasReady = false;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (LightRenderHandle<AreaLightData> h : AREA_HANDLES.values()) if (h != null && h.isValid()) h.close();
            for (LightRenderHandle<PointLightData> h : POINT_HANDLES.values()) if (h != null && h.isValid()) h.close();
            AREA_HANDLES.clear(); POINT_HANDLES.clear(); AREA_DATA.clear(); POINT_DATA.clear(); FLICKER.clear();
            rescanPending = false; rendererWasReady = false;
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world == null || chunk == null) return;
            if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) return;
            ChunkPos cpos = chunk.getPos();
            int minY = world.getBottomY(), maxY = world.getTopY(), sx = cpos.getStartX(), sz = cpos.getStartZ();
            for (int y = minY; y < maxY; y++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        BlockPos bp = new BlockPos(sx + x, y, sz + z);
                        BlockState st = world.getBlockState(bp);
                        if (st.getBlock() instanceof Light2Block) spawnLightsIfNeeded(world, bp);
                    }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (world == null || chunk == null) return;
            ChunkPos cpos = chunk.getPos();
            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos p : AREA_HANDLES.keySet())
                if ((p.getX() >> 4) == cpos.x && (p.getZ() >> 4) == cpos.z) toRemove.add(p);
            for (BlockPos p : toRemove) removeLightsIfAny(p);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.isPaused()) return;
            boolean rendererReady = VeilRenderSystem.renderer() != null && VeilRenderSystem.renderer().getLightRenderer() != null;
            if (rendererReady && !rendererWasReady) rescanPending = true;
            rendererWasReady = rendererReady;

            if (rescanPending && client.world != null && client.player != null && rendererReady) {
                var w = client.world; var p = client.player.getBlockPos(); int r = 4;
                for (int cx = (p.getX() >> 4) - r; cx <= (p.getX() >> 4) + r; cx++)
                    for (int cz = (p.getZ() >> 4) - r; cz <= (p.getZ() >> 4) + r; cz++) {
                        if (w.getChunkManager().getChunk(cx, cz) == null) continue;
                        ChunkPos cp = new ChunkPos(cx, cz);
                        int minY = w.getBottomY(), maxY = w.getTopY(), sx = cp.getStartX(), sz = cp.getStartZ();
                        for (int y = minY; y < maxY; y++)
                            for (int x = 0; x < 16; x++)
                                for (int z = 0; z < 16; z++) {
                                    BlockPos bp = new BlockPos(sx + x, y, sz + z);
                                    BlockState st = w.getBlockState(bp);
                                    if (st.getBlock() instanceof Light2Block) spawnLightsIfNeeded(w, bp);
                                }
                    }
                rescanPending = false;
            }

            if (!rendererReady) return;

            float dt = client.getRenderTickCounter().getLastFrameDuration();
            for (BlockPos pos : AREA_DATA.keySet().toArray(new BlockPos[0])) {
                Flicker f = FLICKER.computeIfAbsent(pos, k -> { Flicker n = new Flicker(); n.phase = Phase.IDLE; n.cooldown = rand(5f, 30f); return n; });

                switch (f.phase) {
                    case IDLE -> {
                        f.cooldown -= dt;
                        applyBrightness(pos, BASE_BRIGHTNESS);
                        if (f.cooldown <= 0f) {
                            f.flashesLeft = 1 + RNG.nextInt(5);
                            f.offDur = rand(0.04f, 0.12f);
                            f.onDur  = rand(0.04f, 0.12f);
                            f.timer = 0f; f.phase = Phase.BURST_OFF;
                        }
                    }
                    case BURST_OFF -> {
                        f.timer += dt; applyBrightness(pos, 0f);
                        if (f.timer >= f.offDur) { f.timer = 0f; f.phase = Phase.BURST_ON; }
                    }
                    case BURST_ON -> {
                        f.timer += dt; applyBrightness(pos, BASE_BRIGHTNESS);
                        if (f.timer >= f.onDur) {
                            f.flashesLeft--;
                            if (f.flashesLeft <= 0) {
                                f.tailLeft = 1 + RNG.nextInt(3);
                                f.tailGap = rand(0.35f, 0.9f);
                                f.timer = 0f; f.phase = Phase.TAIL_WAIT;
                            } else {
                                f.offDur = rand(0.04f, 0.12f);
                                f.onDur  = rand(0.04f, 0.12f);
                                f.timer = 0f; f.phase = Phase.BURST_OFF;
                            }
                        }
                    }
                    case TAIL_WAIT -> {
                        f.timer += dt; applyBrightness(pos, BASE_BRIGHTNESS);
                        if (f.timer >= f.tailGap) {
                            f.timer = 0f;
                            f.offDur = rand(0.12f, 0.25f);
                            f.onDur  = rand(0.10f, 0.22f);
                            f.phase = Phase.TAIL_OFF;
                        }
                    }
                    case TAIL_OFF -> {
                        f.timer += dt; applyBrightness(pos, 0f);
                        if (f.timer >= f.offDur) { f.timer = 0f; f.phase = Phase.TAIL_ON; }
                    }
                    case TAIL_ON -> {
                        f.timer += dt; applyBrightness(pos, BASE_BRIGHTNESS);
                        if (f.timer >= f.onDur) {
                            f.tailLeft--;
                            if (f.tailLeft <= 0) {
                                f.settleTime = 0f; f.settleDur = rand(0.4f, 0.8f);
                                f.settleFrom = BASE_BRIGHTNESS; f.phase = Phase.SETTLE;
                            } else {
                                f.timer = 0f; f.tailGap = rand(0.35f, 0.9f); f.phase = Phase.TAIL_WAIT;
                            }
                        }
                    }
                    case SETTLE -> {
                        f.settleTime += dt;
                        float t = clamp01(f.settleTime / f.settleDur);
                        float b = lerp(f.settleFrom, BASE_BRIGHTNESS, smoothstep01(t));
                        applyBrightness(pos, b);
                        if (t >= 1f) { f.cooldown = rand(5f, 30f); f.phase = Phase.IDLE; }
                    }
                }
            }
        });
    }

    private static void applyBrightness(BlockPos pos, float b) {
        AreaLightData al = AREA_DATA.get(pos);
        if (al != null) al.setBrightness(b);
        PointLightData pl = POINT_DATA.get(pos);
        if (pl != null) pl.setBrightness(b);
        LightRenderHandle<AreaLightData> ah = AREA_HANDLES.get(pos);
        if (ah != null && ah.isValid()) ah.markDirty();
        LightRenderHandle<PointLightData> ph = POINT_HANDLES.get(pos);
        if (ph != null && ph.isValid()) ph.markDirty();
    }

    private static float rand(float a, float b) { return a + (b - a) * RNG.nextFloat(); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float smoothstep01(float x) { x = clamp01(x); return x * x * (3f - 2f * x); }
}
