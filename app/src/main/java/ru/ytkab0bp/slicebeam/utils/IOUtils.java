package ru.ytkab0bp.slicebeam.utils;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ru.ytkab0bp.slicebeam.config.ConfigObject;

public class IOUtils {
    public static String readString(InputStream in) throws IOException {
        return readString(in, false);
    }

    public static String readString(InputStream in, boolean close) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[10240]; int c;
        while ((c = in.read(buffer)) != -1) {
            bos.write(buffer, 0, c);
        }
        if (close) {
            in.close();
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String configJsonToString(Object obj) throws JSONException {
        if (obj instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                if (sb.length() != 0) sb.append(",");
                sb.append(arr.getString(i));
            }
            return sb.toString();
        } else {
            return obj.toString();
        }
    }

    private static ConfigObject downloadProfilesRecursively(String vendor, String type, String profile, List<String> supportedKeys) throws IOException, JSONException {
        ConfigObject cfg = new ConfigObject();

        URLConnection con = new URL(String.format("https://raw.githubusercontent.com/SoftFever/OrcaSlicer/main/resources/profiles/%s/%s/%s.json", vendor, type, profile)).openConnection();
        JSONObject obj = new JSONObject(readString(con.getInputStream()));
        if (!TextUtils.isEmpty(obj.optString("inherits", null))) {
            ConfigObject o = downloadProfilesRecursively(vendor, type, obj.getString("inherits"), supportedKeys);

            for (Map.Entry<String, String> en : o.values.entrySet()) {
                if (supportedKeys.contains(en.getKey())) {
                    if (en.getKey().equals("ironing_type") && en.getValue().equals("no ironing")) {
                        cfg.values.put("ironing", "0");
                        cfg.values.put("ironing_type", "top");
                    } else if (!en.getKey().equals("thumbnails")) {
                        cfg.values.put(en.getKey(), en.getValue());
                    }
                }
            }
        }

        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();

            if (key.equals("print_settings_id") || key.equals("filament_settings_id") || key.equals("printer_settings_id")) {
                cfg.setTitle(obj.getString(key));
            } else if (!key.equals("inherits")) {
                cfg.put(key, configJsonToString(obj.get(key)));
            }
        }
        return cfg;
    }

    public static ConfigObject configJsonToIni(JSONObject obj, String type, List<String> supportedKeys, List<String> inBundle) throws JSONException, IOException, MissingProfileException {
        ConfigObject cfg = new ConfigObject();
        if (!TextUtils.isEmpty(obj.optString("inherits", null))) {
            String inherit = obj.getString("inherits");

            if (inBundle.contains(inherit)) {
                // Will do it later then
                cfg.put("inherits", inherit);
            } else if (inherit.indexOf(' ') == -1) {
                throw new MissingProfileException(inherit);
            } else {
                String vendor;
                if (inherit.indexOf(' ') == -1) {
                    throw new MissingProfileException(inherit);
                }
                if (inherit.contains("@BBL")) {
                    vendor = "BBL";
                } else if (type.equals("process")) {
                    int i = inherit.indexOf('@') + 1;
                    int j = inherit.indexOf(' ', i);
                    if (j == -1) j = inherit.length();
                    vendor = inherit.substring(i, j);
                } else {
                    vendor = inherit.substring(0, inherit.indexOf(' '));
                }

                if (vendor.equals("Generic") || inherit.startsWith("Bambu Lab")) vendor = "BBL";

                ConfigObject _obj = downloadProfilesRecursively(vendor, type, inherit, supportedKeys);
                for (Map.Entry<String, String> en : _obj.values.entrySet()) {
                    if (supportedKeys.contains(en.getKey())) {
                        if (en.getKey().equals("ironing_type") && en.getValue().equals("no ironing")) {
                            cfg.values.put("ironing", "0");
                            cfg.values.put("ironing_type", "top");
                        } if (en.getKey().equals("start_filament_gcode") || en.getKey().equals("end_filament_gcode") ||
                            en.getKey().equals("start_gcode") || en.getKey().equals("end_gcode")) {

                            cfg.values.put(en.getKey(), en.getValue().replaceAll("(\\{|\\[)nozzle_temperature_initial_layer(\\[\\d+]|)(}|])", "$1first_layer_temperature$2$3")
                                    .replaceAll("(\\{|\\[)bed_temperature_initial_layer_single(\\[\\d+]|)(}|])", "$1first_layer_bed_temperature$2$3"));
                        } else if (!en.getKey().equals("thumbnails")) {
                            cfg.values.put(en.getKey(), en.getValue());
                        }
                    }
                }
            }
        }

        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();

            if (key.equals("print_settings_id") || key.equals("filament_settings_id") || key.equals("printer_settings_id")) {
                String v = obj.getString(key);
                if (v.startsWith("[\"") && v.endsWith("\"]")) v = v.substring(2, v.length() - 2);
                cfg.setTitle(v);
            } else if (!key.equals("inherits") && supportedKeys.contains(key)) {
                cfg.put(key, configJsonToString(obj.get(key)));
            }
        }
        return cfg;
    }

    public static class MissingProfileException extends Exception {
        public final String profile;

        public MissingProfileException(String profile) {
            this.profile = profile;
        }

        @Override
        public String toString() {
            return "MissingProfileException{" +
                    "profile='" + profile + '\'' +
                    '}';
        }
    }
}
