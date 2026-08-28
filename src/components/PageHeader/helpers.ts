// Stack.Screen names nested inside the "Settings" navigator (see `SettingsStack` in App.tsx).
// A page reachable only through `navigation.navigate("Settings", { screen, params })` from
// outside that stack (e.g. a search result selected from Home) must be listed here, or the
// nested navigate silently no-ops instead of opening the target settings page.
export const NESTED_SETTINGS_PAGES = [
    "SettingsMain",
    "TrainingSettings",
    "TrainingEventSettings",
    "RacingSettings",
    "RacingPlanSettings",
    "SkillSettings",
    "EventLogVisualizer",
    "ImportSettingsPreview",
    "ScenarioOverridesSettings",
    "RunQueueSettings",
    "DebugSettings",
    "DiscordSettings",
]

/**
 * Whether a search result's target page lives inside the nested Settings stack (as opposed to a
 * top-level Drawer screen), and therefore needs the nested `navigation.navigate("Settings", { screen })`
 * form rather than a direct `navigation.navigate(page)`.
 */
export const isNestedSettingsPage = (page: string): boolean => NESTED_SETTINGS_PAGES.includes(page) || page.startsWith("SkillPlanSettings")
