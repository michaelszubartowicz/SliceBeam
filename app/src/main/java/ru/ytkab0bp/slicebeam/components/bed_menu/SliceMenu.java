package ru.ytkab0bp.slicebeam.components.bed_menu;

import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.message.BasicHeader;
import ru.ytkab0bp.slicebeam.BuildConfig;
import ru.ytkab0bp.slicebeam.MainActivity;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.fragment.BedFragment;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.slic3r.GCodeViewer;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.PositionScrollView;

public class SliceMenu extends ListBedMenu {
    private AsyncHttpClient client = new AsyncHttpClient();

    {
        client.setLoggingEnabled(true);
        client.setMaxRetriesAndTimeout(0, 5000);
    }

    private final static List<String> SUPPORTED_SEND = Collections.singletonList("octoprint");
    private int lastUid;

    @Override
    protected List<SimpleRecyclerItem> onCreateItems(boolean portrait) {
        lastUid = SliceBeam.CONFIG_UID;
        List<SimpleRecyclerItem> items = new ArrayList<>(Arrays.asList(
                new BedMenuItem(R.string.MenuSliceInfo, R.drawable.square_stack_up_outline_28).onClick(v -> fragment.showUnfoldMenu(new PrintInfoMenu(), v)),
                new BedMenuItem(R.string.MenuSliceExportToFile, R.drawable.folder_simple_arrow_right_outline_28).onClick(v -> {
                    if (fragment.getContext() instanceof Activity) {
                        Activity act = (Activity) fragment.getContext();
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.setType("application/x-gcode");
                        i.putExtra(Intent.EXTRA_TITLE, fragment.getGlView().getRenderer().getGcodeResult().getRecommendedName());
                        act.startActivityForResult(i, MainActivity.REQUEST_CODE_EXPORT_GCODE);
                    }
                }),
                new BedMenuItem(R.string.MenuSliceShare, R.drawable.share_external_28).onClick(v -> {
                    if (fragment.getContext() instanceof Activity) {
                        File f = BedFragment.getTempGCodePath();

                        Activity act = (Activity) fragment.getContext();
                        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        i.setType("application/x-gcode");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Simple trick for Samsung to display "1 element" instead of "temp.gcode"
                            // It doesn't actually resolve name from provider and uses path-parsing instead, bruh.
                            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(Collections.singletonList(FileProvider.getUriForFile(act, BuildConfig.APPLICATION_ID + ".provider", f, BedFragment.getTempFileName()))));
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } else {
                            i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        }
                        act.startActivity(Intent.createChooser(i, null));
                    }
                })
        ));
        ConfigObject obj = SliceBeam.CONFIG.findPrinter(SliceBeam.CONFIG.presets.get("printer"));
        assertTrue(obj != null);
        String type = obj.get("host_type");
        if (type == null) type = "octoprint";
        String host = obj.get("print_host");
        String apiKey = obj.get("printhost_apikey");
        if (SUPPORTED_SEND.contains(type) && !TextUtils.isEmpty(host)) {
            String finalType = type;
            items.add(new BedMenuItem(R.string.MenuSliceSendToPrinter, R.drawable.send_outline_28).onClick(v -> upload(finalType, host, apiKey, false)));
            items.add(new BedMenuItem(R.string.MenuSliceSendToPrinterAndPrint, R.drawable.send_28).onClick(v -> upload(finalType, host, apiKey, true)));
        }
        return items;
    }

    private void upload(String type, String host, String apiKey, boolean print) {
        String name = fragment.getGlView().getRenderer().getGcodeResult().getRecommendedName();
        switch (type) {
            default:
            case "octoprint":
                if (!host.startsWith("http://")) {
                    host = "http://" + host;
                }
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuSliceSendToPrinterStarted));
                Header[] headers = TextUtils.isEmpty(apiKey) ? new Header[0] : new Header[] {new BasicHeader("X-Api-Key", apiKey)};
                RequestParams params = new RequestParams();
                try {
                    params.put("file", new FileInputStream(BedFragment.getTempGCodePath()), name, ContentType.TEXT_PLAIN.getMimeType());
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
                params.put("select", String.valueOf(print));
                params.put("print", String.valueOf(print));

                client.post(SliceBeam.INSTANCE, host + "/api/files/local", headers, params, null, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        try {
                            JSONObject obj = new JSONObject(new String(responseBody));
                            if (!obj.has("action") && !obj.has("files")) {
                                throw new JSONException(obj.toString());
                            }
                            SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(print ? R.string.MenuSliceSendToPrinterPrintStarted : R.string.MenuSliceSendToPrinterOK));
                        } catch (JSONException e) {
                            onFailure(statusCode, headers, responseBody, e);
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(fragment.getContext())
                                .setTitle(R.string.MenuSliceSendToPrinterFailed)
                                .setMessage(error.toString())
                                .setPositiveButton(android.R.string.ok, null)
                                .show());
                    }
                });
                break;
        }
    }

    @Override
    public void onViewCreated(View v) {
        super.onViewCreated(v);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (lastUid != SliceBeam.CONFIG_UID) {
                    adapter.setItems(onCreateItems(v.getWidth() < v.getHeight()));
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {}
        });
    }

    private final static class PrintInfoMenu extends UnfoldMenu {
        private PositionScrollView fromTrack, toTrack;
        private TextView title;

        private GCodeViewer getViewer() {
            return fragment.getGlView().getRenderer().getViewer();
        }

        private void applyView(int from, int to) {
            GCodeViewer viewer = getViewer();
            if (viewer == null) {
                return;
            }
            viewer.setLayersViewRange(from - 1, to - 1);
            fragment.getGlView().requestRender();

            title.setText(fragment.getContext().getString(R.string.MenuSliceInfoLayers, from, to));
        }

        @Override
        protected View onCreateView(Context ctx, boolean portrait) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);

            ll.addView(new Space(ctx), new LinearLayout.LayoutParams(0, 0, 1f));

            title = new TextView(ctx);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setTextColor(ThemesRepo.getColor(android.R.attr.colorAccent));
            title.setGravity(Gravity.CENTER);
            ll.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                bottomMargin = ViewUtils.dp(4);
            }});

            fromTrack = new PositionScrollView(ctx);
            fromTrack.setProgressListener(integer -> {
                if (getViewer() == null) return;
                toTrack.setMinMax(integer, (int) getViewer().getLayersCount());
                if (toTrack.getCurrentPosition() < integer) {
                    toTrack.setCurrentPosition(integer);
                }
                applyView(integer, toTrack.getCurrentPosition());
            });
            fromTrack.setListener(integer -> applyView(integer, toTrack.getCurrentPosition()));
            ll.addView(fromTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            toTrack = new PositionScrollView(ctx);
            toTrack.setProgressListener(integer -> {
                // TODO: apply only visual?
                applyView(fromTrack.getCurrentPosition(), integer);
            });
            toTrack.setListener(integer -> applyView(fromTrack.getCurrentPosition(), integer));
            ll.addView(toTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

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

        @Override
        public int getRequestedSize(FrameLayout into, boolean portrait) {
            return portrait ? ViewUtils.dp(80) * 2 + ViewUtils.dp(24) + ViewUtils.dp(52) + ViewUtils.dp(12) : super.getRequestedSize(into, false);
        }

        @Override
        protected void onCreate() {
            super.onCreate();

            GCodeViewer viewer = getViewer();
            long max = viewer.getLayersCount();
            fromTrack.setMinMax(1, (int) max);
            toTrack.setMinMax(1, (int) max);

            Pair<Long, Long> range = viewer.getLayersViewRange();
            // TODO: Support long instead of int in PositionScrollView
            fromTrack.setCurrentPosition(Math.min(range.first.intValue() + 1, range.second.intValue() + 1));
            toTrack.setCurrentPosition(Math.max(range.first.intValue() + 1, range.second.intValue() + 1));

            title.setText(fragment.getContext().getString(R.string.MenuSliceInfoLayers, fromTrack.getCurrentPosition(), toTrack.getCurrentPosition()));
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();

            fromTrack.stopScroll();
            toTrack.stopScroll();
            if (getViewer() != null) {
                applyView(1, (int) getViewer().getLayersCount());
            }
        }
    }
}
