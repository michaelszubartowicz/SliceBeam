package ru.ytkab0bp.slicebeam.events;

import ru.ytkab0bp.eventbus.Event;

@Event
public class LongClickTranslationEvent {
    public final double x;
    public final double y;
    public final boolean visual;

    public LongClickTranslationEvent(double x, double y, boolean visual) {
        this.x = x;
        this.y = y;
        this.visual = visual;
    }
}
