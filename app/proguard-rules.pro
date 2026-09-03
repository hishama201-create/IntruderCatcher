# CameraX تستخدم reflection داخليًا لبعض المزودات - لازم نحافظ عليها
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# FileProvider وAndroidX الأساسية
-keep class androidx.core.content.FileProvider { *; }

# DeviceAdminReceiver لازم يبقى كامل حتى يتعرف عليه النظام
-keep class com.sabri.intrudercatcher.LockAdminReceiver { *; }

# الاحتفاظ بأسماء الكلاسات التي قد يستخدمها النظام عبر intent-filter/service
-keep class com.sabri.intrudercatcher.CaptureService { *; }
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
