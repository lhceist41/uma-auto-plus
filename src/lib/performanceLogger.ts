/**
 * Performance logging for operation timing. Log line format:
 * `[PERF] CATEGORY - operation: durationMs | Details: {json}`
 * CATEGORY is the system area (UI, DATABASE, STATE, SETTINGS); Details is an optional JSON payload.
 *
 * Reading the output: high renderCount for one action means unnecessary re-renders;
 * action:"skip" in search registration means the item was already indexed. duration is the
 * point of it — slow ops trigger console.warn (see logMetric's threshold).
 */

export interface PerformanceMetric {
    /** The name of the operation being timed. */
    operation: string
    /** The duration of the operation in milliseconds. */
    duration: number
    /** The timestamp when the operation started. */
    timestamp: number
    /** Additional details about the operation. */
    details?: Record<string, any>
    /** The category of the operation (e.g., "database", "settings", "state", "ui"). */
    category: "database" | "settings" | "state" | "ui"
}

export class PerformanceLogger {
    public static ENABLED = false
    public static SUPPRESS_LOGGING = true

    private metrics: PerformanceMetric[] = []
    private maxMetricsHistory = 100

    private pendingNavigations: Map<string, number> = new Map()

    /**
     * Start timing an operation. Returns a stop function that records the metric when called.
     * When disabled, the stop function is a no-op returning a zero-duration metric.
     */
    startTiming(operation: string, category: PerformanceMetric["category"] = "settings"): (details?: Record<string, any>) => PerformanceMetric {
        if (!PerformanceLogger.ENABLED) {
            return () => ({
                operation,
                duration: 0,
                timestamp: Date.now(),
                category,
            })
        }

        const startTime = performance.now()
        const timestamp = Date.now()

        return (details?: Record<string, any>) => {
            const endTime = performance.now()
            const duration = endTime - startTime

            const metric: PerformanceMetric = {
                operation,
                duration,
                timestamp,
                details,
                category,
            }

            this.recordMetric(metric)
            return metric
        }
    }

    /** Mark the start of a navigation to `target`; paired with markNavigationEnd. */
    markNavigationStart(target: string) {
        if (!PerformanceLogger.ENABLED) return
        this.pendingNavigations.set(target, performance.now())
    }

    /** Record the duration for `target` against its markNavigationStart; no-ops if none pending. */
    markNavigationEnd(target: string, category: PerformanceMetric["category"] = "ui") {
        if (!PerformanceLogger.ENABLED) return
        const startTime = this.pendingNavigations.get(target)
        if (startTime === undefined) return

        const duration = performance.now() - startTime
        this.pendingNavigations.delete(target)

        const metric: PerformanceMetric = {
            operation: `navigation_to_${target}`,
            duration,
            timestamp: Date.now(),
            category,
        }

        this.recordMetric(metric)
    }

    recordMetric(metric: PerformanceMetric) {
        if (!PerformanceLogger.ENABLED) return

        this.metrics.push(metric)

        // Cap history to the most recent N to bound memory.
        if (this.metrics.length > this.maxMetricsHistory) {
            this.metrics = this.metrics.slice(-this.maxMetricsHistory)
        }

        this.logMetric(metric)
    }

    private logMetric(metric: PerformanceMetric) {
        if (!PerformanceLogger.ENABLED) return
        const logMessage = `[PERF] ${metric.category.toUpperCase()} - ${metric.operation}: ${metric.duration.toFixed(2)}ms${metric.details ? ` | Details: ${JSON.stringify(metric.details)}` : ""}`

        if (metric.duration >= 300) {
            console.warn(logMessage) // warn on slow ops
        } else {
            console.log(logMessage)
        }
    }
}

export const performanceLogger = new PerformanceLogger()

// convenience wrappers around the singleton
export const startTiming = (operation: string, category?: PerformanceMetric["category"]) => performanceLogger.startTiming(operation, category)
export const markNavigationStart = (target: string) => performanceLogger.markNavigationStart(target)
export const markNavigationEnd = (target: string, category?: PerformanceMetric["category"]) => performanceLogger.markNavigationEnd(target, category)
