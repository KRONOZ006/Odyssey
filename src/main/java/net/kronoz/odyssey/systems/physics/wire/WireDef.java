package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.util.Identifier;

public final class WireDef {
    public final Identifier texture;

    // visual / texturing
    public final int texLenPx;
    public final int texWidthPx;
    public final int tubeSides;

    // physics resolution & shape
    public final int segments;
    public final float halfWidth;
    public final float thickness;

    // sag
    public final float baseSlack;
    public final float sagPerMeter;

    // integration / constraints
    public final float damping;
    public final int substeps;
    public final int iters;
    public final float gravity;
    public final float bendK;

    // collisions
    public final int collidePasses;

    public WireDef(Identifier texture,
                   int texLenPx, int texWidthPx,
                   int tubeSides,
                   int segments, float halfWidth,
                   float baseSlack, float sagPerMeter,
                   float damping, int substeps, int iters,
                   float gravity, float bendK,
                   int collidePasses) {

        this.texture = texture;

        this.texLenPx   = Math.max(2, texLenPx);
        this.texWidthPx = Math.max(2, texWidthPx);
        this.tubeSides  = Math.max(3, tubeSides);

        this.segments   = Math.max(4, segments);

        float hw = Math.max(0.0025f, halfWidth);
        this.halfWidth  = hw;
        this.thickness  = hw * 2f;

        this.baseSlack  = Math.max(0f, baseSlack);
        this.sagPerMeter= Math.max(0f, sagPerMeter);

        this.damping    = Math.max(0f, Math.min(0.98f, damping));
        this.substeps   = Math.max(1, substeps);
        this.iters      = Math.max(1, iters);
        this.gravity    = gravity;
        this.bendK      = Math.max(0f, Math.min(1f, bendK));

        this.collidePasses = Math.max(1, collidePasses);
    }

    public static WireDef defaultCable(Identifier tex) {
        return new WireDef(
                tex,
                128, 16,
                8,
                44, 0.04f,
                0.18f, 0.06f,
                0.08f, 2, 8,
                0.045f, 0.35f,
                3
        );
    }
}