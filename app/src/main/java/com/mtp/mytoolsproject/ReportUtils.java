package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gera relatórios simples em PDF (título + lista de linhas de texto, com
 * paginação automática) usando a API nativa do Android — sem depender de
 * nenhuma biblioteca externa — e compartilha via FileProvider.
 */
public final class ReportUtils {

    private static final int LARGURA_PAGINA = 595; // A4 em pontos, ~72dpi
    private static final int ALTURA_PAGINA = 842;
    private static final int MARGEM = 40;
    private static final float ALTURA_LINHA = 16f;

    private ReportUtils() {}

    public static File gerarRelatorioPdf(Context context, String nomeArquivo, String titulo, List<String> linhas) throws Exception {
        PdfDocument document = new PdfDocument();

        Paint paintTitulo = new Paint();
        paintTitulo.setTextSize(18f);
        paintTitulo.setFakeBoldText(true);
        paintTitulo.setColor(0xFF000000);

        Paint paintTexto = new Paint();
        paintTexto.setTextSize(11f);
        paintTexto.setColor(0xFF000000);

        Paint paintRodape = new Paint();
        paintRodape.setTextSize(9f);
        paintRodape.setColor(0xFF888888);

        int numeroPagina = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(LARGURA_PAGINA, ALTURA_PAGINA, numeroPagina).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = MARGEM;
        canvas.drawText(titulo, MARGEM, y, paintTitulo);
        y += 24;

        String dataGeracao = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Gerado por My Toolkit em " + dataGeracao, MARGEM, y, paintRodape);
        y += 20;

        for (String linha : linhas) {
            if (y > ALTURA_PAGINA - MARGEM) {
                document.finishPage(page);
                numeroPagina++;
                pageInfo = new PdfDocument.PageInfo.Builder(LARGURA_PAGINA, ALTURA_PAGINA, numeroPagina).create();
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = MARGEM;
            }
            canvas.drawText(linha, MARGEM, y, paintTexto);
            y += ALTURA_LINHA;
        }

        document.finishPage(page);

        File file = new File(context.getCacheDir(), nomeArquivo);
        FileOutputStream fos = new FileOutputStream(file);
        document.writeTo(fos);
        fos.close();
        document.close();

        return file;
    }

    public static void compartilharPdf(Context context, File arquivo) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", arquivo);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório PDF"));
    }
}
