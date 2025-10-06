package net.kronoz.odyssey.entity.sentinel;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kronoz.odyssey.entity.sentinel.SentinelEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.Iterator;
import java.util.Map;

public final class SentinelLightClient {
    private SentinelLightClient() {}

    private static final float AL_BRIGHTNESS_VISIBLE = 5.0f;
    private static final float AL_BRIGHTNESS_HIDDEN  = 2.5f;
    private static final float AL_ANGLE = (float)Math.toRadians(40.0);
    private static final float AL_DISTANCE = 80.0f;
    private static final float AL_SIZE_X = 0.20f;
    private static final float AL_SIZE_Y = 0.20f;

    private static final float PL_BRIGHTNESS_VISIBLE = 5.0f;
    private static final float PL_BRIGHTNESS_HIDDEN  = 2.5f;
    private static final float PL_RADIUS = 3.0f;

    private static final float CLR_R = 0.447f, CLR_G = 0.0f, CLR_B = 0.651f;

    private static final float EYE_FORWARD_OFFSET = 0.35f;
    private static final float EYE_UP_OFFSET = 1.2f;
    private static final float EYE_SIDE_OFFSET = 0.0f;

    private static final class Pair {
        LightRenderHandle<AreaLightData> areaH;
        AreaLightData areaD;
        LightRenderHandle<PointLightData> pointH;
        PointLightData pointD;
    }

    private static final Int2ObjectOpenHashMap<Pair> HANDLES = new Int2ObjectOpenHashMap<>();

    public static void initClient() {
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());

        WorldRenderEvents.START.register(ctx -> {
            if (!RenderSystem.isOnRenderThread()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            ClientWorld w = mc.world;
            if (w == null) { reset(); return; }
            if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) return;

            for (SentinelEntity e : w.getEntitiesByClass(SentinelEntity.class, mc.player != null ? mc.player.getBoundingBox().expand(256) : null, x -> true)) {
                int id = e.getId();
                if (!HANDLES.containsKey(id)) addFor(e);
            }

            ObjectIterator<Int2ObjectMap.Entry<Pair>> it = HANDLES.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Pair> en = it.next();
                int id = en.getKey();
                SentinelEntity e = (SentinelEntity) w.getEntityById(id);
                if (e == null || e.isRemoved()) {
                    removeFor(id);
                    it.remove();
                }
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;
            ClientWorld w = mc.world;
            for (SentinelEntity e : w.getEntitiesByClass(SentinelEntity.class, mc.player != null ? mc.player.getBoundingBox().expand(256) : null, x -> true)) {
                int id = e.getId();
                Pair p = HANDLES.get(id);
                if (p == null) continue;

                float bodyYawRad = (float)Math.toRadians(e.getYaw());
                float eyeYawRadWorld = bodyYawRad + (-e.getEyeYaw());
                float eyePitchRadWorld = e.getEyePitch();

                float fx = (float)(Math.cos(eyePitchRadWorld) * -Math.sin(eyeYawRadWorld));
                float fy = (float)(Math.sin(eyePitchRadWorld));
                float fz = (float)(Math.cos(eyePitchRadWorld) * Math.cos(eyeYawRadWorld));

                float sx = (float)Math.cos(bodyYawRad);
                float sz = (float)Math.sin(bodyYawRad);
                float px = (float)(-Math.sin(bodyYawRad));
                float pz = (float)(Math.cos(bodyYawRad));

                Vec3d base = e.getPos().add(0, EYE_UP_OFFSET, 0);
                Vec3d eyePos = base
                        .add(px * EYE_FORWARD_OFFSET, 0, pz * EYE_FORWARD_OFFSET)
                        .add(sx * EYE_SIDE_OFFSET, 0, sz * EYE_SIDE_OFFSET);

                Quaternionf q = new Quaternionf()
                        .rotateY(eyeYawRadWorld)
                        .rotateX(-eyePitchRadWorld);

                float brightnessAL = e.isSpotted() ? AL_BRIGHTNESS_VISIBLE : AL_BRIGHTNESS_HIDDEN;
                float brightnessPL = e.isSpotted() ? PL_BRIGHTNESS_VISIBLE : PL_BRIGHTNESS_HIDDEN;

                if (p.areaD != null) {
                    p.areaD.setBrightness(brightnessAL).setColor(CLR_R, CLR_G, CLR_B).setSize(AL_SIZE_X, AL_SIZE_Y).setAngle(AL_ANGLE).setDistance(AL_DISTANCE);
                    p.areaD.getPosition().set((float)eyePos.x, (float)eyePos.y, (float)eyePos.z);
                    p.areaD.getOrientation().set(q);
                    if (p.areaH != null && p.areaH.isValid()) p.areaH.markDirty();
                }

                if (p.pointD != null) {
                    p.pointD.setBrightness(brightnessPL).setColor(CLR_R, CLR_G, CLR_B).setRadius(PL_RADIUS);
                    p.pointD.setPosition((float)eyePos.x, (float)eyePos.y, (float)eyePos.z);
                    if (p.pointH != null && p.pointH.isValid()) p.pointH.markDirty();
                }
            }
        });
    }

    private static void addFor(SentinelEntity e) {
        if (VeilRenderSystem.renderer() == null || VeilRenderSystem.renderer().getLightRenderer() == null) return;
        Pair p = new Pair();

        AreaLightData al = new AreaLightData()
                .setBrightness(AL_BRIGHTNESS_HIDDEN)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setSize(AL_SIZE_X, AL_SIZE_Y)
                .setAngle(AL_ANGLE)
                .setDistance(AL_DISTANCE);
        LightRenderHandle<AreaLightData> ah = VeilRenderSystem.renderer().getLightRenderer().addLight(al);

        PointLightData pl = new PointLightData()
                .setBrightness(PL_BRIGHTNESS_HIDDEN)
                .setColor(CLR_R, CLR_G, CLR_B)
                .setRadius(PL_RADIUS);
        LightRenderHandle<PointLightData> ph = VeilRenderSystem.renderer().getLightRenderer().addLight(pl);

        p.areaD = al; p.areaH = ah; p.pointD = pl; p.pointH = ph;
        HANDLES.put(e.getId(), p);
    }

    private static void removeFor(int id) {
        Pair p = HANDLES.remove(id);
        if (p == null) return;
        if (p.areaH != null && p.areaH.isValid()) p.areaH.close();
        if (p.pointH != null && p.pointH.isValid()) p.pointH.close();
    }

    private static void reset() {
        for (Pair p : HANDLES.values()) {
            if (p.areaH != null && p.areaH.isValid()) p.areaH.close();
            if (p.pointH != null && p.pointH.isValid()) p.pointH.close();
        }
        HANDLES.clear();
    }
}
