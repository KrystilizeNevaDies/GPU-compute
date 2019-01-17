package main;

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import oglutils.OGLUtils;
import oglutils.ShaderUtils;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

class RendererUtils {

    static void checkShaders(GLAutoDrawable glDrawable) {
        OGLUtils.shaderCheck(glDrawable.getGL().getGL4());
        if ((OGLUtils.getVersionGLSL(glDrawable.getGL().getGL4()) < ShaderUtils.COMPUTE_SHADER_SUPPORT_VERSION)
                && (!OGLUtils.getExtensions(glDrawable.getGL().getGL4()).contains("compute_shader"))) {
            System.err.println("Compute shader is not supported");
            System.exit(0);
        }
    }

    static void printShaderLimits(GL4 gl) {
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
    }

    static double getAndShowTime(GL4 gl, IntBuffer timesBuffer, boolean stopwatch) {
        IntBuffer stopTimerAvailable = IntBuffer.allocate(1);
        stopTimerAvailable.put(0, 0);
        while (stopTimerAvailable.get(0) == 0) {
            gl.glGetQueryObjectiv(timesBuffer.get(1), GL4.GL_QUERY_RESULT_AVAILABLE, stopTimerAvailable);
        }

        LongBuffer time1 = LongBuffer.allocate(1);
        LongBuffer time2 = LongBuffer.allocate(1);
        gl.glGetQueryObjectui64v(timesBuffer.get(0), GL4.GL_QUERY_RESULT, time1);
        gl.glGetQueryObjectui64v(timesBuffer.get(1), GL4.GL_QUERY_RESULT, time2);

        double time = (time2.get(0) - time1.get(0)) / 1_000_000.0;

        if (!stopwatch) {
            System.out.println(String.format("Time spent on the GPU (dispatch): %f ms", time));
        }

        return time;
    }
}
