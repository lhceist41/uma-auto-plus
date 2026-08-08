// RaceLab v1 - canonical race catalog.
//
// A thin, deterministic facade over the master-data reader. It exposes canonical-key and bare-name
// lookups plus a per-turn index and collision statistics. There is deliberately NO bare-name
// single-result helper (that would silently pick one of the colliding same-name races), and there is no
// raw-`races.json` fallback (the compiled layer is the single authority).

import { loadMasterDataFromDir } from "../masterData/reader.ts"
import type { MasterDataReader } from "../masterData/reader.ts"
import type { CompiledRace, CatalogStats } from "./types.ts"

/** The read-only catalog surface. All returned arrays are frozen (inherited from the reader). */
export interface RaceCatalog {
    /** The canonical race by its composite `(name, turnNumber)` key, or undefined. */
    raceByKey(name: string, turnNumber: number): CompiledRace | undefined
    /** EVERY race sharing a bare name (same-name races recur across turns), sorted by turn; empty if none. */
    racesByName(name: string): readonly CompiledRace[]
    /** All races landing on a given turn, sorted by name; empty if none. */
    racesAtTurn(turnNumber: number): readonly CompiledRace[]
    /** All races, canonical order (turnNumber, then name). */
    allRaces(): readonly CompiledRace[]
    catalogStats(): CatalogStats
    /** The compiled dataset fingerprint the catalog was built from. */
    fingerprint(): string
}

/** Unambiguous composite string for a `(name, turnNumber)` pair (matches the reader's key encoding). */
function compositeKey(name: string, turnNumber: number): string {
    return JSON.stringify([name, turnNumber])
}

/** Builds a catalog from an already-loaded, hash-verified master-data reader. */
export function createRaceCatalog(reader: MasterDataReader): RaceCatalog {
    // Per-turn index, built once. Reader arrays are already frozen; sort defensively into new arrays.
    const byTurn = new Map<number, CompiledRace[]>()
    for (const race of reader.races) {
        const list = byTurn.get(race.turnNumber)
        if (list) list.push(race)
        else byTurn.set(race.turnNumber, [race])
    }
    for (const list of byTurn.values()) {
        list.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
        Object.freeze(list)
    }

    const distinctNames = new Set(reader.races.map((r) => r.name))
    const stats: CatalogStats = {
        raceCount: reader.races.length,
        uniqueKeyCount: new Set(reader.races.map((r) => compositeKey(r.name, r.turnNumber))).size,
        distinctBareNameCount: distinctNames.size,
        bareNameCollisionCount: reader.races.length - distinctNames.size,
    }
    Object.freeze(stats)

    return {
        raceByKey: (name, turnNumber) => reader.raceByKey(name, turnNumber),
        // reader.racesByName already returns all matches; re-expose sorted by turn for determinism.
        racesByName: (name) => {
            const all = reader.racesByName(name)
            return Object.freeze([...all].sort((a, b) => a.turnNumber - b.turnNumber))
        },
        racesAtTurn: (turnNumber) => byTurn.get(turnNumber) ?? Object.freeze([]),
        allRaces: () => reader.races,
        catalogStats: () => stats,
        fingerprint: () => reader.fingerprint,
    }
}

/** Convenience loader: hash-verify the compiled artifacts from a directory and build a catalog. */
export function loadRaceCatalog(compiledDir: string): RaceCatalog {
    return createRaceCatalog(loadMasterDataFromDir(compiledDir))
}
