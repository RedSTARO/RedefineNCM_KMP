#!/bin/sh
set -eu

REPO_ROOT="$(cd "$SRCROOT/.." && pwd)"
cd "$REPO_ROOT"

if [ -n "${REDEFINE_NCM_BASE_TAG:-}" ] &&
   [ -n "${REDEFINE_NCM_COMMIT_HASH:-}" ] &&
   [ -n "${REDEFINE_NCM_VERSION_CODE:-}" ]; then
  BASE_TAG="$REDEFINE_NCM_BASE_TAG"
  COMMIT_HASH="$REDEFINE_NCM_COMMIT_HASH"
  VERSION_CODE="$REDEFINE_NCM_VERSION_CODE"
elif git rev-parse --git-dir >/dev/null 2>&1; then
  BASE_TAG="$(git describe --tags --match 'v[0-9]*.[0-9]*.[0-9]*' --abbrev=0)"
  COMMIT_HASH="$(git rev-parse --short=8 HEAD)"
  VERSION_CODE="$(git rev-list --count HEAD)"
else
  echo "Cannot derive app version from Git. Set REDEFINE_NCM_BASE_TAG, " >&2
  echo "REDEFINE_NCM_COMMIT_HASH, and REDEFINE_NCM_VERSION_CODE for source archives." >&2
  exit 1
fi

if ! printf '%s' "$BASE_TAG" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "App base version tag must match v<major>.<minor>.<patch>, got '$BASE_TAG'." >&2
  exit 1
fi
if ! printf '%s' "$VERSION_CODE" | grep -Eq '^[1-9][0-9]*$'; then
  echo "App version code must be a positive integer, got '$VERSION_CODE'." >&2
  exit 1
fi
if ! printf '%s' "$COMMIT_HASH" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'; then
  echo "App commit label contains unsupported characters: '$COMMIT_HASH'." >&2
  exit 1
fi

BASE_VERSION="${BASE_TAG#v}"
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
