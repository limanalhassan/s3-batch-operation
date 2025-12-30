locals {
  environment = terraform.workspace
  name_prefix = "${var.project_name}-${local.environment}"
  
  common_tags = merge(var.common_tags, {
    Environment = local.environment
    Project     = var.project_name
    ManagedBy   = "Terraform"
  })

  s3_batch_tags = {
    operation = "s3BatchOperations"
    env       = terraform.workspace
    managedBy = "Terraform"
  }
}

module "vpc" {
  source = "../modules/vpc"

  vpc_name              = "${local.name_prefix}-vpc"
  cidr_block            = var.vpc_cidr_block
  public_subnet_cidrs   = var.public_subnet_cidrs
  private_subnet_cidrs  = var.private_subnet_cidrs
  enable_nat_gateway    = var.enable_nat_gateway
  availability_zones    = var.availability_zones

  tags = local.common_tags
}

module "keypair" {
  count = var.create_key_pair ? 1 : 0
  source = "../modules/keypair"

  key_name          = "${local.name_prefix}-key"
  save_private_key  = var.save_private_key
  private_key_path  = var.private_key_path

  tags = local.common_tags
}

module "iam" {
  source = "../modules/iam"

  role_name             = "${local.name_prefix}-jenkins-role"
  instance_profile_name = "${local.name_prefix}-jenkins-profile"

  tags = local.common_tags
}

module "security_group" {
  source = "../modules/security-group"

  security_group_name = "${local.name_prefix}-jenkins-sg"
  description         = "Security group for Jenkins EC2 instance"
  vpc_id              = module.vpc.vpc_id

  ingress_rules = [
    {
      description = "HTTP"
      from_port   = 80
      to_port     = 80
      protocol    = "tcp"
      cidr_blocks = var.allowed_all_cidr_blocks
    },
    {
      description = "HTTPS"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = var.allowed_all_cidr_blocks
    },
    {
      description = "Jenkins"
      from_port   = 8080
      to_port     = 8080
      protocol    = "tcp"
      cidr_blocks = var.allowed_all_cidr_blocks
    },
    {
      description = "SSH"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.allowed_cidr_blocks
    }
  ]

  tags = local.common_tags
}

module "ec2" {
  source = "../modules/ec2"

  instance_name            = "${local.name_prefix}-jenkins"
  instance_type            = var.instance_type
  key_name                 = var.create_key_pair ? module.keypair[0].key_name : var.key_name
  vpc_id                   = module.vpc.vpc_id
  subnet_id                = var.use_private_subnet ? module.vpc.private_subnet_ids[0] : module.vpc.public_subnet_ids[0]
  iam_instance_profile_name = module.iam.instance_profile_name
  security_group_ids       = [module.security_group.security_group_id]
  volume_type              = var.volume_type
  volume_size              = var.volume_size
  encrypt_volume           = var.encrypt_volume
  user_data                = var.user_data

  tags = local.common_tags
}

module "s3_source" {
  source = "../modules/s3"

  bucket_name            = "${local.name_prefix}-source-${local.environment}"
  enable_versioning      = var.s3_enable_versioning
  encryption_algorithm   = var.s3_encryption_algorithm
  bucket_key_enabled     = var.s3_bucket_key_enabled
  block_public_acls      = var.s3_block_public_acls
  block_public_policy    = var.s3_block_public_policy
  ignore_public_acls     = var.s3_ignore_public_acls
  restrict_public_buckets = var.s3_restrict_public_buckets

  tags = merge(local.s3_batch_tags, {
    Target = "Source"
  })
}

module "s3_destination" {
  source = "../modules/s3"

  bucket_name            = "${local.name_prefix}-destination-${local.environment}"
  enable_versioning      = var.s3_enable_versioning
  encryption_algorithm   = var.s3_encryption_algorithm
  bucket_key_enabled     = var.s3_bucket_key_enabled
  block_public_acls      = var.s3_block_public_acls
  block_public_policy    = var.s3_block_public_policy
  ignore_public_acls     = var.s3_ignore_public_acls
  restrict_public_buckets = var.s3_restrict_public_buckets

  tags = merge(local.s3_batch_tags, {
    Target = "Destination"
  })
}

resource "local_file" "ansible_inventory" {
  content = templatefile("${path.module}/../ansible/inventory.yml.tpl", {
    jenkins_instance_public_ip = module.ec2.instance_public_ip
    key_file_path = var.create_key_pair ? (
      "../dev/${module.keypair[0].key_name}.pem"
    ) : (
      var.key_name != null ? "../dev/${var.key_name}.pem" : "../dev/key.pem"
    )
  })
  filename = "${path.module}/../ansible/inventory.yml"
  
  depends_on = [module.ec2]
}

