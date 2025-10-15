#include veil:space_helper
#veil:buffer veil:camera VeilCamera
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D DiffuseSampler;
uniform float GameTime;
uniform float TrueDarkness;

out vec4 fragColor;
in vec2 texCoord;

// ---------- noise ----------
float hash(vec3 p){
    p = fract(p*0.314 + vec3(0.0,0.0,0.0));
    p += dot(p, p.yzx + 15.19);
    return fract(p.x*p.y*p.z*50.733);
}
float valueNoise(vec3 p){
    vec3 i = floor(p), f = fract(p);
    vec3 u = f*f*(3.0-2.0*f);
    float n000 = hash(i+vec3(0,0,0));
    float n100 = hash(i+vec3(1,0,0));
    float n010 = hash(i+vec3(0,1,0));
    float n110 = hash(i+vec3(1,1,0));
    float n001 = hash(i+vec3(0,0,1));
    float n101 = hash(i+vec3(1,0,1));
    float n011 = hash(i+vec3(0,1,1));
    float n111 = hash(i+vec3(1,1,1));
    float nx00 = mix(n000,n100,u.x);
    float nx10 = mix(n010,n110,u.x);
    float nx01 = mix(n001,n101,u.x);
    float nx11 = mix(n011,n111,u.x);
    float nxy0 = mix(nx00,nx10,u.y);
    float nxy1 = mix(nx01,nx11,u.y);
    return mix(nxy0,nxy1,u.z);
}
float fbm(vec3 p){
    float a = 0.2, s = 0.0;
    for(int i=0;i<3;i++){ s += a*valueNoise(p); p*=5.0; a*=0.5; }
    return s;
}
float rand(vec2 c){ return fract(sin(dot(c, vec2(12.9898,78.223))) * 43758.5453); }
float luma(vec3 c){ return dot(c, vec3(0.2126,0.7152,0.0722)); }

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    vec3 rayDir = normalize(viewDirFromUv(texCoord));
    float j = (rand(texCoord*vec2(1920.0,1080.0) + GameTime*13.37) - 0.5) * 0.08;
    vec3 rayOrigin = VeilCamera.CameraPosition + rayDir * j;

    // --- auto day/night ---
    vec2 px = 1.5 / vec2(textureSize(DiffuseSampler, 0));
    vec3 c0 = scene.rgb;
    vec3 cx = texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb;
    vec3 cy = texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb;
    float lum = 0.5*luma(c0) + 0.25*luma(cx) + 0.25*luma(cy);
    float night = smoothstep(0.6, 0.25, lum);
    night = clamp(max(night, TrueDarkness), 0.0, 1.0);

    if (night < 0.0) {
        fragColor = scene;
        return;
    }

    float stepLen = 0.2;
    vec3  fogCol  = vec3(0.25,0.25,0.30);
    float base    = 0.04;
    float contrast= 0.35;

    bool isSky = (depth >= 0.9999);
    float maxDist = isSky ? 96.0 : length(screenToWorldSpace(texCoord, depth).xyz - rayOrigin);
    float skyReduce = 0.7;

    float T = 1.0;
    float distTraveled = 0.0;

    for(int i = 0; i < 80; i++) {
        if (distTraveled > maxDist || T < 0.01) break;

        vec3 p = rayOrigin + rayDir * distTraveled;

        float n = fbm(p * 0.10 + vec3(0.0, 0.0, GameTime * 0.15));
        n = pow(max(n, 1e-4), 1.6);

        float density = base * (1.0 + (n - 0.5) * contrast);
        if (isSky) density *= skyReduce;
        density *= 4.0;

        float dT = exp(-density * stepLen);
        T *= dT;

        distTraveled += stepLen;
    }

    float fogAmt = clamp(1.0 - T, 0.0, 3.0);

    vec3 outCol = mix(scene.rgb, fogCol, fogAmt);

    {
        float g = rand(texCoord * vec2(1024.0,768.0) + floor(GameTime*9.0)*23.0) - 0.5;
        float grain = g * (0.08 * fogAmt);
        float r = length(texCoord - 0.5) * 1.65;
        float vign = smoothstep(1.1, 0.6, r);
        outCol = outCol * (1.0 - 0.06*(1.0 - vign)) + grain;
    }

    fragColor = vec4(outCol, 1.0);
}
