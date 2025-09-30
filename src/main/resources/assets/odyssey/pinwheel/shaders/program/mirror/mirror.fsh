#version 450

in vec3 vPos;
out vec4 FragColor;

uniform float GameTime;

// Simple 3D hash / noise
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// Optimized 3D noise
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 f3 = f*f*(3.0 - 2.0*f);

    float a = hash(i);
    float b = hash(i + vec3(1.0,0,0));
    float c = hash(i + vec3(0,1.0,0));
    float d = hash(i + vec3(1.0,1.0,0));
    float e = hash(i + vec3(0,0,1.0));
    float f0 = hash(i + vec3(1.0,0,1.0));
    float g = hash(i + vec3(0,1.0,1.0));
    float h = hash(i + vec3(1.0,1.0,1.0));

    float lerpX1 = mix(a,b,f3.x);
    float lerpX2 = mix(c,d,f3.x);
    float lerpX3 = mix(e,f0,f3.x);
    float lerpX4 = mix(g,h,f3.x);

    float lerpY1 = mix(lerpX1,lerpX2,f3.y);
    float lerpY2 = mix(lerpX3,lerpX4,f3.y);

    return mix(lerpY1,lerpY2,f3.z);
}

// Domain-warped fractal noise (5 octaves)
float fbm(vec3 p) {
    float f = 0.0;
    float amp = 0.5;
    mat3 m = mat3(
        0.8,0.6,-0.6,
       -0.6,0.8,0.6,
        0.6,-0.6,0.8
    );

    f += amp*noise(p); p=m*p*2.0; amp*=0.5;
    f += amp*noise(p); p=m*p*2.0; amp*=0.5;
    f += amp*noise(p); p=m*p*2.0; amp*=0.5;
    f += amp*noise(p); p=m*p*2.0; amp*=0.5;
    f += amp*noise(p);

    return f;
}

void main() {
    vec3 uv = vPos * 0.1;

    // Base fbm
    float base = fbm(uv);

    // Domain warp: single vector reused for multiple channels
    vec3 warp1 = vec3(base, base, base);
    vec3 q = vec3(
        base,
        fbm(uv + vec3(5.2,1.3,3.7) + warp1),
        fbm(uv + vec3(2.1,7.3,4.1) - warp1)
    );

    // Combine r and g warp into one computation with offsets
    vec3 rg = vec3(
        fbm(uv + 4.0*q + vec3(GameTime*200.0, GameTime*300.0, GameTime*500.0)),
        fbm(uv + 2.0*q + vec3(GameTime*700.0, GameTime*900.0, GameTime*1000.0)),
        0.0 // unused, saves a call
    );

    // Final noise
    float n = fbm(uv + 5.0*rg - GameTime*0.01);

    // Color composition
    vec3 col = mix(vec3(0.0,0.0,0.0), vec3(0.,0.,0.), n);
    col += vec3(1.0,1.0,1.01) * dot(q,q) * 0.1;
    col += vec3(0.1,0.1,0.1) * rg.y*rg.y * -0.5;
    col += vec3(0.5,0.6,0.7) * rg.x*rg.x;
    col += vec3(0.1,0.1,0.1) * q.x * 1.0;

    // Glow pulse
    col *= 0.7 + 0.01*sin(GameTime*300.0);

    FragColor = vec4(clamp(col,0.0,1.0),1.0);
}
