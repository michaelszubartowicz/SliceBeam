package ru.ytkab0bp.slicebeam.boot;

import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.utils.Prefs;

public class PrefsTask extends BootTask {
    public PrefsTask() {
        super(()->Prefs.init(SliceBeam.INSTANCE));
    }
}
