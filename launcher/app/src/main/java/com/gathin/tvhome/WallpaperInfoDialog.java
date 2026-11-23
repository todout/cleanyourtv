package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WallpaperInfoDialog {
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Dialog activeDialog;

    private WallpaperInfoDialog() {}

    static void dismissCurrent() {
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        activeDialog = null;
    }

    static void show(Context context, JSONObject meta, Runnable onPlayTrailer) {
        if (meta == null) return;
        dismissCurrent();
        float dp = context.getResources().getDisplayMetrics().density;

        Dialog dialog = new Dialog(context);
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Math.round(24 * dp);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB101522);
        bg.setCornerRadius(18 * dp);
        bg.setStroke(Math.round(1 * dp), 0x33FFFFFF);
        card.setBackground(bg);

        String posterUrl = meta.optString("poster_url", "");
        if (!posterUrl.isEmpty()) {
            ImageView poster = new ImageView(context);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    Math.round(170 * dp), Math.round(255 * dp));
            plp.setMarginEnd(Math.round(22 * dp));
            poster.setLayoutParams(plp);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0x22FFFFFF);
            card.addView(poster);
            loadImage(posterUrl, poster);
        }

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                Math.round(460 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        scroll.setLayoutParams(slp);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);

        String titleEs = meta.optString("title", "");
        String titleEn = meta.optString("title_en", "");

        TextView titleView = new TextView(context);
        titleView.setText(titleEs);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setTypeface(UiFont.get(context, R.string.font_medium));
        info.addView(titleView);

        if (!titleEn.isEmpty() && !titleEn.equalsIgnoreCase(titleEs)) {
            TextView enView = new TextView(context);
            enView.setText("Título en inglés / original: " + titleEn);
            enView.setTextColor(0xB3FFFFFF);
            enView.setTextSize(13);
            enView.setPadding(0, Math.round(2 * dp), 0, 0);
            info.addView(enView);
        }

        List<String> parts = new ArrayList<>();
        String mtype = meta.optString("media_type", "");
        String year = meta.optString("year", "");
        String dur = meta.optString("duration", "");
        String country = meta.optString("country", "");
        String rating = meta.optString("rating", "");
        if (!mtype.isEmpty()) parts.add(mtype);
        if (!year.isEmpty()) parts.add(year);
        if (!dur.isEmpty()) parts.add(dur);
        if (!country.isEmpty()) parts.add(country);
        if (!rating.isEmpty()) parts.add("★ " + rating);

        if (!parts.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(TextUtils.join("  •  ", parts));
            subView.setTextColor(0x99FFFFFF);
            subView.setTextSize(13);
            subView.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
            info.addView(subView);
        }

        addField(context, info, "Dirección", meta.optString("director", ""), dp);

        JSONArray castArr = meta.optJSONArray("cast");
        if (castArr != null && castArr.length() > 0) {
            List<String> actors = new ArrayList<>();
            for (int i = 0; i < castArr.length(); i++) {
                String a = castArr.optString(i, "");
                if (!a.isEmpty()) actors.add(a);
            }
            addField(context, info, "Reparto", TextUtils.join(", ", actors), dp);
        }

        addField(context, info, "Premios", meta.optString("awards", ""), dp);

        String overview = meta.optString("overview", "");
        if (!overview.isEmpty()) {
            TextView ovView = new TextView(context);
            ovView.setText(overview);
            ovView.setTextColor(0xCCFFFFFF);
            ovView.setTextSize(13);
            ovView.setLineSpacing(2 * dp, 1f);
            ovView.setPadding(0, Math.round(8 * dp), 0, Math.round(4 * dp));
            info.addView(ovView);
        }

        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.BOTTOM);
        bottomBar.setClipChildren(false);
        bottomBar.setClipToPadding(false);
        bottomBar.setPadding(0, Math.round(10 * dp), Math.round(4 * dp), 0);

        LinearLayout provCol = new LinearLayout(context);
        provCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        provCol.setLayoutParams(pclp);

        JSONArray provArr = meta.optJSONArray("providers");
        TextView provLabel = new TextView(context);
        provLabel.setText("Disponible en Argentina");
        provLabel.setTextColor(0x99FFFFFF);
        provLabel.setTextSize(12);
        provLabel.setPadding(0, 0, 0, Math.round(6 * dp));
        provCol.addView(provLabel);

        if (provArr != null && provArr.length() > 0) {
            LinearLayout provRow = new LinearLayout(context);
            provRow.setOrientation(LinearLayout.HORIZONTAL);
            provRow.setGravity(Gravity.CENTER_VERTICAL);
            int count = Math.min(provArr.length(), 4);
            for (int i = 0; i < count; i++) {
                JSONObject p = provArr.optJSONObject(i);
                if (p == null) continue;
                String pName = p.optString("name", "");
                String pLogo = p.optString("logo_url", "");

                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ilp.setMarginEnd(Math.round(12 * dp));
                item.setLayoutParams(ilp);

                if (!pLogo.isEmpty()) {
                    ImageView iv = new ImageView(context);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(
                            Math.round(38 * dp), Math.round(38 * dp)));
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    item.addView(iv);
                    loadImage(pLogo, iv);
                }

                TextView tv = new TextView(context);
                tv.setText(pName);
                tv.setTextColor(0xCCFFFFFF);
                tv.setTextSize(10);
                tv.setMaxWidth(Math.round(64 * dp));
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, Math.round(3 * dp), 0, 0);
                item.addView(tv);

                provRow.addView(item);
            }
            provCol.addView(provRow);
        } else {
            TextView none = new TextView(context);
            none.setText("Sin plataformas informadas en AR");
            none.setTextColor(0x80FFFFFF);
            none.setTextSize(12);
            provCol.addView(none);
        }

        bottomBar.addView(provCol);

        LinearLayout btnGroup = new LinearLayout(context);
        btnGroup.setOrientation(LinearLayout.HORIZONTAL);
        btnGroup.setGravity(Gravity.END | Gravity.BOTTOM);
        btnGroup.setClipChildren(false);
        btnGroup.setClipToPadding(false);
        LinearLayout.LayoutParams bglp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bglp.setMarginStart(Math.round(12 * dp));
        btnGroup.setLayoutParams(bglp);

        String ytId = meta.optString("youtube_id", "");
        TextView trailerBtn = null;
        if (!ytId.isEmpty() && onPlayTrailer != null) {
            trailerBtn = makeActionPill(context, "Ver tráiler", R.drawable.ic_play, dp, () -> {
                dismissCurrent();
                onPlayTrailer.run();
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginEnd(Math.round(8 * dp));
            trailerBtn.setLayoutParams(tlp);
            btnGroup.addView(trailerBtn);
        }

        TextView closeBtn = makeActionPill(context, "Cerrar", R.drawable.ic_close, dp, WallpaperInfoDialog::dismissCurrent);
        btnGroup.addView(closeBtn);

        bottomBar.addView(btnGroup);
        if (trailerBtn != null) {
            trailerBtn.requestFocus();
        } else {
            closeBtn.requestFocus();
        }

        info.setClipChildren(false);
        info.setClipToPadding(false);
        info.addView(bottomBar);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(info);

        card.addView(scroll);

        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private static TextView makeActionPill(Context context, String label, int iconRes, float dp, Runnable onClick) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setTypeface(UiFont.get(context, R.string.font_medium));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        btn.setCompoundDrawablePadding(Math.round(6 * dp));
        int hPad = Math.round(12 * dp);
        int vPad = Math.round(6 * dp);
        btn.setPadding(hPad, vPad, hPad, vPad);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(0x26FFFFFF);
        normalBg.setCornerRadius(18 * dp);
        normalBg.setStroke(Math.round(1 * dp), 0x40FFFFFF);

        GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(0x4DFFFFFF);
        focusBg.setCornerRadius(18 * dp);
        focusBg.setStroke(Math.round(2 * dp), Color.WHITE);

        btn.setBackground(normalBg);
        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focusBg : normalBg);
            v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(120).start();
        });
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private static void addField(Context ctx, LinearLayout parent, String label, String value, float dp) {
        if (value == null || value.trim().isEmpty()) return;
        TextView tv = new TextView(ctx);
        tv.setText(label + ": " + value);
        tv.setTextColor(0xE6FFFFFF);
        tv.setTextSize(13);
        tv.setPadding(0, Math.round(3 * dp), 0, Math.round(3 * dp));
        parent.addView(tv);
    }

    private static void loadImage(String urlStr, ImageView target) {
        Bitmap cached = CACHE.get(urlStr);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    if (bmp != null) {
                        CACHE.put(urlStr, bmp);
                        MAIN.post(() -> target.setImageBitmap(bmp));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
