# ms-rabbit-admin

API REST para **administrar RabbitMQ** del sistema de reservas de belleza: crear y borrar colas, exchanges y bindings en tiempo de ejecución (guía 2.3.1, `RabbitAdmin`).

- Puerto **8082**.
- Protegida con el **JWT de Cognito**: solo el grupo **aprobadores** (`ROLE_APROBADORES`) puede usar `/v1/admin/**`.
- `/actuator/health` es público, para el health check de ECS.

```text
AdminController ──► RabbitAdminService ──► RabbitAdmin ──► RabbitMQ
 (valida @Valid)     (toda la lógica,       (Spring AMQP)
                      traduce errores)
```

El controller solo valida y delega: no conoce `RabbitAdmin` ni los códigos AMQP.

## Endpoints (`/v1/admin`)

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `POST` | `/queues` | `CrearColaRequest` | **201** + `{nombre, mensajes, consumidores}` |
| `GET` | `/queues/{nombre}` | — | **200** `{nombre, mensajes, consumidores}` · 404 |
| `DELETE` | `/queues/{nombre}?soloSiVacia=true` | — | **204** · 404 · 409 (protegida o con mensajes) |
| `POST` | `/queues/{nombre}/purge` | — | **200** `{cola, mensajesEliminados}` · 404 |
| `POST` | `/exchanges` | `CrearExchangeRequest` | **201** + `{nombre, tipo, durable}` |
| `DELETE` | `/exchanges/{nombre}` | — | **204** · 404 · 409 (protegido) |
| `POST` | `/bindings` | `BindingRequest` | **201** · 404 (falta el exchange o la cola) |
| `DELETE` | `/bindings` | `BindingRequest` | **204** · 404 · 409 (recurso protegido) |

`soloSiVacia` vale **true por defecto**: para borrar una cola con mensajes hay que pedirlo explícitamente con `soloSiVacia=false`. La comprobación la hace el servicio y no el flag `if-empty` de AMQP, porque las colas QUORUM no lo soportan: RabbitMQ responde `540 NOT_IMPLEMENTED`.

### Bodies

```jsonc
// CrearColaRequest
{
  "nombre": "demo.cola",          // obligatorio, 3-100 caracteres [a-zA-Z0-9._-], no empieza con "amq."
  "tipo": "QUORUM",               // CLASSIC | QUORUM (default QUORUM)
  "durable": true,                // default true; QUORUM exige true
  "dlx": "salon.citas.dlx",       // opcional: x-dead-letter-exchange
  "dlxRoutingKey": "demo",        // opcional, requiere dlx: x-dead-letter-routing-key
  "ttlMs": 60000,                 // opcional, > 0: x-message-ttl
  "maxLength": 500                // opcional, > 0: x-max-length
}

// CrearExchangeRequest
{ "nombre": "demo.topic", "tipo": "TOPIC", "durable": true }   // tipo: DIRECT | TOPIC | FANOUT | HEADERS

// BindingRequest
{ "exchange": "demo.topic", "cola": "demo.cola", "routingKey": "demo.#" }   // routingKey puede ser "" (FANOUT)
```

### Errores (ProblemDetail, RFC 9457)

| HTTP | Cuándo | Ejemplo de `detail` |
|---|---|---|
| 400 | validación del body o de la URL, JSON inválido | `"La solicitud tiene 2 error(es) de validación"` + `errores: [{campo, mensaje}]` |
| 404 | la cola o el exchange no existe | `"La cola 'demo.cola' no existe"` |
| 409 | ya existe con **otros** argumentos (PRECONDITION_FAILED), recurso protegido, cola con mensajes | `"'salon.notificaciones' es un recurso protegido del sistema de reservas: ..."` |
| 502 | RabbitMQ rechazó la operación con otro código (por ejemplo 540) | texto del broker |
| 503 | RabbitMQ no disponible | `"RabbitMQ no está disponible: no se pudo consultar la cola 'demo.cola'"` |
| 401 / 403 | sin token / sin el rol APROBADORES | — |

Crear algo que ya existe con **la misma** configuración no es un error: declarar en RabbitMQ es idempotente.

### Recursos protegidos
Las colas y exchanges que usan ms-reservas y ms-notificaciones están listados en `salon.admin.protegidos`, en `application.yml`. Son las 3 colas, sus 3 DLQ y los 3 exchanges `salon.citas.*`. Esta API no permite borrarlos ni quitarles bindings; sí permite consultarlos y vaciarlos (`purge`).

## Cómo probarlo

1. Cluster RabbitMQ local arriba (ver [rabbitmq/README.md](../rabbitmq/README.md) y `docker compose up -d`). En local, la conexión toma usuario y clave de `../rabbitmq/.env` y se conecta a cualquiera de los 3 nodos. Fuera de local, la lista de nodos va en `RABBITMQ_ADDRESSES`.
2. Arrancar con seguridad (por defecto) y un **access token** de Cognito de una usuaria del grupo `aprobadores`:
   ```bash
   ./mvnw spring-boot:run
   TOKEN="<access token>"
   curl -i -X POST http://localhost:8082/v1/admin/exchanges \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"nombre":"demo.topic","tipo":"TOPIC"}'
   ```
3. Solo para pruebas locales sin token existe el perfil **`local-sin-seguridad`**. No es el perfil por defecto, y nunca debe usarse en ECS:
   ```bash
   SPRING_PROFILES_ACTIVE=local-sin-seguridad ./mvnw spring-boot:run      # bash
   $env:SPRING_PROFILES_ACTIVE="local-sin-seguridad"; .\mvnw.cmd spring-boot:run   # PowerShell
   ```

### Ejemplos curl (sin el header Authorization, con el perfil local)

```bash
API=http://localhost:8082/v1/admin
J='Content-Type: application/json'

curl -i -X POST $API/exchanges -H "$J" -d '{"nombre":"demo.topic","tipo":"TOPIC"}'                      # 201
curl -i -X POST $API/queues    -H "$J" -d '{"nombre":"demo.cola","tipo":"QUORUM"}'                       # 201
curl -i -X POST $API/bindings  -H "$J" -d '{"exchange":"demo.topic","cola":"demo.cola","routingKey":"demo.#"}'  # 201
curl -i        $API/queues/demo.cola                                                                     # 200
curl -i -X POST $API/queues/demo.cola/purge                                                              # 200
curl -i -X DELETE $API/bindings -H "$J" -d '{"exchange":"demo.topic","cola":"demo.cola","routingKey":"demo.#"}' # 204
curl -i -X DELETE $API/queues/demo.cola                                                                  # 204
curl -i -X DELETE $API/exchanges/demo.topic                                                              # 204
curl -i -X DELETE $API/queues/salon.notificaciones                                                       # 409 protegida
curl -i -X POST $API/queues    -H "$J" -d '{"nombre":""}'                                                # 400
```

## Tests

```bash
./mvnw test
```
No necesitan RabbitMQ ni Cognito, así que corren en CI:
- **Contexto completo:** la conexión AMQP y el `JwtDecoder` son perezosos.
- **Validación por HTTP:** nombre vacío, `amq.x`, QUORUM no durable, TTL negativo, tipo desconocido y nombre inválido en la URL dan 400. También se prueba el 401 sin token, el 403 con el rol `CLIENTES` y el health público. El token se simula con `jwt()` de spring-security-test.
- **503:** se prueba apuntando RabbitMQ al puerto 1, donde no hay nada escuchando.
