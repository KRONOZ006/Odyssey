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
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerminalBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    private static final Map<Direction, VoxelShape> DIR_SHAPES =
            CollisionShapeHelper.loadDirectionalCollisionFromModelJson(Odyssey.MODID, "terminal");

    private static final double AL_SIZE_X = 0.30;
    private static final double AL_SIZE_Y = 0.22;
    private static final float  AL_ANGLE  = (float) Math.toRadians(50.0);
    private static final float  AL_DIST   = 20.0f;
    private static final float  PL_RADIUS = 5.0f;
    private static final float CLR_R = 0.48f, CLR_G = 0.85f, CLR_B = 1.00f;
    private static final float BASE_BRIGHTNESS = 1.0f;

    private static final Quaternionf ORIENT_NORTH = new Quaternionf().rotateY(0f);
    private static final Quaternionf ORIENT_EAST  = new Quaternionf().rotateY((float)Math.toRadians(90));
    private static final Quaternionf ORIENT_SOUTH = new Quaternionf().rotateY((float)Math.toRadians(180));
    private static final Quaternionf ORIENT_WEST  = new Quaternionf().rotateY((float)Math.toRadians(270));

    private static final Map<BlockPos, LightRenderHandle<AreaLightData>>  AREA_HANDLES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, LightRenderHandle<PointLightData>> POINT_HANDLES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, AreaLightData>  AREA_DATA  = new ConcurrentHashMap<>();
    private static final Map<BlockPos, PointLightData> POINT_DATA = new ConcurrentHashMap<>();

    private static final float X_CENTER = 0.5f;
    private static final float Y_CENTER = 0.62f;
    private static final float FACE_EPS = 0.001f;

    private static final java.util.Random RNG = new java.util.Random();
    private enum Phase { IDLE, BURST_OFF, BURST_ON, TAIL_WAIT, TAIL_OFF, TAIL_ON, SETTLE }
    private static final class Flicker {
        Phase phase = Phase.IDLE;
        float cooldown;
        int flashesLeft;
        float timer;
        float offDur;
        float onDur;
        int tailLeft;
        float tailGap;
        float settleTime;
        float settleDur;
        float settleFrom;
    }
    private static final Map<BlockPos, Flicker> FLICKER = new ConcurrentHashMap<>();

    private static boolean hooks = false;
    private static boolean rescanPending = false;
    private static boolean rendererWasReady = false;

    public TerminalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
        ensureHooks();
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
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
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean moved) {
        super.onBlockAdded(state, world, pos, oldState, moved);
        if (world.isClient) spawnLightsIfNeeded(world, pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) spawnLightsIfNeeded(world, pos, state);
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

    private static void spawnLightsIfNeeded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) return;
        if (AREA_HANDLES.containsKey(pos) || POINT_HANDLES.containsKey(pos)) return;
        if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) return;

        Direction f = state.get(FACING);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + Y_CENTER;
        double z = pos.getZ() + 0.5;
        Quaternionf orient;
        switch (f) {
            case NORTH -> { z = pos.getZ() + FACE_EPS; orient = ORIENT_NORTH; }
            case SOUTH -> { z = pos.getZ() + 1 - FACE_EPS; orient = ORIENT_SOUTH; }
            case EAST  -> { x = pos.getX() + 1 - FACE_EPS; orient = ORIENT_EAST; }
            case WEST  -> { x = pos.getX() + FACE_EPS; orient = ORIENT_WEST; }
            default    -> { orient = ORIENT_NORTH; }
        }

        AreaLightData al = new AreaLightData()
                .setBrightness(BASE_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setSize(AL_SIZE_X, AL_SIZE_Y)
                .setAngle(AL_ANGLE)
                .setDistance(AL_DIST);
        al.getPosition().set(x, y, z);
        al.getOrientation().set(orient);

        PointLightData pl = new PointLightData()
                .setBrightness(BASE_BRIGHTNESS)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(PL_RADIUS);
        pl.setPosition((float)x, (float)y, (float)z);

        var lr = VeilRenderSystem.renderer().getLightRenderer();
        LightRenderHandle<AreaLightData> ah = lr.addLight(al);
        LightRenderHandle<PointLightData> ph = lr.addLight(pl);

        AREA_HANDLES.put(pos, ah);
        POINT_HANDLES.put(pos, ph);
        AREA_DATA.put(pos, al);
        POINT_DATA.put(pos, pl);

        Flicker fl = new Flicker();
        fl.phase = Phase.IDLE;
        fl.cooldown = rand(5f, 30f);
        FLICKER.put(pos, fl);
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
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        if (world.isClient) removeLightsIfAny(pos);
        return state;
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state,
                           @Nullable net.minecraft.block.entity.BlockEntity be, ItemStack tool) {
        super.afterBreak(world, player, pos, state, be, tool);
        if (world.isClient) removeLightsIfAny(pos);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient) return;
        if (!newState.isOf(this)) removeLightsIfAny(pos);
    }

    @Override
    public void onDestroyedByExplosion(World world, BlockPos pos, net.minecraft.world.explosion.Explosion explosion) {
        super.onDestroyedByExplosion(world, pos, explosion);
        if (world.isClient) removeLightsIfAny(pos);
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
                        if (st.getBlock() instanceof TerminalBlock) spawnLightsIfNeeded(world, bp, st);
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
                                    if (st.getBlock() instanceof TerminalBlock) spawnLightsIfNeeded(w, bp, st);
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
