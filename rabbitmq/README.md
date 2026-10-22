# RabbitMQ · cluster de 3 nodos

Cluster local de RabbitMQ 4 para el sistema de reservas de belleza (guía 2.3.1, clase práctica). Lo usan ms-reservas (productor), ms-notificaciones (consumidores y DLQ) y ms-rabbit-admin.

| Nodo | Contenedor | AMQP | Panel web |
|---|---|---|---|
| `rabbit@rabbitmq-1` | `rabbitmq-salon-1` | `localhost:5672` | http://localhost:15672 |
| `rabbit@rabbitmq-2` | `rabbitmq-salon-2` | `localhost:5673` | http://localhost:15673 |
| `rabbit@rabbitmq-3` | `rabbitmq-salon-3` | `localhost:5674` | http://localhost:15674 |

- **3 nodos** es el mínimo para quórum: una cola quorum sigue funcionando mientras haya mayoría (2 de 3).
- **Las 7 colas del salón son quorum:** las 3 principales, sus 3 DLQ y `salon.saludos`. Cada una tiene una réplica en cada nodo y un **líder**. `queue_leader_locator = balanced` reparte los líderes entre los nodos.
- **Los microservicios conocen los 3 nodos** por `spring.rabbitmq.addresses` (variable `RABBITMQ_ADDRESSES`) y eligen uno al azar (`address-shuffle-mode: random`). Si ese nodo se cae, se reconectan a otro.

## Archivos

| Archivo | Para qué |
|---|---|
| `docker-compose.yml` | Los 3 nodos. El ancla `x-nodo` tiene lo común: imagen, usuario, cookie, healthcheck y red `rabbitmq_cluster`. |
| `rabbitmq.conf` | Formación del cluster con `classic_config` (lista fija de nodos), reintentos de descubrimiento y `queue_leader_locator`. |
| `.env.example` | Plantilla. Se copia como `.env`. |
| `.env` | **Fuera de git.** Tiene `RABBITMQ_USER`, `RABBITMQ_PASS` y `RABBITMQ_COOKIE`. |

La **cookie de Erlang** (`RABBITMQ_COOKIE`) tiene que ser la misma en los 3 nodos, o no se reconocen entre sí. Además, es el secreto que permite administrar los nodos por la red: no la compartas.

## Levantar

```powershell
cd rabbitmq
Copy-Item .env.example .env      # solo la primera vez: cambia la clave y la cookie
docker compose up -d
docker compose ps                # espera los 3 "healthy"
```

El usuario de `.env` se crea solo la **primera** vez, con los volúmenes vacíos. Para cambiarlo después: `docker compose down -v`, que **borra los datos** de los 3 nodos, y volver a levantar.

## Ver el cluster

```powershell
# Los 3 nodos deben aparecer en "Running Nodes"
docker exec rabbitmq-salon-1 rabbitmqctl cluster_status

# Colas: tipo, nodo líder, réplicas y réplicas en línea
docker exec rabbitmq-salon-1 rabbitmqctl list_queues name type leader members online messages consumers
```

En el panel (http://localhost:15672 → *Queues*), cada cola quorum muestra su líder y sus réplicas.

## Prueba de failover

1. Levanta el cluster y ms-notificaciones, que declara las colas.
2. Busca el líder de `salon.notificaciones`:
   ```powershell
   docker exec rabbitmq-salon-1 rabbitmqctl list_queues name leader | Select-String salon.notificaciones
   ```
3. Detén **ese** nodo. Por ejemplo, si el líder es `rabbit@rabbitmq-2`:
   ```powershell
   docker stop rabbitmq-salon-2
   ```
4. Publica un `cita.aprobada` en el exchange `salon.citas.eventos` desde el panel de un nodo **vivo** (*Exchanges → salon.citas.eventos → Publish message*, con la propiedad `content_type = application/json`). En el log de ms-notificaciones deben aparecer `[NOTIFICACION CLIENTA]` y `[COMPROBANTE]`: la cola eligió un líder nuevo entre las réplicas que siguen vivas, sin perder mensajes.
5. Consulta el líder nuevo desde un nodo vivo:
   ```powershell
   docker exec rabbitmq-salon-1 rabbitmqctl list_queues name leader online
   ```
6. Vuelve a encender el nodo: `docker start rabbitmq-salon-2`. Se reincorpora solo, se pone al día con el líder y vuelve a aparecer en `online`.

Si caen 2 de los 3 nodos, las colas quorum dejan de aceptar mensajes hasta que vuelva a haber mayoría. Así evita que dos partes del cluster tomen decisiones contradictorias.

## Detener

```powershell
docker compose stop      # detiene los 3 nodos y conserva los datos
docker compose down      # borra los contenedores y conserva los volúmenes
docker compose down -v   # borra también los datos
```

> En esta máquina existe un contenedor `rabbitmq` de otro proyecto (`parcial1-dsy1107`). Por eso estos se llaman `rabbitmq-salon-N`. Los comandos de este compose no lo afectan.
