package ru.ytkab0bp.slicebeam.boot;

import ru.ytkab0bp.eventbus.EventBus;
import ru.ytkab0bp.slicebeam.BuildConfig;

public class EventBusTask extends BootTask {

    public EventBusTask() {
        super(() -> EventBus.registerImpl(BuildConfig.APPLICATION_ID));
        onWorker();
    }
}
