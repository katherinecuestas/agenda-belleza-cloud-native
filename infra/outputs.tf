# Familia de la task definition: update-service --task-definition <familia> usa la última revisión ACTIVE
output "ecs_task_family" {
  value = aws_ecs_task_definition.backend.family
}

output "url_datos" {
  value = "${aws_apigatewayv2_api.api_manager.api_endpoint}/datos"
}
# Configuración del front Angular. Va en public/config.json.
output "config_frontend" {
  value = jsonencode({
    region        = "us-east-1"
    cognitoDomain = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.us-east-1.amazoncognito.com"
    clientId      = aws_cognito_user_pool_client.spa.id
    redirectUri   = "http://localhost:4200/"
    apiUrl        = aws_apigatewayv2_api.api_manager.api_endpoint
  })
}
# La misma configuración, pero para el front publicado en Amplify.
output "config_frontend_amplify" {
  value = jsonencode({
    region        = "us-east-1"
    cognitoDomain = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.us-east-1.amazoncognito.com"
    clientId      = aws_cognito_user_pool_client.spa.id
    redirectUri   = "${local.url_amplify}/"
    apiUrl        = aws_apigatewayv2_api.api_manager.api_endpoint
  })
}
