package com.roflang.tadjikcraft.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.roflang.tadjikcraft.TadjikCraftGame;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new TadjikCraftGame(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("TadjikCraft");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1280, 720);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");

        String renderer = System.getenv("TADJIKCRAFT_RENDERER");
        if (renderer != null && renderer.equalsIgnoreCase("vulkan")) {
            boolean glfwOk = GLFW.glfwInit();
            boolean vulkanSupported = glfwOk && GLFWVulkan.glfwVulkanSupported();
            if (glfwOk) {
                GLFW.glfwTerminate();
            }

            if (vulkanSupported) {
                System.out.println("[TadjikCraft] Vulkan API detected. LibGDX backend still renders with OpenGL today,");
                System.out.println("[TadjikCraft] but Vulkan capability is available for future renderer upgrades.");
            } else {
                System.out.println("[TadjikCraft] Vulkan requested via TADJIKCRAFT_RENDERER=vulkan, but not available. Falling back to OpenGL.");
            }

            configuration.useOpenGL3(true, 3, 2);
        } else {
            configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);
        }

        return configuration;
    }
}
