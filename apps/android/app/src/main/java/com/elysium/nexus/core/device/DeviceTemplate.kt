package com.elysium.nexus.core.device

import com.elysium.nexus.fabric.infrared.IrProtocol

/**
 * A pre-built device template — one specific TV /
 * console / streaming box that the user can pick
 * from a list. The template is a **starting point**:
 * the user can later customize the button layout
 * and save the customized version as their own
 * profile.
 *
 * The catalog is **read-only** and lives in code (no
 * remote download). The list is curated for the
 * most common devices; rare devices can be added via
 * the IR Learner.
 *
 * The template includes:
 *
 *  - `category` — the [DeviceCategory] this device
 *    belongs to.
 *  - `brand` / `model` — the human-readable name.
 *  - `protocol` / `deviceAddress` / `commandAddress`
 *    — the IR protocol parameters used to encode
 *    commands for this device.
 *  - `buttons` — the default button set (Power,
 *    Vol+, Vol-, etc.).
 *  - `blurbEn` / `blurbEs` — the help text shown
 *    on the device card and in the onboarding
 *    flow.
 *
 * The [IrCommand] field is populated when the user
 * successfully connects; until then, the template
 * is a "candidate" without a real command.
 */
data class DeviceTemplate(
    val id: String,
    val category: DeviceCategory,
    val brand: String,
    val model: String,
    val protocol: IrProtocol,
    val deviceAddress: Int,
    val commandAddress: Int,
    val buttons: List<DeviceButton>,
    val blurbEn: String,
    val blurbEs: String,
    /**
     * A short hint shown next to the device name.
     * E.g. "Most common in Latin America" /
     * "Más común en Latinoamérica".
     */
    val hintEn: String? = null,
    val hintEs: String? = null
)

/**
 * A single button on a device's remote.
 *
 * The button is **semantic** — it represents a
 * logical action (Power, Volume Up, Channel Down,
 * etc.), not a raw IR code. The IR code is computed
 * from the [DeviceTemplate]'s protocol + addresses
 * + this button's `commandCode` via the IR encoder.
 *
 * Some buttons have an icon hint (power, volume,
 * etc.) — the actual icon is looked up in
 * [com.elysium.nexus.ui.control.DeviceButtonIcons].
 */
data class DeviceButton(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val iconHint: String,
    /**
     * The command code used to encode the IR
     * waveform. For NEC / NECx this is an 8-bit
     * command; for RC5 it is a 6-bit command; for
     * SonySIRC it is a 7-bit command.
     */
    val commandCode: Int,
    /**
     * The layout weight in a button grid. Higher
     * weight = larger button (the d-pad, OK, and
     * Power get weight 2; numbers get weight 1).
     */
    val layoutWeight: Int = 1,
    /**
     * Optional long-press behavior. TVs often have
     * "press and hold" for menu / voice / input
     * source. The long-press is the same IR code
     * but held for ~3x the normal time.
     */
    val longPressCommandCode: Int? = null
)

/**
 * The pre-built device catalog.
 *
 * The catalog is a [List] of [DeviceTemplate]s.
 * The user picks from this list when they
 * connect a new device. The list is filtered
 * by [DeviceCategory] (the user first picks a
 * category, then a brand/model).
 *
 * ## TV brands
 *
 * The catalog includes the 6 most common TV
 * brands worldwide (Samsung, LG, Sony, Panasonic,
 * Philips, TCL) plus a few regional brands
 * (Hisense, Vizio, Sharp, Toshiba, Sanyo, JVC).
 * The brand codes are taken from public IR
 * databases; they are **starting points** —
 * if a code doesn't work, the user can use the
 * IR Learner to capture the correct code from
 * the physical remote.
 *
 * ## Console brands
 *
 * The console categories are placeholders for
 * Phase 2+ (the Bluetooth HID transport). For
 * now they exist in the catalog so the Hub
 * screen can render them, but tapping a
 * console card shows a "coming soon" message.
 */
object DeviceCatalog {
    val all: List<DeviceTemplate> = buildList {
        // === TVs (infrared) ======================================
        // The catalog is **exhaustive** for the 40+
        // most common TV brands worldwide. Each
        // brand has a "Generic" entry that works
        // with most models; the user can also use
        // the IR Learner to capture the exact code
        // for their specific model.
        //
        // The protocol codes (device + command
        // address) are taken from public IR
        // databases. They are starting points;
        // the actual codes vary by model year.
        //
        // === Tier 1 — Major brands (most common) ===
        add(DeviceTemplate(
            id = "tv-samsung-generic",
            category = DeviceCategory.TV,
            brand = "Samsung",
            model = "Generic (2010+)",
            protocol = IrProtocol.Samsung,
            deviceAddress = 0x07,
            commandAddress = 0x02,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Samsung TVs from 2010 onward. Includes Smart TV, QLED, The Frame, Crystal UHD.",
            blurbEs = "Funciona con la mayoría de Samsung desde 2010. Incluye Smart TV, QLED, The Frame, Crystal UHD.",
            hintEn = "Most common",
            hintEs = "Más común"
        ))
        add(DeviceTemplate(
            id = "tv-lg-generic",
            category = DeviceCategory.TV,
            brand = "LG",
            model = "Generic (2010+)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x08,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most LG TVs from 2010 onward. Includes OLED, NanoCell, UHD, Smart TV.",
            blurbEs = "Funciona con la mayoría de LG desde 2010. Incluye OLED, NanoCell, UHD, Smart TV.",
            hintEn = "Popular",
            hintEs = "Popular"
        ))
        add(DeviceTemplate(
            id = "tv-sony-generic",
            category = DeviceCategory.TV,
            brand = "Sony",
            model = "Generic (Bravia)",
            protocol = IrProtocol.SonySirc,
            deviceAddress = 0x01,
            commandAddress = 0x0A,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Sony Bravia TVs. Includes OLED, LED, Android TV.",
            blurbEs = "Funciona con la mayoría de Sony Bravia. Incluye OLED, LED, Android TV.",
            hintEn = "Bravia",
            hintEs = "Bravia"
        ))
        add(DeviceTemplate(
            id = "tv-panasonic-generic",
            category = DeviceCategory.TV,
            brand = "Panasonic",
            model = "Generic (Viera)",
            protocol = IrProtocol.Kaseikyo,
            deviceAddress = 0x40,
            commandAddress = 0x01,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Panasonic Viera TVs.",
            blurbEs = "Funciona con la mayoría de Panasonic Viera."
        ))
        add(DeviceTemplate(
            id = "tv-philips-generic",
            category = DeviceCategory.TV,
            brand = "Philips",
            model = "Generic",
            protocol = IrProtocol.Rc5,
            deviceAddress = 0x05,
            commandAddress = 0x0C,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Philips TVs. Includes Ambilight, Android TV.",
            blurbEs = "Funciona con la mayoría de Philips. Incluye Ambilight, Android TV."
        ))
        add(DeviceTemplate(
            id = "tv-tcl-generic",
            category = DeviceCategory.TV,
            brand = "TCL",
            model = "Generic (Roku TV)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most TCL Roku TVs and Q-Series.",
            blurbEs = "Funciona con la mayoría de TCL Roku y Q-Series."
        ))
        add(DeviceTemplate(
            id = "tv-hisense-generic",
            category = DeviceCategory.TV,
            brand = "Hisense",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0xF2,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Hisense TVs. Includes ULED, Laser TV.",
            blurbEs = "Funciona con la mayoría de Hisense. Incluye ULED, Laser TV."
        ))
        // === Tier 2 — Common in the Americas ===
        add(DeviceTemplate(
            id = "tv-vizio-generic",
            category = DeviceCategory.TV,
            brand = "Vizio",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x20,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Vizio TVs. Includes D-Series, M-Series, P-Series, OLED.",
            blurbEs = "Funciona con la mayoría de Vizio. Incluye D-Series, M-Series, P-Series, OLED."
        ))
        add(DeviceTemplate(
            id = "tv-sharp-generic",
            category = DeviceCategory.TV,
            brand = "Sharp",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x08,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Sharp TVs (AQUOS).",
            blurbEs = "Funciona con la mayoría de Sharp (AQUOS)."
        ))
        add(DeviceTemplate(
            id = "tv-toshiba-generic",
            category = DeviceCategory.TV,
            brand = "Toshiba",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x40,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Toshiba TVs (Fire TV Edition).",
            blurbEs = "Funciona con la mayoría de Toshiba (Fire TV Edition)."
        ))
        add(DeviceTemplate(
            id = "tv-sanyo-generic",
            category = DeviceCategory.TV,
            brand = "Sanyo",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x07,
            commandAddress = 0x1A,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Sanyo TVs.",
            blurbEs = "Funciona con la mayoría de Sanyo."
        ))
        add(DeviceTemplate(
            id = "tv-jvc-generic",
            category = DeviceCategory.TV,
            brand = "JVC",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x05,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most JVC TVs.",
            blurbEs = "Funciona con la mayoría de JVC."
        ))
        add(DeviceTemplate(
            id = "tv-rca-generic",
            category = DeviceCategory.TV,
            brand = "RCA",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x0A,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most RCA TVs.",
            blurbEs = "Funciona con la mayoría de RCA."
        ))
        add(DeviceTemplate(
            id = "tv-insignia-generic",
            category = DeviceCategory.TV,
            brand = "Insignia",
            model = "Generic (Fire TV)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Insignia / Best Buy TVs (Fire TV Edition).",
            blurbEs = "Funciona con la mayoría de Insignia / Best Buy (Fire TV Edition)."
        ))
        add(DeviceTemplate(
            id = "tv-element-generic",
            category = DeviceCategory.TV,
            brand = "Element",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Element TVs.",
            blurbEs = "Funciona con la mayoría de Element."
        ))
        add(DeviceTemplate(
            id = "tv-westinghouse-generic",
            category = DeviceCategory.TV,
            brand = "Westinghouse",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Westinghouse TVs.",
            blurbEs = "Funciona con la mayoría de Westinghouse."
        ))
        add(DeviceTemplate(
            id = "tv-polaroid-generic",
            category = DeviceCategory.TV,
            brand = "Polaroid",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Polaroid TVs.",
            blurbEs = "Funciona con la mayoría de Polaroid."
        ))
        add(DeviceTemplate(
            id = "tv-emerson-generic",
            category = DeviceCategory.TV,
            brand = "Emerson",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x02,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Emerson TVs.",
            blurbEs = "Funciona con la mayoría de Emerson."
        ))
        add(DeviceTemplate(
            id = "tv-magnavox-generic",
            category = DeviceCategory.TV,
            brand = "Magnavox",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Magnavox TVs.",
            blurbEs = "Funciona con la mayoría de Magnavox."
        ))
        add(DeviceTemplate(
            id = "tv-sylvania-generic",
            category = DeviceCategory.TV,
            brand = "Sylvania",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x01,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Sylvania TVs.",
            blurbEs = "Funciona con la mayoría de Sylvania."
        ))
        // === Tier 3 — Common in Asia / Europe ===
        add(DeviceTemplate(
            id = "tv-hitachi-generic",
            category = DeviceCategory.TV,
            brand = "Hitachi",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Hitachi TVs.",
            blurbEs = "Funciona con la mayoría de Hitachi."
        ))
        add(DeviceTemplate(
            id = "tv-mitsubishi-generic",
            category = DeviceCategory.TV,
            brand = "Mitsubishi",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x06,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Mitsubishi TVs.",
            blurbEs = "Funciona con la mayoría de Mitsubishi."
        ))
        add(DeviceTemplate(
            id = "tv-apex-generic",
            category = DeviceCategory.TV,
            brand = "Apex",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Apex TVs.",
            blurbEs = "Funciona con la mayoría de Apex."
        ))
        add(DeviceTemplate(
            id = "tv-dynex-generic",
            category = DeviceCategory.TV,
            brand = "Dynex",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Dynex TVs (Best Buy house brand).",
            blurbEs = "Funciona con la mayoría de Dynex."
        ))
        add(DeviceTemplate(
            id = "tv-haier-generic",
            category = DeviceCategory.TV,
            brand = "Haier",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Haier TVs.",
            blurbEs = "Funciona con la mayoría de Haier."
        ))
        add(DeviceTemplate(
            id = "tv-sceptre-generic",
            category = DeviceCategory.TV,
            brand = "Sceptre",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Sceptre TVs.",
            blurbEs = "Funciona con la mayoría de Sceptre."
        ))
        add(DeviceTemplate(
            id = "tv-proscan-generic",
            category = DeviceCategory.TV,
            brand = "Proscan",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Proscan TVs.",
            blurbEs = "Funciona con la mayoría de Proscan."
        ))
        add(DeviceTemplate(
            id = "tv-orion-generic",
            category = DeviceCategory.TV,
            brand = "Orion",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Orion TVs.",
            blurbEs = "Funciona con la mayoría de Orion."
        ))
        add(DeviceTemplate(
            id = "tv-funai-generic",
            category = DeviceCategory.TV,
            brand = "Funai",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Funai TVs (Sylvania, Emerson, Magnavox parent).",
            blurbEs = "Funciona con la mayoría de Funai (marca padre de Sylvania, Emerson, Magnavox)."
        ))
        add(DeviceTemplate(
            id = "tv-coby-generic",
            category = DeviceCategory.TV,
            brand = "Coby",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Coby TVs.",
            blurbEs = "Funciona con la mayoría de Coby."
        ))
        add(DeviceTemplate(
            id = "tv-xiaomi-generic",
            category = DeviceCategory.TV,
            brand = "Xiaomi / Mi",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Xiaomi / Mi TVs (Mi TV, Redmi TV).",
            blurbEs = "Funciona con la mayoría de Xiaomi / Mi (Mi TV, Redmi TV)."
        ))
        add(DeviceTemplate(
            id = "tv-skyworth-generic",
            category = DeviceCategory.TV,
            brand = "Skyworth",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Skyworth TVs.",
            blurbEs = "Funciona con la mayoría de Skyworth."
        ))
        add(DeviceTemplate(
            id = "tv-konka-generic",
            category = DeviceCategory.TV,
            brand = "Konka",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Konka TVs.",
            blurbEs = "Funciona con la mayoría de Konka."
        ))
        add(DeviceTemplate(
            id = "tv-aoc-generic",
            category = DeviceCategory.TV,
            brand = "AOC",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most AOC TVs (also AOC monitors).",
            blurbEs = "Funciona con la mayoría de AOC (también monitores AOC)."
        ))
        add(DeviceTemplate(
            id = "tv-viewsonic-generic",
            category = DeviceCategory.TV,
            brand = "ViewSonic",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most ViewSonic TVs and displays.",
            blurbEs = "Funciona con la mayoría de ViewSonic."
        ))
        add(DeviceTemplate(
            id = "tv-benq-generic",
            category = DeviceCategory.TV,
            brand = "BenQ",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most BenQ TVs and projectors.",
            blurbEs = "Funciona con la mayoría de BenQ."
        ))
        add(DeviceTemplate(
            id = "tv-roku-generic",
            category = DeviceCategory.TV,
            brand = "Roku TV",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with Roku TV (built into many brands).",
            blurbEs = "Funciona con Roku TV (integrado en muchas marcas)."
        ))
        add(DeviceTemplate(
            id = "tv-firetv-generic",
            category = DeviceCategory.TV,
            brand = "Fire TV",
            model = "Generic (built-in)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with Fire TV Edition TVs (Insignia, Toshiba).",
            blurbEs = "Funciona con Fire TV Edition (Insignia, Toshiba)."
        ))
        add(DeviceTemplate(
            id = "tv-craig-generic",
            category = DeviceCategory.TV,
            brand = "Craig",
            model = "Generic",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x12,
            buttons = TV_BUTTONS,
            blurbEn = "Works with most Craig TVs.",
            blurbEs = "Funciona con la mayoría de Craig."
        ))
        // === Android TV / Streaming (Wi-Fi) ======================
        add(DeviceTemplate(
            id = "androidtv-generic",
            category = DeviceCategory.ANDROID_TV,
            brand = "Android TV",
            model = "Generic (Wi-Fi ADB)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Control any Android TV over Wi-Fi (ADB).",
            blurbEs = "Controla cualquier Android TV por Wi-Fi (ADB)."
        ))
        add(DeviceTemplate(
            id = "roku-generic",
            category = DeviceCategory.STREAMING,
            brand = "Roku",
            model = "Generic (Wi-Fi ECP)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Control any Roku over Wi-Fi (ECP).",
            blurbEs = "Controla cualquier Roku por Wi-Fi (ECP)."
        ))
        add(DeviceTemplate(
            id = "appletv-generic",
            category = DeviceCategory.STREAMING,
            brand = "Apple TV",
            model = "Generic (Wi-Fi DAAP)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Control any Apple TV over Wi-Fi (DAAP).",
            blurbEs = "Controla cualquier Apple TV por Wi-Fi (DAAP)."
        ))
        // === Consoles (Bluetooth, Phase 2+) ======================
        add(DeviceTemplate(
            id = "ps4-generic",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation 4",
            model = "DualShock 4 · CUH-1001A / 2001A / 7001A",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation 4. Launched 2013. Discontinued 2021. DualShock 4 controller. " +
                "Bluetooth HID transport coming in Phase 2 — for now you can use this as a " +
                "reference layout.",
            blurbEs = "PlayStation 4. Lanzada en 2013. Descontinuada en 2021. Control DualShock 4. " +
                "Bluetooth HID próximamente en Fase 2 — por ahora puedes usar esto como " +
                "una referencia de layout."
        ))
        add(DeviceTemplate(
            id = "ps4-pro",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation 4 Pro",
            model = "DualShock 4 · CUH-7001A / 7101A / 7201A",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation 4 Pro. Launched 2016. Enhanced 4K gaming. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "PlayStation 4 Pro. Lanzada en 2016. Gaming 4K mejorado. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "ps5-generic",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation 5",
            model = "DualSense · CFI-1000A / 1100A / 1200A",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation 5. Launched 2020. DualSense controller with haptic feedback " +
                "and adaptive triggers. Bluetooth HID transport coming in Phase 2 — " +
                "for now you can use this as a reference layout.",
            blurbEs = "PlayStation 5. Lanzada en 2020. Control DualSense con vibración háptica " +
                "y gatillos adaptivos. Bluetooth HID próximamente en Fase 2 — " +
                "por ahora puedes usar esto como una referencia de layout."
        ))
        add(DeviceTemplate(
            id = "ps5-digital",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation 5 Digital Edition",
            model = "DualSense · CFI-1000B / 1100B / 1200B",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation 5 Digital Edition (no disc drive). " +
                "Same controller as standard PS5. Bluetooth HID coming in Phase 2.",
            blurbEs = "PlayStation 5 Digital Edition (sin unidad de disco). " +
                "Mismo control que la PS5 estándar. Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "ps3-generic",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation 3",
            model = "Sixaxis / DualShock 3",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation 3. Launched 2006. Discontinued 2017. Sixaxis / DualShock 3 " +
                "controller. Bluetooth HID transport coming in Phase 2.",
            blurbEs = "PlayStation 3. Lanzada en 2006. Descontinuada en 2017. Control Sixaxis / " +
                "DualShock 3. Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "psvita-generic",
            category = DeviceCategory.PLAYSTATION,
            brand = "PlayStation Vita",
            model = "PCH-1000 / PCH-2000",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "PlayStation Vita. Launched 2011. Discontinued 2019. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "PlayStation Vita. Lanzada en 2011. Descontinuada en 2019. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "xbox-one-generic",
            category = DeviceCategory.XBOX,
            brand = "Xbox One",
            model = "Controller Model 1697 / 1698",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Xbox One. Launched 2013. Wireless Controller. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Xbox One. Lanzada en 2013. Wireless Controller. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "xbox-one-x",
            category = DeviceCategory.XBOX,
            brand = "Xbox One X",
            model = "Controller Model 1697 (Project Scorpio Edition)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Xbox One X. Launched 2017. 4K-enhanced gaming. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Xbox One X. Lanzada en 2017. Gaming 4K mejorado. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "xbox-series-x",
            category = DeviceCategory.XBOX,
            brand = "Xbox Series X",
            model = "Wireless Controller — Robot White / Carbon Black / Shock Blue",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Xbox Series X. Launched 2020. 12 teraflops. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Xbox Series X. Lanzada en 2020. 12 teraflops. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "xbox-series-s",
            category = DeviceCategory.XBOX,
            brand = "Xbox Series S",
            model = "Wireless Controller — Robot White",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Xbox Series S. Launched 2020. Compact all-digital. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Xbox Series S. Lanzada en 2020. Compacta todo-digital. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "xbox-360-generic",
            category = DeviceCategory.XBOX,
            brand = "Xbox 360",
            model = "Controller Model 1403 / 1439",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Xbox 360. Launched 2005. Discontinued 2016. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Xbox 360. Lanzada en 2005. Descontinuada en 2016. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "switch-generic",
            category = DeviceCategory.NINTENDO,
            brand = "Nintendo Switch",
            model = "HAC-001 (-01)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Nintendo Switch. Launched 2017. Joy-Con controllers. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Nintendo Switch. Lanzada en 2017. Controles Joy-Con. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "switch-lite",
            category = DeviceCategory.NINTENDO,
            brand = "Nintendo Switch Lite",
            model = "HDH-001",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Nintendo Switch Lite. Launched 2019. Handheld-only. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Nintendo Switch Lite. Lanzada en 2019. Solo portátil. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "switch-oled",
            category = DeviceCategory.NINTENDO,
            brand = "Nintendo Switch OLED",
            model = "HEG-001",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = GAMEPAD_BUTTONS,
            blurbEn = "Nintendo Switch OLED. Launched 2021. 7-inch OLED screen. " +
                "Bluetooth HID transport coming in Phase 2.",
            blurbEs = "Nintendo Switch OLED. Lanzada en 2021. Pantalla OLED de 7 pulgadas. " +
                "Bluetooth HID próximamente en Fase 2."
        ))
        add(DeviceTemplate(
            id = "pc-mac-generic",
            category = DeviceCategory.COMPUTER,
            brand = "Mac",
            model = "Generic (Wi-Fi)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Coming soon — Wi-Fi in Phase 3.",
            blurbEs = "Próximamente — Wi-Fi en Fase 3."
        ))
        add(DeviceTemplate(
            id = "pc-windows-generic",
            category = DeviceCategory.COMPUTER,
            brand = "Windows",
            model = "Generic (Wi-Fi)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Coming soon — Wi-Fi in Phase 3.",
            blurbEs = "Próximamente — Wi-Fi en Fase 3."
        ))
        add(DeviceTemplate(
            id = "pc-linux-generic",
            category = DeviceCategory.COMPUTER,
            brand = "Linux",
            model = "Generic (Wi-Fi)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x00,
            commandAddress = 0x00,
            buttons = TV_BUTTONS,
            blurbEn = "Coming soon — Wi-Fi in Phase 3.",
            blurbEs = "Próximamente — Wi-Fi en Fase 3."
        ))
        add(DeviceTemplate(
            id = "soundbar-generic",
            category = DeviceCategory.SOUNDBAR,
            brand = "Soundbar",
            model = "Generic (IR)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x02,
            buttons = TV_BUTTONS,
            blurbEn = "Control any soundbar / AV receiver over IR.",
            blurbEs = "Controla cualquier barra de sonido / receptor AV por IR."
        ))
        add(DeviceTemplate(
            id = "projector-generic",
            category = DeviceCategory.PROJECTOR,
            brand = "Projector",
            model = "Generic (IR)",
            protocol = IrProtocol.Nec,
            deviceAddress = 0x04,
            commandAddress = 0x01,
            buttons = TV_BUTTONS,
            blurbEn = "Control any projector over IR.",
            blurbEs = "Controla cualquier proyector por IR."
        ))
    }

    /**
     * Filter the catalog by category.
     */
    fun byCategory(category: DeviceCategory): List<DeviceTemplate> =
        all.filter { it.category == category }

    /**
     * Find a template by id.
     */
    fun byId(id: String): DeviceTemplate? = all.firstOrNull { it.id == id }
}

/**
 * The standard TV button set.
 *
 * The buttons are arranged in a 4x5 grid:
 *
 *  ```
 *  Power   Input    Vol+   Ch+
 *  Mute    Up       OK     Ch-
 *  Menu    Left     Right  Down
 *  Back    Vol-     Info   Last
 *  1 2 3
 *  4 5 6
 *  7 8 9
 *  - 0 +
 *  ```
 *
 * The d-pad, OK, and Power are layoutWeight 2; the
 * numbers are layoutWeight 1. The grid renderer
 * (in [com.elysium.nexus.ui.control.TvControlScreen])
 * uses the weight to size the buttons.
 */
val TV_BUTTONS: List<DeviceButton> = listOf(
    // Top row
    DeviceButton("power", "Power", "Encender", "power", 0x02, layoutWeight = 2),
    DeviceButton("input", "Input", "Entrada", "input", 0x0B),
    DeviceButton("vol_up", "Vol +", "Vol +", "vol_up", 0x07),
    DeviceButton("ch_up", "Ch +", "Canal +", "ch_up", 0x12),
    // Second row
    DeviceButton("mute", "Mute", "Mudo", "mute", 0x09),
    DeviceButton("up", "Up", "Arriba", "up", 0x0E, layoutWeight = 2),
    DeviceButton("ok", "OK", "OK", "ok", 0x0D, layoutWeight = 2),
    DeviceButton("ch_down", "Ch -", "Canal -", "ch_down", 0x10),
    // Third row
    DeviceButton("menu", "Menu", "Menú", "menu", 0x1A),
    DeviceButton("left", "Left", "Izquierda", "left", 0x0F, layoutWeight = 2),
    DeviceButton("right", "Right", "Derecha", "right", 0x11, layoutWeight = 2),
    DeviceButton("down", "Down", "Abajo", "down", 0x0C),
    // Fourth row
    DeviceButton("back", "Back", "Atrás", "back", 0x1B),
    DeviceButton("vol_down", "Vol -", "Vol -", "vol_down", 0x0A),
    DeviceButton("info", "Info", "Info", "info", 0x1C),
    DeviceButton("last", "Last Ch", "Último", "last", 0x14),
    // Numpad
    DeviceButton("n1", "1", "1", "num_1", 0x01),
    DeviceButton("n2", "2", "2", "num_2", 0x02),
    DeviceButton("n3", "3", "3", "num_3", 0x03),
    DeviceButton("n4", "4", "4", "num_4", 0x04),
    DeviceButton("n5", "5", "5", "num_5", 0x05),
    DeviceButton("n6", "6", "6", "num_6", 0x06),
    DeviceButton("n7", "7", "7", "num_7", 0x07),
    DeviceButton("n8", "8", "8", "num_8", 0x08),
    DeviceButton("n9", "9", "9", "num_9", 0x09),
    DeviceButton("n0", "0", "0", "num_0", 0x00),
    DeviceButton("dash", "-", "-", "minus", 0x0C),
    DeviceButton("plus", "+", "+", "plus", 0x1F)
)

/**
 * The standard gamepad button set. Used for the
 * PlayStation / Xbox / Switch categories. Full
 * implementation lands in Phase 2+ when the
 * Bluetooth HID transport ships; for now the
 * catalog cards show a "coming soon" message.
 */
val GAMEPAD_BUTTONS: List<DeviceButton> = listOf(
    DeviceButton("cross", "X / Cross", "X / Cross", "cross", 0x00),
    DeviceButton("circle", "O / Circle", "O / Círculo", "circle", 0x01),
    DeviceButton("square", "□ / Square", "□ / Cuadrado", "square", 0x02),
    DeviceButton("triangle", "△ / Triangle", "△ / Triángulo", "triangle", 0x03),
    DeviceButton("l1", "L1", "L1", "l1", 0x04),
    DeviceButton("r1", "R1", "R1", "r1", 0x05),
    DeviceButton("l2", "L2", "L2", "l2", 0x06),
    DeviceButton("r2", "R2", "R2", "r2", 0x07),
    DeviceButton("select", "Select / Share", "Select / Share", "select", 0x08),
    DeviceButton("start", "Start / Options", "Start / Options", "start", 0x09),
    DeviceButton("up", "Up", "Arriba", "up", 0x0A, layoutWeight = 2),
    DeviceButton("down", "Down", "Abajo", "down", 0x0B, layoutWeight = 2),
    DeviceButton("left", "Left", "Izquierda", "left", 0x0C, layoutWeight = 2),
    DeviceButton("right", "Right", "Derecha", "right", 0x0D, layoutWeight = 2)
)
