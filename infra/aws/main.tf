terraform {
  required_version = ">= 1.6.0, < 2.0.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}
provider "aws" { region = var.region }
variable "region" {
  type = string
  default = "ap-south-1"
}
locals { services = toset(["gateway", "payment", "account", "ledger", "fraud", "notification"]) }
resource "aws_ecr_repository" "service" {
  for_each = local.services
  name = "bank/${each.key}-service"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
  tags = { Project = "banking-ledger" }
}
output "repositories" { value = { for name, repo in aws_ecr_repository.service : name => repo.repository_url } }
