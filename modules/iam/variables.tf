variable "role_name" {
  description = "Name of the IAM role"
  type        = string
}

variable "assume_role_policy" {
  description = "Assume role policy document (JSON string or file path)"
  type        = string
}

variable "policy_file" {
  description = "Path to IAM policy file (relative to modules/iam/policy/)"
  type        = string
  default     = null
}

variable "policy_template_file" {
  description = "Path to IAM policy template file (relative to modules/iam/policy/)"
  type        = string
  default     = null
}

variable "policy_template_vars" {
  description = "Variables for policy template file"
  type        = map(string)
  default     = {}
}

variable "policy_name" {
  description = "Name of the inline policy attached to the role"
  type        = string
  default     = "InlinePolicy"
}

variable "create_instance_profile" {
  description = "Whether to create an instance profile for this role"
  type        = bool
  default     = false
}

variable "instance_profile_name" {
  description = "Name of the IAM instance profile (only used if create_instance_profile is true)"
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

