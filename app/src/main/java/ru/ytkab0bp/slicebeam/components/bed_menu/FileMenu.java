package ru.ytkab0bp.slicebeam.components.bed_menu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.MainActivity;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.components.WebViewMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.NeedDismissCalibrationsMenu;
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent;
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent;
import ru.ytkab0bp.slicebeam.recycler.PreferenceItem;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerAdapter;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.recycler.SpaceItem;
import ru.ytkab0bp.slicebeam.slic3r.Bed3D;
import ru.ytkab0bp.slicebeam.theme.BeamTheme;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.FadeRecyclerView;

public class FileMenu extends ListBedMenu {
    private final static List<String> K3D_SUPPORTED_LANGUAGES = Arrays.asList("en", "ru");

    private String getK3DLanguage() {
        String lang = Locale.getDefault().getLanguage();
        return K3D_SUPPORTED_LANGUAGES.contains(lang) ? lang : "en";
    }

    static String escapeStringForJs(String s) {
        if (s == null) return s;
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\r", "\\r")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    private boolean hasSelection() {
        return fragment.getGlView().getRenderer().getModel() != null && fragment.getGlView().getRenderer().getSelectedObject() != -1;
    }

    @Override
    protected List<SimpleRecyclerItem> onCreateItems(boolean portrait) {
        return Arrays.asList(
                new BedMenuItem(R.string.MenuFileOpen, R.drawable.folder_simple_plus_outline_28).onClick(v -> {
                    if (!fragment.getGlView().getRenderer().getBed().isValid()) {
                        Toast.makeText(fragment.getContext(), R.string.BedConfigurationError, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (fragment.getContext() instanceof Activity) {
                        Activity act = (Activity) fragment.getContext();

                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        act.startActivityForResult(i, MainActivity.REQUEST_CODE_OPEN_FILE);
                    }
                }),
                new BedMenuItem(R.string.MenuFileDelete, R.drawable.delete_outline_android_28).setEnabled(hasSelection()).onClick(v -> {
                    if (fragment.getGlView().getRenderer().getModel() == null) return;

                    if (fragment.getGlView().getRenderer().deleteObject(fragment.getGlView().getRenderer().getSelectedObject())) {
                        fragment.getGlView().requestRender();
                        fragment.updateModel();
                    }
                }),
                new SpaceItem(portrait ? ViewUtils.dp(3) : 0, portrait ? 0 : ViewUtils.dp(3)),
                new BedMenuItem(R.string.MenuFileCalibrations, R.drawable.wrench_outline_28).setSingleLine(true).onClick(v -> {
                    if (!fragment.getGlView().getRenderer().getBed().isValid()) {
                        Toast.makeText(fragment.getContext(), R.string.BedConfigurationError, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    fragment.showUnfoldMenu(new CalibrationsMenu(), v);
                }),
                new SpaceItem(portrait ? ViewUtils.dp(3) : 0, portrait ? 0 : ViewUtils.dp(3)),
                new BedMenuItem(R.string.MenuFileImportProfiles, R.drawable.folder_simple_arrow_up_outline_28).onClick(v -> {
                    if (fragment.getContext() instanceof Activity) {
                        Activity act = (Activity) fragment.getContext();

                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        act.startActivityForResult(i, MainActivity.REQUEST_CODE_IMPORT_PROFILES);
                    }
                }),
                new BedMenuItem(R.string.MenuFileExportProfiles, R.drawable.folder_simple_arrow_right_outline_28).onClick(v -> {
                    CharSequence[] prints = new CharSequence[SliceBeam.CONFIG.printConfigs.size()];
                    boolean[] enabledPrints = new boolean[prints.length];
                    for (int i = 0; i < prints.length; i++) {
                        prints[i] = SliceBeam.CONFIG.printConfigs.get(i).getTitle();
                        enabledPrints[i] = true;
                    }

                    CharSequence[] filaments = new CharSequence[SliceBeam.CONFIG.filamentConfigs.size()];
                    boolean[] enabledFilaments = new boolean[filaments.length];
                    for (int i = 0; i < filaments.length; i++) {
                        filaments[i] = SliceBeam.CONFIG.filamentConfigs.get(i).getTitle();
                        enabledFilaments[i] = true;
                    }

                    CharSequence[] printers = new CharSequence[SliceBeam.CONFIG.printerConfigs.size()];
                    boolean[] enabledPrinters = new boolean[printers.length];
                    for (int i = 0; i < printers.length; i++) {
                        printers[i] = SliceBeam.CONFIG.printerConfigs.get(i).getTitle();
                        enabledPrinters[i] = true;
                    }

                    new BeamAlertDialogBuilder(v.getContext())
                            .setTitle(R.string.MenuFileExportProfilesPrints)
                            .setMultiChoiceItems(prints, enabledPrints, (dialog, which, isChecked) -> enabledPrints[which] = isChecked)
                            .setPositiveButton(android.R.string.ok, (d1, w1) -> new BeamAlertDialogBuilder(v.getContext())
                                    .setTitle(R.string.MenuFileExportProfilesFilaments)
                                    .setMultiChoiceItems(filaments, enabledFilaments, (dialog, which, isChecked) -> enabledFilaments[which] = isChecked)
                                    .setPositiveButton(android.R.string.ok, (d2, w2) -> new BeamAlertDialogBuilder(v.getContext())
                                            .setTitle(R.string.MenuFileExportProfilesPrinters)
                                            .setMultiChoiceItems(printers, enabledPrinters, (dialog, which, isChecked) -> enabledPrinters[which] = isChecked)
                                            .setPositiveButton(android.R.string.ok, (d3, w3) -> {
                                                boolean hasEnabled = false;
                                                MainActivity.EXPORTING_PRINTS = new ArrayList<>();
                                                for (int i = 0; i < enabledPrints.length; i++) {
                                                    if (enabledPrints[i]) {
                                                        hasEnabled = true;
                                                        MainActivity.EXPORTING_PRINTS.add(SliceBeam.CONFIG.printConfigs.get(i));
                                                    }
                                                }
                                                MainActivity.EXPORTING_FILAMENTS = new ArrayList<>();
                                                for (int i = 0; i < enabledFilaments.length; i++) {
                                                    if (enabledFilaments[i]) {
                                                        hasEnabled = true;
                                                        MainActivity.EXPORTING_FILAMENTS.add(SliceBeam.CONFIG.filamentConfigs.get(i));
                                                    }
                                                }
                                                MainActivity.EXPORTING_PRINTERS = new ArrayList<>();
                                                for (int i = 0; i < enabledPrinters.length; i++) {
                                                    if (enabledPrinters[i]) {
                                                        hasEnabled = true;
                                                        MainActivity.EXPORTING_PRINTERS.add(SliceBeam.CONFIG.printerConfigs.get(i));
                                                    }
                                                }
                                                if (!hasEnabled) {
                                                    new BeamAlertDialogBuilder(v.getContext())
                                                            .setTitle(R.string.MenuFileExportProfiles)
                                                            .setMessage(R.string.MenuFileExportProfilesNoProfiles)
                                                            .setPositiveButton(android.R.string.ok, null)
                                                            .show();
                                                    return;
                                                }

                                                if (fragment.getContext() instanceof Activity) {
                                                    Activity act = (Activity) fragment.getContext();
                                                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                                    i.setType("application/ini");
                                                    i.putExtra(Intent.EXTRA_TITLE, "SliceBeam_config_bundle.ini");
                                                    act.startActivityForResult(i, MainActivity.REQUEST_CODE_EXPORT_PROFILES);
                                                }
                                            })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show())
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
        );
    }

    @EventHandler(runOnMainThread = true)
    public void onObjectsChanged(ObjectsListChangedEvent e) {
        ((BedMenuItem) adapter.getItems().get(1)).setEnabled(hasSelection());
        adapter.notifyItemChanged(1);
    }

    @EventHandler(runOnMainThread = true)
    public void onSelectionChanged(SelectedObjectChangedEvent e) {
        ((BedMenuItem) adapter.getItems().get(1)).setEnabled(hasSelection());
        adapter.notifyItemChanged(1);
    }

    public final class CalibrationsMenu extends UnfoldMenu {

        public int getRequestedSize(FrameLayout into, boolean portrait) {
            return (int) (portrait ? into.getHeight() * 0.3f : into.getWidth() * 0.6f);
        }

        private String loadJSLoader(String key) {
            try {
                InputStream in = SliceBeam.INSTANCE.getAssets().open("js_loader/" + key + ".js");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[10240]; int c;
                while ((c = in.read(buffer)) != -1) {
                    bos.write(buffer, 0, c);
                }
                bos.close();
                in.close();

                ConfigObject cfg = SliceBeam.buildCurrentConfigObject();
                Bed3D bed = FileMenu.this.fragment.getGlView().getRenderer().getBed();
                double bedX = bed.getVolumeMax().x - bed.getVolumeMin().x;
                double bedY = bed.getVolumeMax().y - bed.getVolumeMin().y;

                String str = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder(str);
                Pattern placeholderPattern = Pattern.compile("\\$\\['(\\w+?)(\\[\\d+]|)']");
                Matcher m = placeholderPattern.matcher(str);
                int offset = 0;
                while (m.find()) {
                    String pKey = m.group(1);
                    String pIndex = m.group(2);
                    int index = pIndex.isEmpty() ? -1 : Integer.parseInt(pIndex.substring(1, pIndex.length() - 1));

                    String v;
                    boolean quote = false;
                    switch (pKey) {
                        case "bed_x":
                            v = String.format(Locale.ROOT, "%.1f", bedX);
                            quote = true;
                            break;
                        case "bed_y":
                            v = String.format(Locale.ROOT, "%.1f", bedY);
                            quote = true;
                            break;
                        case "color_accent":
                            v = String.format(Locale.ROOT, "#%06X", ThemesRepo.getColor(android.R.attr.colorAccent) & 0xFFFFFF);
                            break;
                        case "window_background_dark":
                            v = String.format(Locale.ROOT, "#%06X", BeamTheme.DARK.colors.get(android.R.attr.windowBackground) & 0xFFFFFF);
                            break;
                        case "window_background_light":
                            v = String.format(Locale.ROOT, "#%06X", BeamTheme.LIGHT.colors.get(android.R.attr.windowBackground) & 0xFFFFFF);
                            break;
                        case "is_dark_theme":
                            v = String.valueOf(ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f);
                            break;
                        default:
                            v = cfg.get(pKey);
                            quote = true;
                            break;
                    }
                    if (v != null && index != -1) {
                        try {
                            v = v.split(",")[index];
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            v = "";
                        }
                    }
                    String newVal = escapeStringForJs(v);
                    if (quote) {
                        newVal = "'" + newVal + "'";
                    }
                    sb = sb.replace(m.start() + offset, m.end() + offset, newVal);
                    offset += newVal.length() - (m.end() - m.start());
                }

                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected View onCreateView(Context ctx, boolean portrait) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);

            RecyclerView rv = new FadeRecyclerView(ctx);
            SimpleRecyclerAdapter adapter = new SimpleRecyclerAdapter();
            adapter.setItems(Arrays.asList(
                    new PreferenceItem().setIcon(R.drawable.menu_calibrate_la_28).setTitle(ctx.getString(R.string.MenuFileCalibrationsLA)).setSubtitle(ctx.getString(R.string.MenuFileCalibrationsLADescription)).setOnClickListener(v -> {
                        if (ctx instanceof MainActivity) {
                            ((MainActivity) ctx).showUnfoldMenu(new WebViewMenu(Uri.parse("https://k3d.tech/calibrations/la/calibrator/").buildUpon().appendQueryParameter("lang", getK3DLanguage()).build(), loadJSLoader("k3d_la")).setFragment(fragment), v);
                        }
                    }),
                    new PreferenceItem().setIcon(R.drawable.menu_calibrate_retract_28).setTitle(ctx.getString(R.string.MenuFileCalibrationsRetract)).setSubtitle(ctx.getString(R.string.MenuFileCalibrationsRetractDescription)).setOnClickListener(v -> {
                        if (ctx instanceof MainActivity) {
                            ((MainActivity) ctx).showUnfoldMenu(new WebViewMenu(Uri.parse("https://k3d.tech/calibrations/retractions/calibrator/").buildUpon().appendQueryParameter("lang", getK3DLanguage()).build(), loadJSLoader("k3d_rct")).setFragment(fragment), v);
                        }
                    })
            ));
            rv.setAdapter(adapter);
            ll.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1f)));

            LinearLayout toolbar = new LinearLayout(ctx);
            toolbar.setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0));
            toolbar.setOnClickListener(v -> dismiss());

            ImageView icon = new ImageView(ctx);
            icon.setImageResource(R.drawable.arrow_left_outline_28);
            icon.setColorFilter(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            toolbar.addView(icon, new LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)));

            TextView title = new TextView(ctx);
            title.setText(R.string.MenuOrientationPositionBack);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{
                leftMargin = ViewUtils.dp(12);
            }});
            ll.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)));
            return ll;
        }

        @EventHandler(runOnMainThread = true)
        public void onDismiss(NeedDismissCalibrationsMenu e) {
            dismiss();
        }

        @Override
        protected void onCreate() {
            super.onCreate();

            SliceBeam.EVENT_BUS.registerListener(this);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();

            SliceBeam.EVENT_BUS.unregisterListener(this);
        }
    }
}
