package com.aryston.helion.render.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ShaderCompilationTest {
    private static final String SHADER_ROOT = "assets/helion/shaders";
    private static final String INCLUDE_PATH = "assets/%s/shaders/include/%s";
    private static final String FRAGMENT_EXTENSION = ".fsh";
    private static final Pattern INCLUDE = Pattern.compile("^#include <([a-z0-9_]+):([a-z0-9_./]+)>$", Pattern.MULTILINE);
    private static final Pattern VERSION = Pattern.compile("^#version .*$", Pattern.MULTILINE);
    private static final String ZERO_TO_ONE_DEFINE = "#define RENDERPEARL_DEPTH_IS_ZERO_TO_ONE";
    private static final String VALIDATOR = "glslangValidator";
    private static final long VALIDATOR_TIMEOUT_SECONDS = 60;
    private static final int SUCCESS = 0;

    @TempDir
    static Path workDirectory;

    @TestFactory
    Stream<DynamicTest> everyFragmentShaderCompiles() throws IOException, URISyntaxException {
        assumeTrue(validatorInstalled(), VALIDATOR + " is not installed, shader compilation is not checked");
        List<Path> shaders = fragmentShaders();
        assertFalse(shaders.isEmpty(), "no fragment shaders found under " + SHADER_ROOT);
        return shaders.stream().flatMap(shader -> Stream.of(
            DynamicTest.dynamicTest(name(shader) + " with depth -1 to 1", () -> compile(shader, false)),
            DynamicTest.dynamicTest(name(shader) + " with depth 0 to 1", () -> compile(shader, true))
        ));
    }

    private static void compile(Path shader, boolean zeroToOneDepth) throws IOException, InterruptedException {
        String source = expand(Files.readString(shader, StandardCharsets.UTF_8), new HashSet<>());
        if (zeroToOneDepth) {
            source = VERSION.matcher(source).replaceFirst(match -> Matcher.quoteReplacement(match.group() + "\n" + ZERO_TO_ONE_DEFINE));
        }
        String fileName = name(shader).replace(FRAGMENT_EXTENSION, "").replace('/', '_') + (zeroToOneDepth ? "_zero_to_one" : "");
        Path input = workDirectory.resolve(fileName + ".frag");
        Files.writeString(input, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(
            VALIDATOR, "-G", "--auto-map-bindings", "--auto-map-locations",
            "-o", workDirectory.resolve(fileName + ".spv").toString(), input.toString()
        ).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(VALIDATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS), VALIDATOR + " timed out");
        assertEquals(SUCCESS, process.exitValue(), name(shader) + "\n" + output);
    }

    private static String expand(String source, Set<String> included) throws IOException {
        Matcher includes = INCLUDE.matcher(source);
        StringBuilder expanded = new StringBuilder();
        while (includes.find()) {
            String resource = INCLUDE_PATH.formatted(includes.group(1), includes.group(2));
            String replacement = included.add(resource) ? expand(readResource(resource), included) : "";
            includes.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        includes.appendTail(expanded);
        return expanded.toString();
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream stream = ShaderCompilationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "include not found on the classpath: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Path> fragmentShaders() throws IOException, URISyntaxException {
        URL root = ShaderCompilationTest.class.getClassLoader().getResource(SHADER_ROOT);
        assertNotNull(root, SHADER_ROOT);
        try (Stream<Path> files = Files.walk(Path.of(root.toURI()))) {
            return files.filter(file -> file.toString().endsWith(FRAGMENT_EXTENSION)).sorted().toList();
        }
    }

    private static String name(Path shader) {
        String path = shader.toString().replace('\\', '/');
        return path.substring(path.indexOf(SHADER_ROOT) + SHADER_ROOT.length() + 1);
    }

    private static boolean validatorInstalled() {
        try {
            Process process = new ProcessBuilder(VALIDATOR, "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(VALIDATOR_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == SUCCESS;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
