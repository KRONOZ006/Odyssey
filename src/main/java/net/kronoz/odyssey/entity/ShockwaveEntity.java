package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.entity.apostasy.ApostasyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;

public class ShockwaveEntity extends Entity {
    private int duration = 40;
    private float maxRadius = 50f;
    private double planeY = 0.05;
    private final HashSet<Integer> hit = new HashSet<>();
    private int ownerId = -1;

    public ShockwaveEntity(EntityType<? extends ShockwaveEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setup(float maxRadius, int duration, double planeY, int ownerId) {
        this.maxRadius = maxRadius;
        this.duration = Math.max(1, duration);
        this.planeY = planeY;
        this.ownerId = ownerId;
    }

    public float getProgress(float tickDelta) {
        return Math.min(1f, (this.age + tickDelta) / Math.max(1f, this.duration));
    }

    public float getCurrentRadius(float tickDelta) {
        return maxRadius * getProgress(tickDelta);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        float r = getCurrentRadius(0f);
        float inner = r * 0.9f;
        Box box = new Box(getX() - r - 1, planeY - 2, getZ() - r - 1, getX() + r + 1, planeY + 2, getZ() + r + 1);

        for (LivingEntity le : this.getWorld().getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (hit.contains(le.getId())) continue;
            if (le instanceof ApostasyEntity) continue;
            if (le.getId() == ownerId) continue;

            double dy = le.getY() - planeY;
            if (dy > 0.9) continue;

            Vec3d v = le.getPos().subtract(getX(), le.getY(), getZ());
            double d2 = Math.hypot(v.x, le.getZ() - this.getZ());
            if (d2 >= inner && d2 <= r + 0.75) {
                float lvl = (le instanceof PlayerEntity pe) ? pe.experienceLevel : nearestPlayerLevel(le);
                float dmg = 4.0f + 0.18f * lvl;
                le.damage(getWorld().getDamageSources().generic(), dmg);
                Vec3d n = new Vec3d(le.getX() - this.getX(), 0, le.getZ() - this.getZ());
                double L = Math.max(0.001, Math.hypot(n.x, n.z));
                n = n.multiply(1.0 / L);
                double h = 1.15 + Math.min(0.85, lvl * 0.02);
                le.addVelocity(n.x * h, 0.45 + Math.min(0.35, lvl * 0.01), n.z * h);
                le.velocityDirty = true;
                hit.add(le.getId());
            }
        }

        if (this.age++ >= this.duration) this.discard();
    }

    private float nearestPlayerLevel(LivingEntity around) {
        PlayerEntity p = this.getWorld().getClosestPlayer(around, 32.0);
        return p != null ? p.experienceLevel : 0f;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("dur")) duration = nbt.getInt("dur");
        if (nbt.contains("rad")) maxRadius = nbt.getFloat("rad");
        if (nbt.contains("py")) planeY = nbt.getDouble("py");
        if (nbt.contains("oid")) ownerId = nbt.getInt("oid");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("dur", duration);
        nbt.putFloat("rad", maxRadius);
        nbt.putDouble("py", planeY);
        nbt.putInt("oid", ownerId);
    }

    @Override
    public boolean shouldRender(double distance) { return true; }

    public double getPlaneY() { return planeY; }
}
