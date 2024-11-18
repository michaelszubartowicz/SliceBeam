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

    public double getUsedFilamentMM(@GCodeViewer.ExtrusionRole int role) {
        return Native.gcoderesult_get_used_filament_mm(pointer, role);
    }

    public double getUsedFilamentG(@GCodeViewer.ExtrusionRole int role) {
        return Native.gcoderesult_get_used_filament_g(pointer, role);
    }

    public String getRecommendedName() {
        return Native.gcoderesult_get_recommended_name(pointer);
    }

    public void release() {
        Native.gcoderesult_release(pointer);
    }
}
