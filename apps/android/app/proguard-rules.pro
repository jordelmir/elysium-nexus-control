# Elysium Nexus — ProGuard / R8 rules
#
# Room: keep DAO methods and entities (reflection-based at runtime)
-keep class com.elysium.nexus.fabric.profile.db.** { *; }
-keep class com.elysium.nexus.fabric.profile.db.SignalSourceEntity { *; }

# Room schema export — keep the schema JSON resource
-keep class androidx.room.** { *; }

# Kotlin coroutines — keep structured concurrency internals
-keep class kotlinx.coroutines.** { *; }

# Compose — standard keep rules
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# JSON serialization — keep enum names for IrAction
-keepclassmembers enum com.elysium.nexus.core.device.IrAction {
    **[] $VALUES;
    public *;
}

# General Android rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
