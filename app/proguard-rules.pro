# Gson uses reflection for API DTOs and persisted/cache model objects.
# Keep these names stable before R8 is re-enabled for release builds.
-keepattributes Signature,*Annotation*
-keep class com.rteats.mpeineo.data.Remote*Dto { *; }
-keep class com.rteats.mpeineo.model.** { *; }

# Gson cache and Exchange API payloads are deserialized reflectively.
# Conservative keep rules for the opt-in shrink comparison only.
-keep class com.rteats.mpeineo.data.** { *; }
-keep class com.rteats.mpeineo.ui.BarsExtractionPayload { *; }
-keep class com.rteats.mpeineo.ui.BarsDiscipline { *; }
-keep class com.rteats.mpeineo.ui.BarsActivity { *; }
-keep class com.rteats.mpeineo.ui.BarsStudentDetail { *; }
# WebView invokes these entry points by name; never obfuscate them.
-keep class com.rteats.mpeineo.ui.BarsJavascriptBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# BARS WebView bridge payloads originate in JavaScript JSON. Gson relies on
# reflecting names and constructing these classes; R8 must not make them abstract.
# In particular BarsRouteDiscovery is reached only by reflective deserialization.
-keep class com.rteats.mpeineo.ui.BarsRouteDiscovery { *; }
-keep class com.rteats.mpeineo.ui.BarsPageState { *; }

# Offline campus geometry is also loaded through Gson using package-private DTOs.
# Keep serialized field names and concrete constructors in optimized Dev builds.
-keep class com.rteats.mpeineo.ui.CampusGeometry { *; }
-keep class com.rteats.mpeineo.ui.CampusFeature { *; }
