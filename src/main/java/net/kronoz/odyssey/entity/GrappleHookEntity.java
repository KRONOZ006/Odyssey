package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.systems.grapple.GrappleServerLogic;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class GrappleHookEntity extends ProjectileEntity {
    public Entity ownerPlayer;
    public boolean latched = false;
    public Vec3d anchor = Vec3d.ZERO;
    public int latchedEntityId = -1;
    public Vec3d entityAnchorOffset = Vec3d.ZERO;
    public double ropeLength = 6.0;

    private static final double DAMPING = 0.98;
    private static final double MAX_PLAYER_SPEED = 1.7;
    private static final double MAX_PULL_PER_TICK = 0.45;

    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world) { super(type, world); }
    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world, Entity ownerPlayer) {
        this(type, world);
        this.ownerPlayer = ownerPlayer;
    }


    @Override
    protected void initDataTracker(DataTracker.Builder builder) {

    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        if (ownerPlayer == null || !ownerPlayer.isAlive()) {
            this.discard();
            return;
        }

        if (!latched) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit != null && hit.getType() != HitResult.Type.MISS) onCollision(hit);

            if (!latched) {
                this.setPosition(this.getPos().add(this.getVelocity()));
                this.setVelocity(this.getVelocity().multiply(0.99));
                if (this.age > 200) this.discard();
            }
        } else {
            applyRopePhysics();
            if (!ownerPlayer.isAlive() || ownerPlayer.distanceTo(this) > 64) {
                if (ownerPlayer instanceof ServerPlayerEntity sp) GrappleServerLogic.detach(sp);
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;

        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = getWorld().getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(getWorld(), pos).isEmpty()) return;

            this.latched = true;
            this.anchor = Vec3d.ofCenter(pos).add(Vec3d.of(bhr.getSide().getVector()).multiply(0.35));
            this.setVelocity(Vec3d.ZERO);
            this.noClip = true;
            this.ropeLength = ownerPlayer.getPos().distanceTo(anchor);
            this.latchedEntityId = -1;
            this.entityAnchorOffset = Vec3d.ZERO;
            GrappleServerLogic.syncToClient(ownerPlayer, this);
        } else if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e == ownerPlayer) return;
            this.latched = true;
            this.latchedEntityId = e.getId();
            Vec3d impact = ehr.getPos();
            this.entityAnchorOffset = impact.subtract(e.getPos());
            this.anchor = impact;
            this.setVelocity(Vec3d.ZERO);
            this.noClip = true;
            this.ropeLength = ownerPlayer.getPos().distanceTo(anchor);
            GrappleServerLogic.syncToClient(ownerPlayer, this);
        }
    }

    @Override
    protected boolean canHit(Entity entity) { return entity.isAlive() && entity != ownerPlayer; }

    private void applyRopePhysics() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (ownerPlayer == null) return;

        Vec3d anchorNow = this.anchor;
        if (latchedEntityId != -1) {
            Entity ent = sw.getEntityById(latchedEntityId);
            if (ent == null || !ent.isAlive()) {
                if (ownerPlayer instanceof ServerPlayerEntity sp) GrappleServerLogic.detach(sp);
                return;
            }
            anchorNow = ent.getPos().add(entityAnchorOffset);
            this.anchor = anchorNow;
        }

        Vec3d p = ownerPlayer.getPos();
        Vec3d d = p.subtract(anchorNow);
        double dist = d.length();

        if (dist > ropeLength) {
            Vec3d dir = d.normalize();
            double excess = dist - ropeLength;
            Vec3d add = dir.multiply(-Math.min(excess, MAX_PULL_PER_TICK));
            ownerPlayer.addVelocity(add.x, add.y, add.z);
            ownerPlayer.velocityModified = true;
        }

        Vec3d v = ownerPlayer.getVelocity().multiply(DAMPING);
        double sp = v.length();
        if (sp > MAX_PLAYER_SPEED) v = v.normalize().multiply(MAX_PLAYER_SPEED);
        ownerPlayer.setVelocity(v);
        ownerPlayer.fallDistance = 0f;

        this.setPos(anchorNow.x, anchorNow.y, anchorNow.z);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.latched = nbt.getBoolean("latched");
        this.anchor = new Vec3d(nbt.getDouble("ax"), nbt.getDouble("ay"), nbt.getDouble("az"));
        this.ropeLength = nbt.getDouble("len");
        this.latchedEntityId = nbt.getInt("aeid");
        this.entityAnchorOffset = new Vec3d(nbt.getDouble("ox"), nbt.getDouble("oy"), nbt.getDouble("oz"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("latched", latched);
        nbt.putDouble("ax", anchor.x);
        nbt.putDouble("ay", anchor.y);
        nbt.putDouble("az", anchor.z);
        nbt.putDouble("len", ropeLength);
        nbt.putInt("aeid", latchedEntityId);
        nbt.putDouble("ox", entityAnchorOffset.x);
        nbt.putDouble("oy", entityAnchorOffset.y);
        nbt.putDouble("oz", entityAnchorOffset.z);
    }
}
