package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.entity.projectile.ProjectileUtil;

public class LaserProjectileEntity extends ProjectileEntity {
    private int life;
    private static final int MAX_LIFE = 200;
    private static final double DRAG = 0.998;
    private static final double SPEED_MIN = 0.01;
    private static final float BASE_DAMAGE = 1.5f;
    private static final float SPEED_DAMAGE = 8.5f;

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world, LivingEntity owner, Vec3d from, Vec3d dir, double speed) {
        this(type, world);
        this.setOwner(owner);
        this.refreshPositionAndAngles(from.x, from.y, from.z, owner.getYaw(), owner.getPitch());
        this.setVelocity(dir.normalize().multiply(speed));
    }

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world, LivingEntity owner) {
        this(type, world);
        this.setOwner(owner);
        Vec3d eye = owner.getEyePos();
        Vec3d dir = owner.getRotationVec(1.0f);
        this.refreshPositionAndAngles(eye.x, eye.y - 0.1, eye.z, owner.getYaw(), owner.getPitch());
        this.setVelocity(dir.normalize().multiply(2.6));
    }

    @Override
    public void tick() {
        super.tick();
        if (++life > MAX_LIFE) { discard(); return; }
        Vec3d v0 = getVelocity();
        if (v0.lengthSquared() < SPEED_MIN * SPEED_MIN) { discard(); return; }

        Vec3d start = getPos();
        Vec3d end = start.add(v0);
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit != null && hit.getPos() != null) end = hit.getPos();

        setPos(end.x, end.y, end.z);

        if (hit != null) { onCollision(hit); return; }
        setVelocity(v0.multiply(DRAG));
    }

    @Override
    protected boolean canHit(Entity e) {
        Entity o = getOwner();
        if (e == o) return false;
        return super.canHit(e);
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (getWorld().isClient) return;
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) hit;
            float speed = (float)this.getVelocity().length();
            float dmg = BASE_DAMAGE + SPEED_DAMAGE * speed;
            ehr.getEntity().damage(getDamageSources().magic(), dmg);
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            getWorld().emitGameEvent(this, GameEvent.PROJECTILE_LAND, ((BlockHitResult)hit).getBlockPos());
        }
        discard();
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { nbt.putInt("life", life); }
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { life = nbt.getInt("life"); }
    @Override
    public boolean hasNoGravity() { return true; }
}
