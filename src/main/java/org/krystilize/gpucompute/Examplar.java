package org.krystilize.gpucompute;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Map;

public class Examplar {

    private static final String FLOAT_SOURCE = """
/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
#version 430 core

/**
 * A Noise value
 */
uniform float noise;

layout (r32f, binding = 1) writeonly uniform image3D output_texture;

#define LARGE_FLOAT 1E+10
#define EPSILON 0.0001

layout (local_size_x = 16, local_size_y = 16) in;

//\tSimplex 3D Noise
//\tby Ian McEwan, Ashima Arts
//
vec4 permute(vec4 x) {return mod(((x * 34.0) + 1.0) * x, 289.0);}
vec4 taylorInvSqrt(vec4 r) {return 1.79284291400159 - 0.85373472095314 * r;}

float snoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    //  x0 = x0 - 0. + 0.0 * C
    vec3 x1 = x0 - i1 + 1.0 * C.xxx;
    vec3 x2 = x0 - i2 + 2.0 * C.xxx;
    vec3 x3 = x0 - 1. + 3.0 * C.xxx;

    // Permutations
    i = mod(i, 289.0);
    vec4 p = permute(permute(permute(
                                 i.z + vec4(0.0, i1.z, i2.z, 1.0))
                             + i.y + vec4(0.0, i1.y, i2.y, 1.0))
                     + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    // Gradients
    // ( N*N points uniformly over a square, mapped onto an octahedron.)
    float n_ = 1.0 / 7.0; // N=7
    vec3 ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);  //  mod(p,N*N)

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);    // mod(j,N)

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    //Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1),
                                  dot(p2, x2), dot(p3, x3)));
}

void return_value(float value) {
    ivec3 pos = ivec3(gl_GlobalInvocationID.xy, gl_GlobalInvocationID.z);
    imageStore(output_texture, pos, vec4(value));
}

/**
 * Entry point of this GLSL compute shader.
 */
void main(void) {
    ivec3 pos = ivec3(gl_GlobalInvocationID.x, gl_GlobalInvocationID.y, gl_GlobalInvocationID.z);
    float noiseValue = snoise(vec3(pos));
    return_value(noiseValue);
}
            """;

    private static final String INT_SOURCE = """
/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
#version 430 core

/**
 * A Noise value
 */
uniform float noise;

layout (r32i, binding = 1) writeonly uniform iimage3D output_texture;

#define LARGE_FLOAT 1E+10
#define EPSILON 0.0001

layout (local_size_x = 16, local_size_y = 16) in;

void return_value(int value) {
    ivec3 pos = ivec3(gl_GlobalInvocationID.xy, gl_GlobalInvocationID.z);
    imageStore(output_texture, pos, ivec4(value));
}

/**
 * Entry point of this GLSL compute shader.
 */
void main(void) {
    ivec3 pos = ivec3(gl_GlobalInvocationID.x, gl_GlobalInvocationID.y, gl_GlobalInvocationID.z);
    return_value(2);
}
            """;

    public static void main(String[] args) {
        System.out.println("Starting...");
        int width = 10;
        {
            ComputeShader computeShader = ComputeShader.create(FLOAT_SOURCE);
            ComputeShader.FloatResult result = computeShader.computeFloat(Map.of(), width, width, width).join();
            FloatBuffer buffer = result.direct();
            FloatBuffer copy = FloatBuffer.allocate(width * width * width);
            for (int i = 0; i < width * width * width; i++) {
                copy.put(buffer.get());
            }
            System.out.println("float: " + Arrays.toString(copy.array()));
        }
        {
            ComputeShader computeShader = ComputeShader.create(INT_SOURCE);
            ComputeShader.IntResult result = computeShader.computeInt(Map.of(), width, width, width).join();
            IntBuffer buffer = result.direct();
            IntBuffer copy = IntBuffer.allocate(width * width * width);
            for (int i = 0; i < width * width * width; i++) {
                copy.put(buffer.get());
            }
            System.out.println("int: " + Arrays.toString(copy.array()));
        }

        // clean up the shaders
        System.gc();
    }
}
