
attribute vec4 vPosition;

attribute vec4 vCoord;

uniform mat4 vMatrix;

varying vec2 aCoord;

void main() {
    gl_Position = vPosition;
    aCoord = (vMatrix * vec4(vCoord.x, vCoord.y, 1.0, 1.0)).xy;
}
