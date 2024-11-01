package ru.ytkab0bp.slicebeam.events;

import ru.ytkab0bp.eventbus.Event;
import ru.ytkab0bp.slicebeam.SliceBeam;

@Event
public class NeedSnackbarEvent {
    public final CharSequence title;

    public NeedSnackbarEvent(CharSequence title) {
        this.title = title;
    }

    public NeedSnackbarEvent(int title, Object... args) {
        this.title = SliceBeam.INSTANCE.getString(title, args);
    }
}
