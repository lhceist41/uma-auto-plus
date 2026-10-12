/**
 * Why the display is not a supported configuration, or null when it is. The 1080x1920 templates are captured
 * at 240 DPI; at 480 DPI the game draws tight button labels such as Legacy Select's Auto-Select smaller and
 * the bot misses them. Taller 1080-wide phones are not checked for density.
 */
export function unsupportedDisplayReason(width: number, height: number, dpi: number): string | null {
    if (width !== 1080 || height < 1920) return "an unsupported screen size"
    if (height === 1920 && dpi !== 240) return "an unsupported display density"
    return null
}
