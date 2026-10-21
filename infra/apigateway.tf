resource "aws_apigatewayv2_integration" "backend" {
  api_id                 = aws_apigatewayv2_api.api_manager.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "GET"
  integration_uri        = "https://mindicador.cl/api"
  payload_format_version = "1.0"
}
resource "aws_apigatewayv2_route" "datos" {
  api_id             = aws_apigatewayv2_api.api_manager.id
  route_key          = "GET /datos"
  target             = "integrations/${aws_apigatewayv2_integration.backend.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.api_manager.id
  name        = "$default"
  auto_deploy = true
}
resource "aws_apigatewayv2_api" "api_manager" {
  name          = "api-reservas-belleza"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["http://localhost:4200", local.url_amplify]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 300
  }
}
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.api_manager.id
  name             = "cognito-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    issuer   = "https://${aws_cognito_user_pool.pool.endpoint}"
    audience = [aws_cognito_user_pool_client.spa.id]
  }
}
resource "aws_apigatewayv2_integration" "reservas" {
  api_id                 = aws_apigatewayv2_api.api_manager.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "http://127.0.0.1:8080/v1/{proxy}"
  payload_format_version = "1.0"

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_route" "reservas" {
  for_each = toset(["GET", "POST", "PUT", "PATCH", "DELETE"])

  api_id               = aws_apigatewayv2_api.api_manager.id
  route_key            = "${each.value} /v1/{proxy+}"
  target               = "integrations/${aws_apigatewayv2_integration.reservas.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["${aws_cognito_resource_server.reservas.identifier}/citas"]
}

output "integracion_reservas_id" {
  value = aws_apigatewayv2_integration.reservas.id
}

resource "aws_apigatewayv2_integration" "aprobaciones" {
  api_id                 = aws_apigatewayv2_api.api_manager.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "http://127.0.0.1:8080/v1/aprobaciones/{proxy}" # provisoria
  payload_format_version = "1.0"

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_route" "aprobaciones" {
  for_each = toset(["GET", "PATCH"])

  api_id               = aws_apigatewayv2_api.api_manager.id
  route_key            = "${each.value} /v1/aprobaciones/{proxy+}"
  target               = "integrations/${aws_apigatewayv2_integration.aprobaciones.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["${aws_cognito_resource_server.reservas.identifier}/aprobaciones"]
}

output "integracion_aprobaciones_id" {
  value = aws_apigatewayv2_integration.aprobaciones.id
}
