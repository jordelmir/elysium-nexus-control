# Cumplimiento Regulatorio SUTEL — Elysium Nexus OS & Hardware Bridge

Este documento establece la estrategia y matriz de cumplimiento regulatorio ante la **Superintendencia de Telecomunicaciones (SUTEL)** de Costa Rica para los dispositivos de hardware de Elysium (Elysium Nexus Bridge, Elysium Nexus One).

---

## 1. Marco Normativo Aplicable

- **Resolución SUTEL RCS-245-2023**: *"Procedimiento de solicitud de homologación de dispositivos que operan en las bandas de frecuencia de uso libre"*.
- **Bandas Espectrales de Uso Libre Utilizadas**:
  - **2.4 GHz ISM Band** (2400 MHz – 2483.5 MHz): Bluetooth Low Energy (BLE 5.x) / Wi-Fi 802.11 b/g/n.
  - **Potencia Máxima Radiada (EIRP)**: <= 100 mW (20 dBm).

---

## 2. Requisitos para Homologación de Dispositivos Radioeléctricos

Para Comercialización en Monge, Gollo y El Verdugo:

1. **Certificado de Conformidad FCC / CE**:
   - Ensayos de laboratorio acreditado (FCC Part 15 Class B o RED 2014/53/EU).
   - Test Reports de Emisiones Conducidas, Radiadas y Tasa de Absorción Específica (SAR) si aplica.
2. **Declaración Jurada del Fabricante / Importador**:
   - Especificaciones técnicas completas (módulos RF, ganancia de antena, modulación).
3. **Identificador de Homologación SUTEL**:
   - Etiquetado visible en el empaque y en el cuerpo del hardware con el número de resolución de homologación.

---

## 3. Estado de Cumplimiento

| Componente de Hardware | Módulo RF | Banda de Frecuencia | Estado SUTEL |
| :--- | :--- | :--- | :--- |
| **Elysium Nexus Bridge V1** | BLE 5.2 Micro-Module | 2.4 GHz ISM | `HOMOLOGATION_READY` |
| **Elysium Nexus One Mando** | BLE 5.2 + IR Array | 2.4 GHz ISM | `HOMOLOGATION_READY` |
| **Elysium Nexus Hub Pro** | BLE + Wi-Fi 2.4/5GHz | 2.4 GHz / 5.8 GHz | `HOMOLOGATION_PENDING` |
