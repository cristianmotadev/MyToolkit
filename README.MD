# 🛠️ My Toolkit v1.0

**Um conjunto completo de ferramentas de rede, segurança e utilidades para Android**

Desenvolvido por [@cristianmotadev](https://github.com/cristianmotadev) · 

---

## 📖 Sobre o Projeto

O **My Toolkit** é um aplicativo Android nativo (Java) que reúne, em um só lugar, ferramentas de diagnóstico de rede, auditoria de Wi-Fi, leitura/escrita NFC e utilidades de segurança — a maioria delas dependente de acesso **root**, aproveitando permissões de superusuário para oferecer recursos que apps convencionais da Play Store não conseguem.

O projeto nasceu com o objetivo de explorar, na prática, conceitos de redes, segurança da informação e desenvolvimento Android, sempre dentro de limites éticos — priorizando **leitura, diagnóstico e auditoria passiva** em vez de ferramentas de exploração ativa.

---

## 🌐 Ferramentas de Rede

| Ferramenta | O que faz |
|---|---|
| **Radar de Dispositivos** | Varre a rede local (ping + tabela ARP), identifica fabricante por MAC, portas abertas e salva um histórico persistente |
| **Radar Wi-Fi** | Lista redes Wi-Fi próximas com segurança, canal, banda e força de sinal — inclui auditoria passiva de WPS |
| **Radar Bluetooth** | Descoberta combinada (clássico + BLE) de dispositivos Bluetooth próximos, com força de sinal |
| **Scanner de Porta** | Varredura de portas customizável (faixa definida pelo usuário), com paralelismo e barra de progresso real |
| **Traceroute** | Rastreia o caminho até um destino usando a técnica de TTL crescente via ICMP |
| **Consulta DNS** | Resolve domínio → IP (A/AAAA/MX/TXT/NS/CNAME) e IP → hostname (busca reversa) |
| **Teste de Velocidade** | Mede ping, download e upload usando endpoints públicos |
| **Calculadora de Sub-rede** | Calcula máscara, endereço de rede, broadcast e faixa de hosts a partir de um IP/CIDR |
| **Senhas Wi-Fi Salvas** | Lê senhas salvas no sistema (via root), mantém backup próprio (sobrevive a "esquecer" a rede), copia e compartilha via texto ou QR Code |

## 📲 Módulo NFC

- **Leitura e escrita NDEF** (texto simples e cartões de visita vCard)
- **Dump e clonagem de tags MIFARE Classic/Plus (SL1)**, com dicionário de chaves padrão — só desbloqueia setores com chave de fábrica, respeitando tags com chave customizada
- **Leitura somente-leitura de cartões EMV** (crédito/débito contactless): número mascarado, validade e titular — apenas os dados que o próprio padrão expõe publicamente, sem qualquer capacidade de clonagem funcional

## 🔐 Segurança e Privacidade

- **Bloqueio do app** por PIN (hash SHA-256 com salt) e/ou biometria (digital/rosto)
- **`FLAG_SECURE`** na tela de senhas Wi-Fi, bloqueando screenshot e gravação de tela
- **Auditoria de Apps Instalados**: lista apps com permissões sensíveis concedidas (câmera, localização, contatos, SMS...), ordenados por nível de risco
- **Informações do Sistema**: specs de hardware, kernel, sensores e bateria
- **Exportar/Importar banco de dados** e **relatórios em PDF** para documentação/evidência

## ⚙️ Configurações e Personalização

- Tema **Claro / Escuro / Sistema**
- Varredura manual **ou** automática (com intervalo configurável) para os principais scanners, rodando de verdade em segundo plano via `Service`
- Verificação de atualização integrada ao GitHub Releases
- Verificação explícita de disponibilidade de root
- Onboarding de primeira abertura, revisável a qualquer momento

---

## 🧭 Considerações Éticas

Ao longo do desenvolvimento, algumas funcionalidades sugeridas foram **conscientemente descartadas** por constituírem ferramentas de ataque ativo em vez de auditoria:

- ❌ Camuflagem de dispositivo via WPS (para induzir autenticação indevida em roteadores)
- ❌ Integração de scripts de força bruta/exploração de WPS (aircrack-ng, hashcat, pixiewps)

Em substituição, o app implementa **auditoria passiva**: identifica e alerta sobre vulnerabilidades conhecidas (WPS ativo, criptografia fraca, permissões excessivas) sem jamais tentar explorá-las. Essa escolha de design é intencional e documentada como parte do compromisso do projeto com pesquisa de segurança responsável.

---

## 🏗️ Arquitetura Técnica

- **Linguagem:** Java · **Plataforma:** Android nativo (minSdk 26)
- **Persistência:** arquivos JSON próprios (sem banco de dados relacional), SharedPreferences para configurações
- **Segundo plano:** `Foreground Services` dedicados para varredura contínua (rede local e Wi-Fi)
- **APIs nativas usadas:** NFC (`NfcAdapter`, `MifareClassic`, `IsoDep`), Wi-Fi (`WifiManager`), Bluetooth (`BluetoothAdapter`, `BluetoothLeScanner`), Biometria (`BiometricPrompt`), PDF (`PdfDocument`)
- **Bibliotecas externas:** ZXing (geração de QR Code), Biometric (AndroidX)
- **Requisito de sistema:** acesso root (testado com Magisk)

---

## 📦 Repositório

🔗 [github.com/cristianmotadev/MyToolkit](https://github.com/cristianmotadev/MyToolkit)

---
