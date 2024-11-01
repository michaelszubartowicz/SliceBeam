package ru.ytkab0bp.slicebeam.slic3r;

import java.io.File;

public class GCodeProcessorResult {
    final long pointer;

    public GCodeProcessorResult(File f) {
        pointer = Native.gcoderesult_load_file(f.getAbsolutePath(), f.getName());
    }

    GCodeProcessorResult(long ptr) {
        pointer = ptr;
    }

    public String getRecommendedName() {
        return Native.gcoderesult_get_recommended_name(pointer);
    }

    public void release() {
        Native.gcoderesult_release(pointer);
    }
}
