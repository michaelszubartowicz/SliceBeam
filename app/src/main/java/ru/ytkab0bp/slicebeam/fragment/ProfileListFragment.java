package ru.ytkab0bp.slicebeam.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import com.mrudultora.colorpicker.ColorPickerPopUp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.BeamColorPickerPopUp;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.navigation.Fragment;
import ru.ytkab0bp.slicebeam.recycler.CubicBezierItemAnimator;
import ru.ytkab0bp.slicebeam.recycler.PreferenceItem;
import ru.ytkab0bp.slicebeam.recycler.PreferenceSwitchItem;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.BeamButton;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.FadeRecyclerView;
import ru.ytkab0bp.slicebeam.view.ProfileDropdownView;

public abstract class ProfileListFragment extends Fragment {
    private final static Object ROTATION_PAYLOAD = new Object();

    protected ProfileDropdownView dropdownView;
    protected FadeRecyclerView recyclerView;
    protected ImageView resetButton;
    protected BeamButton saveButton;

    protected boolean changedConfig;

    private List<OptionWrapper> currentList = Collections.emptyList();
    private SparseArray<List<OptionWrapper>> categoryElements = new SparseArray<>();
    private SparseBooleanArray unfolded = new SparseBooleanArray();

    @Override
    public View onCreateView(Context ctx) {
        FrameLayout containerLayout = new FrameLayout(ctx);
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        dropdownView = new ProfileDropdownView(ctx);
        ProfileListItem selectedItem = getSelectedItem();
        dropdownView.setTitle(selectedItem != null ? selectedItem.getTitle() : null);
        dropdownView.setOnClickListener(v -> {
            List<ProfileListItem> items = getItems(true);
            String[] titles = new String[items.size()];
            int selected = -1;
            for (int i = 0; i < items.size(); i++) {
                ProfileListItem item = items.get(i);
                titles[i] = item.getTitle();
                if (item.isSelected()) {
                    selected = i;
                }
            }

            AlertDialog.Builder builder = new BeamAlertDialogBuilder(getContext())
                    .setTitle(getTitle())
                    .setSingleChoiceItems(titles, selected, (dialog, which) -> {
                        dropdownView.setTitle(items.get(which).getTitle());
                        selectItem(items.get(which));
                        onUpdateConfigItems();
                        dialog.dismiss();
                    });
            if (items.size() > 1) {
                builder.setNegativeButton(R.string.SettingsDeleteProfile, (dialog, which) -> {
                    deleteCurrentProfile();
                    onUpdateConfigItems();
                });
            }
            builder.setPositiveButton(R.string.SettingsCloneProfile, (dialog, which) -> {
                cloneCurrentProfile();
                onUpdateConfigItems();
            });
            builder.show();
        });
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        boolean portrait = dm.widthPixels < dm.heightPixels;
        ll.addView(dropdownView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(48)) {{
            topMargin = portrait ? 0 : ViewUtils.dp(12);
            leftMargin = rightMargin = ViewUtils.dp(12);
            bottomMargin = ViewUtils.dp(8);
        }});
        recyclerView = new FadeRecyclerView(ctx) {
            private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                onApplyTheme();
            }

            @Override
            public void onDraw(Canvas c) {
                super.onDraw(c);

                int startI = -1;
                boolean drawn = false;
                for (int i = 0; i < getChildCount(); i++) {
                    View ch = getChildAt(i);
                    int pos = getChildViewHolder(ch).getAdapterPosition();
                    if (pos == -1 || ch.getAlpha() < 1) continue;

                    boolean top = currentList.get(pos).title != null;
                    boolean bottom = pos == getAdapter().getItemCount() - 1 || currentList.get(pos + 1).title != null;

                    if (top && startI != -1) {
                        c.drawRoundRect(0, getChildAt(startI).getTop() + getChildAt(startI).getTranslationY(), getWidth(), ch.getTop() + ch.getTranslationY() - ViewUtils.dp(8), ViewUtils.dp(32), ViewUtils.dp(32), bgPaint);
                        drawn = true;
                        startI = -1;
                    } else if (bottom) {
                        if (!top && startI == -1) {
                            c.drawRoundRect(0, -ViewUtils.dp(32), getWidth(), ch.getBottom() + ch.getTranslationY(), ViewUtils.dp(32), ViewUtils.dp(32), bgPaint);
                            drawn = true;
                        }
                    }

                    if (top) {
                        int color = ch.getTag() != null ? ThemesRepo.getColor((Integer) ch.getTag()) : 0;
                        if (color != 0) {
                            bgPaint.setColor(ColorUtils.setAlphaComponent(color, 0x22));
                        } else {
                            bgPaint.setColor(ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10));
                        }
                        startI = i;
                    }

                    if (startI != -1 && bottom && pos == getAdapter().getItemCount() - 1) {
                        View s = getChildAt(startI);
                        c.drawRoundRect(0, s.getTop() + s.getTranslationY(), getWidth(), ch.getBottom() + ch.getTranslationY(), ViewUtils.dp(32), ViewUtils.dp(32), bgPaint);
                        drawn = true;
                        startI = -1;
                    }
                }
                if (startI != -1) {
                    View ch = getChildAt(startI);
                    View last = getChildAt(getChildCount() - 1);
                    boolean bottom = getChildViewHolder(last).getAdapterPosition() == getAdapter().getItemCount() - 1;

                    c.drawRoundRect(0, ch.getTop() + ch.getTranslationY(), getWidth(), bottom ? ViewUtils.lerp(ch.getBottom(), last.getBottom(), last.getAlpha()) : getHeight() + ViewUtils.dp(32), ViewUtils.dp(32), ViewUtils.dp(32), bgPaint);
                    drawn = true;
                }

                if (!drawn) {
                    c.drawRoundRect(0, -ViewUtils.dp(32), getWidth(), getHeight() + ViewUtils.dp(32), ViewUtils.dp(32), ViewUtils.dp(32), bgPaint);
                }

                // TODO: Determine when user folds/unfolds category, animate only there
                invalidate();
            }

            @Override
            public void onApplyTheme() {
                super.onApplyTheme();
                if (bgPaint != null) {
                    bgPaint.setColor(ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10));
                }
            }
        };
        recyclerView.setItemAnimator(new CubicBezierItemAnimator());
        recyclerView.setAdapter(new RecyclerView.Adapter() {
            private final static int TYPE_TITLE = 0, TYPE_SIMPLE = 1;

            private Map<Class<?>, Integer> viewType = new HashMap<>();
            private Map<Integer, SimpleRecyclerItem> viewCreator = new HashMap<>();
            private int lastType = TYPE_SIMPLE;

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v;
                switch (viewType) {
                    default: {
                        v = viewCreator.get(viewType).onCreateView(ctx);
                        break;
                    }
                    case TYPE_TITLE:
                        v = new CategoryHolderView(ctx);
                        break;
                }
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
                if (payloads.contains(ROTATION_PAYLOAD)) {
                    CategoryHolderView holderView = (CategoryHolderView) holder.itemView;
                    OptionWrapper w = currentList.get(position);
                    new SpringAnimation(holderView.dropdown, DynamicAnimation.ROTATION)
                            .setSpring(new SpringForce(unfolded.get(w.categoryIndex) ? 180 : 0)
                                    .setStiffness(1000f)
                                    .setDampingRatio(1f))
                            .start();
                    return;
                }
                super.onBindViewHolder(holder, position, payloads);
            }

            @SuppressLint("RecyclerView")
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
                boolean top = position == 0 || currentList.get(position).title != null;
                params.topMargin = top ? ViewUtils.dp(8) : 0;
                params.bottomMargin = position == getItemCount() - 1 ? ViewUtils.dp(8) : 0;

                int type = getItemViewType(position);
                switch (type) {
                    default: {
                        OptionElement el = currentList.get(position).optionEl;
                        el.boundIndex = position;
                        el.simpleItem.onBindView(holder.itemView);
                        break;
                    }
                    case TYPE_TITLE: {
                        OptionWrapper w = currentList.get(position);
                        CategoryHolderView holderView = (CategoryHolderView) holder.itemView;
                        holderView.icon.setImageResource(w.icon);
                        holderView.title.setText(Slic3rLocalization.getString(w.title));
                        holderView.dropdown.setRotation(unfolded.get(w.categoryIndex) ? 180 : 0);
                        holderView.dropdown.setVisibility(w.onClick != null ? View.GONE : View.VISIBLE);
                        holderView.setTag(w.color);
                        holderView.icon.setTag(w.noTint ? true : null);
                        holderView.onApplyTheme();

                        holderView.setOnClickListener(v -> {
                            if (w.onClick != null) {
                                w.onClick.run();
                                return;
                            }

                            boolean unfold = !unfolded.get(w.categoryIndex, false);
                            unfolded.put(w.categoryIndex, unfold);
                            notifyItemChanged(holder.getAdapterPosition(), ROTATION_PAYLOAD);

                            int i = holder.getAdapterPosition() + 1;
                            List<OptionWrapper> l = categoryElements.get(w.categoryIndex);
                            if (l != null) {
                                if (unfold) {
                                    currentList.addAll(i, l);
                                    notifyItemRangeInserted(i, l.size());
                                    recyclerView.invalidate();
                                } else {
                                    currentList.removeAll(l);
                                    notifyItemRangeRemoved(i, l.size());
                                    recyclerView.invalidate();
                                }
                            }
                        });
                        break;
                    }
                }
            }

            @Override
            public int getItemViewType(int position) {
                OptionWrapper w = currentList.get(position);
                if (w.title != null) return TYPE_TITLE;

                if (w.optionEl.simpleItem != null) {
                    SimpleRecyclerItem it = w.optionEl.simpleItem;
                    Integer t = viewType.get(it.getClass());
                    if (t == null) {
                        viewType.put(it.getClass(), t = lastType++);
                        viewCreator.put(t, it);
                    }
                    return t;
                }

                return -1;
            }

            @Override
            public int getItemCount() {
                return currentList.size();
            }
        });
        setConfigItems(getConfigItems());
        ll.addView(recyclerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomLL = new LinearLayout(ctx);
        bottomLL.setOrientation(LinearLayout.HORIZONTAL);
        bottomLL.setGravity(Gravity.CENTER_VERTICAL);
        saveButton = new BeamButton(ctx);
        saveButton.setText(R.string.SettingsSave);
        saveButton.setPadding(ViewUtils.dp(21), ViewUtils.dp(12), ViewUtils.dp(21), ViewUtils.dp(12));

        saveButton.setOnClickListener(v -> {
            FrameLayout fl = new FrameLayout(ctx);
            EditText text = new EditText(ctx);
            text.setText(getCurrentConfig().getTitle());
            text.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            fl.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = rightMargin = ViewUtils.dp(21);
            }});

            AlertDialog dialog = new BeamAlertDialogBuilder(ctx)
                    .setTitle(R.string.SettingsSaveTitle)
                    // TODO: Draw settings delta
                    .setView(fl)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        onApplyConfig(text.getText().toString());
                        resetButton.animate().alpha(0.6f).start();
                        resetButton.setClickable(false);
                        onUpdateConfigItems();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            text.addTextChangedListener(new TextWatcher() {
                char[] chars = Slic3rConfigWrapper.BLACKLISTED_SYMBOLS.toCharArray();

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String str = s.toString();
                    boolean valid = true;
                    for (int i = 0; i < str.length(); i++) {
                        char ch = str.charAt(i);
                        for (char aChar : chars) {
                            if (ch == aChar) {
                                valid = false;
                                break;
                            }
                        }
                        if (!valid) break;
                    }
                    text.getBackground().setTintList(ColorStateList.valueOf(ThemesRepo.getColor(valid ? android.R.attr.textColorPrimary : R.attr.textColorNegative)));
                    View btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setAlpha(valid ? 1f : 0.6f);
                    btn.setClickable(valid);
                }
            });
            // I don't think we need keyboard here every time
//            ViewUtils.postOnMainThread(() -> {
//                text.requestFocus();
//                InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
//                imm.showSoftInput(text, 0);
//                text.setSelection(text.getText().length());
//            }, 500);
        });
        bottomLL.addView(saveButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        resetButton = new ImageView(ctx);
        resetButton.setImageResource(R.drawable.refresh_outline_28);
        resetButton.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorPrimary)));
        resetButton.setScaleX(-1f);
        resetButton.setAlpha(0.6f);
        resetButton.setOnClickListener(v -> new BeamAlertDialogBuilder(ctx)
                .setTitle(R.string.SettingsResetProfileTitle)
                .setMessage(R.string.SettingsResetProfileDescription)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    onResetConfig();
                    resetButton.animate().alpha(0.6f).start();
                    resetButton.setClickable(false);
                    onUpdateConfigItems();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        resetButton.setClickable(false);
        bottomLL.addView(resetButton, new LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)) {{
            leftMargin = ViewUtils.dp(12);
            rightMargin = ViewUtils.dp(4);
        }});

        ll.addView(bottomLL, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            leftMargin = topMargin = rightMargin = bottomMargin = ViewUtils.dp(12);
            bottomMargin += portrait ? 0 : ViewUtils.dp(6);
        }});

        containerLayout.addView(ll);

        return containerLayout;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SliceBeam.EVENT_BUS.registerListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SliceBeam.EVENT_BUS.unregisterListener(this);
    }

    @SuppressLint("NotifyDataSetChanged")
    protected void setConfigItems(List<OptionElement> items) {
        List<OptionWrapper> list = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < items.size(); i++) {
            OptionElement el = items.get(i);
            if (el == null) continue;
            OptionWrapper w = el.title != null ? new OptionWrapper(el.icon, el.title, el.onClick, el.color, el.noTint) : new OptionWrapper(el);
            if (el.title != null) {
                w.categoryIndex = j;
                categoryElements.put(j, new ArrayList<>());
                j++;
                list.add(w);
            } else {
                categoryElements.get(j - 1).add(w);
            }
        }
        currentList = list;
        recyclerView.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();

        ProfileListItem selectedItem = getSelectedItem();
        dropdownView.setTitle(selectedItem != null ? selectedItem.getTitle() : null);
    }

    protected ProfileListItem getSelectedItem() {
        for (ProfileListItem item : getItems(false)) {
            if (item.isSelected()) {
                return item;
            }
        }
        return null;
    }

    private String opt(ConfigOptionDef def, int i) {
        String v = getCurrentConfig().get(def.key);
        if (i != -1) {
            try {
                v = v.split(",")[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.w("ProfileListFragment", "Failed to parse mm option", e);
            }
        }
        return v != null ? v : (Objects.equals("host_type", def.key) ? "octoprint" : def.defaultValue);
    }

    protected void updateConfigField(ConfigOptionDef def, int i, String value) {
        if (i != -1) {
            String[] vals = opt(def, -1).split(",");
            vals[i] = value;
            value = TextUtils.join(",", vals);
        }

        if (!Objects.equals(opt(def, i), value)) {
            changedConfig = true;
            resetButton.animate().alpha(1).start();
            resetButton.setClickable(true);
        }
        getCurrentConfig().put(def.key, value);
    }

    @SuppressLint("NotifyDataSetChanged")
    protected void onUpdateConfigItems() {
        recyclerView.getAdapter().notifyDataSetChanged();
    }

    protected abstract void cloneCurrentProfile();
    protected abstract void deleteCurrentProfile();
    protected abstract void onApplyConfig(String title);
    protected abstract void onResetConfig();
    protected abstract ConfigObject getCurrentConfig();
    protected abstract int getTitle();
    protected abstract void selectItem(ProfileListItem item);
    protected abstract List<ProfileListItem> getItems(boolean filter);
    protected abstract List<OptionElement> getConfigItems();

    public interface ProfileListItem {
        String getTitle();
        boolean isSelected();
    }

    public final class OptionElement {
        public int icon;
        public String title;
        public int color;
        public boolean noTint;

        public SimpleRecyclerItem simpleItem;

        private Runnable onClick;
        private int boundIndex;

        public OptionElement(ConfigOptionDef def) {
            this(def, -1);
        }

        public OptionElement(ConfigOptionDef def, int eIndex) {
            if (def.type != ConfigOptionDef.ConfigOptionType.BOOL && def.type != ConfigOptionDef.ConfigOptionType.BOOLS) {
                simpleItem = new PreferenceItem().setTitle(Slic3rLocalization.getString(def.getLabel())).setOnClickListener(v -> {
                    if (def.guiType == ConfigOptionDef.GUIType.COLOR) {
                        int defClr;
                        try {
                            defClr = (Color.parseColor(opt(def, eIndex)) & 0xFFFFFF) + 0xFF000000;
                        } catch (Exception ignored) {
                            defClr = Prefs.getAccentColor();
                        }
                        new BeamColorPickerPopUp(getContext())
                                .setDialogTitle(Slic3rLocalization.getString(def.getFullLabel()))
                                .setDefaultColor(defClr)
                                .setShowAlpha(false)
                                .setOnPickColorListener(new ColorPickerPopUp.OnPickColorListener() {
                                    @Override
                                    public void onColorPicked(int color) {
                                        int clr = color & 0xFFFFFF;
                                        updateConfigField(def, eIndex, String.format("#%06X", clr));
                                        recyclerView.getAdapter().notifyItemChanged(boundIndex);
                                    }

                                    @Override
                                    public void onCancel() {}
                                })
                                .show();
                        return;
                    }
                    AtomicReference<AlertDialog> ref = new AtomicReference<>();
                    AlertDialog.Builder builder = new BeamAlertDialogBuilder(getContext())
                            .setTitle(Slic3rLocalization.getString(def.getFullLabel()));
                    
                    if (def.type == ConfigOptionDef.ConfigOptionType.ENUM) {
                        String[] labels;
                        String[] values;
                        if (Objects.equals("host_type", def.key)) {
                            labels = new String[]{"OctoPrint"};
                            values = new String[]{"octoprint"};
                        } else {
                            labels = new String[def.enumLabels.length];
                            values = def.enumValues;
                            for (int i = 0; i < def.enumLabels.length; i++) {
                                labels[i] = Slic3rLocalization.getString(def.enumLabels[i]);
                            }
                        }
                        builder.setSingleChoiceItems(labels, Arrays.asList(values).indexOf(opt(def, eIndex)), (dialog, which) -> {
                            updateConfigField(def, eIndex, values[which]);
                            // TODO: Update only value
                            recyclerView.getAdapter().notifyItemChanged(boundIndex);
                            dialog.dismiss();
                        });
                    } else {
                        String msg = Slic3rLocalization.getString(def.tooltip);

                        Context ctx = getContext();
                        LinearLayout ll = new LinearLayout(ctx);
                        ll.setOrientation(LinearLayout.VERTICAL);
                        if (!TextUtils.isEmpty(msg)) {
                            ScrollView scrollView = new ScrollView(ctx);
                            TextView subtitle = new TextView(ctx);
                            subtitle.setTextAppearance(ctx, com.google.android.material.R.style.MaterialAlertDialog_Material3_Body_Text);
                            subtitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
                            subtitle.setText(msg);
                            subtitle.setPadding(ViewUtils.dp(24), ViewUtils.dp(12), ViewUtils.dp(24), ViewUtils.dp(12));
                            scrollView.addView(subtitle, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                            ll.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                        }

                        EditText text = new EditText(ctx);
                        text.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));

                        if (def.type == ConfigOptionDef.ConfigOptionType.FLOAT) {
                            text.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            if (def.min < 0) {
                                text.setInputType(text.getInputType() | InputType.TYPE_NUMBER_FLAG_SIGNED);
                            }
                        } else if (def.type == ConfigOptionDef.ConfigOptionType.INT) {
                            text.setInputType(InputType.TYPE_CLASS_NUMBER);
                            if (def.min < 0) {
                                text.setInputType(text.getInputType() | InputType.TYPE_NUMBER_FLAG_SIGNED);
                            }
                        } else {
                            text.setInputType(InputType.TYPE_CLASS_TEXT);
                        }
                        if (def.multiline) {
                            text.setInputType(text.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                            text.setMaxLines(def.height);
                        }

                        text.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {}

                            private boolean validateFloat(String msg) {
                                if (msg.isEmpty()) return false;
                                try {
                                    float v = Float.parseFloat(msg);
                                    return v >= def.min && v <= def.max;
                                } catch (NumberFormatException e) {
                                    return false;
                                }
                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                String msg = s.toString();
                                boolean valid;

                                if (def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT) {
                                    valid = msg.endsWith("%") ? validateFloat(msg.substring(0, msg.length() - 1).trim()) : validateFloat(msg.trim());
                                } else if (def.type == ConfigOptionDef.ConfigOptionType.PERCENT) {
                                    valid = msg.endsWith("%") && validateFloat(msg.substring(0, msg.length() - 1).trim());
                                } else if (def.type == ConfigOptionDef.ConfigOptionType.FLOAT || def.type == ConfigOptionDef.ConfigOptionType.INT) {
                                    valid = validateFloat(msg.trim());
                                } else if (def.type == ConfigOptionDef.ConfigOptionType.FLOATS || def.type == ConfigOptionDef.ConfigOptionType.INTS) {
                                    String[] vals = msg.split(",");
                                    valid = true;
                                    for (String val : vals) {
                                        if (!validateFloat(val.trim())) {
                                            valid = false;
                                            break;
                                        }
                                    }
                                } else {
                                    valid = true;
                                }
                                text.getBackground().setTintList(ColorStateList.valueOf(ThemesRepo.getColor(valid ? android.R.attr.textColorPrimary : R.attr.textColorNegative)));
                                if (ref.get() != null) {
                                    View btn = ref.get().getButton(AlertDialog.BUTTON_POSITIVE);
                                    btn.setAlpha(valid ? 1f : 0.6f);
                                    btn.setClickable(valid);
                                }
                            }
                        });
                        text.setText(opt(def, eIndex));
                        ll.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                            leftMargin = rightMargin = ViewUtils.dp(21);
                        }});

                        builder.setView(ll).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String str = text.getText().toString();
                            updateConfigField(def, eIndex, str);
                            // TODO: Update only value
                            recyclerView.getAdapter().notifyItemChanged(boundIndex);
                        }).setNegativeButton(android.R.string.cancel, null);
                        ViewUtils.postOnMainThread(() -> {
                            text.requestFocus();
                            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.showSoftInput(text, 0);
                            text.setSelection(text.getText().length());
                        }, 500);
                    }

                    ref.set(builder.show());
                });

                if (def.type == ConfigOptionDef.ConfigOptionType.STRING || def.type == ConfigOptionDef.ConfigOptionType.STRINGS) {
                    ((PreferenceItem) simpleItem).setSubtitleProvider(() -> opt(def, eIndex).trim());
                    if (def.key.endsWith("_gcode")) {
                        ((PreferenceItem) simpleItem).setTitle(null);
                    }
                } else {
                    ((PreferenceItem) simpleItem).setValueProvider(() -> def.type == ConfigOptionDef.ConfigOptionType.ENUM ? Slic3rLocalization.getString(def.enumLabels[Arrays.asList(def.enumValues).indexOf(opt(def, eIndex))]) : opt(def, eIndex));
                }
            }
            switch (def.type) {
                case BOOL:
                case BOOLS:
                    simpleItem = new PreferenceSwitchItem().setTitle(Slic3rLocalization.getString(def.label))
                            .setValueProvider(() -> "1".equals(opt(def, eIndex)))
                            .setChangeListener((buttonView, isChecked) -> updateConfigField(def, eIndex, String.valueOf(isChecked ? 1 : 0)));
                    break;
            }
        }

        public OptionElement(int icon, String title) {
            this.icon = icon;
            this.title = title;
        }

        public OptionElement(SimpleRecyclerItem item) {
            simpleItem = item;
        }

        public OptionElement setOnClick(Runnable onClick) {
            this.onClick = onClick;
            return this;
        }

        public OptionElement setColor(int color, boolean noTint) {
            this.color = color;
            this.noTint = noTint;
            return this;
        }
    }

    public final static class SubHeader extends SimpleRecyclerItem<SubHeader.SubHeaderHolderView> {
        public final String title;

        public SubHeader(String title) {
            this.title = title;
        }

        @Override
        public SubHeaderHolderView onCreateView(Context ctx) {
            return new SubHeaderHolderView(ctx);
        }

        @Override
        public void onBindView(SubHeaderHolderView view) {
            view.bind(this);
        }

        private final static class SubHeaderHolderView extends LinearLayout {
            TextView title;

            SubHeaderHolderView(Context context) {
                super(context);
                setOrientation(VERTICAL);

                addView(new DividerView(context, R.attr.dividerContrastColor), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1f)));

                title = new TextView(context);
                title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                title.setPadding(ViewUtils.dp(20), ViewUtils.dp(12), ViewUtils.dp(20), 0);
                addView(title);
            }

            void bind(SubHeader h) {
                title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
                title.setText(Slic3rLocalization.getString(h.title));
            }
        }
    }

    public final static class SubHint extends SimpleRecyclerItem<SubHint.SubHintHolderView> {
        public final PreferenceItem.ValueProvider provider;

        public SubHint(PreferenceItem.ValueProvider title) {
            this.provider = title;
        }

        @Override
        public SubHintHolderView onCreateView(Context ctx) {
            return new SubHintHolderView(ctx);
        }

        @Override
        public void onBindView(SubHintHolderView view) {
            view.bind(this);
        }

        private final static class SubHintHolderView extends LinearLayout {
            TextView title;

            SubHintHolderView(Context context) {
                super(context);
                setOrientation(VERTICAL);

                addView(new DividerView(context, R.attr.dividerContrastColor), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1f)));

                title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
                title.setPadding(ViewUtils.dp(20), ViewUtils.dp(6), ViewUtils.dp(20), ViewUtils.dp(12));
                addView(title);
            }

            void bind(SubHint h) {
                title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
                title.setText(h.provider.provide());
            }
        }
    }

    private final static class OptionWrapper extends SimpleRecyclerItem<View> {
        int icon;
        String title;
        Runnable onClick;
        int color;
        OptionElement optionEl;
        boolean noTint;

        int categoryIndex;

        OptionWrapper(int icon, String t, Runnable onClick, int color, boolean noTint) {
            this.icon = icon;
            this.title = t;
            this.onClick = onClick;
            this.color = color;
            this.noTint = noTint;
        }

        OptionWrapper(OptionElement el) {
            optionEl = el;
        }

        @Override
        public View onCreateView(Context ctx) {
            FrameLayout v = new FrameLayout(ctx);
            v.setBackgroundColor(Color.GREEN);
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(32)) {{
                bottomMargin = ViewUtils.dp(16);
            }});
            return v;
        }
    }

    private final static class CategoryHolderView extends LinearLayout implements IThemeView {
        private ImageView icon;
        private TextView title;
        private ImageView dropdown;

        public CategoryHolderView(Context context) {
            super(context);

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(ViewUtils.dp(21), ViewUtils.dp(16), ViewUtils.dp(21), ViewUtils.dp(16));

            icon = new ImageView(context);
            addView(icon, new LayoutParams(ViewUtils.dp(26), ViewUtils.dp(26)));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{
                leftMargin = ViewUtils.dp(12);
            }});

            dropdown = new ImageView(context);
            dropdown.setImageResource(R.drawable.dropdown_24);
            addView(dropdown, new LayoutParams(ViewUtils.dp(24), ViewUtils.dp(24)));

            onApplyTheme();
        }

        @Override
        public void onApplyTheme() {
            int color = getTag() != null ? ThemesRepo.getColor((Integer) getTag()) : 0;
            if (icon.getTag() != null) {
                icon.setImageTintList(null);
            } else {
                icon.setImageTintList(ColorStateList.valueOf(color != 0 ? color : ThemesRepo.getColor(android.R.attr.textColorSecondary)));
            }
            title.setTextColor(color != 0 ? color : ThemesRepo.getColor(android.R.attr.textColorPrimary));
            dropdown.setImageTintList(ColorStateList.valueOf(color != 0 ? color : ThemesRepo.getColor(android.R.attr.textColorPrimary)));
            setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 32));
        }
    }
}
