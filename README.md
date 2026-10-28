# GUIA DE PROVA — StudyTrack API (90 minutos)

**Objetivo:** criar uma API Java simples, testar, containerizar, subir ao GitHub e publicar no **Azure App Service (Web App)** pelo Portal do Azure, com CI/CD via GitHub Actions quando o tempo e as permissões permitirem.

> **Atenção:** roteiro de treino, não uma garantia de conclusão em 90 minutos. Pressupõe Java 21, Git, Docker Desktop, VS Code, conta GitHub e assinatura Azure configurados. Uma prova que exija banco de dados, autenticação ou outros requisitos precisará de ajustes. **Não implante recursos pagos sem necessidade; exclua-os ao final.**

## Cronômetro

| Tempo | Tarefa | Critério de conclusão |
|---|---|---|
| 00–20 min | Spring Boot + endpoints | GET e POST funcionam localmente |
| 20–35 min | Testes e Docker | Imagem roda e `/actuator/health` responde |
| 35–50 min | GitHub + Actions | Código enviado; CI executa |
| 50–70 min | Portal Azure | Web App Linux configurado para container |
| 70–85 min | Publicação e validação | URL HTTPS responde; pipeline de deploy se viável |
| 85–90 min | Evidências | Prints e logs essenciais salvos |

**Regra de prova:** se uma etapa estiver bloqueada por mais de 5 minutos, guarde o erro e avance para outra parte que pontue. Reserve os últimos 5 minutos para evidências.

## 0. Pré-requisitos — confira ANTES de começar

```powershell
java -version
git --version
docker --version
docker info
```

- Java 21; Docker Desktop ligado; Git autenticado.
- [Spring Initializr](https://start.spring.io/), [GitHub](https://github.com/), [Docker Hub](https://hub.docker.com/), [Portal Azure](https://portal.azure.com/).
- O plano **App Service B1 pode gerar cobrança enquanto existir**, mesmo sem tráfego. Confira disponibilidade e região permitida na assinatura Azure for Students; no laboratório anterior, `francecentral` estava liberada.
- **Atenção:** alguns ambientes ou assinaturas podem não permitir publicação de contêiner personalizado no plano escolhido. Verifique as opções reais no Portal antes de criar o serviço.

## 1. Criar o projeto — 00–20 minutos

Abra [start.spring.io](https://start.spring.io/) e selecione:

| Campo | Valor |
|---|---|
| Project / Language | Maven / Java |
| Spring Boot | Versão estável compatível com Java 21 exibida no site |
| Group | `br.com.fiap` |
| Artifact / Name | `studytrack` |
| Packaging / Java | Jar / 21 |
| Dependencies | **Spring Web**, **Validation**, **Spring Boot Actuator** |

Baixe e extraia para `C:\fiap26\studytrack`, de forma que o `pom.xml` fique nessa pasta.

### Arquivos mínimos

`src/main/java/br/com/fiap/studytrack/Estudo.java`

```java
package br.com.fiap.studytrack;

public record Estudo(Long id, String materia, String descricao) {}
```

`src/main/java/br/com/fiap/studytrack/EstudoController.java`

```java
package br.com.fiap.studytrack;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/estudos")
public class EstudoController {
    private final List<Estudo> estudos = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    @GetMapping
    public synchronized List<Estudo> listar() {
        return List.copyOf(estudos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public synchronized Estudo criar(@RequestBody Estudo entrada) {
        if (entrada.materia() == null || entrada.materia().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "materia obrigatoria");
        }
        Estudo novo = new Estudo(ids.incrementAndGet(), entrada.materia(), entrada.descricao());
        estudos.add(novo);
        return novo;
    }
}
```

> Armazenamento **em memória**: os registros somem ao reiniciar a aplicação. Este recorte foi escolhido para treinar deploy rapidamente e **não é um CRUD completo** (apenas leitura e criação).

No terminal:

```powershell
cd C:\fiap26\studytrack
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Em **outro** terminal PowerShell:

```powershell
$local = "http://localhost:8080"
Invoke-RestMethod "$local/actuator/health"
Invoke-RestMethod "$local/api/estudos"
Invoke-RestMethod "$local/api/estudos" -Method Post -ContentType "application/json" -Body '{"materia":"DevOps","descricao":"Treinar Azure"}'
Invoke-RestMethod "$local/api/estudos"
```

**Esperado:** health `UP`, `POST` retorna `201` com ID e `GET` lista o estudo. Pare o Spring Boot com `Ctrl+C` antes de executar Docker na mesma porta.

## 2. Docker — 20–35 minutos

Na raiz do projeto, crie o `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Crie também `.dockerignore`:

```gitignore
.git
.github
.idea
.vscode
*.md
```

Execute:

```powershell
.\mvnw.cmd clean package

docker build -t studytrack:local .
docker run --rm -d --name studytrack-test -p 8081:8080 studytrack:local
Invoke-RestMethod http://localhost:8081/actuator/health
docker logs studytrack-test --tail 30
docker stop studytrack-test
```

**Esperado:** `UP`. Se o JAR não for encontrado, confirme que `target/` contém o arquivo e que `mvn package` terminou sem erro. O Dockerfile acima requer compilação local prévia.

## 3. GitHub e integração contínua — 35–50 minutos

Crie um **repositório vazio** chamado `studytrack-api` na sua conta GitHub, sem README automático. Na raiz, crie `.gitignore` (se o Initializr não tiver criado):

```gitignore
/target/
.env
*.log
.idea/
.vscode/
```

Crie `.github/workflows/ci.yml`:

```yaml
name: Java CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Testar e empacotar
        run: ./mvnw clean verify
```

Para o GitHub Actions em Linux, verifique se `mvnw` tem permissão de execução (`git update-index --chmod=+x mvnw` quando necessário). Se preferir evitar esse detalhe, troque `./mvnw clean verify` por `mvn -B clean verify` no workflow.

```powershell
cd C:\fiap26\studytrack
git init
git branch -M main
git add .
git status --short
git commit -m "feat: cria StudyTrack API"
git remote add origin https://github.com/SEU-USUARIO/studytrack-api.git
git push -u origin main
```

Substitua `SEU-USUARIO`. Na página do GitHub, abra **Actions** e confira se o workflow ficou verde.

## 4. Publicar imagem no Docker Hub — 35–50 ou 50–60 minutos

Para seguir o exemplo de deploy em contêiner dos PDFs FIAP, use uma imagem **pública** no Docker Hub durante o treino:

```powershell
docker login
docker tag studytrack:local SEU-USUARIO-DOCKERHUB/studytrack-api:v1
docker push SEU-USUARIO-DOCKERHUB/studytrack-api:v1
```

Confirme em [hub.docker.com](https://hub.docker.com/) que o repositório está público e a tag `v1` existe. **Não coloque credenciais em código-fonte.**

> **Observação importante:** esses passos publicam a imagem manualmente. A CI acima automatiza os testes, não a publicação da imagem. O deploy automático será configurado como etapa adicional após a primeira implantação funcional.

## 5. Criar o Azure Web App PELO PORTAL — 50–70 minutos

Acesse [portal.azure.com](https://portal.azure.com/).

1. **Resource groups** → **Create** → nome `rg-studytrack-prova`, região permitida pela assinatura (exemplo do laboratório: `France Central`).
2. **Create a resource** → **Web App** → **Create**.
3. **Subscription:** Azure for Students; **Resource group:** `rg-studytrack-prova`.
4. **Name:** um nome globalmente exclusivo, por exemplo `studytrack-rm556620-2026` (se estiver disponível).
5. **Publish:** **Container** (pode aparecer como opção de publicação em contêiner, conforme a interface atual); **Operating system:** **Linux**.
6. **Region:** a mesma região permitida; **App Service Plan:** escolha um plano Linux compatível, por exemplo **Basic B1** se disponível na assinatura. **Confirme o preço na tela antes de criar.**
7. **Container:** imagem do **Docker Hub**, repositório `SEU-USUARIO-DOCKERHUB/studytrack-api`, tag `v1`. Use imagem pública para dispensar credenciais do registro.
8. Em configurações de aplicativo/variáveis de ambiente do Web App, adicione **`WEBSITES_PORT=8080`** caso o Portal não detecte automaticamente a porta do contêiner.
9. **Review + create** → confira → **Create** → **Go to resource**.

> O exemplo do PDF “Deployment na Nuvem” utiliza **App Service / Web App pelo Portal**; a configuração específica de Java em contêiner é uma adaptação ao nosso StudyTrack. Os rótulos e opções disponíveis podem variar no Portal.

**URL:** `https://NOME-DO-SEU-WEBAPP.azurewebsites.net`

```powershell
$azure = "https://NOME-DO-SEU-WEBAPP.azurewebsites.net"
Invoke-RestMethod "$azure/actuator/health" -TimeoutSec 120
Invoke-RestMethod "$azure/api/estudos" -TimeoutSec 120
```

Se falhar, no Portal use **Web App → Log stream**, **Deployment Center**, **Configuration / Environment variables** e confira a imagem, a tag e a porta `8080`.

## 6. CI/CD no Portal + GitHub Actions — 70–85 minutos (se disponível)

A apostila de Continuous Integration mostra o uso de **GitHub Actions** e `azure/webapps-deploy` para automatizar o deploy no Web App. Em um treino de 90 minutos, faça a **primeira publicação manual** e só configure o deploy automático se o site já estiver funcional.

**Opção guiada:** no Portal, abra **Web App → Deployment Center**, examine as opções de integração com GitHub / GitHub Actions disponíveis para **imagem de contêiner** e siga a configuração gerada para sua combinação de plano e registro. Verifique especialmente que ela **constrói/publica a imagem e só depois atualiza o Web App**; uma CI que só testa não realiza deploy. Dependendo da interface, a automação precisará de workflow e credenciais adicionais.

**Não** presuma que escolher GitHub automaticamente cria um pipeline de contêiner correto. Valide em **GitHub → Actions** e no endpoint HTTPS publicado.

## 7. Evidências — 85–90 minutos

Capturas recomendadas:

- [ ] `mvn test` / GitHub Actions verde
- [ ] Docker Desktop ou terminal com imagem e endpoint `UP`
- [ ] Imagem/tag pública no Docker Hub
- [ ] Visão geral do Web App no Azure (nome, região, status)
- [ ] Endpoint `/actuator/health` retornando `UP`
- [ ] `POST` e `GET` da API no Azure (se funcionarem)
- [ ] GitHub Actions de deploy verde **somente se de fato configurado**

## 8. Limpeza de custos — após salvar as evidências

No **Portal Azure**: **Resource groups** → `rg-studytrack-prova` → **Delete resource group** → confirme o nome do grupo. A exclusão apaga o Web App, o App Service Plan e quaisquer outros recursos dentro desse grupo.

Se o laboratório anterior ainda existir, procure também `rg-taskflow-fiap`; ele é **separado**, e não é excluído quando você apaga `rg-studytrack-prova`.

## Erros comuns e correções rápidas

| Sintoma | Confira |
|---|---|
| `8080` ocupado | Pare o Spring Boot local ou use `8081:8080` no Docker |
| Container sai imediatamente | `docker logs`, versão Java e JAR copiado |
| Azure retorna 502/503 | Log stream, tag da imagem, `WEBSITES_PORT=8080`, inicialização |
| GitHub Actions falha no Maven | Java 21, `mvnw` executável, `pom.xml` na raiz |
| Web App não aparece na região | Política da assinatura e disponibilidade de SKU |
| Imagem privada não baixa | Configure credenciais ou torne a imagem pública para o treino |
| Cloud continua cobrando | Exclua o **Resource Group**, não somente o Web App |

## Comandos para decorar

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
docker build -t studytrack:local .
docker run --rm -d --name studytrack-test -p 8081:8080 studytrack:local
Invoke-RestMethod http://localhost:8081/actuator/health
docker stop studytrack-test
git add .
git commit -m "feat: atualiza projeto"
git push origin main
```

**Prioridade para a prova:** projeto inicializa → testes passam → Docker funciona → GitHub Actions roda → Web App responde HTTPS → evidências salvas. Não perca todo o tempo depurando uma melhoria opcional.
