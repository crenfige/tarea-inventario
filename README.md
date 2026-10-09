# Reservas de inventario

## Contexto

Te uniste al equipo de una tienda en línea. Los clientes compran desde una app móvil donde arman su carrito, lo envían y pagan. Cada producto del carrito viaja como un pedido independiente. Si la conexión es lenta, la app reenvía el pedido automáticamente hasta recibir respuesta.

Actualmente, la tienda no aparta productos mientras el cliente paga, solo los descuenta cuando el pago se aprueba. Por eso, en temporada alta, cuando cientos de clientes compran los mismos productos al mismo tiempo, puede producirse una sobreventa de unidades.

Queremos que la tienda reserve las unidades de cada pedido mientras el cliente paga.

Criterios a implementar:

- Cuando el cliente envía su carrito, sus unidades quedan reservadas y nadie más puede comprarlas.
- Si no paga a tiempo, la reserva se libera y otros clientes pueden comprar esas unidades.
- Cuando el pago se aprueba, la reserva se confirma y las unidades quedan vendidas.

El tiempo para pagar y el límite de unidades por pedido dependen de la categoría del producto. Marketing suele crear una categoría nueva cada temporada.

| Categoría    | Tiempo para pagar                          | Límite por pedido |
| ------------ | ------------------------------------------ | ----------------- |
| `STANDARD`   | 15 minutos                                 | Sin límite        |
| `PRE_ORDER`  | 24 horas, porque se paga por transferencia | Sin límite        |
| `FLASH_SALE` | 5 minutos                                  | 2 unidades        |

El equipo de compras también quiere enterarse a tiempo para reabastecer. Cuando a un producto le quedan 5 unidades disponibles o menos, debe recibir un aviso, pero no el mismo aviso repetido mientras el producto no se reabastezca. Actualmente, los avisos llegan por correo y en el futuro quieren expandirse a más canales de comunicación.

## Estado actual

Este repositorio es el servicio de inventario. La app ya está preparada para usarlo a través de un contrato, pero la implementación todavía no existe.

- `com.store.inventory.api` es el contrato que usa la app. Contiene la interfaz `InventoryService`, las categorías, las reservas, las excepciones y `StockAlertListener`, que es por donde salen los avisos a compras.
- `Inventory.create(Clock, StockAlertListener)` es el punto de entrada. Lanza `UnsupportedOperationException`.
- `InventoryServiceTest` tiene tests básicos del flujo principal.

Por ahora los datos pueden vivir en memoria. El inventario se migrará a una base de datos y el servicio correrá en varias instancias.

## Tu tarea

Implementa el servicio de reservas cumpliendo lo descrito en el contexto.

Ten en cuenta que este código lo mantendrá el equipo durante los próximos años, y que otras personas tendrán que modificarlo sin tu ayuda cuando cambien las reglas del negocio.

Entrégalo como si fuera un Pull Request listo para revisión, con lo que consideres necesario para que el equipo confíe en que funciona. Incluye un `DECISIONS.md` con tus supuestos, lo que dejaste fuera y lo que cambiarías antes de llevarlo a producción.

## Reglas del contrato

Nuestros tests automáticos se conectan a tu código mediante el contrato. Para que funcionen, por favor sigue estas reglas:

- No modifiques ningún archivo del paquete `com.store.inventory.api`.
- No cambies la firma de `Inventory.create(Clock, StockAlertListener)`. Su implementación sí es tuya.
- Los tests ya incluidos deben pasar.

Tienes la libertad de modificar todo lo demás. Puedes crear las clases, paquetes y dependencias que necesites, y usar las herramientas de tu día a día.

## Cómo correr los tests

```bash
mvn test
```

Requiere Java 21 y Maven.

## Entrega

Compártenos un repositorio con el proyecto completo.
