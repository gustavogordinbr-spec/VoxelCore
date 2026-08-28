#!/usr/bin/env python3
"""Instala add-ons Bedrock com validação e ativa os packs no mundo escolhido."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import stat
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable


ARCHIVE_SUFFIXES = {".mcaddon", ".mcpack", ".zip"}
BEHAVIOR_MODULES = {"data", "script"}
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)


class AddonError(RuntimeError):
    """Erro legível de validação ou instalação."""


@dataclass(frozen=True)
class Limits:
    max_files: int
    max_unpacked_bytes: int


@dataclass(frozen=True)
class Pack:
    source: Path
    pack_id: str
    version: tuple[int, int, int]
    kind: str
    name: str

    @property
    def activation(self) -> dict[str, object]:
        return {"pack_id": self.pack_id, "version": list(self.version)}


def safe_world_name(value: str) -> str:
    value = value.strip()
    if not value or value in {".", ".."} or len(value) > 64:
        raise AddonError("LEVEL_NAME vazio ou maior que 64 caracteres")
    if "/" in value or "\\" in value or "\x00" in value:
        raise AddonError("LEVEL_NAME nao pode conter barras ou byte nulo")
    return value


def is_symlink(info: zipfile.ZipInfo) -> bool:
    mode = info.external_attr >> 16
    return stat.S_ISLNK(mode)


def safe_extract(archive: Path, destination: Path, limits: Limits) -> None:
    if not zipfile.is_zipfile(archive):
        raise AddonError(f"arquivo nao e um ZIP valido: {archive.name}")

    with zipfile.ZipFile(archive) as bundle:
        infos = bundle.infolist()
        if len(infos) > limits.max_files:
            raise AddonError(
                f"{archive.name} excede o limite de {limits.max_files} arquivos"
            )

        unpacked = sum(info.file_size for info in infos)
        if unpacked > limits.max_unpacked_bytes:
            limit_mb = limits.max_unpacked_bytes // (1024 * 1024)
            raise AddonError(f"{archive.name} excede {limit_mb} MB descompactados")

        for info in infos:
            normalized = info.filename.replace("\\", "/")
            member = PurePosixPath(normalized)
            if member.is_absolute() or ".." in member.parts or is_symlink(info):
                raise AddonError(f"caminho inseguro dentro de {archive.name}")

        bundle.extractall(destination)


def read_manifest(path: Path) -> dict[str, object]:
    if path.stat().st_size > 2 * 1024 * 1024:
        raise AddonError(f"manifest.json grande demais: {path}")
    try:
        parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise AddonError(f"manifest.json invalido em {path}: {exc}") from exc
    if not isinstance(parsed, dict):
        raise AddonError(f"manifest.json precisa conter um objeto: {path}")
    return parsed


def parse_version(value: object, manifest: Path) -> tuple[int, int, int]:
    if (
        not isinstance(value, list)
        or len(value) != 3
        or any(type(item) is not int or item < 0 for item in value)
    ):
        raise AddonError(f"versao invalida em {manifest}")
    return value[0], value[1], value[2]


def pack_from_manifest(manifest_path: Path) -> Pack:
    manifest = read_manifest(manifest_path)
    header = manifest.get("header")
    modules = manifest.get("modules")
    if not isinstance(header, dict) or not isinstance(modules, list):
        raise AddonError(f"header/modules ausentes em {manifest_path}")

    pack_id = header.get("uuid")
    if not isinstance(pack_id, str) or not UUID_RE.fullmatch(pack_id):
        raise AddonError(f"UUID de header invalido em {manifest_path}")

    version = parse_version(header.get("version"), manifest_path)
    module_types = {
        str(module.get("type", "")).lower()
        for module in modules
        if isinstance(module, dict)
    }
    if "resources" in module_types:
        kind = "resource"
    elif module_types & BEHAVIOR_MODULES:
        kind = "behavior"
    else:
        raise AddonError(
            f"tipo de pack nao reconhecido em {manifest_path}: "
            f"{', '.join(sorted(module_types)) or 'nenhum'}"
        )

    raw_name = header.get("name", manifest_path.parent.name)
    name = raw_name if isinstance(raw_name, str) else manifest_path.parent.name
    return Pack(manifest_path.parent, pack_id.lower(), version, kind, name)


def archive_candidates(root: Path) -> Iterable[Path]:
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise AddonError(f"links simbolicos nao sao aceitos: {path}")
        if path.is_file() and path.suffix.lower() in ARCHIVE_SUFFIXES:
            yield path


def collect_packs(addons: Path, workspace: Path, limits: Limits) -> list[Pack]:
    roots: list[Path] = []
    queue: list[tuple[Path, int]] = []

    for item in sorted(addons.iterdir()):
        if item.name.startswith("."):
            continue
        if item.is_symlink():
            raise AddonError(f"links simbolicos nao sao aceitos: {item}")
        if item.is_dir():
            roots.append(item)
            queue.extend((archive, 1) for archive in archive_candidates(item))
        elif item.suffix.lower() in ARCHIVE_SUFFIXES:
            queue.append((item, 0))

    extracted_archives: set[Path] = set()
    sequence = 0
    while queue:
        archive, depth = queue.pop(0)
        archive_key = archive.resolve()
        if archive_key in extracted_archives:
            continue
        if depth > 3:
            raise AddonError(f"arquivos compactados aninhados demais: {archive.name}")
        extracted_archives.add(archive_key)
        sequence += 1
        destination = workspace / f"archive-{sequence:04d}"
        destination.mkdir()
        safe_extract(archive, destination, limits)
        roots.append(destination)
        queue.extend(
            (nested, depth + 1) for nested in archive_candidates(destination)
        )

    manifests: dict[Path, None] = {}
    for root in roots:
        for manifest in sorted(root.rglob("manifest.json")):
            manifests[manifest.resolve()] = None

    packs = [pack_from_manifest(path) for path in manifests]
    seen: dict[str, Pack] = {}
    for pack in packs:
        previous = seen.get(pack.pack_id)
        if previous and (previous.version != pack.version or previous.kind != pack.kind):
            raise AddonError(
                f"UUID duplicado com versao/tipo diferente: {pack.pack_id}"
            )
        seen[pack.pack_id] = pack
    return sorted(seen.values(), key=lambda pack: (pack.kind, pack.pack_id))


def load_activation(path: Path) -> list[dict[str, object]]:
    if not path.exists():
        return []
    try:
        content = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise AddonError(f"JSON de ativacao invalido em {path}: {exc}") from exc
    if not isinstance(content, list):
        raise AddonError(f"JSON de ativacao precisa ser uma lista: {path}")
    return [entry for entry in content if isinstance(entry, dict)]


def write_activation(path: Path, additions: Iterable[Pack]) -> None:
    merged: dict[str, dict[str, object]] = {}
    without_id: list[dict[str, object]] = []
    for entry in load_activation(path):
        pack_id = entry.get("pack_id")
        if isinstance(pack_id, str):
            merged[pack_id.lower()] = entry
        else:
            without_id.append(entry)
    for pack in additions:
        merged[pack.pack_id] = pack.activation

    output = without_id + [merged[key] for key in sorted(merged)]
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    temporary.replace(path)


def install_pack(pack: Pack, data: Path) -> Path:
    folder = "resource_packs" if pack.kind == "resource" else "behavior_packs"
    destination = data / folder / f"vc_{pack.pack_id.replace('-', '')}"
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".installing")
    if temporary.exists():
        shutil.rmtree(temporary)
    shutil.copytree(pack.source, temporary)
    if destination.exists():
        shutil.rmtree(destination)
    temporary.replace(destination)
    return destination


def install(addons: Path, data: Path, world: str, limits: Limits) -> int:
    addons.mkdir(parents=True, exist_ok=True)
    data.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="voxelcore-addons-") as temp:
        packs = collect_packs(addons, Path(temp), limits)

        if not packs:
            print("VoxelCore: nenhum add-on encontrado; nada para instalar.")
            return 0

        for pack in packs:
            destination = install_pack(pack, data)
            print(
                f"VoxelCore: {pack.kind} pack instalado: {pack.name} -> {destination}"
            )

        world_dir = data / "worlds" / safe_world_name(world)
        behavior = [pack for pack in packs if pack.kind == "behavior"]
        resources = [pack for pack in packs if pack.kind == "resource"]
        if behavior:
            write_activation(world_dir / "world_behavior_packs.json", behavior)
        if resources:
            write_activation(world_dir / "world_resource_packs.json", resources)

        print(
            f"VoxelCore: {len(packs)} pack(s) ativado(s) no mundo "
            f"{world_dir.name}."
        )
    return 0


def positive_integer(value: str, variable: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise AddonError(f"{variable} precisa ser um numero inteiro") from exc
    if parsed <= 0:
        raise AddonError(f"{variable} precisa ser maior que zero")
    return parsed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--addons", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--world", required=True)
    args = parser.parse_args()

    limits = Limits(
        max_files=positive_integer(
            os.getenv("MAX_ADDON_FILES", "20000"), "MAX_ADDON_FILES"
        ),
        max_unpacked_bytes=positive_integer(
            os.getenv("MAX_ADDON_UNPACKED_MB", "1024"),
            "MAX_ADDON_UNPACKED_MB",
        )
        * 1024
        * 1024,
    )
    try:
        return install(args.addons, args.data, safe_world_name(args.world), limits)
    except AddonError as exc:
        print(f"VoxelCore: erro: {exc}", file=os.sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
