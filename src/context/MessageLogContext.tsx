import { createContext, useCallback, useEffect, useMemo, useRef, useState } from "react"

/**
 * Represents a single entry in the message log.
 */
export interface MessageLogEntry {
    /** The sequential message ID from the bot service. */
    id: number
    /** The text content of the log message. */
    message: string
}

/**
 * Context value interface for the MessageLog provider.
 * Exposes the message log array and methods to update it.
 */
export interface MessageLogProviderProps {
    /** The array of all message log entries. */
    messageLog: MessageLogEntry[]
    /** Direct setter for the entire message log array. */
    setMessageLog: React.Dispatch<React.SetStateAction<MessageLogEntry[]>>
    /** Appends a new message entry to the log. */
    addMessageToLog: (id: number, message: string) => void
}

export const MessageLogContext = createContext<MessageLogProviderProps>({} as MessageLogProviderProps)

/** Maximum number of log entries kept in memory to prevent unbounded growth. */
const MAX_LOG_ENTRIES = 500

/** Interval in ms to flush batched messages to state. */
const BATCH_FLUSH_INTERVAL = 300

/**
 * Provider component for the MessageLog context.
 * Manages the message log entries and provides methods to add new messages.
 *
 * Messages are batched and flushed at a fixed interval to reduce the number of
 * React re-renders and bridge pressure on the Hermes JS engine. The log is also
 * capped at MAX_LOG_ENTRIES to prevent unbounded memory growth during long runs.
 *
 * @param children The child components to render within the provider.
 * @returns The message log context provider.
 */
export const MessageLogProvider = ({ children }: any): React.ReactElement => {
    const [messageLog, setMessageLog] = useState<MessageLogEntry[]>([])

    // Batch buffer: accumulates messages between flushes without triggering re-renders.
    const batchRef = useRef<MessageLogEntry[]>([])

    // Flush batched messages into state on a fixed interval.
    useEffect(() => {
        const timer = setInterval(() => {
            if (batchRef.current.length > 0) {
                const batch = batchRef.current
                batchRef.current = []
                setMessageLog((prev) => {
                    const merged = prev.concat(batch)
                    // Cap the log to prevent unbounded memory growth.
                    if (merged.length > MAX_LOG_ENTRIES) {
                        return merged.slice(merged.length - MAX_LOG_ENTRIES)
                    }
                    return merged
                })
            }
        }, BATCH_FLUSH_INTERVAL)

        return () => clearInterval(timer)
    }, [])

    /**
     * Queue a message for the next batch flush instead of immediately updating state.
     * This dramatically reduces the number of re-renders during heavy logging.
     * @param id The sequential message ID from the bot service.
     * @param message The text content of the log message.
     */
    const addMessageToLog = useCallback((id: number, message: string) => {
        batchRef.current.push({ id, message })
    }, [])

    // Memoize the provider value to prevent cascading re-renders.
    const providerValues = useMemo<MessageLogProviderProps>(
        () => ({
            messageLog,
            setMessageLog,
            addMessageToLog,
        }),
        [messageLog, addMessageToLog]
    )

    return <MessageLogContext.Provider value={providerValues}>{children}</MessageLogContext.Provider>
}
