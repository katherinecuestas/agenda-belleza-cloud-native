resource "aws_db_instance" "postgres" {
  identifier        = "agenda-belleza-db"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = "db.t3.micro"
  allocated_storage = 20

  db_name  = "db_belleza_reserva"
  username = "reservas_admin"
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # Laboratorio: se puede borrar sin pasos extra
  skip_final_snapshot = true
  deletion_protection = false
}
