/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.krystilize.gpucompute;

import krys.Utils;
import org.lwjgl.BufferUtils;

import java.lang.ref.Cleaner;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.lwjgl.opengl.GL43C.*;

/**
 * This is the first part of a small code-centric tutorial on ray tracing with
 * LWJGL 3 and OpenGL's compute shaders. The way these tutorials work is by
 * simply reading JavaDoc and inline comments as you work your way through the
 * Java code in a pretty much "top-to-bottom" way. Everything necessary will be
 * mentioned and explained as your read. You should be somewhat familiar with
 * Java, OpenGL and with how LWJGL 3 translates the native OpenGL API to Java.
 * <p>
 * The focus of this part will be to set up everything needed for a first
 * working ray tracer to render a scene of a few uncolored boxes. Later parts
 * will cover more and more advanced topics. But for now, this part will only
 * cover the following:
 * <ul>
 * <li>Create a GLFW window to present the final raytraced/rendered image on
 * <li>Allocate a texture to act as our framebuffer for the ray tracer
 * <li>Compile a GLSL compute shader and properly bind to its interface
 * uniforms, especially the framebuffer texture
 * <li>Call the GLSL compute shader to render our scene image
 * <li>Present the rendered image on the GLFW window
 * <li>In addition, a simple way to rotate around the scene via the mouse
 * </ul>
 * <p>
 * The goal is to create a very simple ray tracer able to trace "primary" rays
 * originating from the eye through the scene and detect any intersections with
 * scene geometry (in the form of axis-aligned boxes). In this first part, the
 * rendered result will not look different to what you could achieve easily when
 * rasterizing (the usual way to render with OpenGL) the boxes on the window
 * framebuffer. But be patient. Things will get much different in later parts.
 *
 * @author Kai Burjack
 */
class ComputeShaderImpl implements ComputeShader {

    private final ScheduledExecutorService SERVICE = Executors.newSingleThreadScheduledExecutor((runnable) -> {
        Thread thread = new Thread(runnable, "ComputeShaderImpl");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The shader program handle of the compute shader.
     */
    private final int computeProgram;

    ComputeShaderImpl(CharSequence shader) {
        /* Create all needed GL resources */
        try {
            SERVICE.submit(GLSLShaderEnvironment::initialize).get();
            computeProgram = SERVICE.submit(() -> createComputeProgram(shader)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to create compute shader", e);
        }

        // Setup cleanup
        Cleaner cleaner = Cleaner.create();

        // We need to explicitly cache local variables here to separate them from `this` when `this` is getting GCed
        ScheduledExecutorService SERVICE = this.SERVICE;
        int computeProgram = this.computeProgram;
        cleaner.register(this, () -> {
            SERVICE.submit(() -> glDeleteProgram(computeProgram));
            SERVICE.shutdown();
        });
    }

    /**
     * Create the tracing compute shader program.
     */
    private int createComputeProgram(CharSequence shaderSource) {
        /*
         * Compile our GLSL compute shader. It does not look any different to creating a
         * program with vertex/fragment shaders. The only thing that changes is the
         * shader type, now being GL_COMPUTE_SHADER.
         */
        int program = glCreateProgram();
        int cshader = Utils.compileShader(shaderSource.toString(), GL_COMPUTE_SHADER);
        glAttachShader(program, cshader);
        glLinkProgram(program);
        int linked = glGetProgrami(program, GL_LINK_STATUS);
        String programLog = glGetProgramInfoLog(program);
        if (programLog.trim().length() > 0) {
            System.err.println(programLog);
        }
        if (linked == 0) {
            throw new AssertionError("Could not link program");
        }
        return program;
    }

    private void compute(Map<String, Object> uniforms, int format, int sizeX, int sizeY, int sizeZ) {
        // Ensure we are using the correct program
        glUseProgram(computeProgram);

        // Setup texture to output to
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, texture);
        glTexStorage3D(GL_TEXTURE_3D, 1, format, sizeX, sizeY, sizeZ);

        /* Query the "image binding point" of the image uniform */
        IntBuffer params = BufferUtils.createIntBuffer(1);
        int uniformLocation = glGetUniformLocation(computeProgram, "output_texture");
        glGetUniformiv(computeProgram, uniformLocation, params);
        int textureBinding = params.get(0);

        /*
         * Bind level 0 of framebuffer texture as writable image in the shader. This
         * tells OpenGL that any writes to the image defined in our shader is going to
         * go to the first level of the texture 'texture'.
         */
        glBindImageTexture(textureBinding, texture, 0, false, 0, GL_WRITE_ONLY, format);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        // Set the uniforms
        uniforms.forEach((name, value) -> applyUniform(glGetUniformLocation(computeProgram, name), value));

        /* Invoke the compute shader. */
        glDispatchCompute(sizeX, sizeY, sizeZ);
        glUseProgram(0);
    }

    @Override
    public CompletableFuture<FloatResult> computeFloat(Map<String, Object> uniforms, int sizeX, int sizeY, int sizeZ) {
        return CompletableFuture.supplyAsync(() -> {
            // Compute
            compute(uniforms, GL_R32F, sizeX, sizeY, sizeZ);

            // Get the outputs
            FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(sizeX * sizeY * sizeZ);
            glGetTexImage(GL_TEXTURE_3D, 0, GL_RED, GL_FLOAT, floatBuffer);
            glBindTexture(GL_TEXTURE_3D, 0);

            return new FloatResultImpl(floatBuffer, sizeX, sizeY, sizeZ);
        }, SERVICE);
    }

    @Override
    public CompletableFuture<IntResult> computeInt(Map<String, Object> uniforms, int sizeX, int sizeY, int sizeZ) {
        return CompletableFuture.supplyAsync(() -> {
            // Compute
            compute(uniforms, GL_R32I, sizeX, sizeY, sizeZ);

            // Get the outputs
            IntBuffer intBuffer = BufferUtils.createIntBuffer(sizeX * sizeY * sizeZ);
            glGetTexImage(GL_TEXTURE_3D, 0, GL_RED_INTEGER, GL_INT, intBuffer);
            glBindTexture(GL_TEXTURE_3D, 0);

            return new IntResultImpl(intBuffer, sizeX, sizeY, sizeZ);
        }, SERVICE);
    }

    private record FloatResultImpl(FloatBuffer buffer, int sizeX, int sizeY, int sizeZ) implements FloatResult {

        @Override
        public float retrieve(int x, int y, int z) {
            return buffer.get(x + y * sizeX + z * sizeX * sizeY);
        }

        @Override
        public FloatBuffer direct() {
            return buffer;
        }
    }

    private record IntResultImpl(IntBuffer buffer, int sizeX, int sizeY, int sizeZ) implements IntResult {

        @Override
        public int retrieve(int x, int y, int z) {
            return buffer.get(x + y * sizeX + z * sizeX * sizeY);
        }

        @Override
        public IntBuffer direct() {
            return buffer;
        }
    }

    private void applyUniform(int location, Object uniformValue) {
        if (uniformValue instanceof Integer value) glUniform1i(location, value);
        else if (uniformValue instanceof Float value) glUniform1f(location, value);
        else if (uniformValue instanceof Double value) glUniform1d(location, value);
        else throw new IllegalArgumentException("Unsupported uniform type: " + uniformValue.getClass());
    }
}