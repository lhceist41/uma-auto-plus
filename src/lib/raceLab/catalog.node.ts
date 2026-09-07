// RaceLab v1 - filesystem convenience loader for Node callers (CLI, tests).

import { loadMasterDataFromDir } from "../masterData/reader.node.ts"
import { createRaceCatalog } from "./catalog.ts"
import type { RaceCatalog } from "./catalog.ts"

/** Hash-verifies the compiled artifacts in a directory and builds a catalog from them. */
export function loadRaceCatalog(compiledDir: string): RaceCatalog {
    return createRaceCatalog(loadMasterDataFromDir(compiledDir))
}
