# Dragon and fire sources

The Game of Thrones theme's animations were generated with AI for Pantheon (Sume: `seedance-2.5`), starting from
`reference.png` (the dragon on a green screen; `reference_wide.png` is the same with room above for the wings):

| clip | what it is | cut by |
|---|---|---|
| `idle.mp4` | perched, breathing, glancing around (square, 5 s) | `tools/dragon_sprites.py` |
| `hopwide.mp4` | leaps up, beats its wings, lands (16:9, 5 s) | `tools/dragon_sprites.py` |
| `breath.mp4` | rears, thrusts its jaws forward and roars (square, 5 s) | `tools/dragon_sprites.py` |
| `fire.mp4` | a flame jet left to right on black (16:9, 4 s) | `tools/fire_sprites.py` |

The clips (~30 MB) aren't in git; the sheets made from them (`../*.webp`, `../dragon.js`, `../fire.js`) are.
To change the dragon, put new clips here with these names and run both tools.
