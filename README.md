faça o clone do frontend para o android studio e o clone do back para sua IDE( eu utilizei Intellij, mas pode ser vsCode)

Passo a Passo para Execução
1. Configurar o Banco de Dados (PostgreSQL)
O backend espera uma conexão com um banco PostgreSQL rodando na porta 5432. A maneira mais rápida de subir o banco é via Docker.

Execute o seguinte comando no terminal para criar o container com as credenciais esperadas pelo código (user: postgres, password: admin):
docker run --name postgres-tcc -e POSTGRES_PASSWORD=admin -p 5432:5432 -d postgres

2. Rodar o Backend
Navegue até a pasta do backend:
cd seudiretorio/backendTCC/backendTCC

Execute o servidor via Gradle:
gradlew.bat run

Aguarde a mensagem: Responding at http://0.0.0.0:8080.

O servidor estará rodando em localhost:8080.

3. Rodar o Aplicativo Mobile
Abra o Android Studio.

Selecione Open e navegue até a pasta do projeto mobile: seudiretorio/healthcareTCC/HealthCareTCC

Aguarde a sincronização do Gradle.

Crie um Emulador Android (AVD) ou conecte um dispositivo físico.

Clique no botão Run (Play)

-----------------//--------------

Importante sobre Conexão (IP)
O aplicativo está configurado para rodar no Emulador Android por padrão.

No arquivo RetrofitClient.kt, a BASE_URL está definida como http://10.0.2.2:8080/.

O IP 10.0.2.2 é um endereço especial usado pelo emulador do Android para acessar o localhost da máquina do computador.

Se você for rodar em um celular físico:

Certifique-se de que o celular e o computador estão na mesma rede Wi-Fi.

Descubra o IP local do seu computador (ex: 192.168.0.15).

Altere o arquivo RetrofitClient.kt:

// De:
private const val BASE_URL = "http://10.0.2.2:8080/"
// Para:
private const val BASE_URL = "http://192.168.0.15:8080/"
