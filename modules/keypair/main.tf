terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

resource "tls_private_key" "this" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "this" {
  key_name   = var.key_name
  public_key = tls_private_key.this.public_key_openssh

  tags = var.tags
}

resource "local_file" "private_key" {
  count = var.save_private_key ? 1 : 0

  content         = tls_private_key.this.private_key_pem
  filename        = "${var.private_key_path}/${var.key_name}.pem"
  file_permission = "0400"
}

