# Helion Architecture: Layers

Part of the [Helion architecture](../ARCHITECTURE.md).

## Layers

```
Minecraft LevelRenderer.render
   │  LevelRendererMixin (@WrapMethod): Helion active → Helion frame, otherwise vanilla
   ▼
integration/vanilla          Minecraft adapter: builds the frame, owns every vanilla call
   │  LevelRenderHook → VanillaFrameDriver → Vanilla*Source, VanillaFrameTargets, StageEvents
   ▼
render/                      Helion render core: stages, graph, resources, camera
   │  FrameStages: clear → sky → opaque geometry → deferred lighting → ambient occlusion → solid features → transparent geometry → temporal anti-aliasing → post (bloom, sharpen) → present or image composite → geometry buffer view
   ▼
renderpearl api + blaze3d    Mojang GPU abstraction (Vulkan backend)
```

| Layer | Packages | May use |
|---|---|---|
| Render core | `render`, `render.*` | Java, JOML, `com.mojang.blaze3d.*`, `com.mojang.renderpearl.api.*`, and `net.minecraft.resources.Identifier` because the renderpearl API itself uses it for pipeline and shader ids |
| Minecraft adapter | `integration`, `integration.vanilla` | Everything, including vanilla internals through `LevelRendererAccessor` |
| Mixins | `mixin` | Only the hooks listed below |
| Tools | `debug`, `config` | Render core and Minecraft client APIs |
