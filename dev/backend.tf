terraform {
  backend "s3" { 
    bucket         = "s3-batch-operations-terraform-state"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

