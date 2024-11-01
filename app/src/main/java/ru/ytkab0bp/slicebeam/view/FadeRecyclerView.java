package ru.ytkab0bp.slicebeam.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class FadeRecyclerView extends RecyclerView implements IThemeView {
    private final static int HEIGHT_DP = 32;

    private Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float topProgress, bottomProgress;
    private float overlayAlpha = 1f;

    public FadeRecyclerView(@NonNull Context context) {
        super(context);

        LinearLayoutManager llm = new LinearLayoutManager(context);
        setLayoutManager(llm);
        setWillNotDraw(false);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                topProgress = 1f;
                if (llm.findFirstVisibleItemPosition() == 0) {
                    View ch = llm.getChildAt(0);
                    int size = Math.min(ch.getHeight(), ViewUtils.dp(HEIGHT_DP) / 2);
                    topProgress = MathUtils.clamp(-ch.getTop() / (float) size, 0, 1);
                }
                bottomProgress = 1f;
                if (llm.findLastVisibleItemPosition() == recyclerView.getAdapter().getItemCount() - 1) {
                    View ch = llm.getChildAt(llm.getChildCount() - 1);
                    int size = Math.min(ch.getHeight(), ViewUtils.dp(HEIGHT_DP) / 2);
                    bottomProgress = MathUtils.clamp((ch.getBottom() - getHeight()) / (float) size, 0, 1);
                }
                invalidate();
            }
        });
        onApplyTheme();
    }

    public void setOverlayAlpha(float overlayAlpha) {
        this.overlayAlpha = overlayAlpha;
        invalidate();
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        if (topProgress > 0) {
            topPaint.setAlpha((int) (topProgress * overlayAlpha * 0xFF));
            c.drawRect(0, 0, getWidth(), ViewUtils.dp(HEIGHT_DP), topPaint);
        }
        if (bottomProgress > 0) {
            bottomPaint.setAlpha((int) (bottomProgress * overlayAlpha * 0xFF));
            c.drawRect(0, getHeight() - ViewUtils.dp(HEIGHT_DP), getWidth(), getHeight(), bottomPaint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateShaders();
    }

    private void invalidateShaders() {
        if (getWidth() == 0 || getHeight() == 0) return;

        topPaint.setShader(new LinearGradient(getWidth() / 2f, 0, getWidth() / 2f, ViewUtils.dp(HEIGHT_DP), ThemesRepo.getColor(android.R.attr.windowBackground), 0, Shader.TileMode.CLAMP));
        bottomPaint.setShader(new LinearGradient(getWidth() / 2f, getHeight() - ViewUtils.dp(HEIGHT_DP), getWidth() / 2f, getHeight(), 0, ThemesRepo.getColor(android.R.attr.windowBackground), Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override
    public void onApplyTheme() {
        invalidateShaders();
    }
}
