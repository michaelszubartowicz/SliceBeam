package ru.ytkab0bp.slicebeam.boot;

import com.instacart.library.truetime.TrueTime;

import java.io.IOException;

public class TrueTimeTask extends BootTask {
    /** @noinspection BusyWait*/
    public TrueTimeTask() {
        super(() -> {
            while (true) {
                try {
                    TrueTime.build().withNtpHost("0.ru.pool.ntp.org").initialize();
                    break;
                } catch (IOException ignore) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
        onWorker();
    }
}
