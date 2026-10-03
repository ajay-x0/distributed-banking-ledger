"""Workflow tests use mocked Docker commands; they are not container runtime tests."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('lite', Path(__file__).parents[1] / 'lite.py')
lite = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lite)


class LiteWorkflowTest(unittest.TestCase):
    def test_build_finishes_before_any_service_starts(self):
        with patch.object(lite, 'check_docker'), patch.object(lite, 'prepare_kafka_volume') as prepare, patch.object(lite, 'run') as run, patch.object(lite, 'compose') as compose:
            lite.start()
        calls = [c.args for c in compose.call_args_list]
        stop = calls.index(('stop',))
        builder = calls.index(('--profile', 'build', 'run', '--rm', '--no-deps', 'builder'))
        builds = [i for i, c in enumerate(calls) if c[0] == 'build']
        starts = [i for i, c in enumerate(calls) if c[0] == 'up']
        self.assertLess(stop, builder)
        self.assertLess(builder, min(builds))
        self.assertLess(max(builds), min(starts))
        self.assertEqual([calls[i][-1] for i in starts], list(lite.ALL_SERVICES))
        for i in starts:
            self.assertIn('--wait', calls[i])
            self.assertIn('--no-build', calls[i])
        self.assertFalse(any('down' in c for c in calls))
        prepare.assert_called_once_with()

    def test_skip_build_still_uses_staged_startup(self):
        with patch.object(lite, 'check_docker'), patch.object(lite, 'prepare_kafka_volume'), patch.object(lite, 'run'), patch.object(lite, 'compose') as compose:
            lite.start(skip_build=True)
        calls = [c.args for c in compose.call_args_list]
        self.assertFalse(any(c[0] == 'build' or 'builder' in c for c in calls))
        self.assertEqual(len([c for c in calls if c[0] == 'up']), len(lite.ALL_SERVICES))

    def test_compose_is_scoped_to_lite_project(self):
        with patch.object(lite, 'run', return_value='') as run:
            lite.compose('stop')
        args = run.call_args.args[0]
        self.assertEqual(args[args.index('--project-name') + 1], 'banking-ledger-lite')
        self.assertEqual(args[args.index('--parallel') + 1], '1')
        self.assertEqual(args[args.index('--file') + 1], str(lite.ROOT / 'compose.yml'))

    def test_build_failure_prevents_partial_start(self):
        def invoke(*args, **kwargs):
            if 'builder' in args:
                raise RuntimeError('Simulated build failure')
        with patch.object(lite, 'check_docker'), patch.object(lite, 'prepare_kafka_volume'), patch.object(lite, 'run'), patch.object(lite, 'compose', side_effect=invoke) as compose:
            with self.assertRaises(RuntimeError):
                lite.start()
        self.assertFalse(any(c.args[0] == 'up' for c in compose.call_args_list))

    def test_readiness_failure_prevents_starting_dependents(self):
        def invoke(*args, **kwargs):
            if args[0] == 'up' and args[-1] == 'postgres':
                raise RuntimeError('Postgres not ready')
        with patch.object(lite, 'check_docker'), patch.object(lite, 'prepare_kafka_volume'), patch.object(lite, 'run'), patch.object(lite, 'compose', side_effect=invoke) as compose:
            with self.assertRaises(RuntimeError):
                lite.start(skip_build=True)
        starts = [c.args[-1] for c in compose.call_args_list if c.args[0] == 'up']
        self.assertEqual(starts, ['postgres'])

    def test_windows_container_engine_rejected(self):
        with patch.object(lite.shutil, 'which', return_value='docker'), patch.object(lite, 'run', side_effect=['v2', json.dumps({'OSType': 'windows', 'MemTotal': 8 * 1024**3})]):
            with self.assertRaisesRegex(RuntimeError, 'Linux containers'):
                lite.check_docker()

    def test_kafka_volume_is_prepared_for_image_user(self):
        with patch.object(lite, 'run') as run:
            lite.prepare_kafka_volume()
        arguments = run.call_args.args[0]
        self.assertIn('banking-ledger-lite_kafkadata:/data', arguments)
        self.assertIn('apache/kafka:3.9.1', arguments)
        self.assertIn('chown -R 1000:1000 /data', arguments)

    def test_stats_does_not_collect_other_projects(self):
        with patch.object(lite, 'compose', return_value='') as compose, patch.object(lite, 'run') as run:
            lite.stats()
        run.assert_not_called()

    def test_diagnostic_report_uses_only_selected_inspect_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(lite, 'ROOT', Path(directory)), patch.object(lite, 'container_ids', return_value=['test-id']), patch.object(lite, 'compose', return_value=''), patch.object(lite, 'run', return_value='gateway oom=false') as run:
                lite.report()
            args = run.call_args.args[0]
            self.assertIn('--format', args)
            self.assertIn('.State.OOMKilled', args[args.index('--format') + 1])
            self.assertNotIn('.Config.Env', ' '.join(args))
            self.assertIn('oom=false', (Path(directory) / 'lite-diagnostics.txt').read_text())


if __name__ == '__main__':
    unittest.main()
