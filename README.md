# MiniTask API — FIAP DevOps

API REST mínima com Java 21, Spring Boot, Maven, Docker e GitHub Actions, preparada para deploy **pelo Portal do Azure (App Service / Web App)**.

## Começar no Windows (PowerShell)

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

Em outro terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/tarefas
$body = @{ titulo = 'Estudar DevOps' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/tarefas -Method Post -ContentType 'application/json' -Body $body
```

## Docker

```powershell
.\mvnw.cmd clean package
docker build -t minitask:1.0 .
docker run --rm -p 8080:8080 minitask:1.0
```

## CI e nuvem

Configure `DOCKERHUB_USERNAME` e `DOCKERHUB_TOKEN` nos secrets do GitHub para ativar a publicação da imagem no Docker Hub após um push na `main`.

O workflow publica a imagem Docker; **não implanta automaticamente no Azure**. Para isso, siga o `GUIA-PROVA.md` e crie um **Web App Linux Container** pelo Portal. A imagem no Docker Hub precisa ser pública para seguir o guia sem credenciais Azure.

**Observação:** dados armazenados apenas em memória; não persistem após reinício. Dependendo do enunciado da prova, podem ser necessários CRUD completo, banco de dados ou CD automático.
