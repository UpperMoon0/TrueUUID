#!/usr/bin/env python3
"""Explicit owner-fork Forge-only publisher; never grants all-loader approval."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]


def validate(manifest, release, targets):
    assert manifest['repository'] == 'UpperMoon0/TrueUUID', 'wrong fork repository'
    assert manifest['target'] == 'forge-1.20.1', 'only Forge 1.20.1 is approved here'
    assert targets['release_ready'] is False, 'all-loader approval must remain separate'
    assert not any(t['release'] for t in targets['targets']), 'unexpected loader publication approval'
    assert manifest['tag'] == 'v' + manifest['version'] == release['tag_name'], 'wrong version tag'
    assert manifest['fileName'] == f"trueuuid-{manifest['version']}-forge-1.20.1.jar", 'wrong artifact'
    assert len(release['assets']) == 1, 'this path publishes exactly one Forge artifact'
    asset = release['assets'][0]
    assert asset['name'] == manifest['fileName'], 'unexpected release asset'
    assert asset['size'] == manifest['size'], 'artifact size changed'
    assert asset['digest'] == 'sha256:' + manifest['sha256'], 'artifact digest changed'
    assert asset['state'] == 'uploaded', 'upload incomplete'
    return asset


def output(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def api(*args):
    return json.loads(output('gh', 'api', *args))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('--check-jar', type=Path)
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'release/forge-fork.json').read_text())
    targets = json.loads((ROOT / 'release/targets.json').read_text())
    if args.check_jar:
        data = args.check_jar.read_bytes()
        assert len(data) == manifest['size'] and hashlib.sha256(data).hexdigest() == manifest['sha256']
        print('Verified canonical Forge-only JAR identity')
        return
    repo, tag = manifest['repository'], manifest['tag']
    assert output('gh', 'repo', 'view', repo, '--json', 'nameWithOwner', '--jq', '.nameWithOwner') == repo
    head = output('git', 'rev-parse', 'HEAD')
    assert api(f'repos/{repo}/git/ref/heads/main')['object']['sha'] == head, 'publish only merged main'
    # Changes to the publication helper must not silently change tested JAR inputs.
    subprocess.run(['git', 'diff', '--exit-code', manifest['testedCommit'], head, '--',
                    'platform/forge/1.20.1', 'shared/protocol', 'shared/presentation',
                    'build.gradle', 'gradle.properties', 'branding', 'LICENSE',
                    'docs/development/nstut-1.3.1.md'], cwd=ROOT, check=True)
    releases = api(f'repos/{repo}/releases?per_page=100')
    matches = [r for r in releases if r['tag_name'] == tag]
    assert len(matches) == 1, 'release missing or ambiguous'
    release = matches[0]
    validate(manifest, release, targets)
    with tempfile.TemporaryDirectory(prefix='trueuuid-forge-release-') as directory:
        subprocess.run(['gh', 'release', 'download', tag, '--repo', repo, '--pattern',
                        manifest['fileName'], '--dir', directory], cwd=ROOT, check=True)
        data = (Path(directory) / manifest['fileName']).read_bytes()
        assert len(data) == manifest['size'] and hashlib.sha256(data).hexdigest() == manifest['sha256']
    if not args.publish:
        print('Forge-only release preflight passed; no publication performed')
        return
    if release['draft']:
        refs = api(f'repos/{repo}/git/matching-refs/tags/{tag}')
        exact = [r for r in refs if r['ref'] == 'refs/tags/' + tag]
        if exact:
            assert exact[0]['object']['type'] == 'commit' and exact[0]['object']['sha'] == head, 'existing tag differs; never move it'
        else:
            api('--method', 'POST', f'repos/{repo}/git/refs', '-f', 'ref=refs/tags/' + tag, '-f', 'sha=' + head)
        body = (f'TrueUUID {manifest["version"]} — UpperMoon0 Forge 1.20.1-only fork.\n\n'
                'Fixes rejected/disconnected login acceptance through cleanup and delayed callbacks. '
                'Existing premium/offline policy and migration semantics are preserved.\n\n'
                'Seventeen controller regressions: fourteen fail upstream, all pass patched. '
                'All 132 Forge/shared Linux tests and the full verification matrix pass. '
                'Packaged JAR is reproducible. Full TFG live-login acceptance is not claimed.\n\n'
                'No Fabric, NeoForge or server-plugin release is approved or published by this path. '
                'The upstream all-loader release_ready flag remains false.\n\n'
                f'SHA256: {manifest["sha256"]}; bytes: {manifest["size"]}.\n'
                f'Source: https://github.com/{repo}/tree/{head}\n'
                'License: LGPL-3.0; upstream authors and source history retained.\n')
        release = api('--method', 'PATCH', f'repos/{repo}/releases/{release["id"]}',
                      '-f', 'target_commitish=' + head, '-f', 'name=TrueUUID 1.3.1 — Forge 1.20.1 login-race fix',
                      '-f', 'body=' + body, '-F', 'draft=false', '-F', 'prerelease=false')
    validate(manifest, release, targets)
    assert release['draft'] is False
    url = f'https://github.com/{repo}/releases/download/{tag}/{manifest["fileName"]}'
    with urlopen(url, timeout=60) as response:
        data = response.read(manifest['size'] + 1)
    assert len(data) == manifest['size'] and hashlib.sha256(data).hexdigest() == manifest['sha256']
    print(json.dumps({'url': release['html_url'], 'download': url, 'source': head,
                      'size': len(data), 'sha256': manifest['sha256'], 'scope': manifest['target']}))


if __name__ == '__main__':
    main()
