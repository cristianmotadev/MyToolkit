package com.mtp.mytoolsproject.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mtp.mytoolsproject.RootUtils;

/**
 * ViewModel para gerenciamento de verificação de root.
 * 
 * Implementa padrão MVVM para separar lógica de negócio da UI.
 */
public class RootViewModel extends AndroidViewModel {
    
    private final MutableLiveData<Boolean> isRooted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> estaVerificando = new MutableLiveData<>(false);
    private final MutableLiveData<String> erroVerificacao = new MutableLiveData<>();
    
    public RootViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * LiveData que indica se o dispositivo tem root.
     * @return LiveData observável com status de root
     */
    public LiveData<Boolean> getIsRooted() {
        return isRooted;
    }
    
    /**
     * LiveData que indica se uma verificação está em andamento.
     * @return LiveData observável com estado de loading
     */
    public LiveData<Boolean> isEstaVerificando() {
        return estaVerificando;
    }
    
    /**
     * LiveData que emite mensagens de erro durante verificação.
     * @return LiveData observável com mensagem de erro
     */
    public LiveData<String> getErroVerificacao() {
        return erroVerificacao;
    }
    
    /**
     * Verifica root de forma assíncrona usando método completo.
     * O resultado é emitido via LiveData.
     */
    public void verificarRoot() {
        estaVerificando.postValue(true);
        erroVerificacao.postValue(null);
        
        RootUtils.checkRootAsync(getApplication(), new RootUtils.RootCheckCallback() {
            @Override
            public void onResult(boolean rooted) {
                isRooted.postValue(rooted);
                estaVerificando.postValue(false);
            }
            
            @Override
            public void onError(Exception error) {
                erroVerificacao.postValue(error.getMessage());
                estaVerificando.postValue(false);
            }
        });
    }
    
    /**
     * Verificação rápida de root (apenas binário su).
     * Mais rápida mas menos precisa.
     */
    public void verificarRootRapido() {
        estaVerificando.postValue(true);
        
        new Thread(() -> {
            try {
                boolean rooted = RootUtils.isRootedQuick(getApplication());
                isRooted.postValue(rooted);
            } catch (Exception e) {
                erroVerificacao.postValue(e.getMessage());
            } finally {
                estaVerificando.postValue(false);
            }
        }).start();
    }
    
    /**
     * Verifica root incluindo apps conhecidos.
     * Mais abrangente que verificação padrão.
     */
    public void verificarRootCompleto() {
        estaVerificando.postValue(true);
        
        new Thread(() -> {
            try {
                boolean rooted = RootUtils.isRootedWithAppCheck(getApplication());
                isRooted.postValue(rooted);
            } catch (Exception e) {
                erroVerificacao.postValue(e.getMessage());
            } finally {
                estaVerificando.postValue(false);
            }
        }).start();
    }
    
    /**
     * Limpa cache de root para forçar nova verificação.
     */
    public void limparCache() {
        RootUtils.clearCache();
        isRooted.postValue(null);
    }
}
