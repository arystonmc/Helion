package com.aryston.helion.render.rtx;

import java.util.List;

record RtxScenePlan(List<RtxSectionBlas> builds, long scratchBase, int builtSections, int pendingSections, int visibleSections, long builtBytes) {
}
