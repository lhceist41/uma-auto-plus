// Local Master-Data Compiler v1 - filesystem convenience loader for Node callers (CLI, tests).
//
// Kept apart from reader.ts so the verification logic stays importable from the app bundle, which has no
// node:fs.

import { readFileSync } from "node:fs"
import { join } from "node:path"
import { createMasterDataReader, MasterDataReaderError } from "./reader.ts"
import type { MasterDataReader } from "./reader.ts"

/**
 * Reads the three artifacts from a compiled directory and builds a verified reader. A missing artifact
 * throws a deterministic {@link MasterDataReaderError}.
 */
export function loadMasterDataFromDir(compiledDir: string): MasterDataReader {
    const read = (name: string): string => {
        try {
            return readFileSync(join(compiledDir, name), "utf8")
        } catch (e) {
            throw new MasterDataReaderError("missingArtifact", `cannot read ${name} from ${compiledDir}: ${e instanceof Error ? e.message : String(e)}`)
        }
    }
    return createMasterDataReader({ manifest: read("manifest.json"), skills: read("skills.json"), races: read("races.json") })
}
