#!/usr/bin/env bash
# Creates a local backup and uploads credentials directly to GitHub Actions secrets.
# Never commit or share the generated keystore or password.
set -euo pipefail
umask 077
repo=Mikalia0/Slate
dir="${SLATE_SIGNING_DIR:-$HOME/.local/share/slate-signing}"
key="$dir/slate-release.p12"
password="$dir/password"
command -v keytool >/dev/null
command -v gh >/dev/null
gh auth status >/dev/null
gh repo view "$repo" --json nameWithOwner --jq .nameWithOwner
mkdir -p "$dir"
chmod 700 "$dir"
if [[ -e "$key" || -e "$password" ]]; then
  if [[ ! -s "$key" || ! -s "$password" ]]; then
    echo "Incomplete signing backup in $dir; restore it before continuing." >&2
    exit 1
  fi
  echo "Reusing signing backup in $dir."
else
  echo "Creating a new Slate signing identity."
  head -c 32 /dev/urandom | base64 | tr -d '\n' > "$password"
  export SLATE_SIGNING_PASSWORD="$(cat "$password")"
  keytool -genkeypair -keystore "$key" -storetype PKCS12 \
    -storepass:env SLATE_SIGNING_PASSWORD -alias slate \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Slate"
  unset SLATE_SIGNING_PASSWORD
fi
export SLATE_SIGNING_PASSWORD="$(cat "$password")"
keytool -list -keystore "$key" -storetype PKCS12 -storepass:env SLATE_SIGNING_PASSWORD -alias slate >/dev/null
unset SLATE_SIGNING_PASSWORD
base64 < "$key" | tr -d '\n' | gh secret set SLATE_KEYSTORE_BASE64 --repo "$repo"
gh secret set SLATE_KEYSTORE_PASSWORD --repo "$repo" < "$password"
echo "Signing secrets configured. Keep an offline backup of $dir."
gh workflow run android.yml --repo "$repo" --ref main
