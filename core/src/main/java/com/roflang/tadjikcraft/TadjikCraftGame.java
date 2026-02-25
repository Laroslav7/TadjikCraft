package com.roflang.tadjikcraft;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.LinkedHashMap;
import java.util.Map;

public class TadjikCraftGame extends ApplicationAdapter {

    private static final int WORLD_SIZE = 64;
    private static final int MAX_BUILD_HEIGHT = 24;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_EYE_OFFSET = 1.62f;
    private static final float GRAVITY = 20f;
    private static final float JUMP_SPEED = 7f;
    private static final float BLOCK_REACH = 6f;

    private PerspectiveCamera camera;
    private ShaderProgram shader;
    private Mesh cubeMesh;
    private Texture terrainTexture;
    private Texture hand;

    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    private final Matrix4 modelMatrix = new Matrix4();
    private final Vector3 tmp = new Vector3();

    private float yaw = -90f;
    private float pitch = 0f;
    private float yVelocity = 0f;
    private boolean onGround = false;
    private boolean flyMode = false;
    private float dayTime = 0f;

    private BlockType selectedBlock = BlockType.GRASS;

    private final LinkedHashMap<BlockPos, BlockType> worldBlocks = new LinkedHashMap<>();

    @Override
    public void create() {
        camera = new PerspectiveCamera(75f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(WORLD_SIZE / 2f, 18f, WORLD_SIZE / 2f);
        camera.near = 0.1f;
        camera.far = 420f;

        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(Gdx.files.internal("default.vert"), Gdx.files.internal("default.frag"));

        terrainTexture = new Texture("dirt.png");
        hand = new Texture("steve-hand.png");

        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();

        cubeMesh = createCubeMesh();
        generateWorld();

        Gdx.input.setCursorCatched(true);
    }

    private void generateWorld() {
        worldBlocks.clear();
        for (int x = 0; x < WORLD_SIZE; x++) {
            for (int z = 0; z < WORLD_SIZE; z++) {
                float terrainNoise = MathUtils.sin(x * 0.18f) * 2.7f + MathUtils.cos(z * 0.16f) * 2.4f + MathUtils.sin((x + z) * 0.08f) * 3.2f;
                int height = MathUtils.clamp(6 + MathUtils.floor(terrainNoise), 2, 14);

                float biomeNoise = MathUtils.sin(x * 0.045f) + MathUtils.cos(z * 0.055f);
                BlockType surface = biomeNoise > 0.75f ? BlockType.SNOW : biomeNoise < -0.75f ? BlockType.SAND : BlockType.GRASS;

                for (int y = 0; y <= height; y++) {
                    BlockType type;
                    if (y == height) {
                        type = surface;
                    } else if (y > height - 3) {
                        type = BlockType.DIRT;
                    } else {
                        type = BlockType.STONE;
                    }
                    worldBlocks.put(new BlockPos(x, y, z), type);
                }

                if (surface == BlockType.GRASS && MathUtils.randomBoolean(0.02f) && height + 5 < MAX_BUILD_HEIGHT) {
                    spawnTree(x, height + 1, z);
                }
            }
        }
    }

    private void spawnTree(int baseX, int baseY, int baseZ) {
        int trunkHeight = MathUtils.random(3, 5);
        for (int i = 0; i < trunkHeight; i++) {
            worldBlocks.put(new BlockPos(baseX, baseY + i, baseZ), BlockType.WOOD);
        }

        int top = baseY + trunkHeight;
        for (int ox = -2; ox <= 2; ox++) {
            for (int oy = -1; oy <= 2; oy++) {
                for (int oz = -2; oz <= 2; oz++) {
                    int dist = Math.abs(ox) + Math.abs(oz) + Math.abs(oy);
                    if (dist <= 4) {
                        worldBlocks.put(new BlockPos(baseX + ox, top + oy, baseZ + oz), BlockType.LEAVES);
                    }
                }
            }
        }
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        dayTime = (dayTime + dt * 0.03f) % 1f;

        handleMouse();
        handleModeSwitch();
        handleMovement(dt);
        if (!flyMode) {
            applyGravity(dt);
        }
        handleBlockSelection();
        handleBlocks();

        float dayLight = 0.3f + 0.7f * MathUtils.sin(dayTime * MathUtils.PI2) * 0.5f + 0.35f;
        float skyR = 0.08f + dayLight * 0.45f;
        float skyG = 0.12f + dayLight * 0.55f;
        float skyB = 0.22f + dayLight * 0.65f;

        Gdx.gl.glClearColor(skyR, skyG, skyB, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.update();

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        terrainTexture.bind();
        shader.setUniformi("u_texture", 0);

        drawWorld(dayLight);
        drawHud(dayLight);
    }

    private void drawWorld(float dayLight) {
        for (Map.Entry<BlockPos, BlockType> block : worldBlocks.entrySet()) {
            BlockPos pos = block.getKey();
            BlockType type = block.getValue();

            if (isCompletelyHidden(pos)) {
                continue;
            }

            modelMatrix.idt().translate(pos.x, pos.y, pos.z);
            shader.setUniformMatrix("u_model", modelMatrix);

            Color tint = type.tint;
            shader.setUniformf("u_tint", tint.r * dayLight, tint.g * dayLight, tint.b * dayLight, 1f);
            cubeMesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private boolean isCompletelyHidden(BlockPos pos) {
        return worldBlocks.containsKey(new BlockPos(pos.x + 1, pos.y, pos.z))
            && worldBlocks.containsKey(new BlockPos(pos.x - 1, pos.y, pos.z))
            && worldBlocks.containsKey(new BlockPos(pos.x, pos.y + 1, pos.z))
            && worldBlocks.containsKey(new BlockPos(pos.x, pos.y - 1, pos.z))
            && worldBlocks.containsKey(new BlockPos(pos.x, pos.y, pos.z + 1))
            && worldBlocks.containsKey(new BlockPos(pos.x, pos.y, pos.z - 1));
    }

    private void drawHud(float dayLight) {
        batch.begin();
        font.draw(batch,
            "WASD move | SPACE jump | SHIFT sprint | F fly: " + (flyMode ? "ON" : "OFF") + " | 1-7 block type",
            12,
            Gdx.graphics.getHeight() - 12);
        font.draw(batch,
            "Selected: " + selectedBlock.name() + " | Blocks: " + worldBlocks.size() + " | Daylight: " + MathUtils.round(dayLight * 100f) + "%",
            12,
            Gdx.graphics.getHeight() - 34);
        batch.draw(hand, Gdx.graphics.getWidth() - 230, -42, 280, 280);
        batch.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 1f, 1f, 0.95f);
        float cx = Gdx.graphics.getWidth() / 2f;
        float cy = Gdx.graphics.getHeight() / 2f;
        shapeRenderer.rect(cx - 1f, cy - 8f, 2f, 16f);
        shapeRenderer.rect(cx - 8f, cy - 1f, 16f, 2f);
        shapeRenderer.end();
    }

    private void handleModeSwitch() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            flyMode = !flyMode;
            yVelocity = 0f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
        }
    }

    private void handleMouse() {
        if (!Gdx.input.isCursorCatched()) {
            return;
        }

        float sensitivity = 0.15f;
        yaw -= Gdx.input.getDeltaX() * sensitivity;
        pitch -= Gdx.input.getDeltaY() * sensitivity;
        pitch = MathUtils.clamp(pitch, -89f, 89f);

        tmp.set(
            MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch),
            MathUtils.sinDeg(pitch),
            MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch)
        ).nor();

        camera.direction.set(tmp);
        camera.up.set(Vector3.Y);
    }

    private void handleMovement(float dt) {
        float speed = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ? 11f : 7.5f) * dt;
        Vector3 forward = new Vector3(camera.direction.x, 0f, camera.direction.z).nor();
        Vector3 right = new Vector3(forward).crs(Vector3.Y).nor();

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.mulAdd(forward, speed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.mulAdd(forward, -speed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.mulAdd(right, -speed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.mulAdd(right, speed);
        }

        if (flyMode) {
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
                camera.position.y += speed * 1.4f;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
                camera.position.y -= speed * 1.4f;
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && onGround) {
            yVelocity = JUMP_SPEED;
            onGround = false;
        }
    }

    private void applyGravity(float dt) {
        yVelocity -= GRAVITY * dt;
        camera.position.y += yVelocity * dt;

        int feetX = MathUtils.floor(camera.position.x);
        int feetZ = MathUtils.floor(camera.position.z);
        int groundY = getTopSolidBlockY(feetX, feetZ);
        float minY = groundY + PLAYER_HEIGHT;

        if (camera.position.y <= minY) {
            camera.position.y = minY;
            yVelocity = 0f;
            onGround = true;
        } else {
            onGround = false;
        }
    }

    private int getTopSolidBlockY(int x, int z) {
        for (int y = MAX_BUILD_HEIGHT; y >= 0; y--) {
            if (worldBlocks.containsKey(new BlockPos(x, y, z))) {
                return y + 1;
            }
        }
        return 0;
    }

    private void handleBlockSelection() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedBlock = BlockType.GRASS;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectedBlock = BlockType.DIRT;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedBlock = BlockType.STONE;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) selectedBlock = BlockType.SAND;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) selectedBlock = BlockType.WOOD;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6)) selectedBlock = BlockType.LEAVES;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7)) selectedBlock = BlockType.SNOW;
    }

    private void handleBlocks() {
        BlockPos target = getTargetedBlock(true);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && target != null) {
            worldBlocks.remove(target);
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            BlockPos placePos = getTargetedBlock(false);
            if (placePos != null && !intersectsPlayer(placePos)) {
                worldBlocks.put(placePos, selectedBlock);
            }
        }
    }

    private BlockPos getTargetedBlock(boolean solidBlock) {
        Vector3 origin = new Vector3(camera.position.x, camera.position.y - (PLAYER_HEIGHT - PLAYER_EYE_OFFSET), camera.position.z);
        Vector3 dir = new Vector3(camera.direction).nor();

        for (float d = 0.2f; d <= BLOCK_REACH; d += 0.1f) {
            int x = MathUtils.floor(origin.x + dir.x * d);
            int y = MathUtils.floor(origin.y + dir.y * d);
            int z = MathUtils.floor(origin.z + dir.z * d);
            BlockPos pos = new BlockPos(x, y, z);
            boolean occupied = worldBlocks.containsKey(pos);
            if (solidBlock && occupied) {
                return pos;
            }
            if (!solidBlock && !occupied && d > 0.25f) {
                BlockPos prev = new BlockPos(
                    MathUtils.floor(origin.x + dir.x * (d - 0.12f)),
                    MathUtils.floor(origin.y + dir.y * (d - 0.12f)),
                    MathUtils.floor(origin.z + dir.z * (d - 0.12f))
                );
                if (worldBlocks.containsKey(prev)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private boolean intersectsPlayer(BlockPos p) {
        float px = camera.position.x;
        float py = camera.position.y;
        float pz = camera.position.z;
        return px > p.x - 0.25f && px < p.x + 1.25f
            && pz > p.z - 0.25f && pz < p.z + 1.25f
            && py > p.y - 0.05f && py < p.y + 2.1f;
    }

    private Mesh createCubeMesh() {
        float[] vertices = {
            // Front
            0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 1, 1, 1,
            0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 0, 1, 1, 0, 1,
            // Back
            1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 1,
            1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1,
            // Left
            0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1,
            0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 1, 1, 0, 1,
            // Right
            1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 1,
            1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1,
            // Top
            0, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1, 0, 1, 1,
            0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1,
            // Bottom
            0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 1,
            0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 0, 1
        };

        Mesh mesh = new Mesh(true, vertices.length / 5, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        );
        mesh.setVertices(vertices);
        return mesh;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        cubeMesh.dispose();
        shader.dispose();
        terrainTexture.dispose();
        hand.dispose();
        batch.dispose();
        font.dispose();
        shapeRenderer.dispose();
    }

    private enum BlockType {
        GRASS(new Color(0.62f, 0.82f, 0.55f, 1f)),
        DIRT(new Color(0.70f, 0.54f, 0.41f, 1f)),
        STONE(new Color(0.70f, 0.70f, 0.73f, 1f)),
        SAND(new Color(0.90f, 0.84f, 0.58f, 1f)),
        WOOD(new Color(0.66f, 0.50f, 0.30f, 1f)),
        LEAVES(new Color(0.44f, 0.72f, 0.40f, 1f)),
        SNOW(new Color(0.95f, 0.96f, 1.0f, 1f));

        final Color tint;

        BlockType(Color tint) {
            this.tint = tint;
        }
    }

    private static final class BlockPos {
        final int x;
        final int y;
        final int z;

        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockPos)) return false;
            BlockPos pos = (BlockPos) other;
            return x == pos.x && y == pos.y && z == pos.z;
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = 31 * h + x;
            h = 31 * h + y;
            h = 31 * h + z;
            return h;
        }
    }
}
