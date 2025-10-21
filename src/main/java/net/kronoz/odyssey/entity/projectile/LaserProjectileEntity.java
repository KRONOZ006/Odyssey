package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LaserProjectileEntity extends ShulkerBulletEntity {
    private float odysseyDamage = 1.0f; // half-heart

    public LaserProjectileEntity(EntityType<? extends ShulkerBulletEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public void setOdysseyDamage(float dmg) { this.odysseyDamage = dmg; }

    public void initHoming(LivingEntity owner, Entity target, Vec3d spawnPos, double initialBoost) {
        this.setOwner(owner);
        this.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        if (initialBoost > 0) {
            Vec3d dir = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0).subtract(spawnPos).normalize();
            this.setVelocity(dir.multiply(initialBoost));
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        if (target.isAlive()) {
            target.damage(this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), odysseyDamage);
        }
        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("odmg")) odysseyDamage = nbt.getFloat("odmg");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("odmg", odysseyDamage);
    }
}
