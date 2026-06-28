# Whip Mod

A highly dynamic, physics-based 3D whip mod for Minecraft Forge.



## Features

*   **Dynamic 3D Physics:** The whip is not just a 2D texture; it is a fully 3D rendered entity that uses Verlet integration physics. It reacts to your movements, swings around corners, and collides with blocks realistically.
*   **Visceral Combat:** 
    *   **Side Slash:** Left-click for a quick, sweeping horizontal attack.
    *   **Overhead Strike:** Hold Right-click to charge your attack. The longer you hold it, the more devastating the forward crack will be!
*   **Grappling Hook:** Enchant your whip with the **Grapple** enchantment. When fully charged, aim at a block to hook onto it and pull yourself through the air like a true adventurer!
*   **Reach Enchantment:** Want a longer whip? The **Reach** enchantment dynamically adds more physical segments to your whip, increasing its reach and grapple distance up to a massive 60 segments (at Reach III).
*   **Custom Sound Effects & Visuals:** Features satisfying whip crack sounds and dynamic scaling based on charge.

## Enchantments

*   **Reach (I - III):** Increases the physical length, attack range, and grapple distance of the whip.
*   **Grapple:** Allows the whip to latch onto solid blocks to swing the player.

## Setup for Development

If you want to build this mod from the source code:

1. Clone the repository.
2. Run `./gradlew genEclipseRuns` or `./gradlew genIntellijRuns` based on your IDE.
3. Build the jar using `./gradlew build`. The output will be in `build/libs/`.

## License

This project is open-source. Feel free to contribute or use it in your modpacks!
