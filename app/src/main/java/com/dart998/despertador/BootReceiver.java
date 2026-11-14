package com.dart998.despertador;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Tras reiniciar el teléfono, actualizar la app o cambiar la hora del sistema:
 * vuelve a programar la alarma y, si estaba vibrando sin haberse parado con la
 * tarjeta, sigue vibrando.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.apply(context);
        if (Store.ringing(context)) {
            try {
                AlarmService.start(context);
            } catch (RuntimeException ignored) {
                // El sistema no permite arrancar el servicio en este momento.
            }
        }
    }
}
