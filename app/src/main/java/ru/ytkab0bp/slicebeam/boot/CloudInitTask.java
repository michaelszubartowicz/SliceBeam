package ru.ytkab0bp.slicebeam.boot;

import java.util.Arrays;

import ru.ytkab0bp.slicebeam.cloud.CloudController;

public class CloudInitTask extends BootTask {
    public CloudInitTask() {
        super(Arrays.asList(PrefsTask.class, TrueTimeTask.class, LoadSlic3rConfigTask.class), CloudController::init);
        onWorker();
    }
}
