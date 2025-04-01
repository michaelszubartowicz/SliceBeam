package ru.ytkab0bp.slicebeam;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipFile;

import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.ChangeLogBottomSheet;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent;
import ru.ytkab0bp.slicebeam.fragment.BedFragment;
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate;
import ru.ytkab0bp.slicebeam.navigation.NavigationDelegate;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.IOUtils;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout;

public class MainActivity extends AppCompatActivity {
    public final static int REQUEST_CODE_OPEN_FILE = 1, REQUEST_CODE_EXPORT_GCODE = 2,
                            REQUEST_CODE_IMPORT_PROFILES = 3, REQUEST_CODE_EXPORT_PROFILES = 4;

    private static MainActivity activeInstance;

    public static List<ConfigObject> EXPORTING_PRINTS;
    public static List<ConfigObject> EXPORTING_FILAMENTS;
    public static List<ConfigObject> EXPORTING_PRINTERS;

    private static SparseArray<NavigationDelegate> liveDelegate = new SparseArray<>();
    private static int lastId;

    private int id;
    private NavigationDelegate delegate;
    private boolean landscape;
    private UnfoldMenu unfoldMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.getPrefs().contains("crash")) {
            startActivity(new Intent(this, SafeStartActivity.class));
            finish();
            return;
        }
        if (SliceBeam.CONFIG == null) {
            Prefs.setLastCommit();
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        if (activeInstance == null) {
            activeInstance = this;
        } else {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (getIntent() != null) {
                i.setAction(getIntent().getAction());
                i.putExtras(getIntent());
                i.setDataAndType(getIntent().getData(), getIntent().getType());
            }
            startActivity(i);

            finish();
            return;
        }

        id = savedInstanceState == null ? lastId++ : savedInstanceState.getInt("id");

        if (delegate == null) {
            NavigationDelegate saved = liveDelegate.get(id);
            liveDelegate.remove(id);
            if (saved != null && isCompatible(saved)) {
                delegate = saved;
            } else {
                delegate = onCreateDelegate();
            }
        }
        delegate.setContext(this);

        delegate.onCreate();
        View v = delegate.onCreateView(this);
        if (delegate.getContainerView() == null || delegate.getContainerView().getParent() == null) {
            throw new IllegalArgumentException("Delegate hasn't created container view!");
        }
        ViewCompat.setOnApplyWindowInsetsListener(v, (v2, insets) -> {
            Insets systemBars = insets.getSystemWindowInsets();
            v2.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets.consumeSystemWindowInsets();
        });
        setContentView(v);

        if (getIntent() != null && getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            loadFile(getIntent().getData());
            setIntent(null);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        landscape = dm.widthPixels > dm.heightPixels;
        View decorView = getWindow().getDecorView();
        decorView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (landscape) {
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);

            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    int finalVisibility = visibility;
                    ViewUtils.postOnMainThread(() -> decorView.setSystemUiVisibility(finalVisibility), 500);
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setStatusBarContrastEnforced(false);
                getWindow().setNavigationBarContrastEnforced(false);
            }
            if (ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f) {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        if (!Objects.equals(Prefs.getLastCommit(), BuildConfig.COMMIT) && SliceBeam.hasUpdateInfo) {
            Prefs.setLastCommit();
            BeamServerData.load();
            new ChangeLogBottomSheet(this).show();
        }
    }

    @NonNull
    public NavigationDelegate getNavigationDelegate() {
        return delegate;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        loadFile(intent.getData());
        setIntent(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == MainActivity.REQUEST_CODE_EXPORT_GCODE) {
                try {
                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    InputStream in = new FileInputStream(BedFragment.getTempGCodePath());
                    byte[] buffer = new byte[10240];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    in.close();
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (requestCode == MainActivity.REQUEST_CODE_OPEN_FILE) {
                loadFile(data.getData());
            } else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_PROFILES) {
                try {
                    Slic3rConfigWrapper w = new Slic3rConfigWrapper();
                    w.printConfigs.addAll(EXPORTING_PRINTS);
                    w.filamentConfigs.addAll(EXPORTING_FILAMENTS);
                    w.printerConfigs.addAll(EXPORTING_PRINTERS);

                    EXPORTING_PRINTS = null;
                    EXPORTING_FILAMENTS = null;
                    EXPORTING_PRINTERS = null;

                    w.presets = new ConfigObject();
                    if (w.findPrint(SliceBeam.CONFIG.presets.get("print")) != null) {
                        w.presets.put("print", SliceBeam.CONFIG.presets.get("print"));
                    }
                    if (w.findFilament(SliceBeam.CONFIG.presets.get("filament")) != null) {
                        w.presets.put("filament", SliceBeam.CONFIG.presets.get("filament"));
                    }
                    if (w.findPrinter(SliceBeam.CONFIG.presets.get("printer")) != null) {
                        w.presets.put("printer", SliceBeam.CONFIG.presets.get("printer"));
                    }

                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    out.write(w.serialize().getBytes(StandardCharsets.UTF_8));
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (requestCode == MainActivity.REQUEST_CODE_IMPORT_PROFILES) {
                Uri uri = data.getData();
                ContentResolver resolver = getContentResolver();

                String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
                Cursor metaCursor = resolver.query(uri, projection, null, null, null);
                String fileName = null;
                if (metaCursor != null) {
                    try {
                        if (metaCursor.moveToFirst()) {
                            fileName = metaCursor.getString(0);
                        }
                    } finally {
                        metaCursor.close();
                    }
                }

                if (fileName == null) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(R.string.MenuFileOpenFileFailedNullName)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                if (fileName.endsWith(".orca_printer")) {
                    Toast.makeText(MainActivity.this, R.string.OrcaConversionPleaseWait, Toast.LENGTH_SHORT).show();

                    File f = new File(SliceBeam.getModelCacheDir(), "orca_conv.zip");
                    new Thread(()->{
                        try {
                            InputStream in = resolver.openInputStream(uri);
                            FileOutputStream fos = new FileOutputStream(f);
                            byte[] buffer = new byte[10240]; int c;
                            while ((c = in.read(buffer)) != -1) {
                                fos.write(buffer, 0, c);
                            }
                            fos.close();
                            in.close();

                            ZipFile zf = new ZipFile(f);
                            JSONObject bundle = new JSONObject(IOUtils.readString(zf.getInputStream(zf.getEntry("bundle_structure.json"))));
                            if (!bundle.get("bundle_type").equals("printer config bundle")) {
                                zf.close();

                                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                                        .setTitle(R.string.MenuFileImportProfilesFailed)
                                        .setMessage(R.string.OrcaConversionNotAConfigBundle)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show());

                                return;
                            }

                            Slic3rConfigWrapper w = new Slic3rConfigWrapper();
                            if (bundle.has("process_config")) {
                                JSONArray arr = bundle.getJSONArray("process_config");
                                List<String> names = new ArrayList<>();
                                List<String> stripped = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    String v = arr.getString(i);
                                    names.add(v);
                                    stripped.add(v.substring(v.indexOf('/') + 1, v.length() - 5));
                                }

                                for (String name : names) {
                                    w.printConfigs.add(IOUtils.configJsonToIni(new JSONObject(IOUtils.readString(zf.getInputStream(zf.getEntry(name)))), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, stripped));
                                }
                                for (ConfigObject obj : w.printConfigs) {
                                    String inherit = obj.get("inherits");
                                    while (inherit != null) {
                                        ConfigObject _obj = w.findPrint(inherit);
                                        if (_obj == null) throw new IOUtils.MissingProfileException(inherit);

                                        obj.values.remove("inherits");
                                        HashMap<String, String> newMap = new HashMap<>();
                                        newMap.putAll(_obj.values);
                                        newMap.putAll(obj.values);
                                        obj.values = newMap;

                                        inherit = obj.values.get("inherits");
                                    }
                                }
                            }
                            if (bundle.has("filament_config")) {
                                JSONArray arr = bundle.getJSONArray("filament_config");
                                List<String> names = new ArrayList<>();
                                List<String> stripped = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    String v = arr.getString(i);
                                    names.add(v);
                                    stripped.add(v.substring(v.indexOf('/') + 1, v.length() - 5));
                                }

                                for (String name : names) {
                                    w.filamentConfigs.add(IOUtils.configJsonToIni(new JSONObject(IOUtils.readString(zf.getInputStream(zf.getEntry(name)))), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, stripped));
                                }
                                for (ConfigObject obj : w.filamentConfigs) {
                                    String inherit = obj.get("inherits");
                                    while (inherit != null) {
                                        ConfigObject _obj = w.findFilament(inherit);
                                        if (_obj == null) throw new IOUtils.MissingProfileException(inherit);

                                        obj.values.remove("inherits");
                                        HashMap<String, String> newMap = new HashMap<>();
                                        newMap.putAll(_obj.values);
                                        newMap.putAll(obj.values);
                                        obj.values = newMap;

                                        inherit = obj.values.get("inherits");
                                    }
                                }
                            }
                            if (bundle.has("printer_config")) {
                                JSONArray arr = bundle.getJSONArray("printer_config");
                                List<String> names = new ArrayList<>();
                                List<String> stripped = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    String v = arr.getString(i);
                                    names.add(v);
                                    stripped.add(v.substring(v.indexOf('/') + 1));
                                }

                                for (String name : names) {
                                    w.printerConfigs.add(IOUtils.configJsonToIni(new JSONObject(IOUtils.readString(zf.getInputStream(zf.getEntry(name)))), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, stripped));
                                }
                                for (ConfigObject obj : w.printerConfigs) {
                                    String inherit = obj.get("inherits");
                                    while (inherit != null) {
                                        ConfigObject _obj = w.findPrinter(inherit);
                                        if (_obj == null) throw new IOUtils.MissingProfileException(inherit);

                                        obj.values.remove("inherits");
                                        HashMap<String, String> newMap = new HashMap<>();
                                        newMap.putAll(_obj.values);
                                        newMap.putAll(obj.values);
                                        obj.values = newMap;

                                        inherit = obj.values.get("inherits");
                                    }
                                }
                            }

                            zf.close();

                            loadIniForImport(new ByteArrayInputStream(w.serialize().getBytes(StandardCharsets.UTF_8)));
                        } catch (Exception e) {
                            ViewUtils.postOnMainThread(() -> {
                                new BeamAlertDialogBuilder(this)
                                        .setTitle(R.string.MenuFileImportProfilesFailed)
                                        .setMessage(e.toString())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            });
                        }
                    }).start();
                    return;
                }

                if (!fileName.endsWith(".ini")) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(R.string.MenuFileImportProfilesFailedNotIni)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                try {
                    loadIniForImport(resolver.openInputStream(uri));
                } catch (FileNotFoundException e) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        }
    }

    private void loadIniForImport(InputStream in) {
        new Thread(()->{
            try {
                Slic3rConfigWrapper w = new Slic3rConfigWrapper(in);

                ViewUtils.postOnMainThread(() -> {
                    CharSequence[] prints = new CharSequence[w.printConfigs.size()];
                    boolean[] enabledPrints = new boolean[prints.length];
                    for (int i = 0; i < prints.length; i++) {
                        prints[i] = w.printConfigs.get(i).getTitle();
                        enabledPrints[i] = true;
                    }

                    CharSequence[] filaments = new CharSequence[w.filamentConfigs.size()];
                    boolean[] enabledFilaments = new boolean[filaments.length];
                    for (int i = 0; i < filaments.length; i++) {
                        filaments[i] = w.filamentConfigs.get(i).getTitle();
                        enabledFilaments[i] = true;
                    }

                    CharSequence[] printers = new CharSequence[w.printerConfigs.size()];
                    boolean[] enabledPrinters = new boolean[printers.length];
                    for (int i = 0; i < printers.length; i++) {
                        printers[i] = w.printerConfigs.get(i).getTitle();
                        enabledPrinters[i] = true;
                    }

                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileExportProfilesPrints)
                            .setMultiChoiceItems(prints, enabledPrints, (dialog, which, isChecked) -> enabledPrints[which] = isChecked)
                            .setPositiveButton(android.R.string.ok, (d1, w1) -> new BeamAlertDialogBuilder(this)
                                    .setTitle(R.string.MenuFileExportProfilesFilaments)
                                    .setMultiChoiceItems(filaments, enabledFilaments, (dialog, which, isChecked) -> enabledFilaments[which] = isChecked)
                                    .setPositiveButton(android.R.string.ok, (d2, w2) -> new BeamAlertDialogBuilder(this)
                                            .setTitle(R.string.MenuFileExportProfilesPrinters)
                                            .setMultiChoiceItems(printers, enabledPrinters, (dialog, which, isChecked) -> enabledPrinters[which] = isChecked)
                                            .setPositiveButton(android.R.string.ok, (d3, w3) -> {
                                                for (int i = 0; i < enabledPrints.length; i++) {
                                                    if (enabledPrints[i]) {
                                                        SliceBeam.CONFIG.importPrint(w.printConfigs.get(i));
                                                    }
                                                }
                                                for (int i = 0; i < enabledFilaments.length; i++) {
                                                    if (enabledFilaments[i]) {
                                                        SliceBeam.CONFIG.importFilament(w.filamentConfigs.get(i));
                                                    }
                                                }
                                                for (int i = 0; i < enabledPrinters.length; i++) {
                                                    if (enabledPrinters[i]) {
                                                        SliceBeam.CONFIG.importPrinter(w.printerConfigs.get(i));
                                                    }
                                                }
                                                SliceBeam.saveConfig();
                                            })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show())
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to read file", e);

                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start();
    }

    private void loadFile(File f, boolean autoorient) {
        String tag = UUID.randomUUID().toString();
        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag(tag));
        IOUtils.IO_POOL.submit(() -> {
            Process.setThreadPriority(-20);
            if (delegate.getCurrentFragment() instanceof BedFragment) {
                BedFragment fragment = (BedFragment) delegate.getCurrentFragment();
                try {
                    boolean gcode = f.getName().endsWith(".gcode");
                    if (gcode) {
                        fragment.loadGCode(f);
                    } else {
                        fragment.loadModel(f);
                    }
                    fragment.getGlView().queueEvent(() -> {
                        if (!gcode) {
                            SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
                        }
                        Model model = fragment.getGlView().getRenderer().getModel();
                        int i = model.getObjectsCount() - 1;
                        if (autoorient) {
                            model.autoOrient(i);
                            fragment.getGlView().getRenderer().invalidateGlModel(i);
                            fragment.getGlView().requestRender();
                        }
                        SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded));
                        if (model.isBigObject(i)) {
                            SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.WARNING, R.string.MenuFileOpenFileBigObject));
                        }
                    });
                } catch (Slic3rRuntimeError e) {
                    Log.e("MainActivity", "Failed to load model", e);
                    f.delete();

                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileOpenFileFailed)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show());
                }
            }
        });
    }

    private void loadFile(Uri uri) {
        if (uri == null) return;

        ContentResolver resolver = getContentResolver();

        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = resolver.query(uri, projection, null, null, null);
        String fileName = null;
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    fileName = metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }

        if (fileName == null) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileOpenFileFailed)
                    .setMessage(R.string.MenuFileOpenFileFailedNullName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        File f = new File(SliceBeam.getModelCacheDir(), fileName);
        // TODO: Check if file already exists
        new Thread(()->{
            try {
                InputStream in = resolver.openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buffer = new byte[10240]; int c;
                while ((c = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, c);
                }
                fos.close();
                in.close();
                loadFile(f, false);
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to write cache file", e);

                f.delete();
                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileOpenFileFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if ((newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            ThemesRepo.resetSystemResolvedTheme();
            ThemesRepo.invalidate(this);
        }
    }

    public void onApplyTheme() {
        delegate.onApplyTheme();

        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f) {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        decorView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isChangingConfigurations()) {
            outState.putInt("id", id);
            liveDelegate.put(id, delegate);
        }
    }

    private boolean isCompatible(NavigationDelegate delegate) {
        return true;
    }

    private NavigationDelegate onCreateDelegate() {
        return new MobileNavigationDelegate();
    }

    public void showUnfoldMenu(UnfoldMenu menu, View v) {
        if (unfoldMenu != null) return;
        menu.setOnDismiss(() -> unfoldMenu = null);
        menu.show(v, delegate.getOverlayView());
        unfoldMenu = menu;
    }

    @Override
    public void onBackPressed() {
        if (unfoldMenu != null) {
            unfoldMenu.dismiss();
            return;
        }
        if (delegate.onBackPressed()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        delegate.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        delegate.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (delegate != null) {
            delegate.onDestroy();
        }
    }
}