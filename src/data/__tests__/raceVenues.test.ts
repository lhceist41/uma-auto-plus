import racesData from "../races.json"

type Venue = { raceTrack: string; course: string | null; direction: string; distanceMeters: number; distanceType: string; nameFormatted: string }
type Race = { name: string; raceTrack: string; turnNumber: number; nameFormatted: string; venues?: Venue[] }

const races = Object.values(racesData) as Race[]
const withVenues = races.filter((race) => race.venues)

describe("races.json alternate courses", () => {
    it("lists 21 alternate courses on the six JBC rows and the three trainee races", () => {
        expect(withVenues).toHaveLength(9)
        expect(withVenues.reduce((sum, race) => sum + race.venues!.length, 0)).toBe(21)
        expect(withVenues.map((race) => `${race.name}@${race.turnNumber}`).sort()).toEqual(
            [
                ...[45, 69].flatMap((turn) => ["JBC Classic", "JBC Ladies’ Classic", "JBC Sprint"].map((name) => `${name}@${turn}`)),
                "Satsuki Sho@31",
                "Takarazuka Kinen@60",
                "Tenno Sho (Spring)@56",
            ].sort(),
        )
    })

    it("files the three trainee courses under the labels the race list prints", () => {
        const labelOf = (name: string) => withVenues.find((race) => race.name === name)!.venues!.map((venue) => venue.nameFormatted)
        expect(labelOf("Satsuki Sho")).toEqual(["Tokyo Turf 2000m (Med) Left"])
        expect(labelOf("Tenno Sho (Spring)")).toEqual(["Hanshin Turf 3200m (Long) Right / Outer-Inner"])
        expect(labelOf("Takarazuka Kinen")).toEqual(["Kyoto Turf 2200m (Med) Right / Outer"])
    })

    it("keeps no venue label equal to another label on its turn", () => {
        const baseLabels = new Set(races.map((race) => `${race.turnNumber}|${race.nameFormatted}`))
        const venueLabels = withVenues.flatMap((race) => race.venues!.map((venue) => `${race.turnNumber}|${venue.nameFormatted}`))
        expect(venueLabels.filter((label) => baseLabels.has(label))).toEqual([])
        expect(new Set(venueLabels).size).toBe(venueLabels.length)
    })

    it("spells Ooi as Oi in the label and keeps Ooi as the track", () => {
        const ooi = races.filter((race) => race.raceTrack === "Ooi")
        expect(ooi).toHaveLength(16)
        expect(ooi.every((race) => race.nameFormatted.startsWith("Oi "))).toBe(true)
        expect(races.some((race) => race.nameFormatted.startsWith("Ooi "))).toBe(false)
    })
})
