# El user pool es el "tenant": el directorio donde viven los usuarios
# y el servidor de autorización que emite los tokens.
resource "aws_cognito_user_pool" "pool" {
  name = "agenda-belleza-katherine"

  # Plan Essentials: necesario para la opción B (scopes por grupo con Lambda)
  user_pool_tier = "ESSENTIALS"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # false = los usuarios pueden REGISTRARSE solos desde el frontend
  admin_create_user_config {
    allow_admin_create_user_only = false
  }
}

resource "aws_cognito_user_group" "clientes" {
  name         = "clientes"
  user_pool_id = aws_cognito_user_pool.pool.id
  description  = "Solicitantes: crean, editan y cancelan sus citas"
}

resource "aws_cognito_user_group" "aprobadores" {
  name         = "aprobadores"
  user_pool_id = aws_cognito_user_pool.pool.id
  description  = "Especialistas y administradores: aprueban o rechazan citas"
}
resource "aws_cognito_user_pool_domain" "hosted_ui" {
  domain       = "agenda-belleza-katherine"
  user_pool_id = aws_cognito_user_pool.pool.id

  # 1 = Hosted UI clásica. La versión 2 exige definir un estilo
  # de marca, o la pantalla de login queda en blanco.
  managed_login_version = 1
}
# Angular es un CLIENTE PÚBLICO: su código se descarga completo en el
# navegador y no puede guardar un secreto. Por eso no hay secreto y el
# flujo es Authorization Code + PKCE.
resource "aws_cognito_user_pool_client" "spa" {
  name         = "spa-angular"
  user_pool_id = aws_cognito_user_pool.pool.id

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  supported_identity_providers         = ["COGNITO"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]

  # Deben coincidir EXACTAMENTE con lo que envíe Angular, barra final incluida
  callback_urls = ["http://localhost:4200/"]
  logout_urls   = ["http://localhost:4200/"]

  explicit_auth_flows = ["ALLOW_USER_SRP_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]

  # No revela si un correo existe o no al fallar el login
  prevent_user_existence_errors = "ENABLED"

  access_token_validity = 60
  id_token_validity     = 60
  token_validity_units {
    access_token = "minutes"
    id_token     = "minutes"
  }
}
