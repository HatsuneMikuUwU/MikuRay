-repackageclasses
-allowaccessmodification
-dontobfuscate
-keep class com.miku.ray.** { *; }
-keep class com.yalantis.ucrop.** { *; }

# Removed Kotlin's built-in Null and Exception checks
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    
# Added: Removed internal error throwing (NPE, Assert, IllegalArgument, etc.)
    static void throwUninitializedPropertyAccessException(java.lang.String);
    static void throwNpe();
    static void throwNpe(java.lang.String);
    static void throwJavaNpe();
    static void throwJavaNpe(java.lang.String);
    static void throwAssert();
    static void throwAssert(java.lang.String);
    static void throwIllegalArgument();
    static void throwIllegalArgument(java.lang.String);
    static void throwIllegalState();
    static void throwIllegalState(java.lang.String);
    static void needClassReification();
}

# Remove requireNotNull, checkNotNull, etc. from compilation results
-assumenosideeffects class kotlin.PreconditionsKt {
    static void check(boolean);
    static void check(boolean, kotlin.jvm.functions.Function0);
    static void require(boolean);
    static void require(boolean, kotlin.jvm.functions.Function0);
}

# Remove print stack trace call
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
    public void printStackTrace(java.io.PrintStream);
    public void printStackTrace(java.io.PrintWriter);
}

# Remove Kotlin Metadata if this is an APK build (not an AAR)
-keepattributes !kotlin.Metadata

# Essential attributes to keep debug/stacktrace readable
-keepattributes SourceFile, LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions, InnerClasses

# SnakeYAML references java.beans.* (Introspector, PropertyDescriptor, etc.)
# for optional JavaBean-style introspection. These classes don't exist on
# Android and the code path is never exercised, so silence R8's missing-class
# errors instead of trying to keep classes that can't be provided.
-dontwarn java.beans.**
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
