package ru.ytkab0bp.slicebeam.boot;

import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.utils.VibrationUtils;

public class VibrationUtilsTask extends BootTask {

    public VibrationUtilsTask() {
        super(() -> VibrationUtils.init(SliceBeam.INSTANCE));
        onWorker();
    }
}
