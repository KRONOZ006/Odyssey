#version 150
in vec2 texCoord;
out vec4 fragColor;

uniform float u_time;


void main() {
    vec2 uv = texCoord;






        float pulse = abs(sin(u_time));


           fragColor = vec4(texCoord.y + pulse, texCoord.x + pulse, 0.1 * pulse, 1.0);
    }