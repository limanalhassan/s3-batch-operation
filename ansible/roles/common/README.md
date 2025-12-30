# Common Role

This role handles common system setup tasks including:
- System patching and updates
- Installation of basic tools
- SSH public key management for user access

## Requirements

- Ansible 2.9+
- Target system: Amazon Linux 2, RHEL, CentOS, or Debian/Ubuntu

## Role Variables

### `common_users`
List of users to create and configure SSH keys for. Each user should have:
- `user`: Username (required)
- `comment`: Full name or description (optional)
- `public_key`: SSH public key content (required)
- `key_comment`: Comment for the SSH key (optional, defaults to comment)
- `home`: Home directory path (optional, defaults to /home/username)
- `groups`: List of groups to add user to (optional, e.g., ["wheel", "docker"])

Example:
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
```

### `common_update_packages`
Whether to update system packages. Default: `true`

### `common_install_tools`
Whether to install basic tools. Default: `true`

## Dependencies

None

## Example Playbook

```yaml
- hosts: all
  become: yes
  roles:
    - common
  vars:
    common_users:
      - user: liman
        comment: "Liman Alhassan"
        public_key: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB..."
        key_comment: "Liman's key"
        groups: ["wheel"]
```

## License

MIT
