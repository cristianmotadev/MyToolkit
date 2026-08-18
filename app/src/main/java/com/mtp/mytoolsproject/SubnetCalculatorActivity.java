package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SubnetCalculatorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subnet_calculator);

        EditText editEntrada = findViewById(R.id.editSubnetEntrada);
        Button btnCalcular = findViewById(R.id.btnCalcularSubnet);
        TextView txtResultado = findViewById(R.id.txtSubnetResultado);

        editEntrada.setText("192.168.1.10/24");

        btnCalcular.setOnClickListener(v -> {
            try {
                SubnetUtils.ResultadoSubnet r = SubnetUtils.calcular(editEntrada.getText().toString());
                StringBuilder sb = new StringBuilder();
                sb.append("📶 Máscara: ").append(r.mascara).append(" (/").append(r.prefixo).append(")\n\n");
                sb.append("🏠 Endereço de rede: ").append(r.enderecoRede).append("\n\n");
                sb.append("📡 Broadcast: ").append(r.enderecoBroadcast).append("\n\n");
                sb.append("▶️ Primeiro host utilizável: ").append(r.primeiroHost).append("\n\n");
                sb.append("⏹️ Último host utilizável: ").append(r.ultimoHost).append("\n\n");
                sb.append("🔢 Total de hosts utilizáveis: ").append(r.totalHostsUtilizaveis);
                txtResultado.setText(sb.toString());
                txtResultado.setTextColor(0xFFFFFFFF);
            } catch (Exception e) {
                txtResultado.setText("❌ " + e.getMessage());
                txtResultado.setTextColor(0xFFFF5252);
            }
        });
    }
}
