package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.systems.grapple.SimpleGrappleServer;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class GrappleHookEntity extends ProjectileEntity {
    public Entity ownerPlayer;
    public boolean latched = false;
    public Vec3d anchor = Vec3d.ZERO;   // point d’ancrage monde
    public double ropeLength = 6.0;     // fixé à l’impact

    // swing “simple & nerveux”
    private static final double EPS_LEN      = 0.01;
    private static final double BAUMGARTE    = 0.35;
    private static final double AIR_DRAG     = 0.006;
    private static final double TANGENT_DAMP = 0.008;
    private static final double SPEED_CLAMP  = 4.2;
    private static final Vec3d  GRAVITY      = new Vec3d(0, -0.08, 0);
    private static final double MAX_RANGE    = 64.0;

    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world) { super(type, world); }
    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world, Entity owner) {
        this(type, world);
        this.ownerPlayer = owner;
    }

    // 1.21.1 signature
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        if (ownerPlayer == null || !ownerPlayer.isAlive()) { discard(); return; }

        if (!latched) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit != null && hit.getType() != HitResult.Type.MISS) onCollision(hit);

            if (!latched) {
                setPosition(getX()+getVelocity().x, getY()+getVelocity().y, getZ()+getVelocity().z);
                setVelocity(getVelocity().multiply(0.99));
                if (age > 200) discard();
            }
        } else {
            applySwingPhysics();
            if (!ownerPlayer.isAlive() || ownerPlayer.distanceTo(this) > MAX_RANGE) {
                if (ownerPlayer instanceof ServerPlayerEntity sp) SimpleGrappleServer.detach(sp);
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (getWorld().isClient) return;

        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState st = getWorld().getBlockState(pos);
            if (st.isAir() || st.getCollisionShape(getWorld(), pos).isEmpty()) return;

            Direction face = bhr.getSide();
            latched = true;
            anchor = Vec3d.ofCenter(pos).add(Vec3d.of(face.getVector()).multiply(0.35));
            setVelocity(Vec3d.ZERO);
            noClip = true;
            ropeLength = ownerPlayer.getPos().distanceTo(anchor);
        } else if (hit instanceof EntityHitResult) {
            // version simple: pas d’attache entité (on ignore)
        }
    }

    @Override
    protected boolean canHit(Entity e) { return e.isAlive() && e != ownerPlayer; }

    private void applySwingPhysics() {
        if (!(getWorld() instanceof ServerWorld)) return;

        Vec3d p = ownerPlayer.getPos();
        Vec3d v = ownerPlayer.getVelocity();

        Vec3d diff = p.subtract(anchor);
        double dist = diff.length();
        if (dist < 1e-9) { setPos(anchor.x, anchor.y, anchor.z); return; }
        Vec3d dir = diff.multiply(1.0 / dist); // ancre -> joueur

        // projection gravité sur tangente (pendule)
        double vr = v.dotProduct(dir);
        Vec3d vt = v.subtract(dir.multiply(vr));
        Vec3d gTan = GRAVITY.subtract(dir.multiply(GRAVITY.dotProduct(dir)));
        vt = vt.add(gTan);

        // contrainte dure de longueur
        double C = dist - ropeLength;
        if (C > EPS_LEN) {
            Vec3d corr = dir.multiply(-BAUMGARTE * C);
            ownerPlayer.setPosition(p.x + corr.x, p.y + corr.y, p.z + corr.z);
            p = ownerPlayer.getPos();
            vr = Math.min(0.0, vr);
        } else if (dist > ropeLength - EPS_LEN) {
            vr = Math.min(0.0, vr);
            Vec3d exact = anchor.add(dir.multiply(ropeLength));
            ownerPlayer.setPosition(exact.x, exact.y, exact.z);
            p = exact;
        }

        // léger damping
        vt = vt.multiply(1.0 - TANGENT_DAMP);
        vr = vr * (1.0 - AIR_DRAG);

        Vec3d newV = vt.add(dir.multiply(vr));
        double s = newV.length();
        if (s > SPEED_CLAMP) newV = newV.multiply(SPEED_CLAMP / s);

        ownerPlayer.setVelocity(newV);
        ownerPlayer.velocityModified = true;
        ownerPlayer.fallDistance = 0.0f;

        setPos(anchor.x, anchor.y, anchor.z);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound n) {
        latched = n.getBoolean("latched");
        anchor = new Vec3d(n.getDouble("ax"), n.getDouble("ay"), n.getDouble("az"));
        ropeLength = n.getDouble("len");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound n) {
        n.putBoolean("latched", latched);
        n.putDouble("ax", anchor.x);
        n.putDouble("ay", anchor.y);
        n.putDouble("az", anchor.z);
        n.putDouble("len", ropeLength);
    }
}
