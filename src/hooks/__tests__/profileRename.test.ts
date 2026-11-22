import fs from "fs"
import path from "path"
import { parseSync, transformSync, traverse, types as t } from "@babel/core"

const DEFAULT_PROFILE_NAME = "Default Profile"

/** Source of every node in a .tsx file that `pick` selects, keyed by the name it returns. */
function snippets(file: string, pick: (node: t.Node) => string | undefined): Record<string, string> {
    const source = fs.readFileSync(path.join(process.cwd(), file), "utf8")
    const ast = parseSync(source, { configFile: false, babelrc: false, filename: file, presets: ["@babel/preset-typescript"] })!
    const out: Record<string, string> = {}
    traverse(ast, {
        enter(p) {
            const name = pick(p.node)
            if (name && !(name in out)) {
                const target =
                    t.isVariableDeclarator(p.node) && t.isCallExpression(p.node.init)
                        ? p.node.init.arguments[0]
                        : t.isVariableDeclarator(p.node)
                          ? p.node.init
                          : t.isJSXAttribute(p.node)
                            ? (p.node.value as t.JSXExpressionContainer).expression
                            : (p.node as t.CallExpression).arguments[0]
                out[name] = source.slice(target!.start!, target!.end!)
            }
        },
    })
    return out
}

/** Compiles a function expression and closes it over `bindings`, the values its render would have seen. */
function bind(code: string, bindings: Record<string, unknown>) {
    const js = transformSync(`return (${code})`, {
        configFile: false,
        babelrc: false,
        filename: "snippet.ts",
        parserOpts: { allowReturnOutsideFunction: true },
        presets: ["@babel/preset-typescript"],
    })!.code!
    return new Function(...Object.keys(bindings), js)(...Object.values(bindings))
}

const declared = (names: string[]) => (node: t.Node) => (t.isVariableDeclarator(node) && t.isIdentifier(node.id) && names.includes(node.id.name) ? node.id.name : undefined)

const hook = snippets("src/hooks/useProfileManager.tsx", declared(["findProfileById", "hasNameConflict", "updateProfile"]))
const modal = snippets("src/components/ProfileManagerModal/index.tsx", declared(["handleUpdateProfile"]))
const selector = snippets("src/components/ProfileSelector/index.tsx", (node) => {
    if (t.isVariableDeclarator(node) && t.isIdentifier(node.id, { name: "getDefaultSelectedProfile" })) return "getDefaultSelectedProfile"
    if (t.isJSXAttribute(node) && t.isJSXIdentifier(node.name, { name: "onProfileUpdated" })) return "onProfileUpdated"
    if (t.isCallExpression(node) && t.isIdentifier(node.callee, { name: "useEffect" })) {
        const deps = node.arguments[1]
        if (t.isArrayExpression(deps) && deps.elements.map((e) => (e as t.Identifier).name).join() === "profiles,currentProfileName,selectedProfileName") return "syncEffect"
    }
    return undefined
})

type Row = { id: number; name: string; settings: string }

/** The profile hook, Manage Profiles modal and selector callbacks over an in-memory profiles table and context. */
function app(rows: Row[], active: string | null) {
    const db = { rows: rows.map((r) => ({ ...r })), current: active }
    const state = { profiles: [] as { id: number; name: string; settings: object }[], currentProfileName: null as string | null, selected: DEFAULT_PROFILE_NAME }
    const errors: string[] = []
    const databaseManager = {
        saveProfile: async (p: { id: number; name: string; settings: object }) => {
            const row = db.rows.find((r) => r.id === p.id)!
            row.name = p.name
            row.settings = JSON.stringify(p.settings)
            return p.id
        },
        setCurrentProfileName: async (name: string | null) => {
            db.current = name
        },
        getCurrentProfileName: async () => db.current,
    }
    const loadProfiles = async () => {
        state.profiles = db.rows.map((r) => ({ id: r.id, name: r.name, settings: JSON.parse(r.settings) })).sort((a, b) => a.name.localeCompare(b.name))
    }
    const getDefaultSelectedProfile = bind(selector.getDefaultSelectedProfile, {})
    const setSelectedProfileName = (name: string) => {
        state.selected = name
    }

    /** Re-runs the selector's sync effect until the selection stops changing, as React would after each state change. */
    const settle = () => {
        for (let i = 0; i < 10; i++) {
            const before = state.selected
            bind(selector.syncEffect, {
                profiles: state.profiles,
                currentProfileName: state.currentProfileName,
                selectedProfileName: state.selected,
                setSelectedProfileName,
                DEFAULT_PROFILE_NAME,
                getDefaultSelectedProfile,
            })()
            if (state.selected === before) return
        }
        throw new Error("selection never settled")
    }

    /** One render's hook callbacks. */
    const renderCallbacks = () => {
        const common = { profiles: state.profiles, onError: (m: string) => errors.push(m) }
        const findProfileById = bind(hook.findProfileById, common)
        const hasNameConflict = bind(hook.hasNameConflict, common)
        const updateProfile = bind(hook.updateProfile, {
            ...common,
            findProfileById,
            hasNameConflict,
            databaseManager,
            loadProfiles,
            currentProfileName: state.currentProfileName,
            setCurrentProfileName: (name: string | null) => {
                state.currentProfileName = name
            },
            DEFAULT_PROFILE_NAME,
            logWithTimestamp: () => {},
            logErrorWithTimestamp: () => {},
        })
        return { updateProfile }
    }

    /** Types `newName` into the rename field of profile `id` and taps save, as the player does in Manage Profiles. */
    const rename = async (id: number, newName: string) => {
        const { updateProfile } = renderCallbacks()
        const onProfileUpdated = bind(selector.onProfileUpdated, { loadProfiles, selectedProfileName: state.selected, setSelectedProfileName })
        const save = bind(modal.handleUpdateProfile, {
            profileName: newName,
            editingProfileId: id,
            profiles: state.profiles,
            updateProfile,
            setProfileName: () => {},
            setEditingProfileId: () => {},
            onProfileUpdated,
            onError: (m: string) => errors.push(m),
        })
        await save()
        settle()
    }

    const start = async () => {
        await loadProfiles()
        state.currentProfileName = await databaseManager.getCurrentProfileName()
        settle()
    }

    return { db, state, errors, start, rename, renderCallbacks }
}

const ROWS: Row[] = [
    { id: 1, name: "Alpha", settings: "{}" },
    { id: 2, name: "Bravo", settings: "{}" },
]

describe("renaming a profile", () => {
    it("keeps the renamed active profile selected and active", async () => {
        const a = app(ROWS, "Bravo")
        await a.start()
        expect(a.state.selected).toBe("Bravo")

        await a.rename(2, "Charlie")

        expect(a.state.selected).toBe("Charlie")
        expect(a.state.currentProfileName).toBe("Charlie")
        expect(a.db.current).toBe("Charlie")
        expect(a.errors).toEqual([])
    })

    it("leaves the active profile alone when another profile is renamed", async () => {
        const a = app(ROWS, "Bravo")
        await a.start()

        await a.rename(1, "Delta")

        expect(a.state.selected).toBe("Bravo")
        expect(a.state.currentProfileName).toBe("Bravo")
        expect(a.db.current).toBe("Bravo")
    })

    it("keeps the active profile when the new name is refused", async () => {
        const a = app(ROWS, "Bravo")
        await a.start()

        await a.rename(2, "alpha")

        expect(a.errors).toEqual(['Profile with name "alpha" already exists.'])
        expect(a.db.rows.map((r) => r.name)).toEqual(["Alpha", "Bravo"])
        expect(a.state.selected).toBe("Bravo")
        expect(a.state.currentProfileName).toBe("Bravo")
        expect(a.db.current).toBe("Bravo")
    })

    it("follows the shown profile's new name without activating it when no profile is active", async () => {
        const a = app(ROWS, null)
        await a.start()
        expect(a.state.selected).toBe("Alpha")

        await a.rename(1, "Zulu")

        expect(a.state.selected).toBe("Zulu")
        expect(a.state.currentProfileName).toBeNull()
        expect(a.db.current).toBeNull()
    })

    it("keeps showing the same profile when its new name is refused and no profile is active", async () => {
        const a = app(ROWS, null)
        await a.start()

        await a.rename(1, "Bravo")

        expect(a.errors).toEqual(['Profile with name "Bravo" already exists.'])
        expect(a.state.selected).toBe("Alpha")
        expect(a.state.currentProfileName).toBeNull()
    })

    it("keeps the active name when only the settings are overwritten", async () => {
        const a = app(ROWS, "Bravo")
        await a.start()

        await a.renderCallbacks().updateProfile(2, { settings: { training: { maximumFailureChance: 15 } } })

        expect(a.state.currentProfileName).toBe("Bravo")
        expect(a.db.current).toBe("Bravo")
        expect(JSON.parse(a.db.rows[1].settings)).toEqual({ training: { maximumFailureChance: 15 } })
    })
})
