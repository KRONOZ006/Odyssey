package net.kronoz.odyssey.particle;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class SentryShieldFullParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    private static final float PARTICLE_SCALE = 5f;

    private float lifeProgress = (float)this.age / (float)this.maxAge;

    private static final float BODY_LIGHT_BRIGHTNESS = 0.2f;
    private static final float BODY_LIGHT_RADIUS     = 5f;
    private static final float BODY_R = 1.00f, BODY_G = 1.00f, BODY_B = 5.00f;

    private PointLightData bodyLight;
    private LightRenderHandle<PointLightData> bodyLightHandle;

    public SentryShieldFullParticle(ClientWorld clientWorld, double x, double y, double z,
                               SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
        super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed);

        this.setSpriteForAge(spriteProvider);

        this.sprites = spriteProvider;


        this.scale(7f);


        this.maxAge = 5;


        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
    }








    @Override
    public void tick() {
        super.tick();
        this.setSpriteForAge(this.sprites);

        float lifeProgress = (float)this.age / (float)this.maxAge;
//        float finalScale =  MathHelper.lerp(lifeProgress, PARTICLE_SCALE, PARTICLE_SCALE * 0.2f);






        this.alpha = MathHelper.lerp(lifeProgress, 1f, 0.1f);
        boolean alive = this.isAlive() && !this.dead;
        if (alive) {
            if (bodyLightHandle == null || !bodyLightHandle.isValid()) {

                Vec3d p = new Vec3d(this.x, this.y, this.z);
                bodyLight = new PointLightData()
                        .setBrightness(BODY_LIGHT_BRIGHTNESS)
                        .setColor(BODY_R, BODY_G, BODY_B )
                        .setRadius(BODY_LIGHT_RADIUS)
                        .setPosition(p.x, p.y, p.z);
                bodyLightHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(bodyLight);
            } else {
                Vec3d p = new Vec3d(this.x, this.y, this.z);




                float brightness = MathHelper.lerp(lifeProgress, BODY_LIGHT_BRIGHTNESS, 0.01f);


                float r = 1.0f;
                float g = 0.0f;
                float b = 1.0f + lifeProgress * 0.5f;

                float radius = MathHelper.lerp(lifeProgress, BODY_LIGHT_RADIUS, 8f);

                bodyLight
                        .setBrightness(brightness)
                        .setColor(r, g, b)
                        .setRadius(radius)
                        .setPosition(p.x, p.y, p.z);
                bodyLightHandle.markDirty();
            }
        } else {
            freeLight();
        }

    }

    private void freeLight() {
        if (bodyLightHandle != null && bodyLightHandle.isValid()) {
            bodyLightHandle.free();
        }
        bodyLightHandle = null;
        bodyLight = null;
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0; // max brightness
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return new SentryShieldFullParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
        }
    }
}
