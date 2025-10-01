#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

void main(){
    ivec2 ts = textureSize(DiffuseSampler, 0);
    float px = 1.0 / float(ts.x);

    vec3 c = vec3(0.0);
    c += texture(DiffuseSampler, texCoord).rgb * 0.227027;
    c += texture(DiffuseSampler, texCoord + vec2(1.384615 * px, 0.0)).rgb * 0.316216;
    c += texture(DiffuseSampler, texCoord - vec2(1.384615 * px, 0.0)).rgb * 0.316216;
    c += texture(DiffuseSampler, texCoord + vec2(3.230769 * px, 0.0)).rgb * 0.070270;
    c += texture(DiffuseSampler, texCoord - vec2(3.230769 * px, 0.0)).rgb * 0.070270;

    fragColor = vec4(c, 0.0);
}
