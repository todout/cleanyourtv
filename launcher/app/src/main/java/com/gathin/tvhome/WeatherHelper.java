package com.gathin.tvhome;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WeatherHelper {
    private static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final double DEFAULT_LAT = -37.45859; // Coronel Suárez, Buenos Aires, Argentina (CP 7540)
    private static final double DEFAULT_LON = -61.93294;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static long lastFetchMs = 0L;

    private static boolean hasData = false;
    private static int cachedCurTemp, cachedHumidity, cachedCurCode, cachedTodayMax, cachedTodayMin;
    private static String[] cachedFcDays, cachedFcTemps;
    private static int[] cachedFcIcons;

    private WeatherHelper() {}

    static void refreshIfNeeded(Activity activity) {
        if (hasData) {
            applyToUi(activity);
        }
        long now = System.currentTimeMillis();
        if (now - lastFetchMs < REFRESH_MS) return;
        lastFetchMs = now;
        EXEC.execute(() -> {
            try {
                double lat = DEFAULT_LAT;
                double lon = DEFAULT_LON;
                String apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=temperature_2m,relative_humidity_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=4";
                JSONObject json = new JSONObject(httpGet(apiUrl));
                JSONObject current = json.getJSONObject("current");
                int curTemp = (int) Math.round(current.getDouble("temperature_2m"));
                int humidity = (int) Math.round(current.getDouble("relative_humidity_2m"));
                int curCode = current.getInt("weather_code");

                JSONObject daily = json.getJSONObject("daily");
                JSONArray times = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray maxes = daily.getJSONArray("temperature_2m_max");
                JSONArray mins = daily.getJSONArray("temperature_2m_min");

                int todayMax = (int) Math.round(maxes.getDouble(0));
                int todayMin = (int) Math.round(mins.getDouble(0));

                String[] fcDays = new String[3];
                String[] fcTemps = new String[3];
                int[] fcIcons = new int[3];
                SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat outFmt = new SimpleDateFormat("EE", new Locale("es", "ES"));

                for (int i = 0; i < 3; i++) {
                    int idx = i + 1;
                    Date d = inFmt.parse(times.getString(idx));
                    String day = d != null ? outFmt.format(d) : "";
                    if (day.length() > 2) day = day.substring(0, 2);
                    if (!day.isEmpty()) {
                        day = Character.toUpperCase(day.charAt(0)) + day.substring(1) + ".";
                    }
                    fcDays[i] = day;
                    int mx = (int) Math.round(maxes.getDouble(idx));
                    int mn = (int) Math.round(mins.getDouble(idx));
                    fcTemps[i] = mx + "°/" + mn + "°";
                    fcIcons[i] = iconForCode(codes.getInt(idx));
                }

                cachedCurTemp = curTemp;
                cachedHumidity = humidity;
                cachedCurCode = curCode;
                cachedTodayMax = todayMax;
                cachedTodayMin = todayMin;
                cachedFcDays = fcDays;
                cachedFcTemps = fcTemps;
                cachedFcIcons = fcIcons;
                hasData = true;

                activity.runOnUiThread(() -> applyToUi(activity));
            } catch (Exception ignored) {
                lastFetchMs = 0L;
            }
        });
    }

    private static void applyToUi(Activity activity) {
        if (!hasData || activity.isFinishing() || activity.isDestroyed()) return;
        View widget = activity.findViewById(R.id.weatherWidget);
        if (widget == null) return;
        ((TextView) activity.findViewById(R.id.weatherTemp)).setText(cachedCurTemp + "°");
        TextView minMaxView = activity.findViewById(R.id.weatherMinMax);
        String mm = "↑" + cachedTodayMax + "° ↓" + cachedTodayMin + "°";
        SpannableString span = new SpannableString(mm);
        span.setSpan(new ForegroundColorSpan(0xFFFF6B35), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int downIdx = mm.indexOf('↓');
        if (downIdx >= 0) {
            span.setSpan(new ForegroundColorSpan(0xFF38BDF8), downIdx, downIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        minMaxView.setText(span);
        ((TextView) activity.findViewById(R.id.weatherHumidity)).setText(cachedHumidity + "%");
        ((ImageView) activity.findViewById(R.id.weatherIcon)).setImageResource(iconForCode(cachedCurCode));

        int[] iconIds = {R.id.fcIcon1, R.id.fcIcon2, R.id.fcIcon3};
        int[] dayIds = {R.id.fcDay1, R.id.fcDay2, R.id.fcDay3};
        int[] tempIds = {R.id.fcTemp1, R.id.fcTemp2, R.id.fcTemp3};
        for (int i = 0; i < 3; i++) {
            ((ImageView) activity.findViewById(iconIds[i])).setImageResource(cachedFcIcons[i]);
            ((TextView) activity.findViewById(dayIds[i])).setText(cachedFcDays[i]);
            ((TextView) activity.findViewById(tempIds[i])).setText(cachedFcTemps[i]);
        }
        widget.setVisibility(View.VISIBLE);
    }

    private static int iconForCode(int code) {
        if (code == 0 || code == 1) return R.drawable.ic_weather_sun;
        if (code == 2) return R.drawable.ic_weather_partly;
        if (code == 3 || code == 45 || code == 48) return R.drawable.ic_weather_cloud;
        if (code >= 95) return R.drawable.ic_weather_storm;
        return R.drawable.ic_weather_rain;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
