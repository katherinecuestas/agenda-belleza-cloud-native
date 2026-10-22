# Pipelines de GitHub Actions · Agenda Belleza

El repositorio tiene 6 workflows en `.github/workflows/`: cuatro de **build** (prueban y empaquetan) y dos de **deploy** (publican en AWS). Cada deploy se dispara solo cuando su build termina bien en `main`.

```text
push a main (frontend/**)          →  Front · Build             →  Front · Deploy (Amplify)
push a cualquier rama (src/**, …)  →  Back · Build              →  Back · Deploy (ECS)  (solo si es main)
push a cualquier rama (ms-…/**)    →  MS Notificaciones · Build    (sin deploy: los levanta la EC2)
                                   →  MS Rabbit Admin · Build
```

Los pull requests a `main` ejecutan solo los builds: nunca despliegan.

Terraform **no** corre en los pipelines: la infraestructura tiene estado local (`infra/terraform.tfstate`) y se aplica a mano. Los pipelines solo publican código sobre esa infraestructura.

---

## Qué hace cada pipeline

### Front · Build — `front-build.yml`
- **Se dispara con:** push o pull request a `main` que cambie `frontend/**` o el propio workflow; también a mano (*Run workflow*).
- **Pasos:** Node 22 con caché de npm → `npm ci` → `ng test --watch=false` → `ng build`.
- Los tests usan Vitest con jsdom: corren sin navegador, así que funcionan en CI.
- **Resultado:** el artifact `front-dist`, con el contenido de `frontend/dist/frontend/browser`. Se guarda 7 días.

### Front · Deploy (Amplify) — `front-deploy.yml`
- **Se dispara con:** "Front · Build" terminado con éxito por un push a `main`; también a mano.
- **Pasos:**
  1. Descarga el artifact `front-dist` del build que lo disparó. En una ejecución manual, compila desde `main`.
  2. Escribe `config.json` desde la variable `CONFIG_JSON_AMPLIFY`. El archivo no está en el repo, y el paso valida que el JSON tenga las 5 claves.
  3. Comprime el **contenido** del build, con `index.html` en la raíz del zip.
  4. Ejecuta `aws amplify create-deployment`, luego `curl -T` del zip a la URL prefirmada (enmascarada en el log) y después `start-deployment`.
  5. Consulta el estado cada 10 segundos hasta `SUCCEED`, o falla si llega a `FAILED`, `CANCELLED` o pasan 15 minutos.
- **Resumen del run:** app, job, estado y URL publicada.

### Back · Build — `back-build.yml`
- **Se dispara con:** push a **cualquier rama**, o pull request a `main`, que cambie `src/**`, `pom.xml`, `Dockerfile`, `mvnw`, `.mvn/**` o el propio workflow; también a mano. Solo un build exitoso de un push a `main` dispara el deploy: en otras ramas solo compila y prueba.
- **Pasos:** Java 21 Temurin con caché de Maven → `chmod +x mvnw` → `./mvnw -B verify`.
- Los tests usan el perfil `test` con H2 en memoria, así que no necesitan PostgreSQL.
- **Resultado:** el artifact `back-jar`, con `reservas-0.0.1-SNAPSHOT.jar`. Se guarda 7 días.

### Back · Deploy (ECS) — `back-deploy.yml`
Hace lo mismo que `scripts/publicar-ecs.ps1`:

1. Usa el jar del build que lo disparó. En una ejecución manual, ejecuta `./mvnw package` con tests.
2. Inicia sesión en ECR con `aws-actions/amazon-ecr-login`.
3. Ejecuta `docker build --platform linux/amd64` y sube la imagen con el tag `:latest`, que es el que usa la task definition. También la sube con un tag del commit (12 caracteres), para saber qué versión corre.
4. Ejecuta `aws ecs update-service --task-definition <ECS_TASK_FAMILY> --force-new-deployment`. Al pasar la familia sin número de revisión, ECS usa la última revisión ACTIVE, que es la que registra Terraform.
5. Ejecuta `aws ecs wait services-stable`, que espera como máximo unos 10 minutos.
6. Obtiene la IP pública de la tarea nueva: `list-tasks` → `describe-tasks` → `networkInterfaceId` → `describe-network-interfaces`.
7. Ejecuta `update-integration` en las dos integraciones del API Gateway: `…/v1/{proxy}` y `…/v1/aprobaciones/{proxy}`.
8. Llama a `GET http://<ip>:8080/actuator/health`, con hasta 5 reintentos.

**Resumen del run:** imagen, IP nueva, las dos `IntegrationUri` y el resultado del health check.

### MS Notificaciones · Build — `ms-notificaciones-build.yml`
- **Se dispara con:** push a **cualquier rama**, o pull request a `main`, que cambie `ms-notificaciones/**` o el propio workflow; también a mano.
- **Pasos** (dentro de `ms-notificaciones/`): Java 21 Temurin con caché de Maven → `chmod +x mvnw` → `./mvnw -B verify` → `docker build` de la imagen. El `docker build` es **sin push**: solo comprueba que el Dockerfile multi-stage funciona.
- Los tests no necesitan RabbitMQ: los listeners no arrancan y la conexión AMQP se abre recién al usarla.
- **Resultado:** el artifact `ms-notificaciones-jar`, con `ms-notificaciones-0.0.1-SNAPSHOT.jar`. Se guarda 7 días.

### MS Rabbit Admin · Build — `ms-rabbit-admin-build.yml`
- **Se dispara con:** push a **cualquier rama**, o pull request a `main`, que cambie `ms-rabbit-admin/**` o el propio workflow; también a mano.
- **Pasos** (dentro de `ms-rabbit-admin/`): los mismos que MS Notificaciones · Build.
- Los tests no necesitan RabbitMQ ni Cognito: el token se simula con `jwt()` de spring-security-test, y el 503 se prueba contra un puerto sin broker.
- **Resultado:** el artifact `ms-rabbit-admin-jar`, con `ms-rabbit-admin-0.0.1-SNAPSHOT.jar`. Se guarda 7 días.

**Estos dos no tienen deploy en GitHub Actions.** En la nube los levanta la EC2 con `docker compose -f docker-compose.yml -f docker-compose.cloud.yml up -d --build` (`infra/ec2-rabbitmq.tf`), que construye las imágenes en la propia máquina a partir de la rama `rama_repo`. Los builds de CI aseguran que esa construcción no va a fallar.

### Reglas comunes
- **Credenciales:** solo por *secrets*. Los ids van solo en *variables* del repositorio (`vars.*`); no hay nada escrito a mano en los workflows.
- **Permisos mínimos:** `contents: read`. Los deploys tienen además `actions: read`, para descargar el artifact de otro run.
- **`concurrency`:** los builds de la misma rama cancelan el anterior. Los deploys se encolan y nunca se cancelan a mitad de camino.
- **Versiones de las actions:** fijas por versión mayor (`@v4`, `@v2`).

---

## Secrets y variables que hay que crear

Se crean en GitHub, en **Settings → Secrets and variables → Actions**.

### Secrets (pestaña *Secrets*)

| Nombre | Valor | Dónde se obtiene |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | *(secreto, cambia en cada sesión del lab)* | AWS Academy → *AWS Details* → *AWS CLI* |
| `AWS_SECRET_ACCESS_KEY` | *(secreto, cambia en cada sesión del lab)* | ídem |
| `AWS_SESSION_TOKEN` | *(secreto, cambia en cada sesión del lab)* | ídem |

### Variables (pestaña *Variables*)

Son los valores actuales, sacados de `terraform -chdir="infra" output`. No son secretos.

| Nombre | Valor | Origen |
|---|---|---|
| `AMPLIFY_APP_ID` | `d1iar2rl9h1cqx` | output `amplify_app_id` |
| `CONFIG_JSON_AMPLIFY` | `{"apiUrl":"https://dukv1s21ak.execute-api.us-east-1.amazonaws.com","clientId":"1ofks8eshs50n011tmq7u44qes","cognitoDomain":"https://agenda-belleza-katherine.auth.us-east-1.amazoncognito.com","redirectUri":"https://main.d1iar2rl9h1cqx.amplifyapp.com/","region":"us-east-1"}` | output `config_frontend_amplify` |
| `ECR_REPOSITORY` | `agenda-belleza-backend` | nombre del repositorio en `ecr_repositorio` (sin el registro; el registro lo entrega `amazon-ecr-login`) |
| `ECS_CLUSTER` | `agenda-belleza` | output `ecs_cluster` |
| `ECS_SERVICE` | `agenda-belleza-backend` | output `ecs_servicio` |
| `ECS_TASK_FAMILY` | `agenda-belleza-backend` | output `ecs_task_family` |
| `API_ID` | `dukv1s21ak` | id del API Gateway (aparece en `url_datos`) |
| `INTEGRACION_RESERVAS_ID` | `2qnc4m8` | output `integracion_reservas_id` |
| `INTEGRACION_APROBACIONES_ID` | `qyeelje` | output `integracion_aprobaciones_id` |

Si la infraestructura se destruye y se vuelve a crear con `terraform destroy` y `apply`, los ids cambian. En ese caso hay que volver a cargar las variables.

### Crear las variables con GitHub CLI

Desde la raíz del proyecto, en PowerShell y con `gh auth login` ya hecho:

```powershell
$repo = "katherinecuestas/agenda-belleza-cloud-native"
gh variable set AMPLIFY_APP_ID              -R $repo --body (terraform -chdir="infra" output -raw amplify_app_id)
gh variable set ECS_CLUSTER                 -R $repo --body (terraform -chdir="infra" output -raw ecs_cluster)
gh variable set ECS_SERVICE                 -R $repo --body (terraform -chdir="infra" output -raw ecs_servicio)
gh variable set ECS_TASK_FAMILY             -R $repo --body (terraform -chdir="infra" output -raw ecs_task_family)
gh variable set INTEGRACION_RESERVAS_ID     -R $repo --body (terraform -chdir="infra" output -raw integracion_reservas_id)
gh variable set INTEGRACION_APROBACIONES_ID -R $repo --body (terraform -chdir="infra" output -raw integracion_aprobaciones_id)
gh variable set ECR_REPOSITORY              -R $repo --body "agenda-belleza-backend"
gh variable set API_ID                      -R $repo --body "dukv1s21ak"

# El JSON va por stdin: como argumento, PowerShell 5.1 le quita las comillas
terraform -chdir="infra" output -raw config_frontend_amplify | gh variable set CONFIG_JSON_AMPLIFY -R $repo
```

---

## Renovar los secrets cada vez que se reinicia el lab

Las credenciales de AWS Academy son **temporales**: duran lo que dura la sesión del lab, unas 4 horas. Cuando la sesión vence, los deploys fallan en *Configurar credenciales AWS* o con `ExpiredToken`. Los builds no usan AWS y siguen funcionando.

Cada vez que inicies el lab:

1. En AWS Academy, pulsa **Start Lab** y espera a que el indicador esté verde.
2. Abre **AWS Details → AWS CLI → Show** y copia el bloque a `~/.aws/credentials` (perfil `[default]`).
3. Comprueba que funcionen:
   ```powershell
   aws sts get-caller-identity
   ```
4. Sube las tres credenciales a GitHub. Se leen de la AWS CLI, así que no hay que pegarlas en la terminal ni quedan en el historial:
   ```powershell
   $repo = "katherinecuestas/agenda-belleza-cloud-native"
   gh secret set AWS_ACCESS_KEY_ID     -R $repo --body (aws configure get aws_access_key_id)
   gh secret set AWS_SECRET_ACCESS_KEY -R $repo --body (aws configure get aws_secret_access_key)
   gh secret set AWS_SESSION_TOKEN     -R $repo --body (aws configure get aws_session_token)
   ```
   Se usa `--body (...)` y no un pipe porque, en Windows PowerShell 5.1, el pipe hacia un programa externo agrega un salto de línea al final, y un secret con ese salto extra hace fallar las credenciales.
   Sin `gh`, se hace a mano en **Settings → Secrets and variables → Actions → Secrets**, editando cada uno con *Update*.
5. Si un deploy falló por credenciales vencidas, vuelve a ejecutarlo desde **Actions → (el workflow) → Run workflow**, o con *Re-run jobs* en el run fallido.

Si además reiniciaste la tarea de ECS a mano, la IP del backend cambió. Ejecutar **Back · Deploy (ECS)** a mano vuelve a apuntar el API Gateway a la IP nueva.

---

## Notas

- **Los deploys solo se encadenan desde `main`.** GitHub solo dispara `workflow_run` con workflows que ya están en la rama por defecto. Mientras los archivos no estén en `main`, los deploys no se ejecutan solos.
- **La primera vez:** después de subir los workflows a `main` y crear los secrets y las variables, conviene ejecutar cada deploy a mano una vez (*Run workflow*) para comprobarlos.
- **`config.json` está en `.gitignore`.** El front publicado lo recibe desde la variable `CONFIG_JSON_AMPLIFY`; para desarrollo local se usa `frontend/public/config.json` con el output `config_frontend`.
- **El despliegue manual sigue funcionando.** `scripts/publicar-ecs.ps1` hace lo mismo que **Back · Deploy (ECS)**, desde la máquina local.
