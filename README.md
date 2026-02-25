# TadjikCraft

A voxel sandbox inspired by Minecraft, built on [libGDX](https://libgdx.com/) + LWJGL3.

## What was improved

- Procedural terrain generation with biomes (grass / sand / snow), hills, and trees.
- Multiple block types with hotbar-like selection (`1..7`).
- Better mining/building via short raycast targeting.
- Day/night lighting cycle.
- Sprinting, jumping, optional fly mode (`F`), and HUD + crosshair.
- Vulkan API capability check in desktop launcher (`TADJIKCRAFT_RENDERER=vulkan`) with OpenGL fallback.

## Controls

- `WASD` — move
- `SHIFT` — sprint
- `SPACE` — jump (or fly up in fly mode)
- `CTRL` — fly down (fly mode)
- `F` — toggle fly mode
- `1..7` — select block type
- `LMB` — break block
- `RMB` — place selected block
- `ESC` — release/capture mouse

## Vulkan note

Current libGDX rendering still uses OpenGL under LWJGL3. The launcher now probes Vulkan support via GLFW and can be started with:

```bash
TADJIKCRAFT_RENDERER=vulkan ./gradlew lwjgl3:run
```

If Vulkan is unavailable, the game falls back to OpenGL.

## Run

```bash
./gradlew lwjgl3:run
```
