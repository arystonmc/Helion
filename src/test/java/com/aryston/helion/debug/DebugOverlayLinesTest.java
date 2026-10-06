package com.aryston.helion.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugOverlayLinesTest {
    @Test
    void showsEverySectionWithItsValues() {
        JsonObject core = new JsonObject();
        core.addProperty("active", true);
        core.addProperty("passiveReason", "none");
        JsonObject snapshot = new JsonObject();
        snapshot.add("core", core);

        assertEquals(List.of("Helion core: active=true, passiveReason=none"), DebugOverlayLines.format(snapshot));
    }

    @Test
    void wrapsLongSectionsAfterFourValues() {
        JsonObject stages = new JsonObject();
        for (int index = 1; index <= 6; index++) {
            stages.addProperty("stage" + index, index);
        }
        JsonObject snapshot = new JsonObject();
        snapshot.add("gpuStages", stages);

        assertEquals(List.of(
            "Helion gpuStages: stage1=1, stage2=2, stage3=3, stage4=4",
            "    stage5=5, stage6=6"
        ), DebugOverlayLines.format(snapshot));
    }

    @Test
    void marksEmptySections() {
        JsonObject snapshot = new JsonObject();
        snapshot.add("gpuStages", new JsonObject());

        assertEquals(List.of("Helion gpuStages: -"), DebugOverlayLines.format(snapshot));
    }

    @Test
    void showsPlainValuesOnTheirOwnLine() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("note", "ok");

        assertEquals(List.of("Helion note: ok"), DebugOverlayLines.format(snapshot));
    }
}
