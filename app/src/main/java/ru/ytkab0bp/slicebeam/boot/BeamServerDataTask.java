package ru.ytkab0bp.slicebeam.boot;

import org.json.JSONException;
import org.json.JSONObject;

import ru.ytkab0bp.slicebeam.BeamServerData;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class BeamServerDataTask extends BootTask {
    public BeamServerDataTask() {
        super(() -> {
            try {
                SliceBeam.SERVER_DATA = new BeamServerData(new JSONObject(Prefs.getBeamServerData()));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (System.currentTimeMillis() - Prefs.getLastCheckedInfo() >= 86400000L) {
                ViewUtils.postOnMainThread(BeamServerData::load);
            }
        });
        onWorker();
    }
}
