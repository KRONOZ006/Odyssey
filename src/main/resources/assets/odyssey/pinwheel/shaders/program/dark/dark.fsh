#version 150
#include veil:space_helper
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseDepthSampler;
uniform sampler2D DiffuseSampler;

uniform float GameTime;

uniform float TrueDarkness;

uniform vec3  VoidTint      = vec3(0.502, 0.0353, 0.239);
uniform float Density       = 0.2;
uniform float MaxDark       = 1.0;

uniform float SkyDistance   = 80.0;

uniform float NoiseScale    = 1.0;
uniform float NoiseAmount   = 0.1;
uniform float NoiseSpeed    = 0.8;

uniform float HeightZeroY   = 80.0;
uniform float HeightSlope   = 1.0;
uniform float HeightClamp   = 0.4;

uniform float DitherAmp     = 0.0006;

out vec4 fragColor;
in vec2 texCoord;

float hash12(vec2 p){
    vec3 p3 = fract(vec3(p.xyx)*0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y)*p3.z);
}

float cheepNoise(vec3 p){
    vec3 q = floor(p), f = fract(p);
    vec3 u = f*f*(3.0 - 2.0*f);
    float n000 = hash12(q.xy + q.z);
    float n100 = hash12((q.xy+vec2(1,0)) + q.z);
    float n010 = hash12((q.xy+vec2(0,1)) + q.z);
    float n110 = hash12((q.xy+vec2(1,1)) + q.z);
    float nx0 = mix(n000, n100, u.x);
    float nx1 = mix(n010, n110, u.x);
    return mix(nx0, nx1, u.y);
}

void main(){
    vec3 base = texture(DiffuseSampler, texCoord).rgb;

    float rawDepth = texture(DiffuseDepthSampler, texCoord).r;
    bool  sky     = (rawDepth >= 0.999);
    rawDepth = clamp(rawDepth, 0.0, 0.999999);

    vec3  camPos  = VeilCamera.CameraPosition;
    float distM;

    if (sky){
        distM = SkyDistance;
    } else {
        vec3 hitW = screenToWorldSpace(texCoord, rawDepth).xyz;
        distM = max(distance(hitW, camPos), 0.0);
    }

    float dens = Density * mix(1.0, 2.0, clamp(TrueDarkness, 0.0, 1.0));

    if (HeightSlope != 0.0){
        float h = clamp((HeightZeroY - camPos.y) * HeightSlope, -HeightClamp, HeightClamp);
        dens *= (1.0 + h);
    }

    if (NoiseAmount > 0.0 && NoiseScale > 0.0){
        vec3 sampleP;
        if (sky) {
            vec3 rd = normalize(viewDirFromUv(texCoord));
            sampleP = camPos + rd * SkyDistance;
        } else {
            sampleP = screenToWorldSpace(texCoord, rawDepth).xyz;
        }
        float n = cheepNoise(sampleP * NoiseScale + vec3(0.0, GameTime*NoiseSpeed, 0.0));
        dens *= mix(1.0, 0.5 + n, clamp(NoiseAmount, 0.0, 1.0));
    }

    float T = exp(-dens * distM);

    float alpha = clamp(1.0 - T, 0.0, MaxDark);

    vec3 outCol = base * (1.0 - alpha) + (VoidTint * 0.05) * alpha;

    if (DitherAmp > 0.0){
        outCol += (hash12(gl_FragCoord.xy) - 0.5) * DitherAmp * alpha;
    }

    fragColor = vec4(clamp(outCol, 0.0, 1.0), 1.0);
}
