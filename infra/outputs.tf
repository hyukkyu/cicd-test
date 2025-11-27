output "alb_dns" {
  value = aws_lb.app.dns_name
}

output "cloudfront_user_domain" {
  value = aws_cloudfront_distribution.user.domain_name
}

output "cloudfront_admin_domain" {
  value = aws_cloudfront_distribution.admin.domain_name
}

output "cloudfront_media_domain" {
  value = aws_cloudfront_distribution.media.domain_name
}

output "rds_endpoint" {
  value = aws_db_instance.app.address
}

output "bastion_public_ip" {
  value = aws_instance.bastion.public_ip
}

output "secrets" {
  value = {
    cognito  = aws_secretsmanager_secret.cognito.arn
    database = aws_secretsmanager_secret.database.arn
    api_keys = aws_secretsmanager_secret.api_keys.arn
  }
}

output "media_bucket" {
  value = aws_s3_bucket.media.bucket
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "cognito_user_client_id" {
  value = aws_cognito_user_pool_client.user.id
}

output "cognito_admin_client_id" {
  value = aws_cognito_user_pool_client.admin.id
}

output "cognito_domain" {
  value = local.cognito_domain
}

output "monitoring_iam_instance_profile" {
  value = aws_iam_instance_profile.monitoring.name
}

output "monitoring_iam_role_arn" {
  value = aws_iam_role.monitoring.arn
}
