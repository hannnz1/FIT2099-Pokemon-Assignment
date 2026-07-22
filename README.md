# FIT2099 Pokemon Console Game

A turn-based Pokemon-inspired console game developed for Monash University's FIT2099 Object-Oriented Design and Implementation unit.

The project was built on top of a teaching game engine and starter framework supplied by the unit. The engine provides reusable infrastructure such as actors, actions, maps, locations, items, weapons, menus, and the world loop. My work focused on extending that framework with the concrete Pokemon gameplay and domain logic required by the assignment.

## Project Overview

The player explores a map populated by Pokemon and non-player characters. Gameplay includes movement, combat, Pokemon-specific behaviours, capture mechanics, items, environmental effects, trading-related components, affection management, and time-based world behaviour.

The implementation uses object-oriented modelling to keep game entities and behaviours separate. Actions encapsulate player interactions, behaviours control autonomous actors, and specialised Pokemon and environment classes extend common abstractions supplied by the engine.

## Main Features

- Turn-based map exploration through a console interface
- Pokemon actors including Mudkip, Torchic, and Treecko
- Combat with weapon damage, hit probability, defeat, and item-drop handling
- Autonomous attack, follow, and wander behaviours
- Pokemon capture actions and different Poke Ball types
- Element and capability-based interaction rules
- Affection-point management
- Professor Oak and Shopkeeper NPCs
- Tradable items and candy
- Terrain and environmental elements such as lava, puddles, waterfalls, trees, hay, and craters
- Day/night and time-perception components

## My Contribution - Han Zhu

This repository contains a teaching framework whose original source headers name the framework authors, including Riordan D. Alfredo and Ian K. Felix. Those headers identify the origin of the supplied scaffold; they do not mean that the submitted gameplay requirements were already implemented.

Working within that scaffold, I contributed to the implementation and integration of the assignment-specific gameplay, including:

- extending the supplied `Actor`, `Action`, `Ground`, `Item`, and `Weapon` abstractions for the Pokemon domain;
- implementing and integrating concrete Pokemon, NPC, action, behaviour, item, environment, and world components under `src/game`;
- connecting combat, capture, elemental capability, affection, inventory, map, and time-related rules to the engine lifecycle;
- configuring the playable world, map terrain, actors, items, and starting state;
- applying inheritance, polymorphism, interfaces, composition, and behaviour/action abstractions to keep responsibilities separated; and
- contributing to the UML class and sequence-diagram documentation supplied in `docs`.

This was a team assignment. The statements above describe my contribution to implementing functionality on top of the supplied framework and do not claim authorship of the FIT2099 engine or sole authorship of every file in the team submission.

## Supplied Course Code vs Student Implementation

### Course-supplied engine

The reusable engine is located primarily under:

```text
src/edu/monash/fit2099/engine/
```

It supplies the generic game runtime, including actors, actions, maps, display, items, weapons, and turn processing.

### Assignment implementation

The Pokemon-specific implementation is located primarily under:

```text
src/game/
```

This package adapts and extends the engine for the assignment domain. It contains Pokemon, NPCs, player actions, AI behaviours, environmental objects, items, weapons, conditions, time management, and world configuration.

## Object-Oriented Design

The project demonstrates several object-oriented techniques and design ideas:

- **Command-style actions:** interactions such as attacking, capturing, talking, and trading are represented as action objects.
- **Strategy-style behaviours:** autonomous actors can select between attack, follow, and wander behaviours.
- **Inheritance and polymorphism:** concrete Pokemon and terrain types specialise shared base classes.
- **Capability-based rules:** actor and element capabilities are used to decide which interactions are permitted.
- **Manager components:** affection, time perception, world state, and backup weapons are separated from individual actors.

Design material is available in the `docs` directory, including class diagrams and an attack-action sequence diagram.

## Project Structure

```text
src/
├── edu/monash/fit2099/engine/  # Course-supplied reusable game engine
└── game/                       # Pokemon assignment implementation
    ├── actions/
    ├── actors/
    ├── behaviours/
    ├── conditions/
    ├── environments/
    ├── items/
    ├── positions/
    ├── time/
    └── weapons/
docs/                           # UML and design documentation
```

## Running the Project

The repository currently uses an IntelliJ IDEA project configuration.

1. Open the repository in IntelliJ IDEA.
2. Configure a compatible Java SDK.
3. Mark `src` as the source root if IntelliJ does not detect it automatically.
4. Run `game.Application`.
5. Follow the numbered commands displayed in the console.

## Current Limitations

This repository preserves the coursework version of the project. Before treating it as a production-ready application, the following areas should be improved:

- `TalkAction` and `TradeAction` still require complete execution logic.
- Affection updates require correction and stronger validation.
- Remaining template `TODO`, `FIXME`, and `HINT` comments should be resolved or removed.
- The project does not currently include automated JUnit tests.
- A Maven or Gradle build should be added for reproducible command-line builds.
- Generated IDE output should be excluded from version control.

## Team

- Rian Barrett
- Xinwei Li
- Han Zhu

## Academic Attribution

This project was created for educational purposes as part of FIT2099 at Monash University. The engine and starter framework were supplied by the teaching team. Pokemon-related names and concepts belong to their respective rights holders. This repository is intended only as a coursework and portfolio demonstration.
