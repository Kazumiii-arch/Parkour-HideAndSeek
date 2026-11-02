# Parkour Hide & Seek

A standalone Paper minigame plugin where hiders use parkour routes to evade a seeker. Periodic **Rift Pulses** briefly reveal hiders, so players must keep moving and use the arena creatively.

## Current features

- Join/leave queue and round status commands
- Admin-started rounds with a randomly selected seeker
- 15-second seeker release window
- Hiders become seekers when tagged
- Configurable round length and lobby/hider/seeker spawn points
- Timed Rift Pulse that briefly glows all hiders
- Winner detection, cleanup, and restoration of player game modes

## Requirements

- Paper 1.20.1
- Java 17

## Build

Install Gradle 8 or newer, then run:

```bash
gradle build
```

The plugin JAR is created in `build/libs/`.

## Server setup

1. Copy the built JAR into the server's `plugins/` folder and restart Paper.
2. Join the server and stand at the desired lobby, hider, and seeker spawn points.
3. Run `/phs setlobby`, `/phs sethider`, and `/phs setseeker` as an operator.
4. Players run `/phs join`; an operator starts a round with `/phs start`.

## Commands

| Command | Description |
|---|---|
| `/phs join` | Join the next round |
| `/phs leave` | Leave the game |
| `/phs status` | Show round and player counts |
| `/phs help` | Show help |
| `/phs setlobby` | Save the lobby spawn (admin) |
| `/phs sethider` | Save the hider spawn (admin) |
| `/phs setseeker` | Save the seeker spawn (admin) |
| `/phs start` | Start a round (admin; at least two players) |
| `/phs stop` | Stop the current round (admin) |

Admin commands require `parkourhideseek.admin` (granted to operators by default).

## Configuration

Edit `plugins/ParkourHideAndSeek/config.yml` to set `min-players` and `round-seconds`. Set spawn locations in-game with the commands above; locations are saved automatically.

> Early playable prototype: build and test on your target Paper server before using it publicly. Arena protection, automatic round scheduling, and multi-arena support are not included yet.
