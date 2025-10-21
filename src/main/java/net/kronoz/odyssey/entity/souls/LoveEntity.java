package net.kronoz.odyssey.entity.souls;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.kronoz.odyssey.init.ModParticles;
import net.kronoz.odyssey.systems.cinematics.api.Easing;
import net.kronoz.odyssey.systems.cinematics.api.RotationTweener;
import net.minecraft.block.BlockState;
import net.minecraft.entity.AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;




public class LoveEntity extends AnimalEntity implements GeoEntity {
    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private static final float BODY_LIGHT_BRIGHTNESS = 1.5f;
    private static final float BODY_LIGHT_RADIUS     = 13f;
    private static final float BODY_R = 0.70f, BODY_G = 0.70f, BODY_B = 0.10f;

    private PointLightData bodyLight;
    private LightRenderHandle<PointLightData> bodyLightHandle;

    private int spawnAge = -1;
    private int maxLifeTicks = 0;
    private UUID ownerUuid = null;

    private double hitboxSize = 1.0;
    private final double growthRate = 0.12;
    private final double minSize = 0.6;
    private final double maxSize = 2.5;





    float minRandom = 1.3f;
    float maxRandom = 2.0f;

    Random random = new Random();

    float pitch = minRandom + random.nextFloat() * (maxRandom - minRandom);

// distance fom owner




    public LoveEntity(EntityType<? extends AnimalEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;






    }





    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 15.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6F)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2F);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.ownerUuid = owner != null ? owner.getUuid() : null;
    }



    @Nullable
    public Entity getOwnerEntity() {
        if (ownerUuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;
        return sw.getEntity(ownerUuid);
    }

    @Override
    protected void initGoals() {}

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = 15;
            this.idleAnimationState.start(this.age);
        } else {
            --this.idleAnimationTimeout;
        }
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }


    @Override
    public void tick() {

        super.tick();

        if (!this.getWorld().isClient) {
            BlockPos below = this.getBlockPos().down();


            int power = this.getWorld().getReceivedRedstonePower(below);

float targetYaw = this.getWorld().getReceivedRedstonePower(below);
          float currentSpinSpeed = (float) (power * 0.1f * 100f);

            RotationTweener tweener = new RotationTweener(
      this.getYaw(), this.getPitch(),  // start yaw/pitch
      targetYaw, this.pitch,              // target yaw/pitch
     power * 1,                                  // duration in ticks (2 seconds at 20 TPS)
       Easing.IN_OUT_SINE );                  // easing type


// in a tick handler or goal tick
if (!tweener.isDone()) {
       tweener.tick((yaw, pitch) -> {
      this.setYaw(yaw);
       this.setPitch(pitch);
    });
           }





            this.setYaw(this.getYaw() - currentSpinSpeed);
           this.setBodyYaw(this.getYaw());
           this.setHeadYaw(this.getYaw());


           if (this.getYaw() > 360f || this.getYaw() < -360f) {
               this.setYaw(0f);
          }
       }

        if (this.getWorld().isClient) {
            setupAnimationStates();
            boolean alive = this.isAlive() && !this.isRemoved();
            if (alive) {
                if (bodyLightHandle == null || !bodyLightHandle.isValid()) {
                    Vec3d p = this.getPos();
                    bodyLight = new PointLightData()
                            .setBrightness(BODY_LIGHT_BRIGHTNESS)
                            .setColor(BODY_R, BODY_G, BODY_B)
                            .setRadius(BODY_LIGHT_RADIUS)
                            .setPosition(p.x, p.y, p.z);
                    bodyLightHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(bodyLight);
                } else {
                    Vec3d p = this.getPos();
                    bodyLight.setPosition(p.x, p.y, p.z);
                    bodyLightHandle.markDirty();
                }
            } else {
                freeLight();
            }
        }
    }



    private void explodeAndRemove() {
        if (this.getWorld() instanceof ServerWorld sw) {


            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 3, getZ(), 10, 0, 0, 0, 0);
            sw.playSound(this, getBlockPos(), SoundEvents.ENTITY_ALLAY_HURT, SoundCategory.AMBIENT, 10, pitch
            );




        }
        this.discard();
    }

    private void freeLight() {
        if (bodyLightHandle != null && bodyLightHandle.isValid()) {
            bodyLightHandle.free();
        }
        bodyLightHandle = null;
        bodyLight = null;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || amount > 5000) {

            return super.damage(source, amount);


        } else {
            return false;
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }
    @Override
    protected void pushAway(Entity entity) {}

    @Override public boolean isBreedingItem(ItemStack stack) { return false; }
    @Nullable @Override public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) { return null; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }
    private PlayState predicate(software.bernie.geckolib.animation.AnimationState<LoveEntity> s) {
        s.getController().setAnimation(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
        return PlayState.CONTINUE;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void onRemoved() {
        if (this.getWorld().isClient) freeLight();
        super.onRemoved();
    }
    @Override
    public void remove(RemovalReason reason) {
        if (this.getWorld().isClient) freeLight();
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("SpawnAge", spawnAge);
        nbt.putInt("MaxLife", maxLifeTicks);
        nbt.putDouble("HitboxSize", hitboxSize);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        spawnAge = nbt.getInt("SpawnAge");
        maxLifeTicks = nbt.getInt("MaxLife");
        hitboxSize = nbt.contains("HitboxSize") ? nbt.getDouble("HitboxSize") : hitboxSize;
    }
}
