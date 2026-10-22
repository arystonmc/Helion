package com.aryston.helion.render.rtx;

record RtxTraceInputs(
    long maskView,
    long scene,
    long agreement,
    long agreementOffset,
    long agreementRange,
    long boxes,
    long boxesRange,
    long[] sampledViews
) {
}
