variable "db_password" {
  description = "Contraseña del usuario administrador de RDS PostgreSQL"
  type        = string
  sensitive   = true
}

# ───────── EC2 con el cluster RabbitMQ (ec2-rabbitmq.tf) ─────────

variable "rabbitmq_user" {
  description = "Usuario administrador de RabbitMQ (se crea en el primer arranque del cluster)"
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]{3,64}$", var.rabbitmq_user))
    error_message = "rabbitmq_user: 3 a 64 caracteres, solo letras, números, punto, guion o guion bajo."
  }
}

variable "rabbitmq_pass" {
  description = "Clave del usuario de RabbitMQ"
  type        = string
  sensitive   = true

  # Va a un archivo .env: sin espacios, comillas ni #, que ese formato interpreta
  validation {
    condition     = can(regex("^[^\\s#'\"]{12,128}$", var.rabbitmq_pass))
    error_message = "rabbitmq_pass: 12 a 128 caracteres, sin espacios, comillas ni #."
  }
}

variable "rabbitmq_cookie" {
  description = "Cookie de Erlang: la misma en los 3 nodos para que formen cluster"
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[A-Za-z0-9]{20,255}$", var.rabbitmq_cookie))
    error_message = "rabbitmq_cookie: 20 a 255 letras o números (por ejemplo: openssl rand -hex 32)."
  }
}

variable "mi_ip" {
  description = "Tu IP pública en formato CIDR /32: la única que puede entrar por SSH y al panel de RabbitMQ"
  type        = string

  validation {
    condition     = can(cidrhost(var.mi_ip, 0)) && endswith(var.mi_ip, "/32")
    error_message = "mi_ip debe ser una IP con /32, por ejemplo 203.0.113.10/32."
  }
}

variable "rama_repo" {
  description = "Rama del repositorio que clona la EC2"
  type        = string
  default     = "parcial2-rabbitmq"
}
