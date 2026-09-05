# Optional integrations referenced by dependency bytecode but not shipped in this JVM app.
# Keep these targeted: unexpected unresolved references must still fail the release build.
-dontwarn lombok.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# ProGuard 7.7.0's return-type specialization can produce invalid bytecode for Kotlin facade
# methods such as Okio's Source.buffer(): it narrows the descriptor to RealBufferedSource but
# leaves the BufferedSource checkcast in place. Disable only that optimizer and retain every
# other shrinking, optimization, and obfuscation pass.
-optimizations !method/specialization/returntype,*

# JNA and dbus-java inspect generic signatures, annotations, nested classes, interface methods,
# callbacks, and Structure field names at runtime.
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,*Annotation*

# Native method names and descriptor types are part of the JNI ABI (SQLite and JNA's
# jnidispatch both reach native implementations by those exact signatures).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# JNA maps these Java method names and signatures directly to native symbols. In particular,
# allowoptimization cannot be used here because ProGuard may remove parameters from methods.
-keep class com.sun.jna.* { *; }
-keep interface com.leejlredstar.redefinencm.kmp.smtc.WindowsMediaControls$Combase { *; }
-keepclassmembernames,includedescriptorclasses interface * extends com.sun.jna.Library { <methods>; }
-keepclassmembers,includedescriptorclasses interface * extends com.sun.jna.Callback { <methods>; }
-keepclassmembers,includedescriptorclasses class * extends com.sun.jna.Structure {
    <fields>;
    public <init>();
    public <init>(com.sun.jna.Pointer);
}

# JNA builds every NativeMapped return and parameter type reflectively through
# NativeMappedConverter, which requires the public no-arg constructor. The rule above matches
# only the com.sun.jna root package, so the Win32 handle types (WinDef$HRGN, WinDef$HWND, and
# the rest of PointerType's subclasses) lost theirs. The Legacy AMLL overlay windows then died
# on the EDT inside CreateRoundRectRgn with
# `Can't create an instance of class com.sun.jna.platform.win32.WinDef$HRGN`.
-keepclassmembers class * implements com.sun.jna.NativeMapped {
    public <init>();
}

# dbus-java discovers exported interfaces, methods, signals, and struct fields reflectively.
-keep @org.freedesktop.dbus.annotations.DBusInterfaceName interface * { *; }
-keepclassmembers class * implements org.freedesktop.dbus.interfaces.DBusInterface { public <methods>; }
-keep class com.leejlredstar.redefinencm.kmp.smtc.MprisService { public <methods>; }
-keep,allowshrinking,includedescriptorclasses class * extends org.freedesktop.dbus.messages.DBusSignal {
    public <init>(...);
}
-keepclassmembers,includedescriptorclasses class * extends org.freedesktop.dbus.Struct {
    public <init>(...);
}
-keepclassmembers class * {
    @org.freedesktop.dbus.annotations.Position <fields>;
}

# sqlite-jdbc's native library resolves these classes, fields, and callbacks by their original
# JNI names. Its configuration enums are also passed to EnumMap/Enum.valueOf at runtime.
-keep class org.sqlite.core.NativeDB { *; }
-keep class org.sqlite.core.DB** { *; }
-keep class org.sqlite.Function** { *; }
-keep class org.sqlite.Collation { *; }
-keep class org.sqlite.ProgressHandler { *; }
-keep class org.sqlite.BusyHandler { *; }
-keep enum org.sqlite.** { *; }

# Providers are constructed by ServiceLoader and therefore have no direct bytecode call sites.
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.network.ktor3.internal.KtorNetworkFetcherServiceLoaderTarget { *; }
-keep class io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-keep class org.freedesktop.dbus.spi.transport.ITransportProvider { *; }
-keep class org.freedesktop.dbus.transport.jre.NativeTransportProvider { *; }
-keep class org.slf4j.spi.SLF4JServiceProvider { *; }
-keep class org.slf4j.simple.SimpleServiceProvider { *; }
-keep class org.sqlite.JDBC { *; }

# JavaCPP resolves generated FFmpeg wrapper classes, native method names, annotations, and
# bundled JNI resources reflectively. Keep only the JavaCV classes used by the dynamic-cover
# decoder while preserving the complete generated JavaCPP/FFmpeg JNI surface.
-keep class org.bytedeco.javacv.Frame { *; }
# Frame.Type is an enum JavaCV switches on while seeking. ProGuard renamed it to Frame$a and
# unboxed it into a plain class, so setAudioTimestamp() threw
# `ClassCastException: org.bytedeco.javacv.Frame$a not an enum` — caught by decoding against the
# release jar, not the development classpath, which is the only place this ever reproduces.
-keep class org.bytedeco.javacv.Frame$** { *; }
-keep enum org.bytedeco.javacv.** { *; }
-keep class org.bytedeco.javacv.FrameGrabber { *; }
# FrameGrabber$** covers the nested SampleMode enum the audio decoder selects and the
# nested Exception it throws. `-keep class FrameGrabber { *; }` keeps neither: a nested
# type is its own class, and dropping SampleMode leaves the decoder unable to start.
-keep class org.bytedeco.javacv.FrameGrabber$** { *; }
-keep class org.bytedeco.javacv.FrameConverter { *; }
-keep class org.bytedeco.javacv.FFmpegFrameGrabber { *; }
-keep class org.bytedeco.javacv.FFmpegFrameGrabber$** { *; }
-keep class org.bytedeco.javacv.Java2DFrameConverter { *; }
-keep class org.bytedeco.javacpp.** { *; }
-keep class org.bytedeco.ffmpeg.** { *; }

# javacv.jar also contains optional camera/OpenCV converters that are intentionally absent
# because its dependency is non-transitive; they are unreachable from this app.
-dontwarn org.bytedeco.javacv.**
# javacpp.jar also ships Maven-plugin build tools and optional OSGi package annotations.
# Neither is loaded by the Desktop runtime, and their Maven/OSGi APIs are deliberately absent.
-dontwarn org.bytedeco.javacpp.tools.**
-dontwarn org.osgi.annotation.**
