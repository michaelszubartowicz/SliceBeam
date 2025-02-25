package ru.ytkab0bp.slicebeam.components.bed_menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class BedMenuItem extends SimpleRecyclerItem<BedMenuItem.BedMenuItemHolderView> {
    public final int titleRes;
    public final int iconRes;
    public boolean isSingleLine;

    public boolean isEnabled = true;
    public boolean isChecked = false;
    public boolean isCheckable = false;
    public View.OnClickListener clickListener;
    public CompoundButton.OnCheckedChangeListener checkedChangeListener;

    public BedMenuItem(int titleRes, int iconRes) {
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }

    public BedMenuItem onClick(View.OnClickListener listener) {
        clickListener = listener;
        return this;
    }

    public BedMenuItem setCheckable(CompoundButton.OnCheckedChangeListener checkedChangeListener, boolean checked) {
        this.checkedChangeListener = checkedChangeListener;
        isCheckable = true;
        isChecked = checked;
        return this;
    }

    public BedMenuItem setEnabled(boolean enabled) {
        isEnabled = enabled;
        return this;
    }

    public BedMenuItem setSingleLine(boolean singleLine) {
        isSingleLine = singleLine;
        return this;
    }

    @Override
    public BedMenuItemHolderView onCreateView(Context ctx) {
        return new BedMenuItemHolderView(ctx);
    }

    @Override
    public void onBindView(BedMenuItemHolderView view) {
        view.bind(this);
    }

    public final static class BedMenuItemHolderView extends LinearLayout implements IThemeView {
        private ImageView icon;
        private TextView title;

        private Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Path path = new Path();
        private float checkedProgress;
        private boolean enabled;

        public BedMenuItemHolderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            icon = new ImageView(context);
            addView(icon, new LinearLayout.LayoutParams(ViewUtils.dp(24), ViewUtils.dp(24)));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                topMargin = ViewUtils.dp(2);
            }});

            setPadding(ViewUtils.dp(8), ViewUtils.dp(6), ViewUtils.dp(8), ViewUtils.dp(6));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT) {{
                leftMargin = topMargin = bottomMargin = ViewUtils.dp(6);
            }});
            setWillNotDraw(false);
            onApplyTheme();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int rad = ViewUtils.dp(16);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), rad, rad, bgPaint);

            if (enabled && checkedProgress != 0f) {
                if (checkedProgress == 1f) {
                    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), rad, rad, accentPaint);
                } else {
                    path.rewind();
                    path.addRoundRect(0, 0, getWidth(), getHeight(), rad, rad, Path.Direction.CW);

                    canvas.save();
                    canvas.clipPath(path);
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (float) (Math.sqrt(getWidth() * getWidth() + getHeight() * getHeight()) / 2f * checkedProgress), accentPaint);
                    canvas.restore();
                }
            }

            super.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.getMode(heightMeasureSpec)), heightMeasureSpec);
        }

        public void bind(BedMenuItem item) {
            enabled = item.isEnabled;
            title.setMaxLines(item.isSingleLine ? 1 : 2);
            title.setText(item.titleRes);
            icon.setImageResource(item.iconRes);
            checkedProgress = item.isCheckable && item.isChecked ? 1 : 0;
            title.setTextColor(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorPrimary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress));
            icon.setImageTintList(ColorStateList.valueOf(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorSecondary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress)));

            if (item.checkedChangeListener != null) {
                setOnClickListener(v -> {
                    item.isChecked = !item.isChecked;
                    new SpringAnimation(new FloatValueHolder(item.isChecked ? 0 : 1))
                            .setMinimumVisibleChange(1 / 256f)
                            .setSpring(new SpringForce(item.isChecked ? 1 : 0)
                                    .setStiffness(1000f)
                                    .setDampingRatio(1f))
                            .addUpdateListener((animation, value, velocity) -> {
                                checkedProgress = value;
                                title.setTextColor(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorPrimary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress));
                                icon.setImageTintList(ColorStateList.valueOf(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorSecondary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress)));

                                invalidate();
                            })
                            .start();

                    item.checkedChangeListener.onCheckedChanged(null, item.isChecked);
                });
            } else {
                setOnClickListener(item.clickListener);
            }
            setClickable(item.isEnabled);
            setAlpha(item.isEnabled ? 1f : 0.6f);
        }

        @Override
        public void onApplyTheme() {
            title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            icon.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
            setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 16));
            bgPaint.setColor(ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10));
            accentPaint.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
        }
    }
}
