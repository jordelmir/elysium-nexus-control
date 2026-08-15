# TV Node release rules — R8 keep list. The app is a thin system-facing
# daemon: keep the default rules plus explicit keeps for the serviced
# components referenced from XML (never obfuscate names declared in
# the manifest or accessibility/IME configs).
-keep class com.elysium.nexus.tvnode.** { *; }