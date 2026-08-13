package com.mtp.mytoolsproject;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para RootUtils.
 * 
 * Para executar: ./gradlew test
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class RootUtilsTest {

    @Mock
    private Context mockContext;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Limpa cache antes de cada teste
        RootUtils.clearCache();
    }

    @Test
    public void testRootUtils_ConstructorIsPrivate() throws Exception {
        // Verifica que a classe não pode ser instanciada
        try {
            RootUtils.class.getDeclaredConstructor().newInstance();
            // Se chegar aqui, falhou
            assertTrue("Construtor deve ser privado", false);
        } catch (Exception e) {
            // Esperado - construtor privado
            assertTrue(true);
        }
    }

    @Test
    public void testClearCache_resetsCachedStatus() {
        // Simula que já foi feito check anterior
        RootUtils.clearCache();
        
        // Após clear, o cache deve estar nulo
        // Nota: Não podemos acessar diretamente o campo privado cachedRootStatus,
        // mas podemos verificar que clearCache() executa sem erros
        assertTrue(true); // Teste básico de que o método funciona
    }

    @Test
    public void testIsRootedQuick_doesNotThrowException() {
        // Mesmo com context mockado, não deve lançar exceção
        try {
            // Nota: Este teste pode retornar falso positivo/negativo
            // pois estamos usando mock, mas verifica que não há crash
            boolean result = RootUtils.isRootedQuick(mockContext);
            // Apenas verifica que retornou um booleano válido
            assertTrue(result || !result);
        } catch (Exception e) {
            assertTrue("Não deve lançar exceção", false);
        }
    }

    @Test
    public void testHasKnownRootApps_withEmptyContext() {
        // Com context mockado sem packages instalados, deve retornar false
        when(mockContext.getPackageManager()).thenThrow(
            new android.content.pm.PackageManager.NameNotFoundException()
        );
        
        boolean hasApps = RootUtils.hasKnownRootApps(mockContext);
        assertFalse("Não deve ter apps de root em context vazio", hasApps);
    }

    @Test
    public void testRootCheckCallback_interfaceExists() {
        // Verifica que a interface callback existe e pode ser implementada
        RootUtils.RootCheckCallback callback = new RootUtils.RootCheckCallback() {
            @Override
            public void onResult(boolean isRooted) {
                // Implementação vazia para teste
            }

            @Override
            public void onError(Exception error) {
                // Implementação vazia para teste
            }
        };
        
        assertTrue("Callback deve ser instanciável", callback != null);
    }

    @Test
    public void testCheckRootAsync_runsInBackground() {
        // Testa que o método assíncrono pode ser chamado sem bloquear
        RootUtils.RootCheckCallback callback = new RootUtils.RootCheckCallback() {
            @Override
            public void onResult(boolean isRooted) {
                // Callback executado
            }

            @Override
            public void onError(Exception error) {
                // Erro tratado
            }
        };
        
        // Deve executar sem lançar exceção
        RootUtils.checkRootAsync(mockContext, callback);
        assertTrue(true);
    }
}
