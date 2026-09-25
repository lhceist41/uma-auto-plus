import { contentHash128 } from "../identity.ts"

export function fixtureRank(rating: number): string | null {
    const intervals: readonly [string, number, number][] = [
        ["E", 1300, 1799], ["E+", 1800, 2299], ["B", 6500, 8199], ["B+", 8200, 9999],
        ["A", 10000, 12099], ["A+", 12100, 14499], ["S", 14500, 15899], ["S+", 15900, 17499],
    ]
    return intervals.find(([, low, high]) => rating >= low && rating <= high)?.[0] ?? null
}

export function fixtureFingerprint(record: Record<string, any>): string {
    return contentHash128(JSON.stringify({
        v: 1, character: record.character, outfit: record.outfit, rank: record.rank, rating: record.rating,
        stats: ["spd", "sta", "pwr", "grt", "wit"].map((key) => record.stats[key]),
        aptitudes: ["turf", "dirt", "sprint", "mile", "medium", "long", "front", "pace", "late", "end"].map((key) => record.aptitudes[key]),
    }))
}
