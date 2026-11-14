package com.dart998.despertador;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Llega la hora: empieza a vibrar y deja programada la alarma del día siguiente. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Store.enabled(context) && Store.tag(context) != null) {
            AlarmService.start(context);
        }
        Scheduler.apply(context);
    }
}
