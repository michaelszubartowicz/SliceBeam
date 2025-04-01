package ru.ytkab0bp.slicebeam.boot;

import java.io.File;
import java.io.IOException;

import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class LoadSlic3rConfigTask extends BootTask {
    public LoadSlic3rConfigTask() {
        super(() -> {
            File cfgFile = SliceBeam.getConfigFile();
            SliceBeam.getCurrentConfigFile().delete();
            if (cfgFile.exists()) {
                try {
                    SliceBeam.CONFIG = new Slic3rConfigWrapper(cfgFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        onWorker();
    }
}
