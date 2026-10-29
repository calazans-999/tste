# MiniTask API — Guia completo para prova prática FIAP (90 minutos)

> **Objetivo:** criar uma API REST mínima em Java 21 e Spring Boot, testar localmente, empacotar com Docker, publicar no GitHub/Docker Hub e disponibilizar pelo **Portal Azure → App Service (Web App)**. O fluxo acompanha os temas dos PDFs da FIAP de Deployment na Nuvem e Continuous Integration, mas **não substitui o enunciado oficial da avaliação**.
>
> **Escopo mínimo:** `GET /api/tarefas`, `POST /api/tarefas` e `GET /actuator/health`. Sem banco de dados: os registros são perdidos quando o processo reinicia. O pipeline de CI/CD é um exemplo executável que exige configurar credenciais e permissões.

## 0. Cronograma de 90 minutos

| Etapa | Tempo-alvo |
|---|---:|
| Gerar Spring Boot e criar controller | 15 min |
| Testes locais, build e Docker | 15 min |
| GitHub + workflow CI/CD | 20 min |
| Azure Portal: Web App | 25 min |
| Verificações, prints e limpeza | 15 min |
| **Total** | **90 min** |

**Antes de começar:** Java 21, Docker Desktop e Git instalados; conta GitHub, Docker Hub e Azure já criadas. O provisionamento de recursos e o processamento do workflow podem ultrapassar os tempos. Use **France Central** se a assinatura restringir a região; confirme a disponibilidade do plano na sua assinatura. Evite criar um segundo PostgreSQL para este exercício.

## 1. Gerar projeto Spring Boot pelo navegador

Entre em https://start.spring.io/ e selecione:

| Campo | Escolha |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | versão estável oferecida no site, compatível com Java 21 |
| Group | `br.com.fiap` |
| Artifact / Name | `minitask` |
| Package | `br.com.fiap.minitask` |
| Packaging | Jar |
| Java | 21 |
| Dependencies | **Spring Web** e **Spring Boot Actuator** |

Clique **Generate**. Extraia o ZIP para `C:\fiap26\minitask` (evite uma pasta `minitask\minitask` duplicada).

Abra o PowerShell:

```powershell
cd C:\fiap26\minitask
java -version
.\mvnw.cmd -version
```

## 2. Implementar a API mínima

Crie `src/main/java/br/com/fiap/minitask/TarefaController.java`:

```java
package br.com.fiap.minitask;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/tarefas")
public class TarefaController {

    private final Map<Long, Tarefa> tarefas = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public record Tarefa(Long id, String titulo, String status) {}
    public record CriarTarefa(String titulo) {}

    @GetMapping
    public Collection<Tarefa> listar() {
        return tarefas.values();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tarefa cadastrar(@RequestBody CriarTarefa entrada) {
        if (entrada == null || entrada.titulo() == null || entrada.titulo().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "titulo e obrigatorio"
            );
        }
        long id = sequencia.incrementAndGet();
        Tarefa tarefa = new Tarefa(id, entrada.titulo(), "PENDENTE");
        tarefas.put(id, tarefa);
        return tarefa;
    }
}
```

A classe `MinitaskApplication.java` foi gerada pelo Spring Initializr; **não é necessário alterá-la**. Em `src/main/resources/application.properties`, use:

```properties
spring.application.name=minitask
server.port=${PORT:8080}
management.endpoints.web.exposure.include=health,info
```

> O código usa armazenamento em memória. Ele demonstra endpoints e deploy, mas **não é um CRUD completo nem persistente**. Se a prova exigir PUT, DELETE, banco ou autenticação, adapte ao enunciado.

## 3. Criar um teste automatizado mínimo

Crie `src/test/java/br/com/fiap/minitask/TarefaControllerTest.java`:

```java
package br.com.fiap.minitask;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class TarefaControllerTest {

    @Test
    void deveCadastrarEListarTarefa() {
        TarefaController controller = new TarefaController();
        var criada = controller.cadastrar(new TarefaController.CriarTarefa("Estudar DevOps"));
        assertEquals(1L, criada.id());
        assertEquals("PENDENTE", criada.status());
        assertEquals(1, controller.listar().size());
    }

    @Test
    void deveRejeitarTituloVazio() {
        TarefaController controller = new TarefaController();
        var erro = assertThrows(ResponseStatusException.class,
            () -> controller.cadastrar(new TarefaController.CriarTarefa(" ")));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
    }
}
```

O Initializr já fornece normalmente o Spring Boot Starter Test. Caso não esteja no `pom.xml`, adicione a dependência de testes compatível com a versão do Spring Boot escolhida.

Execute:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

Com a aplicação em execução, em **outro PowerShell**:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/tarefas

$body = @{ titulo = 'Estudar DevOps' } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/tarefas -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod http://localhost:8080/api/tarefas
```

O primeiro health check deve retornar `UP`. O POST retorna HTTP 201 e uma tarefa com `id`, `titulo` e `status`. Encerre o processo local com **Ctrl+C** antes de usar a porta 8080 com Docker.

## 4. Dockerfile e teste do container

Crie `Dockerfile` na raiz:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Crie `.dockerignore`:

```dockerignore
.git
.github
.idea
.vscode
*.md
```

> **Atenção:** não ignore `target/` neste Dockerfile: a imagem precisa copiar o JAR compilado. Primeiro execute o `package`, depois o `docker build`.

```powershell
.\mvnw.cmd clean package
docker build -t minitask:1.0 .
docker run --rm --name minitask-local -p 8080:8080 minitask:1.0
```

Em outro terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Encerre o container com **Ctrl+C**.

## 5. Versionar no GitHub

Crie um repositório **vazio** no GitHub, por exemplo `minitask-fiap`, preferencialmente **público** para o fluxo de imagem pública (se a prova permitir). Não marque README ou `.gitignore` na criação.

Crie `.gitignore` na raiz, se o Initializr não tiver gerado um:

```gitignore
.env
*.log
.idea/
.vscode/
target/
```

Confirme que nenhum token ou senha esteja versionado.

```powershell
cd C:\fiap26\minitask
git init
git branch -M main
git add .
git status --short
git commit -m "feat: cria MiniTask API com testes e Docker"
git remote add origin https://github.com/SEU_USUARIO/minitask-fiap.git
git push -u origin main
```

Se o repositório local já tiver Git configurado, **não repita** `git init` nem `git remote add origin`. Substitua `SEU_USUARIO` pelo nome real da sua conta.

## 6. Docker Hub — repositório e credenciais

No site https://hub.docker.com/ crie o repositório **público** `minitask-fiap`. Crie um **access token** adequado para publicar imagens.

No GitHub, abra o repositório → **Settings → Secrets and variables → Actions → New repository secret** e cadastre:

- `DOCKERHUB_USERNAME` = nome de usuário do Docker Hub;
- `DOCKERHUB_TOKEN` = token de acesso ao Docker Hub.

Nunca coloque o token diretamente no YAML ou no `README.md`.

Para confirmar que a construção local funciona, o comando opcional é:

```powershell
docker tag minitask:1.0 SEU_USUARIO_DOCKERHUB/minitask-fiap:latest
# Opcional: autentique e publique manualmente se necessário
# docker login -u SEU_USUARIO_DOCKERHUB
# docker push SEU_USUARIO_DOCKERHUB/minitask-fiap:latest
```

## 7. GitHub Actions — build, teste e push para Docker Hub

Crie `.github/workflows/ci-cd.yml`:

```yaml
name: MiniTask CI e Docker Hub

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
      - name: Testar e gerar JAR
        run: ./mvnw clean verify

  docker:
    if: github.event_name == 'push'
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Gerar JAR para imagem
        run: ./mvnw -DskipTests package
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.DOCKERHUB_USERNAME }}/minitask-fiap:latest
            ${{ secrets.DOCKERHUB_USERNAME }}/minitask-fiap:${{ github.sha }}
```

**Importante:** o comando `./mvnw` precisa ter permissão de execução no Linux. No Windows, antes de enviar o commit do workflow, rode:

```powershell
git update-index --chmod=+x mvnw
```

O workflow acima **automatiza build, testes e publicação da imagem**, mas **não atualiza automaticamente o Azure Web App**. Para o exercício rápido, publicaremos a imagem `latest` no Portal e acionaremos a atualização/reinicialização quando necessário. Se a avaliação exigir CD automático, será necessário acrescentar um job de deploy autenticado no Azure (descrito no PDF da FIAP).

Envie os arquivos:

```powershell
git add .github/workflows/ci-cd.yml mvnw
git commit -m "ci: adiciona testes e publicacao Docker Hub"
git push origin main
```

No GitHub → aba **Actions**, verifique ambos os jobs com resultado verde. Depois, confira se a tag `latest` está publicada em Docker Hub.

## 8. Criar Azure Web App pelo **Portal**

> **Atenção aos custos:** Azure App Service em plano B1 é cobrado enquanto o recurso/plano existe. Configure apenas para o teste e remova o Resource Group ao final. O plano Free F1 pode não atender a containers Linux; escolha um plano compatível e permitido pela assinatura.

Acesse https://portal.azure.com/ e siga:

1. **Resource groups → Create**: nome `rg-minitask-prova`, região `France Central` (ou outra permitida), **Review + create → Create**.
2. Na busca superior, digite **App Services** → **Create → Web App**.
3. Em **Basics**, selecione sua assinatura e o grupo `rg-minitask-prova`; nome globalmente único, por exemplo `minitask-fiap-SEU_IDENTIFICADOR`; **Publish: Container** (não `Code`, pois usaremos Docker Hub); sistema operacional **Linux**; região **France Central**. Escolha plano de preço suportado, como **Basic B1**, se disponível e dentro do orçamento.
4. Na aba **Container** (nome pode variar), escolha **Docker Hub** ou **Other container registry** e informe a imagem pública `SEU_USUARIO_DOCKERHUB/minitask-fiap:latest`. Selecione o modo de imagem único. Como o repositório é público, não é necessário incluir credenciais do Docker Hub no Azure.
5. Confirme os detalhes em **Review + create** e finalize com **Create**; depois clique **Go to resource**.
6. Em **Settings → Environment variables** (ou **Configuration**, conforme a interface), adicione a configuração de aplicativo `WEBSITES_PORT` com valor `8080` e salve. Isso informa a porta do container para o App Service, se exigido pelo tipo de implantação. Reinicie o Web App após salvar.
7. Veja a URL em **Overview → Default domain** (`https://NOME.azurewebsites.net`).

### Verificar

No PowerShell:

```powershell
$url = 'https://NOME.azurewebsites.net'
Invoke-RestMethod "$url/actuator/health" -TimeoutSec 120
Invoke-RestMethod "$url/api/tarefas" -TimeoutSec 120
$body = @{ titulo = 'Publicacao Azure FIAP' } | ConvertTo-Json
Invoke-RestMethod "$url/api/tarefas" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 120
Invoke-RestMethod "$url/api/tarefas" -TimeoutSec 120
```

A primeira inicialização pode demorar. Se não abrir, verifique **Log stream**, configuração da imagem, `WEBSITES_PORT`, tag publicada no Docker Hub, status do Web App e disponibilidade da região/plano.

### Se a prova exigir deploy automático no Azure

O PDF da FIAP demonstra GitHub Actions publicando imagens e usando **Azure Web App** como destino de deployment. O workflow acima é CI + publicação Docker; para CD automático real, será necessário configurar uma autenticação Azure segura, preferencialmente por identidade federada (OIDC), ou usar um método permitido pela prova. Não cole publish profiles, tokens ou senhas na documentação.

## 9. Evidências para a avaliação

Salve capturas de tela de:

- Projeto Spring Boot abrindo sem erros e endpoint `/actuator/health` respondendo `UP`;
- Teste automatizado executado com sucesso (`mvnw clean test`);
- Docker container funcionando (`docker ps` ou endpoint local);
- Repositório GitHub com código e Dockerfile;
- GitHub Actions com workflow verde;
- Docker Hub com imagem `minitask-fiap:latest`;
- Azure Portal com **App Service** em execução e nome do Resource Group;
- Endpoint HTTPS público e POST funcionando.

> Se a prova tiver critérios específicos (banco de dados, CRUD completo, pipeline Azure automático, testes adicionais, Kubernetes etc.), siga o enunciado. Este roteiro é deliberadamente um **projeto mínimo de treino**.

## 10. Limpar recursos para não consumir créditos

Após guardar evidências, no **Portal Azure**:

1. Abra **Resource groups → rg-minitask-prova**.
2. Clique **Delete resource group**.
3. Digite o nome do grupo para confirmar, confira os recursos e confirme a exclusão.
4. Verifique posteriormente se o Resource Group desapareceu.

Isso remove o Web App e seu plano quando estiverem dentro do grupo. Não remove arquivos locais, GitHub nem Docker Hub.

## 11. Diagnóstico rápido

| Sintoma | Conferir |
|---|---|
| Maven falha | `java -version`, rede, dependencies e resultado de `./mvnw test` |
| Docker não encontra JAR | Executar `mvnw clean package` **antes** de `docker build` |
| Porta 8080 ocupada | Encerrar Spring Boot local ou outro container |
| GitHub Actions: `Permission denied` | `git update-index --chmod=+x mvnw`, commit e push |
| Docker Hub: acesso negado | Secrets do GitHub, nome do usuário, token e repositório |
| Web App não inicia | Imagem/tag pública, Linux container, `WEBSITES_PORT=8080` e Log stream |
| Dados desaparecem após reinício | Comportamento esperado: armazenamento em memória |
| Criar Web App bloqueado | Região permitida pela assinatura, quota e preço do plano |

## 12. Checklist de prova

- [ ] Projeto Spring Boot criado com Java 21 e dependências corretas.
- [ ] API responde GET, POST e health check.
- [ ] Testes Maven passam.
- [ ] JAR e imagem Docker são construídos.
- [ ] Container local responde na porta 8080.
- [ ] GitHub contém o código e workflow.
- [ ] GitHub Actions executou com sucesso.
- [ ] Docker Hub contém imagem publicada.
- [ ] Azure Web App foi criado **pelo Portal** e a URL pública funciona.
- [ ] Evidências salvas.
- [ ] Recursos pagos excluídos depois da avaliação.

---

**Referência de estudo:** apostilas FIAP Fase 6 — *Deployment na Nuvem* (Azure Web App pelo Portal) e *Continuous Integration e Continuous Delivery* (GitHub Actions, Docker e deployment no Azure). Este projeto adapta o roteiro didático a uma API Java pequena, projetada para caber em um simulado de aproximadamente 90 minutos.
