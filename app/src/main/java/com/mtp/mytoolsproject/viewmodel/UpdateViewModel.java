package com.mtp.mytoolsproject.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mtp.mytoolsproject.UpdateChecker;

/**
 * ViewModel para gerenciamento de verificações de atualização.
 * 
 * Implementa padrão MVVM para separar lógica de negócio da UI.
 * Fornece dados observáveis para Activities/Fragments.
 */
public class UpdateViewModel extends AndroidViewModel {
    
    private final MutableLiveData<UpdateChecker.ResultadoVerificacao> resultadoAtualizacao = new MutableLiveData<>();
    private final MutableLiveData<Boolean> estaVerificando = new MutableLiveData<>(false);
    private final SharedPreferences prefs;
    
    public UpdateViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences("NetworkPrefs", Context.MODE_PRIVATE);
    }
    
    /**
     * LiveData que emite o resultado da última verificação de atualização.
     * @return LiveData observável com o resultado
     */
    public LiveData<UpdateChecker.ResultadoVerificacao> getResultadoAtualizacao() {
        return resultadoAtualizacao;
    }
    
    /**
     * LiveData que indica se uma verificação está em andamento.
     * @return LiveData observável com estado de loading
     */
    public LiveData<Boolean> isEstaVerificando() {
        return estaVerificando;
    }
    
    /**
     * Verifica atualizações de forma assíncrona.
     * O resultado é emitido via LiveData.
     * 
     * @param canal Canal (OFICIAL ou BETA)
     * @param tipo Tipo (RELEASE ou COMMIT)
     * @param forcarAtualizacao true para ignorar cache
     */
    public void verificarAtualizacao(UpdateChecker.Canal canal, UpdateChecker.Tipo tipo, boolean forcarAtualizacao) {
        estaVerificando.postValue(true);
        
        new Thread(() -> {
            try {
                UpdateChecker.ResultadoVerificacao resultado = UpdateChecker.verificar(
                    getApplication(), canal, tipo, forcarAtualizacao
                );
                resultadoAtualizacao.postValue(resultado);
            } catch (Exception e) {
                UpdateChecker.ResultadoVerificacao erro = new UpdateChecker.ResultadoVerificacao();
                erro.mensagemErro = "Erro ao verificar: " + e.getMessage();
                resultadoAtualizacao.postValue(erro);
            } finally {
                estaVerificando.postValue(false);
            }
        }).start();
    }
    
    /**
     * Verifica atualizações usando preferências salvas.
     * Útil para verificações automáticas (ex: ao abrir o app).
     */
    public void verificarAtualizacaoAutomatica() {
        boolean canalBeta = prefs.getBoolean("canal_atualizacao_beta", false);
        boolean tipoCommit = prefs.getBoolean("tipo_atualizacao_commit", false);
        
        UpdateChecker.Canal canal = canalBeta ? UpdateChecker.Canal.BETA : UpdateChecker.Canal.OFICIAL;
        UpdateChecker.Tipo tipo = tipoCommit ? UpdateChecker.Tipo.COMMIT : UpdateChecker.Tipo.RELEASE;
        
        // Não força atualização nas verificações automáticas (usa cache)
        verificarAtualizacao(canal, tipo, false);
    }
    
    /**
     * Limpa o cache de verificações.
     */
    public void limparCache() {
        UpdateChecker.limparCache();
    }
    
    /**
     * Obtém as configurações atuais de atualização.
     * @return Array com [canalBeta, tipoCommit]
     */
    public boolean[] getConfiguracoesAtuais() {
        boolean canalBeta = prefs.getBoolean("canal_atualizacao_beta", false);
        boolean tipoCommit = prefs.getBoolean("tipo_atualizacao_commit", false);
        return new boolean[]{canalBeta, tipoCommit};
    }
}
