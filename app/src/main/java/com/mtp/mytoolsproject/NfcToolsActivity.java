package com.mtp.mytoolsproject;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ferramentas NFC: leitura e escrita de tags NDEF, além de dump completo e
 * clonagem de tags MIFARE Classic (e MIFARE Plus em modo compatível SL1)
 * usando um dicionário de chaves padrão conhecidas publicamente.
 *
 * IMPORTANTE (uso responsável): a clonagem só destrava setores que ainda
 * usam chave de fábrica/padrão. Tags com chave customizada permanecem
 * bloqueadas — isso é o comportamento correto, não uma falha do app.
 * Use apenas em tags próprias ou com autorização explícita do proprietário.
 */
public class NfcToolsActivity extends AppCompatActivity {

    private enum Modo { NENHUM, LER, ESCREVER, DUMP, CLONAR, LER_CARTAO }

    private NfcAdapter nfcAdapter;
    private volatile Modo modoAtual = Modo.NENHUM;

    private EditText editTexto;
    private TextView txtLog;
    private TextView txtStatus;

    /** Guarda o último dump feito em memória, usado como origem na hora de clonar. */
    private JSONObject ultimoDump;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_tools);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        editTexto = findViewById(R.id.editNfcTexto);
        txtLog = findViewById(R.id.txtNfcLog);
        txtStatus = findViewById(R.id.txtNfcStatus);
        txtLog.setMovementMethod(new ScrollingMovementMethod());

        Button btnLer = findViewById(R.id.btnNfcLer);
        Button btnEscrever = findViewById(R.id.btnNfcEscrever);
        Button btnDump = findViewById(R.id.btnNfcDump);
        Button btnClonar = findViewById(R.id.btnNfcClonar);
        Button btnCartao = findViewById(R.id.btnNfcCartao);

        if (nfcAdapter == null) {
            txtStatus.setText("❌ Este aparelho não possui NFC.");
            btnLer.setEnabled(false);
            btnEscrever.setEnabled(false);
            btnDump.setEnabled(false);
            btnClonar.setEnabled(false);
            btnCartao.setEnabled(false);
            return;
        }

        btnLer.setOnClickListener(v -> ativarModo(Modo.LER, "Aproxime a tag para LER o conteúdo NDEF..."));

        btnEscrever.setOnClickListener(v -> {
            String texto = editTexto.getText().toString();
            if (texto.trim().isEmpty()) {
                Toast.makeText(this, "Digite um texto antes de escrever.", Toast.LENGTH_SHORT).show();
                return;
            }
            ativarModo(Modo.ESCREVER, "Aproxime a tag para ESCREVER o texto...");
        });

        btnDump.setOnClickListener(v -> ativarModo(Modo.DUMP, "Aproxime a tag de ORIGEM para o dump completo (MIFARE Classic)..."));

        btnClonar.setOnClickListener(v -> {
            if (ultimoDump == null) {
                Toast.makeText(this, "Faça um dump completo primeiro (na tag de origem).", Toast.LENGTH_LONG).show();
                return;
            }
            ativarModo(Modo.CLONAR, "Aproxime a tag de DESTINO (em branco ou com chave padrão) para clonar...");
        });

        btnCartao.setOnClickListener(v -> ativarModo(Modo.LER_CARTAO, "Aproxime o cartão de crédito/débito (contactless) para ler os dados públicos..."));
    }

    private void ativarModo(Modo modo, String instrucao) {
        modoAtual = modo;
        txtStatus.setText("👉 " + instrucao);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter == null) return;

        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;

        if (modoAtual == Modo.NENHUM) {
            log("ℹ️ Tag detectada (ID: " + NfcUtils.bytesParaHex(tag.getId()) + "), mas nenhuma ação foi selecionada. Toque em um dos botões acima antes de aproximar a tag.");
            return;
        }

        final Modo modoNoMomento = modoAtual;
        final String textoParaEscrever = editTexto.getText().toString();
        modoAtual = Modo.NENHUM; // evita reprocessar a mesma tag em loop
        runOnUiThread(() -> txtStatus.setText("⏳ Processando..."));

        // Toda operação NFC é bloqueante (transceive de baixo nível) — sempre fora da UI thread.
        new Thread(() -> {
            switch (modoNoMomento) {
                case LER:
                    lerNdef(tag);
                    break;
                case ESCREVER:
                    escreverNdef(tag, textoParaEscrever);
                    break;
                case DUMP:
                    dumpMifareClassic(tag);
                    break;
                case CLONAR:
                    clonarParaTag(tag);
                    break;
                case LER_CARTAO:
                    lerCartaoEmv(tag);
                    break;
                default:
                    break;
            }
            runOnUiThread(() -> txtStatus.setText("Pronto. Escolha uma ação e aproxime a tag."));
        }).start();
    }

    // ---------------------------------------------------------------
    // Modo LER (NDEF)
    // ---------------------------------------------------------------

    private void lerNdef(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            log("❌ Esta tag não está formatada como NDEF (pode ser uma MIFARE Classic sem formatação — use o Dump Completo para essas).");
            return;
        }
        try {
            ndef.connect();
            NdefMessage mensagem = ndef.getNdefMessage();
            if (mensagem == null) {
                log("ℹ️ Tag NDEF detectada, porém vazia.");
                return;
            }
            NdefRecord[] registros = mensagem.getRecords();
            if (registros.length == 0) {
                log("ℹ️ Nenhum registro encontrado na tag.");
            }
            for (NdefRecord registro : registros) {
                log("📄 Conteúdo lido: " + decodificarTextRecord(registro));
            }
        } catch (Exception e) {
            log("❌ Erro ao ler a tag: " + e.getMessage());
        } finally {
            try { ndef.close(); } catch (Exception ignored) {}
        }
    }

    private String decodificarTextRecord(NdefRecord record) {
        try {
            byte[] payload = record.getPayload();
            if (payload.length == 0) return "(registro vazio)";
            int status = payload[0] & 0xFF;
            int languageCodeLength = status & 0x3F;
            boolean isUtf16 = (status & 0x80) != 0;
            String charset = isUtf16 ? "UTF-16" : "UTF-8";
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, charset);
        } catch (Exception e) {
            return new String(record.getPayload(), StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------
    // Modo ESCREVER (NDEF)
    // ---------------------------------------------------------------

    private void escreverNdef(Tag tag, String texto) {
        NdefRecord record = NdefRecord.createTextRecord("pt", texto);
        NdefMessage mensagem = new NdefMessage(new NdefRecord[]{record});

        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                if (!ndef.isWritable()) {
                    log("⚠️ Esta tag está protegida contra escrita (somente leitura).");
                    return;
                }
                int tamanhoMax = ndef.getMaxSize();
                int tamanhoNecessario = mensagem.toByteArray().length;
                if (tamanhoNecessario > tamanhoMax) {
                    log("⚠️ Texto grande demais para a tag (" + tamanhoNecessario + " bytes; capacidade: " + tamanhoMax + " bytes).");
                    return;
                }
                ndef.writeNdefMessage(mensagem);
                log("✅ Escrita concluída com sucesso!");
            } catch (Exception e) {
                log("❌ Erro ao escrever: " + e.getMessage());
            } finally {
                try { ndef.close(); } catch (Exception ignored) {}
            }
            return;
        }

        // Tag ainda não formatada como NDEF — tenta formatar e escrever de uma vez
        NdefFormatable formatavel = NdefFormatable.get(tag);
        if (formatavel != null) {
            try {
                formatavel.connect();
                formatavel.format(mensagem);
                log("✅ Tag em branco formatada e escrita com sucesso!");
            } catch (Exception e) {
                log("❌ Erro ao formatar/escrever: " + e.getMessage());
            } finally {
                try { formatavel.close(); } catch (Exception ignored) {}
            }
            return;
        }

        log("❌ Esta tag não suporta NDEF nem formatação (provavelmente é uma MIFARE Classic sem NDEF — use o Dump Completo).");
    }

    // ---------------------------------------------------------------
    // Modo DUMP (MIFARE Classic / MIFARE Plus SL1)
    // ---------------------------------------------------------------

    private void dumpMifareClassic(Tag tag) {
        MifareClassic mifare = MifareClassic.get(tag);
        if (mifare == null) {
            log("❌ Esta tag (ou este aparelho) não suporta MIFARE Classic.");
            return;
        }

        try {
            mifare.connect();
            int totalSetores = mifare.getSectorCount();

            JSONObject dump = new JSONObject();
            dump.put("tagId", NfcUtils.bytesParaHex(tag.getId()));
            dump.put("tipo", mifare.getType());
            dump.put("tamanhoBytes", mifare.getSize());
            dump.put("totalSetores", totalSetores);
            JSONArray setoresArray = new JSONArray();

            int setoresAbertos = 0;

            for (int setor = 0; setor < totalSetores; setor++) {
                JSONObject setorObj = new JSONObject();
                setorObj.put("indice", setor);

                byte[] chaveEncontrada = null;
                String tipoChave = null;

                for (byte[] chave : NfcKeyDictionary.CHAVES_PADRAO) {
                    if (mifare.authenticateSectorWithKeyA(setor, chave)) {
                        chaveEncontrada = chave;
                        tipoChave = "A";
                        break;
                    }
                }
                if (chaveEncontrada == null) {
                    for (byte[] chave : NfcKeyDictionary.CHAVES_PADRAO) {
                        if (mifare.authenticateSectorWithKeyB(setor, chave)) {
                            chaveEncontrada = chave;
                            tipoChave = "B";
                            break;
                        }
                    }
                }

                if (chaveEncontrada == null) {
                    setorObj.put("status", "bloqueado");
                    log("🔒 Setor " + setor + ": bloqueado (nenhuma chave do dicionário padrão funcionou).");
                } else {
                    setoresAbertos++;
                    setorObj.put("status", "aberto");
                    setorObj.put("chave", NfcUtils.bytesParaHex(chaveEncontrada));
                    setorObj.put("tipoChave", tipoChave);

                    int primeiroBloco = mifare.sectorToBlock(setor);
                    int blocosNoSetor = mifare.getBlockCountInSector(setor);
                    JSONArray blocosArray = new JSONArray();
                    for (int b = 0; b < blocosNoSetor; b++) {
                        try {
                            byte[] dadosBloco = mifare.readBlock(primeiroBloco + b);
                            blocosArray.put(NfcUtils.bytesParaHex(dadosBloco));
                        } catch (Exception e) {
                            blocosArray.put("ERRO");
                        }
                    }
                    setorObj.put("blocos", blocosArray);
                    log("🔓 Setor " + setor + ": aberto com chave " + tipoChave + " (" + NfcUtils.bytesParaHex(chaveEncontrada) + ").");
                }

                setoresArray.put(setorObj);
            }

            dump.put("dados", setoresArray);
            this.ultimoDump = dump;
            salvarDumpEmArquivo(tag, dump);

            log("✅ Dump concluído: " + setoresAbertos + "/" + totalSetores + " setores acessíveis com o dicionário de chaves padrão.");
            if (setoresAbertos < totalSetores) {
                log("ℹ️ Setores bloqueados usam chave customizada (fora do alcance de um dicionário padrão) — comportamento esperado em tags protegidas.");
            }

        } catch (Exception e) {
            log("❌ Erro durante o dump: " + e.getMessage());
        } finally {
            try { mifare.close(); } catch (Exception ignored) {}
        }
    }

    private void salvarDumpEmArquivo(Tag tag, JSONObject dump) {
        try {
            String nomeArquivo = "nfc_dump_" + NfcUtils.bytesParaHex(tag.getId()) + ".json";
            File file = new File(getFilesDir(), nomeArquivo);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(dump.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            log("💾 Dump salvo em: " + file.getAbsolutePath());
        } catch (Exception e) {
            log("⚠️ Não foi possível salvar o dump em arquivo: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Modo CLONAR (usa o último dump feito como origem)
    // ---------------------------------------------------------------

    private void clonarParaTag(Tag tagAlvo) {
        if (ultimoDump == null) {
            log("⚠️ Nenhum dump disponível. Use 'Dump Completo' na tag de origem primeiro.");
            return;
        }

        MifareClassic mifare = MifareClassic.get(tagAlvo);
        if (mifare == null) {
            log("❌ A tag de destino não suporta MIFARE Classic.");
            return;
        }

        try {
            mifare.connect();
            JSONArray setoresArray = ultimoDump.getJSONArray("dados");
            int gravados = 0, falhas = 0, ignorados = 0;

            for (int i = 0; i < setoresArray.length(); i++) {
                JSONObject setorObj = setoresArray.getJSONObject(i);
                int setor = setorObj.getInt("indice");

                if (!"aberto".equals(setorObj.optString("status"))) {
                    ignorados++;
                    continue;
                }

                byte[] chave = NfcUtils.hexParaBytes(setorObj.getString("chave"));
                boolean autenticado = "A".equals(setorObj.getString("tipoChave"))
                        ? mifare.authenticateSectorWithKeyA(setor, chave)
                        : mifare.authenticateSectorWithKeyB(setor, chave);

                if (!autenticado) {
                    falhas++;
                    log("🔒 Setor " + setor + ": a tag de destino não aceitou a mesma chave (provavelmente já tem uma chave diferente).");
                    continue;
                }

                int primeiroBloco = mifare.sectorToBlock(setor);
                JSONArray blocosArray = setorObj.getJSONArray("blocos");

                for (int b = 0; b < blocosArray.length(); b++) {
                    boolean ehBlocoZero = (setor == 0 && b == 0);
                    if (ehBlocoZero) {
                        // O bloco 0 (UID/fabricante) só é regravável em tags "mágicas"
                        // (Gen1a/Gen2/CUID). Tags comuns bloqueiam essa escrita por hardware.
                        continue;
                    }

                    String hex = blocosArray.getString(b);
                    if ("ERRO".equals(hex)) continue;

                    try {
                        mifare.writeBlock(primeiroBloco + b, NfcUtils.hexParaBytes(hex));
                        gravados++;
                    } catch (Exception e) {
                        falhas++;
                    }
                }
            }

            log("✅ Clonagem concluída: " + gravados + " blocos gravados, " + falhas + " falhas, " + ignorados + " setores ignorados (bloqueados no dump original).");
            log("ℹ️ O bloco 0 (UID/fabricante) não é copiado — isso só é possível em tags 'mágicas' regraváveis; tags comuns bloqueiam essa escrita por hardware.");

        } catch (Exception e) {
            log("❌ Erro durante a clonagem: " + e.getMessage());
        } finally {
            try { mifare.close(); } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------
    // Modo LER CARTÃO (EMV contactless — crédito/débito, somente leitura)
    // ---------------------------------------------------------------

    private void lerCartaoEmv(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            log("❌ Esta tag não é compatível com ISO-DEP/EMV (não parece ser um cartão de pagamento contactless).");
            return;
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(5000);

            log("💳 Selecionando aplicativo de pagamento (PPSE)...");
            byte[] respostaPpse = isoDep.transceive(EmvUtils.APDU_SELECT_PPSE);
            if (!respostaSucesso(respostaPpse)) {
                log("❌ Não foi possível selecionar o PPSE. Esta tag pode não ser um cartão EMV.");
                return;
            }

            byte[] aid = EmvUtils.buscarTag(respostaPpse, "4F");
            if (aid == null) {
                log("❌ Nenhuma aplicação de pagamento (AID) encontrada na resposta do PPSE.");
                return;
            }
            log("✅ Aplicação encontrada (AID: " + NfcUtils.bytesParaHex(aid) + ")");

            byte[] respostaSelectAid = isoDep.transceive(EmvUtils.montarSelectAid(aid));
            if (!respostaSucesso(respostaSelectAid)) {
                log("❌ Falha ao selecionar a aplicação de pagamento.");
                return;
            }

            byte[] respostaGpo = isoDep.transceive(EmvUtils.APDU_GPO_SEM_PDOL);
            if (!respostaSucesso(respostaGpo)) {
                log("❌ O cartão exigiu dados adicionais (PDOL) que esta leitura simples não fornece — não suportado para este cartão específico.");
                return;
            }

            byte[] afl = EmvUtils.buscarTag(respostaGpo, "94");
            List<EmvUtils.EntradaAfl> entradas = EmvUtils.parsearAfl(afl);

            String pan = null, validade = null, nomeTitular = null;

            if (entradas.isEmpty()) {
                log("⚠️ Lista de registros (AFL) não encontrada — tentando os registros mais comuns.");
                for (int sfi = 1; sfi <= 3 && pan == null; sfi++) {
                    for (int rec = 1; rec <= 3 && pan == null; rec++) {
                        byte[] resp = isoDep.transceive(EmvUtils.montarReadRecord(rec, sfi));
                        if (respostaSucesso(resp)) {
                            String[] extraidos = extrairDadosDoRegistro(resp);
                            if (extraidos[0] != null) pan = extraidos[0];
                            if (extraidos[1] != null) validade = extraidos[1];
                            if (extraidos[2] != null) nomeTitular = extraidos[2];
                        }
                    }
                }
            } else {
                for (EmvUtils.EntradaAfl entrada : entradas) {
                    for (int rec = entrada.primeiroRegistro; rec <= entrada.ultimoRegistro; rec++) {
                        byte[] resp = isoDep.transceive(EmvUtils.montarReadRecord(rec, entrada.sfi));
                        if (respostaSucesso(resp)) {
                            String[] extraidos = extrairDadosDoRegistro(resp);
                            if (extraidos[0] != null) pan = extraidos[0];
                            if (extraidos[1] != null) validade = extraidos[1];
                            if (extraidos[2] != null) nomeTitular = extraidos[2];
                        }
                    }
                }
            }

            log("──────────────");
            log(pan != null ? "💳 Número (mascarado): " + EmvUtils.mascararPan(pan)
                    : "💳 Número: não foi possível ler (fora do padrão simples suportado).");
            log(validade != null ? "📅 Validade: " + formatarValidade(validade) : "📅 Validade: não encontrada.");
            log(nomeTitular != null ? "🙍 Titular: " + nomeTitular.trim()
                    : "🙍 Titular: não disponível neste cartão (muitos bancos não gravam o nome no chip contactless).");
            log("ℹ️ Estes são apenas os dados públicos que o padrão EMV expõe sem senha — o mesmo que já está impresso/gravado no cartão físico. Não é possível clonar nem realizar transações com isso: o chip usa criptografia dinâmica única por transação.");

        } catch (Exception e) {
            log("❌ Erro ao ler o cartão: " + e.getMessage());
        } finally {
            try { isoDep.close(); } catch (Exception ignored) {}
        }
    }

    private boolean respostaSucesso(byte[] resposta) {
        return resposta != null && resposta.length >= 2
                && (resposta[resposta.length - 2] & 0xFF) == 0x90
                && (resposta[resposta.length - 1] & 0xFF) == 0x00;
    }

    /** Retorna {pan, validadeYYMMDD, nomeTitular} — cada posição pode vir null se não encontrada. */
    private String[] extrairDadosDoRegistro(byte[] registro) {
        String pan = null, validade = null, nome = null;

        byte[] panBytes = EmvUtils.buscarTag(registro, "5A");
        if (panBytes != null) {
            pan = EmvUtils.bcdParaDigitos(panBytes);
        }

        byte[] validadeBytes = EmvUtils.buscarTag(registro, "5F24");
        if (validadeBytes != null) {
            validade = EmvUtils.bcdParaDigitos(validadeBytes);
        }

        byte[] nomeBytes = EmvUtils.buscarTag(registro, "5F20");
        if (nomeBytes != null) {
            nome = new String(nomeBytes, StandardCharsets.US_ASCII);
        }

        return new String[]{pan, validade, nome};
    }

    private String formatarValidade(String yymmdd) {
        if (yymmdd == null || yymmdd.length() < 4) return yymmdd;
        return yymmdd.substring(2, 4) + "/" + yymmdd.substring(0, 2); // MM/AA
    }

    // ---------------------------------------------------------------

    private void log(String mensagem) {
        runOnUiThread(() -> txtLog.append(mensagem + "\n\n"));
    }
}
