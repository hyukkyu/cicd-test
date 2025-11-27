terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.45"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = "ap-northeast-1"
}

provider "aws" {
  alias  = "us-east-1"
  region = "us-east-1"
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_route53_zone" "primary" {
  name = var.domain_name
}

resource "random_id" "media" {
  byte_length = 4
}

locals {
  name_prefix = var.project
  tags = {
    Project     = var.project
    Environment = "prod"
  }
  cognito_domain  = "${var.cognito_domain_prefix}.auth.ap-northeast-1.amazoncognito.com"
  container_image = "${aws_ecr_repository.backend.repository_url}:latest"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(local.tags, { Name = "${local.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.tags, { Name = "${local.name_prefix}-igw" })
}

resource "aws_subnet" "public" {
  for_each                = toset(var.public_subnets)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = each.value
  map_public_ip_on_launch = true
  availability_zone       = data.aws_availability_zones.available.names[index(var.public_subnets, each.value)]
  tags                    = merge(local.tags, { Name = "${local.name_prefix}-public-${index(var.public_subnets, each.value) + 1}" })
}

resource "aws_subnet" "private" {
  for_each          = toset(var.private_subnets)
  vpc_id            = aws_vpc.main.id
  cidr_block        = each.value
  availability_zone = data.aws_availability_zones.available.names[index(var.private_subnets, each.value)]
  tags              = merge(local.tags, { Name = "${local.name_prefix}-private-${index(var.private_subnets, each.value) + 1}" })
}

locals {
  public_subnet_ids  = [for s in aws_subnet.public : s.id]
  private_subnet_ids = [for s in aws_subnet.private : s.id]
}

resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = merge(local.tags, { Name = "${local.name_prefix}-nat-eip" })
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = local.public_subnet_ids[0]
  tags          = merge(local.tags, { Name = "${local.name_prefix}-nat" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-public-rt" })
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat.id
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-private-rt" })
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}


# Security Groups
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Allow HTTPS from internet"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-alb-sg" })
}

resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-ecs-sg"
  description = "Allow traffic from ALB"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
    description     = "ALB to ECS"
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-ecs-sg" })
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Allow MySQL from ECS and bastion"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.bastion.id]
  }
  dynamic "ingress" {
    for_each = var.db_public_cidrs
    content {
      from_port   = 3306
      to_port     = 3306
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
      description = "Temporary direct DB access"
    }
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-rds-sg" })
}

resource "aws_security_group" "bastion" {
  name        = "${local.name_prefix}-bastion-sg"
  description = "Allow SSH from allow list"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.bastion_allowed_cidr
  }
  ingress {
    description     = "Allow ECS tasks to reach Grafana"
    from_port       = 3000
    to_port         = 3000
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  ingress {
    description     = "Allow ECS tasks to reach Prometheus"
    from_port       = 9090
    to_port         = 9090
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-bastion-sg" })
}

resource "aws_security_group" "monitoring" {
  name        = "${local.name_prefix}-monitoring-sg"
  description = "Allow Grafana/Prometheus within VPC"
  vpc_id      = aws_vpc.main.id
  ingress {
    description = "Grafana"
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  ingress {
    description     = "Grafana from backend ECS"
    from_port       = 3000
    to_port         = 3000
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  ingress {
    description = "Prometheus"
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  ingress {
    description     = "Prometheus from backend ECS"
    from_port       = 9090
    to_port         = 9090
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(local.tags, { Name = "${local.name_prefix}-monitoring-sg" })
}

resource "aws_key_pair" "bastion" {
  count      = var.bastion_public_key != "" ? 1 : 0
  key_name   = var.bastion_key_name != "" ? var.bastion_key_name : "${local.name_prefix}-bastion"
  public_key = var.bastion_public_key
  tags       = local.tags
}

locals {
  bastion_key_name = try(aws_key_pair.bastion[0].key_name, var.bastion_key_name)
}



# Bastion host
resource "aws_instance" "bastion" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = "t3.micro"
  subnet_id                   = local.public_subnet_ids[0]
  key_name                    = local.bastion_key_name
  vpc_security_group_ids      = [aws_security_group.bastion.id]
  associate_public_ip_address = true
  tags                        = merge(local.tags, { Name = "${local.name_prefix}-bastion" })

  lifecycle {
    precondition {
      condition     = local.bastion_key_name != ""
      error_message = "제공된 키 페어가 없습니다. existing key의 이름(bastion_key_name) 또는 공개키(bastion_public_key)를 설정하세요."
    }
  }
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-*-kernel-6.1-x86_64"]
  }
}

# Cognito User Pool
resource "aws_cognito_user_pool" "main" {
  name                     = "${local.name_prefix}-user-pool"
  auto_verified_attributes = ["email"]
  alias_attributes         = ["email"]
  mfa_configuration        = "OFF"
  username_configuration {
    case_sensitive = false
  }
  admin_create_user_config {
    allow_admin_create_user_only = false
  }
  schema {
    attribute_data_type      = "String"
    developer_only_attribute = false
    mutable                  = true
    name                     = "email"
    required                 = true
    string_attribute_constraints {
      min_length = "5"
      max_length = "256"
    }
  }
  schema {
    attribute_data_type      = "String"
    developer_only_attribute = false
    mutable                  = true
    name                     = "nickname"
    required                 = false
    string_attribute_constraints {
      min_length = "1"
      max_length = "50"
    }
  }
  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }
  tags = local.tags
}

resource "aws_cognito_user_group" "admin" {
  name         = "admin"
  user_pool_id = aws_cognito_user_pool.main.id
  precedence   = 1
  description  = "Administrators"
}

resource "aws_cognito_user_group" "user" {
  name         = "user"
  user_pool_id = aws_cognito_user_pool.main.id
  precedence   = 5
  description  = "Regular users"
}

resource "aws_cognito_resource_server" "admin" {
  identifier   = "${var.project}.api"
  name         = "${local.name_prefix}-resource-server"
  user_pool_id = aws_cognito_user_pool.main.id
  scope {
    scope_name        = "admin"
    scope_description = "Administrator access"
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = var.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}

resource "aws_cognito_user_pool_client" "user" {
  name                                 = "${local.name_prefix}-user-client"
  user_pool_id                         = aws_cognito_user_pool.main.id
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "profile"]
  explicit_auth_flows                  = ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_PASSWORD_AUTH", "ALLOW_USER_SRP_AUTH", "ALLOW_ADMIN_USER_PASSWORD_AUTH"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls = [
    "https://${var.user_domain}/auth/callback",
    "https://${var.user_domain}/"
  ]
  logout_urls = [
    "https://${var.user_domain}/"
  ]
  generate_secret               = false
  prevent_user_existence_errors = "ENABLED"
  refresh_token_validity        = 30
  access_token_validity         = 60
  id_token_validity             = 60
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}

resource "aws_cognito_user_pool_client" "admin" {
  name                                 = "${local.name_prefix}-admin-client"
  user_pool_id                         = aws_cognito_user_pool.main.id
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes = [
    "openid",
    "profile",
    aws_cognito_resource_server.admin.scope_identifiers[0]
  ]
  explicit_auth_flows          = ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH", "ALLOW_ADMIN_USER_PASSWORD_AUTH"]
  supported_identity_providers = ["COGNITO"]
  callback_urls = [
    "https://${var.admin_domain}/auth/callback",
    "https://${var.admin_domain}/"
  ]
  logout_urls = [
    "https://${var.admin_domain}/"
  ]
  generate_secret               = false
  prevent_user_existence_errors = "ENABLED"
  refresh_token_validity        = 30
  access_token_validity         = 60
  id_token_validity             = 60
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}

# Secrets Manager
resource "aws_secretsmanager_secret" "cognito" {
  name = "${local.name_prefix}/cognito"
  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "cognito" {
  secret_id = aws_secretsmanager_secret.cognito.id
  secret_string = jsonencode({
    COGNITO_POOL_ID         = aws_cognito_user_pool.main.id
    COGNITO_CLIENT_ID       = aws_cognito_user_pool_client.user.id
    COGNITO_ADMIN_CLIENT_ID = aws_cognito_user_pool_client.admin.id
    COGNITO_DOMAIN          = local.cognito_domain
    COGNITO_ISSUER          = "https://cognito-idp.ap-northeast-1.amazonaws.com/${aws_cognito_user_pool.main.id}"
    AWS_REGION              = "ap-northeast-1"
  })
}

resource "aws_secretsmanager_secret" "database" {
  name = "${local.name_prefix}/db"
  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    DB_USERNAME = var.db_username,
    DB_PASSWORD = var.db_password,
    DB_NAME     = var.db_name,
    DB_JDBC_URL = "jdbc:mysql://${aws_db_instance.app.address}:3306/${var.db_name}?useSSL=true"
  })
}

resource "aws_secretsmanager_secret" "api_keys" {
  name = "${local.name_prefix}/s3"
  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "api_keys" {
  secret_id = aws_secretsmanager_secret.api_keys.id
  secret_string = jsonencode({
    AWS = {
      rekognitionProject = "cms-community"
      comprehendJob      = "cms-community"
    }
    cms = {
      media = {
        bucket = aws_s3_bucket.media.bucket
      }
      frontend = {
        userBucket  = aws_s3_bucket.frontend_user.bucket
        adminBucket = aws_s3_bucket.frontend_admin.bucket
      }
    }
  })
}

# RDS
resource "aws_db_subnet_group" "mysql" {
  name       = "${local.name_prefix}-rds-subnet"
  subnet_ids = local.private_subnet_ids
  tags       = local.tags
}

resource "aws_db_instance" "app" {
  identifier              = "${local.name_prefix}-mysql"
  allocated_storage       = 20
  engine                  = "mysql"
  engine_version          = "8.0"
  instance_class          = "db.t3.micro"
  db_subnet_group_name    = aws_db_subnet_group.mysql.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  username                = var.db_username
  password                = var.db_password
  db_name                 = var.db_name
  skip_final_snapshot     = true
  publicly_accessible     = false
  deletion_protection     = false
  apply_immediately       = true
  backup_retention_period = 7
  multi_az                = false
  tags                    = local.tags
}

# ECR
resource "aws_ecr_repository" "backend" {
  name = "${local.name_prefix}-backend"
  image_scanning_configuration { scan_on_push = true }
  tags = local.tags
}

# ECS cluster
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"
  tags = local.tags
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/aws/ecs/${local.name_prefix}"
  retention_in_days = 30
  tags              = local.tags
}

locals {
  task_execution_role_name = "${local.name_prefix}-ecs-task-exec"
}

resource "aws_iam_role" "ecs_task_execution" {
  name = local.task_execution_role_name
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action    = "sts:AssumeRole",
      Effect    = "Allow",
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task_role" {
  name = "${local.name_prefix}-ecs-task-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "ecs-tasks.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "ecs_task_secrets" {
  name = "${local.name_prefix}-task-secrets"
  role = aws_iam_role.ecs_task_role.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ],
        Resource = [
          aws_secretsmanager_secret.database.arn,
          aws_secretsmanager_secret.cognito.arn,
          aws_secretsmanager_secret.api_keys.arn
        ]
      },
      {
        Effect = "Allow",
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion",
          "s3:AbortMultipartUpload",
          "s3:ListMultipartUploadParts",
          "s3:ListBucket"
        ],
        Resource = [
          "${aws_s3_bucket.media.arn}/*",
          aws_s3_bucket.media.arn
        ]
      },
      {
        Effect = "Allow",
        Action = [
          "comprehend:DetectSentiment",
          "comprehend:DetectPiiEntities",
          "translate:TranslateText"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "rekognition:DetectModerationLabels",
          "rekognition:StartContentModeration",
          "rekognition:GetContentModeration"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "cognito-idp:AdminInitiateAuth",
          "cognito-idp:AdminDisableUser",
          "cognito-idp:AdminEnableUser",
          "cognito-idp:AdminListGroupsForUser",
          "cognito-idp:AdminAddUserToGroup"
        ],
        Resource = [
          "*",
          aws_cognito_user_pool.main.arn
        ]
      },
      {
        Effect = "Allow",
        Action = [
          "ec2:DescribeInstanceStatus"
        ],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role" "monitoring" {
  name = "${local.name_prefix}-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "ec2.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
  tags = local.tags
}

resource "aws_iam_role_policy" "monitoring_cloudwatch" {
  name = "${local.name_prefix}-monitoring-cloudwatch"
  role = aws_iam_role.monitoring.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "cloudwatch:GetMetricData",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
          "cloudwatch:DescribeAlarms",
          "cloudwatch:DescribeAlarmHistory"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "elasticloadbalancing:DescribeLoadBalancers",
          "elasticloadbalancing:DescribeTargetGroups",
          "elasticloadbalancing:DescribeListeners",
          "elasticloadbalancing:DescribeRules"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "tag:GetResources"
        ],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "monitoring" {
  name = "${local.name_prefix}-monitoring"
  role = aws_iam_role.monitoring.name
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-task"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn
  container_definitions = jsonencode([
    {
      name         = "backend"
      image        = local.container_image
      essential    = true
      portMappings = [{ containerPort = 8080, hostPort = 8080 }]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "AWS_REGION", value = "ap-northeast-1" },
        { name = "CMS_MEDIA_BUCKET", value = aws_s3_bucket.media.bucket },
        { name = "CLOUDFRONT_DOMAIN", value = var.media_domain },
        {
          name  = "MONITORING_EC2_INSTANCE_ID"
          value = var.monitoring_ec2_instance_id != "" ? var.monitoring_ec2_instance_id : aws_instance.bastion.id
        },
        { name = "GRAFANA_URL", value = "http://${aws_lb.monitoring.dns_name}:3000" },
        { name = "PROMETHEUS_SERVER_URL", value = "http://${aws_lb.monitoring.dns_name}:9090" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = "ap-northeast-1"
          awslogs-stream-prefix = "cms"
        }
      }
    }
  ])
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  tags = local.tags
}

# ALB
resource "aws_lb" "app" {
  name                       = "${local.name_prefix}-alb"
  internal                   = false
  load_balancer_type         = "application"
  subnets                    = local.public_subnet_ids
  security_groups            = [aws_security_group.alb.id]
  enable_deletion_protection = false
  tags                       = local.tags
}

resource "aws_lb_target_group" "ecs" {
  name        = "${local.name_prefix}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  health_check {
    interval            = 30
    path                = "/actuator/health"
    healthy_threshold   = 3
    unhealthy_threshold = 3
  }
  tags = local.tags
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.alb.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ecs.arn
  }
}

resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = local.private_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.ecs.arn
    container_name   = "backend"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https]
  tags       = local.tags
}

# ACM Certificates
resource "aws_acm_certificate" "alb" {
  domain_name       = var.api_domain
  validation_method = "DNS"
  tags              = local.tags
}

resource "aws_acm_certificate_validation" "alb" {
  certificate_arn         = aws_acm_certificate.alb.arn
  validation_record_fqdns = [for record in aws_route53_record.alb_validation : record.fqdn]
}

resource "aws_acm_certificate" "cloudfront" {
  provider                  = aws.us-east-1
  domain_name               = var.domain_name
  subject_alternative_names = [var.user_domain, var.admin_domain, var.media_domain]
  validation_method         = "DNS"
  tags                      = local.tags
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us-east-1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.cf_validation : record.fqdn]
}

resource "aws_route53_record" "alb_validation" {
  for_each = {
    for dvo in aws_acm_certificate.alb.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }
  zone_id         = data.aws_route53_zone.primary.zone_id
  allow_overwrite = true
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
}

resource "aws_route53_record" "cf_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }
  zone_id         = data.aws_route53_zone.primary.zone_id
  allow_overwrite = true
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
}

resource "aws_s3_bucket" "frontend_user" {
  bucket        = "${local.name_prefix}-frontend-user"
  force_destroy = true
  tags          = local.tags
}

resource "aws_s3_bucket" "frontend_admin" {
  bucket        = "${local.name_prefix}-frontend-admin"
  force_destroy = true
  tags          = local.tags
}

resource "aws_s3_bucket" "media" {
  bucket        = "${local.name_prefix}-media-${random_id.media.hex}"
  force_destroy = true
  tags          = local.tags
}

resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  cors_rule {
    allowed_methods = ["GET", "HEAD", "PUT"]
    allowed_origins = [
      "https://${var.user_domain}",
      "https://${var.admin_domain}",
    ]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "oac" {
  name                              = "${local.name_prefix}-oac"
  description                       = "S3 access for CloudFront"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_s3_bucket_policy" "frontend_user" {
  bucket = aws_s3_bucket.frontend_user.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = "cloudfront.amazonaws.com"
        },
        Action   = "s3:GetObject",
        Resource = "${aws_s3_bucket.frontend_user.arn}/*",
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.user.arn
          }
        }
      }
    ]
  })
}

resource "aws_s3_bucket_policy" "frontend_admin" {
  bucket = aws_s3_bucket.frontend_admin.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = "cloudfront.amazonaws.com"
        },
        Action   = "s3:GetObject",
        Resource = "${aws_s3_bucket.frontend_admin.arn}/*",
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.admin.arn
          }
        }
      }
    ]
  })
}

resource "aws_s3_bucket_policy" "media" {
  bucket = aws_s3_bucket.media.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = "cloudfront.amazonaws.com"
        },
        Action   = "s3:GetObject",
        Resource = "${aws_s3_bucket.media.arn}/*",
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.media.arn
          }
        }
      }
    ]
  })
}

resource "aws_cloudfront_distribution" "user" {
  enabled             = true
  aliases             = [var.user_domain]
  default_root_object = "index.html"

  origin {
    domain_name              = aws_s3_bucket.frontend_user.bucket_regional_domain_name
    origin_id                = "user"
    origin_access_control_id = aws_cloudfront_origin_access_control.oac.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    target_origin_id       = "user"
    compress               = true
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  price_class = "PriceClass_200"
  viewer_certificate {
    acm_certificate_arn = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method  = "sni-only"
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  tags = local.tags
}

resource "aws_cloudfront_distribution" "admin" {
  enabled             = true
  aliases             = [var.admin_domain]
  default_root_object = "index.html"

  origin {
    domain_name              = aws_s3_bucket.frontend_admin.bucket_regional_domain_name
    origin_id                = "admin"
    origin_access_control_id = aws_cloudfront_origin_access_control.oac.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    target_origin_id       = "admin"
    compress               = true
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  price_class = "PriceClass_200"
  viewer_certificate {
    acm_certificate_arn = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method  = "sni-only"
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  tags = local.tags
}

resource "aws_cloudfront_distribution" "media" {
  enabled = true
  aliases = [var.media_domain]

  origin {
    domain_name              = aws_s3_bucket.media.bucket_regional_domain_name
    origin_id                = "media"
    origin_access_control_id = aws_cloudfront_origin_access_control.oac.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    target_origin_id       = "media"
    compress               = true
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  price_class = "PriceClass_200"
  viewer_certificate {
    acm_certificate_arn = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method  = "sni-only"
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  tags = local.tags
}

resource "aws_route53_record" "api" {
  zone_id         = data.aws_route53_zone.primary.zone_id
  name            = var.api_domain
  type            = "A"
  allow_overwrite = true
  alias {
    name                   = aws_lb.app.dns_name
    zone_id                = aws_lb.app.zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "user" {
  zone_id         = data.aws_route53_zone.primary.zone_id
  name            = var.user_domain
  type            = "A"
  allow_overwrite = true
  alias {
    name                   = aws_cloudfront_distribution.user.domain_name
    zone_id                = aws_cloudfront_distribution.user.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "admin" {
  zone_id         = data.aws_route53_zone.primary.zone_id
  name            = var.admin_domain
  type            = "A"
  allow_overwrite = true
  alias {
    name                   = aws_cloudfront_distribution.admin.domain_name
    zone_id                = aws_cloudfront_distribution.admin.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "media" {
  zone_id         = data.aws_route53_zone.primary.zone_id
  name            = var.media_domain
  type            = "A"
  allow_overwrite = true
  alias {
    name                   = aws_cloudfront_distribution.media.domain_name
    zone_id                = aws_cloudfront_distribution.media.hosted_zone_id
    evaluate_target_health = false
  }
}

# SNS + SQS + Lambda pipeline
resource "aws_sns_topic" "moderation" {
  name = "${local.name_prefix}-moderation"
  tags = local.tags
}

resource "aws_sqs_queue" "moderation" {
  name                       = "${local.name_prefix}-moderation"
  visibility_timeout_seconds = 30
  tags                       = local.tags
}

resource "aws_lambda_function" "moderation" {
  function_name    = "${local.name_prefix}-moderation"
  runtime          = "python3.11"
  handler          = "moderation_handler.lambda_handler"
  role             = aws_iam_role.lambda.arn
  filename         = "../lambda/moderation.zip"
  source_code_hash = filebase64sha256("../lambda/moderation.zip")
  environment {
    variables = {
      MODERATION_THRESHOLD    = "0.8"
      CMS_COMMUNITY_SNS_TOPIC = aws_sns_topic.moderation.arn
    }
  }
  tags = local.tags
}

resource "aws_iam_role" "lambda" {
  name = "${local.name_prefix}-lambda"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action    = "sts:AssumeRole",
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "lambda_moderation" {
  name = "${local.name_prefix}-lambda-moderation"
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.moderation.arn
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.moderation.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = "${aws_s3_bucket.media.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "comprehend:DetectSentiment",
          "rekognition:DetectModerationLabels"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.api_keys.arn,
          aws_secretsmanager_secret.cognito.arn,
          aws_secretsmanager_secret.database.arn
        ]
      }
    ]
  })
}

resource "aws_lambda_event_source_mapping" "moderation" {
  event_source_arn = aws_sqs_queue.moderation.arn
  function_name    = aws_lambda_function.moderation.arn
  batch_size       = 10
}

resource "aws_sns_topic_subscription" "moderation" {
  endpoint             = aws_sqs_queue.moderation.arn
  protocol             = "sqs"
  topic_arn            = aws_sns_topic.moderation.arn
  raw_message_delivery = true
}

resource "aws_sqs_queue_policy" "moderation" {
  queue_url = aws_sqs_queue.moderation.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = "*",
      Action    = "sqs:SendMessage",
      Resource  = aws_sqs_queue.moderation.arn,
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = aws_sns_topic.moderation.arn
        }
      }
    }]
  })
}

locals {
  prometheus_config      = <<-EOT
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: "cms-community-backend"
    scheme: https
    metrics_path: /actuator/prometheus
    tls_config:
      insecure_skip_verify: true
    static_configs:
      - targets: ["${aws_lb.app.dns_name}"]
EOT
  grafana_datasource     = <<-EOT
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://localhost:9090
    isDefault: true
EOT
  grafana_dashboard_def  = file("${path.module}/../monitoring/grafana/provisioning/dashboards/dashboard.yml")
  grafana_dashboard_json = file("${path.module}/../monitoring/grafana/provisioning/dashboards/json/spring.json")
}

resource "aws_lb" "monitoring" {
  name               = "${local.name_prefix}-mon-nlb"
  load_balancer_type = "network"
  internal           = true
  subnets            = local.private_subnet_ids
  tags               = local.tags
}

resource "aws_lb_target_group" "monitoring_grafana" {
  name        = "${local.name_prefix}-mon-gf3"
  port        = 3000
  protocol    = "TCP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  lifecycle {
    create_before_destroy = true
  }
  tags = local.tags
}

resource "aws_lb_target_group" "monitoring_prometheus" {
  name        = "${local.name_prefix}-mon-pr3"
  port        = 9090
  protocol    = "TCP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  lifecycle {
    create_before_destroy = true
  }
  tags = local.tags
}

resource "aws_lb_listener" "monitoring_grafana" {
  load_balancer_arn = aws_lb.monitoring.arn
  port              = 3000
  protocol          = "TCP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.monitoring_grafana.arn
  }
}

resource "aws_lb_listener" "monitoring_prometheus" {
  load_balancer_arn = aws_lb.monitoring.arn
  port              = 9090
  protocol          = "TCP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.monitoring_prometheus.arn
  }
}

resource "aws_ecs_task_definition" "monitoring" {
  family                   = "${local.name_prefix}-monitoring"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "2048"
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn
  container_definitions = jsonencode([
    {
      name         = "prometheus"
      image        = "prom/prometheus:v2.51.2"
      essential    = true
      portMappings = [{ containerPort = 9090, hostPort = 9090 }]
      entryPoint   = ["/bin/sh", "-c"]
      command = [
        <<-EOT
cat <<'EOF' >/etc/prometheus/prometheus.yml
${local.prometheus_config}
EOF
exec /bin/prometheus --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus --web.enable-lifecycle
EOT
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = "ap-northeast-1"
          awslogs-stream-prefix = "monitoring"
        }
      }
    },
    {
      name         = "grafana"
      image        = "grafana/grafana:10.4.2"
      essential    = true
      portMappings = [{ containerPort = 3000, hostPort = 3000 }]
      entryPoint   = ["/bin/sh", "-c"]
      command = [
        <<-EOT
mkdir -p /etc/grafana/provisioning/datasources /etc/grafana/provisioning/dashboards/json
cat <<'EOF' >/etc/grafana/provisioning/datasources/datasource.yml
${local.grafana_datasource}
EOF
cat <<'EOF' >/etc/grafana/provisioning/dashboards/dashboard.yml
${local.grafana_dashboard_def}
EOF
cat <<'EOF' >/etc/grafana/provisioning/dashboards/json/spring.json
${local.grafana_dashboard_json}
EOF
exec /run.sh
EOT
      ]
      environment = [
        { name = "GF_SECURITY_ADMIN_PASSWORD", value = var.grafana_admin_password },
        { name = "GF_SECURITY_ALLOW_EMBEDDING", value = "true" },
        { name = "GF_AUTH_ANONYMOUS_ENABLED", value = "true" },
        { name = "GF_AUTH_ANONYMOUS_ORG_ROLE", value = "Viewer" },
        { name = "GF_SERVER_SERVE_FROM_SUB_PATH", value = "true" },
        { name = "GF_LIVE_ENABLED", value = "false" },
        // Trailing slash required for sub-path to avoid redirect stripping the path.
        { name = "GF_SERVER_ROOT_URL", value = "https://${var.api_domain}/api/admin/monitoring/grafana/proxy/" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = "ap-northeast-1"
          awslogs-stream-prefix = "monitoring"
        }
      }
    }
  ])
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  tags = local.tags
}

resource "aws_ecs_service" "monitoring" {
  name            = "${local.name_prefix}-monitoring"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.monitoring.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = local.private_subnet_ids
    security_groups  = [aws_security_group.monitoring.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.monitoring_grafana.arn
    container_name   = "grafana"
    container_port   = 3000
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.monitoring_prometheus.arn
    container_name   = "prometheus"
    container_port   = 9090
  }
  depends_on = [aws_lb_listener.monitoring_grafana, aws_lb_listener.monitoring_prometheus]
  tags       = local.tags
}
