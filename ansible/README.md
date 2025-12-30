# Ansible Configuration for S3 Batch Operations

This directory contains Ansible playbooks and roles for configuring the EC2 instance.

## Structure

```
ansible/
├── group_vars/
│   ├── all.yml             # Variables for all hosts (SSH keys, users, Jenkins config)
│   └── all.yml.example     # Example file - copy to all.yml and customize
├── roles/
│   ├── common/              # Custom role for system setup, patching, and user management
│   └── geerlingguy.jenkins/ # Jenkins installation role from Ansible Galaxy
├── playbook.yml             # Main playbook
├── inventory.yml            # Inventory file with EC2 instance details
└── README.md               # This file
```

## Roles

### common
Handles:
- System patching and updates
- Installation of basic tools (git, wget, curl, vim, etc.)
- User creation and SSH key management

### geerlingguy.jenkins
Installs and configures Jenkins on the EC2 instance.

## Usage

### 1. Update Inventory
Edit `inventory.yml` with your EC2 instance public IP:
```yaml
ansible_host: YOUR_INSTANCE_IP
```

### 2. Configure Variables
Copy the example variables file and customize it:
```bash
cp group_vars/all.yml.example group_vars/all.yml
```

Edit `group_vars/all.yml` and add your SSH public keys and user information:
```yaml
common_users:
  - user: liman
    comment: "Liman Alhassan"
    public_key: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB..."  # Your actual key
    key_comment: "Liman's key"
    groups: ["wheel"]
```

To get your public key:
```bash
cat ~/.ssh/id_rsa.pub
# or
cat ~/.ssh/id_ed25519.pub
```

**Note:** The `group_vars/all.yml` file contains sensitive information (SSH keys). Make sure it's not committed to version control if it contains real keys.

### 3. Run the Playbook
```bash
cd ansible
ansible-playbook -i inventory.yml playbook.yml
```

## Adding More Users

To add additional users, edit `group_vars/all.yml` and add them to the `common_users` list:

```yaml
common_users:
  - user: liman
    comment: "Liman Alhassan"
    public_key: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB..."
    key_comment: "Liman's key"
    groups: ["wheel"]
  - user: john
    comment: "John Doe"
    public_key: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB..."
    key_comment: "John's key"
    groups: ["wheel"]
```

## After Running the Playbook

Once the playbook completes:
1. Users will be created on the EC2 instance
2. SSH keys will be configured
3. You can SSH directly without using the PEM key:
   ```bash
   ssh liman@YOUR_INSTANCE_IP
   ```
4. Jenkins will be accessible at: `http://YOUR_INSTANCE_IP:8080`

## Requirements

- Ansible 2.9+
- Access to the EC2 instance via SSH using the PEM key

