package net.kronoz.odyssey.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class GroundDecalEntity extends Entity {
    public static final TrackedData<Integer> DURATION = DataTracker.registerData(GroundDecalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Float>   RADIUS   = DataTracker.registerData(GroundDecalEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public GroundDecalEntity(EntityType<? extends GroundDecalEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setup(double radius, int durationTicks) {
        this.getDataTracker().set(RADIUS, (float) radius);
        this.getDataTracker().set(DURATION, durationTicks);
    }

    public float getRadius()   { return this.getDataTracker().get(RADIUS); }
    public int   getDuration() { return this.getDataTracker().get(DURATION); }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(RADIUS, 0.9f);
        builder.add(DURATION, 20);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && this.age >= getDuration()) discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("radius"))   this.getDataTracker().set(RADIUS, nbt.getFloat("radius"));
        if (nbt.contains("duration")) this.getDataTracker().set(DURATION, nbt.getInt("duration"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("radius", this.getRadius());
        nbt.putInt("duration", this.getDuration());
    }

    @Override
    public boolean shouldRender(double distance) { return true; }
}
