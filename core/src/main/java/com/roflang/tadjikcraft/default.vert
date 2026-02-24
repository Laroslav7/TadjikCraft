attribute vec3 a_position;
uniform mat4 u_projViewTrans;

void main(){
    gl_Position = u_projViewTrans * vec4(a_position,1.0);
}
