package com.dart998.despertador;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Programa (o cancela) la próxima alarma en el sistema. */
final class Scheduler {

    private Scheduler() {
    }

    /** Próxima vez que toca la hora configurada, en milisegundos. */
    static long nextTrigger(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Store.hour(c));
        cal.set(Calendar.MINUTE, Store.minute(c));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    static void apply(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent fire = PendingIntent.getBroadcast(c, 0,
                new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!Store.enabled(c) || Store.tag(c) == null) {
            am.cancel(fire);
            return;
        }

        long when = nextTrigger(c);
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || am.canScheduleExactAlarms();
        if (exact) {
            PendingIntent show = PendingIntent.getActivity(c, 1,
                    new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), fire);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, fire);
        }
    }
}
