variable "db_password" {
  description = "Contraseña del usuario administrador de RDS PostgreSQL"
  type        = string
  sensitive   = true
}
