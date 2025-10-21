package net.kronoz.odyssey.entity.thrasher;



import net.kronoz.odyssey.init.ModParticles;
import net.kronoz.odyssey.init.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;

import java.util.*;

public final class SliceWave {
    private final ServerWorld world;
    private final UUID ownerId;
    private final Vec3d origin;
    private final Vec3d direction;
    private final double speed;
    private final double range;
    private final double hitRadius;
    private final float damage;
    private final double push;
    private final double lift;

    private int age = 0;
    private double distanceTravelled = 0.0;
    private final Set<Integer> hitEntities = new HashSet<>();
    float minRandom = 1.3f;
    float maxRandom = 2.0f;

    Random random = new Random();

    float pitch = minRandom + random.nextFloat() * (maxRandom - minRandom);

    public SliceWave(ServerWorld world, PlayerEntity owner, Vec3d origin, Vec3d direction) {
        this.world = world;
        this.ownerId = owner.getUuid();
        this.origin = origin;
        this.direction = direction.normalize();
        this.speed = 1.5;          // blocks per tick
        this.range = 6;          // max distance
        this.hitRadius = 3.0;      // AoE radius
        this.damage = 6f;          // direct damage
        this.push = 1.5;           // horizontal knockback
        this.lift = 0.5;           // vertical knockback
    }

    public static SliceWave start(ServerWorld world, PlayerEntity owner, Vec3d direction) {
        return new SliceWave(world, owner, owner.getEyePos(), direction);
    }

    public boolean tick() {
        PlayerEntity owner = world.getPlayerByUuid(ownerId);
        if (owner == null || owner.isRemoved()) return false;

        distanceTravelled = Math.min(distanceTravelled + speed, range);
        Vec3d from = origin.add(direction.multiply(distanceTravelled - speed));
        Vec3d to = origin.add(direction.multiply(distanceTravelled));

        // Spawn particles along the path


        // Check for block collisions
        RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, owner);
        HitResult hit = world.raycast(ctx);
        if (hit.getType() == HitResult.Type.BLOCK) {
            applyAoE(owner, hit.getPos());
            return false;
        }

        // Apply damage along the path
        applyAoE(owner, to);

        age++;
        return distanceTravelled < range;
    }

//    private void spawnParticles(Vec3d from, Vec3d to) {
//        int steps = (int)Math.ceil(from.distanceTo(to) * 2);
//        for (int i = 0; i <= steps; i++) {
//            Vec3d pos = from.lerp(to, i / (double) steps);
//            Vec3d eye = this.getEyePos();
//            Vec3d look = this.getRotationVec(1.0f);
//            world.spawnParticles(ModParticles.SLICE_PARTICLE, pos.x, pos.y, pos.z,
//                    1, 0.0, 0.0, 0.0, 0.0);
//        }
//    }

    private void applyAoE(PlayerEntity owner, Vec3d center) {
        Box box = new Box(center.x - hitRadius, center.y - hitRadius, center.z - hitRadius,
                center.x + hitRadius, center.y + hitRadius, center.z + hitRadius);




                Vec3d eye = owner.getEyePos();
                Vec3d look = owner.getRotationVec(1.0f);
                world.spawnParticles(ModParticles.SLICE_PARTICLE, eye.x + look.x * 6,
                        eye.y + 0.1 + look.y * 6,
                        eye.z + look.z * 6,
                        1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, BlockPos.ofFloored(owner.getPos()),
                ModSounds.SLICE, SoundCategory.PLAYERS, 0.8f, pitch);



        List<Entity> targets = world.getOtherEntities(owner, box, e -> e.isAlive()  &&
                !e.isSpectator() &&
                e != owner &&
                e != owner.getVehicle());

        for (Entity e : targets) {
            if (!hitEntities.add(e.getId())) continue; // only hit once
            e.addVelocity(direction.x * push, lift, direction.z * push);
            e.velocityModified = true;

            System.out.println(owner.getVehicle());

            if (e instanceof LivingEntity le) {
                le.timeUntilRegen = 0;
                le.damage(world.getDamageSources().playerAttack(owner), damage);
            }
            world.spawnParticles(ModParticles.SLICE_PARTICLE, e.getX(),
                    e.getY() + 1.0,
                    e.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            world.playSound(null, BlockPos.ofFloored(e.getPos()),
                    ModSounds.SLICE, SoundCategory.PLAYERS, 0.8f, pitch);
        }
    }
}
