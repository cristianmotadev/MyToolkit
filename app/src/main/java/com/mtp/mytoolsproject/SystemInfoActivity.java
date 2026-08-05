package com.mtp.mytoolsproject;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Mostra informações detalhadas do aparelho: modelo, Android, CPU, RAM,
 * armazenamento, bateria, kernel e sensores disponíveis.
 */
public class SystemInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_info);

        TextView txtDispositivo = findViewById(R.id.txtInfoDispositivo);
        TextView txtCpu = findViewById(R.id.txtInfoCpu);
        TextView txtMemoria = findViewById(R.id.txtInfoMemoria);
        TextView txtArmazenamento = findViewById(R.id.txtInfoArmazenamento);
        TextView txtBateria = findViewById(R.id.txtInfoBateria);
        TextView txtKernel = findViewById(R.id.txtInfoKernel);
        TextView txtSensores = findViewById(R.id.txtInfoSensores);

        txtDispositivo.setText(
                "📱 Modelo: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "🤖 Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "🏷️ Marca: " + Build.BRAND + "\n" +
                "🔖 Build: " + Build.DISPLAY
        );

        txtCpu.setText(
                "⚙️ Núcleos disponíveis: " + Runtime.getRuntime().availableProcessors() + "\n" +
                "🧩 Arquitetura(s): " + String.join(", ", Build.SUPPORTED_ABIS)
        );

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(memInfo);
        txtMemoria.setText(
                "💾 RAM total: " + formatarBytes(memInfo.totalMem) + "\n" +
                "💾 RAM disponível: " + formatarBytes(memInfo.availMem) + "\n" +
                "⚠️ Memória baixa: " + (memInfo.lowMemory ? "Sim" : "Não")
        );

        StatFs statFsInterno = new StatFs(getFilesDir().getAbsolutePath());
        long totalInterno = statFsInterno.getBlockCountLong() * statFsInterno.getBlockSizeLong();
        long livreInterno = statFsInterno.getAvailableBlocksLong() * statFsInterno.getBlockSizeLong();
        txtArmazenamento.setText(
                "💽 Armazenamento total: " + formatarBytes(totalInterno) + "\n" +
                "💽 Espaço livre: " + formatarBytes(livreInterno)
        );

        Intent bateriaIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bateriaIntent != null) {
            int nivel = bateriaIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int escala = bateriaIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int temperatura = bateriaIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int status = bateriaIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean carregando = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            int percentual = (nivel >= 0 && escala > 0) ? Math.round(100f * nivel / escala) : -1;

            txtBateria.setText(
                    "🔋 Nível: " + (percentual >= 0 ? percentual + "%" : "desconhecido") + "\n" +
                    "🌡️ Temperatura: " + (temperatura >= 0 ? (temperatura / 10.0) + "°C" : "desconhecida") + "\n" +
                    "🔌 Carregando: " + (carregando ? "Sim" : "Não")
            );
        } else {
            txtBateria.setText("🔋 Não foi possível ler o status da bateria.");
        }

        txtKernel.setText("🐧 Kernel: " + lerKernelViaRoot());

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensores = sensorManager != null ? sensorManager.getSensorList(Sensor.TYPE_ALL) : null;
        StringBuilder listaSensores = new StringBuilder("🧭 Sensores disponíveis (" + (sensores != null ? sensores.size() : 0) + "):\n");
        if (sensores != null) {
            for (Sensor s : sensores) {
                listaSensores.append("• ").append(s.getName()).append("\n");
            }
        }
        txtSensores.setText(listaSensores.toString());
    }

    private String lerKernelViaRoot() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /proc/version"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha = reader.readLine();
            process.waitFor();
            return linha != null ? linha : System.getProperty("os.version");
        } catch (Exception e) {
            return System.getProperty("os.version", "desconhecido");
        }
    }

    private String formatarBytes(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1) return String.format("%.2f GB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        return String.format("%.0f MB", mb);
    }
}
