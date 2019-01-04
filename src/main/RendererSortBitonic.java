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

public class RendererSortBitonic implements GLEventListener {

    private final static int ITEM_SIZE = 4; // integer has 4 bytes

    private int computeProgram;
    private int locGroupSize, locBrown, locDataCount;
    private int[] locBuffer;

    private final int dataCount = 1 << 7;
    private final IntBuffer data = Buffers.newDirectIntBuffer(dataCount);

    @SuppressWarnings("FieldCanBeLocal")
    private int iterationsCount = 1;
    private final int iterationsGoal = (int) Math.ceil(Math.log(dataCount) / Math.log(2)); // log2(dataCount)
    @SuppressWarnings("FieldCanBeLocal")
    private int currentGroupSize = 2;
    @SuppressWarnings("FieldCanBeLocal")
    private boolean brownGroup = true;

    private static final boolean PRINT = false;
    private static final boolean STOPWATCH = true;

    private double totalTime = 0;

    @Override
    public void init(GLAutoDrawable glDrawable) {
        checkShaders(glDrawable);

        glDrawable.setGL(new DebugGL4(glDrawable.getGL().getGL4()));
        GL4 gl = glDrawable.getGL().getGL4();

        printShaderLimits(gl);
        // load programs
        computeProgram = ShaderUtils.loadProgram(gl, "/computeSort");

        // load uniforms
        locBrown = gl.glGetUniformLocation(computeProgram, "brown");
        locGroupSize = gl.glGetUniformLocation(computeProgram, "groupSize");
        locDataCount = gl.glGetUniformLocation(computeProgram, "dataCount");

        // buffer initialization
        long time = System.currentTimeMillis();
        data.rewind();
        Random r = new Random();
        for (int i = 0; i < dataCount; i++) {
            data.put(i, r.nextInt(10) + 1);
        }
        data.put(0, 11);
        System.out.println("Buffer initialization took " + (System.currentTimeMillis() - time) + " ms.");

        if (PRINT) {
            System.out.println("Input values");
            print(data);
        }

        // declare and generate a buffer object name
        locBuffer = new int[1];
        gl.glGenBuffers(1, locBuffer, 0);

        System.out.println("glBufferData...");
        // bind the buffer and define its initial storage capacity
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, ITEM_SIZE * dataCount, data, GL4.GL_STATIC_DRAW);
        System.out.println("DONE");

        // unbind the buffer
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);

        gl.glShaderStorageBlockBinding(computeProgram, 0, 0); //input buffer
        System.out.println();
    }

    private void print(final IntBuffer data) {
        for (int i = 0; i < dataCount; i++) {
            System.out.print(data.get(i) + ", ");
        }
        System.out.println();
        System.out.println();
    }

    @Override
    public void display(GLAutoDrawable glDrawable) {
        final GL4 gl = glDrawable.getGL().getGL4();

        int count = -1; // first
        double timesSum = 0;

        while (count++ < 2) {

            iterationsCount = 1;
            currentGroupSize = 2;
            brownGroup = true;

            boolean finish = false;

            while (!finish) {

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

                gl.glUniform1i(locBrown, brownGroup ? 1 : 0);
                gl.glUniform1i(locGroupSize, currentGroupSize);
                gl.glUniform1i(locDataCount, dataCount);

                // set input and output buffer
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
                gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[0]);

                // dispatch compute, query counter
                gl.glQueryCounter(timesBuffer.get(0), GL4.GL_TIMESTAMP);
                gl.glDispatchCompute(dataCount, 1, 1);

                gl.glQueryCounter(timesBuffer.get(1), GL4.GL_TIMESTAMP);
                totalTime += getAndShowTime(gl, timesBuffer, STOPWATCH);

                // get compute shader invocations count
                if (!STOPWATCH) {
                    gl.glEndQuery(GL4.GL_COMPUTE_SHADER_INVOCATIONS_ARB);
                    IntBuffer invocationsCount = IntBuffer.allocate(1);
                    gl.glGetQueryObjectiv(invocationsQueryId.get(0), GL4.GL_QUERY_RESULT, invocationsCount);
                    System.out.println("Compute shader invocations: " + invocationsCount.get(0));
                }

                if (PRINT) {
                    // make sure writing to image has finished before read
                    gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
                    gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
                    gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, ITEM_SIZE * dataCount, data);
                    data.rewind();
                    System.out.println("Output values");
                    print(data);
                }

                if (currentGroupSize == 2) {
                    brownGroup = true;
                    iterationsCount++;
                    currentGroupSize = (int) Math.pow(2, iterationsCount);
                } else {
                    brownGroup = false;
                    currentGroupSize /= 2;
                }

                if (iterationsCount == iterationsGoal + 1) {
                    System.out.println("Total time: " + totalTime + " ms");

                    if (count > 0) { // ignore first (-1) time
                        timesSum += totalTime;
                    } else if (STOPWATCH) {
                        System.out.println("IGNORED");
                    }
                    finish = true;
                    System.out.println(count);
                    System.out.println();
                    totalTime = 0;
                }
            }

            if (!STOPWATCH) {
                break;
            }
        }

        if (STOPWATCH) {
            System.out.println("Mean time: " + (timesSum / (count - 1)));
        }

        dispose(glDrawable);
        System.exit(0);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    }

    @Override
    public void dispose(GLAutoDrawable glDrawable) {
        GL4 gl = glDrawable.getGL().getGL4();
        gl.glDeleteProgram(computeProgram);
        gl.glDeleteBuffers(1, locBuffer, 0);
    }

}