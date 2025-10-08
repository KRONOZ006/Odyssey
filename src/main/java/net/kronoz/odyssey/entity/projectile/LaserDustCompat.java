package net.kronoz.odyssey.entity.projectile;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class LaserDustCompat {
    private LaserDustCompat(){}

    public static void ping(World world, Vec3d pos, Vec3d vel) {
        try {
            Class<?> pingerCls = Class.forName("net.kronoz.odyssey.client.dust.LightDustPinger");
            Class<?> managerCls = Class.forName("net.kronoz.odyssey.client.dust.DustManager");
            Constructor<?> ctor = pingerCls.getDeclaredConstructor(double.class,double.class,double.class,double.class,double.class,double.class,float.class,float.class,float.class,float.class,int.class);
            Object pinger = ctor.newInstance(pos.x,pos.y,pos.z, vel.x,vel.y,vel.z, 1f,0.1f,0.1f, 0.8f, 6);
            Method add = managerCls.getDeclaredMethod("add", pingerCls);
            add.invoke(null, pinger);
            return;
        } catch (Throwable ignored) {}

        world.addParticle(new DustParticleEffect(new Vector3f(1f,0.1f,0.1f), 1.0f), pos.x, pos.y, pos.z, 0, 0, 0);
        world.addParticle(new DustParticleEffect(new Vector3f(1f,0.1f,0.1f), 1.0f), pos.x - vel.x*0.5, pos.y - vel.y*0.5, pos.z - vel.z*0.5, 0, 0, 0);
    }
}
