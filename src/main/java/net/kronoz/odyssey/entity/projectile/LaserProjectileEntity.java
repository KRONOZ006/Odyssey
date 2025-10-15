package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class LaserProjectileEntity extends ProjectileEntity {
    private double damage = 4.0;
    private int lifetime = 60;
    private boolean ignoreShooter = true;

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    public void setDamage(double dmg) { this.damage = dmg; }
    public void setLifetime(int ticks) { this.lifetime = ticks; }


    @Override
    protected void initDataTracker(DataTracker.Builder builder) {

    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (this.age++ > lifetime) { discard(); return; }

        Vec3d pos = this.getPos();
        Vec3d vel = this.getVelocity();
        if (vel.lengthSquared() < 1e-6) { discard(); return; }

        Vec3d next = pos.add(vel);

        BlockHitResult hr = this.getWorld().raycast(new RaycastContext(
                pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (hr.getType() != HitResult.Type.MISS) {
            next = hr.getPos();
        }

        EntityHitResult ehr = raycastEntities(pos, next);
        if (ehr != null) {
            onEntityHit(ehr);
            return;
        }

        if (hr.getType() == HitResult.Type.BLOCK) {
            onBlockHit(hr);
            return;
        }

        this.setPos(next.x, next.y, next.z);
    }

    private EntityHitResult raycastEntities(Vec3d start, Vec3d end) {
        EntityHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : this.getWorld().getOtherEntities(this, getBoundingBox().stretch(getVelocity()).expand(1.0))) {
            if (!e.isAlive()) continue;
            if (ignoreShooter && e == this.getOwner()) continue;

            Box bb = e.getBoundingBox().expand(0.1);
            Vec3d hit = bb.raycast(start, end).orElse(null);
            if (hit != null) {
                double d = start.squaredDistanceTo(hit);
                if (d < bestDist) { bestDist = d; best = new EntityHitResult(e, hit); }
            }
        }
        return best;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        if (target.isAlive()) {
            DamageSource src = this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null);
            target.damage(src, (float)damage);
        }
        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("dmg")) damage = nbt.getDouble("dmg");
        if (nbt.contains("life")) lifetime = nbt.getInt("life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("dmg", damage);
        nbt.putInt("life", lifetime);
    }

    @Override
    public boolean shouldRender(double distance) { return true; }
}
