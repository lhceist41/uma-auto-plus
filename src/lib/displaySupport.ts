/**
 * Why the display is not the recommended configuration, or null when it is. The 1080x1920 templates are captured
 * at 240 DPI; at 480 DPI the game draws some text smaller, such as Legacy Select's Auto-Select. The bot measures
 * that at Start and retries some text at smaller sizes, but most screens are unmeasured at other densities.
 * Taller 1080-wide phones are not checked for density.
 */
export function unsupportedDisplayReason(width: number, height: number, dpi: number): string | null {
    if (width !== 1080 || height < 1920) return "an unsupported screen size"
    if (height === 1920 && dpi !== 240) return "a display density other than 240 DPI"
    return null
}
