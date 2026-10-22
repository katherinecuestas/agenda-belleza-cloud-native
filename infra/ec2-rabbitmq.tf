# EC2 con docker compose: cluster RabbitMQ de 3 nodos + ms-notificaciones + ms-rabbit-admin.
# Es la misma composición que en local (rabbitmq/docker-compose.yml + docker-compose.cloud.yml).
#
# Academy no permite crear roles: se usa el instance profile LabInstanceProfile y el key pair vockey.

locals {
  repo_url = "https://github.com/katherinecuestas/agenda-belleza-cloud-native.git"

  # Contenido de rabbitmq/.env en la EC2. Viaja en base64 dentro del user_data para que ningún
  # carácter de la clave rompa el script. Los valores salen de terraform.tfvars (fuera de git).
  rabbitmq_env = <<-EOT
    RABBITMQ_USER=${var.rabbitmq_user}
    RABBITMQ_PASS=${var.rabbitmq_pass}
    RABBITMQ_COOKIE=${var.rabbitmq_cookie}
    COGNITO_ISSUER=https://${aws_cognito_user_pool.pool.endpoint}
    COGNITO_CLIENT_ID=${aws_cognito_user_pool_client.spa.id}
  EOT
}

# Amazon Linux 2023, x86_64, la más reciente
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Firewall de la EC2. Todo el cluster corre en la misma máquina: los nodos se hablan por la red
# interna de Docker, así que no hace falta abrir puertos entre miembros del grupo.
resource "aws_security_group" "rabbitmq" {
  name        = "agenda-belleza-rabbitmq"
  description = "Cluster RabbitMQ y microservicios en EC2"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "AMQP de los 3 nodos, solo desde las tasks de ECS"
    from_port       = 5672
    to_port         = 5674
    protocol        = "tcp"
    security_groups = [aws_security_group.tarea.id]
  }

  ingress {
    description = "Panel de RabbitMQ de los 3 nodos, solo desde mi IP"
    from_port   = 15672
    to_port     = 15674
    protocol    = "tcp"
    cidr_blocks = [var.mi_ip]
  }

  ingress {
    description = "SSH solo desde mi IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.mi_ip]
  }

  ingress {
    description = "ms-rabbit-admin, protegido con el JWT de Cognito"
    from_port   = 8082
    to_port     = 8082
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Salida libre: dnf, GitHub, Docker Hub, Maven Central y Cognito
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "agenda-belleza-rabbitmq"
  }
}

resource "aws_instance" "rabbitmq" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = "t3.medium" # 4 GB: 3 nodos RabbitMQ + 2 JVM + el build con Maven
  subnet_id                   = data.aws_subnets.publicas.ids[0]
  vpc_security_group_ids      = [aws_security_group.rabbitmq.id]
  associate_public_ip_address = true # salida a internet mientras se asocia la EIP
  iam_instance_profile        = "LabInstanceProfile"
  key_name                    = "vockey"

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  # IMDSv2: los metadatos de la instancia solo se leen con token de sesión
  metadata_options {
    http_tokens = "required"
  }

  user_data = templatefile("${path.module}/user-data-rabbitmq.sh.tftpl", {
    repo_url  = local.repo_url
    rama_repo = var.rama_repo
    env_b64   = base64encode(local.rabbitmq_env)
  })
  # El user_data solo corre en el primer arranque: si cambia (otra rama, otra clave),
  # se recrea la instancia para aplicarlo desde cero.
  user_data_replace_on_change = true

  tags = {
    Name = "agenda-belleza-rabbitmq"
  }

  lifecycle {
    # Que salga una AMI nueva no debe recrear la instancia en el próximo apply
    ignore_changes = [ami]
  }
}

# IP fija: la IP pública automática cambia cada vez que el lab detiene y enciende la instancia
resource "aws_eip" "rabbitmq" {
  domain   = "vpc"
  instance = aws_instance.rabbitmq.id

  tags = {
    Name = "agenda-belleza-rabbitmq"
  }
}

output "rabbitmq_ip_publica" {
  description = "IP fija (EIP) de la EC2 de RabbitMQ"
  value       = aws_eip.rabbitmq.public_ip
}

output "rabbitmq_ip_privada" {
  description = "IP privada de la EC2: la usan las tasks de ECS dentro de la VPC"
  value       = aws_instance.rabbitmq.private_ip
}

output "rabbitmq_addresses_privadas" {
  description = "Valor de RABBITMQ_ADDRESSES para los microservicios en ECS (los 3 nodos)"
  value       = join(",", [for puerto in [5672, 5673, 5674] : "${aws_instance.rabbitmq.private_ip}:${puerto}"])
}

output "url_panel_rabbitmq" {
  description = "Panel de RabbitMQ (solo accesible desde mi_ip)"
  value       = "http://${aws_eip.rabbitmq.public_ip}:15672"
}

output "comando_ssh" {
  description = "SSH con la llave de Academy (AWS Details -> Download PEM, guardada como labsuser.pem)"
  value       = "ssh -i labsuser.pem ec2-user@${aws_eip.rabbitmq.public_ip}"
}
