variable "role_name" {
  description = "Name of the IAM role for Jenkins"
  type        = string
  default     = "jenkins-role"
}

variable "instance_profile_name" {
  description = "Name of the IAM instance profile"
  type        = string
  default     = "jenkins-instance-profile"
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

