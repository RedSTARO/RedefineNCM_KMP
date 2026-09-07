package com.leejlredstar.redefinencm.kmp

/**
 * The desktop host this JVM is running on.
 *
 * Five places used to read and parse `os.name` for themselves — the media-controls backend
 * selector, the SMTC guard, the click-through window style, the dynamic-cover capability check,
 * and the packaging script — each with its own spelling of the match. `Darwin` was accepted in
 * two of them and not the others, and case sensitivity differed. The parse lives here now.
 */
internal enum class DesktopOs {
    Windows,
    Linux,
    MacOs,

    /** A JVM host with no native integration of ours: BSD, Solaris, anything unrecognised. */
    Other,
    ;

    companion object {
        /** The raw `os.name`, kept for messages that report an unsupported host back to the user. */
        val currentName: String = System.getProperty("os.name").orEmpty()

        val current: DesktopOs = of(currentName)

        /**
         * Classifies an `os.name`.
         *
         * Apple JVMs have reported both `Mac OS X` and `Darwin` over the years, so both map to
         * [MacOs].
         */
        fun of(osName: String): DesktopOs = when {
            osName.contains("Windows", ignoreCase = true) -> Windows
            osName.contains("Linux", ignoreCase = true) -> Linux
            osName.contains("Mac", ignoreCase = true) ||
                osName.contains("Darwin", ignoreCase = true) -> MacOs
            else -> Other
        }
    }
}

/** The instruction set this JVM is running, as far as the native libraries we ship care. */
internal enum class DesktopArch {
    X64,
    Arm64,
    Other,
    ;

    companion object {
        val current: DesktopArch = of(System.getProperty("os.arch").orEmpty())

        fun of(architecture: String): DesktopArch = when (architecture.lowercase()) {
            "amd64", "x86_64" -> X64
            "aarch64", "arm64" -> Arm64
            else -> Other
        }
    }
}
