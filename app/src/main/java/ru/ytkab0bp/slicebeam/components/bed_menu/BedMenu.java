package ru.ytkab0bp.slicebeam.components.bed_menu;

import android.content.Context;
import android.view.View;

import androidx.annotation.CallSuper;

import ru.ytkab0bp.slicebeam.fragment.BedFragment;
import ru.ytkab0bp.slicebeam.slic3r.Bed3D;

public abstract class BedMenu {
    private View view;

    public abstract View onCreateView(Context ctx, boolean portrait);

    @CallSuper
    public void onViewCreated(View v) {
        view = v;
    }

    @CallSuper
    public void onViewDestroyed() {
        view = null;
    }

    public View getView() {
        return view;
    }

    public void onSetBed(BedFragment fragment) {}
}
