terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_iam_role" "this" {
  name = var.role_name

  assume_role_policy = var.assume_role_policy

  tags = var.tags
}

resource "aws_iam_role_policy" "this" {
  count = var.policy_file != null || var.policy_template_file != null ? 1 : 0
  
  name = var.policy_name
  role = aws_iam_role.this.id

  policy = var.policy_template_file != null ? (
    templatefile("${path.module}/policy/${var.policy_template_file}", var.policy_template_vars)
  ) : (
    file("${path.module}/policy/${var.policy_file}")
  )
}

resource "aws_iam_instance_profile" "this" {
  count = var.create_instance_profile ? 1 : 0
  
  name = var.instance_profile_name != null ? var.instance_profile_name : "${var.role_name}-profile"
  role = aws_iam_role.this.name

  tags = var.tags
}

