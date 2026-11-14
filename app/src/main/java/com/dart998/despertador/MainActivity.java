package com.dart998.despertador;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfc;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 1000);
        }
    };

    private boolean registering;
    private boolean lockScreenMode;

    private View normal;
    private View sonando;
    private TextView hora;
    private TextView estado;
    private Switch activa;
    private TextView tarjetaEstado;
    private TextView registrar;
    private TextView avisoNfc;
    private TextView sonandoHora;
    private TextView sonandoTexto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        normal = findViewById(R.id.normal);
        sonando = findViewById(R.id.sonando);
        hora = findViewById(R.id.hora);
        estado = findViewById(R.id.estado);
        activa = findViewById(R.id.activa);
        tarjetaEstado = findViewById(R.id.tarjeta_estado);
        registrar = findViewById(R.id.registrar);
        avisoNfc = findViewById(R.id.aviso_nfc);
        sonandoHora = findViewById(R.id.sonando_hora);
        sonandoTexto = findViewById(R.id.sonando_texto);

        nfc = NfcAdapter.getDefaultAdapter(this);

        hora.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            Store.setTime(this, h, m);
            Scheduler.apply(this);
            render();
        }, Store.hour(this), Store.minute(this), true).show());

        activa.setOnClickListener(v -> {
            boolean on = activa.isChecked();
            if (on && Store.tag(this) == null) {
                Toast.makeText(this, R.string.falta_tarjeta, Toast.LENGTH_LONG).show();
                on = false;
            }
            Store.setEnabled(this, on);
            Scheduler.apply(this);
            render();
        });

        registrar.setOnClickListener(v -> {
            registering = !registering;
            render();
        });

        avisoNfc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        Scheduler.apply(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null) {
            nfc.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (nfc != null) {
            nfc.disableReaderMode(this);
        }
    }

    /** El sistema ha leído una tarjeta mientras la app está en pantalla. */
    @Override
    public void onTagDiscovered(Tag tag) {
        final byte[] id = tag.getId();
        runOnUiThread(() -> handleTag(id));
    }

    private void handleTag(byte[] id) {
        if (id == null || id.length == 0) {
            return;
        }
        String hex = toHex(id);

        if (Store.ringing(this)) {
            if (hex.equals(Store.tag(this))) {
                AlarmService.stop(this);
                Toast.makeText(this, R.string.alarma_parada, Toast.LENGTH_SHORT).show();
            } else {
                sonandoTexto.setText(R.string.tarjeta_incorrecta);
                handler.postDelayed(() -> sonandoTexto.setText(R.string.sonando_texto), 2500);
            }
            render();
            return;
        }

        if (registering) {
            // Algunas tarjetas (y los móviles) dan un identificador distinto en
            // cada lectura: empiezan por 08 y tienen 4 bytes. No sirven.
            if (id.length == 4 && id[0] == 0x08) {
                Toast.makeText(this, R.string.tarjeta_aleatoria, Toast.LENGTH_LONG).show();
                return;
            }
            Store.setTag(this, hex);
            registering = false;
            Scheduler.apply(this);
            Toast.makeText(this, R.string.tarjeta_guardada, Toast.LENGTH_SHORT).show();
            render();
        }
    }

    private void render() {
        boolean ringing = Store.ringing(this);
        setLockScreenMode(ringing);

        String time = String.format(Locale.ROOT, "%02d:%02d",
                Store.hour(this), Store.minute(this));

        sonando.setVisibility(ringing ? View.VISIBLE : View.GONE);
        normal.setVisibility(ringing ? View.GONE : View.VISIBLE);
        if (ringing) {
            registering = false;
            sonandoHora.setText(time);
            return;
        }

        hora.setText(time);

        String tag = Store.tag(this);
        boolean on = Store.enabled(this) && tag != null;
        activa.setChecked(on);
        hora.setAlpha(on ? 1f : 0.45f);
        estado.setText(on
                ? getString(R.string.suena_en, remaining())
                : getString(R.string.desactivada));

        if (registering) {
            tarjetaEstado.setText(R.string.acerca_registro);
            registrar.setText(R.string.cancelar);
        } else if (tag == null) {
            tarjetaEstado.setText(R.string.sin_tarjeta);
            registrar.setText(R.string.registrar);
        } else {
            tarjetaEstado.setText(getString(R.string.tarjeta_ok, tag));
            registrar.setText(R.string.cambiar);
        }

        if (nfc == null) {
            avisoNfc.setText(R.string.sin_nfc);
            avisoNfc.setVisibility(View.VISIBLE);
        } else {
            avisoNfc.setVisibility(nfc.isEnabled() ? View.GONE : View.VISIBLE);
        }
    }

    /** Mientras vibra: se muestra sobre la pantalla de bloqueo y la mantiene encendida. */
    private void setLockScreenMode(boolean on) {
        if (on == lockScreenMode) {
            return;
        }
        lockScreenMode = on;
        setShowWhenLocked(on);
        setTurnScreenOn(on);
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // El NFC solo lee con el teléfono desbloqueado: pide el desbloqueo.
            getSystemService(KeyguardManager.class).requestDismissKeyguard(this, null);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private String remaining() {
        long mins = (Scheduler.nextTrigger(this) - System.currentTimeMillis() + 59_999) / 60_000;
        long h = mins / 60;
        long m = mins % 60;
        if (h == 0) {
            return m + " min";
        }
        return h + " h " + m + " min";
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%02X", bytes[i]));
        }
        return sb.toString();
    }
}
