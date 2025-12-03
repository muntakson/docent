# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep JUpnP classes
-keep class org.jupnp.** { *; }
-keep class org.seamless.** { *; }

# Keep JmDNS classes
-keep class javax.jmdns.** { *; }

# Keep model classes
-keep class com.docent.bot.model.** { *; }
