resource "aws_ecr_repository" "app" {
  name                 = "currency-price-api"
  force_delete         = true
  image_tag_mutability = "MUTABLE"
}

data "aws_ecr_authorization_token" "token" {}

output "ecr_repo_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecr_registry" {
  value = split("/", aws_ecr_repository.app.repository_url)[0]
}

output "ecr_password" {
  value     = data.aws_ecr_authorization_token.token.password
  sensitive = true
}
