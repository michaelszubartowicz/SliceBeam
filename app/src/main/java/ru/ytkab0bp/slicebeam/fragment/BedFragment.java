package ru.ytkab0bp.slicebeam.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Process;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.navigation.NavigationBarView;

import java.io.File;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.SliceProgressBottomSheet;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.BedMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.OrientationMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.TransformMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent;
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.SlicingProgressEvent;
import ru.ytkab0bp.slicebeam.navigation.Fragment;
import ru.ytkab0bp.slicebeam.slic3r.Bed3D;
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.BedSwipeDownLayout;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.GLView;
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout;
import ru.ytkab0bp.slicebeam.view.ThemeBottomNavigationView;
import ru.ytkab0bp.slicebeam.view.ThemeRailNavigationView;

public class BedFragment extends Fragment {
    private final static boolean DEBUG_VIEWER = false;
    private final static int MENU_SIZE_DP = 80;

    private FrameLayout overlayLayout;
    private SnackbarsLayout snackbarsLayout;
    private GLView glView;
    private NavigationBarView navigationView;

    private boolean isAnimatingMenu;
    private boolean isChangingByCode;
    private int currentMenuSlot;
    private FrameLayout menuView;
    private SparseArray<BedMenu> menuMap = new SparseArray<BedMenu>() {
        @Override
        public BedMenu get(int key) {
            BedMenu menu = super.get(key);
            if (menu == null) {
                switch (MenuCategory.values()[key]) {
                    default:
                    case FILE:
                        menu = new FileMenu();
                        break;
                    case CAMERA:
                        menu = new CameraMenu();
                        break;
                    case ORIENTATION:
                        menu = new OrientationMenu();
                        break;
                    case TRANSFORM:
                        menu = new TransformMenu();
                        break;
                    case SLICE_AND_EXPORT:
                        menu = new SliceMenu();
                        break;
                }

                put(key, menu);
            }
            return menu;
        }
    };

    private View contentView;

    private Model model;
    private GCodeProcessorResult gCodeResult;
    private UnfoldMenu currentUnfoldMenu;

    private BedSwipeDownLayout swipeDownLayout;
    private WebView panelWebView;
    private LinearLayout panelWebViewError;
    private ImageView webViewErrIcon;
    private TextView webViewErrDescription;
    private ProgressBar webViewProgressBar;

    private static String tempFileName;
    private static File tempExportingFile;

    public static String getTempFileName() {
        return tempFileName;
    }

    public static File getTempGCodePath() {
        return tempExportingFile != null ? tempExportingFile : new File(SliceBeam.INSTANCE.getCacheDir(), "temp.gcode");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SliceBeam.EVENT_BUS.registerListener(this);
    }

    @EventHandler(runOnMainThread = true)
    public void onNeedSnackbar(NeedSnackbarEvent e) {
        SnackbarsLayout.Snackbar s = new SnackbarsLayout.Snackbar(e.type, e.title);
        if (e.tag != null) {
            s.tag(e.tag);
        }
        snackbarsLayout.show(s);
    }

    @EventHandler(runOnMainThread = true)
    public void onDismissSnackbar(NeedDismissSnackbarEvent e) {
        snackbarsLayout.dismiss(e.tag);
    }

    public void showUnfoldMenu(UnfoldMenu menu, View from) {
        if (currentUnfoldMenu != null) return;

        menu.setOnDismiss(()-> {
            if (menu.isAttached()) return;
            currentUnfoldMenu = null;
        });
        currentUnfoldMenu = menu;
        menu.show(from, this);
    }

    public void loadGCode(File f) {
        gCodeResult = new GCodeProcessorResult(f);
        ViewUtils.postOnMainThread(()-> {
            glView.queueEvent(()->{
                glView.getRenderer().setGCodeViewer(gCodeResult);
                glView.requestRender();
            });

            tempFileName = gCodeResult.getRecommendedName();
            tempExportingFile = f;

            isChangingByCode = true;
            navigationView.setSelectedItemId(MenuCategory.SLICE_AND_EXPORT.ordinal());
            isChangingByCode = false;

            DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
            boolean portrait = dm.widthPixels < dm.heightPixels;
            selectMenu(getContext(), portrait, MenuCategory.SLICE_AND_EXPORT.ordinal());
        });
    }

    @Override
    public boolean onBackPressed() {
        if (currentUnfoldMenu != null) {
            currentUnfoldMenu.dismiss();
            return true;
        }
        if (swipeDownLayout.onBackPressed()) {
            return true;
        }
        if (currentMenuSlot != 0) {
            navigationView.setSelectedItemId(0);
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SliceBeam.EVENT_BUS.unregisterListener(this);

        for (int i = 0; i < menuMap.size(); i++) {
            menuMap.valueAt(i).onViewDestroyed();
        }

        if (!(getContext() instanceof Activity && ((Activity) getContext()).isChangingConfigurations())) {
            if (model != null) {
                model.release();
                model = null;
            }
            if (gCodeResult != null) {
                gCodeResult.release();
                gCodeResult = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        glView.onResume();
        ConfigObject cfg = SliceBeam.CONFIG.findPrinter(SliceBeam.CONFIG.presets.get("printer"));
        boolean enable = cfg != null && cfg.get("host_type") != null && !TextUtils.isEmpty(cfg.get("print_host"));
        swipeDownLayout.setEnableTop(enable);
        if (enable) {
            String host = cfg.get("print_host");
            if (host.contains(":")) {
                try {
                    int port = Integer.parseInt(host.split(":")[1]);
                    if (port >= 7125 && port <= 7200) {
                        host = host.split(":")[0];
                    }
                } catch (Exception ignored) {}
            }
            if (!host.startsWith("http://")) {
                host = "http://" + host;
            }
            webViewProgressBar.animate().alpha(1).setDuration(150).start();
            panelWebView.setAlpha(0f);
            panelWebView.loadUrl(host);
            panelWebViewError.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    panelWebViewError.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        glView.onPause();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(Context ctx) {
        glView = new GLView(ctx);
        glView.getRenderer().setModel(model);
        glView.getRenderer().setGCodeViewer(gCodeResult);
        overlayLayout = new FrameLayout(ctx) {
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                if (currentUnfoldMenu != null) {
                    currentUnfoldMenu.relayout();
                }
            }
        };

        LinearLayout ll = new LinearLayout(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean portrait = dm.widthPixels < dm.heightPixels;

        ll.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigationView = null;
        constructMenuView(ctx, portrait);

        if (!portrait) {
            ll.addView(navigationView = new ThemeRailNavigationView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewUtils.dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
            ll.addView(menuView, new LinearLayout.LayoutParams(ViewUtils.dp(MENU_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        }

        swipeDownLayout = new BedSwipeDownLayout(ctx);
        panelWebView = new WebView(ctx);
        panelWebView.getSettings().setJavaScriptEnabled(true);
        panelWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                webViewErrDescription.setText(description);
                panelWebViewError.setVisibility(View.VISIBLE);
                panelWebViewError.setAlpha(0f);
                panelWebViewError.animate().alpha(1).setDuration(150).setListener(null).start();
                webViewProgressBar.animate().alpha(0).setDuration(150).start();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                panelWebView.animate().alpha(0).setDuration(150).start();
                webViewProgressBar.animate().alpha(0).setDuration(150).start();
            }
        });

        FrameLayout wfl = new FrameLayout(ctx);
        wfl.addView(panelWebView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panelWebViewError = new LinearLayout(ctx);
        panelWebViewError.setVisibility(View.GONE);
        panelWebViewError.setOrientation(LinearLayout.VERTICAL);
        panelWebViewError.setGravity(Gravity.CENTER);
        panelWebViewError.setPadding(ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12));
        webViewErrIcon = new ImageView(ctx);
        webViewErrIcon.setImageResource(R.drawable.globe_cross_outline_28);
        panelWebViewError.addView(webViewErrIcon, new LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)) {{
            bottomMargin = ViewUtils.dp(8);
        }});
        webViewErrDescription = new TextView(ctx);
        webViewErrDescription.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        webViewErrDescription.setGravity(Gravity.CENTER);
        panelWebViewError.addView(webViewErrDescription);
        wfl.addView(panelWebViewError, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webViewProgressBar = new ProgressBar(ctx);
        webViewProgressBar.setAlpha(0f);
        wfl.addView(webViewProgressBar, new FrameLayout.LayoutParams(ViewUtils.dp(36), ViewUtils.dp(36), Gravity.CENTER));

        if (portrait) {
            LinearLayout inner = new LinearLayout(ctx);
            inner.setOrientation(LinearLayout.VERTICAL);
            ll = inner;

            inner.addView(glView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            swipeDownLayout.addView(inner);
            swipeDownLayout.addView(wfl);
        } else {
            swipeDownLayout.addView(glView);
            swipeDownLayout.addView(wfl);
            ll.addView(swipeDownLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        if (portrait) {
            ll.addView(menuView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(MENU_SIZE_DP)));
            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1)));
            ll.addView(navigationView = new ThemeBottomNavigationView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        navigationView.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        for (MenuCategory cat : MenuCategory.values()) {
            navigationView.getMenu().add(0, cat.ordinal(), 0, cat.titleRes).setIcon(cat.iconRes);
        }
        navigationView.setSelectedItemId(currentMenuSlot);
        navigationView.setOnItemSelectedListener(item -> {
            if (currentMenuSlot == item.getItemId() || isChangingByCode) return true;
            if (isAnimatingMenu) return false;
            if (item.getItemId() == MenuCategory.SLICE_AND_EXPORT.ordinal()) {
                if (glView.getRenderer().getModel() == null && !DEBUG_VIEWER) {
                    new BeamAlertDialogBuilder(ctx)
                            .setTitle(R.string.SliceFailed)
                            .setMessage(R.string.SliceFailedNoModels)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    tempExportingFile = null;
                    File cfg = SliceBeam.getCurrentConfigFile();
                    File gcode = getTempGCodePath();

                    if (!DEBUG_VIEWER) {
                        new SliceProgressBottomSheet(ctx).show();
                    }
                    new Thread(()->{
                        try {
                            Process.setThreadPriority(-20);

                            try {
                                SliceBeam.genCurrentConfig();
                            } catch (Exception e) {
                                Log.e("BedFragment", "Failed to write config", e);

                                ViewUtils.postOnMainThread(()->{
                                    SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                                    new BeamAlertDialogBuilder(ctx)
                                            .setTitle(R.string.SliceFailed)
                                            .setMessage(e.getMessage())
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                });
                            }

                            if (!DEBUG_VIEWER) {
                                gCodeResult = glView.getRenderer().getModel().slice(cfg.getAbsolutePath(), gcode.getAbsolutePath(), (progress, text) -> SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(progress, text)));
                                SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                            } else {
                                gCodeResult = new GCodeProcessorResult(gcode);
                            }
                            ViewUtils.postOnMainThread(()-> {
                                glView.queueEvent(()->{
                                    glView.getRenderer().setGCodeViewer(gCodeResult);
                                    glView.requestRender();
                                });

                                tempFileName = gCodeResult.getRecommendedName();
                                tempExportingFile = null;

                                isChangingByCode = true;
                                navigationView.setSelectedItemId(item.getItemId());
                                isChangingByCode = false;
                                selectMenu(ctx, portrait, item.getItemId());
                            });
                        } catch (Exception e) {
                            Log.e("BedFragment", "Slice failed", e);
                            ViewUtils.postOnMainThread(()->{
                                SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                                new BeamAlertDialogBuilder(ctx)
                                        .setTitle(R.string.SliceFailed)
                                        .setMessage(e.getMessage())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            });
                        }
                    }).start();
                }
                return false;
            } else {
                glView.queueEvent(()->{
                    if (gCodeResult != null) {
                        gCodeResult.release();
                        gCodeResult = null;
                    }

                    glView.getRenderer().setGCodeViewer(null);
                    glView.requestRender();
                });
            }

            selectMenu(ctx, portrait, item.getItemId());
            return true;
        });

        if (portrait) {
            overlayLayout.addView(contentView = swipeDownLayout);
        } else {
            overlayLayout.addView(contentView = ll);
        }
        overlayLayout.addView(snackbarsLayout = new SnackbarsLayout(ctx), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) {{
            if (portrait) {
                bottomMargin = ViewUtils.dp(80 * 2);
            } else {
                leftMargin = ViewUtils.dp(80 * 2);
            }
        }});
        return overlayLayout;
    }

    public SnackbarsLayout getSnackbarsLayout() {
        return snackbarsLayout;
    }

    public FrameLayout getOverlayLayout() {
        return overlayLayout;
    }

    private void selectMenu(Context ctx, boolean portrait, int slot) {
        if (glView.getRenderer().resetFlattenMode()) {
            glView.requestRender();
            SliceBeam.EVENT_BUS.fireEvent(new FlattenModeResetEvent());
        }
        isAnimatingMenu = true;

        BedMenu prevMenu = menuMap.get(currentMenuSlot);
        boolean forward = slot > currentMenuSlot;
        currentMenuSlot = slot;

        BedMenu currentMenu = menuMap.get(currentMenuSlot);
        if (currentMenu.getView() == null) {
            currentMenu.onSetBed(this);
            currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait));
        }
        View v = currentMenu.getView();
        if (v.getParent() != null) {
            menuView.removeView(v);
        }
        menuView.addView(v, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Runnable next = ()->{
            if (portrait) {
                v.setTranslationX(v.getWidth() * (forward ? 1 : -1));
            } else {
                v.setTranslationY(v.getHeight() * (forward ? 1 : -1));
            }
            v.setAlpha(0f);

            View prevView = prevMenu.getView();

            new SpringAnimation(new FloatValueHolder(0))
                    .setMinimumVisibleChange(1 / 256f)
                    .setSpring(new SpringForce(1f)
                            .setStiffness(1000f)
                            .setDampingRatio(1f))
                    .addUpdateListener((animation, value, velocity) -> {
                        prevView.setAlpha(1f - value);
                        v.setAlpha(value);

                        if (portrait) {
                            prevView.setTranslationX(-v.getWidth() * value * 0.5f * (forward ? 1 : -1));
                            v.setTranslationX(v.getWidth() * (1f - value) * 0.5f * (forward ? 1 : -1));
                        } else {
                            prevView.setTranslationY(-prevView.getHeight() * value * 0.5f * (forward ? 1 : -1));
                            v.setTranslationY(v.getHeight() * (1f - value) * 0.5f * (forward ? 1 : -1));
                        }
                    })
                    .addEndListener((animation, canceled, value, velocity) -> {
                        menuView.removeView(prevMenu.getView());
                        isAnimatingMenu = false;
                    })
                    .start();
        };

        if (!v.isLaidOut()) {
            v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    next.run();
                }
            });
        } else {
            next.run();
        }
    }

    public GLView getGlView() {
        return glView;
    }

    public void loadModel(File f) throws Slic3rRuntimeError {
        Model m = new Model(f);
        if (model != null) {
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    Bed3D bed = glView.getRenderer().getBed();
                    if (bed == null) {
                        ViewUtils.postOnMainThread(()-> glView.queueEvent(this));
                        return;
                    }
                    Vec3d center = bed.getVolumeMin().center(bed.getVolumeMax());
                    Vec3d objMin = new Vec3d(), objMax = new Vec3d();
                    Vec3d objTranslate = new Vec3d();
                    for (int i = 0; i < m.getObjectsCount(); i++) {
                        m.getTranslation(i, objTranslate);
                        m.getBoundingBoxExact(i, objMin, objMax);

                        m.translate(i, -objTranslate.x + center.x, -objTranslate.y + center.y, -objTranslate.z + (objMax.z - objMin.z) / 2);
                    }

                    for (int i = 0; i < m.getObjectsCount(); i++) {
                        model.addObject(m, i);
                    }
                    m.release();
                }
            });
        } else {
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    Bed3D bed = glView.getRenderer().getBed();
                    if (bed == null) {
                        ViewUtils.postOnMainThread(()-> glView.queueEvent(this));
                        return;
                    }
                    glView.getRenderer().setModel(model = m);

                    Vec3d center = bed.getVolumeMin().center(bed.getVolumeMax());
                    Vec3d objMin = new Vec3d(), objMax = new Vec3d();
                    Vec3d objTranslate = new Vec3d();
                    for (int i = 0; i < m.getObjectsCount(); i++) {
                        m.getTranslation(i, objTranslate);
                        m.getBoundingBoxExact(i, objMin, objMax);

                        m.translate(i, -objTranslate.x + center.x, -objTranslate.y + center.y, -objTranslate.z + (objMax.z - objMin.z) / 2);
                    }
                }
            });
        }
        glView.requestRender();
    }

    @Override
    public void onApplyTheme() {
        super.onApplyTheme();

        webViewErrIcon.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
        webViewErrDescription.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        webViewProgressBar.setIndeterminateTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
        menuView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
        for (int i = 0; i < MenuCategory.values().length; i++) {
            if (i != currentMenuSlot) {
                ThemesRepo.invalidateView(menuMap.get(i).getView());
            }
        }
    }

    private void constructMenuView(Context ctx, boolean portrait) {
        menuView = new FrameLayout(ctx);
        BedMenu currentMenu = menuMap.get(currentMenuSlot);
        if (currentMenu.getView() == null) {
            currentMenu.onSetBed(this);
            currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait));
        }
        menuView.addView(currentMenu.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void updateModel() {
        model = glView.getRenderer().getModel();
    }

    public enum MenuCategory {
        FILE(R.string.MenuFile, R.drawable.folder_simple_outline_28),
        CAMERA(R.string.MenuCamera, R.drawable.camera_outline_28),
        ORIENTATION(R.string.MenuOrientation, R.drawable.menu_orientation_28),
        TRANSFORM(R.string.MenuTransform, R.drawable.menu_scale_28),
//        MODIFIERS(R.string.MenuModifiers, R.drawable.sliders_outline_28),
        SLICE_AND_EXPORT(R.string.MenuSlice, R.drawable.magic_wand_outline_28);

        final int titleRes;
        final int iconRes;

        MenuCategory(int titleRes, int iconRes) {
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }
    }
}
