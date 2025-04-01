package ru.ytkab0bp.slicebeam.boot;

import java.io.IOException;

import ru.ytkab0bp.slicebeam.SliceBeam;

public class CheckUpdateJsonTask extends BootTask {
    public CheckUpdateJsonTask() {
        super(() -> {
            try {
                SliceBeam.INSTANCE.getAssets().open("update.json").close();
                SliceBeam.hasUpdateInfo = true;
            } catch (IOException e) {
                SliceBeam.hasUpdateInfo = false;
            }
        });
        onWorker();
    }
}
