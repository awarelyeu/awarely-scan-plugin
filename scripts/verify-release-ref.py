"""Require a GitHub-verified release ref pointing to the checked-out commit."""
import argparse
import json
import re
import subprocess


def verify(ref, expected, get_object):
    obj = ref.get('object', {})
    if obj.get('type') == 'tag':
        tag = get_object('tags', obj.get('sha', ''))
        if tag.get('verification', {}).get('verified') is not True:
            raise ValueError('Annotated release tags must have a verified signature')
        obj = tag.get('object', {})
    if obj.get('type') != 'commit' or obj.get('sha') != expected:
        raise ValueError('Release ref does not point to the checked-out commit')
    commit = get_object('commits', expected)
    if commit.get('verification', {}).get('verified') is not True:
        raise ValueError('Release commit must have a GitHub-verified signature')


def api(path):
    result = subprocess.run(['gh', 'api', path], check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repository')
    parser.add_argument('tag')
    parser.add_argument('commit')
    args = parser.parse_args()
    if args.repository != 'awarelyeu/awarely-scan-plugin':
        raise ValueError('Only the configured GitHub release repository is permitted')
    if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-alpha\.[1-9][0-9]*)?', args.tag):
        raise ValueError('Invalid release tag')
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        raise ValueError('Expected an exact commit SHA')
    base = 'repos/' + args.repository + '/git/'

    def get_object(kind, sha):
        if not re.fullmatch(r'[0-9a-f]{40}', sha):
            raise ValueError('Invalid release object SHA')
        return api(base + kind + '/' + sha)

    verify(api(base + 'ref/tags/' + args.tag), args.commit, get_object)
    print('Verified release ref and commit signature')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, TypeError, subprocess.SubprocessError):
        raise SystemExit('Release ref verification failed; inspect tag/commit signatures and GitHub access') from None
