variable "region" {
  type    = string
  default = "us-east-2"
}

variable "aws_profile" {
  type    = string
  default = "currency-api"
}

variable "image_tag" {
  type    = string
  default = "7414bc8"
}

variable "alert_email" {
  description = "Email for alarm notifications. Leave null to skip the subscription."
  type        = string
  default     = null
}
