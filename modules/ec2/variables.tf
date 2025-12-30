variable "instance_name" {
  description = "Name tag for the Jenkins EC2 instance"
  type        = string
  default     = "jenkins-server"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.medium"
}

variable "ami_id" {
  description = "AMI ID for the instance. If not provided, will use latest Amazon Linux 2"
  type        = string
  default     = null
}

variable "key_name" {
  description = "Name of the AWS key pair to use for SSH access"
  type        = string
  default     = null
}

variable "vpc_id" {
  description = "VPC ID where the instance will be launched"
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID where the instance will be launched"
  type        = string
}

variable "iam_instance_profile_name" {
  description = "Name of the IAM instance profile to attach to the EC2 instance"
  type        = string
}

variable "security_group_ids" {
  description = "List of security group IDs to attach to the instance"
  type        = list(string)
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

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

