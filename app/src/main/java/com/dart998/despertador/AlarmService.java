package com.dart998.despertador;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Vibra sin parar (sin sonido) hasta que MainActivity lee la tarjeta registrada. */
public class AlarmService extends Service {

    private static final String CHANNEL = "alarma";
    private static final int NOTIF_ID = 1;

    private boolean started;
    private Vibrator vibrator;
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;

    static void start(Context c) {
        Store.setRinging(c, true);
        c.startForegroundService(new Intent(c, AlarmService.class));
    }

    static void stop(Context c) {
        Store.setRinging(c, false);
        c.stopService(new Intent(c, AlarmService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (!Store.ringing(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "despertador:alarma");
            wakeLock.acquire();
        }
        if (!started) {
            started = true;
            // Uso "alarma": suena y vibra también con el teléfono en silencio o
            // con la pantalla apagada.
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            boolean soundOk = Store.sound(this) && startSound(attrs);
            // Si el sonido falla, vibra de todos modos para que la alarma no quede muda.
            if (Store.vibrate(this) || !soundOk) {
                vibrator = getSystemService(Vibrator.class);
                // 0,8 s vibrando y 0,4 s de pausa, en bucle.
                vibrator.vibrate(
                        VibrationEffect.createWaveform(new long[]{0, 800, 400}, 0), attrs);
            }
        }
        // Si el sistema mata el servicio, lo vuelve a arrancar y sigue vibrando.
        return START_STICKY;
    }

    /** Reproduce en bucle el tono de alarma del teléfono. */
    private boolean startSound(AudioAttributes attrs) {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            player = mp;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.canal),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(Store.message(this))
                .setContentText(getString(R.string.notif_texto))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
