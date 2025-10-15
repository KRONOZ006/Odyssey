package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class LaserProjectileEntity extends ProjectileEntity {
    private double damage = 4.0;
    private int lifetime = 60;

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    public void setDamage(double dmg) { this.damage = dmg; }
    public void setLifetime(int ticks) { this.lifetime = Math.max(1, ticks); }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        this.setVelocity(this.getVelocity()); // keep straight line
        this.move(MovementType.SELF, this.getVelocity());

        HitResult hr = ProjectileUtil.getCollision(this, this::canHit);
        if (hr != null && hr.getType() != HitResult.Type.MISS) onCollision(hr);

        if (this.age++ >= this.lifetime) discard();
    }

    public boolean canHit(Entity e) {
        if (!e.isAlive()) return false;
        Entity owner = getOwner();
        if (owner != null && e.getId() == owner.getId()) return false;
        return e.isAttackable();
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity e = hit.getEntity();
        if (this.getWorld() instanceof ServerWorld sw) {
            DamageSource src = getWorld().getDamageSources().mobProjectile(this, (LivingEntity)(getOwner() instanceof LivingEntity ? getOwner() : null));
            e.damage(src, (float)this.damage);
        }
        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        discard();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (hitResult.getType() == HitResult.Type.ENTITY) onEntityHit((EntityHitResult)hitResult);
        else if (hitResult.getType() == HitResult.Type.BLOCK) onBlockHit((BlockHitResult)hitResult);
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
}
