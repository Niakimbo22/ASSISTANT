# Règles ProGuard par défaut. L'AccessibilityService est référencé depuis le
# manifeste ; on conserve donc les services pour éviter tout retrait par R8.
-keep public class * extends android.accessibilityservice.AccessibilityService
