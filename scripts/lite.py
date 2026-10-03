#!/usr/bin/env python3
"""Bounded build and staged startup for the 8 GB laptop configuration."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'banking-ledger-lite'
INFRA = ('postgres', 'redis', 'kafka')
JAVA_APPS = ('account', 'ledger', 'fraud', 'notification', 'payment', 'gateway')
WEB_APPS = ('ui',)
ALL_SERVICES = INFRA + JAVA_APPS + WEB_APPS


def run(arguments, capture=False):
    result = subprocess.run(arguments, cwd=ROOT, text=True,
                            stdout=subprocess.PIPE if capture else None, check=True)
    return result.stdout.strip() if capture else ''


def compose(*arguments, capture=False):
    return run(['docker', 'compose', '--project-name', PROJECT, '--file',
                str(ROOT / 'compose.yml'), '--parallel', '1', *arguments], capture=capture)


def check_docker():
    if not shutil.which('docker'):
        raise RuntimeError('Docker is not installed or is not on PATH. Install/open Docker Desktop.')
    run(['docker', 'compose', 'version'], capture=True)
    info = json.loads(run(['docker', 'info', '--format', '{{json .}}'], capture=True))
    if info.get('OSType') != 'linux':
        raise RuntimeError('Switch Docker Desktop to Linux containers (WSL 2).')
    memory = info.get('MemTotal', 0) / 1024**3
    print(f'Docker VM memory: {memory:.2f} GiB', flush=True)
    print('Base container limits total 3.23 GiB; VM overhead and Windows need additional RAM.', flush=True)
    if memory < 3.5:
        print('Docker has less than 3.5 GiB available to its VM. Startup may run out of memory. '
              'Check Docker/WSL memory settings; close IntelliJ and other heavy apps.', flush=True)


def require_configuration():
    if not (ROOT / '.env').exists():
        raise RuntimeError('Local configuration is missing. Run: py scripts/lite.py start')


def prepare_kafka_volume():
    """Make the named Kafka volume writable by the official image's app user."""
    print('Preparing Kafka data-volume permissions.', flush=True)
    run([
        'docker', 'run', '--rm', '--user', '0', '--entrypoint', 'sh',
        '--volume', f'{PROJECT}_kafkadata:/data',
        'apache/kafka:3.9.1',
        '-c', 'chown -R 1000:1000 /data'
    ])


def start(skip_build=False):
    check_docker()
    run([sys.executable, str(ROOT / 'scripts/init_env.py')])
    compose('config', '--quiet')
    # Stop only this named project before using the builder, leaving its data volumes intact.
    # Graceful stopping can delay an in-flight saga; persisted state is recovered on restart.
    print('Stopping this lightweight stack before build/start; preserving its data.', flush=True)
    compose('stop')
    prepare_kafka_volume()
    if not skip_build:
        print('Building all Java modules sequentially in the 768 MiB builder.', flush=True)
        compose('--profile', 'build', 'run', '--rm', '--no-deps', 'builder')
        for service in JAVA_APPS:
            print(f'Packaging image: {service}', flush=True)
            compose('build', service)
        print('Packaging image: ui', flush=True)
        compose('build', 'ui')
    print('Starting one container at a time; each must pass its readiness check.', flush=True)
    for service in ALL_SERVICES:
        print(f'Starting {service}...', flush=True)
        compose('up', '-d', '--no-build', '--no-deps', '--wait', '--wait-timeout', '240', service)
    compose('ps')
    print('API ready at http://localhost:8080. Transaction simulator ready at http://localhost:3000.\n'
          'Next: py scripts/lite.py test\n'
          'Memory: py scripts/lite.py stats', flush=True)


def container_ids():
    return compose('ps', '-a', '-q', capture=True).split()


def stats():
    ids = compose('ps', '-q', capture=True).split()
    if not ids:
        print('No lightweight containers are running.')
        return
    run(['docker', 'stats', '--no-stream', '--format',
         '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}\t{{.PIDs}}', *ids])


def report():
    sections = ['Banking Ledger Lite diagnostic snapshot',
                compose('ps', '-a', capture=True)]
    for cid in container_ids():
        # Do not dump all inspect output: container environments contain credentials.
        sections.append(run(['docker', 'inspect', '--format',
            '{{.Name}} status={{.State.Status}} exit={{.State.ExitCode}} '
            'oom={{.State.OOMKilled}} restarts={{.RestartCount}}', cid], capture=True))
    running = compose('ps', '-q', capture=True).split()
    if running:
        sections.append(run(['docker', 'stats', '--no-stream', '--format',
                             '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}', *running], capture=True))
    path = ROOT / 'lite-diagnostics.txt'
    path.write_text('\n\n'.join(sections) + '\n', encoding='utf-8')
    print(f'Saved {path.name}; it contains status, memory and restart/OOM data, not credentials.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    startup = commands.add_parser('start', help='Build, then start the stack sequentially')
    startup.add_argument('--skip-build', action='store_true', help='Reuse existing images; not for the first run')
    for action in ['stop', 'status', 'stats', 'report', 'test', 'check']:
        commands.add_parser(action)
    logs = commands.add_parser('logs')
    logs.add_argument('service', nargs='?', choices=ALL_SERVICES)
    args = parser.parse_args()
    try:
        if args.action == 'start':
            start(args.skip_build)
        elif args.action == 'check':
            check_docker()
        else:
            require_configuration()
            if args.action == 'stop':
                compose('stop')
            elif args.action == 'status':
                compose('ps', '-a')
            elif args.action == 'stats':
                stats()
            elif args.action == 'report':
                report()
            elif args.action == 'test':
                run([sys.executable, str(ROOT / 'scripts/smoke.py'), '--wait', '600'])
                run([sys.executable, str(ROOT / 'scripts/ui_smoke.py')])
            elif args.action == 'logs':
                compose('logs', '--tail', '100', *([args.service] if args.service else []))
    except (RuntimeError, OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f'\nStopped: {error}\nNo data volumes were deleted. '
              'If containers were created, run "py scripts/lite.py report" and '
              '"py scripts/lite.py logs" to investigate.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
