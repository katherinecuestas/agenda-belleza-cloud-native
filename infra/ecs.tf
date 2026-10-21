# Dónde vive la imagen del backend
resource "aws_ecr_repository" "backend" {
  name         = "agenda-belleza-backend"
  force_delete = true # terraform destroy no falla aunque queden imágenes dentro

  image_scanning_configuration {
    scan_on_push = false # en el lab no aporta y agrega ruido
  }
}

# Dónde va el stdout del contenedor: la primera herramienta cuando la task falla
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/agenda-belleza-backend"
  retention_in_days = 7
}

output "ecr_repositorio" {
  description = "URL del repositorio ECR: aquí se sube la imagen"
  value       = aws_ecr_repository.backend.repository_url
}

# Tu número de cuenta, para armar el ARN de LabRole
data "aws_caller_identity" "actual" {}

resource "aws_ecs_cluster" "backend" {
  name = "agenda-belleza"
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "agenda-belleza-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512 # Fargate solo acepta combinaciones concretas: 512/1024 es válida
  memory                   = 1024

  # Academy no permite crear roles: se usa LabRole, que ya existe.
  # Es el que baja la imagen de ECR y escribe en CloudWatch.
  execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/LabRole"

  # Fargate es x86_64. Tu imagen se construyó con --platform linux/amd64: se mueven juntos.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "backend"
    image        = "${aws_ecr_repository.backend.repository_url}:latest"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    # Las mismas variables que usaste en docker run, ahora apuntando a RDS.
    # sslmode=require: RDS rechaza las conexiones sin cifrar.
    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/db_belleza_reserva?sslmode=require" },
      { name = "DB_USER", value = aws_db_instance.postgres.username },
      { name = "DB_PASSWORD", value = var.db_password }
    ]

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60 # Spring Boot tardó 21 s en tu PC: 60 s deja margen
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = "backend"
      }
    }
  }])
}

resource "aws_ecs_service" "backend" {
  name            = "agenda-belleza-backend"
  cluster         = aws_ecs_cluster.backend.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.publicas.ids
    security_groups  = [aws_security_group.tarea.id]
    assign_public_ip = true # para bajar la imagen Y para que el Gateway la alcance
  }

  wait_for_steady_state = false

  # Trampa nº2 de la guía: el despliegue decide qué revisión corre, no Terraform.
  lifecycle {
    ignore_changes = [task_definition]
  }
}

output "ecs_cluster" {
  value = aws_ecs_cluster.backend.name
}

output "ecs_servicio" {
  value = aws_ecs_service.backend.name
}
