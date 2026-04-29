import { useEffect, useRef, useState, useMemo, Children, forwardRef } from "react"
import { Animated, ViewStyle, View, LayoutChangeEvent, NativeSyntheticEvent, NativeScrollEvent, PanResponder } from "react-native"
import { FlashList, FlashListProps } from "@shopify/flash-list"
import { getIndicatorPositionStyle } from "./helpers.ts"

type CustomIndicatorProps = {
    /** Animated value controlling indicator's translation. */
    indicatorPosition: Animated.Value
    /** Animated value controlling indicator's scale. */
    indicatorScale: Animated.Value
    /** Whether the scroll direction is horizontal. */
    horizontal: boolean
    /** Length (width/height) of the indicator thumb. */
    indicatorSize: number
    /** Maximum distance indicator can travel within the track. */
    scrollableTrackLength: number
    /** Whether scroll direction is inverted. */
    inverted: boolean
    /** Reference to FlashList for programmatic scrolling. */
    flashListRef: React.RefObject<any>
    /** Total content size of the list. */
    contentSize: number
    /** Viewport size of the list. */
    visibleSize: number
    /** Style positioning the indicator. */
    indicatorPositionStyle: ViewStyle
    /** Style defining the indicator's appearance. */
    indicatorStyle: ViewStyle
}

/**
 * Draggable scrollbar thumb. Dragging it scrolls the content; scrolling the content moves it.
 */
const CustomIndicator = ({
    indicatorPosition,
    indicatorScale,
    horizontal,
    indicatorSize,
    scrollableTrackLength,
    inverted,
    flashListRef,
    contentSize,
    visibleSize,
    indicatorPositionStyle,
    indicatorStyle,
}: CustomIndicatorProps) => {
    // Store offset at drag start to calculate relative motion.
    const dragStartOffset = useRef(0)

    const refs = useRef({
        horizontal,
        inverted,
        scrollableTrackLength,
        contentSize,
        visibleSize,
    }).current

    useEffect(() => {
        refs.horizontal = horizontal
        refs.inverted = inverted
        refs.scrollableTrackLength = scrollableTrackLength
        refs.contentSize = contentSize
        refs.visibleSize = visibleSize
    }, [horizontal, inverted, scrollableTrackLength, contentSize, visibleSize])

    const pan = useRef(new Animated.ValueXY()).current

    // Drive indicator and list scroll from drag gestures.
    pan.addListener(({ x, y }) => {
        const gestureOffset = refs.horizontal ? (refs.inverted ? -x : x) : refs.inverted ? -y : y
        // Add drag-start offset for continuity, clamp to track.
        const newIndicatorOffset = Math.min(Math.max(gestureOffset + dragStartOffset.current, 0), refs.scrollableTrackLength)

        indicatorPosition.setValue(newIndicatorOffset)

        // Proportional content offset from indicator offset.
        const maxContentOffset = refs.contentSize - refs.visibleSize
        const contentOffset = refs.scrollableTrackLength > 0 ? (newIndicatorOffset / refs.scrollableTrackLength) * maxContentOffset : 0

        // No animation here; we animate the indicator ourselves.
        flashListRef.current?.scrollToOffset({
            offset: contentOffset,
            animated: false,
        })
    })

    const panResponder = useRef(
        PanResponder.create({
            onMoveShouldSetPanResponder: () => true,

            onPanResponderGrant: () => {
                // @ts-ignore: reading the private Animated value to capture the current indicator position.
                dragStartOffset.current = indicatorPosition._value || 0
                // Reset offset and value so gesture deltas start from zero at the current position.
                pan.setOffset({ x: 0, y: 0 })
                pan.setValue({ x: 0, y: 0 })
            },

            onPanResponderMove: Animated.event([null, { dx: pan.x, dy: pan.y }], {
                useNativeDriver: false,
            }),

            onPanResponderRelease: () => {
                // Flatten so the next drag starts from the current position (no cumulative offset drift).
                pan.flattenOffset()
            },
        })
    ).current

    // Indicator translation along the track. Reversed when inverted; "extend" lets out-of-range values continue linearly.
    const translateAnim = indicatorPosition.interpolate({
        inputRange: [0, scrollableTrackLength],
        outputRange: inverted ? [scrollableTrackLength, 0] : [0, scrollableTrackLength],
        extrapolate: "extend",
    })

    // Indicator scale for visual feedback while scrolling; "clamp" keeps it within [0, 1].
    const scaleAnim = indicatorScale.interpolate({
        inputRange: [0, 1],
        outputRange: [0, 1],
        extrapolate: "clamp",
    })

    return (
        <Animated.View
            style={{
                ...indicatorStyle,
                ...indicatorPositionStyle,
                position: "absolute",
                height: horizontal ? indicatorStyle.width : indicatorSize,
                width: horizontal ? indicatorSize : indicatorStyle.width,
                transform: horizontal ? [{ translateX: translateAnim }, { scaleX: scaleAnim }] : [{ translateY: translateAnim }, { scaleY: scaleAnim }],
            }}
            {...panResponder.panHandlers}
        />
    )
}

type CustomScrollViewProps<T> = {
    /** Props passed directly to FlashList. */
    targetProps?: Partial<FlashListProps<T>>
    /** Indicator position: left/right/top/bottom or percentage. */
    position?: string | number
    /** Scroll orientation. */
    horizontal?: boolean
    /** Hide scrollbar if true. */
    hideScrollbar?: boolean
    /** Always show scrollbar if true. */
    persistentScrollbar?: boolean
    /** Styling for scrollbar indicator. */
    indicatorStyle?: ViewStyle
    /** Container view styling. */
    containerStyle?: ViewStyle
    /** Child elements (alternative to data prop). */
    children?: React.ReactNode | React.ReactNode[]
    /** Minimum pixel size of indicator. */
    minIndicatorSize?: number
    /** Enable custom indicator (WIP). When false, uses native Android scrollbar. */
    enableCustomIndicator?: boolean
}

/**
 * Wraps a `FlashList` with a custom draggable scrollbar indicator, sized/positioned from content
 * vs viewport size. Two interaction paths: dragging the thumb (pan responder) scrolls the content
 * proportionally, and scrolling the content (`onScroll`) moves the thumb to match. Horizontal and
 * vertical both supported.
 */
export const CustomScrollView = forwardRef<any, CustomScrollViewProps<any>>(
    (
        {
            targetProps,
            position = "right",
            horizontal = false,
            hideScrollbar = false,
            persistentScrollbar = false,
            indicatorStyle = {},
            containerStyle = { flex: 1 },
            children,
            minIndicatorSize,
            enableCustomIndicator = false,
        },
        ref
    ) => {
        // Total size of the content inside the scroll view (width for horizontal, height for vertical).
        const [contentSize, setContentSize] = useState(1)

        // Size of the visible viewport of the scroll view (width for horizontal, height for vertical).
        const [visibleSize, setVisibleSize] = useState(0)

        // Size orthogonal to the scroll direction (height for horizontal, width for vertical).
        const [orthogonalSize, setOrthogonalSize] = useState(0)

        const flashListRef = useRef<any>(null)

        const childArray = useMemo(() => {
            return children ? Children.toArray(children) : []
        }, [children])

        // Indicator size from the content-to-viewport ratio, floored at minIndicatorSize if given.
        const calculatedSize = contentSize > visibleSize ? (visibleSize * visibleSize) / contentSize : visibleSize
        const indicatorSize = minIndicatorSize !== undefined ? Math.max(calculatedSize, minIndicatorSize) : calculatedSize

        // Max travel along the track; keeps the indicator fully inside the viewport.
        const scrollableTrackLength = visibleSize > indicatorSize ? visibleSize - indicatorSize : 1

        const indicatorPosition = useRef(new Animated.Value(0)).current
        const indicatorScale = useRef(new Animated.Value(1)).current

        // Track content size on FlashList content-size change.
        const handleContentSizeChange = (width: number, height: number) => {
            setContentSize(horizontal ? width : height)
        }

        // On layout change, update visible and orthogonal size for scroll calculations.
        const handleLayoutChange = (event: LayoutChangeEvent) => {
            const { width, height } = event.nativeEvent.layout
            setVisibleSize(horizontal ? width : height)
            setOrthogonalSize(horizontal ? height : width)
        }

        // On scroll, update indicator position and scale.
        const handleScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
            const contentOffset = horizontal ? event.nativeEvent.contentOffset.x : event.nativeEvent.contentOffset.y

            const maxContentOffset = contentSize - visibleSize

            // Proportional indicator offset along the track.
            const indicatorOffset = maxContentOffset > 0 ? (contentOffset * scrollableTrackLength) / maxContentOffset : 0

            indicatorPosition.setValue(indicatorOffset)

            indicatorScale.setValue(indicatorOffset >= 0 ? (indicatorSize + 2 * scrollableTrackLength - 2 * indicatorOffset) / indicatorSize : (indicatorSize + 2 * indicatorOffset) / indicatorSize)
        }

        // Forward the ref to the FlashList so parents can control scrolling.
        useEffect(() => {
            if (ref) {
                if (typeof ref === "function") {
                    ref(flashListRef.current)
                } else {
                    ref.current = flashListRef.current
                }
            }
        }, [ref, flashListRef])

        return (
            <View style={containerStyle}>
                <FlashList
                    ref={flashListRef}
                    data={children ? (childArray as any) : (targetProps?.data as any)}
                    renderItem={children ? ({ item }: any) => <View style={{ width: "100%" }}>{item}</View> : (targetProps?.renderItem as any)}
                    horizontal={horizontal}
                    showsVerticalScrollIndicator={!hideScrollbar && !enableCustomIndicator && !horizontal}
                    showsHorizontalScrollIndicator={!hideScrollbar && !enableCustomIndicator && horizontal}
                    onContentSizeChange={enableCustomIndicator ? handleContentSizeChange : targetProps?.onContentSizeChange}
                    onLayout={(e) => {
                        if (enableCustomIndicator) {
                            handleLayoutChange(e)
                        }
                        // Preserve user-provided onLayout if it exists.
                        if (typeof targetProps?.onLayout === "function") {
                            targetProps.onLayout(e)
                        }
                    }}
                    // Update onScroll every 16ms (~60fps) for smooth indicator movement.
                    scrollEventThrottle={enableCustomIndicator ? 16 : targetProps?.scrollEventThrottle}
                    onScroll={(e) => {
                        if (enableCustomIndicator) {
                            handleScroll(e)
                        }
                        // Preserve user-provided onScroll if it exists.
                        if (typeof targetProps?.onScroll === "function") {
                            targetProps.onScroll(e)
                        }
                    }}
                    {...(targetProps as any)}
                />

                {enableCustomIndicator && (persistentScrollbar || indicatorSize < visibleSize) && (
                    <CustomIndicator
                        indicatorPosition={indicatorPosition}
                        indicatorScale={indicatorScale}
                        horizontal={horizontal}
                        indicatorSize={indicatorSize}
                        scrollableTrackLength={scrollableTrackLength}
                        inverted={false}
                        flashListRef={flashListRef}
                        contentSize={contentSize}
                        visibleSize={visibleSize}
                        indicatorPositionStyle={getIndicatorPositionStyle(horizontal, position, orthogonalSize, indicatorStyle.width as number)}
                        indicatorStyle={indicatorStyle}
                    />
                )}
            </View>
        )
    }
)

// Set displayName for React DevTools and to satisfy react/display-name (forwardRef inner fns are
// anonymous by default; without this, DevTools shows "ForwardRef" instead of "CustomScrollView").
CustomScrollView.displayName = "CustomScrollView"

export default CustomScrollView
