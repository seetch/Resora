<div align="center">
  <h1>Resora</h1>
  <p>Save server resource packs without leaving the game.</p>
  <p><strong>Fabric · Client-side · Minecraft 1.21.4–26.3</strong></p>
</div>

---

Resora keeps resource-pack capture simple: join a server, let Minecraft download its pack, then press <kbd>I</kbd>. Save it on demand or let Resora handle every new server pack automatically.

## Highlights

- A compact native Minecraft menu on <kbd>I</kbd>.
- Manual and automatic resource-pack saving.
- Separate archives for every server address and port.
- Optional replacement of an existing server archive.
- Optional ZIP normalization for packs with malformed or duplicate entries.
- Lightweight log tailing that reads only newly appended data.
- No Fabric API dependency.

## Supported versions

| Minecraft | Java |
|:--|:--:|
| 1.21.4 | 21+ |
| 1.21.8 | 21+ |
| 1.21.11 | 21+ |
| 26.2 | 25+ |
| 26.3 | 25+ |

## Installation

1. Install Fabric Loader 0.19.3 or newer.
2. Download the Resora JAR matching your Minecraft version.
3. Place it in the instance's `mods` folder.

Fabric API is not required.

## Usage

1. Join a server and accept its resource pack.
2. Press <kbd>I</kbd> to open Resora.
3. Select **Save current resource pack**.

The button becomes available after Minecraft finishes caching a pack during the current connection. Saved archives appear in `resora-packs/` inside the game directory.

## Settings

| Setting | Behavior |
|:--|:--|
| Automatic saving | Saves each newly cached server pack without another click. |
| Replace existing pack | Overwrites the archive already stored for that server. |
| Normalize ZIP archive | Rebuilds the pack from its central directory for broader archive compatibility. |

Settings are stored in `config/resora.json`.

## How it works

Minecraft stores downloaded server packs under `downloads/` and appends completed download records to `downloads/log.json`. Resora follows only new records, links them to the current server from `logs/latest.log`, and copies the cached archive to `resora-packs/<host>_<port>.zip`.

When ZIP normalization is enabled, the archive is rebuilt through `java.util.zip.ZipFile`. Minecraft-visible entries are preserved while malformed sequential data and shadowed duplicate entries are left behind.

## Building

Build every supported release:

```bash
./gradlew buildAllVersions
```

The five release JARs are collected in `build/versions/`.

Build one version:

```bash
./gradlew -Pminecraft_version=1.21.11 build
```

## Responsible use

Only save or inspect packs you own or have permission to audit. Resource-pack assets may be protected by their authors' licenses.

## License

Released under CC0-1.0.
