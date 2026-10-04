# Third-party notices

## Code
- Monster Tamer: Dev Share Academy, MIT; copyright notice preserved in LICENSE.
- Phaser 3.60.0: Phaser Studio / Photon Storm, MIT; license header retained in vendor file.
- WebFontLoader 1.6.28: Google / Typekit, Apache-2.0; vendor license retained separately.

## Downloaded development assets
Source: https://github.com/devshareacademy/monster-tamer/releases/download/assets/all-game-assets-v2.zip
Original asset credits: https://github.com/devshareacademy/monster-tamer#credits

| Assets | Creator | Status |
|---|---|---|
| UI panels / bars / fonts | Kenney | Included license files retained |
| Character sprites | AlexDreamer / AxulArt | Included license retained; attribution required; redistribution wording needs review before publishing |
| Maps / terrain | AxulArt, The Pixel Nook, upstream author | Source credits retained; full license review pending |
| Monsters / battle backgrounds / UI | Dev Share Academy and credited upstream artists | Development reference assets; publication clearance pending |
| Spell effects | Pimen | Publication clearance pending |
| NPC | Parabellum Games | Publication clearance pending |
| Music / sounds | xdeviruchi, leohpaz | Publication clearance pending; not used by product preview |

Assets are used locally for integration review. Repository MIT code license does not
establish a blanket license for artwork/audio. Do not publish the bundled asset
directory until its item-by-item permissions are recorded. Live quest/training/growth views now use Pokemon images described below; older standalone upstream references may still display their original creatures.

New UI: original Chinese copy and layout adaptations for Echo Islands. NineSlice
and HealthBar are directly imported from upstream code; product gameplay state
comes only through GameGateway. reference.html runs the separate upstream sample.

## Pokemon depiction update (2026-10-02)

Local demo uses PokeAPI/sprites official-artwork PNGs for portraits and generation-iii/emerald PNGs for maps. National Dex 252–260 covers Treecko/Grovyle/Sceptile, Torchic/Combusken/Blaziken, and Mudkip/Marshtomp/Swampert. Images are preserved unchanged. Each source URL and SHA256 is recorded in assets/images/pokemon/sources.json; upstream repository license is retained as UPSTREAM-LICENCE.txt.

Source: https://github.com/PokeAPI/sprites . Pokemon names/designs/artwork remain associated with their respective rights holders; an open repository does not establish blanket commercial artwork permission. These are local demo reference assets, not a grant of commercial/public redistribution rights.

## Local river / mountain demo recomposition (2026-10-03)

`river_demo.json` and `mountain_demo.json` are local Demo layouts, not upstream or official Pokemon maps. Phaser composes unchanged 64px grass, water, gravel, boulder and cliff cells from the existing `main_1_level_background.png`. Bridge planks and camp/gate overlays use Phaser geometry. Atlas/source hashes and coordinates are recorded in `growth-maps.json` and generated map JSON files. Existing terrain attribution and local-development asset limitations above continue to apply. No new third-party bitmap artwork was downloaded for these maps.

## V3 content expansion (2026-10-03)

Added National Dex 261–264, 276–279, 285–286 and 309–310: Poochyena/Mightyena, Zigzagoon/Linoone, Taillow/Swellow, Wingull/Pelipper, Shroomish/Breloom and Electrike/Manectric. Both unchanged official-artwork portraits and Emerald map sprites use the same PokeAPI/sprites source as the starters. Exact URLs, bytes and SHA256 appear in assets/images/pokemon/sources.json. Base stat/type reference is recorded in docs/expansion-species-reference.json. Short learnsets and encounter/reward/EV schedules are custom Demo rules. Existing local reference asset limits continue to apply.
