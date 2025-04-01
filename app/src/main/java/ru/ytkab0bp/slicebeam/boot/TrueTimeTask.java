package ru.ytkab0bp.slicebeam.boot;

import com.instacart.library.truetime.TrueTime;

import java.io.IOException;

public class TrueTimeTask extends BootTask {
    public TrueTimeTask() {
        super(() -> {
            for (int i = 0; i < 2; i++) {
                try {
                    TrueTime.build().withNtpHost("1.ru.pool.ntp.org").initialize();
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
