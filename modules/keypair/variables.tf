variable "key_name" {
  description = "Name of the AWS key pair"
  type        = string
}

variable "save_private_key" {
  description = "Whether to save the private key to a local file"
  type        = bool
  default     = false
}

variable "private_key_path" {
  description = "Path to save the private key file (only used if save_private_key is true)"
  type        = string
  default     = "."
}

variable "tags" {
  description = "Tags to apply to the key pair"
  type        = map(string)
  default     = {}
}

