package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class LaserProjectileEntity extends ProjectileEntity {
    private int lifetime = 120;
    private double damage = 0.5;

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    public void setLifetime(int ticks) { this.lifetime = ticks; }
    public void setDamage(double dmg) { this.damage = dmg; }

    @Override
    public void tick() {
        super.tick();
        this.move(MovementType.SELF, this.getVelocity());
        if (this.age > lifetime) this.discard();
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult br = (BlockHitResult) hit;
            if (!this.getWorld().isClient) this.discard();
        }
        super.onCollision(hit);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (!this.getWorld().isClient && hit.getEntity() instanceof LivingEntity le) {
            le.damage(this.getDamageSources().indirectMagic(this, this.getOwner()), (float) damage);
            this.discard();
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        lifetime = nbt.getInt("Life");
        damage = nbt.getDouble("Dmg");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", lifetime);
        nbt.putDouble("Dmg", damage);
    }
}
