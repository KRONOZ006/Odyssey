#version 150
in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

/* ================= CONFIG (safe defaults) ================= */
uniform vec2  CenterUV       = vec2(0.5, 0.5);   // center of vignette bubble
uniform vec3  VoidTint       = vec3(0.05, 0.02, 0.03);
uniform float Strength       = 0.0;             // master darkness
uniform float MaxDark        = 0.95;             // hard cap per pixel

// Depth → fog curve (raw depth 0=near, 1=far). If no depth, set HasDepth=0.
uniform float DepthStart     = 0.2;
uniform float DepthEnd       = 1.0;
uniform float DepthPower     = 2.0;
uniform int   HasDepth       = 1;                // 1 if DiffuseDepthSampler is valid

// Vignette bubble (screen-space)
uniform float SafeRadius     = 0.20;
uniform float VignettePower  = 3.2;
uniform float VignetteWeight = 0.00;

// Vertical “pit” shaping (screen-space) for atmosphere
uniform float PitOffset      = 0.0;             // expands bright mid band
uniform float PitPower       = 1.10;
uniform float PitWeight      = 0.55;

// Silhouette readability (rim)
uniform float EdgeGlow       = 0.07;
uniform float GlowWidth      = 0.8;

// Anti-banding
uniform float NoiseAmp       = 0.02;            // 0 disables
/* ========================================================== */

/* Tiny hash noise */
float hash21(vec2 p){
    p = fract(p*vec2(123.34,456.21));
    p += dot(p, p+34.345);
    return fract(p.x*p.y);
}

void main() {
    vec2 uv = texCoord;
    vec3 base = texture(DiffuseSampler, uv).rgb;

    /* Depth (SAFE): don’t linearize, clamp, and allow “no depth” path */
    float rawDepth = texture(DiffuseDepthSampler, uv).r;
    rawDepth = clamp(rawDepth, 0.0, 0.999999); // sky usually ~1.0; keep inside [0,1)

    float dFog = 0.0;
    if (HasDepth == 1) {
        // If DepthStart >= DepthEnd or values are nonsense, avoid NaN by swapping
        float ds = min(DepthStart, DepthEnd - 1e-5);
        float de = max(DepthEnd, DepthStart + 1e-5);
        dFog = smoothstep(ds, de, rawDepth);
        dFog = pow(dFog, max(DepthPower, 0.001));
    }

    /* Vignette bubble */
    float r = length(uv - CenterUV);
    float vign = pow(smoothstep(SafeRadius, 1.0, r), max(VignettePower, 0.001)) * clamp(VignetteWeight, 0.0, 2.0);

    /* Vertical “pit” */
    float band = abs((uv.y - CenterUV.y) * 2.0);
    float pit = pow(
    clamp((band - clamp(PitOffset, 0.0, 0.95)) / max(1.0 - PitOffset, 1e-5), 0.0, 1.0),
    max(PitPower, 0.001)
    ) * clamp(PitWeight, 0.0, 2.0);

    /* Combine → darkness alpha */
    float alpha = clamp((dFog + vign + pit) * Strength, 0.0, MaxDark);

    /* Edge glow rim (thin) */
    float edge = 0.0;
    if (EdgeGlow > 0.0 && GlowWidth > 0.0) {
        float e0 = smoothstep(SafeRadius, SafeRadius + GlowWidth, r);
        float e1 = 1.0 - smoothstep(0.95, 1.0, r);
        edge = e0 * e1 * EdgeGlow;
    }

    /* Film grain / dither */
    float n = (hash21(gl_FragCoord.xy) * 2.0 - 1.0) * NoiseAmp;

    /* Final: multiplicative darkening with tiny tint + rim + grain */
    vec3 tint = VoidTint;
    vec3 outC = base * (1.0 - alpha) + tint * 0.05 * alpha; // mostly multiply, slight color bias
    outC += edge;
    outC += n;

    fragColor = vec4(clamp(outC, 0.0, 1.0), 1.0);
}
