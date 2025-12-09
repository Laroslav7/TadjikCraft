package com.roflang.tadjikcraft;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        final Vector3 normal;
        final float distance;

        RaycastHit(BlockPos pos, Vector3 normal, float distance) {
            this.pos = pos;
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

    Map<BlockPos, ModelInstance> visibleBlocks = new HashMap<>();
    ArrayList<ModelInstance> visibleList = new ArrayList<>();

    HashSet<BlockPos> extraBlocks = new HashSet<>();
    HashSet<BlockPos> removedBaseBlocks = new HashSet<>();

    int viewDistance = 72;
    int lastViewCenterX = Integer.MIN_VALUE;
    int lastViewCenterZ = Integer.MIN_VALUE;

    static final int PLATFORM_START = 0;
    static final int PLATFORM_END = 511;

    float speed = 5f;
    float camHeight = 1.7f;

    float yVel = 0;
    float gravity = -15f;
    boolean onGround = false;

    ShapeRenderer shape;
    SpriteBatch spriteBatch;
    BitmapFont font;

    @Override
    public void create() {
        batch = new ModelBatch();
        shape = new ShapeRenderer();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();

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

        camera.position.set((PLATFORM_END - PLATFORM_START) / 2f, camHeight + 0.1f, PLATFORM_END / 2f + 5);

        refreshVisibleBlocks(true);
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

        refreshVisibleBlocks(false);

        // рендер блоков
        batch.begin(camera);
        for (ModelInstance block : visibleList)
            batch.render(block);
        batch.end();

        // прицел
        drawCrosshair();

        // ломание + установка
        blockRaycast();

        // GUI
        drawHud();
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
        RaycastHit hit = findBlockHit(5f);
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
            addBlockAt(target.x, target.y, target.z);
        }
    }

    private void addBlockAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (isBlockPresent(pos)) return;
        if (isBaseBlock(pos)) {
            removedBaseBlocks.remove(pos);
        } else {
            extraBlocks.add(pos);
        }

        if (isWithinView(pos)) {
            ensureVisible(pos);
        }
    }

    private void removeBlock(BlockPos pos) {
        if (extraBlocks.remove(pos)) {
            // removed extra block
        } else if (isBaseBlock(pos)) {
            removedBaseBlocks.add(pos);
        }
        ModelInstance inst = visibleBlocks.remove(pos);
        if (inst != null) {
            visibleList.remove(inst);
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
                    if (!isBlockPresent(pos)) continue;

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
            if (isBlockPresent(new BlockPos(bx, y, bz))) {
                return y + 0.5f;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    private RaycastHit findBlockHit(float maxDistance) {
        Vector3 origin = new Vector3(camera.position);
        Vector3 dir = new Vector3(camera.direction).nor();

        int bx = MathUtils.floor(origin.x + 0.5f);
        int by = MathUtils.floor(origin.y + 0.5f);
        int bz = MathUtils.floor(origin.z + 0.5f);

        int stepX = dir.x > 0 ? 1 : dir.x < 0 ? -1 : 0;
        int stepY = dir.y > 0 ? 1 : dir.y < 0 ? -1 : 0;
        int stepZ = dir.z > 0 ? 1 : dir.z < 0 ? -1 : 0;

        float tMaxX = stepX == 0 ? Float.POSITIVE_INFINITY : ((bx + (stepX > 0 ? 0.5f : -0.5f)) - origin.x) / dir.x;
        float tMaxY = stepY == 0 ? Float.POSITIVE_INFINITY : ((by + (stepY > 0 ? 0.5f : -0.5f)) - origin.y) / dir.y;
        float tMaxZ = stepZ == 0 ? Float.POSITIVE_INFINITY : ((bz + (stepZ > 0 ? 0.5f : -0.5f)) - origin.z) / dir.z;

        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : 1f / Math.abs(dir.x);
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : 1f / Math.abs(dir.y);
        float tDeltaZ = stepZ == 0 ? Float.POSITIVE_INFINITY : 1f / Math.abs(dir.z);

        Vector3 normal = new Vector3();
        float distance = 0f;

        for (int i = 0; i < 200 && distance <= maxDistance; i++) {
            BlockPos pos = new BlockPos(bx, by, bz);
            if (isBlockPresent(pos)) {
                return new RaycastHit(pos, new Vector3(normal), distance);
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                bx += stepX;
                distance = tMaxX;
                tMaxX += tDeltaX;
                normal.set(-stepX, 0, 0);
            } else if (tMaxY < tMaxZ) {
                by += stepY;
                distance = tMaxY;
                tMaxY += tDeltaY;
                normal.set(0, -stepY, 0);
            } else {
                bz += stepZ;
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                normal.set(0, 0, -stepZ);
            }
        }

        return null;
    }

    @Override
    public void dispose() {
        batch.dispose();
        cubeModel.dispose();
        for (Texture t : blockTextures) t.dispose();
        shape.dispose();
        spriteBatch.dispose();
        font.dispose();
    }

    private boolean isBaseBlock(BlockPos pos) {
        return pos.y == 0 && pos.x >= PLATFORM_START && pos.x <= PLATFORM_END && pos.z >= PLATFORM_START && pos.z <= PLATFORM_END;
    }

    private boolean isBlockPresent(BlockPos pos) {
        if (extraBlocks.contains(pos)) return true;
        if (isBaseBlock(pos) && !removedBaseBlocks.contains(pos)) return true;
        return false;
    }

    private boolean isWithinView(BlockPos pos) {
        float dx = pos.x + 0.5f - camera.position.x;
        float dz = pos.z + 0.5f - camera.position.z;
        return dx * dx + dz * dz <= viewDistance * viewDistance;
    }

    private void ensureVisible(BlockPos pos) {
        if (visibleBlocks.containsKey(pos)) return;
        ModelInstance inst = new ModelInstance(cubeModel, pos.x, pos.y, pos.z);
        visibleBlocks.put(pos, inst);
        visibleList.add(inst);
    }

    private void refreshVisibleBlocks(boolean force) {
        int cx = MathUtils.floor(camera.position.x + 0.5f);
        int cz = MathUtils.floor(camera.position.z + 0.5f);

        if (!force && cx == lastViewCenterX && cz == lastViewCenterZ) return;

        lastViewCenterX = cx;
        lastViewCenterZ = cz;

        ArrayList<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : visibleBlocks.keySet()) {
            if (!isWithinView(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : toRemove) {
            ModelInstance inst = visibleBlocks.remove(pos);
            if (inst != null) visibleList.remove(inst);
        }

        int minX = cx - viewDistance;
        int maxX = cx + viewDistance;
        int minZ = cz - viewDistance;
        int maxZ = cz + viewDistance;

        for (int x = minX; x <= maxX; x++) {
            if (x < PLATFORM_START || x > PLATFORM_END) continue;
            for (int z = minZ; z <= maxZ; z++) {
                if (z < PLATFORM_START || z > PLATFORM_END) continue;
                BlockPos pos = new BlockPos(x, 0, z);
                if (removedBaseBlocks.contains(pos)) continue;
                ensureVisible(pos);
            }
        }

        for (BlockPos pos : extraBlocks) {
            if (isWithinView(pos)) {
                ensureVisible(pos);
            }
        }
    }

    private void drawHud() {
        spriteBatch.begin();
        String blockStr = "Блок: " + (selectedBlock + 1);
        String fps = "FPS: " + Gdx.graphics.getFramesPerSecond();
        spriteBatch.setColor(Color.WHITE);
        font.draw(spriteBatch, fps, 10, Gdx.graphics.getHeight() - 10);
        font.draw(spriteBatch, blockStr, 10, Gdx.graphics.getHeight() - 30);
        font.draw(spriteBatch, "WASD — движение | ЛКМ ломать | ПКМ ставить | ESC пауза", 10, 40);
        spriteBatch.end();
    }
}
