package krys;

import static org.lwjgl.opengl.GL20.*;

/**
 * Utility methods for most of the ray tracing demos.
 *
 * @author Kai Burjack
 */
public class Utils {
    public static int compileShader(String sourceCode, int type) {
        int shader = glCreateShader(type);

        glShaderSource(shader, sourceCode);
        glCompileShader(shader);

        int compiled = glGetShaderi(shader, GL_COMPILE_STATUS);
        String shaderLog = glGetShaderInfoLog(shader);
        if (shaderLog.trim().length() > 0) {
            System.err.println(shaderLog);
        }
        if (compiled == 0) {
            throw new AssertionError("Could not compile shader");
        }
        return shader;
    }
}