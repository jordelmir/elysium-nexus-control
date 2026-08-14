# Política Comercial de Garantía, Devoluciones y RMA — Elysium Nexus

Lineamientos comerciales para el canal retail en Costa Rica (Monge, Gollo, El Verdugo).

---

## 1. Términos de Garantía Comercial

- **Período de Garantía**: 12 meses de garantía limitada del fabricante para hardware (Nexus Bridge / Nexus One).
- **Cobertura**: Defectos de materiales, fallas del emisor/receptor IR, fallas del chipset BLE o corrupción de firmware.
- **SLA de Reemplazo Inmediato en Tienda**: Si un cliente devuelve un dispositivo con falla en los primeros 30 días, la tienda autoriza cambio inmediato contra SKU verificado.

---

## 2. Flujo de Certificación de Compatibilidad por Devolución

Si un consumidor reporta que su televisor (dentro del catálogo homologado del retailer) no responde al control:
1. El empleado de tienda escanea el SKU del TV del cliente.
2. Si el SKU está en estado `CORE_VERIFIED`, se realiza un diagnóstico guiado en tienda.
3. Si se detecta un patrón IR no cubierto (nueva revisión de firmware OEM), se genera un ticket prioritario a la pipeline de HIL Lab de Elysium para emitir una actualización de perfil OTA en menos de 48 horas.
