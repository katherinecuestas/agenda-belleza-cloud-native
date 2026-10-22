# ms-notificaciones

Microservicio de mensajería del sistema de reservas de belleza. Por ahora implementa el **Hello World de la guía 2.1.2**: una cola simple en RabbitMQ, un productor y un consumidor en el mismo servicio.

```text
POST /v1/saludos ──► SaludoProductor ──► [exchange por defecto] ──► cola salon.saludos ──► SaludoConsumidor
                     RabbitTemplate        routing key = nombre de la cola                  @RabbitListener
```

| Pieza | Archivo |
|---|---|
| Nombre de la cola (único lugar donde está escrito) | `src/main/resources/application.yml` → `salon.mensajeria.colas.saludos` |
| Declaración de la cola (`durable = true`) | `config/RabbitConfig.java` |
| Productor (log `[→] Enviado`) | `saludo/SaludoProductor.java` |
| Consumidor (log `[✓] Recibido`) | `saludo/SaludoConsumidor.java` |
| Endpoint (`202 Accepted`) | `saludo/SaludoController.java` |

Puerto: **8081** (ms-reservas usa el 8080).

## Cómo probarlo

1. **Levantar el cluster RabbitMQ de 3 nodos** (necesita Docker Desktop encendido; detalles en [rabbitmq/README.md](../rabbitmq/README.md)):
   ```powershell
   cd rabbitmq
   Copy-Item .env.example .env   # solo la primera vez; cambia la clave y la cookie
   docker compose up -d
   docker compose ps             # espera los 3 nodos "healthy"
   ```
   El panel queda en http://localhost:15672 (también en 15673 y 15674), con el usuario y la clave de `rabbitmq/.env`.

2. **Arrancar el microservicio:**
   ```powershell
   cd ms-notificaciones
   .\mvnw.cmd spring-boot:run
   ```
   En local, la conexión toma `RABBITMQ_USER` y `RABBITMQ_PASS` de `../rabbitmq/.env` y se conecta a cualquiera de los 3 nodos (`localhost:5672,5673,5674`). En otros entornos se usan las variables de entorno `RABBITMQ_ADDRESSES` (lista `host:puerto` separada por comas), `RABBITMQ_USER` y `RABBITMQ_PASS`.

3. **Enviar un saludo:**
   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/saludos `
     -ContentType "application/json" -Body '{"mensaje":"Hola salón de belleza"}'
   ```
   El log debe mostrar:
   ```text
   [→] Enviado: 'Hola salón de belleza' a la cola salon.saludos
   [✓] Recibido: 'Hola salón de belleza'
   ```
   Si el mensaje viene vacío, la respuesta es `400 Bad Request`.

4. **Ver la cola en el broker:**
   ```powershell
   docker exec rabbitmq-salon-1 rabbitmqctl list_queues name type leader members online messages consumers
   ```

## Tests

```powershell
.\mvnw.cmd test
```
El test de contexto **no necesita RabbitMQ**: desactiva el arranque de los listeners, y la conexión de Spring AMQP solo se abre al enviar o consumir un mensaje. Por eso corre en CI, donde no hay broker.

## Notas

- **Cambiar la clave después del primer arranque:** el usuario y la clave de `.env` solo se aplican la primera vez que se crea el volumen `rabbitmq_data`. Para cambiarlos después, ejecuta `docker compose down -v`, que borra el volumen y sus mensajes, y vuelve a levantar.
- **`rabbitmq/.env` está fuera de git.** El que se versiona es `.env.example`.
