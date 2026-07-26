# procgen — Design Document

**Status:** proposal · **Scope:** standalone desktop sandbox/game · **Stack:** Java 21, Swing/Java2D (kept)

This document covers the evolution of procgen from a terrain-generation demo into an
independently simulated, observable world with zoom, zone selection, nested procedural
generation (world → zone → building → interior), and Songs-of-Syx-style fast-forward.

---

## 1. Where the project is today

The current pipeline is: `Main` builds a `WorldGenerator` (min-conflicts solver over a
256×256 toroidal tile grid, rules learned from a concentric-rings reference pattern) and a
`WorldPanel`, then `SimulationOrchestrator` drives everything with a Swing `Timer` (25 ms):
`step()` → `repaint()`, all on the Event Dispatch Thread.

This is fine for a generation demo, but four structural issues block every feature you want:

1. **No camera.** `WorldPanel.tileSize` changes on mouse wheel, but `paintComponent` always
   stretches the whole cached image to the panel — zoom is currently cosmetic-only. Zoom,
   pan, and zone selection all require a real world↔screen transform.
2. **Full image rebuild every paint.** `rebuildImage()` runs inside `paintComponent`,
   regenerating all 65k pixels per frame even when nothing changed. Won't survive layers,
   agents, or bigger maps.
3. **Simulation and rendering share one thread and one clock.** The Swing Timer is both the
   tick rate and the frame rate. Fast-forward at 25x–250x is impossible on the EDT — the UI
   would freeze.
4. **Mutable shared state.** The renderer reads the same `List<Tile>` the generator mutates.
   Safe today (single thread), but the moment simulation moves off the EDT this becomes a
   data race. (Minor: `WorldPanel.redraw()` creates a detached `JLabel` that is never added
   to any container, and the second `setText` overwrites the first — dead code to remove.)

None of this is criticism of the min-conflicts work — the generator itself is solid and
stays. The work is around it.

---

## 2. Target architecture at a glance

```
┌────────────────────────────  Simulation thread  ────────────────────────────┐
│  fixed-timestep loop (e.g. 60 ticks/s × speed multiplier 1/5/25/250)        │
│                                                                             │
│  CommandQueue ──▶ [apply player commands] ──▶ [run systems in order]        │
│   (from EDT)        select zone, build,        ecology → construction →     │
│                     pause, speed, ...          agents → economy             │
│                                                                             │
│                 every N ms: publish WorldSnapshot (immutable-ish)           │
└──────────────────────────────────│──────────────────────────────────────────┘
                                   ▼  AtomicReference<WorldSnapshot>
┌────────────────────────────  EDT (Swing)  ──────────────────────────────────┐
│  render Timer (~30–60 fps): read latest snapshot, draw through Camera       │
│  input listeners: translate mouse/keys into Commands, enqueue               │
└─────────────────────────────────────────────────────────────────────────────┘
```

Three rules that everything below follows:

- **The core is headless.** Generation and simulation live in packages that never import
  `java.awt`/`javax.swing`. The Swing shell is a thin client that could be replaced.
- **One-way data flow.** Simulation publishes snapshots; rendering never touches live state.
  Input flows the other way as command objects; the EDT never mutates world state directly.
- **Everything generated is a pure function of (seed, coordinates).** Zones and building
  interiors are generated lazily and reproducibly; only *changes* (player actions, sim
  history) are stored.

---

## 3. Simulation loop and fast-forward (the Songs of Syx model)

Songs of Syx does **not** switch to an abstracted statistical model when fast-forwarding.
It runs the exact same per-entity simulation with a tick multiplier (25x, then 250x),
renders at a fixed ~30 fps regardless, and lets the CPU be the ceiling. The design
consequence: *there is one simulation model, and it must be cheap per tick.* That's the
model to copy.

### 3.1 The loop

Dedicated simulation thread (a plain `Thread` or single-thread `ScheduledExecutorService` —
not the Swing Timer), running a classic fixed-timestep accumulator:

```
BASE_TICK = 1/60 s of game time
speed ∈ {0 (paused), 1, 5, 25, 250}       // volatile, set from UI

loop:
    targetTicksThisFrame = speed × elapsedWall / BASE_TICK
    run up to targetTicksThisFrame ticks, but stop after ~budgetMs (e.g. 30 ms)
    publish snapshot if ≥ snapshotInterval since last publish
    sleep the remainder
```

Key properties:

- **Ticks are fixed-size.** Fast-forward means *more ticks per wall second*, never bigger
  dt. Simulation results are identical at any speed — only wall-clock time differs. This
  is what keeps fast-forward trustworthy (and testable).
- **Graceful degradation.** At 250x with a heavy world, the loop simply achieves fewer
  ticks than requested. Show effective speed in the UI ("250x (achieving 180x)") like Syx
  effectively does — never lengthen the timestep to catch up.
- **Snapshot cadence is independent of tick rate.** At 250x there's no point publishing
  250 snapshots/s; publish at most ~30/s. Rendering cost stays constant regardless of speed.

### 3.2 Making ticks cheap (this is 90% of fast-forward)

- **Staggered updates ("N-slicing").** Not every system runs every tick. Give each system a
  cadence and slice its work: e.g. vegetation updates 1/32 of the map per tick (full map
  every ~0.5 s of game time); each agent "thinks" every 30 ticks, offset by `id % 30`, but
  moves every tick. Songs of Syx handles tens of thousands of citizens exactly this way.
- **Struct-of-arrays for hot data.** For agents at scale, parallel primitive arrays
  (`int[] x, y; short[] state; ...`) beat object-per-agent for cache behavior. You already
  wrote a SparseSet/DenseStore ECS in multinet-server — the *pattern* (not the code, per
  your standalone constraint) is the right one here. Start with plain objects; switch the
  hot loops to arrays when profiling says so.
- **Spatial hash** for neighbor queries (agents near fire, villagers near a job site).
  Uniform grid of buckets, rebuilt or maintained incrementally per tick.
- **Layered cadences by domain** (see §6): agents tick fast, ecology ticks in slices,
  economy ticks once per in-game day. A "day" of game time then costs mostly agent ticks.

### 3.3 Time model

Introduce a proper `GameClock`: `long tick`, with derived calendar (ticks → hour/day/season/
year). All systems read time from it; seasons and daily economic ticks hang off it. Pause is
`speed = 0` — the render loop keeps running (you can still pan/zoom/select while paused,
which is also how you'll want to inspect the world).

### 3.4 Threading and state hand-off

- **Commands in:** `ConcurrentLinkedQueue<Command>` (records: `SetSpeed`, `SelectZone`,
  `GenerateZone`, `EnterBuilding`, ...). EDT enqueues; sim thread drains at tick start.
  This is your write path — the EDT *never* mutates world state.
- **Snapshots out:** `AtomicReference<WorldSnapshot>`. Publish-by-replacement; the renderer
  grabs the reference each frame. Never lock the world for rendering.
- **Cheap snapshots:** don't deep-copy the map every publish. Terrain changes rarely —
  snapshot it as (immutable base + list of changed chunks this interval). Agents are the
  volatile part — copy just their render data (position, type, facing) into a flat array.
  A few thousand agents × ~16 bytes is nothing.
- **Determinism:** one seeded `RandomGenerator` owned by the sim thread (Java 21's
  `RandomGeneratorFactory` — e.g. `Xoroshiro128PlusPlus`), *never* shared with rendering
  or generation-preview code. Same seed + same commands ⇒ same world. This gives you
  save-game-as-seed-plus-command-log for free later, and makes bugs reproducible.

---

## 4. Camera, zoom, and rendering

### 4.1 Camera

A small pure class in the core (`double camX, camY` in world tile units, `double zoom` in
pixels-per-tile, clamped ~0.25..64):

- `worldToScreen(wx, wy)` / `screenToWorld(sx, sy)` — every draw call and every mouse event
  goes through these. This single abstraction is what makes zone selection trivial later
  (mouse click → `screenToWorld` → tile coords → zone lookup).
- **Zoom to cursor:** on wheel, convert cursor to world coords, apply zoom factor
  (multiplicative, e.g. ×1.15 per notch — not the current additive step), re-convert, and
  pan so the world point under the cursor stays put. This is the difference between zoom
  that feels right and zoom that feels broken.
- **Pan:** middle/right-drag mapped to camera translation; clamp to world bounds.

### 4.2 Layered, chunked rendering

Replace the single cached image with layers, composited in `paintComponent`:

1. **Terrain layer** — the world split into 32×32-tile chunks, each pre-rendered to a
   `BufferedImage` with a dirty flag. Simulation changes (tile changed by fire, construction)
   mark chunks dirty via the snapshot's changed-chunk list; only dirty *and visible* chunks
   re-render. Only chunks intersecting the camera view get drawn at all.
2. **Structures layer** — roads, buildings; same chunking, changes rarely.
3. **Agents layer** — drawn fresh every frame from the snapshot's flat agent array
   (positions change constantly; caching is pointless).
4. **Overlay/UI layer** — selection rectangle, zone borders, debug heat-maps (suitability,
   moisture — invaluable for tuning generation), stats text.

**Level-of-detail by zoom:** zoomed out (< ~2 px/tile), skip agents or draw settlements as
dots; mid-zoom, agents are 2–4 px quads; zoomed in (≥ ~16 px/tile), sprite detail. This
keeps worst-case draw cost bounded and doubles as a design tool — the far view is your
"observe the world" mode.

Keep `VALUE_INTERPOLATION_NEAREST_NEIGHBOR` (already set) so tiles stay crisp when scaled.

### 4.3 Practical Swing notes

- Rendering stays a pull model: render `Timer` (~33 ms) calls `repaint()`; `paintComponent`
  reads the latest snapshot. Never push repaints from the sim thread.
- `setDoubleBuffered(true)` (default) suffices; no need for manual `BufferStrategy` unless
  you later go full-screen.
- Fix now: remove `rebuildImage()` from `paintComponent`; delete the dead `redraw()` label
  code; move iteration/conflict stats into the overlay layer.

---

## 5. Multi-scale generation: world → zone → building → interior

### 5.1 The seed hierarchy (the core idea)

Everything derives deterministically from coordinates:

```
worldSeed                                  (chosen / random at world creation)
zoneSeed     = mix(worldSeed, zoneX, zoneY)          // e.g. SplitMix64 of packed coords
buildingSeed = mix(zoneSeed, lotIndex)
interiorSeed = mix(buildingSeed, floorIndex)
```

(Use a proper 64-bit mix — SplitMix64 finalizer or similar — not `Objects.hash`, which has
far too few bits and will visibly correlate neighboring zones.)

Consequences:

- **Lazy generation.** Nothing below world scale exists until the player (or the sim) needs
  it. Entering a building generates its interior on the spot, identically every visit.
- **Tiny persistence.** A save file is: worldSeed + sim state + a delta store of player/sim
  modifications keyed by (scale, coords). Unvisited content costs zero bytes.
- **Fast-forward friendly.** The coarse sim can decide "settlement X built a granary"
  without materializing the granary's interior; the interior generator just has to *respect
  committed facts* (footprint, type, owner) when it eventually runs. This "coarse facts
  first, detail on demand" contract is the load-bearing design decision of the whole
  document — every generator takes (seed, constraints) where constraints are whatever the
  simulation has already committed.

### 5.2 Zone selection flow

1. World generated (existing min-conflicts pass) → world map mode.
2. Player zooms in; past a zoom threshold, show the zone grid overlay (e.g. 32×32-tile
   zones over the 256×256 world → an 8×8 zone grid; tune sizes freely).
3. Click → `screenToWorld` → zone coords → `SelectZone` command → sim marks it, overlay
   highlights it, side panel shows zone info (biome mix, water access, suitability score).
4. "Generate/settle zone" → `GenerateZone` command → zone-scale generator runs (on the sim
   thread, or a worker thread with results merged at a tick boundary if generation is slow)
   → zone becomes an active settlement site the simulation develops over time (§6).

### 5.3 Generation algorithms per scale

**World scale — keep min-conflicts, add fields.** The min-conflicts pass gives believable
adjacency. Augment it with two cheap scalar fields sampled per tile — *elevation* and
*moisture* (fractal value/simplex noise, 4–5 octaves) — because downstream systems want
continuous values, not just tile classes:

- Rivers: pick high-elevation sources, walk downhill (steepest descent with slight
  meander noise) to water; carve `SHALLOW_WATER`. Instant realism, ~50 lines.
- Biome refinement (optional): reclassify tiles Whittaker-style from
  (elevation, moisture) — swamp, dry grassland, dense forest — while the min-conflicts pass
  cleans up adjacency afterward. Your solver becomes the *constraint-repair* stage of the
  pipeline, which is a genuinely nice architecture (noise proposes, min-conflicts disposes).
- Suitability map for settlements: score(tile) = w₁·nearWater + w₂·flatness + w₃·resources
  − w₄·nearOtherSettlement. Render it as a debug overlay; both the player UI ("good spot")
  and the autonomous sim (§6) read the same map.

**Zone scale — settlement layout.**
- *Site points:* Poisson-disk sampling (Bridson) for well-spaced building sites; weight
  acceptance by the suitability field.
- *Roads first, lots second:* connect the zone's entry points and center with A* over a
  terrain-cost field (water expensive, slopes costly, existing roads cheap). Roads that
  follow terrain are the single biggest realism win at this scale. Secondary streets: either
  recursive perpendicular branching off main roads, or a small L-system if you want
  organic sprawl.
- *Lots:* along road frontage, place rectangular lots (greedy strip subdivision, or BSP for
  block interiors). Each lot gets a `buildingSeed` and a type from settlement needs
  (house/farm/workshop/granary...).
- Fields/farms: flood-fill flat grass near the settlement, subdivide into irregular
  quads.

**Building scale — footprint and shell.** From lot + type + seed: footprint polygon
(rectangle, or 2–3 fused rectangles for L/T shapes), door on the road-facing edge, material
from local biome (wood near forest, stone near mountains — free flavor from data you have).

**Interior scale — rooms.** When the player enters (a separate scene/panel with its own
camera — do *not* embed interiors into the world grid; different resolution, different
lifecycle):
- *BSP subdivision* of the footprint into rooms — the workhorse; guaranteed watertight,
  trivial door placement on shared walls, 100 lines.
- *Room-graph grammar* on top for semantics: building type ⇒ required rooms and adjacency
  (house: bedroom–hall, kitchen–hall; workshop: shop–storage). Generate the graph, then let
  BSP realize it (assign BSP leaves to graph nodes; reject-and-retry with a different seed
  offset on mismatch — cheap at this size).
- *Furnishing:* per-room-type constraint placement (bed against wall, table centered, no
  door blocking). Simple rejection sampling with a handful of rules gets you 90% of the way.
  WFC on furnishing-tile patterns is a fun later upgrade, not a v1 need.

---

## 6. The living world: four systems, one design pattern

You want all four pillars (ecology, settlements/construction, agents, economy). Best
practice from the genre (Syx, Dwarf Fortress, RimWorld): **each pillar is a system with its
own cadence and its own state, communicating through the shared world + committed facts,
not through each other's internals.** Suggested order of implementation is the order below —
each one gives the observer something new to watch, and each provides substrate for the next.

| System | Model | Cadence | Cost |
|---|---|---|---|
| Ecology | cellular automata + scalar fields | sliced (1/32 map per tick) | very low |
| Settlements | stage machine + planner (agent-less) | per in-game day | negligible |
| Construction | job list consumed by agents (or timers pre-agents) | per tick (few jobs) | low |
| Agents | needs-based utility AI, staggered thinking | move 1/tick, think 1/30 ticks | the budget |
| Economy | stock-and-flow per settlement + trade graph | per in-game day | negligible |

**Ecology (build first).** Vegetation as a CA: grass spreads to adjacent bare tiles,
forest matures from old grass near forest, density regrows over time. Wildfire: ignition
(lightning, rare Poisson event; dry season raises rate), spread by wind/dryness, burn to
scorched, regrow — you've built exactly this in multinet-server's wildfire sim, so this
pillar is mostly porting knowledge you already have. Wildlife: don't simulate animals
individually at world scale; keep per-region population pools (logistic growth, predation
coupling — discrete Lotka-Volterra with clamping). Materialize individual animals only
near the camera or near hunters (simulation LOD, same trick as render LOD).

**Settlements & construction (the visible magic).** A settlement is: location, population
number, stockpiles, growth stage (camp → hamlet → village → town), and a *planner* that
runs once per in-game day: if needs unmet (housing, food, storage) and resources available,
commit a construction job (lot + building type + progress 0). Construction progresses
tick-by-tick — first via simple timers, later by requiring agent labor — and buildings
appear frame-by-frame as scaffolding → shell → done. This pillar is what makes observation
and fast-forward *satisfying*: at 25x you watch a hamlet become a town. Note the planner is
agent-less: settlements grow believably even before any agent AI exists, and Syx-style
population numbers drive everything.

**Agents (add once the world gives them jobs).** Needs-based utility AI: needs decay
(hunger, sleep, work); on think-tick, pick the highest-scoring action (eat, sleep at home,
work at job site, haul to construction); actions are multi-tick tasks with pathfinding
(A* on the tile grid; cache paths; spatial-hash lookups for nearby targets). Bound agent
count by settlement population, and represent most of the population statistically (Syx
does full simulation, but it earns it with SoA arrays and staggered ticks — start with
"N visible representative agents per settlement" and raise N as your profiler allows).

**Economy (cheap depth).** Per settlement: production per day = f(buildings, population,
season), consumption = f(population), stockpiles clamp. Trade: settlements with surplus/
deficit pair up over a road-distance graph; a trade sends a caravan *agent* (visible
world event!) or, pre-agents, just transfers stock with a delay. Prices can wait; flows
before markets. This system is nearly free at any fast-forward speed since it ticks daily.

**Events glue it together:** append events (`FIRE_STARTED`, `BUILDING_COMPLETED`,
`TRADE_ARRIVED`) to a ring buffer in the snapshot; the UI shows a ticker. For an
observation game, the event feed *is* half the gameplay.

---

## 7. Proposed package structure

```
com.ntros.procgen
  core/        GameClock, SimulationLoop, CommandQueue, Command records, Speed
  world/       World, Tile, chunks, scalar fields (elevation/moisture), zones
  gen/         SeedMix, WorldGen (min-conflicts + noise + rivers), ZoneGen,
               BuildingGen, InteriorGen   — all pure (seed, constraints) → structure
  sim/         Systems: EcologySystem, SettlementSystem, ConstructionSystem,
               AgentSystem, EconomySystem; events
  snapshot/    WorldSnapshot, AgentRenderData, changed-chunk lists
  ui/          Swing only: WorldPanel, InteriorPanel, Camera, layers, input→commands,
               overlays, SimulationOrchestrator (render timer + wiring)
```

`core/world/gen/sim/snapshot` must not import AWT/Swing. Enforce by habit or a tiny
ArchUnit test.

---

## 8. Roadmap

Each phase is independently shippable and playable/observable.

1. **Decouple.** Sim thread + fixed timestep + command queue + snapshot publishing; Swing
   Timer becomes render-only. Speed controls 0/1/5/25/250 with effective-speed display.
   *(Foundation for literally everything else.)*
2. **Camera.** Real zoom-to-cursor/pan through `worldToScreen`; chunked terrain layer with
   dirty flags; stats overlay. Fixes the fake zoom.
3. **World gen v2.** Elevation/moisture fields, rivers, suitability map + debug overlays;
   min-conflicts becomes the repair stage.
4. **Zones.** Zone grid, selection overlay + info panel, `GenerateZone` → roads + lots +
   building shells appear in world.
5. **Ecology.** Vegetation CA + wildfire + seasons; first "watch the world live" payoff;
   proves fast-forward under load.
6. **Settlements.** Planner + staged construction; towns grow autonomously; event ticker.
7. **Interiors.** Enter building → BSP + room-grammar interior scene; separate panel/camera;
   delta persistence for modifications.
8. **Agents.** Visible villagers with needs/jobs/paths; construction requires labor.
9. **Economy.** Daily production/consumption, caravans between settlements.

Core gameplay can stay undecided through phase ~6 — every phase above serves any likely
direction (god game, colony sim, explorer/RPG), and by then watching the world will tell
you which one it wants to be.

---

## 9. Risks and pitfalls

- **EDT leaks into the core.** The moment `sim/` touches a Swing class, threading bugs
  arrive. Guard the boundary from phase 1.
- **Snapshot cost creep.** If publishing ever deep-copies the whole map, fast-forward
  dies. Keep snapshots = immutable base + deltas + flat agent array, and measure.
- **Non-determinism.** One stray `new Random()` or iteration over a `HashMap` in sim code
  breaks reproducibility silently. Centralize RNG; iterate ordered collections in sim.
- **Interior/world coupling.** Embedding interiors in the world grid (instead of separate
  scenes) forces one resolution on two scales and bloats memory. Keep scenes separate.
- **Simulating detail nobody sees.** The recurring theme of the genre: statistics at
  distance, entities up close — for animals, for population, for off-screen zones. When a
  system gets expensive, the answer is almost always LOD, not a faster loop.
- **Min-conflicts non-convergence.** The 2% random-worse acceptance means the solver may
  churn forever on some seeds; add an iteration cap + "good enough" conflict threshold so
  world gen terminates deterministically.
