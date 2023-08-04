package org.krystilize.gpucompute;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ComputeShader extends AutoCloseable {

    static ComputeShader create(CharSequence sourceCode) {
        return new ComputeShaderImpl(sourceCode);
    }

    CompletableFuture<FloatResult> computeFloat(Map<String, Object> uniforms, int sizeX, int sizeY, int sizeZ);
    CompletableFuture<IntResult> computeInt(Map<String, Object> uniforms, int sizeX, int sizeY, int sizeZ);

    @Override
    void close();

    interface Result<B extends Buffer> {
        /**
         * Provides a fast, yet potentially unsafe way to retrieve the result.
         * @return the result as a float buffer
         */
        B direct();
    }

    interface FloatResult extends Result<FloatBuffer> {
        float retrieve(int x, int y, int z);
    }

    interface IntResult extends Result<IntBuffer> {
        int retrieve(int x, int y, int z);
    }

}
