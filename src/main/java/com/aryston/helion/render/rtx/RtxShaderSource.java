package com.aryston.helion.render.rtx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

final class RtxShaderSource {
    private static final Pattern INCLUDE = Pattern.compile("^[ \\t]*#include <([a-z0-9_]+):([a-z0-9_./]+)>[ \\t]*$", Pattern.MULTILINE);
    private static final String INCLUDE_PATH = "/assets/%s/shaders/include/%s";
    private static final String ENTRY = "main";
    private static final String DEFINE = "#define %s\n";
    private static final char LINE_END = '\n';

    private RtxShaderSource() {
    }

    static ByteBuffer compileCompute(String resource, String name) {
        return compileCompute(resource, name, List.of());
    }

    static ByteBuffer compileCompute(String resource, String name, List<String> defines) {
        String source = defined(expand(read(resource), new HashSet<>()), defines);
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_5);
            Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_performance);
            long result = Shaderc.shaderc_compile_into_spv(compiler, source, Shaderc.shaderc_glsl_compute_shader, name, ENTRY, options);
            try {
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                    throw new IllegalStateException("Could not compile " + name + ": " + Shaderc.shaderc_result_get_error_message(result));
                }
                ByteBuffer bytes = Objects.requireNonNull(Shaderc.shaderc_result_get_bytes(result), name);
                ByteBuffer copy = MemoryUtil.memAlloc(bytes.remaining());
                MemoryUtil.memCopy(bytes, copy);
                return copy;
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    static String entry() {
        return ENTRY;
    }

    private static String defined(String source, List<String> defines) {
        int afterVersion = source.indexOf(LINE_END) + 1;
        StringBuilder directives = new StringBuilder();
        defines.forEach(define -> directives.append(DEFINE.formatted(define)));
        return source.substring(0, afterVersion) + directives + source.substring(afterVersion);
    }

    private static String expand(String source, Set<String> included) {
        Matcher includes = INCLUDE.matcher(source);
        StringBuilder expanded = new StringBuilder();
        while (includes.find()) {
            String resource = INCLUDE_PATH.formatted(includes.group(1), includes.group(2));
            String replacement = included.add(resource) ? expand(read(resource), included) : "";
            includes.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        includes.appendTail(expanded);
        return expanded.toString();
    }

    private static String read(String resource) {
        try (InputStream input = Objects.requireNonNull(RtxShaderSource.class.getResourceAsStream(resource), resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
