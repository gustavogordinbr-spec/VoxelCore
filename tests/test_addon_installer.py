from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "scripts" / "addon_installer.py"
SPEC = importlib.util.spec_from_file_location("addon_installer", MODULE_PATH)
assert SPEC and SPEC.loader
addon_installer = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = addon_installer
SPEC.loader.exec_module(addon_installer)


class AddonInstallerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addons = self.root / "addons"
        self.data = self.root / "data"
        self.addons.mkdir()
        self.limits = addon_installer.Limits(100, 10 * 1024 * 1024)

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def manifest(pack_id: str, kind: str, name: str) -> dict[str, object]:
        return {
            "format_version": 2,
            "header": {"name": name, "uuid": pack_id, "version": [1, 2, 3]},
            "modules": [
                {
                    "type": kind,
                    "uuid": "90000000-0000-4000-8000-000000000001",
                    "version": [1, 2, 3],
                }
            ],
        }

    def create_archive(self) -> Path:
        archive = self.addons / "example.mcaddon"
        behavior_id = "10000000-0000-4000-8000-000000000001"
        resource_id = "20000000-0000-4000-8000-000000000002"
        with zipfile.ZipFile(archive, "w") as bundle:
            bundle.writestr(
                "behavior/manifest.json",
                json.dumps(self.manifest(behavior_id, "data", "Behavior")),
            )
            bundle.writestr("behavior/functions/hello.mcfunction", "say hello")
            bundle.writestr(
                "resources/manifest.json",
                json.dumps(self.manifest(resource_id, "resources", "Resources")),
            )
            bundle.writestr("resources/textures/example.txt", "texture")
        return archive

    def test_installs_and_activates_behavior_and_resource_packs(self) -> None:
        self.create_archive()
        result = addon_installer.install(
            self.addons, self.data, "VoxelCore", self.limits
        )
        self.assertEqual(result, 0)

        behavior_json = self.data / "worlds/VoxelCore/world_behavior_packs.json"
        resource_json = self.data / "worlds/VoxelCore/world_resource_packs.json"
        behavior = json.loads(behavior_json.read_text())
        resource = json.loads(resource_json.read_text())
        self.assertEqual(behavior[0]["pack_id"], "10000000-0000-4000-8000-000000000001")
        self.assertEqual(resource[0]["pack_id"], "20000000-0000-4000-8000-000000000002")
        self.assertEqual(behavior[0]["version"], [1, 2, 3])

    def test_preserves_existing_activation_entries(self) -> None:
        self.create_archive()
        world = self.data / "worlds/VoxelCore"
        world.mkdir(parents=True)
        existing = {"pack_id": "30000000-0000-4000-8000-000000000003", "version": [1, 0, 0]}
        (world / "world_behavior_packs.json").write_text(json.dumps([existing]))

        addon_installer.install(self.addons, self.data, "VoxelCore", self.limits)
        entries = json.loads((world / "world_behavior_packs.json").read_text())
        self.assertEqual({entry["pack_id"] for entry in entries}, {
            existing["pack_id"],
            "10000000-0000-4000-8000-000000000001",
        })

    def test_rejects_archive_path_traversal(self) -> None:
        archive = self.addons / "unsafe.mcpack"
        with zipfile.ZipFile(archive, "w") as bundle:
            bundle.writestr("../outside.txt", "bad")
        with self.assertRaises(addon_installer.AddonError):
            addon_installer.install(
                self.addons, self.data, "VoxelCore", self.limits
            )
        self.assertFalse((self.root / "outside.txt").exists())

    def test_rejects_unsafe_world_name(self) -> None:
        with self.assertRaises(addon_installer.AddonError):
            addon_installer.safe_world_name("../world")


if __name__ == "__main__":
    unittest.main()
