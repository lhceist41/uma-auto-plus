// ParentLab PL-3 - public export surface. Pure, offline, read-only Veteran shadow library derived from
// the existing career-outcome corpus. No affinity engine, no lineage capture, no ranking, no persistence:
// PL-3 is only the trustworthy account-owned Veteran data foundation later stages build on.

export * from "./types.ts"
export * from "./inspiration.ts"
export * from "./lineage.ts"
export * from "./roster.ts"
export * from "./reconcile.ts"
export * from "./retentionTypes.ts"
export * from "./retentionTargets.ts"
export * from "./retentionEvidence.ts"
export * from "./retentionAdvisor.ts"
export * from "./quarantineTypes.ts"
export * from "./quarantineSnapshot.ts"
export * from "./quarantineLedger.ts"
export * from "./quarantineBatch.ts"
export * from "./approvalManifest.ts"
export * from "./affinityData.ts"
export * from "./affinityEvidence.ts"
export * from "./targetBuild.ts"
export * from "./parentCandidate.ts"
export * from "./parentPairing.ts"
export * from "./affinityAdvisor.ts"
export * from "./capacityTypes.ts"
export * from "./capacityEvidence.ts"
export { buildVeteranLibrary } from "./buildVeteranLibrary.ts"
export {
    canonicalCareerEvidence,
    contentHash128,
    finalKeptRecord,
    normalizeSparkNameForIdentity,
    veteranIdFor,
} from "./identity.ts"
