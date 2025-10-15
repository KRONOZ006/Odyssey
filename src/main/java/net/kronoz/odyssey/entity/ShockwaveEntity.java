package net.kronoz.odyssey.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;

public class ShockwaveEntity extends Entity {
    private int duration = 40;
    private float maxRadius = 50f;
    private float band = 1.25f;
    private double yOffset = 0.05;
    private final HashSet<Integer> hit = new HashSet<>();

    public ShockwaveEntity(EntityType<? extends ShockwaveEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setup(float maxRadius, int duration, boolean overhead) {
        this.maxRadius = maxRadius;
        this.duration = Math.max(1, duration);
        this.band = Math.max(0.5f, maxRadius / 40f);
        this.yOffset = overhead ? 2.0 : 0.05;
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
        float inner = Math.max(0f, r - band);
        Box box = new Box(getX() - r - 1, getY() - 2, getZ() - r - 1, getX() + r + 1, getY() + 2, getZ() + r + 1);
        for (LivingEntity le : this.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive())) {
            if (hit.contains(le.getId())) continue;
            Vec3d d = le.getPos().subtract(getX(), le.getY(), getZ());
            double dist2D = Math.hypot(d.x, le.getZ() - this.getZ());
            if (dist2D >= inner && dist2D <= r + 0.75) {
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
        if (nbt.contains("band")) band = nbt.getFloat("band");
        if (nbt.contains("yoff")) yOffset = nbt.getDouble("yoff");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("dur", duration);
        nbt.putFloat("rad", maxRadius);
        nbt.putFloat("band", band);
        nbt.putDouble("yoff", yOffset);
    }

    @Override
    public boolean shouldRender(double distance) { return true; }

    public double getYOffset() { return yOffset; }
}
