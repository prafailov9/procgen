# Optimization Notes

How the expensive parts of the tick were made cheap. Each section states the problem, the idea,
the algorithm step by step, and what it actually cost before and after.

All the concrete numbers come from a measured big-world run: **1920 x 1080 = 2,073,600 tiles**,
**25,085 rabbits**, **665 foxes**, rabbit vision radius **5**, fox vision radius **10**.

There is one idea underneath all of these:

> Do work proportional to what is actually happening, not proportional to what could possibly
> happen.

Almost every slow thing in this project was some version of paying for the whole world, or the
whole capacity, when only a small live part mattered.

---

## 1. The spatial index — "which creature is standing here?"

### The problem

`creatureAt(x, y)` used to walk every living creature and compare coordinates:

```
for each alive id:
    if x[id] == targetX and y[id] == targetY: return id
return -1
```

That is **O(A)** per question, where A = alive creatures. It gets asked a lot:

- every fox checks its 8 neighbour tiles for prey, every tick
- every breeding-ready creature looks for a mate
- newborn placement looks for a free tile

At 5,000 creatures with ~600 foxes that is `600 x 8 x 5000 = 24 million` comparisons per tick.
The sim thread collapsed under it.

### The idea

Creatures are spread over millions of tiles, so almost every tile holds **0 or 1** creature.
Instead of searching, keep a lookup table from tile to creature, rebuilt once per tick.

The subtlety: a tile *can* hold more than one creature, so each entry needs to be a list. But
allocating a list object per tile would be far worse than the problem being solved. The trick is an
**intrusive linked list**: the "next" pointer lives in a flat array indexed by the creature's own
id. Because a creature is in exactly one tile's list, one slot per creature is enough, and there
is zero allocation.

### The data

```
cellHead[tile]  = id of the first creature on that tile,  -1 if empty
nextInCell[id]  = id of the next creature on the same tile, -1 if last
```

### The algorithm, step by step

**Rebuild** (once per tick, after movement has settled positions):

1. Clear the entries touched last tick (see the used-cells trick below).
2. For each alive creature `id`:
   1. `cell = y * width + x`  — flatten 2D coordinates to one array index
   2. `nextInCell[id] = cellHead[cell]`  — the creature points at whoever was there
   3. `cellHead[cell] = id`  — the creature becomes the new head

Steps 2.2 and 2.3 are a **push onto the front of a linked list**. Front insertion is used because
it needs no traversal — it is two array writes, always.

**Query:**

```
creatureAt(x, y)  ->  cellHead[y * width + x]
```

One multiply, one add, one array read.

### Worked example

A 4-wide world. Creature 7 is at (1,0), creature 3 at (2,1), creature 9 also at (1,0).

```
tile index = y * 4 + x
creature 7 -> tile 1        creature 3 -> tile 6        creature 9 -> tile 1

after rebuild (ids inserted in ascending order 3, 7, 9):
  cellHead[1] = 9     nextInCell[9] = 7     nextInCell[7] = -1
  cellHead[6] = 3     nextInCell[3] = -1
  every other cellHead = -1
```

Asking "who is at (1,0)?" returns 9 immediately; following `nextInCell` finds 7 if the caller
wants every creature on that tile.

### Cost

| | before | after |
|---|---|---|
| one query | O(A) | O(1) |
| per tick total | O(queries x A) | O(A) rebuild + O(1) per query |

---

## 2. The used-cells trick (shared by both grids)

Both the spatial index and the danger grid must be wiped at the start of each rebuild. The obvious
way is `Arrays.fill(cellHead, -1)` — but that is **2,073,600 writes per tick** on a big world, or
about 124 million writes per second at 60 ticks/sec, to erase data that only occupied ~25,000
tiles.

So each rebuild records which tiles it touched:

```
when writing to a previously-empty cell:
    usedCells[usedCount++] = cell

at the start of the next rebuild:
    for i in 0..usedCount:  clear usedCells[i]
    usedCount = 0
```

Clearing now costs **O(occupied)** instead of **O(world area)**. On the measured run that is
~25,000 writes instead of ~2,000,000 — an **80x** reduction, and unlike the naive version it does
not get worse when the map gets bigger.

The guard "only record a cell the first time it is written this tick" matters: without it,
overlapping writes would add the same cell many times and the list would grow without bound.

---

## 3. The danger grid — inverting an expensive question

### The problem

Every rabbit asked, every tick: *"is there a fox within my vision radius, and which way?"*

That was a ring search outward from the rabbit. Ring searches are efficient when they **find**
something — they stop at the first hit. But the common case is *no fox nearby*, and a search that
finds nothing must visit **every** tile in the disc before it can say so:

```
(2 x 5 + 1)^2 = 121 tiles per rabbit per tick
25,085 rabbits x 121 = 3.04 million lookups per tick
```

This was the single most expensive thing in the tick.

### The idea

Look at the asymmetry: **25,085 askers, 665 subjects**. The question is being asked by the many
about the few. So turn it around — have the few **write** the answer, and let the many **read** it.

This is called an **influence map** (or potential field): a grid where each tile stores a summary
of what is near it, maintained by the small set of things that create the influence.

### The data

```
direction[tile] = which way the nearest predator is (a Direction ordinal 0..7), or -1 for "safe"
distance[tile]  = how far that predator is (Chebyshev), used to resolve overlaps
```

### The algorithm, step by step

**Rebuild** (once per tick, before Behavior runs):

1. Clear the tiles stamped last tick (used-cells trick).
2. For each alive **predator** at `(px, py)`:
   - For every offset `(dx, dy)` in the square `-r .. +r`:
     1. Skip the centre `(0,0)` — a tile has no direction to itself.
     2. Skip if `(px+dx, py+dy)` is outside the world.
     3. `d = max(|dx|, |dy|)` — the Chebyshev ("chessboard king") distance.
     4. If `d >= distance[cell]`, skip: a closer predator already claimed this tile.
     5. Otherwise write `distance[cell] = d` and
        `direction[cell] = directionFrom(cell, predator)`.

**Query:**

```
dangerDirectionAt(x, y)  ->  direction[y * width + x]
```

One array read. If it is -1, no predator is in range and the rabbit skips fleeing entirely.

### Why Chebyshev distance

Because creatures move in 8 directions, one diagonal step covers one unit in both axes. The set of
tiles reachable in `n` steps is a **square**, not a circle. Chebyshev distance
(`max(|dx|, |dy|)`) is the number of king-moves between two tiles, so it is the metric that
matches how things actually move here — and it is what the old ring search used. Using Euclidean
distance instead would silently change which fox a rabbit reacts to.

### Worked example

Two foxes, stamp radius 2. Fox A at (5,5), fox B at (8,5). Tile (7,5) is 2 away from A and 1 away
from B.

```
A stamps first: distance[(7,5)] = 2, direction = "west"  (toward A)
B stamps next:  1 < 2, so overwrite: distance[(7,5)] = 1, direction = "east" (toward B)
```

A rabbit standing at (7,5) reads "east", and `MovementSystem` inverts it for FLEE — so it runs
**west**, away from the nearer fox. Correct without either the rabbit or the grid ever comparing
the two foxes explicitly.

### Cost

| | before (prey ask) | after (predators write) |
|---|---|---|
| formula | `prey x (2r+1)^2` | `predators x (2r+1)^2 + prey` |
| measured | 25,085 x 121 = **3,035,285** | 665 x 121 + 25,085 = **105,550** |
| ratio | | **~29x less work** |

And the gap *widens* as prey outnumber predators, which is the normal state of a food chain.

### The gotcha

The stamp radius must equal the **prey's** vision radius, because that is the range at which prey
used to detect predators for themselves. It has nothing to do with how far the predator can see.
If a second prey species with different vision is ever added, it needs its own grid (or the grid
needs to store distance and let each species compare against its own radius).

---

## 4. The compact snapshot — stop copying capacity

### The problem

Every published snapshot cloned the creature store's raw arrays. Those arrays are sized to
`CREATURES_MAX_CAPACITY`, not to the population:

```
x, y, energy   float[capacity]  = 4 bytes each
age            short[capacity]  = 2 bytes
species, motive byte[capacity]  = 1 byte each
                                 --------------
                                 ~16 bytes per slot
```

At the old capacity of 1,000,000 that is **~16 MB copied per publish**, ten times a second, on the
sim thread — **~160 MB/s** — whether 1,000 or 100,000 creatures were alive. The cost was tied to a
constant instead of to the simulation.

### The idea

Copy only the living creatures, packed into arrays exactly as long as the population.

### The algorithm

1. `count = alive.cardinality()`
2. Allocate each component array with length `count`
3. Walk the alive bitset in ascending order, writing row `0, 1, 2, ...`
4. Also store `ids[row]` — the entity id that row came from

Row `i` is no longer entity `i`; `ids[i]` records which creature it is.

### The problem this creates, and the fix

The renderer interpolates positions between two snapshots so creatures glide instead of teleporting.
That needs to match *the same creature* across snapshots — but row 3 in one snapshot is a different
animal from row 3 in the next, because births and deaths shift everything.

The obvious fix, an array mapping id to row, would be **capacity-sized** — reintroducing exactly
what was just removed.

Instead: both `ids` arrays are **ascending** (the bitset is walked in order). Two sorted lists can
be matched with a **two-pointer merge**:

1. Walk the current snapshot with index `i`, and keep a cursor `j` into the previous one.
2. Advance `j` while `prevIds[j] < ids[i]`.
3. If `prevIds[j] == ids[i]`, the same creature exists in both — interpolate between them.
4. If not, this creature was born since the last snapshot — draw it where it is.

Neither pointer ever moves backwards, so the whole pass is **O(n + m)** with no extra memory.

```
prevIds:  2   5   9   14        <- j walks forward only
ids:      2   9   11  14        <- i walks forward only
match:    y   y   no  y            (11 is newborn)
```

### Cost

At 22,151 living creatures: **~440 KB per snapshot instead of ~16 MB** — about **36x less**, and
now proportional to population. Measured snapshot time is **1.06 ms**, most of which is now the
biomass layer (2M floats = 8.3 MB), not creatures.

### Related: terrain is shared, not copied

Terrain is written once at generation and never mutated by any system, so the snapshot passes the
same array by reference instead of cloning 2 MB per publish. The renderer's "did terrain change?"
check became a reference comparison (`lastTerrain != next.terrain()`) instead of an
`Arrays.equals` over two million bytes.

**This is only safe while nothing mutates terrain.** If erosion, fire scars, or player
terraforming are ever added, this must go back to `.clone()` or the renderer will see edits
mid-frame.

---

## 5. Smaller ones worth remembering

**The time bucket (accumulator).** Ticks are a fixed size of game time (1/60 s), but wall time
between loop iterations is whatever the OS gives. You cannot run "1.7 ticks", so elapsed real time
is banked, whole ticks are spent out of the bank, and the remainder carries to the next iteration —
so nothing is lost to rounding. A cap on ticks per iteration prevents the "spiral of death", where
a long stall creates a debt so large that repaying it causes a longer stall.

**Growth scaled by area, not absolute.** Biomass growth used a flat 50 tiles/tick for any world
size, so a big world got the same total food spread over 15.7x more land. The fix is a per-tile
rate multiplied by world area, with a fractional accumulator so worlds too small to earn one whole
event per tick keep the remainder instead of truncating it to zero. The lesson generalises: rates
should be **per unit of world**, or the same numbers mean different things at different scales.

**Bake the overlay, don't draw shapes.** Drawing biomass and creatures as individual antialiased
ellipses meant hundreds of thousands of shape calls per frame (430 ms paints). Writing them into
one ARGB image once per snapshot makes painting two image blits, so paint cost stops depending on
how much life the world holds. Paints dropped to single-digit milliseconds.

**Level of detail.** Above a zoom threshold the visible tile count is small, so pretty
antialiased circles are affordable. Below it they are sub-pixel and indistinguishable from flat
rectangles, so the expensive path is simply not taken.

---

## The recurring patterns

1. **Invert the query.** If many things ask about few things, have the few write the answer down.
2. **Pay for what is alive, not for what is possible.** Capacity-sized work is a constant cost
   masquerading as a real one.
3. **Remember what you touched.** Clearing only the used entries turns O(world) into O(occupied).
4. **Flat arrays instead of objects.** Intrusive linked lists and struct-of-arrays give the same
   structure with zero allocation and better cache behaviour.
5. **Sorted data is a free index.** Two ascending id lists merge in linear time; no map needed.
6. **Failed searches are the expensive case.** A search that finds something early exits early.
   Optimise for the miss, because the miss is usually the common case.
