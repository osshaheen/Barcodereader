# Add project specific ProGuard rules here.
# Keep ML Kit barcode model classes.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
