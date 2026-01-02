terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}

locals {
  # Python 3.9 installation script for Ansible compatibility
  python39_install_script = <<-EOF
    #!/bin/bash
    set -e
    
    # Check if Python 3.9 is already installed
    if [ -f /usr/local/bin/python3.9 ]; then
      echo "Python 3.9 is already installed"
      exit 0
    fi
    
    # Install development tools and dependencies
    yum groupinstall -y 'Development Tools' || true
    yum install -y openssl-devel bzip2-devel libffi-devel zlib-devel readline-devel sqlite-devel wget || true
    
    # Download and compile Python 3.9
    cd /tmp
    if [ ! -f Python-3.9.18.tgz ]; then
      wget https://www.python.org/ftp/python/3.9.18/Python-3.9.18.tgz
    fi
    
    if [ ! -d Python-3.9.18 ]; then
      tar xzf Python-3.9.18.tgz
    fi
    
    cd Python-3.9.18
    
    # Configure and install Python 3.9
    # Note: --enable-optimizations makes compilation much slower (10-15 min vs 3-5 min)
    # Remove it for faster installation if optimizations aren't critical
    ./configure --prefix=/usr/local
    make -j$(nproc) altinstall
    
    # Create symlink so python3 points to python3.9 (for Ansible compatibility)
    # This ensures python3 command uses Python 3.9 instead of system Python 3.7
    ln -sf /usr/local/bin/python3.9 /usr/local/bin/python3
    
    # Clean up
    cd /
    rm -rf /tmp/Python-3.9.18*
    
    echo "Python 3.9 installation completed"
  EOF

  # Merge Python 3.9 installation with custom user_data if provided
  combined_user_data = var.user_data != null ? join("\n", [
    local.python39_install_script,
    "",
    "# Custom user_data",
    var.user_data
  ]) : local.python39_install_script
}

resource "aws_instance" "jenkins" {
  ami                    = var.ami_id != null ? var.ami_id : data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  key_name               = var.key_name
  vpc_security_group_ids = var.security_group_ids
  subnet_id              = var.subnet_id
  iam_instance_profile   = var.iam_instance_profile_name

  user_data = base64encode(local.combined_user_data)
  user_data_replace_on_change = true

  # Ensure metadata service is enabled and accessible for instance profile
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "optional"  # Use optional for IMDSv1 compatibility
    http_put_response_hop_limit = 1
    instance_metadata_tags     = "enabled"
  }

  root_block_device {
    volume_type = var.volume_type
    volume_size = var.volume_size
    encrypted   = var.encrypt_volume
  }

  tags = merge(var.tags, {
    Name = var.instance_name
  })
}

