package ru.ytkab0bp.slicebeam.boot;

import androidx.annotation.NonNull;

import com.instacart.truetime.TrueTimeEventListener;
import com.instacart.truetime.time.TrueTimeImpl;
import com.instacart.truetime.time.TrueTimeParameters;

import java.net.InetAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import kotlinx.coroutines.Dispatchers;
import ru.ytkab0bp.slicebeam.SliceBeam;

public class TrueTimeTask extends BootTask {
    public TrueTimeTask() {
        super(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            SliceBeam.TRUE_TIME = new TrueTimeImpl(new TrueTimeParameters.Builder().buildParams(), Dispatchers.getIO(), new TrueTimeEventListener() {
                @Override
                public void initialize(@NonNull TrueTimeParameters trueTimeParameters) {}

                @Override
                public void initializeSuccess(@NonNull long[] longs) {
                    latch.countDown();
                }

                @Override
                public void initializeFailed(@NonNull Exception e) {}

                @Override
                public void nextInitializeIn(long l) {}

                @Override
                public void resolvedNtpHostToIPs(@NonNull String s, @NonNull List<? extends InetAddress> list) {}

                @Override
                public void lastSntpRequestAttempt(@NonNull InetAddress inetAddress) {}

                @Override
                public void sntpRequestFailed(@NonNull Exception e) {}

                @Override
                public void syncDispatcherException(@NonNull Throwable throwable) {}

                @Override
                public void sntpRequest(@NonNull InetAddress inetAddress) {}

                @Override
                public void sntpRequestSuccessful(@NonNull InetAddress inetAddress) {}

                @Override
                public void sntpRequestFailed(@NonNull InetAddress inetAddress, @NonNull Exception e) {}

                @Override
                public void storingTrueTime(@NonNull long[] longs) {}

                @Override
                public void returningTrueTime(@NonNull Date date) {}

                @Override
                public void returningDeviceTime() {}
            });
            SliceBeam.TRUE_TIME.sync();
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
        });
        onWorker();
        nonCritical = true;
    }
}
