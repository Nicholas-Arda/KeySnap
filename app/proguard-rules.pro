# Keep crash stack traces readable; the mapping file is uploaded to Play, not shipped.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The bridge's classes are dexed separately and executed by app_process in another process, but
# :app compiles against them so both sides derive identical key codes and action ids. R8 must not
# rename what the shipped bridge.dex refers to by name.
-keep class com.example.bridge.** { *; }

# Moshi codegen adapters are resolved by name (Foo -> FooJsonAdapter) at runtime.
-keep class com.example.data.**JsonAdapter { *; }
-keepclassmembers class com.example.data.** { <init>(...); }

# Bouncy Castle is used directly (libadb's AES/HKDF primitives, one self-signed X.509 cert), never
# registered as a java.security.Provider, so R8 sees every reference and no keep rule is needed.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
