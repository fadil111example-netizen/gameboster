package com.gamebooster.app;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dialog "Pilih Game" bergaya glass: ikon asli, pencarian, dan filter khusus game. */
public class GamePickerDialog {

    public interface OnSave {
        void onSave(Set<String> selected);
    }

    private final Activity activity;
    private final List<AppEntry> all;
    private final List<AppEntry> shown = new ArrayList<>();
    private final Set<String> selected;
    private final OnSave callback;

    private AppCompatDialog dialog;
    private TextView tvCount, tvEmpty, chipGames, btnClear;
    private EditText etSearch;
    private ListView listView;
    private PickAdapter adapter;
    private boolean gamesOnly = false;
    private String title = null;

    public GamePickerDialog(Activity activity, List<AppEntry> all, Set<String> initial, OnSave callback) {
        this.activity = activity;
        this.all = all;
        this.selected = new HashSet<>(initial);
        this.callback = callback;
    }

    public GamePickerDialog setTitle(String t) {
        this.title = t;
        return this;
    }

    public void show() {
        dialog = new AppCompatDialog(activity, R.style.GlassDialog);
        dialog.setContentView(R.layout.dialog_pick);

        tvCount = dialog.findViewById(R.id.pickCount);
        tvEmpty = dialog.findViewById(R.id.pickEmpty);
        chipGames = dialog.findViewById(R.id.pickGamesOnly);
        btnClear = dialog.findViewById(R.id.pickClear);
        etSearch = dialog.findViewById(R.id.pickSearch);
        listView = dialog.findViewById(R.id.pickList);
        TextView btnCancel = dialog.findViewById(R.id.pickCancel);
        TextView btnSave = dialog.findViewById(R.id.pickSave);
        TextView tvTitle = dialog.findViewById(R.id.pickTitle);
        if (tvTitle != null && title != null) tvTitle.setText(title);

        adapter = new PickAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppEntry e = shown.get(position);
                if (!selected.add(e.pkg)) selected.remove(e.pkg);
                CheckBox cb = view.findViewById(R.id.pickCheck);
                cb.setChecked(selected.contains(e.pkg));
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    }
                }).start();
                updateCount();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSearch.setText("");
            }
        });

        chipGames.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gamesOnly = !gamesOnly;
                chipGames.setText(gamesOnly ? "🎮  Hanya Game: AKTIF" : "🎮  Hanya Game: MATI");
                chipGames.setTextColor(gamesOnly ? 0xFF22D3EE : 0xFFC7D2FE);
                applyFilter();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (callback != null) callback.onSave(new HashSet<>(selected));
            }
        });

        Anim.press(btnCancel, btnSave, chipGames);

        applyFilter();
        updateCount();

        Window w = dialog.getWindow();
        dialog.show();
        if (w != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            w.setLayout((int) (dm.widthPixels * 0.92f), (int) (dm.heightPixels * 0.84f));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppEntry e : all) {
            if (gamesOnly && !e.isGame) continue;
            if (q.length() > 0 && !e.label.toLowerCase(Locale.getDefault()).contains(q)
                    && !e.pkg.toLowerCase(Locale.US).contains(q)) continue;
            shown.add(e);
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateCount() {
        tvCount.setText(selected.size() + " dipilih • " + all.size() + " aplikasi");
    }

    private class PickAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(activity).inflate(R.layout.item_pick, parent, false);
            }
            AppEntry e = shown.get(position);
            ((ImageView) v.findViewById(R.id.pickIcon)).setImageDrawable(e.icon);
            ((TextView) v.findViewById(R.id.pickName)).setText(e.label);
            ((TextView) v.findViewById(R.id.pickPkg)).setText(e.pkg);
            ((CheckBox) v.findViewById(R.id.pickCheck)).setChecked(selected.contains(e.pkg));
            return v;
        }
    }
}
