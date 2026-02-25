#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_tex;
uniform sampler2D u_texture;
uniform vec4 u_tint;

void main(){
    gl_FragColor = texture2D(u_texture, v_tex) * u_tint;
}
