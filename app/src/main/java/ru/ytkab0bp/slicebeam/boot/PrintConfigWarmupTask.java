package ru.ytkab0bp.slicebeam.boot;

import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef;

public class PrintConfigWarmupTask extends BootTask {
    public PrintConfigWarmupTask() {
        super(PrintConfigDef::getInstance);
        onWorker();
    }
}
