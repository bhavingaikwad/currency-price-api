data "aws_caller_identity" "me" {}

output "who_am_i" {
  value = data.aws_caller_identity.me.arn
}
