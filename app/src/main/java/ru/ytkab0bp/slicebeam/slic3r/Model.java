package ru.ytkab0bp.slicebeam.slic3r;

import java.io.File;
import java.util.UUID;

import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class Model {
    public final String key = UUID.randomUUID().toString();
    final long pointer;

    private double[] boundingExact;
    private double[] boundingApprox;

    public Model() {
        this(Native.model_create());
    }

    public Model(File f) throws Slic3rRuntimeError {
        this(f.getAbsolutePath());
    }

    public Model(String path) throws Slic3rRuntimeError {
        this(Native.model_read_from_file(path, getBaseName(path)));
    }

    private Model(long ptr) {
        this.pointer = ptr;
    }

    private static String getBaseName(String path) {
        if (path.contains("/")) {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        if (path.contains(".")) {
            path = path.substring(0, path.lastIndexOf('.'));
        }
        return path;
    }

    public void getBoundingBoxExact(int i, Vec3d min, Vec3d max) {
        double[] data = Native.model_get_bounding_box_exact(pointer, i);
        min.set(data[0], data[1], data[2]);
        max.set(data[3], data[4], data[5]);
    }

    public void getBoundingBoxApprox(int i, Vec3d min, Vec3d max) {
        double[] data = Native.model_get_bounding_box_approx(pointer, i);
        min.set(data[0], data[1], data[2]);
        max.set(data[3], data[4], data[5]);
    }

    public Vec3d getBoundingBoxExactMin() {
        if (boundingExact == null) boundingExact = Native.model_get_bounding_box_exact_global(pointer);
        return new Vec3d(boundingExact[0], boundingExact[1], boundingExact[2]);
    }

    public Vec3d getBoundingBoxExactMax() {
        if (boundingExact == null) boundingExact = Native.model_get_bounding_box_exact_global(pointer);
        return new Vec3d(boundingExact[3], boundingExact[4], boundingExact[5]);
    }

    public Vec3d getBoundingBoxApproxMin() {
        if (boundingApprox == null) boundingApprox = Native.model_get_bounding_box_approx_global(pointer);
        return new Vec3d(boundingApprox[0], boundingApprox[1], boundingApprox[2]);
    }

    public Vec3d getBoundingBoxApproxMax() {
        if (boundingApprox == null) boundingApprox = Native.model_get_bounding_box_approx_global(pointer);
        return new Vec3d(boundingApprox[3], boundingApprox[4], boundingApprox[5]);
    }

    public void resetBoundingBox() {
        boundingExact = null;
        boundingApprox = null;
    }

    public void translate(int i, double x, double y, double z) {
        Native.model_translate(pointer, i, x, y, z);
    }

    public void translate(double x, double y, double z) {
        Native.model_translate_global(pointer, x, y, z);
        resetBoundingBox();
    }

    public void scale(int i, double x, double y, double z) {
        Native.model_scale(pointer, i, x, y, z);
    }

    public void rotate(int i, double x, double y, double z) {
        Native.model_rotate(pointer, i, x, y, z);
    }

    public int getObjectsCount() {
        return Native.model_get_objects_count(pointer);
    }

    public void addObject(Model from, int i) {
        Native.model_add_object_from_another(pointer, from.pointer, i);
    }

    public void deleteObject(int i) {
        Native.model_delete_object(pointer, i);
    }

    public void getTranslation(int i, Vec3d vec) {
        double[] tr = Native.model_get_translation(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public void getRotation(int i, Vec3d vec) {
        double[] tr = Native.model_get_rotation(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public boolean isLeftHanded(int i) {
        return Native.model_is_left_handed(pointer, i);
    }

    public void getScale(int i, Vec3d vec) {
        double[] tr = Native.model_get_scale(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public void getMirror(int i, Vec3d vec) {
        double[] tr = Native.model_get_mirror(pointer, i);
        vec.set(tr[0], tr[1], tr[2]);
    }

    public GCodeProcessorResult slice(String configPath, String gcodePath, SliceListener listener) throws Slic3rRuntimeError {
        return new GCodeProcessorResult(Native.model_slice(pointer, configPath, gcodePath, listener));
    }

    public void release() {
        Native.model_release(pointer);
    }

    public static Model merge(Model... models) {
        long[] ptrs = new long[models.length];
        for (int i = 0, modelsSize = models.length; i < modelsSize; i++) {
            Model m = models[i];
            ptrs[i] = m.pointer;
        }
        return new Model(Native.models_merge(ptrs));
    }
}
