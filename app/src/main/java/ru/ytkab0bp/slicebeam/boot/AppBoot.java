package ru.ytkab0bp.slicebeam.boot;

import android.util.Log;
import android.util.SparseBooleanArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AppBoot {
    static ExecutorService executor = Executors.newCachedThreadPool();
    static List<BootTask> tasks;
    static List<Runnable> pendingMain = new ArrayList<>();
    static List<BootTask> pendingTasks = new ArrayList<>();
    static SparseBooleanArray completed = new SparseBooleanArray();
    static CountDownLatch latch;

    public static void run(List<BootTask> tasks) {
        long start = System.currentTimeMillis();
        AppBoot.tasks = tasks;
        AppBoot.latch = new CountDownLatch(tasks.size());

        for (int i = 0, s = tasks.size(); i < s; i++) {
            BootTask task = tasks.get(i);
            task.index = i;
            tryRunTask(task, true, false);
        }
        try {
            while (!latch.await(50, TimeUnit.MILLISECONDS)) {
                if (!pendingMain.isEmpty()) {
                    List<Runnable> clone = new ArrayList<>(pendingMain);
                    for (Runnable r : clone) {
                        r.run();
                    }
                    pendingMain.removeAll(clone);
                }
            }
            Log.d("boot", "Boot in " + (System.currentTimeMillis() - start) + "ms");
            executor.shutdown();
            executor = null;
            pendingMain = null;
            pendingTasks = null;
            completed = null;
            latch = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void tryRunTask(BootTask task, boolean fromMain, boolean isContinue) {
        if (checkDependencies(task.dependencies)) {
            if (task.workerThread) {
                executor.submit(() -> {
                    task.run.run();
                    completed.put(task.index, true);
                    latch.countDown();

                    if (!isContinue) {
                        continueTasks(fromMain);
                    }
                });
            } else {
                Runnable r = () -> {
                    task.run.run();
                    completed.put(task.index, true);
                    latch.countDown();

                    if (!isContinue) {
                        continueTasks(fromMain);
                    }
                };
                if (fromMain) {
                    r.run();
                } else {
                    pendingMain.add(r);
                }
            }
        } else {
            pendingTasks.add(task);
        }
    }

    private static void continueTasks(boolean fromMain) {
        for (Iterator<BootTask> it = pendingTasks.iterator(); it.hasNext();) {
            BootTask task = it.next();
            if (checkDependencies(task.dependencies)) {
                tryRunTask(task, fromMain, true);
                it.remove();
            }
        }
    }

    private static boolean checkDependencies(List<Class<?>> clzs) {
        if (clzs.isEmpty()) {
            return true;
        }

        for (int i = 0, s = tasks.size(); i < s; i++) {
            if (clzs.contains(tasks.get(i).getClass())) {
                if (!completed.get(i)) {
                    return false;
                }
            }
        }
        return true;
    }
}
