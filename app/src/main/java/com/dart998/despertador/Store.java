package com.dart998.despertador;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes guardados: hora, si está activa, tarjeta registrada y si está vibrando. */
final class Store {
    private static final String FILE = "ajustes";

    private Store() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int hour(Context c) {
        return p(c).getInt("hora", 23);
    }

    static int minute(Context c) {
        return p(c).getInt("minuto", 0);
    }

    static void setTime(Context c, int h, int m) {
        p(c).edit().putInt("hora", h).putInt("minuto", m).apply();
    }

    static boolean enabled(Context c) {
        return p(c).getBoolean("activa", false);
    }

    static void setEnabled(Context c, boolean on) {
        p(c).edit().putBoolean("activa", on).apply();
    }

    static String tag(Context c) {
        return p(c).getString("tarjeta", null);
    }

    static void setTag(Context c, String id) {
        p(c).edit().putString("tarjeta", id).apply();
    }

    static boolean ringing(Context c) {
        return p(c).getBoolean("vibrando", false);
    }

    static void setRinging(Context c, boolean on) {
        // commit(): debe quedar escrito aunque el sistema mate el proceso justo después.
        p(c).edit().putBoolean("vibrando", on).commit();
    }
}
