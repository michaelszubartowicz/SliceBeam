package ru.ytkab0bp.slicebeam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Region;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.ViewConfiguration;

import java.nio.IntBuffer;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.render.GLRenderer;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class GLView extends GLSurfaceView implements IThemeView {
    private GLRenderer renderer;

    private float lastX, lastY;
    private float lastLength;
    private int touchSlop;

    private boolean fromTwoPointers;
    private boolean isRotating;
    private boolean isMoving;
    private boolean isScaling;

    private long lastActionTime = System.currentTimeMillis();

    private Path path = new Path();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint xferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TextPaint invalidBedText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout invalidBedDescriptionLayout;

    private float lastScale;

    private long lastDraw;
    private float invalidOffset;

    public GLView(Context context) {
        super(context);

        xferPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        setEGLContextClientVersion(3);
        renderer = new GLRenderer(this);

        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
    }

    public void arrange() {
        if (renderer.getModel() == null) return;

        queueEvent(() -> {
            renderer.getBed().arrange(renderer.getModel());
            renderer.resetGlModels();
            requestRender();
        });
    }

    public GLRenderer getRenderer() {
        return renderer;
    }

    public void drawOverlay(Canvas canvas, boolean toBitmap) {
        long dt = Math.min(System.currentTimeMillis() - lastDraw, 16);
        lastDraw = System.currentTimeMillis();

        int rad = ViewUtils.dp(16);
        float offsetX = getTranslationX(), offsetY = -getTranslationY();
        if (toBitmap) {
            paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground));
            canvas.drawRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, paint);
            canvas.drawRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, rad, rad, xferPaint);
        } else {
            path.rewind();
            path.addRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, rad, rad, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(path, Region.Op.DIFFERENCE);
            canvas.drawColor(ThemesRepo.getColor(android.R.attr.windowBackground));
            canvas.restore();

            if (getRenderer().getBed() != null && !getRenderer().getBed().isValid()) {
                invalidOffset += dt / 10000f;

                paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground));
                int size = ViewUtils.dp(200);
                canvas.drawRect(0, (getHeight() - size) / 2f, getWidth(), (getHeight() + size) / 2f, paint);

                double angle = Math.toRadians(60);
                int stableWidth = ViewUtils.dp(16);
                int lineHeight = ViewUtils.dp(16);
                int lineWidth = ViewUtils.dp(16 + (float) (32 * Math.sin(angle)));
                Path linePath = new Path();
                linePath.moveTo(0, 0);
                linePath.lineTo(stableWidth, 0);
                linePath.lineTo(lineWidth, lineHeight);
                linePath.lineTo(lineWidth - stableWidth, lineHeight);
                linePath.lineTo(0, 0);
                linePath.close();

                paint.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
                int x = (int) (-lineWidth - invalidOffset * getWidth());
                while (x < getWidth()) {
                    canvas.save();
                    canvas.translate(x, (getHeight() - size) / 2f);
                    canvas.drawPath(linePath, paint);

                    canvas.translate(stableWidth, 0);
                    int alpha = paint.getAlpha();
                    paint.setAlpha((int) (alpha * 0.5f));
                    canvas.drawPath(linePath, paint);
                    canvas.restore();
                    paint.setAlpha(alpha);

                    x += stableWidth * 2;
                }

                x = (int) (getWidth() + lineWidth + invalidOffset * getWidth());
                while (x >= -lineWidth) {
                    canvas.save();
                    canvas.translate(x, (getHeight() + size) / 2f - lineHeight);
                    canvas.drawPath(linePath, paint);

                    canvas.translate(-stableWidth, 0);
                    int alpha = paint.getAlpha();
                    paint.setAlpha((int) (alpha * 0.5f));
                    canvas.drawPath(linePath, paint);
                    canvas.restore();
                    paint.setAlpha(alpha);

                    x -= stableWidth * 2;
                }

                if (invalidBedDescriptionLayout == null) {
                    invalidBedText.setTextSize(ViewUtils.dp(16));
                    invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
                    invalidBedText.setTypeface(Typeface.DEFAULT);
                    invalidBedDescriptionLayout = new StaticLayout(getContext().getString(R.string.BedConfigurationErrorDesc), invalidBedText, getWidth() - ViewUtils.dp(32), Layout.Alignment.ALIGN_CENTER, 1, 0, false);
                }

                int realTextSize = ViewUtils.dp(22);
                int padding = ViewUtils.dp(12);
                int totalHeight = realTextSize + invalidBedDescriptionLayout.getHeight();

                invalidBedText.setTextSize(ViewUtils.dp(18));
                invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
                invalidBedText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                String errString = getContext().getString(R.string.BedConfigurationError);
                canvas.drawText(errString, 0, errString.length(), (getWidth() - invalidBedText.measureText(errString)) / 2f, getHeight() / 2f - totalHeight / 2f + realTextSize - padding / 2f, invalidBedText);

                invalidBedText.setTextSize(ViewUtils.dp(16));
                invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
                invalidBedText.setTypeface(Typeface.DEFAULT);

                canvas.save();
                canvas.translate((getWidth() - invalidBedDescriptionLayout.getWidth()) / 2f, getHeight() / 2f + totalHeight / 2f - invalidBedDescriptionLayout.getHeight() + padding / 2f);
                invalidBedDescriptionLayout.draw(canvas);
                canvas.restore();

                invalidate();
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        drawOverlay(canvas, false);
    }

    public Bitmap snapshotBitmap() {
        int w = getWidth(), h = getHeight();
        int[] bitmapBuffer = new int[w * h];
        int[] bitmapSource = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            throw new RuntimeException(e);
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private void calcStartFocus(MotionEvent e) {
        lastX = (e.getX(0) + e.getX(1)) / 2f;
        lastY = (e.getY(0) + e.getY(1)) / 2f;

        float x = e.getX(0) - e.getX(1), y = e.getY(0) - e.getY(1);
        lastLength = (float) Math.sqrt(x * x + y * y);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? renderer.stopHover() : renderer.hover(event.getX() * Prefs.getRenderScale(), event.getY() * Prefs.getRenderScale())) {
            queueEvent(this::requestRender);
        }
        return super.onHoverEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        long deltaMs = System.currentTimeMillis() - lastActionTime;
        lastActionTime = System.currentTimeMillis();
        int action = e.getActionMasked();

        if (e.getPointerCount() > 2) {
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (e.getPointerCount() == 2) {
                calcStartFocus(e);
                fromTwoPointers = true;
            } else {
                lastX = e.getX();
                lastY = e.getY();
            }

            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (fromTwoPointers) {
                if (e.getPointerCount() == 1) {
                    fromTwoPointers = false;
                    isScaling = false;
                    isMoving = false;
                    lastActionTime = 0;
                }
                return true;
            }

            if (e.getPointerCount() == 1) {
                if (!isRotating && action != MotionEvent.ACTION_CANCEL) {
                    if (renderer.onClick(e.getX() * Prefs.getRenderScale(), e.getY() * Prefs.getRenderScale())) {
                        requestRender();
                    }
                }

                lastX = e.getX(0);
                lastY = e.getY(0);
                isRotating = false;
            }

            // TODO: Rotate with inertia
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() == 2) {
                float x = (e.getX(0) + e.getX(1)) / 2f;
                float y = (e.getY(0) + e.getY(1)) / 2f;

                float lenX = e.getX(0) - e.getX(1), lenY = e.getY(0) - e.getY(1);
                float len = (float) Math.sqrt(lenX * lenX + lenY * lenY);
                float distanceX = lastX - x, distanceY = lastY - y;

                if (deltaMs > 128) {
                    isScaling = false;
                    isMoving = false;
                }

                boolean startingGesture = false;
                if (!isScaling && !isMoving) {
                    if (Math.abs(distanceX) < touchSlop && Math.abs(distanceY) < touchSlop && Math.abs(len - lastLength) > touchSlop * 1.5f) {
                        isScaling = true;
                        startingGesture = true;
                    } else if (Math.sqrt(distanceX * distanceX + distanceY * distanceY) >= touchSlop) {
                        isMoving = true;
                        startingGesture = true;
                    }
                }
                if (isScaling) {
                    float delta = len - lastLength;
                    lastLength = len;

                    if (!startingGesture) {
                        renderer.getCamera().zoom(delta / touchSlop * Prefs.getCameraSensitivity());
                        renderer.updateProjection();
                        requestRender();
                    }

                    lastX = x;
                    lastY = y;
                } else if (isMoving) {
                    if (!startingGesture) {
                        renderer.getCamera().move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity());
                        requestRender();
                    }

                    lastX = x;
                    lastY = y;
                }
            } else if (!fromTwoPointers) {
                float distanceX = lastX - e.getX(), distanceY = lastY - e.getY();
                boolean startingGesture = false;

                if (!isRotating) {
                    if (Math.sqrt(distanceX * distanceX + distanceY * distanceY) >= touchSlop) {
                        isRotating = true;
                        startingGesture = true;
                    }
                }

                if (isRotating) {
                    if (!startingGesture) {
                        if (Prefs.isRotationEnabled()) {
                            renderer.getCamera().rotateAround(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity());
                        } else {
                            renderer.getCamera().move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity());
                        }
                        requestRender();
                    }

                    lastX = e.getX();
                    lastY = e.getY();
                }
            }
        }

        return true;
    }

    private void applyScale() {
        int w = getWidth(), h = getHeight();
        float realScale = Math.round(w * (lastScale = Prefs.getRenderScale())) / (float) w;
        getHolder().setFixedSize((int) (w * realScale), (int) (h * realScale));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != 0 && h != 0) {
            applyScale();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWidth() > 0 && getHeight() > 0 && lastScale != Prefs.getRenderScale()) {
            applyScale();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
        requestRender();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        requestRender();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        renderer.onDestroy();
    }

    @Override
    public void onApplyTheme() {
        requestRender();
    }
}
