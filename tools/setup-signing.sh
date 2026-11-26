#!/usr/bin/env bash
#
# Creates the MangaOS release keystore and uploads it to the repository's
# GitHub Actions secrets. Secret values are never printed.
#
# Usage:
#   tools/setup-signing.sh [owner/repo]
#
# Requirements: keytool (JDK 17+), gh (authenticated), base64.
set -euo pipefail

REPO="${1:-}"
KEY_ALIAS="${KEY_ALIAS:-mangaos}"
KEY_PASSWORD="${KEY_PASSWORD:-}"
STORE_PASSWORD="${STORE_PASSWORD:-}"
KEYSTORE_DIR="signing"
KEYSTORE_FILE="$KEYSTORE_DIR/mangaos-release.jks"
DNAME="${DNAME:-CN=MangaOS, OU=Mobile, O=MangaOS, L=Amman, C=JO}"

if [ -n "$(git status --porcelain "$KEYSTORE_DIR" 2>/dev/null || true)" ]; then
  echo "Refusing to continue: '$KEYSTORE_DIR' has uncommitted changes." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool was not found. Install a JDK 17 and make sure JAVA_HOME/bin is on PATH." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "The GitHub CLI (gh) is required to upload the secrets." >&2
  exit 1
fi

if [ -z "$STORE_PASSWORD" ]; then
  read -r -s -p "New keystore password: " STORE_PASSWORD; echo
fi
if [ -z "$KEY_PASSWORD" ]; then
  read -r -s -p "New key password (press Enter to reuse the keystore password): " KEY_PASSWORD; echo
  KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"
fi
if [ "${#STORE_PASSWORD}" -lt 6 ]; then
  echo "The keystore password must be at least 6 characters." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"
if [ -f "$KEYSTORE_FILE" ]; then
  echo "A keystore already exists at $KEYSTORE_FILE; refusing to overwrite it." >&2
  echo "Delete it yourself only if you are sure you can never need the existing signing key again." >&2
  exit 1
fi

keytool -genkeypair \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "$DNAME"

echo
echo "Keystore created: $KEYSTORE_FILE (excluded from git by .gitignore)."
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" \
  | grep -E "Alias name|Valid from|SHA256:" || true

echo
echo "IMPORTANT: back up $KEYSTORE_FILE and both passwords in a password manager."
echo "Without them you cannot ship updates to the same app identity."

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
base64 < "$KEYSTORE_FILE" | tr -d '\n' > "$TMP_DIR/keystore.b64"

repo_args=()
if [ -n "$REPO" ]; then
  repo_args=(--repo "$REPO")
fi

gh secret set KEYSTORE_BASE64 "${repo_args[@]}" < "$TMP_DIR/keystore.b64"
printf '%s' "$STORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD "${repo_args[@]}"
printf '%s' "$KEY_ALIAS" | gh secret set KEY_ALIAS "${repo_args[@]}"
printf '%s' "$KEY_PASSWORD" | gh secret set KEY_PASSWORD "${repo_args[@]}"

echo
echo "Uploaded KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
echo "Push to main or run the 'Android Release Build' workflow to produce signed artifacts."
