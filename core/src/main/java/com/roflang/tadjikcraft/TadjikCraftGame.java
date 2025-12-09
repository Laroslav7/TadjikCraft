package com.roflang.tadjikcraft;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;

public class TadjikCraftGame extends ApplicationAdapter {

    enum GameState { MENU, GAME, DEAD, PAUSE }
    GameState state = GameState.MENU;

    PerspectiveCamera camera;
    ModelBatch batch;

    Model cubeModel;
    Texture[] blockTextures = new Texture[5];
    int selectedBlock = 0;

    ArrayList<ModelInstance> blocks = new ArrayList<>();

    float speed = 5f;
    float camHeight = 1.7f;

    float yVel = 0;
    float gravity = -15f;
    boolean onGround = false;

    ShapeRenderer shape;

    @Override
    public void create() {
        batch = new ModelBatch();
        shape = new ShapeRenderer();

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, camHeight, 5);
        camera.near = 0.1f;
        camera.far = 200;
        camera.lookAt(0, camHeight, 0);
        camera.update();

        Gdx.input.setCursorCatched(false);

        // 5 блоков (по умолчанию можно делать один и тот же)
        blockTextures[0] = new Texture("dirt.png");
        blockTextures[1] = new Texture("dirt.png");
        blockTextures[2] = new Texture("dirt.png");
        blockTextures[3] = new Texture("dirt.png");
        blockTextures[4] = new Texture("dirt.png");

        Material mat = new Material(TextureAttribute.createDiffuse(blockTextures[0]));

        ModelBuilder mb = new ModelBuilder();
        cubeModel = mb.createBox(
            1, 1, 1,
            mat,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );

        // платформа
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                blocks.add(new ModelInstance(cubeModel, x, 0, z));
            }
        }
    }

    @Override
    public void render() {

        // ===== МЕНЮ =====
        if (state == GameState.MENU) {
            Gdx.gl.glClearColor(0,0,0,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.graphics.setTitle("PRESS ENTER TO START — TadjikCraft");
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                state = GameState.GAME;
                Gdx.input.setCursorCatched(true);
            }
            return;
        }

        // ===== СМЕРТЬ =====
        if (state == GameState.DEAD) {
            Gdx.gl.glClearColor(0.2f,0,0,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.graphics.setTitle("YOU DIED — PRESS R TO RESPAWN");

            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) respawn();
            return;
        }

        // ===== ПАУЗА =====
        if (state == GameState.PAUSE) {
            Gdx.gl.glClearColor(0,0,0,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.graphics.setTitle("PAUSE — ENTER=RESUME | Q=QUIT");

            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                state = GameState.GAME;
                Gdx.input.setCursorCatched(true);
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) Gdx.app.exit();
            return;
        }

        // ===== ГЕЙМПЛЕЙ =====

        float dt = Gdx.graphics.getDeltaTime();

        handleInput(dt);
        applyGravity(dt);

        if (camera.position.y < -10) {
            state = GameState.DEAD;
            Gdx.input.setCursorCatched(false);
        }

        camera.update();

        Gdx.gl.glClearColor(0.5f, 0.7f, 1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // рендер блоков
        batch.begin(camera);
        for (ModelInstance block : blocks)
            batch.render(block);
        batch.end();

        // прицел
        drawCrosshair();

        // ломание + установка
        blockRaycast();
    }

    private void handleInput(float dt) {

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            state = GameState.PAUSE;
            Gdx.input.setCursorCatched(false);
        }

        // выбор блока 1—5
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedBlock = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectedBlock = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedBlock = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) selectedBlock = 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) selectedBlock = 4;

        float move = speed * dt;

        // мышь
        float mx = -Gdx.input.getDeltaX() * 0.15f;
        float my = -Gdx.input.getDeltaY() * 0.15f;

        camera.direction.rotate(Vector3.Y, mx);
        Vector3 pitchAxis = new Vector3(camera.direction).crs(Vector3.Y).nor();
        camera.direction.rotate(pitchAxis, my);

        Vector3 forward = new Vector3(camera.direction.x,0,camera.direction.z).nor();
        Vector3 right   = new Vector3(forward).crs(Vector3.Y).nor();

        Vector3 newPos = new Vector3(camera.position);

        if (Gdx.input.isKeyPressed(Input.Keys.W)) newPos.mulAdd(forward, move);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) newPos.mulAdd(forward, -move);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) newPos.mulAdd(right, -move);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) newPos.mulAdd(right, move);

        newPos.y = camera.position.y;

        camera.position.set(newPos);
    }

    private void applyGravity(float dt) {
        yVel += gravity * dt;
        camera.position.y += yVel * dt;

        if (camera.position.y <= camHeight) {
            camera.position.y = camHeight;
            yVel = 0;
            onGround = true;
        } else {
            onGround = false;
        }

        if (onGround && Gdx.input.isKeyJustPressed(Input.Keys.SPACE))
            yVel = 7f;
    }

    private void respawn() {
        camera.position.set(0, camHeight, 5);
        yVel = 0;
        state = GameState.GAME;
        Gdx.input.setCursorCatched(true);
    }

    private void drawCrosshair() {
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(Color.WHITE);

        float cx = Gdx.graphics.getWidth() / 2f;
        float cy = Gdx.graphics.getHeight() / 2f;

        shape.line(cx - 10, cy, cx + 10, cy);
        shape.line(cx, cy - 10, cx, cy + 10);

        shape.end();
    }

    // Raycast ломание и установка
    private void blockRaycast() {
        Vector3 origin = new Vector3(camera.position);
        Vector3 dir = new Vector3(camera.direction).nor();

        float max = 4f;
        ModelInstance hit = null;
        float best = 999;

        for (ModelInstance m : blocks) {
            Vector3 p = new Vector3();
            m.transform.getTranslation(p);
            float d = origin.dst(p);

            if (d < best && d < max) {
                best = d;
                hit = m;
            }
        }

        if (hit == null) return;

        // ЛОМАНИЕ
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            blocks.remove(hit);
        }

        // ПОСТАНОВКА
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            Vector3 pos = new Vector3();
            hit.transform.getTranslation(pos);

            Vector3 raw = pos.add(dir.scl(1.1f));

            Vector3 place = new Vector3(
                (int)Math.floor(raw.x),
                (int)Math.floor(raw.y),
                (int)Math.floor(raw.z)
            );


            ModelInstance newBlock = new ModelInstance(cubeModel, place);
            blocks.add(newBlock);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        cubeModel.dispose();
        for (Texture t : blockTextures) t.dispose();
        shape.dispose();
    }
}
