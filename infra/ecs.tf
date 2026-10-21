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
