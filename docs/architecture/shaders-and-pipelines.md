# Helion Architecture: Shaders and Pipelines

Part of the [Helion architecture](../ARCHITECTURE.md).

## Shaders and Pipelines

- Shaders live in `src/main/resources/assets/helion/shaders/<feature>/*.fsh`, shared code in `shaders/include/helion_*.glsl`, included as `#include <helion:helion_view.glsl>`.
- GLSL rules of 26.3: `#version 330`, `GL_ARB_separate_shader_objects`, explicit `layout(location)`, no `layout(binding)`, uniforms bound by name, `gl_VertexIndex`.
- Fullscreen passes use the vanilla `minecraft:core/screenquad` vertex shader and `FullscreenPass.draw` (three vertices, no vertex buffer).
- Every pipeline is listed in `HelionPipelines.all()` and registered as optional through `RegisterRenderPipelinesEvent`. Stages look them up with `HelionPipelines.compiled`; a missing pipeline makes the stage skip itself instead of crashing.
- Never sample a depth or color texture that is attached to the same render pass.
- `ShaderCompilationTest` compiles every fragment shader with `glslangValidator` on every build where it is installed (the CI installs it; on Windows it comes with the Vulkan SDK), so a broken shader fails the build instead of only logging a missing pipeline in game.
