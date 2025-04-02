package ru.ytkab0bp.slicebeam.boot;

import java.io.File;

import ru.ytkab0bp.slicebeam.SliceBeam;

public class ClearModelCacheTask extends BootTask {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public ClearModelCacheTask() {
        super(()->{
            File cache = SliceBeam.getModelCacheDir();
            if (cache.exists()) {
                for (File f : cache.listFiles()) {
                    f.delete();
                }
            }
        });
        nonCritical = true;
        onWorker();
    }
}
