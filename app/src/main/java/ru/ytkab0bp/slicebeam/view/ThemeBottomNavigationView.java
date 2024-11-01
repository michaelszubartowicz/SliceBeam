package ru.ytkab0bp.slicebeam.view;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;

public class ThemeBottomNavigationView extends BottomNavigationView implements IThemeView {
    public ThemeBottomNavigationView(@NonNull Context context) {
        super(context);
        setItemTextAppearanceInactive(R.style.Theme_SliceBeam_NavigationTextFix);
        setItemTextAppearanceActive(R.style.Theme_SliceBeam_NavigationTextFixActive);
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

    @Override
    public int getMaxItemCount() {
        return 6;
    }
}
