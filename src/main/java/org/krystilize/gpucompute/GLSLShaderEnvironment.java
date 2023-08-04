package org.krystilize.gpucompute;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

class GLSLShaderEnvironment {

    public static void initialize() {
        // Setup executor
        /*
         * Set a GLFW error callback to be notified about any error messages GLFW
         * generates.
         */
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));
        /*
         * Initialize GLFW itself.
         */
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        /*
         * And set some OpenGL context attributes, such as the version.
         * This is the minimum core version such so that can use compute shaders.
         */
        GLSLVersion version = GpuCompute.GLSL_VERSION;
        
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, version.major());
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, version.minor());
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        long window = glfwCreateWindow(1, 1, "", NULL, NULL);
        if (window == NULL)
            throw new AssertionError("Failed to create the GLFW window");
        glfwMakeContextCurrent(window);

        GL.createCapabilities();
    }
}
