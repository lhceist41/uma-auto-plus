// ParentLab PL-3 - public export surface. Pure, offline, read-only Veteran shadow library derived from
// the existing career-outcome corpus. No affinity engine, no lineage capture, no ranking, no persistence:
// PL-3 is only the trustworthy account-owned Veteran data foundation later stages build on.

export * from "./types.ts"
export * from "./lineage.ts"
export { buildVeteranLibrary } from "./buildVeteranLibrary.ts"
export {
    canonicalCareerEvidence,
    contentHash128,
    finalKeptRecord,
    normalizeSparkNameForIdentity,
    veteranIdFor,
} from "./identity.ts"
