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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlarmBlock extends Block implements Waterloggable {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    private static final Map<Direction, VoxelShape> DIR_SHAPES =
            CollisionShapeHelper.loadDirectionalCollisionFromModelJson(Odyssey.MODID, "alarm");

    private static final double AL_SIZE_X = 0.20;
    private static final double AL_SIZE_Y = 0.20;
    private static final float  AL_ANGLE  = (float) Math.toRadians(40.0);
    private static final float  AL_DIST   = 25.0f;
    private static final float  PL_RADIUS = 5.0f;
    private static final float CLR_R = 1f, CLR_G = 0f, CLR_B = 0f;
    private static final Quaternionf ORIENTATION_DOWN = new Quaternionf().rotateX((float)(-Math.PI/2.0));

    private static final Map<BlockPos, LightRenderHandle<AreaLightData>>  AREA_HANDLES = new HashMap<>();
    private static final Map<BlockPos, LightRenderHandle<PointLightData>> POINT_HANDLES = new HashMap<>();
    private static final Map<BlockPos, AreaLightData>  AREA_DATA  = new HashMap<>();
    private static final Map<BlockPos, PointLightData> POINT_DATA = new HashMap<>();

    private static final Vec3d BONE_LIGHT_OFFSET = new Vec3d(0.5, 0.8, 0.5);
    private static boolean hooks = false;
    private static boolean rescanPending = false;
    private static boolean rendererWasReady = false;

    public AlarmBlock(Settings settings) {
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
                .setBrightness(5.0f)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setSize(AL_SIZE_X, AL_SIZE_Y)
                .setAngle(AL_ANGLE)
                .setDistance(AL_DIST);
        al.getPosition().set(c.x, c.y, c.z);
        al.getOrientation().set(ORIENTATION_DOWN);

        PointLightData pl = new PointLightData()
                .setBrightness(5.0f)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(PL_RADIUS);
        pl.setPosition((float) c.x, (float) c.y, (float) c.z);

        LightRenderHandle<AreaLightData> ah = VeilRenderSystem.renderer().getLightRenderer().addLight(al);
        LightRenderHandle<PointLightData> ph = VeilRenderSystem.renderer().getLightRenderer().addLight(pl);

        AREA_HANDLES.put(pos, ah);
        POINT_HANDLES.put(pos, ph);
        AREA_DATA.put(pos, al);
        POINT_DATA.put(pos, pl);
    }

    private static void removeLightsIfAny(BlockPos pos) {
        LightRenderHandle<AreaLightData> ah = AREA_HANDLES.remove(pos);
        if (ah != null && ah.isValid()) ah.close();
        AREA_DATA.remove(pos);

        LightRenderHandle<PointLightData> ph = POINT_HANDLES.remove(pos);
        if (ph != null && ph.isValid()) ph.close();
        POINT_DATA.remove(pos);
    }

    private static void ensureHooks() {
        if (hooks) return;
        hooks = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            rescanPending = true;
            rendererWasReady = false;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (LightRenderHandle<AreaLightData> h : AREA_HANDLES.values()) if (h != null && h.isValid()) h.close();
            for (LightRenderHandle<PointLightData> h : POINT_HANDLES.values()) if (h != null && h.isValid()) h.close();
            AREA_HANDLES.clear(); POINT_HANDLES.clear(); AREA_DATA.clear(); POINT_DATA.clear();
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
                        if (st.getBlock() instanceof AlarmBlock) spawnLightsIfNeeded(world, bp);
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
                                    if (st.getBlock() instanceof AlarmBlock) spawnLightsIfNeeded(w, bp);
                                }
                    }
                rescanPending = false;
            }
        });
    }
}
