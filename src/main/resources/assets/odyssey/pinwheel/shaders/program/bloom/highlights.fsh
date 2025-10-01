#version 150

uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

uniform float HighlightIntensity = 0.975;

vec3 BloomLod(float scale, vec2 offset){
    vec2 uv = (texCoord - offset) * scale;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
    return vec3(0.0);

    return texture(DiffuseSampler0, uv).rgb;
}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);

    vec3 color = vec3(0.0);
    float scale = 2.0;
    float offset = 0.0;

    for (int i = 0; i < 5; i++) {
        color += BloomLod(scale, vec2(offset, 0.0));
        offset = (1.0 - (1.0 / scale));
        scale *= 2.0;
    }

    // apply intensity scaling
    color *= HighlightIntensity;

    float Brightness = dot(color, vec3(0.126, 0.4, 0.5));

    if (Brightness >= 1.0) {
        fragColor = vec4(color, 1.0);
    } else {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
}
