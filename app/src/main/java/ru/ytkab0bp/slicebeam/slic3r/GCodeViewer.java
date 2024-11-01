package ru.ytkab0bp.slicebeam.slic3r;

import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.graphics.Color;

import androidx.core.util.Pair;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;

public class GCodeViewer {
    private ThreadLocal<float[]> viewMatrixBuffer = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[16];
        }
    };
    private ThreadLocal<float[]> projectionMatrixBuffer = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[16];
        }
    };

    private final long pointer;

    public GCodeViewer() {
        pointer = Native.vgcode_create();
    }

    public boolean isInitialized() {
        return Native.vgcode_is_initialized(pointer);
    }

    public void initGL() {
        Native.vgcode_init(pointer);
    }

    public void load(GCodeProcessorResult result) {
        Native.vgcode_load(pointer, result.pointer);
    }

    public void setLayersViewRange(long min, long max) {
        Native.vgcode_set_layers_view_range(pointer, min, max);
    }

    public Pair<Long, Long> getLayersViewRange() {
        long[] data = Native.vgcode_get_layers_view_range(pointer);
        return new Pair<>(data[0], data[1] < 0 ? getLayersCount() : data[1]);
    }

    public long getLayersCount() {
        return Native.vgcode_get_layers_count(pointer);
    }

    public void render(double[] viewMatrix, double[] projectionMatrix) {
        assertTrue(viewMatrix.length == 16);
        assertTrue(projectionMatrix.length == 16);

        float[] vmFloats = viewMatrixBuffer.get();
        for (int i = 0; i < viewMatrix.length; i++) {
            vmFloats[i] = (float) viewMatrix[i];
        }
        float[] pmFloats = projectionMatrixBuffer.get();
        for (int i = 0; i < projectionMatrix.length; i++) {
            pmFloats[i] = (float) projectionMatrix[i];
        }
        Native.vgcode_render(pointer, vmFloats, pmFloats);
    }

    public void setThemeColors() {
        setColors(
                ThemesRepo.getColor(R.attr.gcodeViewerSkirt),
                ThemesRepo.getColor(R.attr.gcodeViewerExternalPerimeter),
                ThemesRepo.getColor(R.attr.gcodeViewerSupportMaterial),
                ThemesRepo.getColor(R.attr.gcodeViewerSupportMaterialInterface),
                ThemesRepo.getColor(R.attr.gcodeViewerInternalInfill),
                ThemesRepo.getColor(R.attr.gcodeViewerSolidInfill),
                ThemesRepo.getColor(R.attr.gcodeViewerWipeTower)
        );
    }

    public void setColors(int skirt, int externalPerimeter, int supportMaterial, int supportMaterialInterface, int internalInfill, int solidInfill, int wipeTower) {
        Native.vgcode_set_colors(pointer, new int[] {
                Color.red(skirt), Color.green(skirt), Color.blue(skirt),
                Color.red(externalPerimeter), Color.green(externalPerimeter), Color.blue(externalPerimeter),
                Color.red(supportMaterial), Color.green(supportMaterial), Color.blue(supportMaterial),
                Color.red(supportMaterialInterface), Color.green(supportMaterialInterface), Color.blue(supportMaterialInterface),
                Color.red(internalInfill), Color.green(internalInfill), Color.blue(internalInfill),
                Color.red(solidInfill), Color.green(solidInfill), Color.blue(solidInfill),
                Color.red(wipeTower), Color.green(wipeTower), Color.blue(wipeTower)
        });
    }

    public void reset() {
        Native.vgcode_reset(pointer);
    }

    public void release() {
        Native.vgcode_release(pointer);
    }
}
