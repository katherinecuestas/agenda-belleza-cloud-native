# Firewall de la task de ECS. El 8080 queda abierto: sin balanceador, quien llama
# es el API Gateway por internet. La única puerta real es el Gateway (guía 1.3.9).
resource "aws_security_group" "tarea" {
  name        = "agenda-belleza-tarea"
  description = "Backend Spring Boot en ECS"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Salida libre: la task necesita bajar su imagen de ECR
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Firewall de RDS: el 5432 SOLO acepta conexiones desde la task. Nadie más.
resource "aws_security_group" "rds" {
  name        = "agenda-belleza-rds"
  description = "PostgreSQL solo accesible desde el backend"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.tarea.id]
  }
}
