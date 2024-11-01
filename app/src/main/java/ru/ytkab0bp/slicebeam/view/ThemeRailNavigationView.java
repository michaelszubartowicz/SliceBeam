package ru.ytkab0bp.slicebeam.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.google.android.material.navigationrail.NavigationRailView;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;

public class ThemeRailNavigationView extends NavigationRailView implements IThemeView {
    public ThemeRailNavigationView(@NonNull Context context) {
        super(context);
        setMenuGravity(Gravity.CENTER);
        onApplyTheme();
    }

    @Override
    public void onApplyTheme() {
        setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
        setItemActiveIndicatorColor(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.colorAccent)));
        setItemTextColor(new ColorStateList(new int[][]{
                {android.R.attr.state_enabled, android.R.attr.state_checked},
                {android.R.attr.state_enabled, -android.R.attr.state_checked}
        }, new int[]{
                ThemesRepo.getColor(android.R.attr.colorAccent),
                ThemesRepo.getColor(android.R.attr.textColorSecondary)
        }));
        setItemIconTintList(new ColorStateList(new int[][]{
                {android.R.attr.state_enabled, android.R.attr.state_checked},
                {android.R.attr.state_enabled, -android.R.attr.state_checked}
        }, new int[]{
                ThemesRepo.getColor(R.attr.textColorOnAccent),
                ThemesRepo.getColor(android.R.attr.textColorSecondary)
        }));
        setItemRippleColor(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.colorControlHighlight)));
    }
}
