#!/usr/bin/env bash
# Generates Orbit's release signing key, ONCE, and the gitignored keystore.properties that points
# the release build at it.
#
# ADR 0005: Orbit is sideloaded, so Android only accepts an update signed with the same key as the
# installed app. Lose this key and the only way forward is to uninstall, which deletes every Person
# on the phone unless a recent Backup exists. So, right after running this:
#   1. Store the keystore file AND the password printed below in the password manager.
#   2. Keep a second, offline copy of both (e.g. an encrypted USB stick kept somewhere safe).
# Never commit the keystore or keystore.properties (both are in .gitignore).
#
# Usage: scripts/generate-release-keystore.sh [keystore-path]
#   keystore-path defaults to ~/.orbit/orbit-release.jks (outside the repository).
#   ORBIT_KEY_DNAME overrides the certificate's distinguished name (default "CN=Orbit").
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
keystore="${1:-$HOME/.orbit/orbit-release.jks}"
properties="$repo_root/keystore.properties"
alias="orbit"
dname="${ORBIT_KEY_DNAME:-CN=Orbit}"

if [[ -e "$keystore" ]]; then
  echo "Refusing to overwrite the existing keystore $keystore." >&2
  echo "The release key is permanent (ADR 0005); restore it from the password manager instead." >&2
  exit 1
fi
if [[ -e "$properties" ]]; then
  echo "Refusing to overwrite $properties." >&2
  exit 1
fi
case "$(cd "$(dirname "$keystore")" 2>/dev/null && pwd || dirname "$keystore")" in
  "$repo_root"*) echo "Put the keystore outside the repository, not under $repo_root." >&2; exit 1 ;;
esac

keytool="keytool"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then keytool="$JAVA_HOME/bin/keytool"; fi
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool.exe" ]]; then keytool="$JAVA_HOME/bin/keytool.exe"; fi

# PKCS12 uses one password for the store and the key. 32 random bytes, base64 without padding.
password="$(head -c 32 /dev/urandom | base64 | tr -d '=\n' | tr '+/' '-_')"

mkdir -p "$(dirname "$keystore")"
"$keytool" -genkeypair \
  -keystore "$keystore" -storetype PKCS12 \
  -storepass "$password" -keypass "$password" \
  -alias "$alias" -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "$dname"

# Properties files treat backslashes as escapes, so write the path with forward slashes.
keystore_path="$(cd "$(dirname "$keystore")" && pwd -W 2>/dev/null || pwd)/$(basename "$keystore")"
keystore_path="${keystore_path//\\//}"
umask 077
cat > "$properties" <<EOF
# Release signing for Orbit (ADR 0005). Never commit this file.
storeFile=$keystore_path
storePassword=$password
keyAlias=$alias
keyPassword=$password
EOF

cat <<EOF

Created:
  keystore             $keystore_path
  keystore.properties  $properties

Password: $password

Now, before anything else (ADR 0005):
  1. Save the keystore file and this password in the password manager.
  2. Make an offline copy of both.
Then build a signed release with: ./gradlew assembleRelease
EOF
