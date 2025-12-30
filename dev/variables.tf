variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "s3-batch-operations"
}

variable "instance_type" {
  description = "EC2 instance type for Jenkins"
  type        = string
  default     = "t3.medium"
}

variable "create_key_pair" {
  description = "Whether to create a new key pair. If false, use existing key_name"
  type        = bool
  default     = true
}

variable "key_name" {
  description = "Name of existing AWS key pair to use (only if create_key_pair is false)"
  type        = string
  default     = null
}

variable "save_private_key" {
  description = "Whether to save the private key to a local file"
  type        = bool
  default     = false
}

variable "private_key_path" {
  description = "Path to save the private key file"
  type        = string
  default     = "."
}

variable "vpc_cidr_block" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.20.0/24"]
}

variable "availability_zones" {
  description = "Availability zones for subnets. If empty, will use available AZs"
  type        = list(string)
  default     = []
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway for private subnets"
  type        = bool
  default     = true
}

variable "use_private_subnet" {
  description = "Whether to deploy Jenkins in a private subnet"
  type        = bool
  default     = false
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access Jenkins"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "allowed_all_cidr_blocks" {
  description = "CIDR blocks allowed to access the allow bucket"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "volume_type" {
  description = "Type of EBS volume"
  type        = string
  default     = "gp3"
}

variable "volume_size" {
  description = "Size of EBS volume in GB"
  type        = number
  default     = 30
}

variable "encrypt_volume" {
  description = "Whether to encrypt the root volume"
  type        = bool
  default     = true
}

variable "user_data" {
  description = "User data script to run on instance launch"
  type        = string
  default     = null
}


variable "s3_enable_versioning" {
  description = "Enable versioning on the S3 bucket"
  type        = bool
  default     = false
}

variable "s3_encryption_algorithm" {
  description = "Server-side encryption algorithm for S3"
  type        = string
  default     = "AES256"
}

variable "s3_bucket_key_enabled" {
  description = "Enable bucket key for S3 bucket encryption"
  type        = bool
  default     = false
}

variable "s3_block_public_acls" {
  description = "Block public ACLs on S3 bucket"
  type        = bool
  default     = true
}

variable "s3_block_public_policy" {
  description = "Block public policy on S3 bucket"
  type        = bool
  default     = true
}

variable "s3_ignore_public_acls" {
  description = "Ignore public ACLs on S3 bucket"
  type        = bool
  default     = true
}

variable "s3_restrict_public_buckets" {
  description = "Restrict public buckets on S3"
  type        = bool
  default     = true
}

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {}
}

