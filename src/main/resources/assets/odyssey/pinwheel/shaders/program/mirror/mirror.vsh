// vertex shader
#version 450
layout(location=0) in vec3 Position; // quad positions
out vec3 vPos;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    vPos = Position; // pass to fragment shader
    gl_Position = ProjMat * ModelViewMat * vec4(Position,1.0);
}
