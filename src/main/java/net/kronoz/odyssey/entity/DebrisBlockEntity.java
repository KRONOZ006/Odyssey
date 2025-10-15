package net.kronoz.odyssey.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class DebrisBlockEntity extends Entity {
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(DebrisBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BLOCK_ID = DataTracker.registerData(DebrisBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private BlockState state = Blocks.BARRIER.getDefaultState();
    private int lifetime = 60;
    private float rollX, rollY, rollZ;
    private float avx, avy, avz;
    private boolean onGroundDamping = true;

    public DebrisBlockEntity(EntityType<? extends DebrisBlockEntity> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(false);
        this.intersectionChecked = true;
    }

    public void init(BlockState st, int life) {
        this.state = st;
        this.lifetime = life;
        this.getDataTracker().set(LIFE, life);
        this.getDataTracker().set(BLOCK_ID, net.minecraft.block.Block.getRawIdFromState(st));
    }

    public void setAngularVelocity(float xDegPerTick, float yDegPerTick, float zDegPerTick) {
        this.avx = xDegPerTick;
        this.avy = yDegPerTick;
        this.avz = zDegPerTick;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(LIFE, 60);
        builder.add(BLOCK_ID, net.minecraft.block.Block.getRawIdFromState(this.state));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        int life = this.getDataTracker().get(LIFE);
        life--;
        if (life <= 0) { this.discard(); return; }
        this.getDataTracker().set(LIFE, life);

        // gravity + move with collisions
        var vel = this.getVelocity();
        vel = vel.add(0, -0.04, 0);
        double damp = this.isOnGround() ? 0.65 : 0.98;
        vel = vel.multiply(damp, 0.98, damp);
        this.setVelocity(vel);
        this.move(MovementType.SELF, this.getVelocity());

        // bounce a little on ground
        if (this.horizontalCollision) this.setVelocity(-vel.x * 0.4, vel.y, -vel.z * 0.4);
        if (this.verticalCollision && vel.y < 0) this.setVelocity(vel.x, -vel.y * 0.4, vel.z);

        // spin
        rollX += avx;
        rollY += avy;
        rollZ += avz;
    }

    public BlockState getBlockStateRender() {
        int id = this.getDataTracker().get(BLOCK_ID);
        return net.minecraft.block.Block.getStateFromRawId(id);
    }

    public float getRollX() { return rollX; }
    public float getRollY() { return rollY; }
    public float getRollZ() { return rollZ; }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("life")) this.lifetime = nbt.getInt("life");
        if (nbt.contains("raw")) {
            int raw = nbt.getInt("raw");
            this.state = net.minecraft.block.Block.getStateFromRawId(raw);
        }
        this.getDataTracker().set(LIFE, this.lifetime);
        this.getDataTracker().set(BLOCK_ID, net.minecraft.block.Block.getRawIdFromState(this.state));
        this.rollX = nbt.getFloat("rx");
        this.rollY = nbt.getFloat("ry");
        this.rollZ = nbt.getFloat("rz");
        this.avx = nbt.getFloat("avx");
        this.avy = nbt.getFloat("avy");
        this.avz = nbt.getFloat("avz");
    }
    private void pullDownColumnBlocks(ServerWorld sw, BlockPos pos, int scanUp, int maxPull) {
        int pulled = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 1; dy <= scanUp && pulled < maxPull; dy++) {
            m.set(pos.getX(), pos.getY() + dy, pos.getZ());
            var st = sw.getBlockState(m);
            if (st.isAir()) continue;
            var blk = st.getBlock();
            if (blk == net.minecraft.block.Blocks.BEDROCK || blk == net.minecraft.block.Blocks.BARRIER) continue;
            if (st.getCollisionShape(sw, m).isEmpty()) continue;

            sw.setBlockState(m, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            var f = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(sw, m, st);
            if (f != null) {
                f.dropItem = false;
                try { f.setDestroyedOnLanding(); } catch (Throwable ignored) {}
                double spread = 0.35;
                f.setVelocity(
                        (this.random.nextDouble() - 0.5) * spread,
                        -0.2 - this.random.nextDouble() * 0.2,
                        (this.random.nextDouble() - 0.5) * spread
                );
            }
            pulled++;
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", this.getDataTracker().get(LIFE));
        nbt.putInt("raw", net.minecraft.block.Block.getRawIdFromState(getBlockStateRender()));
        nbt.putFloat("rx", rollX);
        nbt.putFloat("ry", rollY);
        nbt.putFloat("rz", rollZ);
        nbt.putFloat("avx", avx);
        nbt.putFloat("avy", avy);
        nbt.putFloat("avz", avz);
    }
    @Override
    public boolean shouldRender(double distance) { return true; }

    @Override
    public Box getVisibilityBoundingBox() {
        return this.getBoundingBox().expand(1.0);
    }
}
