package net.kronoz.odyssey.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
        this.setNoGravity(true);
    }

    public void setLifetime(int life) { this.lifetime = life; }
    public void setDamage(double dmg) { this.damage = dmg; }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();

        // détection collision côté serveur uniquement
        if (!this.getWorld().isClient) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHitProjectileTarget);
            if (hit.getType() != HitResult.Type.MISS) {
                this.onCollision(hit);
            }
            if (this.isRemoved()) return;
        }

        // mouvement visible client + serveur
        this.move(MovementType.SELF, this.getVelocity());
        this.checkBlockCollision();

        if (!this.getWorld().isClient && this.age > lifetime) {
            this.discard();
        }
    }

    private boolean canHitProjectileTarget(Entity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        Entity owner = this.getOwner();
        if (owner != null && e.getId() == owner.getId()) return false; // ignore tireur
        return true;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (this.getWorld().isClient) return;
        if (hit.getEntity() instanceof LivingEntity l) {
            l.damage(this.getDamageSources().indirectMagic(this, this.getOwner()), (float) damage);
        }
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (!this.getWorld().isClient) this.discard();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Life")) this.lifetime = nbt.getInt("Life");
        if (nbt.contains("Dmg"))  this.damage   = nbt.getDouble("Dmg");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", lifetime);
        nbt.putDouble("Dmg", damage);
    }
}
