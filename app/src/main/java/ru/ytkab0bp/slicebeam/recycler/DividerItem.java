package ru.ytkab0bp.slicebeam.recycler;

import android.content.Context;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.DividerView;

public class DividerItem extends SimpleRecyclerItem<DividerView> {
    @Override
    public DividerView onCreateView(Context ctx) {
        DividerView v = new DividerView(ctx);
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1)) {{
            leftMargin = rightMargin = ViewUtils.dp(16);
            topMargin = bottomMargin = ViewUtils.dp(6);
        }});
        return v;
    }
}
