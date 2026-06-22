package com.steve1316.uma_android_automation.utils

import android.graphics.Bitmap
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.IconScrollListBottomRight
import com.steve1316.uma_android_automation.components.IconScrollListTopLeft
import com.steve1316.uma_android_automation.types.BoundingBox
import org.opencv.core.Point
import kotlin.math.abs

/** Default maximum processing time in milliseconds. */
const val MAX_PROCESS_TIME_DEFAULT_MS = 60000

/** Functional interface for a callback that is called whenever an entry is detected while processing the list. */
fun interface OnEntryDetectedCallback {
    /**
     * Called whenever an entry is detected while processing the list.
     *
     * @param scrollList A reference to this class instance.
     * @param entry The [ScrollListEntry] instance that we detected.
     * @return True to stop the [ScrollList.process] loop early (e.g., after finding a specific entry).
     */
    fun onEntryDetected(scrollList: ScrollList, entry: ScrollListEntry): Boolean
}

/**
 * Stores a single entry's information in the scroll list.
 *
 * @param index The index of this entry in the list.
 * @param bitmap A single entry's bitmap, extracted from the screen.
 * @param bbox The bounding box for the [bitmap], in screen coordinates.
 * @property refX The reference X-coordinate for the entry.
 * @property refY The reference Y-coordinate for the entry.
 */
data class ScrollListEntry(val index: Int, val bitmap: Bitmap, val bbox: BoundingBox, val refX: Int? = null, val refY: Int? = null)

/**
 * Stores configuration for entry image detection.
 *
 * See [CustomImageUtils.detectRoundedRectangles] or [CustomImageUtils.detectRectanglesGeneric] for more information.
 *
 * @property bUseGeneric Whether to use generic rectangle detection.
 * @property minArea The minimum area for a detected rectangle.
 * @property maxArea The maximum area for a detected rectangle.
 * @property blurSize The size of the blur kernel.
 * @property epsilonScalar The epsilon scalar for contour approximation.
 * @property cannyLowerThreshold The lower threshold for Canny edge detection.
 * @property cannyUpperThreshold The upper threshold for Canny edge detection.
 * @property bUseAdaptiveThreshold Whether to use adaptive thresholding.
 * @property adaptiveThresholdBlockSize The block size for adaptive thresholding.
 * @property adaptiveThresholdConstant The constant for adaptive thresholding.
 * @property fillSeedPoint The seed point for flood fill.
 * @property fillLoDiffValue The low difference value for flood fill.
 * @property fillUpDiffValue The high difference value for flood fill.
 * @property morphKernelSize The kernel size for morphological operations.
 * @property bIgnoreOverflowYAxis Whether to ignore overflow on the Y-axis.
 * @property bIgnoreOverflowXAxis Whether to ignore overflow on the X-axis.
 */
data class ScrollListEntryDetectionConfig(
    val bUseGeneric: Boolean = true,
    // The area parameters can be updated later to fit the scroll list's dims.
    var minArea: Int? = null,
    var maxArea: Int? = null,
    val blurSize: Int = if (bUseGeneric) 7 else 5,
    val epsilonScalar: Double = 0.02,
    // CustomImageUtils.detectRoundedRectangles params.
    val cannyLowerThreshold: Int = 30,
    val cannyUpperThreshold: Int = 50,
    val bUseAdaptiveThreshold: Boolean = true,
    val adaptiveThresholdBlockSize: Int = 11,
    val adaptiveThresholdConstant: Double = 2.0,
    // CustomImageUtils.detectRectanglesGeneric params.
    val fillSeedPoint: Point = Point(10.0, 10.0),
    val fillLoDiffValue: Int = 1,
    val fillUpDiffValue: Int = 1,
    val morphKernelSize: Int = 100,
    val bIgnoreOverflowYAxis: Boolean = true,
    // Ignoring X overflow helps for things like the race list, where the selected
    // race has angle brackets around it that overflow with the scroll bar.
    val bIgnoreOverflowXAxis: Boolean = false,
)

/**
 * Handles parsing entries in a scrollable list.
 *
 * Example:
 * ```
 * val list: ScrollList? = ScrollList.create(game)
 * if (list == null) throw InvalidStateException()
 * scrollList.process() { scrollList: ScrollList, entry: ScrollListEntry ->
 *      imageUtils.saveBitmap(entry.bitmap, "entry_${entry.index}")
 *      // Return true to stop the scrollList loop if we've read 5 entries.
 *      entry.index > 5
 * }
 * ```
 *
 * @param game Reference to the bot's [Game] instance.
 * @param bboxList The bounding region of the full list.
 * @param bboxEntries The refined [bboxList] with a buffer on the top and bottom to prevent partial entries.
 * @param entryDetectionConfig The configuration for image detection.
 */
class ScrollList private constructor(private val game: Game, private val bboxList: BoundingBox, entryDetectionConfig: ScrollListEntryDetectionConfig) {
    /** The minimum height for a single entry. */
    private val defaultMinEntryHeight: Int = game.imageUtils.relHeight((SharedData.displayHeight * 0.0781).toInt()) // 150px on 1920h

    /** The maximum height for a single entry. */
    private val defaultMaxEntryHeight: Int = game.imageUtils.relHeight((SharedData.displayHeight * 0.1302).toInt()) // 250px on 1920h

    /** The configuration used for image detection. */
    private val entryDetectionConfig =
        ScrollListEntryDetectionConfig(
            bUseGeneric = entryDetectionConfig.bUseGeneric,
            minArea = entryDetectionConfig.minArea ?: (defaultMinEntryHeight * (bboxList.w.toDouble() * 0.7).toInt()),
            maxArea = entryDetectionConfig.maxArea ?: (defaultMaxEntryHeight * bboxList.w),
            blurSize = entryDetectionConfig.blurSize,
            epsilonScalar = entryDetectionConfig.epsilonScalar,
            // detectRoundedRectangles params
            cannyLowerThreshold = entryDetectionConfig.cannyLowerThreshold,
            cannyUpperThreshold = entryDetectionConfig.cannyUpperThreshold,
            bUseAdaptiveThreshold = entryDetectionConfig.bUseAdaptiveThreshold,
            adaptiveThresholdBlockSize = entryDetectionConfig.adaptiveThresholdBlockSize,
            adaptiveThresholdConstant = entryDetectionConfig.adaptiveThresholdConstant,
            // detectRectanglesGeneric params
            fillSeedPoint = entryDetectionConfig.fillSeedPoint,
            fillLoDiffValue = entryDetectionConfig.fillLoDiffValue,
            fillUpDiffValue = entryDetectionConfig.fillUpDiffValue,
            morphKernelSize = entryDetectionConfig.morphKernelSize,
            bIgnoreOverflowYAxis = entryDetectionConfig.bIgnoreOverflowYAxis,
            bIgnoreOverflowXAxis = entryDetectionConfig.bIgnoreOverflowXAxis,
        )

    /**
     * The padding around the edge of the list.
     *
     * Create a small padding within the bboxList. This is where the list entries reside. This prevents us from accidentally clicking outside the list.
     */
    private val listPadding: Int = 5

    /** The bounding box for the entries in the list. */
    private val bboxEntries: BoundingBox =
        BoundingBox(
            x = bboxList.x + listPadding,
            y = bboxList.y + listPadding,
            w = bboxList.w - (listPadding * 2),
            h = bboxList.h - (listPadding * 2),
        )

    /**
     * The default width of the scroll bar.
     *
     * An estimate of the scrollbar's location within the list. Roughly 35px wide on a 1080 screen, scaled to screen width.
     */
    private val defaultScrollBarWidth: Int = (0.0325 * SharedData.displayWidth).toInt()

    /** The default region of the scroll bar. */
    val bboxScrollBarRegionDefault =
        BoundingBox(
            x = bboxList.x + (bboxList.w - defaultScrollBarWidth),
            y = bboxList.y + 10,
            w = defaultScrollBarWidth,
            h = bboxList.h - 20,
        )

    /**
     * The minimum area of the scroll bar.
     *
     * No known scrollbars that are anywhere near this small.
     */
    private val bboxScrollBarMinArea: Int = 100

    /** The maximum area of the scroll bar. */
    private val bboxScrollBarMaxArea: Int = bboxScrollBarRegionDefault.w * bboxScrollBarRegionDefault.h

    /** Whether the list is scrollable. */
    var bIsScrollable: Boolean = false
        private set

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]ScrollList"

        /**
         * Creates a new [ScrollList] instance.
         *
         * @param game Reference to the bot's [Game] instance.
         * @param bitmap Optional bitmap used for detecting list bounding region.
         * @param listTopLeftComponent An image component used to detect the top left corner of the list.
         * @param listBottomRightComponent An image component used to detect the bottom right corner of the list.
         * @param entryDetectionConfig Optional image detection configuration.
         * @return On success, the [ScrollList] instance. Otherwise, null.
         */
        fun create(
            game: Game,
            bitmap: Bitmap? = null,
            listTopLeftComponent: ComponentInterface? = null,
            listBottomRightComponent: ComponentInterface? = null,
            entryDetectionConfig: ScrollListEntryDetectionConfig? = null,
        ): ScrollList? {
            val bboxList: BoundingBox = getListBoundingRegion(game, bitmap, listTopLeftComponent, listBottomRightComponent) ?: return null
            return ScrollList(game, bboxList, entryDetectionConfig ?: ScrollListEntryDetectionConfig())
        }

        /**
         * Gets the bounding region for the list on the screen.
         *
         * @param game Reference to the bot's [Game] instance.
         * @param bitmap Optional bitmap used for detecting list bounding region. If not specified, a screenshot will be taken and used instead. NOTE: This parameter must be specified in thread-safe
         *    contexts.
         * @param listTopLeftComponent The Component used to detect the top left corner of the list. Defaults to [IconScrollListTopLeft].
         * @param listBottomRightComponent The Component used to detect the bottom right corner of the list. Defaults to [IconScrollListBottomRight].
         * @param debugString Optional string used for naming debug screenshots.
         * @return On success, the bounding region. On failure, null.
         */
        private fun getListBoundingRegion(
            game: Game,
            bitmap: Bitmap? = null,
            listTopLeftComponent: ComponentInterface? = null,
            listBottomRightComponent: ComponentInterface? = null,
            debugString: String = "",
        ): BoundingBox? {
            val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

            val listTopLeftComponent: ComponentInterface = listTopLeftComponent ?: IconScrollListTopLeft
            val listBottomRightComponent: ComponentInterface = listBottomRightComponent ?: IconScrollListBottomRight

            val listTopLeftBitmap: Bitmap? = listTopLeftComponent.template.getBitmap(game.imageUtils)
            if (listTopLeftBitmap == null) {
                MessageLog.e(TAG, "[ERROR] getListBoundingRegion:: Failed to load bitmap: ${listTopLeftComponent.template.path} ")
                return null
            }

            val listBottomRightBitmap: Bitmap? = listBottomRightComponent.template.getBitmap(game.imageUtils)
            if (listBottomRightBitmap == null) {
                MessageLog.e(TAG, "[ERROR] getListBoundingRegion:: Failed to load bitmap: ${listBottomRightComponent.template.path}")
                return null
            }

            val listTopLeft: Point? = listTopLeftComponent.findImageWithBitmap(game.imageUtils, bitmap)
            if (listTopLeft == null) {
                MessageLog.e(TAG, "[ERROR] getListBoundingRegion:: Failed to find top left corner of race list.")
                return null
            }
            val listBottomRight: Point? = listBottomRightComponent.findImageWithBitmap(game.imageUtils, bitmap)
            if (listBottomRight == null) {
                MessageLog.e(TAG, "[ERROR] getListBoundingRegion:: Failed to find bottom right corner of race list.")
                return null
            }
            val x0 = (listTopLeft.x - (listTopLeftBitmap.width / 2)).toInt()
            val y0 = (listTopLeft.y - (listTopLeftBitmap.height / 2)).toInt()
            val x1 = (listBottomRight.x + (listBottomRightBitmap.width / 2)).toInt()
            val y1 = (listBottomRight.y + (listBottomRightBitmap.height / 2)).toInt()

            val bbox =
                BoundingBox(
                    x = x0,
                    y = y0,
                    w = abs(x1 - x0),
                    h = abs(y1 - y0),
                )

            if (bbox.w <= 0 || bbox.h <= 0) {
                MessageLog.e(TAG, "[ERROR] getListBoundingRegion:: Invalid bounding box (zero width or height): $bbox")
                return null
            }

            if (y1 < y0 || x1 < x0) {
                // Out-of-order anchors (bottom-right above/left of top-left) can't describe a real
                // list - the abs()-normalized bbox is garbage (e.g. the post-purchase "Exchange
                // Complete" item dialog yields a bbox running off-screen, then aborts on empty
                // frames). Fail region detection so callers fall back to component-based reads
                // (processWithFallback finds the row + buttons directly). Plain create() callers
                // (skill/race list) already handle a null here.
                MessageLog.w(TAG, "[WARN] getListBoundingRegion:: Scroll list anchors detected out of order (TL=($x0,$y0), BR=($x1,$y1)). Treating as a failed region detection so callers use the component fallback.")
                return null
            }

            if (game.debugMode) {
                game.imageUtils.saveBitmapWithBbox(bitmap, "getListBoundingRegion_$debugString", bbox)
            }

            return bbox
        }

        /**
         * Processes a list, automatically falling back to non-scrollable detection if the standard scroll region isn't found.
         *
         * @param game Reference to the bot's [Game] instance.
         * @param maxTimeMs Maximum processing time before timeout.
         * @param bScrollBottomToTop If true, process from bottom to top.
         * @param keyExtractor Optional callback to generate unique keys and skip duplicates.
         * @param fallbackComponent The component to use for identifying rows if the scroll list region isn't found.
         * @param bForceComponentDetection If true, force component-based detection even if the scroll list region is found.
         * @param listTopLeftComponent Optional top-left corner component for the scroll list.
         * @param listBottomRightComponent Optional bottom-right corner component for the scroll list.
         * @param entryDetectionConfig Optional config for entry detection.
         * @param onEntry Callback executed for each entry. Return true to exit early.
         * @return True if processing completed or exited early via callback.
         */
        fun processWithFallback(
            game: Game,
            maxTimeMs: Int = MAX_PROCESS_TIME_DEFAULT_MS,
            bScrollBottomToTop: Boolean = false,
            keyExtractor: ((ScrollListEntry) -> String?)? = null,
            fallbackComponent: ComponentInterface,
            bForceComponentDetection: Boolean = false,
            listTopLeftComponent: ComponentInterface? = null,
            listBottomRightComponent: ComponentInterface? = null,
            entryDetectionConfig: ScrollListEntryDetectionConfig? = null,
            onEntry: OnEntryDetectedCallback,
        ): Boolean {
            val sourceBitmap = game.imageUtils.getSourceBitmap()

            // Step 1: Attempt to create a standard ScrollList.
            val list = create(game, sourceBitmap, listTopLeftComponent, listBottomRightComponent, entryDetectionConfig)
            if (list != null) {
                MessageLog.d(TAG, "[DEBUG] processWithFallback:: Standard ScrollList detected. Processing...")
                return list.process(maxTimeMs, bScrollBottomToTop, keyExtractor, fallbackComponent, bForceComponentDetection, onEntry)
            }

            // Step 2: Fallback to component-based detection.
            MessageLog.d(TAG, "[DEBUG] processWithFallback:: ScrollList region not found. Falling back to ${fallbackComponent.template.basename} detection.")
            val entries = detectEntriesByComponent(game, sourceBitmap, fallbackComponent)
            if (entries.isEmpty()) {
                MessageLog.w(TAG, "[WARN] processWithFallback:: Failed to detect any entries using fallback component.")
                return false
            }

            // Static "list" for fallback mode (not scrollable).
            val processedKeys = mutableSetOf<String>()
            for (entry in entries) {
                if (keyExtractor != null) {
                    val key = keyExtractor(entry)
                    if (key != null) {
                        if (processedKeys.contains(key)) continue
                        processedKeys.add(key)
                    }
                }

                if (onEntry.onEntryDetected(ScrollList(game, BoundingBox(0, 0, sourceBitmap.width, sourceBitmap.height), ScrollListEntryDetectionConfig()), entry)) {
                    return true
                }
            }

            return true
        }

        /**
         * Detects entries by finding all instances of a specific component on the screen.
         *
         * @param game Reference to the bot's [Game] instance.
         * @param sourceBitmap The bitmap to scan.
         * @param component The component whose locations identify the rows.
         * @return A list of pseudo-ScrollListEntry objects.
         */
        private fun detectEntriesByComponent(game: Game, sourceBitmap: Bitmap, component: ComponentInterface): List<ScrollListEntry> {
            val points = component.findAll(game.imageUtils, sourceBitmap = sourceBitmap)
            MessageLog.d(TAG, "[DEBUG] detectEntriesByComponent:: Found ${points.size} instances of ${component.template.basename}.")

            // Sort points by Y-coordinate.
            val sortedPoints = points.sortedBy { it.y }

            return sortedPoints.mapIndexed { index, point ->
                // Estimate entry height based on screen size (roughly 220px on 1920h).
                val entryHeight = game.imageUtils.relHeight(220)
                val entryY = (point.y - (entryHeight / 2)).toInt().coerceIn(0, sourceBitmap.height - entryHeight)
                val entryBBox = BoundingBox(x = 0, y = entryY, w = sourceBitmap.width, h = entryHeight)
                val entryBitmap = game.imageUtils.createSafeBitmap(sourceBitmap, entryBBox, "PseudoEntry_$index")

                ScrollListEntry(
                    index = index,
                    bitmap = entryBitmap ?: sourceBitmap,
                    bbox = entryBBox,
                    refX = point.x.toInt(),
                    refY = (point.y - entryY).toInt(),
                )
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Detects locations of each entry in the visible portion of the list.
     *
     * @param bitmap An optional bitmap to use when detecting entries. If not specified, a screenshot will be taken.
     * @param entryComponent Optional component to use for identifying rows.
     * @param bForceComponentDetection If true, force component-based detection even if the scroll list region is found.
     * @return A list of [ScrollListEntry] objects for each entry that we detected.
     */
    private fun detectEntries(bitmap: Bitmap? = null, entryComponent: ComponentInterface? = null, bForceComponentDetection: Boolean = false): List<ScrollListEntry> {
        val sourceBitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        val bboxBar: BoundingBox? = getListScrollBarBoundingRegion(sourceBitmap).first

        // We want to cut the scroll bar region out of the search region. This way, the scroll bar doesn't cause entries to merge together.
        // This is really only important for lists where entries can have overlay icons (such as selection brackets) around them that can overlap with the scrollbar.
        val bboxNoScrollbar =
            BoundingBox(
                x = bboxList.x,
                y = bboxList.y,
                w = if (bboxBar == null) bboxList.w else bboxBar.x - bboxList.x,
                h = bboxList.h,
            )

        // If a component is provided and we are forcing component detection, use it to identify entries.
        if (entryComponent != null && bForceComponentDetection) {
            // Find all instances of the landmark component in the current frame.
            val points = entryComponent.findAll(game.imageUtils, sourceBitmap = sourceBitmap)
            val filteredPoints =
                points.filter { point ->
                    // Check if the landmark is within the horizontal bounds of the list.
                    val xInRange = point.x >= bboxNoScrollbar.x && point.x <= bboxNoScrollbar.x + bboxNoScrollbar.w
                    // Use a 150px vertical padding buffer to identify landmarks that may be partially outside the nominal scroll area.
                    val yInRange = point.y >= (bboxNoScrollbar.y - 150) && point.y <= (bboxNoScrollbar.y + bboxNoScrollbar.h + 150)

                    if (!xInRange || !yInRange) {
                        MessageLog.d(
                            TAG,
                            "[DEBUG] detectEntries:: Point at (${point.x.toInt()}, ${point.y.toInt()}) filtered out. List BBox: $bboxNoScrollbar. xInRange=$xInRange, yInRange=$yInRange (Padding: 150px)",
                        )
                    }

                    xInRange && yInRange
                }
            val sortedPoints = filteredPoints.sortedBy { it.y }

            return sortedPoints.mapIndexed { index, point ->
                // Estimate entry height based on screen size (roughly 220px on 1920h).
                val entryHeight = game.imageUtils.relHeight(220)
                // Relax clamping to the entire screen height to correctly position entries that are outside the nominal scroll area (e.g. at the top).
                val entryY = (point.y - (entryHeight / 2)).toInt().coerceIn(0, sourceBitmap.height - entryHeight)
                val entryBBox = BoundingBox(x = bboxNoScrollbar.x, y = entryY, w = bboxNoScrollbar.w, h = entryHeight)
                val entryBitmap = game.imageUtils.createSafeBitmap(sourceBitmap, entryBBox, "Entry_$index")

                ScrollListEntry(
                    index = -1, // To be filled by process().
                    bitmap = entryBitmap ?: sourceBitmap,
                    bbox = entryBBox,
                    // Preserve the landmark's coordinates relative to the entry bitmap for downstream components.
                    refX = point.x.toInt(),
                    refY = (point.y - entryY).toInt(),
                )
            }
        }

        // Extract a list of bounding boxes for each entry in the list.
        val rects: List<BoundingBox> =
            if (entryDetectionConfig.bUseGeneric) {
                game.imageUtils.detectRectanglesGeneric(
                    bitmap = sourceBitmap,
                    region = bboxNoScrollbar,
                    minArea = entryDetectionConfig.minArea,
                    maxArea = entryDetectionConfig.maxArea,
                    blurSize = entryDetectionConfig.blurSize,
                    epsilonScalar = entryDetectionConfig.epsilonScalar,
                    fillSeedPoint = entryDetectionConfig.fillSeedPoint,
                    fillLoDiffValue = entryDetectionConfig.fillLoDiffValue,
                    fillUpDiffValue = entryDetectionConfig.fillUpDiffValue,
                    morphKernelSize = entryDetectionConfig.morphKernelSize,
                    bIgnoreOverflowYAxis = entryDetectionConfig.bIgnoreOverflowYAxis,
                    bIgnoreOverflowXAxis = entryDetectionConfig.bIgnoreOverflowXAxis,
                )
            } else {
                game.imageUtils.detectRoundedRectangles(
                    bitmap = sourceBitmap,
                    region = bboxNoScrollbar,
                    minArea = entryDetectionConfig.minArea,
                    maxArea = entryDetectionConfig.maxArea,
                    blurSize = entryDetectionConfig.blurSize,
                    epsilonScalar = entryDetectionConfig.epsilonScalar,
                    cannyLowerThreshold = entryDetectionConfig.cannyLowerThreshold,
                    cannyUpperThreshold = entryDetectionConfig.cannyUpperThreshold,
                    bUseAdaptiveThreshold = entryDetectionConfig.bUseAdaptiveThreshold,
                    adaptiveThresholdBlockSize = entryDetectionConfig.adaptiveThresholdBlockSize,
                    adaptiveThresholdConstant = entryDetectionConfig.adaptiveThresholdConstant,
                )
            }

        // Adjust BoundingBox coordinates to be screen-relative and create ScrollListEntry objects.
        val result: List<ScrollListEntry> =
            rects.mapIndexed { index, it ->
                val entryBBox =
                    BoundingBox(
                        x = it.x + bboxList.x,
                        y = it.y + bboxList.y,
                        w = it.w,
                        h = it.h,
                    )
                val entryBitmap = game.imageUtils.createSafeBitmap(sourceBitmap, entryBBox, "Entry_$index")

                ScrollListEntry(
                    index = -1, // To be filled by process()
                    bitmap = entryBitmap ?: sourceBitmap,
                    bbox = entryBBox,
                )
            }

        // Sort by screen position top to bottom.
        return result.sortedBy { it.bbox.y }
    }

    /**
     * Gets the bounding region of the scroll bar on screen.
     *
     * @param bitmap Optional non-cropped bitmap to detect the scroll bar. Providing this avoids a new screenshot.
     * @return A pair containing the scrollbar BoundingBox and its thumb component.
     */
    fun getListScrollBarBoundingRegion(bitmap: Bitmap? = null): Pair<BoundingBox?, BoundingBox?> {
        val result: Pair<BoundingBox?, BoundingBox?> =
            game.imageUtils.detectScrollBar(
                bitmap = bitmap,
                region = bboxScrollBarRegionDefault,
                minArea = bboxScrollBarMinArea,
                maxArea = bboxScrollBarMaxArea,
                morphCloseKernelSize = 10,
            )

        val tmpBar: BoundingBox? = result.first
        val tmpThumb: BoundingBox? = result.second

        val bboxScrollBar: BoundingBox? =
            if (tmpBar == null) {
                null
            } else {
                // Add original region offsets to results.
                BoundingBox(
                    x = bboxScrollBarRegionDefault.x + tmpBar.x,
                    y = bboxScrollBarRegionDefault.y + tmpBar.y,
                    w = tmpBar.w,
                    h = tmpBar.h,
                )
            }

        val bboxThumb: BoundingBox? =
            if (tmpThumb == null) {
                null
            } else {
                BoundingBox(
                    x = bboxScrollBarRegionDefault.x + tmpThumb.x,
                    y = bboxScrollBarRegionDefault.y + tmpThumb.y,
                    w = tmpThumb.w,
                    h = tmpThumb.h,
                )
            }

        // Set a flag so we know if this list is scrollable.
        // We only ever set this to true. If we detect a scrollbar once, then the list will always be scrollable even if we fail to detect a scrollbar later.
        if (bboxScrollBar != null) {
            bIsScrollable = true
        }

        return Pair(bboxScrollBar, bboxThumb)
    }

    /**
     * Stops list inertia by clicking a safe location.
     *
     * Prevents list movement after swiping to ensure stable OCR results.
     *
     * @param bboxSafeZone Optional region for safe clicks.
     */
    private fun stopScrolling(bboxSafeZone: BoundingBox? = null) {
        val bboxSafeZone: BoundingBox =
            bboxSafeZone ?: BoundingBox(
                x = bboxEntries.x,
                y = bboxEntries.y,
                w = 1,
                h = bboxEntries.h,
            )
        // Define tap region.
        val x0: Int = game.imageUtils.relX(bboxSafeZone.x.toDouble(), 0)
        val x1: Int = game.imageUtils.relX(bboxSafeZone.x.toDouble(), bboxSafeZone.w)
        val y0: Int = game.imageUtils.relY(bboxSafeZone.y.toDouble(), 0)
        val y1: Int = game.imageUtils.relY(bboxSafeZone.y.toDouble(), bboxSafeZone.h)

        // Select random tap point. Order the bounds with minOf/maxOf first: a short list (e.g. the
        // post-purchase "choose how many to use" screen) can invert the computed range by a few px,
        // and IntRange.random() throws NoSuchElementException on an empty (start > end) range.
        val x: Double = (minOf(x0, x1)..maxOf(x0, x1)).random().toDouble()
        val y: Double = (minOf(y0, y1)..maxOf(y0, y1)).random().toDouble()

        // Execute tap.
        game.tap(x, y, taps = 1, ignoreWaiting = true)
        // Wait for list stabilization and animation to clear.
        game.wait(0.2, skipWaitingForLoading = true)
    }

    /**
     * Scrolls to the top of the list.
     *
     * @param bitmap Optional source bitmap to use when detecting scrollbar.
     */
    private fun scrollToTop(bitmap: Bitmap? = null, force: Boolean = false) {
        val bboxThumb: BoundingBox? = getListScrollBarBoundingRegion().second
        if (!bIsScrollable && !force) {
            MessageLog.d(TAG, "[DEBUG] scrollToTop:: List is not scrollable.")
            return
        }

        if (bboxThumb == null) {
            MessageLog.d(TAG, "[DEBUG] scrollToTop:: No scrollbar thumb detected. Falling back to lazy scrolling.")
            game.gestureUtils.swipe(
                (bboxList.x + (bboxList.w / 2)).toFloat(),
                (bboxList.y + (bboxList.h / 2)).toFloat(),
                (bboxList.x + (bboxList.w / 2)).toFloat(),
                // High value here ensures we go all the way to top of list.
                // We can't use this method in [scrollToBottom] since negative Y values aren't allowed by gestureUtils.
                (bboxList.y + (bboxList.h * 1000)).toFloat(),
            )
            stopScrolling()
        } else {
            game.gestureUtils.swipe(
                (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
                (bboxThumb.y + (bboxThumb.h.toFloat() / 2.0)).toFloat(),
                (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
                bboxList.y.toFloat(),
                duration = 1500L,
            )
        }

        // Small delay for list to stabilize.
        game.wait(1.0, skipWaitingForLoading = true)
    }

    /**
     * Scrolls to the bottom of the list.
     *
     * @param bitmap Optional source bitmap to use when detecting scrollbar.
     */
    internal fun scrollToBottom(bitmap: Bitmap? = null) {
        val bboxThumb: BoundingBox? = getListScrollBarBoundingRegion().second
        if (!bIsScrollable) {
            MessageLog.d(TAG, "[DEBUG] scrollToBottom:: List is not scrollable.")
            return
        }

        if (bboxThumb == null) {
            MessageLog.d(TAG, "[DEBUG] scrollToBottom:: No scrollbar thumb detected. Falling back to lazy scrolling.")
            for (i in 0 until 20) {
                scrollDown(durationMs = 250L)
            }
            stopScrolling()
        } else {
            game.gestureUtils.swipe(
                (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
                (bboxThumb.y + (bboxThumb.h.toFloat() / 2.0)).toFloat(),
                (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
                (bboxList.y + bboxList.h).toFloat(),
                duration = 1500L,
            )
        }

        // Small delay for list to stabilize.
        game.wait(1.0, skipWaitingForLoading = true)
    }

    /**
     * Scrolls to a specific percentage. Requires a detectable scrollbar.
     *
     * @param percent The list percentage (0-100) to scroll to.
     * @return True if scroll operation was attempted.
     */
    private fun scrollToPercent(percent: Int): Boolean {
        val percent: Int = percent.coerceIn(0, 100)

        val bboxes: Pair<BoundingBox?, BoundingBox?> = getListScrollBarBoundingRegion()
        if (!bIsScrollable) {
            MessageLog.d(TAG, "[DEBUG] scrollToPercent:: List is not scrollable.")
            return false
        }

        val bboxBar: BoundingBox? = bboxes.first
        val bboxThumb: BoundingBox? = bboxes.second

        if (bboxBar == null) {
            MessageLog.w(TAG, "[WARN] scrollToPercent:: Failed to detect scrollbar.")
            return false
        }

        if (bboxThumb == null) {
            MessageLog.d(TAG, "[DEBUG] scrollToPercent:: Failed to detect scrollbar thumb.")
            return false
        }

        val targetY: Int = bboxBar.y + (bboxBar.h.toDouble() * (percent.toDouble() / 100.0)).toInt()

        game.gestureUtils.swipe(
            (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
            (bboxThumb.y + (bboxThumb.h.toFloat() / 2.0)).toFloat(),
            (bboxThumb.x + (bboxThumb.w.toFloat() / 2.0)).toFloat(),
            targetY.toFloat(),
            duration = 1500L,
        )

        // Small delay for list to stabilize.
        game.wait(1.0, skipWaitingForLoading = true)

        return true
    }

    /**
     * Scrolls down the list.
     *
     * @param startLoc Optional swipe start location. Defaults to list center.
     * @param entryHeight Optional entry height to determine scroll distance.
     * @param durationMs Swipe duration. Minimum 250ms for Accessibility Service registration.
     */
    fun scrollDown(startLoc: Point? = null, entryHeight: Int = 0, durationMs: Long = 250L, force: Boolean = false) {
        if (!bIsScrollable && !force) {
            MessageLog.d(TAG, "[DEBUG] scrollDown:: List is not scrollable.")
            return
        }

        val durationMs: Long = durationMs.coerceAtLeast(250L)
        val x0: Int = ((startLoc?.x ?: (bboxList.x + (bboxList.w / 2)))).toInt()
        val y0: Int = ((startLoc?.y ?: (bboxList.y + (bboxList.h / 2)))).toInt()
        // Add some extra height since scrolling isn't accurate.
        val y1: Int = (bboxList.y - entryHeight).toInt().coerceAtLeast(0)
        game.gestureUtils.swipe(x0.toFloat(), y0.toFloat(), x0.toFloat(), y1.toFloat(), duration = durationMs)
        stopScrolling()
    }

    /**
     * Scrolls up the list.
     *
     * @param startLoc Optional swipe start location. Defaults to list center.
     * @param entryHeight Optional entry height to determine scroll distance.
     * @param durationMs Swipe duration. Minimum 250ms for Accessibility Service registration.
     */
    fun scrollUp(startLoc: Point? = null, entryHeight: Int = 0, durationMs: Long = 250L, force: Boolean = false) {
        if (!bIsScrollable && !force) {
            MessageLog.d(TAG, "[DEBUG] scrollUp:: List is not scrollable.")
            return
        }

        val durationMs: Long = durationMs.coerceAtLeast(250L)
        val x0: Int = ((startLoc?.x ?: (bboxList.x + (bboxList.w / 2)))).toInt()
        val y0: Int = ((startLoc?.y ?: (bboxList.y + (bboxList.h / 2)))).toInt()
        // Add some extra height since scrolling isn't accurate.
        val y1: Int = (bboxList.y + bboxList.h + (entryHeight * 1.5)).toInt().coerceAtLeast(0)
        game.gestureUtils.swipe(x0.toFloat(), y0.toFloat(), x0.toFloat(), y1.toFloat(), duration = durationMs)
        stopScrolling()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Scrolls through the list and executes a callback for each entry.
     *
     * @param maxTimeMs Maximum processing time before timeout.
     * @param bScrollBottomToTop If true, process from bottom to top.
     * @param keyExtractor Optional callback to generate unique keys and skip duplicates.
     * @param entryComponent Optional component to use for identifying rows.
     * @param bForceComponentDetection If true, force component-based detection even if the scroll list region is found.
     * @param onEntry Callback executed for each entry. Return true to exit early.
     * @return True if processing completed or exited early via callback.
     */
    fun process(
        maxTimeMs: Int = MAX_PROCESS_TIME_DEFAULT_MS,
        bScrollBottomToTop: Boolean = false,
        keyExtractor: ((ScrollListEntry) -> String?)? = null,
        entryComponent: ComponentInterface? = null,
        bForceComponentDetection: Boolean = false,
        onEntry: OnEntryDetectedCallback,
    ): Boolean {
        var bitmap = game.imageUtils.getSourceBitmap()

        // Force the initial scroll-to-top when the caller can detect list content by key: the
        // scrollbar often fails to render on an idle list (career-end Learn list), so without this
        // a second pass would start wherever the previous one ended and only see the bottom
        // screenful. A forced swipe on a genuinely single-screen list is a harmless no-op.
        if (bScrollBottomToTop) scrollToBottom(bitmap) else scrollToTop(bitmap, force = keyExtractor != null)

        // Max time for the scroll loop, in ms. Use the caller-supplied value: an earlier version
        // shadowed the parameter with a hardcoded 60_000 local, pinning every caller to 60s.
        val startTime: Long = System.currentTimeMillis()
        val maxTimeMsLong: Long = maxTimeMs.toLong()

        // Track bounding boxes for average entry height calculation.
        val entryBboxes: MutableList<BoundingBox> = mutableListOf()
        // Y position for termination check.
        var prevThumbY: Int? = null
        // Consecutive "thumb didn't advance after a scroll" reads. A single no-move is usually a
        // dropped/under-registered swipe (on MuMu the thumb climbs steadily, then one swipe no-ops
        // mid-list), not the end of the list - so require several in a row, re-issuing the scroll
        // each time, before concluding we've reached the bottom.
        var consecutiveNoMove = 0
        // A genuine end with the thumb at track bottom exits via the fast path below, so this only
        // governs the ambiguous "thumb not at bottom, not moving" case; 2 still tolerates a single
        // dropped swipe while cutting one confirmation scroll per list-end.
        val maxConsecutiveNoMove = 2
        // Treat thumb movement within this many pixels as "no move". The scrollbar bbox comes from
        // CV detection and jitters +-1-2px between frames; an exact-equality test let a stalled
        // thumb read as "moved", resetting consecutiveNoMove forever so a wedged list never
        // concluded end and ran to the maxTimeMs timeout. A real scroll step moves it far more.
        val thumbMoveTolerancePx = 3
        // Content-based end detection for lists whose scrollbar is never detected (used by the
        // no-scrollbar branch in the loop below): consecutive scrolls that reveal no new entries.
        var consecutiveNoNew = 0
        // Two no-new reads in a row still tolerates a single dropped swipe / detection miss while
        // cutting one confirmation scroll per list-end on the no-scrollbar path.
        val maxConsecutiveNoNew = 2
        // Consecutive frames in which detection found ZERO entries despite retries. Distinct from
        // "no NEW entries": zero detections means the list is unreadable (occluded by a popup or a
        // stale capture), and riding the end-of-list paths from that state silently drops the rest
        // of the list (a buy pass saw zero entries and exited as a clean "end of list").
        var consecutiveEmptyFrames = 0
        val maxConsecutiveEmptyFrames = 3
        // One-shot escalation for a scrollbar thumb pinned mid-track (frozen list / dropped input).
        var bEscalatedScroll = false

        // Stores keys from the previous frame to identify the overlap with the current frame.
        var lastFrameKeys: List<String> = emptyList()

        var index = 0
        while (System.currentTimeMillis() - startTime < maxTimeMsLong) {
            var currentFrameEntries: List<ScrollListEntry> = emptyList()
            // Whether this frame revealed at least one entry not present in the previous frame.
            // Drives content-based end detection when no scrollbar is available.
            var foundNewEntries = false
            var retries = 3
            while (retries > 0) {
                bitmap = game.imageUtils.getSourceBitmap()
                val detectedEntries = detectEntries(bitmap, entryComponent, bForceComponentDetection)

                if (detectedEntries.isNotEmpty()) {
                    currentFrameEntries =
                        if (bScrollBottomToTop) {
                            // If scrolling bottom to top, reverse the entries to process them in meaningful order.
                            // Entries from detectEntries() are always sorted top to bottom on the screen.
                            detectedEntries.reversed().map { entry ->
                                // Assign a unique increasing index to each detected entry.
                                entry.copy(index = index++)
                            }
                        } else {
                            detectedEntries.map { entry ->
                                // Assign a unique increasing index to each detected entry.
                                entry.copy(index = index++)
                            }
                        }
                    break
                }

                retries--
                if (retries > 0) {
                    MessageLog.d(TAG, "[DEBUG] process:: No entries detected. Retrying ($retries left)...")
                    game.wait(0.2, skipWaitingForLoading = true)
                }
            }

            // Rectangle-based row detection (detectRectanglesGeneric) flood-fills the page background and
            // keeps only high-contrast blobs; on the Trackblazer shop the white-on-white item cards blend
            // into that background and intermittently yield ZERO rows on a fully-rendered screen. When that
            // happens and the caller supplied a per-row landmark component, locate the rows by that
            // component before counting an empty frame. Only the shop/inventory scans pass an
            // entryComponent, so this is a no-op for the race-list and skill-list scans (they pass null).
            if (currentFrameEntries.isEmpty() && entryComponent != null && !bForceComponentDetection) {
                val byComponent = detectEntries(bitmap, entryComponent, bForceComponentDetection = true)
                if (byComponent.isNotEmpty()) {
                    MessageLog.i(
                        TAG,
                        "[INFO] process:: Rectangle detection found no rows; recovered ${byComponent.size} via ${entryComponent.template.basename} landmark detection.",
                    )
                    currentFrameEntries =
                        if (bScrollBottomToTop) {
                            byComponent.reversed().map { it.copy(index = index++) }
                        } else {
                            byComponent.map { it.copy(index = index++) }
                        }
                }
            }

            if (currentFrameEntries.isEmpty()) {
                consecutiveEmptyFrames++
                MessageLog.w(TAG, "[WARN] process:: No entries detected in current frame after retries (empty frame $consecutiveEmptyFrames/$maxConsecutiveEmptyFrames).")
                if (consecutiveEmptyFrames >= maxConsecutiveEmptyFrames) {
                    game.imageUtils.saveBitmap(filename = "scroll_list_empty_frames", fullRes = true)
                    MessageLog.e(TAG, "[ERROR] process:: $consecutiveEmptyFrames consecutive frames with zero detected entries - the list is empty or row detection failed (capture rendered but no rows matched). Aborting the scroll pass.")
                    return false
                }
            } else {
                consecutiveEmptyFrames = 0
                // Determine the overlap with the previous frame's entries using the provided keyExtractor.
                var skipCount = 0
                if (keyExtractor != null && lastFrameKeys.isNotEmpty()) {
                    val currentFrameKeys = currentFrameEntries.map { keyExtractor(it) ?: "UNKNOWN_${it.index}" }

                    // Find the largest suffix of lastFrameKeys that matches a prefix of currentFrameKeys.
                    for (i in lastFrameKeys.size.coerceAtMost(currentFrameKeys.size) downTo 1) {
                        val suffix = lastFrameKeys.takeLast(i)
                        val prefix = currentFrameKeys.take(i)
                        if (suffix == prefix) {
                            skipCount = i
                            break
                        }
                    }

                    if (game.debugMode && skipCount > 0) {
                        MessageLog.d(TAG, "[DEBUG] process:: Identified overlap of $skipCount items between frames. Matching sequence: ${currentFrameKeys.take(skipCount).joinToString(", ")}")
                    }
                }

                // This frame is "new" if not every detected entry overlapped the previous frame.
                foundNewEntries = skipCount < currentFrameEntries.size

                // Process only the new entries that weren't part of the overlap.
                for (i in skipCount until currentFrameEntries.size) {
                    val entry = currentFrameEntries[i]
                    if (onEntry.onEntryDetected(this, entry)) {
                        MessageLog.d(TAG, "[DEBUG] process:: onEntry callback returned TRUE for entry ${entry.index}. Exiting loop.")
                        return true
                    }
                }

                // Update the last frame's keys for the next iteration's overlap detection.
                if (keyExtractor != null) {
                    lastFrameKeys = currentFrameEntries.map { keyExtractor(it) ?: "UNKNOWN_${it.index}" }
                }
            }

            entryBboxes.addAll(currentFrameEntries.map { it.bbox })
            val avgEntryHeight: Int = if (entryBboxes.isEmpty()) 0 else entryBboxes.map { it.h }.average().toInt()
            // Swipe from the list CENTER, not the left edge. bboxEntries.x is the list's left margin (~16px);
            // edge swipes under-register and pin mid-list on MuMu. The horizontal center is reliably inside
            // the scrollable area. The start Y stays at the last visible entry so the upward drag still
            // spans the full list height.
            val scrollStartLoc: Point? = if (currentFrameEntries.isEmpty()) null else Point((bboxList.x + (bboxList.w / 2)).toDouble(), currentFrameEntries.last().bbox.y.toDouble())

            // Always attempt a scroll. When the scrollbar wasn't detected on this frame
            // (bIsScrollable == false) but the caller supplied a keyExtractor - so we can detect the
            // end of the list by content - force a probe scroll instead of giving up. Some screens
            // (notably the career-end "Learn" skill list) hide or fail to render a detectable
            // scrollbar while idle; trusting that single detection truncated the read to one
            // screenful and dropped every entry below (career-end skill buy left hundreds of SP
            // unspent). A probe scroll usually makes the scrollbar appear, and the content-based
            // check below terminates the loop if the list is genuinely a single screen.
            val force: Boolean = !bIsScrollable && keyExtractor != null
            if (bScrollBottomToTop) {
                scrollUp(startLoc = scrollStartLoc, entryHeight = avgEntryHeight, force = force)
            } else {
                scrollDown(startLoc = scrollStartLoc, entryHeight = avgEntryHeight, force = force)
            }
            // Slight delay to allow screen to settle before next iteration.
            game.wait(0.5, skipWaitingForLoading = true)

            // END-OF-LIST DETECTION
            // A scroll can reveal a scrollbar that wasn't rendered while idle, so re-detect here.
            val scrollBarBboxes: Pair<BoundingBox?, BoundingBox?> = getListScrollBarBoundingRegion()
            val bboxBar: BoundingBox? = scrollBarBboxes.first
            val bboxThumb: BoundingBox? = scrollBarBboxes.second
            when {
                bboxThumb != null -> {
                    // Scrollbar present: terminate on thumb movement. If the thumb didn't advance,
                    // the scroll either reached the end OR a swipe failed to register. Distinguish
                    // them by counting consecutive no-moves and only concluding "end" after several;
                    // each no-move falls through and re-issues the scroll, and the per-frame dedup
                    // prevents double-processing.
                    if (prevThumbY != null && abs(bboxThumb.y - prevThumbY!!) <= thumbMoveTolerancePx) {
                        consecutiveNoMove++
                        // A thumb resting at the BOTTOM of its track is a genuine end of list; a thumb
                        // pinned mid-track means the list stopped responding to swipes entirely (an
                        // emulator input outage can pin it mid-track across many swipes) - treating
                        // that as "end" silently dropped everything below.
                        val trackBottom: Int? = bboxBar?.let { it.y + it.h }
                        val bAtTrackBottom: Boolean = trackBottom == null || (bboxThumb.y + bboxThumb.h) >= trackBottom - 20
                        // Fast end-of-list: a thumb already at the track bottom on a frame that revealed
                        // NO new entries is unambiguous - we scrolled to the end and already processed
                        // everything above it (new rows are handled at the top of the loop), so exit now
                        // instead of burning two more no-move re-scroll cycles to re-confirm. A dropped
                        // swipe can't fake "thumb at bottom", and the !foundNewEntries co-guard guarantees
                        // nothing unread remains. Deliberately NOT gated on a "thumb fills track" heuristic:
                        // a faint or partially-rendered scrollbar can collapse the track bbox down to the
                        // thumb height and mis-fire at the TOP of a genuinely long list (would re-introduce
                        // the career-end skill-list truncation).
                        if (bAtTrackBottom && !foundNewEntries && currentFrameEntries.isNotEmpty()) {
                            MessageLog.w(TAG, "[WARN] process:: Reached end of scroll list (thumb at track bottom, no new entries). Exiting loop.")
                            return true
                        }
                        if (consecutiveNoMove >= maxConsecutiveNoMove) {
                            // A thumb pinned mid-track means the list stopped responding to swipes
                            // entirely. Escalate once, then abort loudly rather than treating it as "end".
                            if (bAtTrackBottom) {
                                MessageLog.w(TAG, "[WARN] process:: Reached end of scroll list ($consecutiveNoMove consecutive no-move reads, thumb at track bottom). Exiting loop.")
                                return true
                            }
                            if (!bEscalatedScroll) {
                                bEscalatedScroll = true
                                consecutiveNoMove = 0
                                MessageLog.w(TAG, "[WARN] process:: Thumb pinned mid-track at y=${bboxThumb.y} (track bottom $trackBottom); escalating with one slow full swipe.")
                                if (bScrollBottomToTop) {
                                    scrollUp(startLoc = scrollStartLoc, entryHeight = avgEntryHeight, durationMs = 600L, force = true)
                                } else {
                                    scrollDown(startLoc = scrollStartLoc, entryHeight = avgEntryHeight, durationMs = 600L, force = true)
                                }
                                game.wait(0.5, skipWaitingForLoading = true)
                            } else {
                                game.imageUtils.saveBitmap(filename = "scroll_list_frozen", fullRes = true)
                                MessageLog.e(TAG, "[ERROR] process:: List frozen mid-track (thumb pinned at y=${bboxThumb.y} after escalation). Aborting the scroll pass.")
                                return false
                            }
                        } else {
                            MessageLog.w(TAG, "[WARN] process:: Thumb did not advance (no-move $consecutiveNoMove/$maxConsecutiveNoMove at y=${bboxThumb.y}); re-issuing scroll.")
                        }
                        // Do NOT update prevThumbY - keep comparing against the last position the thumb actually held.
                    } else {
                        consecutiveNoMove = 0
                        prevThumbY = bboxThumb.y
                    }
                }
                keyExtractor != null -> {
                    // No scrollbar detected even after scrolling, but content tells us when the list
                    // ends: if a scroll produced no new entries (the whole frame overlapped the
                    // previous one), the list is done. Require several in a row so a single dropped
                    // swipe - or a brief detection miss - doesn't end the read prematurely.
                    if (foundNewEntries) {
                        consecutiveNoNew = 0
                    } else {
                        consecutiveNoNew++
                        if (consecutiveNoNew >= maxConsecutiveNoNew) {
                            MessageLog.w(TAG, "[WARN] process:: No new entries after $consecutiveNoNew scrolls and no scrollbar; treating as end of list. Exiting loop.")
                            return true
                        }
                        MessageLog.w(TAG, "[WARN] process:: No new entries (no-new $consecutiveNoNew/$maxConsecutiveNoNew) and no scrollbar; re-issuing scroll.")
                    }
                }
                else -> {
                    // No scrollbar and no keyExtractor to detect a content-based end: preserve the
                    // original single-frame behavior rather than loop until the timeout.
                    MessageLog.d(TAG, "[DEBUG] process:: No scrollbar thumb detected. Exiting loop.")
                    return true
                }
            }
        }

        MessageLog.e(TAG, "[ERROR] process:: Timed out.")
        return false
    }
}
