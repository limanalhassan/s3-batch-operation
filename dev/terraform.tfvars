# AWS Configuration
aws_region = "us-east-1"

# Project Configuration
project_name = "s3-batch-operations-liman"

# VPC Configuration
vpc_cidr_block      = "10.0.0.0/16"
public_subnet_cidrs = ["10.0.1.0/24", "10.0.2.0/24"]
private_subnet_cidrs = ["10.0.10.0/24", "10.0.20.0/24"]
enable_nat_gateway  = true
use_private_subnet  = false  # Set to true to deploy Jenkins in private subnet

# Key Pair Configuration
create_key_pair  = true  # Set to false to use existing key pair
key_name         = null  # Only needed if create_key_pair is false
save_private_key = true  # Set to true to save private key locally
private_key_path = "."   # Path to save private key if save_private_key is true

# EC2 Configuration
instance_type = "t3.micro"  # Free Tier eligible instance type

# Security Configuration
allowed_cidr_blocks = ["50.72.113.242/32", "102.91.4.158/32"]  # Your public IP address
allowed_all_cidr_blocks = ["0.0.0.0/0"]

# Storage Configuration
volume_type   = "gp3"
volume_size   = 16
encrypt_volume = true

# S3 Configuration
# Two buckets will be created: {project}-{env}-source and {project}-{env}-destination
# Both buckets will have tags: operation=s3BatchOperations, env={workspace}, managedBy=Terraform
# Source bucket will have Target=Source tag, Destination bucket will have Target=Destination tag
s3_enable_versioning    = false
s3_encryption_algorithm = "AES256"
s3_bucket_key_enabled   = false

# Common Tags
common_tags = {
  Owner       = "DevOps Team"
  CostCenter  = "Engineering"
  Application = "Jenkins"
}

