package org.krystilize.gpucompute;

sealed interface Versions permits GLSLVersion {
    GLSLVersion VERSION_4_3 = new GLSLVersionImpl(4, 3);

    record GLSLVersionImpl(int major, int minor) implements GLSLVersion {
    }
}
