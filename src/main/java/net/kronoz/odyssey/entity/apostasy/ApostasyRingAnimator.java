package net.kronoz.odyssey.entity.apostasy;

import java.util.Random;

public final class ApostasyRingAnimator {
    private ApostasyRingAnimator(){}

    public static final class Ang { public float x,y,z; public Ang(float x,float y,float z){this.x=x;this.y=y;this.z=z;} }

    private static int mix(int a, int b) {
        int x = a ^ b * 0x9E3779B9;
        x ^= (x >>> 16); x *= 0x85EBCA6B;
        x ^= (x >>> 13); x *= 0xC2B2AE35;
        x ^= (x >>> 16);
        return x;
    }

    private static float rf(Random r, float min, float max) {
        return min + r.nextFloat() * (max - min);
    }

    public static Ang angles(long seed, String bone, float t) {
        int h = mix((int)(seed ^ (seed >>> 32)), bone.hashCode());
        Random r = new Random(h);

        float sYaw   = rf(r, -2.6f,  2.6f);
        float sPitch = rf(r, -2.0f,  2.0f);
        float sRoll  = rf(r, -3.0f,  3.0f);

        float wobAmpX = rf(r, 0.35f, 1.10f);
        float wobAmpY = rf(r, 0.25f, 0.95f);
        float wobAmpZ = rf(r, 0.35f, 1.25f);

        float wobFx = rf(r, 0.6f, 1.8f);
        float wobFy = rf(r, 0.5f, 1.6f);
        float wobFz = rf(r, 0.7f, 2.1f);

        float phx = rf(r, 0f, (float)(Math.PI * 2));
        float phy = rf(r, 0f, (float)(Math.PI * 2));
        float phz = rf(r, 0f, (float)(Math.PI * 2));

        float x = sPitch * t + (float)Math.sin(t * wobFx + phx) * wobAmpX;
        float y = sYaw   * t + (float)Math.sin(t * wobFy + phy) * wobAmpY;
        float z = sRoll  * t + (float)Math.sin(t * wobFz + phz) * wobAmpZ;

        return new Ang(x, y, z);
    }
}
