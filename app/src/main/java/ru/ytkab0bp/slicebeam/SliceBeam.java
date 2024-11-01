package ru.ytkab0bp.slicebeam;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import ru.ytkab0bp.eventbus.EventBus;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef;
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.VibrationUtils;

public class SliceBeam extends Application {
    public static SliceBeam INSTANCE;
    public static EventBus EVENT_BUS = EventBus.newBus("main");
    public static Slic3rConfigWrapper CONFIG;
    public static int CONFIG_UID = 0;
    public static BeamServerData SERVER_DATA;
    public static boolean hasUpdateInfo;

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        EventBus.registerImpl(this);
        Prefs.init(this);
        VibrationUtils.init(this);
        tryCheckInfo();
        PrintConfigDef.getInstance();
        try {
            getAssets().open("update.json").close();
            hasUpdateInfo = true;
        } catch (IOException e) {
            hasUpdateInfo = false;
        }

        File cache = SliceBeam.getModelCacheDir();
        if (cache.exists()) {
            for (File f : cache.listFiles()) {
                f.delete();
            }
        }

        File cfgFile = getConfigFile();
        getCurrentConfigFile().delete();
        if (cfgFile.exists()) {
            try {
                CONFIG = new Slic3rConfigWrapper(cfgFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        WebView.setWebContentsDebuggingEnabled(true);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            Prefs.getPrefs().edit().putString("crash", sw.toString()).commit();
            Intent intent = new Intent(this, SafeStartActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            Runtime.getRuntime().exit(0);
        });
    }

    private static void tryCheckInfo() {
        try {
            SERVER_DATA = new BeamServerData(new JSONObject(Prefs.getBeamServerData()));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (System.currentTimeMillis() - Prefs.getLastCheckedInfo() >= 86400000L) {
            BeamServerData.load();
        }
    }

    public static void saveConfig() {
        SliceBeam.CONFIG_UID++;
        File f = getConfigFile();
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(CONFIG.serialize().getBytes(StandardCharsets.UTF_8));
            fos.close();

            getCurrentConfigFile().delete();
        } catch (Exception e) {
            Log.e("Config", "Failed to save config", e);
        }
    }

    public static File getModelCacheDir() {
        File f = new File(INSTANCE.getCacheDir(), "model");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File getConfigFile() {
        return new File(INSTANCE.getFilesDir(), "slic3r.ini");
    }

    public static ConfigObject buildCurrentConfigObject() {
        ConfigObject singleObject = new ConfigObject();
        singleObject.values.putAll(SliceBeam.CONFIG.findPrinter(SliceBeam.CONFIG.presets.get("printer")).values);
        if (SliceBeam.CONFIG.findPrint(SliceBeam.CONFIG.presets.get("print")) != null) {
            singleObject.values.putAll(SliceBeam.CONFIG.findPrint(SliceBeam.CONFIG.presets.get("print")).values);
        }
        if (SliceBeam.CONFIG.findFilament(SliceBeam.CONFIG.presets.get("filament")) != null) {
            singleObject.values.putAll(SliceBeam.CONFIG.findFilament(SliceBeam.CONFIG.presets.get("filament")).values);
        }

        PrintConfigDef def = PrintConfigDef.getInstance();
        for (Map.Entry<String, ConfigOptionDef> en : def.options.entrySet()) {
            if (singleObject.get(en.getKey()) == null && !PrintConfigDef.SKIP_DEFAULT_OPTIONS.contains(en.getKey()) && en.getValue().defaultValue != null) {
                singleObject.put(en.getKey(), en.getValue().defaultValue);
            }
        }
        return singleObject;
    }

    public static void genCurrentConfig() throws IOException {
        File cfg = getCurrentConfigFile();
        if (!cfg.exists()) {
            FileOutputStream fos = new FileOutputStream(cfg);
            ConfigObject singleObject = buildCurrentConfigObject();
            fos.write(singleObject.serialize().getBytes(StandardCharsets.UTF_8));
            fos.close();
        }
    }

    public static File getCurrentConfigFile() {
        return new File(INSTANCE.getFilesDir(), "slic3r_current.ini");
    }
}
