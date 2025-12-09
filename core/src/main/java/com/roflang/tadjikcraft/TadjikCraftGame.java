package com.roflang.tadjikcraft;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TadjikCraftGame extends ApplicationAdapter {

    private static class BlockPos {
        final int x;
        final int y;
        final int z;

        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos blockPos = (BlockPos) o;
            return x == blockPos.x && y == blockPos.y && z == blockPos.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static class RaycastHit {
        final BlockPos pos;
        final ModelInstance instance;
        final Vector3 normal;
        final float distance;

        RaycastHit(BlockPos pos, ModelInstance instance, Vector3 normal, float distance) {
            this.pos = pos;
            this.instance = instance;
            this.normal = normal;
            this.distance = distance;
        }
    }

    enum GameState { MENU, GAME, DEAD, PAUSE }
    GameState state = GameState.MENU;

    PerspectiveCamera camera;
    ModelBatch batch;

    Model cubeModel;
    Texture[] blockTextures = new Texture[5];
    int selectedBlock = 0;

    ArrayList<ModelInstance> blocks = new ArrayList<>();
    Map<BlockPos, ModelInstance> blockLookup = new HashMap<>();

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
        camera.far = 800;
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

        // платформа 512x512
        int start = 0;
        int end = 511;
        for (int x = start; x <= end; x++) {
            for (int z = start; z <= end; z++) {
                addBlockAt(x, 0, z);
            }
        }

        camera.position.set((end - start) / 2f, camHeight + 0.1f, end / 2f + 5);
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

        if (!isCollidingWithBlock(newPos)) {
            camera.position.set(newPos);
        }
    }

    private void applyGravity(float dt) {
        float groundHeight = findGroundHeight(camera.position);
        if (groundHeight != Float.NEGATIVE_INFINITY) {
            if (camera.position.y < groundHeight + camHeight) {
                camera.position.y = groundHeight + camHeight;
            }
            onGround = true;
        } else {
            onGround = false;
        }

        // Гравитация временно отключена, чтобы игрок не падал с платформы
        yVel = 0f;
    }

    private void respawn() {
        camera.position.set(256, camHeight + 0.1f, 261);
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
        RaycastHit hit = findBlockHit(4f);
        if (hit == null) return;

        // ЛОМАНИЕ
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            removeBlock(hit.pos);
        }

        // ПОСТАНОВКА
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            BlockPos target = new BlockPos(
                hit.pos.x + Math.round(hit.normal.x),
                hit.pos.y + Math.round(hit.normal.y),
                hit.pos.z + Math.round(hit.normal.z)
            );
            if (!blockLookup.containsKey(target)) {
                addBlockAt(target.x, target.y, target.z);
            }
        }
    }

    private void addBlockAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (blockLookup.containsKey(pos)) return;
        ModelInstance inst = new ModelInstance(cubeModel, x, y, z);
        blockLookup.put(pos, inst);
        blocks.add(inst);
    }

    private void removeBlock(BlockPos pos) {
        ModelInstance inst = blockLookup.remove(pos);
        if (inst != null) {
            blocks.remove(inst);
        }
    }

    private boolean isCollidingWithBlock(Vector3 desiredPos) {
        float radius = 0.3f;
        float feetY = desiredPos.y - camHeight;
        float headY = desiredPos.y;

        int minX = MathUtils.floor(desiredPos.x - radius - 0.5f);
        int maxX = MathUtils.ceil(desiredPos.x + radius - 0.5f);
        int minZ = MathUtils.floor(desiredPos.z - radius - 0.5f);
        int maxZ = MathUtils.ceil(desiredPos.z + radius - 0.5f);
        int minY = MathUtils.floor(feetY - 0.1f);
        int maxY = MathUtils.ceil(headY + 0.1f);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!blockLookup.containsKey(pos)) continue;

                    float blockMinX = x - 0.5f;
                    float blockMaxX = x + 0.5f;
                    float blockMinY = y - 0.5f;
                    float blockMaxY = y + 0.5f;
                    float blockMinZ = z - 0.5f;
                    float blockMaxZ = z + 0.5f;

                    boolean overlapX = desiredPos.x + radius > blockMinX && desiredPos.x - radius < blockMaxX;
                    boolean overlapZ = desiredPos.z + radius > blockMinZ && desiredPos.z - radius < blockMaxZ;
                    boolean overlapY = headY > blockMinY && feetY < blockMaxY;

                    if (overlapX && overlapY && overlapZ) return true;
                }
            }
        }

        return false;
    }

    private float findGroundHeight(Vector3 pos) {
        int bx = (int)Math.floor(pos.x + 0.5f);
        int bz = (int)Math.floor(pos.z + 0.5f);
        int maxY = (int)Math.floor(pos.y);

        for (int y = maxY; y >= -64; y--) {
            if (blockLookup.containsKey(new BlockPos(bx, y, bz))) {
                return y + 0.5f;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    private RaycastHit findBlockHit(float maxDistance) {
        Vector3 origin = new Vector3(camera.position);
        Vector3 dir = new Vector3(camera.direction).nor();

        RaycastHit bestHit = null;

        for (Map.Entry<BlockPos, ModelInstance> entry : blockLookup.entrySet()) {
            BlockPos pos = entry.getKey();
            Vector3 min = new Vector3(pos.x - 0.5f, pos.y - 0.5f, pos.z - 0.5f);
            Vector3 max = new Vector3(pos.x + 0.5f, pos.y + 0.5f, pos.z + 0.5f);

            float[] tRange = new float[]{0f, maxDistance};
            Vector3 hitNormal = new Vector3();

            if (!intersectAxis(origin.x, dir.x, min.x, max.x, hitNormal, 1, tRange)) continue;
            if (!intersectAxis(origin.y, dir.y, min.y, max.y, hitNormal, 2, tRange)) continue;
            if (!intersectAxis(origin.z, dir.z, min.z, max.z, hitNormal, 3, tRange)) continue;

            float tMin = tRange[0];

            if (tMin < 0 || tMin > maxDistance) continue;

            if (bestHit == null || tMin < bestHit.distance) {
                bestHit = new RaycastHit(pos, entry.getValue(), new Vector3(hitNormal), tMin);
            }
        }

        return bestHit;
    }

    private boolean intersectAxis(float origin, float dir, float min, float max, Vector3 normal, int axis, float[] tRange) {
        if (Math.abs(dir) < 0.0001f) {
            return origin >= min && origin <= max;
        }

        float invD = 1f / dir;
        float t0 = (min - origin) * invD;
        float t1 = (max - origin) * invD;

        int normalDir = invD >= 0 ? -1 : 1;

        if (t0 > t1) {
            float tmp = t0;
            t0 = t1;
            t1 = tmp;
            normalDir = -normalDir;
        }

        if (t0 > tRange[0]) {
            tRange[0] = t0;
            normal.set(0, 0, 0);
            if (axis == 1) normal.x = normalDir;
            if (axis == 2) normal.y = normalDir;
            if (axis == 3) normal.z = normalDir;
        }

        if (t1 < tRange[1]) {
            tRange[1] = t1;
        }

        return tRange[1] >= tRange[0];
    }

    @Override
    public void dispose() {
        batch.dispose();
        cubeModel.dispose();
        for (Texture t : blockTextures) t.dispose();
        shape.dispose();
    }
}
