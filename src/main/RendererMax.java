package main;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.DebugGL4;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import oglutils.ShaderUtils;

import java.nio.IntBuffer;
import java.util.Random;

import static main.RendererUtils.*;

public class RendererMax implements GLEventListener {

    private final static int ITEM_SIZE = 4; // integer has 4 bytes

    private int computeProgram;
    private int locColumnsCount;

    private int shrinkProgram;
    private int locOriginalColumnsCount, locShrinkColumnsCount;

    private int[] locBuffer;

    private final int GROUP_SIZE = 8; // limit 1536 -> 39
    private final int DATA_SIZE = 1 << 10;

    private int origColumnsCount = DATA_SIZE;
    // private int origColumnsCountRounded = (origColumnsCount / GROUP_SIZE) * GROUP_SIZE + ((origColumnsCount % GROUP_SIZE == 0) ? 0 : GROUP_SIZE);
    // najdi první větší dělitel čísla "origColumnsCount" číslem "GROUP_SIZE"
    private int groupCount = origColumnsCount / GROUP_SIZE;
    private int originalDataSize = origColumnsCount * origColumnsCount;
    private final IntBuffer data = IntBuffer.allocate(DATA_SIZE * DATA_SIZE);
    private final IntBuffer dataOut = Buffers.newDirectIntBuffer(DATA_SIZE * DATA_SIZE);

    private int shrinkColumnCount;
    private int shrinkDataSize;

    private static final boolean PRINT = false;
    private static final boolean STOPWATCH = false;

    private double timeOne = 0, timeTotal = 0;

    @Override
    public void init(GLAutoDrawable glDrawable) {
        checkShaders(glDrawable);

        glDrawable.setGL(new DebugGL4(glDrawable.getGL().getGL4()));
        GL4 gl = glDrawable.getGL().getGL4();

        printShaderLimits(gl);

        // load programs
        computeProgram = ShaderUtils.loadProgram(gl, "/computeMax");
        shrinkProgram = ShaderUtils.loadProgram(gl, "/shrink");

        // load uniforms
        locColumnsCount = gl.glGetUniformLocation(computeProgram, "columnsCount");
        locOriginalColumnsCount = gl.glGetUniformLocation(shrinkProgram, "originalColumnsCount");
        locShrinkColumnsCount = gl.glGetUniformLocation(shrinkProgram, "shrinkColumnsCount");

        // declare and generate a buffer object name
        locBuffer = new int[2];
        gl.glGenBuffers(2, locBuffer, 0);

        System.out.println("glShaderStorageBlockBinding...");
        // assign the index of shader storage block to the binding point (see shader)
        gl.glShaderStorageBlockBinding(computeProgram, 0, 0); //input buffer
        gl.glShaderStorageBlockBinding(computeProgram, 1, 1); //output buffer

        gl.glShaderStorageBlockBinding(shrinkProgram, 0, 0);
        gl.glShaderStorageBlockBinding(shrinkProgram, 1, 1);
        System.out.println("DONE");

//        long time = System.currentTimeMillis();
//        IntBuffer testData = IntBuffer.allocate(400_000_000);
//        System.out.println(System.currentTimeMillis() - time);
//
//        time = System.currentTimeMillis();
//        IntBuffer testData2 = Buffers.newDirectIntBuffer(testData.array(), 0, 100_000_000);
//        System.out.println(System.currentTimeMillis() - time);
    }

    private void initData(GL4 gl) {
        long time = System.currentTimeMillis();
        data.rewind();
        Random r = new Random();
        for (int i = 0; i < originalDataSize; i++) {
            data.put(i, r.nextInt(100));
        }
        data.put(9, 100);
        if (!STOPWATCH && !PRINT) {
            System.out.println("Buffer initialization took " + (System.currentTimeMillis() - time) + " ms.");
        }

        if (PRINT) {
            System.out.println("Input values");
            print(originalDataSize, origColumnsCount, groupCount, data);
        }

        if (!STOPWATCH && !PRINT) System.out.println("glBufferData...");
        // bind the buffer and define its initial storage capacity
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, ITEM_SIZE * originalDataSize, data, GL4.GL_STATIC_DRAW);

        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, ITEM_SIZE * originalDataSize, dataOut, GL4.GL_STATIC_DRAW);
        if (!STOPWATCH && !PRINT) System.out.println("DONE");

        // unbind the buffer
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private void print(final int dataSize, final int columnsCount, final int groupCount, final IntBuffer data) {
        String dashes = new String(new char[(GROUP_SIZE * 4 + 1) * groupCount]).replace("\0", "-");
        for (int i = 0; i < dataSize; i++) {
            if (i > 0 && i % columnsCount == 0) {
                System.out.println();
            }
            System.out.print(String.format("%02d, ", data.get(i)));
            if ((i + 1) % GROUP_SIZE == 0 && (i + 1) % columnsCount != 0) {
                System.out.print("| ");
            }
            if ((i + 1) % (columnsCount * GROUP_SIZE) == 0) {
                System.out.println();
                System.out.print(dashes);
            }
        }
        System.out.println();
        System.out.println();
    }

    @Override
    public void display(GLAutoDrawable glDrawable) {
        final GL4 gl = glDrawable.getGL().getGL4();

// GL_TIME_ELAPSED test
//        IntBuffer timesBuffer2 = IntBuffer.allocate(1);
//        gl.glGenQueries(1, timesBuffer2);
//        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timesBuffer2.get(0));
//        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
//        IntBuffer intBuffer = IntBuffer.allocate(1);
//        gl.glGetQueryObjectiv(timesBuffer2.get(0), GL4.GL_QUERY_RESULT, intBuffer);
//        System.out.println("Time elapsed: " + intBuffer.get(0));
//        https://stackoverflow.com/questions/24446207/what-is-the-difference-between-querying-time-elapsed-in-opengl-with-gl-time-elap

        int count = -1;

        while (count++ < 2) {

            origColumnsCount = DATA_SIZE;
            groupCount = origColumnsCount / GROUP_SIZE;
            originalDataSize = origColumnsCount * origColumnsCount;

            shrinkColumnCount = groupCount;
            shrinkDataSize = shrinkColumnCount * shrinkColumnCount;

            initData(gl);

            while (shrinkColumnCount > 0) {
                compute(gl, count);
            }

            if (!STOPWATCH) {
                break; // ignore outer while loop when not doing measuring time
            }
        }

        if (STOPWATCH) {
            System.out.println(String.format("Mean time: %f ms", (timeTotal / (count - 1))));
        }

        dispose(glDrawable);
        System.exit(0);
    }

    private void compute(GL4 gl, int count) {
        IntBuffer timesBuffer = IntBuffer.allocate(2);
        gl.glGenQueries(2, timesBuffer);

        IntBuffer invocationsQueryId;
        if (!STOPWATCH) {
            // https://www.khronos.org/registry/OpenGL/extensions/ARB/ARB_pipeline_statistics_query.txt
            invocationsQueryId = IntBuffer.allocate(1);
            gl.glGenQueries(1, invocationsQueryId);
            gl.glBeginQuery(GL4.GL_COMPUTE_SHADER_INVOCATIONS_ARB, invocationsQueryId.get(0));
        }

        gl.glUseProgram(computeProgram);

        gl.glUniform1i(locColumnsCount, origColumnsCount);

        //set input and output buffer
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[0]);

        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, locBuffer[1]);

        // dispatch compute, query counter
        if (!STOPWATCH)
            System.out.println("Calling dispatch compute with " + groupCount + "^2 group" + (groupCount > 1 ? "s" : "") + ".");
        gl.glQueryCounter(timesBuffer.get(0), GL4.GL_TIMESTAMP);
        gl.glDispatchCompute(groupCount, groupCount, 1);
        gl.glQueryCounter(timesBuffer.get(1), GL4.GL_TIMESTAMP);

        timeOne += getAndShowTime(gl, timesBuffer, STOPWATCH);

        // get compute shader invocations count
        if (!STOPWATCH) {
            gl.glEndQuery(GL4.GL_COMPUTE_SHADER_INVOCATIONS_ARB);
            IntBuffer invocationsCount = IntBuffer.allocate(1);
            gl.glGetQueryObjectiv(invocationsQueryId.get(0), GL4.GL_QUERY_RESULT, invocationsCount);
            System.out.println("Compute shader invocations: " + invocationsCount.get(0));
            System.out.println();
        }

        if (PRINT) {
            // make sure writing to image has finished before read
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, ITEM_SIZE * originalDataSize, dataOut);

            System.out.println("Output values");
            dataOut.rewind();
            print(originalDataSize, origColumnsCount, groupCount, dataOut);
        }

        // second step
        // shrink data
        gl.glUseProgram(shrinkProgram);

        gl.glUniform1i(locShrinkColumnsCount, shrinkColumnCount);
        gl.glUniform1i(locOriginalColumnsCount, origColumnsCount);

        // bind input and output buffer
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[1]);

        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, locBuffer[0]);

        gl.glDispatchCompute(groupCount, groupCount, 1);

        if (PRINT) {
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, ITEM_SIZE * shrinkDataSize, dataOut);
            System.out.println("Shrinked values");
            dataOut.rewind();
            print(shrinkDataSize, shrinkColumnCount, groupCount / GROUP_SIZE, dataOut);
        }

        originalDataSize /= Math.pow(GROUP_SIZE, 2);
        origColumnsCount /= GROUP_SIZE;
        shrinkDataSize /= Math.pow(GROUP_SIZE, 2);
        shrinkColumnCount /= GROUP_SIZE;

        groupCount /= GROUP_SIZE;

        if (shrinkColumnCount == 0) {
            if (!STOPWATCH) {
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
                gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, ITEM_SIZE, dataOut);
                System.out.println("Final value");
                System.out.println(dataOut.get(0));
            }

            System.out.println();
            System.out.println(String.format("Total time: %f ms", timeOne));

            if (count > 0) { // ignore first time
                timeTotal += timeOne;
            } else if (STOPWATCH) {
                System.out.println("IGNORED");
            }
            System.out.println(count);
            timeOne = 0;
        }
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    }

    @Override
    public void dispose(GLAutoDrawable glDrawable) {
        GL4 gl = glDrawable.getGL().getGL4();
        gl.glDeleteProgram(computeProgram);
        gl.glDeleteProgram(shrinkProgram);
        gl.glDeleteBuffers(2, locBuffer, 0);
    }

}