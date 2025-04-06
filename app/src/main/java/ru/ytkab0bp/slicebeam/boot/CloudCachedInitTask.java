package ru.ytkab0bp.slicebeam.boot;

import java.util.Collections;

import ru.ytkab0bp.slicebeam.cloud.CloudController;

public class CloudCachedInitTask extends BootTask {
    public CloudCachedInitTask() {
        super(Collections.singletonList(PrefsTask.class), CloudController::initCached);
        onWorker();
    }
}
