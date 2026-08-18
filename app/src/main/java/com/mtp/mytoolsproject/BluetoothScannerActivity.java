package com.mtp.mytoolsproject;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Radar Bluetooth: combina descoberta clássica (BluetoothAdapter.startDiscovery)
 * com varredura BLE (BluetoothLeScanner) pra achar o máximo de dispositivos
 * próximos possível, com força de sinal — mesma lógica visual do Radar Wi-Fi.
 */
public class BluetoothScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSOES_BT = 401;
    private static final long DURACAO_SCAN_BLE_MS = 10000;

    private LinearLayout container;
    private Button btnEscanear;
    private TextView txtStatus;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner leScanner;

    /** Chave = endereço MAC, evita duplicar o mesmo dispositivo achado pelos dois métodos. */
    private final Map<String, DispositivoBt> dispositivosEncontrados = new LinkedHashMap<>();

    private static class DispositivoBt {
        String nome;
        String endereco;
        int rssi;
        boolean pareado;
        String tipo; // "Clássico", "BLE" ou "Clássico + BLE"
    }

    private BroadcastReceiver receiverClassico;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_scanner);

        container = findViewById(R.id.containerBluetooth);
        btnEscanear = findViewById(R.id.btnEscanearBluetooth);
        txtStatus = findViewById(R.id.txtStatusBluetooth);

        android.bluetooth.BluetoothManager bm = (android.bluetooth.BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm != null ? bm.getAdapter() : null;

        if (bluetoothAdapter == null) {
            txtStatus.setText("❌ Este aparelho não possui Bluetooth.");
            btnEscanear.setEnabled(false);
            return;
        }

        receiverClassico = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    if (device != null) registrarDispositivo(device, rssi, "Clássico");
                }
            }
        };

        btnEscanear.setOnClickListener(v -> iniciarVarredura());
    }

    private boolean temPermissoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermissoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
            }, REQUEST_CODE_PERMISSOES_BT);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_CODE_PERMISSOES_BT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSOES_BT && temPermissoes()) {
            iniciarVarredura();
        } else if (requestCode == REQUEST_CODE_PERMISSOES_BT) {
            Toast.makeText(this, "Permissão necessária para escanear Bluetooth.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("MissingPermission") // permissões conferidas manualmente em temPermissoes()
    private void iniciarVarredura() {
        if (!temPermissoes()) {
            solicitarPermissoes();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Ative o Bluetooth para escanear.", Toast.LENGTH_SHORT).show();
            return;
        }

        container.removeAllViews();
        dispositivosEncontrados.clear();
        btnEscanear.setEnabled(false);
        txtStatus.setText("🔍 Escaneando (clássico + BLE) por 10 segundos...");

        // Dispositivos já pareados aparecem na hora, sem precisar descobrir
        for (BluetoothDevice pareado : bluetoothAdapter.getBondedDevices()) {
            registrarDispositivo(pareado, Short.MIN_VALUE, "Pareado");
        }

        registerReceiver(receiverClassico, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        bluetoothAdapter.startDiscovery();

        leScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                registrarDispositivo(result.getDevice(), result.getRssi(), "BLE");
            }
        };
        if (leScanner != null) leScanner.startScan(scanCallback);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { bluetoothAdapter.cancelDiscovery(); } catch (Exception ignored) {}
            try { if (leScanner != null) leScanner.stopScan(scanCallback); } catch (Exception ignored) {}
            try { unregisterReceiver(receiverClassico); } catch (Exception ignored) {}

            btnEscanear.setEnabled(true);
            txtStatus.setText("✅ Varredura concluída: " + dispositivosEncontrados.size() + " dispositivo(s) encontrado(s).");
            renderizarResultados();
        }, DURACAO_SCAN_BLE_MS);
    }

    @SuppressWarnings("MissingPermission")
    private void registrarDispositivo(BluetoothDevice device, int rssi, String origem) {
        String endereco = device.getAddress();
        if (endereco == null) return;

        DispositivoBt d = dispositivosEncontrados.get(endereco);
        if (d == null) {
            d = new DispositivoBt();
            d.endereco = endereco;
            d.tipo = origem;
            dispositivosEncontrados.put(endereco, d);
        } else if (!d.tipo.contains(origem)) {
            d.tipo = d.tipo + " + " + origem;
        }

        String nome = null;
        try { nome = device.getName(); } catch (SecurityException ignored) {}
        d.nome = (nome != null && !nome.isEmpty()) ? nome : "Dispositivo sem nome";
        d.pareado = device.getBondState() == BluetoothDevice.BOND_BONDED;
        if (rssi != Short.MIN_VALUE) d.rssi = rssi;
    }

    private void renderizarResultados() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (dispositivosEncontrados.isEmpty()) {
            TextView vazio = new TextView(this);
            vazio.setText("Nenhum dispositivo Bluetooth encontrado por perto.");
            vazio.setTextColor(0xFFAAAAAA);
            container.addView(vazio);
            return;
        }

        for (DispositivoBt d : dispositivosEncontrados.values()) {
            View card = inflater.inflate(R.layout.item_bluetooth_device, container, false);
            TextView txtNome = card.findViewById(R.id.txtBtNome);
            TextView txtEndereco = card.findViewById(R.id.txtBtEndereco);
            TextView txtTipo = card.findViewById(R.id.txtBtTipo);
            TextView txtSinal = card.findViewById(R.id.txtBtSinal);

            txtNome.setText((d.pareado ? "🔗 " : "📡 ") + d.nome);
            txtEndereco.setText("Endereço: " + d.endereco);
            txtTipo.setText("Tipo: " + d.tipo + (d.pareado ? " (pareado)" : ""));
            txtSinal.setText(d.rssi != 0 ? "📶 Sinal: " + d.rssi + " dBm" : "📶 Sinal: não medido");

            container.addView(card);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothAdapter != null && temPermissoes()) bluetoothAdapter.cancelDiscovery();
        } catch (Exception ignored) {}
    }
}
