#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
tag="${1:?release tag}"
code="${2:?version code}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# Publish only after the APK and changelog are uploaded. Confirm release metadata.
gh release view "$tag" --json tagName,isDraft,assets > "$tmp/release.json"
python3 - "$tag" "$code" "$tmp" <<'PY'
import hashlib, json, pathlib, re, sys
tag, code, tmp = sys.argv[1], int(sys.argv[2]), pathlib.Path(sys.argv[3])
release = json.loads((tmp / 'release.json').read_text())
assert release['tagName'] == tag and not release['isDraft']
apk = pathlib.Path('Slate.apk')
size = apk.stat().st_size
assert 0 < size <= 200 * 1024 * 1024
asset = next(a for a in release['assets'] if a['name'] == 'Slate.apk')
assert asset['size'] == size
match = re.fullmatch(r'dev-([1-9][0-9]*)-([1-9][0-9]*)', tag)
if match:
    assert 1000 + int(match[1]) == code
    attempt = int(match[2])
else:
    assert re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', tag)
    attempt = 1
assert 1001 <= code <= 2100000000
with apk.open('rb') as f:
    digest = hashlib.file_digest(f, 'sha256').hexdigest()
notes = pathlib.Path('changelog.txt').read_text(encoding='utf-8')[:12000]
manifest = dict(schema=1, tag=tag, versionCode=code, attempt=attempt,
                url=f'https://github.com/stevenmx2/Slate/releases/download/{tag}/Slate.apk',
                size=size, sha256=digest, notes=notes, versionNotes=f'Version code: {code}')
(tmp / 'update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
PY
# The branch contains only public release metadata. No build trigger on updates.
# Non-force push and version guard keep an older run from rolling back the feed.
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
for attempt in 1 2 3; do
  parent=""
  if git ls-remote --exit-code --heads origin refs/heads/updates > "$tmp/ref"; then
    git fetch --no-tags origin refs/heads/updates
    parent="$(git rev-parse FETCH_HEAD)"
    git show "$parent:update.json" > "$tmp/previous.json"
    if python3 - "$tmp" <<'PY'
import json, pathlib, sys
p=pathlib.Path(sys.argv[1])
a=json.loads((p/'previous.json').read_text())
b=json.loads((p/'update.json').read_text())
sys.exit(0 if (a['versionCode'],a['attempt']) >= (b['versionCode'],b['attempt']) else 1)
PY
    then echo "Update feed already current or newer"; exit 0; fi
  else
    status=$?
    if [[ "$status" != 2 ]]; then exit "$status"; fi
  fi
  blob="$(git hash-object -w "$tmp/update.json")"
  tree="$(printf '100644 blob %s\tupdate.json\n' "$blob" | git mktree)"
  parents=()
  if [[ -n "$parent" ]]; then parents=(-p "$parent"); fi
  commit="$(git commit-tree "$tree" "${parents[@]}" -m "Publish Slate update $tag")"
  if git push origin "$commit:refs/heads/updates"; then exit 0; fi
done
echo "::error::Could not publish update feed"
exit 1
