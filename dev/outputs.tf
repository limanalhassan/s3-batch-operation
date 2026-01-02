output "environment" {
  description = "Current Terraform workspace/environment"
  value       = terraform.workspace
}

output "key_pair_name" {
  description = "Name of the key pair used for EC2 instance"
  value       = var.create_key_pair ? module.keypair[0].key_name : var.key_name
}

output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = module.vpc.private_subnet_ids
}

output "jenkins_instance_id" {
  description = "ID of the Jenkins EC2 instance"
  value       = module.ec2.instance_id
}

output "jenkins_instance_public_ip" {
  description = "Public IP address of the Jenkins EC2 instance"
  value       = module.ec2.instance_public_ip
}

output "jenkins_instance_private_ip" {
  description = "Private IP address of the Jenkins EC2 instance"
  value       = module.ec2.instance_private_ip
}

output "jenkins_security_group_id" {
  description = "ID of the Jenkins security group"
  value       = module.security_group.security_group_id
}

output "jenkins_iam_role_arn" {
  description = "ARN of the Jenkins IAM role"
  value       = module.iam_jenkins.role_arn
}

output "jenkins_iam_role_name" {
  description = "Name of the Jenkins IAM role"
  value       = module.iam_jenkins.role_name
}

output "jenkins_instance_profile_name" {
  description = "Name of the Jenkins IAM instance profile"
  value       = module.iam_jenkins.instance_profile_name
}

output "s3_batch_infra_role_arn" {
  description = "ARN of the S3 batch infrastructure creation IAM role"
  value       = module.iam_s3_batch_infra.role_arn
}

output "s3_batch_infra_role_name" {
  description = "Name of the S3 batch infrastructure creation IAM role"
  value       = module.iam_s3_batch_infra.role_name
}

output "s3_source_bucket_id" {
  description = "ID of the S3 source bucket"
  value       = module.s3_source.bucket_id
}

output "s3_source_bucket_arn" {
  description = "ARN of the S3 source bucket"
  value       = module.s3_source.bucket_arn
}

output "s3_source_bucket_domain_name" {
  description = "Domain name of the S3 source bucket"
  value       = module.s3_source.bucket_domain_name
}

output "s3_destination_bucket_id" {
  description = "ID of the S3 destination bucket"
  value       = module.s3_destination.bucket_id
}

output "s3_destination_bucket_arn" {
  description = "ARN of the S3 destination bucket"
  value       = module.s3_destination.bucket_arn
}

output "s3_destination_bucket_domain_name" {
  description = "Domain name of the S3 destination bucket"
  value       = module.s3_destination.bucket_domain_name
}

