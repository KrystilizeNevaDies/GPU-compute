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

import static com.jogamp.opengl.GL2ES2.*;
import static oglutils.ShaderUtils.COMPUTE_SHADER_SUPPORT_VERSION;

/**
 * GLSL sample:<br/>
 * Using computy shader for searching minimal key value<br/>
 * Requires JOGL 4.3.0 or newer
 *
 * @author PGRF FIM UHK
 * @version 2.0
 * @since 2016-09-09
 */
public class Renderer implements GLEventListener {

    private int computeProgram, shrinkProgram;
    private int locOffset;
    private int[] locBuffer;

    private final int groupSize = 4;
    private int size = 16;
    private int groupCount = size / groupSize;
    private final int dataSize = size * size;
    private IntBuffer data = IntBuffer.allocate(dataSize);
    private IntBuffer dataOut = Buffers.newDirectIntBuffer(dataSize);
    private IntBuffer dataShrink = Buffers.newDirectIntBuffer(size);

    private int offset = 1, compute = 0;
    private int locSize;

    @Override
    public void init(GLAutoDrawable glDrawable) {
        // check whether shaders are supported
        OGLUtils.shaderCheck(glDrawable.getGL().getGL2GL3());
        if ((OGLUtils.getVersionGLSL(glDrawable.getGL().getGL2GL3()) < COMPUTE_SHADER_SUPPORT_VERSION)
                && (!OGLUtils.getExtensions(glDrawable.getGL().getGL2GL3()).contains("compute_shader"))) {
            System.err.println("Compute shader is not supported");
            System.exit(0);
        }

        glDrawable.setGL(new DebugGL4(glDrawable.getGL().getGL4()));
        GL4 gl = glDrawable.getGL().getGL4();

        computeProgram = ShaderUtils.loadProgram(gl, "/computeTest");
        shrinkProgram = ShaderUtils.loadProgram(gl, "/shrink");

        locOffset = gl.glGetUniformLocation(computeProgram, "offset");
        locSize = gl.glGetUniformLocation(computeProgram, "size");

        // buffer initialization
        data.rewind();
        Random r = new Random();
        for (int i = 0; i < dataSize; i++) {
            data.put(i, r.nextInt(99));
        }

        System.out.print("Input values");
        print(dataSize, data);

        // declare and generate a buffer object name
        locBuffer = new int[3];
        gl.glGenBuffers(3, locBuffer, 0);

        // bind the buffer and define its initial storage capacity
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 4 * dataSize, data, GL4.GL_DYNAMIC_DRAW);

        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 4 * dataSize, dataOut, GL4.GL_DYNAMIC_DRAW);

        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[2]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, 4 * size, dataShrink, GL4.GL_DYNAMIC_DRAW);

        // unbind the buffer
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);

        //assign the index of shader storage block to the binding point (see shader)
        gl.glShaderStorageBlockBinding(computeProgram, 0, 0); //input buffer
        gl.glShaderStorageBlockBinding(computeProgram, 1, 1); //output buffer

        gl.glShaderStorageBlockBinding(shrinkProgram, 0, 0); //shrink buffer
        gl.glShaderStorageBlockBinding(shrinkProgram, 1, 1); //shrink buffer
    }

    private void print(final int dataSize, final IntBuffer data) {
        String dashes = new String(new char[18 * groupCount]).replace("\0", "-");
        for (int i = 0; i < dataSize; i++) {
            if (i % size == 0) {
                System.out.println();
            }
            System.out.print(String.format("%02d, ", data.get(i)));
            if ((i + 1) % groupSize == 0 && (i + 1) % size != 0) {
                System.out.print("| ");
            }
            if ((i + 1) % (size * groupSize) == 0) {
                System.out.println();
                System.out.print(dashes);
            }
        }
        System.out.println();
    }

    @Override
    public void display(GLAutoDrawable glDrawable) {
        GL4 gl = glDrawable.getGL().getGL4();
        //LongBuffer longBuffer = LongBuffer.allocate(1);
        //gl.glGetInteger64v(GL_TIMESTAMP, longBuffer);
        //System.out.println(String.format("Milliseconds: %f\n", longBuffer.get(0)/1000000.0));

        IntBuffer timesBuffer = IntBuffer.allocate(3);
        gl.glGenQueries(3, timesBuffer);
        gl.glQueryCounter(timesBuffer.get(0), GL_TIMESTAMP);

        if (offset > 0) {
            gl.glUseProgram(computeProgram);

            gl.glUniform1i(locOffset, offset);
            gl.glUniform1i(locSize, size);

            //set input and output buffer
            //if (compute % 2 == 0) {
                //bind input buffer
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
                gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[0]);
                //bind output buffer
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
                gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, locBuffer[1]);
            /*} else {
                //bind input buffer
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
                gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[1]);
                //bind output buffer
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
                gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, locBuffer[0]);
            }*/
            gl.glQueryCounter(timesBuffer.get(1), GL_TIMESTAMP);

            gl.glDispatchCompute(size / groupSize, size / groupSize, 1);

            gl.glQueryCounter(timesBuffer.get(2), GL_TIMESTAMP);

            IntBuffer stopTimerAvailable = IntBuffer.allocate(1);
            stopTimerAvailable.put(0, 0);
            while (stopTimerAvailable.get(0) == 0) {
                gl.glGetQueryObjectiv(timesBuffer.get(2), GL_QUERY_RESULT_AVAILABLE, stopTimerAvailable);
            }
            LongBuffer time1 = LongBuffer.allocate(1);
            LongBuffer time2 = LongBuffer.allocate(1);
            LongBuffer time3 = LongBuffer.allocate(1);
            gl.glGetQueryObjectui64v(timesBuffer.get(0), GL_QUERY_RESULT, time1);
            gl.glGetQueryObjectui64v(timesBuffer.get(1), GL_QUERY_RESULT, time2);
            gl.glGetQueryObjectui64v(timesBuffer.get(2), GL_QUERY_RESULT, time3);


            //System.out.println(String.format("Time spent on the GPU 2-1 (bind): %f ms", (time2.get(0) - time1.get(0)) / 1000000.0));
            System.out.println(String.format("Time spent on the GPU 3-2 (dispatch): %f ms", (time3.get(0) - time2.get(0)) / 1000000.0));
            //System.out.println(String.format("Time spent on the GPU 3-1 (all): %f ms", (time3.get(0) - time1.get(0)) / 1000000.0));

            // make sure writing to image has finished before read
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);

            if (compute % 2 == 0) {
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
            } else {
                gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[0]);
            }

            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 4 * dataSize, dataOut);

            System.out.println();
            System.out.print("Output values");
            dataOut.rewind();
            print(dataSize, dataOut);

            //Buffers.newDirectIntBuffer()


            gl.glUseProgram(shrinkProgram);

            //bind input buffer
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[1]);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, locBuffer[1]);
            //bind output buffer
            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[2]);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, locBuffer[2]);


            gl.glDispatchCompute(size / groupSize, size / groupSize, 1);


            gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, locBuffer[2]);
            gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 4 * size, dataShrink);
            System.out.print("Shrink values");
            dataShrink.rewind();
            print(size, dataShrink);

            compute++;
            //offset = offset / 2;
            offset--;

        } else {
            System.exit(0);
        }

        /*gl.glUseProgram(0);

        gl.glClearColor(0.5f, 0.1f, 0.1f, 1.0f);
        gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);*/
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    }

    @Override
    public void dispose(GLAutoDrawable glDrawable) {
        GL4 gl = glDrawable.getGL().getGL4();
        gl.glDeleteProgram(computeProgram);
        gl.glDeleteBuffers(2, locBuffer, 0);
        gl.glGenBuffers(2, locBuffer, 0);
    }

}