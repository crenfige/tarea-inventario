# Architectural Decisions & Assumptions

## 1. Supuestos de Dominio
- **Idempotencia de Red:** Dado que la app móvil reintenta las solicitudes ante demoras de red, `reserve` devuelve la reserva existente si el `orderId` coincide exactamente con el `sku` y `quantity` de una reserva activa previa, evitando deducciones redundantes de stock.
- **Ciclo de vida de la reserva:** Un pedido confirmado reduce el stock físico permanentemente y no puede volver a confirmarse. Las reservas no pagadas que sobrepasan el TTL de su categoría liberan su inventario automáticamente al consultar disponibilidad o intentar reservar.
- **Deduplicación de Alertas:** Se asume que el reabastecimiento ocurre formalmente mediante el método `addStock`. Una vez notificado el equipo de compras (`available <= 5`), no se vuelven a emitir avisos para ese SKU hasta que ingrese nuevo stock al almacén.

## 2. Decisiones Técnicas y Concurrencia
- **Bloqueo a nivel de producto:** Para maximizar el rendimiento en alta concurrencia, se optó por bloqueos granulares (`ReentrantLock`) por cada `ProductStock`, evitando cuellos de botella globales en todo el catálogo.
- **Desacoplamiento de eventos ("Alien Methods"):** Las notificaciones al listener `StockAlertListener` se ejecutan **fuera** de la sección crítica bloqueada. Esto previene deadlocks y degradación del rendimiento si el listener realiza I/O bloqueante (correo, webhooks o mensajería).
- **Extensibilidad (Open-Closed Principle):** Se implementó `CategoryPolicyRegistry` y `CategoryPolicy`, permitiendo que el equipo incorpore nuevas categorías de temporada con límites y TTL propios sin alterar el motor central.

## 3. Lo que se dejó fuera (Scope actual)
- **Persistencia en base de datos:** Los datos residen en memoria mediante colecciones concurrentes (`ConcurrentHashMap`), tal como solicitaban las especificaciones iniciales.
- **Worker reactivo de expiración:** La expiración se evalúa de manera perezosa (*lazy*) al consultar o modificar stock. No se agregó un scheduler en segundo plano para no introducir hilos no controlados durante la suite de pruebas.

## 4. Evolución hacia Producción
- **Persistencia y Bloqueo Distribuido:** En un despliegue multi-instancia, se reemplazará la sincronización en memoria por bloqueos distribuidos (Redis Redlock) o control optimista a nivel de base de datos (`SELECT FOR UPDATE` / versionado con `@Version`).
- **Transactional Outbox & Mensajería:** Los avisos a compras (`StockAlertListener`) se desacoplarán mediante un bus de eventos (Kafka, RabbitMQ o AWS SQS) usando el patrón Outbox para garantizar entrega confiable y desacoplar la latencia del checkout.
- **Worker de Limpieza:** Incorporación de un job programado (o TTL nativo de Redis) para limpiar reservas huérfanas de forma periódica.