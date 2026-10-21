#!/usr/bin/env bash
# Checks the Java runtime a desktop package bundles, from the `release` file jlink writes into it:
# it must be the JDK the CI job set up (not the Gradle daemon's pinned one), and it must carry the
# modules the app needs at run time.
#
# Usage: check_packaged_runtime.sh <runtime release file> <setup-java version, e.g. 21.0.12+7>
set -euo pipefail

release_file="$1"
expected_version="${2%%+*}"

if [ ! -f "$release_file" ]; then
    echo "No packaged runtime at $release_file" >&2
    exit 1
fi

value_of() {
    sed -n "s/^$1=\"\(.*\)\"\$/\1/p" "$release_file" | tr -d '\r'
}

version=$(value_of JAVA_VERSION)
modules=$(value_of MODULES)

if [ "$version" != "$expected_version" ]; then
    echo "The package bundles Java $version, not the $expected_version set up for this job" >&2
    exit 1
fi

# jdk.accessibility is the Java Access Bridge: without it Windows screen readers find nothing.
for module in java.desktop jdk.accessibility; do
    case " $modules " in
        *" $module "*) ;;
        *)
            echo "The packaged runtime lacks $module" >&2
            exit 1
            ;;
    esac
done

echo "Packaged runtime: Java $version, $(printf '%s\n' $modules | wc -l | tr -d ' ') modules"
