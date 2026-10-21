# Republica el backend en ECS Fargate y reapunta el API Gateway a la tarea nueva.
#
# Uso (desde cualquier carpeta):  .\scripts\publicar-ecs.ps1
# Requisitos: credenciales AWS vigentes, Docker Desktop corriendo y terraform init hecho en infra/.
# No contiene credenciales: usa las de la AWS CLI.

$ErrorActionPreference = "Stop"

# Constantes del despliegue
$Region = "us-east-1"
$ApiId  = "dukv1s21ak"
$Tag    = "latest"   # el mismo tag que usa la task definition en infra/ecs.tf
$Jar    = "target\reservas-0.0.1-SNAPSHOT.jar"

# Detiene el script si el último comando nativo terminó con error
function Confirmar-Exito([string]$Paso) {
    if ($LASTEXITCODE -ne 0) {
        throw "Falló: $Paso (código de salida $LASTEXITCODE)"
    }
}

# Trabaja siempre desde la raíz del proyecto (donde están pom.xml y Dockerfile)
$Raiz = Split-Path -Parent $PSScriptRoot
Push-Location $Raiz

try {
    # ------------------------------------------------------------------
    Write-Host "[1/10] Leyendo outputs de Terraform..." -ForegroundColor Cyan
    $Repositorio = terraform -chdir="infra" output -raw ecr_repositorio
    Confirmar-Exito "terraform output ecr_repositorio"
    $Cluster = terraform -chdir="infra" output -raw ecs_cluster
    Confirmar-Exito "terraform output ecs_cluster"
    $Servicio = terraform -chdir="infra" output -raw ecs_servicio
    Confirmar-Exito "terraform output ecs_servicio"
    $IntegracionReservas = terraform -chdir="infra" output -raw integracion_reservas_id
    Confirmar-Exito "terraform output integracion_reservas_id"
    $IntegracionAprobaciones = terraform -chdir="infra" output -raw integracion_aprobaciones_id
    Confirmar-Exito "terraform output integracion_aprobaciones_id"

    $Imagen   = "${Repositorio}:${Tag}"
    $Registro = $Repositorio.Split("/")[0]
    Write-Host "       Imagen:  $Imagen"
    Write-Host "       Cluster: $Cluster  |  Servicio: $Servicio"

    # ------------------------------------------------------------------
    Write-Host "[2/10] Compilando backend y ejecutando tests (perfil test con H2)..." -ForegroundColor Cyan
    & .\mvnw.cmd clean package
    Confirmar-Exito "mvnw clean package"
    if (-not (Test-Path $Jar)) {
        throw "No se generó $Jar (el Dockerfile lo necesita)"
    }

    # ------------------------------------------------------------------
    Write-Host "[3/10] Construyendo imagen Docker para linux/amd64..." -ForegroundColor Cyan
    docker build --platform linux/amd64 -t $Imagen .
    Confirmar-Exito "docker build"

    # ------------------------------------------------------------------
    Write-Host "[4/10] Iniciando sesión en ECR ($Registro)..." -ForegroundColor Cyan
    # Se usa cmd /c porque el pipe de PowerShell corrompe la contraseña de docker login
    cmd /c "aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $Registro"
    Confirmar-Exito "docker login en ECR"

    # ------------------------------------------------------------------
    Write-Host "[5/10] Subiendo imagen a ECR..." -ForegroundColor Cyan
    docker push $Imagen
    Confirmar-Exito "docker push"

    # ------------------------------------------------------------------
    Write-Host "[6/10] Forzando un despliegue nuevo en ECS..." -ForegroundColor Cyan
    $Despliegue = aws ecs update-service --region $Region --cluster $Cluster --service $Servicio `
        --force-new-deployment --query "service.deployments[0].id" --output text
    Confirmar-Exito "aws ecs update-service"
    Write-Host "       Despliegue: $Despliegue"

    # ------------------------------------------------------------------
    Write-Host "[7/10] Esperando a que el servicio quede estable (puede tardar varios minutos)..." -ForegroundColor Cyan
    aws ecs wait services-stable --region $Region --cluster $Cluster --services $Servicio
    Confirmar-Exito "aws ecs wait services-stable"

    # ------------------------------------------------------------------
    Write-Host "[8/10] Obteniendo la IP pública de la tarea nueva..." -ForegroundColor Cyan
    $ArnsTareas = aws ecs list-tasks --region $Region --cluster $Cluster --service-name $Servicio `
        --desired-status RUNNING --query "taskArns" --output json | ConvertFrom-Json
    Confirmar-Exito "aws ecs list-tasks"
    if (-not $ArnsTareas) {
        throw "No hay tareas RUNNING en el servicio $Servicio"
    }

    # Si hubiera más de una tarea corriendo, se toma la que arrancó más tarde
    $Tareas = aws ecs describe-tasks --region $Region --cluster $Cluster --tasks $ArnsTareas `
        --output json | ConvertFrom-Json
    Confirmar-Exito "aws ecs describe-tasks"
    $Tarea = $Tareas.tasks | Sort-Object { [datetime]$_.startedAt } -Descending | Select-Object -First 1

    $Eni = ($Tarea.attachments[0].details | Where-Object { $_.name -eq "networkInterfaceId" }).value
    if (-not $Eni) {
        throw "La tarea no tiene interfaz de red (networkInterfaceId)"
    }

    $Ip = aws ec2 describe-network-interfaces --region $Region --network-interface-ids $Eni `
        --query "NetworkInterfaces[0].Association.PublicIp" --output text
    Confirmar-Exito "aws ec2 describe-network-interfaces"
    if (-not $Ip -or $Ip -eq "None") {
        throw "La interfaz $Eni no tiene IP pública"
    }
    Write-Host "       IP nueva: $Ip"

    # ------------------------------------------------------------------
    Write-Host "[9/10] Reapuntando las integraciones del API Gateway..." -ForegroundColor Cyan
    $UriReservas = aws apigatewayv2 update-integration --region $Region --api-id $ApiId `
        --integration-id $IntegracionReservas --integration-uri "http://${Ip}:8080/v1/{proxy}" `
        --query "IntegrationUri" --output text
    Confirmar-Exito "update-integration reservas"

    $UriAprobaciones = aws apigatewayv2 update-integration --region $Region --api-id $ApiId `
        --integration-id $IntegracionAprobaciones --integration-uri "http://${Ip}:8080/v1/aprobaciones/{proxy}" `
        --query "IntegrationUri" --output text
    Confirmar-Exito "update-integration aprobaciones"

    # ------------------------------------------------------------------
    Write-Host "[10/10] Probando el health check del backend..." -ForegroundColor Cyan
    $Salud = Invoke-RestMethod -Uri "http://${Ip}:8080/actuator/health" -TimeoutSec 15
    Write-Host "       /actuator/health -> $($Salud | ConvertTo-Json -Compress)"

    # ------------------------------------------------------------------
    Write-Host ""
    Write-Host "Despliegue terminado" -ForegroundColor Green
    Write-Host "  Tag publicado:            $Imagen"
    Write-Host "  IP nueva:                 $Ip"
    Write-Host "  Integración reservas:     $UriReservas"
    Write-Host "  Integración aprobaciones: $UriAprobaciones"
}
finally {
    Pop-Location
}
