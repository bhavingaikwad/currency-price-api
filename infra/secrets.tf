resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "reader" {
  length  = 20
  special = false
}

resource "random_password" "admin" {
  length  = 20
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/currency-api/POSTGRES_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/currency-api/JWT_SECRET"
  type  = "SecureString"
  value = random_password.jwt.result
}

resource "aws_ssm_parameter" "reader_password" {
  name  = "/currency-api/READER_PASSWORD"
  type  = "SecureString"
  value = random_password.reader.result
}

resource "aws_ssm_parameter" "admin_password" {
  name  = "/currency-api/ADMIN_PASSWORD"
  type  = "SecureString"
  value = random_password.admin.result
}

output "reader_password" {
  value     = random_password.reader.result
  sensitive = true
}

output "admin_password" {
  value     = random_password.admin.result
  sensitive = true
}
