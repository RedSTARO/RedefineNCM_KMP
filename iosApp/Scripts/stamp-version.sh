#!/bin/sh
set -eu

REPO_ROOT="$(cd "$SRCROOT/.." && pwd)"
cd "$REPO_ROOT"

BASE_TAG="$(git describe --tags --match 'v[0-9]*.[0-9]*.[0-9]*' --abbrev=0)"
case "$BASE_TAG" in
  v*.*.*) ;;
  *)
    echo "App base version tag must match v<major>.<minor>.<patch>, got '$BASE_TAG'." >&2
    exit 1
    ;;
esac

BASE_VERSION="${BASE_TAG#v}"
COMMIT_HASH="$(git rev-parse --short=8 HEAD)"
VERSION_CODE="$(git rev-list --count HEAD)"
VERSION_NAME="$BASE_TAG.$COMMIT_HASH"
PLIST_PATH="$TARGET_BUILD_DIR/$INFOPLIST_PATH"

if [ ! -f "$PLIST_PATH" ]; then
  echo "Info.plist not found at '$PLIST_PATH'." >&2
  exit 1
fi

set_plist_string() {
  key="$1"
  value="$2"
  /usr/libexec/PlistBuddy -c "Set :$key $value" "$PLIST_PATH" 2>/dev/null ||
    /usr/libexec/PlistBuddy -c "Add :$key string $value" "$PLIST_PATH"
}

set_plist_string "CFBundleShortVersionString" "$BASE_VERSION"
set_plist_string "CFBundleVersion" "$VERSION_CODE"
set_plist_string "RedefineNCMVersionName" "$VERSION_NAME"

echo "Stamped $PRODUCT_NAME version $VERSION_NAME (build $VERSION_CODE)."
