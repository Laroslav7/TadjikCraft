package com.roflang.tadjikcraft;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.HashSet;

public class TadjikCraftGame extends ApplicationAdapter {

    PerspectiveCamera camera;
    ShaderProgram shader;
    Mesh platformMesh;
    Texture dirt;
    Texture hand;

    SpriteBatch batch;

    float yaw = -90f;
    float pitch = 0f;

    float yVelocity = 0;
    boolean onGround = false;

    static final int PLATFORM_SIZE = 64;
    static final float PLAYER_HEIGHT = 1.8f;

    HashSet<Vector3> blocks = new HashSet<>();

    @Override
    public void create() {

        camera = new PerspectiveCamera(70,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight());

        camera.position.set(10, 3f, 10);
        camera.near = 0.1f;
        camera.far = 300f;

        ShaderProgram.pedantic = false;

        shader = new ShaderProgram(
            Gdx.files.internal("default.vert"),
            Gdx.files.internal("default.frag")
        );

        dirt = new Texture("dirt.png");
        hand = new Texture("steve-hand.png");

        batch = new SpriteBatch();

        buildPlatform();

        Gdx.input.setCursorCatched(true);
    }

    private void buildPlatform() {

        float[] vertices = new float[PLATFORM_SIZE * PLATFORM_SIZE * 6 * 5];
        int idx = 0;

        for(int x=0;x<PLATFORM_SIZE;x++){
            for(int z=0;z<PLATFORM_SIZE;z++){

                float x0=x, z0=z;
                float x1=x+1, z1=z+1;
                float y=0;

                float[] quad={
                    x0,y,z0,0,0,
                    x1,y,z0,1,0,
                    x1,y,z1,1,1,

                    x0,y,z0,0,0,
                    x1,y,z1,1,1,
                    x0,y,z1,0,1
                };

                System.arraycopy(quad,0,vertices,idx,quad.length);
                idx+=quad.length;
            }
        }

        platformMesh = new Mesh(true,
            vertices.length/5,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_texCoord0")
        );

        platformMesh.setVertices(vertices);
    }

    @Override
    public void render() {

        float dt = Gdx.graphics.getDeltaTime();

        handleMouse();
        handleMovement(dt);
        applyGravity(dt);
        handleBlocks();

        Gdx.gl.glClearColor(0.5f,0.7f,1f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.update();

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        dirt.bind();
        shader.setUniformi("u_texture",0);

        platformMesh.render(shader,GL20.GL_TRIANGLES);

        drawBlocks();

        drawHand();
    }

    private void handleMouse(){

        float sens=0.15f;

        yaw += -Gdx.input.getDeltaX()*sens;
        pitch += -Gdx.input.getDeltaY()*sens;

        pitch=MathUtils.clamp(pitch,-89f,89f);

        Vector3 dir=new Vector3();
        dir.x=MathUtils.cosDeg(yaw)*MathUtils.cosDeg(pitch);
        dir.y=MathUtils.sinDeg(pitch);
        dir.z=MathUtils.sinDeg(yaw)*MathUtils.cosDeg(pitch);

        camera.direction.set(dir.nor());
        camera.up.set(Vector3.Y);
    }

    private void handleMovement(float dt){

        float speed=8f*dt;

        Vector3 forward=new Vector3(camera.direction.x,0,camera.direction.z).nor();
        Vector3 right=new Vector3(forward).crs(Vector3.Y).nor();

        if(Gdx.input.isKeyPressed(Input.Keys.W))
            camera.position.add(forward.scl(speed));

        if(Gdx.input.isKeyPressed(Input.Keys.S))
            camera.position.sub(forward.scl(speed));

        if(Gdx.input.isKeyPressed(Input.Keys.A))
            camera.position.sub(right.scl(speed));

        if(Gdx.input.isKeyPressed(Input.Keys.D))
            camera.position.add(right.scl(speed));

        if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && onGround){
            yVelocity=6f;
            onGround=false;
        }
    }

    private void applyGravity(float dt){

        yVelocity-=20f*dt;
        camera.position.y+=yVelocity*dt;

        if(camera.position.y<=PLAYER_HEIGHT){
            camera.position.y=PLAYER_HEIGHT;
            yVelocity=0;
            onGround=true;
        }
    }

    private void handleBlocks(){

        if(Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)){
            Vector3 pos=new Vector3(
                MathUtils.floor(camera.position.x+camera.direction.x*2),
                1,
                MathUtils.floor(camera.position.z+camera.direction.z*2)
            );
            blocks.add(pos);
        }

        if(Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)){
            Vector3 pos=new Vector3(
                MathUtils.floor(camera.position.x+camera.direction.x*2),
                1,
                MathUtils.floor(camera.position.z+camera.direction.z*2)
            );
            blocks.remove(pos);
        }
    }

    private void drawBlocks(){

        for(Vector3 b:blocks){

            float x=b.x;
            float y=b.y;
            float z=b.z;

            float[] cube={
                x,y,z,0,0,
                x+1,y,z,1,0,
                x+1,y+1,z,1,1,

                x,y,z,0,0,
                x+1,y+1,z,1,1,
                x,y+1,z,0,1
            };

            Mesh m=new Mesh(true,6,0,
                new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,2,"a_texCoord0")
            );

            m.setVertices(cube);
            m.render(shader,GL20.GL_TRIANGLES);
            m.dispose();
        }
    }

    private void drawHand(){

        batch.begin();
        batch.draw(hand,
            Gdx.graphics.getWidth()-250,
            -40,
            300,
            300);
        batch.end();
    }

    @Override
    public void dispose(){
        platformMesh.dispose();
        shader.dispose();
        dirt.dispose();
        hand.dispose();
        batch.dispose();
    }
}
