package main;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.DebugGL4;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import oglutils.OGLUtils;
import oglutils.ShaderUtils;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Random;

public class RendererSortBitonic implements GLEventListener {

    private final static int ITEM_SIZE = 4; // integer has 4 bytes

    private int computeProgram;
    private int locGroupSize, locBrown, locDataCount;
    private int[] locBuffer;

    private final int dataCount = 2097152;
    private final IntBuffer data = Buffers.newDirectIntBuffer(dataCount);

    private int iterationsCount = 1;
    private final int iterationsGoal = (int) Math.ceil(Math.log(dataCount) / Math.log(2)); // log2(dataCount)
    private int currentGroupSize = 2;
    private boolean brownGroup = true;

    private final boolean PRINT = false;

    @Override
    public void init(GLAutoDrawable glDrawable) {
        // check if shaders are supported
        OGLUtils.shaderCheck(glDrawable.getGL().getGL4());
        if ((OGLUtils.getVersionGLSL(glDrawable.getGL().getGL4()) < ShaderUtils.COMPUTE_SHADER_SUPPORT_VERSION)
                && (!OGLUtils.getExtensions(glDrawable.getGL().getGL4()).contains("compute_shader"))) {
            System.err.println("Compute shader is not supported");
            System.exit(0);
        }

        glDrawable.setGL(new DebugGL4(glDrawable.getGL().getGL4()));
        GL4 gl = glDrawable.getGL().getGL4();

        // get limits on work group size per dimension
        for (int dim = 0; dim < 3; dim++) {
            IntBuffer val = IntBuffer.allocate(1);
            gl.glGetIntegeri_v(GL4.GL_MAX_COMPUTE_WORK_GROUP_SIZE, dim, val);
            System.out.println("GL_MAX_COMPUTE_WORK_GROUP_SIZE [" + dim + "]: " + val.get(0));
        }

        {
            // get limit on work group size (sum on all dimensions)
            LongBuffer val = LongBuffer.allocate(1);
            gl.glGetInteger64v(GL4.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, val);
            System.out.println("GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS: " + val.get(0));
        }

        // get limit on work group count per dimension
        for (int dim = 0; dim < 3; dim++) {
            IntBuffer val = IntBuffer.allocate(1);
            gl.glGetIntegeri_v(GL4.GL_MAX_COMPUTE_WORK_GROUP_COUNT, dim, val);
            System.out.println("GL_MAX_COMPUTE_WORK_GROUP_COUNT [" + dim + "]: " + val.get(0));
        }

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
        GL4 gl = glDrawable.getGL().getGL4();

        while (true) {
            IntBuffer timesBuffer = IntBuffer.allocate(2);
            gl.glGenQueries(2, timesBuffer);

            // https://www.khronos.org/registry/OpenGL/extensions/ARB/ARB_pipeline_statistics_query.txt
            IntBuffer invocationsQueryId = IntBuffer.allocate(1);
            gl.glGenQueries(1, invocationsQueryId);
            gl.glBeginQuery(GL4.GL_COMPUTE_SHADER_INVOCATIONS_ARB, invocationsQueryId.get(0));

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
            getAndShowTime(gl, timesBuffer);

            // get compute shader invocations count
            gl.glEndQuery(GL4.GL_COMPUTE_SHADER_INVOCATIONS_ARB);
            IntBuffer invocationsCount = IntBuffer.allocate(1);
            gl.glGetQueryObjectiv(invocationsQueryId.get(0), GL4.GL_QUERY_RESULT, invocationsCount);
            System.out.println("Compute shader invocations: " + invocationsCount.get(0));

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
                dispose(glDrawable);
                System.exit(0);
            }
        }
    }

    private void getAndShowTime(GL4 gl, IntBuffer timesBuffer) {
        IntBuffer stopTimerAvailable = IntBuffer.allocate(1);
        stopTimerAvailable.put(0, 0);
        while (stopTimerAvailable.get(0) == 0) {
            gl.glGetQueryObjectiv(timesBuffer.get(1), GL4.GL_QUERY_RESULT_AVAILABLE, stopTimerAvailable);
        }
        LongBuffer time1 = LongBuffer.allocate(1);
        LongBuffer time2 = LongBuffer.allocate(1);
        gl.glGetQueryObjectui64v(timesBuffer.get(0), GL4.GL_QUERY_RESULT, time1);
        gl.glGetQueryObjectui64v(timesBuffer.get(1), GL4.GL_QUERY_RESULT, time2);

        System.out.println(String.format("Time spent on the GPU (dispatch): %f ms", (time2.get(0) - time1.get(0)) / 1000000.0));
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