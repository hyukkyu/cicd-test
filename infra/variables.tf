variable "project" {
  description = "Project prefix"
  type        = string
  default     = "cms-community"
}

variable "domain_name" {
  description = "Route53 root domain"
  type        = string
  default     = "cms-community.com"
}

variable "user_domain" {
  description = "User frontend domain"
  type        = string
  default     = "user.cms-community.com"
}

variable "admin_domain" {
  description = "Admin frontend domain"
  type        = string
  default     = "admin.cms-community.com"
}

variable "media_domain" {
  description = "Media delivery domain (CloudFront)"
  type        = string
  default     = "media.cms-community.com"
}

variable "api_domain" {
  description = "Backend/API domain"
  type        = string
  default     = "api.cms-community.com"
}

variable "vpc_cidr" {
  type    = string
  default = "10.30.0.0/16"
}

variable "public_subnets" {
  type    = list(string)
  default = ["10.30.1.0/24", "10.30.2.0/24"]
}

variable "private_subnets" {
  type    = list(string)
  default = ["10.30.11.0/24", "10.30.12.0/24"]
}

variable "bastion_allowed_cidr" {
  description = "CIDR allowed to SSH into bastion"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "db_public_cidrs" {
  description = "Optional CIDR blocks allowed to connect directly to the database (use only for temporary external access)."
  type        = list(string)
  default     = []
}

variable "db_username" {
  description = "RDS master username"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "RDS master password"
  type        = string
  default     = "Kopo1234!!"
  sensitive   = true
}

variable "db_name" {
  description = "Initial database name"
  type        = string
  default     = "cmsdb"
}

variable "cognito_domain_prefix" {
  type        = string
  description = "Cognito hosted UI domain prefix (e.g., cms-community)"
  default     = "cms-community"
}

variable "grafana_admin_password" {
  type    = string
  default = "admin"
}

variable "bastion_key_name" {
  description = "Existing SSH key pair name"
  type        = string
  default     = "cms-project-tokyo"
}

variable "bastion_public_key" {
  description = "Public key material (e.g. ssh-rsa ...) to register as a new bastion key pair."
  type        = string
  default     = ""
}

variable "monitoring_ec2_instance_id" {
  description = "EC2 instance ID that should be surfaced on the admin dashboard health widget."
  type        = string
  default     = ""
}
