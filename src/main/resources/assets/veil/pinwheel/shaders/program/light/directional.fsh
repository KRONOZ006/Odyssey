#include veil:common
#include veil:space_helper
#include veil:color_utilities
#include veil:light
#include veil:voxel_shadow

in vec2 texCoord;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;

uniform vec3 LightColor;
uniform vec3 LightDirection;

out vec4 fragColor;

void main() {
    vec2 depthSize = vec2(textureSize(DepthSampler, 0));
    vec2 screenUv = gl_FragCoord.xy / max(depthSize, vec2(1.0));

    vec4 albedoColor = texture(AlbedoSampler, screenUv);
    if (albedoColor.a == 0.0) {
        discard;
    }

    float depth = texture(DepthSampler, screenUv).r;
    if (depth >= 1.0) {
        discard;
    }

    vec3 normalVS = texture(NormalSampler, screenUv).xyz;
    vec3 lightDirectionVS = normalize((VeilCamera.ViewMat * vec4(LightDirection, 0.0)).xyz);
    float diffuse = clamp(smoothstep(-0.2, 0.2, -dot(normalVS, lightDirectionVS)), 0.0, 1.0);

    vec3 worldPos = screenToWorldSpace(screenUv, depth).xyz;
    vec3 normalWS = normalize((VeilCamera.IViewMat * vec4(normalVS, 0.0)).xyz);
    vec3 towardLight = normalize(-LightDirection);
    float visibility = voxelshadowVisibility(worldPos + normalWS * 0.015, worldPos + towardLight * 96.0);
    diffuse *= visibility;

    float reflectivity = 0.05;
    vec3 diffuseColor = diffuse * LightColor;
    fragColor = vec4(albedoColor.rgb * diffuseColor * (1.0 - reflectivity) + diffuseColor * reflectivity, 1.0);
}
