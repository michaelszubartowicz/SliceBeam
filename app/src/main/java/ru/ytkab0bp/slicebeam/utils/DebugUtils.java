package ru.ytkab0bp.slicebeam.utils;

import ru.ytkab0bp.slicebeam.BuildConfig;

public class DebugUtils {
    public static void assertTrue(boolean value) {
        throwIfNot(value);
    }

    public static void assertFalse(boolean value) {
        throwIfNot(!value);
    }

    private static void throwIfNot(boolean value) {
        if (!BuildConfig.DEBUG) return;
        if (!value) {
            throw new AssertionError("Assert failed");
        }
    }
}
