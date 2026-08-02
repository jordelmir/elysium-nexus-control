package com.elysium.nexus.ui.hub

import com.elysium.nexus.core.device.ConsoleSubcategory
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate

/**
 * The §15 navigation state.
 *
 * The app is a **stack of screens**. The user
 * navigates forward by tapping a card. The user
 * navigates back with the "Atrás" button.
 *
 * The hierarchy is:
 *
 *  - **Hub** (home / category grid)
 *      - **TV category** (brand list)
 *          - **TV brand** (connect flow)
 *              - **TV control** (button grid)
 *      - **PlayStation category**
 *          - **PS5 / PS4 sub-category** (model list)
 *              - **PS5 device** (Phase 2+ Bluetooth)
 *      - **Xbox category**
 *          - **Xbox Series / One sub-category**
 *              - **Xbox device** (Phase 2+ Bluetooth)
 *      - **Nintendo category**
 *          - **Switch sub-category**
 *              - **Switch device** (Phase 2+ Bluetooth)
 *      - **Other categories** (Android TV, Streaming,
 *        Computer, Soundbar, Projector) → brand list
 *        → connect flow → control.
 *
 * The stack is a [List] of [HubDestination]. The
 * top of the stack is the currently visible screen.
 * The bottom is the Hub (the home).
 */
sealed class HubDestination {
    /** The home screen (category grid). */
    object Hub : HubDestination()

    /** The dedicated TV controls section. */
    object TvControls : HubDestination()

    /** The device-picker screen (brand list) for a category. */
    data class Category(val category: DeviceCategory) : HubDestination()

    /** A console sub-category picker (PS5, PS4, etc.). */
    data class ConsolePicker(
        val category: DeviceCategory,
        val subcategory: ConsoleSubcategory
    ) : HubDestination()

    /** A console device (a specific PS5 / PS4 / Xbox model). */
    data class ConsoleDevice(val template: DeviceTemplate) : HubDestination()

    /** The IR connection flow. */
    data class Connect(val template: DeviceTemplate) : HubDestination()

    /** The control surface (button grid). */
    data class Control(val template: DeviceTemplate) : HubDestination()

    /** The Mac/PC discovery screen. */
    object MacDiscovery : HubDestination()

    /** The Mac/PC pairing screen (with a chosen host). */
    data class MacPairing(val host: com.elysium.nexus.ui.mac.DiscoveredHost) : HubDestination()

    /** The Mac/PC control surface (trackpad + keyboard). */
    data class MacControl(val host: com.elysium.nexus.ui.mac.DiscoveredHost) : HubDestination()

    /**
     * Phase ULT.5 — the Universal Remote
     * surface. Uses Bluetooth HID to present
     * the phone as a generic keyboard + mouse
     * to any host that accepts Bluetooth HID
     * input (Mac, Windows, Linux, Android TV,
     * smart TVs, Raspberry Pi, set-top boxes).
     * No software is required on the host.
     */
    object UniversalRemote : HubDestination()

    /**
     * Phase ULT.9 — USB-C wired transport screen.
     * Shows connection status, detected device,
     * and the zero-latency control surface.
     */
    object UsbC : HubDestination()
}

/**
 * A navigation stack of [HubDestination]s.
 *
 * The stack is a regular [MutableList] wrapped
 * with helper methods. The activity owns the
 * stack as a `mutableStateOf<List<HubDestination>>`
 * so the Compose UI re-renders on changes.
 */
class HubStack(initial: HubDestination = HubDestination.Hub) {
    private val items: MutableList<HubDestination> = mutableListOf(initial)

    fun current(): HubDestination = items.last()

    fun push(destination: HubDestination) {
        items.add(destination)
    }

    fun pop() {
        if (items.size > 1) items.removeAt(items.lastIndex)
    }

    fun replaceTop(destination: HubDestination) {
        items.removeAt(items.lastIndex)
        items.add(destination)
    }

    fun snapshot(): List<HubDestination> = items.toList()
}
