# SunSzafa

**English** · [Polski](README.pl.md)

A jukebox for Paper 26.2 that plays Note Block Studio songs (`.nbs`) using note block sounds, with a song queue and a "now playing" hologram above it.

## Features

- Turn any block into a jukebox; players right-click it to pick a song from a menu
- Song queue shared by everyone nearby, with a hologram showing the current track
- Plays `.nbs` files from [Open Note Block Studio](https://opennbs.org); drop new ones in and reload, no restart needed
- Ships with a set of public-domain tunes (Für Elise, Ode to Joy, Korobeiniki, In the Hall of the Mountain King and more)
- Players can mute all jukeboxes for themselves with `/szafa wycisz`, and it's remembered

## Requirements

- Paper **26.2**
- Java **25**
- [SunCore](https://github.com/oonnyyx/SunCore)

## Installation

1. Download `SunSzafa.jar` and `SunCore.jar` from [Releases](../../releases/latest).
2. Drop both into `plugins/` and restart the server.
3. Look at a block (a jukebox looks best) and run `/szafa postaw`.
4. Put your own `.nbs` files in `plugins/SunSzafa/utwory/` and run `/szafa reload`.

## Commands

Alias: `/jukebox`. Admin commands need `szafa.admin` (op by default).

| Command | Description |
|---|---|
| `/szafa wycisz` | Mute or unmute jukeboxes for yourself (everyone) |
| `/szafa postaw` | Turn the block you're looking at into a jukebox |
| `/szafa usun` | Remove the jukebox you're looking at (the block stays) |
| `/szafa lista` | List loaded songs |
| `/szafa stop` | Stop all jukeboxes |
| `/szafa reload` | Load new songs from the folder |

In-game messages are in Polish.

## Building

```bash
git clone https://github.com/oonnyyx/SunCore && cd SunCore && mvn install && cd ..
git clone https://github.com/oonnyyx/SunSzafa && cd SunSzafa && mvn package
```

## License

[MIT](LICENSE)
