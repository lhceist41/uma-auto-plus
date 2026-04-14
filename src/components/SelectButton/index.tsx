import React, { useRef, useState, useMemo } from "react"
import { View, LayoutChangeEvent, StyleSheet, StyleProp, ViewStyle } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { Option, Select, SelectContent, SelectGroup, SelectItem, SelectLabel, NativeSelectScrollView } from "../ui/select"
import { Separator } from "../ui/separator"
import CustomButton from "../CustomButton"
import { ChevronDown } from "lucide-react-native"
import type { LucideIcon } from "lucide-react-native"
import * as SelectPrimitive from "@rn-primitives/select"

/**
 * Represents an option in the select dropdown.
 */
interface SelectOption {
    /** The value of the option. */
    value: string
    /** The display label of the option. */
    label: string
    /** Whether the option is disabled. */
    disabled?: boolean
}

/**
 * Props for the SelectButton component.
 */
interface SelectButtonProps {
    /** The visual style variant of the button. */
    variant?: "default" | "destructive" | "outline" | "primary" | "secondary" | "ghost" | "link" | "success" | "info" | "warning" | "error"
    /** The size preset for the button. */
    size?: "default" | "sm" | "lg" | "icon"
    /** Optional custom icon element. */
    icon?: React.ReactElement
    /** The Lucide icon component to display. */
    iconName?: LucideIcon
    /** Whether the icon appears to the left or right of the text. */
    iconPosition?: "left" | "right"
    /** The list of options for the select dropdown. */
    options?: SelectOption[]
    /** The label for the option group. */
    groupLabel?: string
    /** The portal host for the select dropdown. */
    portalHost?: string
    /** The default selected value. */
    defaultValue?: string
    /** The placeholder text when no option is selected. */
    placeholder?: string
    /** The currently selected value. */
    value?: string
    /** The function to set the selected value. */
    setValue?: React.Dispatch<React.SetStateAction<string>>
    /** Callback fired when the value changes. */
    onValueChange?: (value: string | undefined) => void
    /** Callback fired when the button is pressed. */
    onPress?: () => void
    /** Optional custom style for the container. */
    style?: StyleProp<ViewStyle>
}

/**
 * A themed button that opens a select dropdown. Colors track the variant and dark/light mode.
 * See SelectButtonProps for the props.
 */
const SelectButton: React.FC<SelectButtonProps> = ({
    variant = "default",
    size = "default",
    icon,
    iconName,
    iconPosition = "left",
    options = [],
    groupLabel,
    portalHost,
    defaultValue,
    placeholder,
    value,
    setValue,
    onValueChange,
    onPress,
    style,
}) => {
    const { colors, isDark } = useTheme()

    const triggerRef = useRef<View>(null)

    // Measured so the dropdown can match the trigger's width.
    const [triggerWidth, setTriggerWidth] = useState<number>(0)

    const currentLabel = options.find((item) => item.value === value || item.value === defaultValue)?.label

    /** Background color for the current variant and theme. */
    const getBackgroundColor = () => {
        switch (variant) {
            case "destructive":
                return colors.destructive
            case "outline":
                return isDark ? "black" : "white"
            case "primary":
                return colors.primary
            case "secondary":
                return colors.secondary
            case "ghost":
                return "transparent"
            case "link":
                return "transparent"
            case "success":
                return colors.success
            case "info":
                return colors.info
            case "warning":
                return colors.warning
            case "error":
                return colors.error
            default:
                return colors.secondary
        }
    }

    /** Text color for the current variant and theme. */
    const getTextColor = () => {
        switch (variant) {
            case "destructive":
                return colors.destructiveForeground
            case "outline":
                return isDark ? "white" : "black"
            case "primary":
                return colors.primaryForeground
            case "secondary":
                return colors.secondaryForeground
            case "ghost":
                return isDark ? "white" : "black"
            case "link":
                return isDark ? "white" : "black"
            case "success":
                return colors.successContent
            case "info":
                return colors.infoContent
            case "warning":
                return colors.warningContent
            case "error":
                return colors.errorContent
            default:
                return colors.secondaryForeground
        }
    }

    const getSelectContentBorderColor = () => {
        switch (variant) {
            case "outline":
                return isDark ? "white" : "black"
            default:
                return getBackgroundColor()
        }
    }

    const styles = useMemo(
        () =>
            StyleSheet.create({
                container: {
                    flexDirection: "row",
                    alignItems: "center",
                },
                button: {
                    flex: 1,
                    borderTopRightRadius: 0,
                    borderBottomRightRadius: 0,
                    borderWidth: 0,
                },
                buttonDropdown: {
                    borderTopLeftRadius: 0,
                    borderBottomLeftRadius: 0,
                    borderColor: "transparent",
                    borderWidth: 0,
                },
            }),
        [colors]
    )

    // Capture the trigger width on layout so the dropdown content can match it.
    const onTriggerLayout = (event: LayoutChangeEvent) => {
        const { width: measuredWidth } = event.nativeEvent.layout
        setTriggerWidth(measuredWidth)
    }

    const onPressButton = () => {
        if (onPress) {
            onPress()
        }
    }

    const handleValueChange = (option: Option) => {
        if (onValueChange) {
            onValueChange(option?.value || "")
        }
        if (setValue) {
            setValue(option?.value || "")
        }
    }

    /** Resolves the icon to render: explicit element wins, else build one from iconName. */
    const getIcon = (): React.ReactElement | undefined => {
        if (icon) {
            return icon
        } else if (iconName != null && typeof iconName === "function") {
            const IconComponent = iconName
            return <IconComponent size={20} color={getTextColor()} />
        } else {
            return undefined
        }
    }

    return (
        <Select onValueChange={handleValueChange} value={value as any} defaultValue={defaultValue as any}>
            <View style={[styles.container, style]} ref={triggerRef} onLayout={onTriggerLayout}>
                <CustomButton style={styles.button} variant={variant as any} icon={getIcon()} iconPosition={iconPosition} size={size} isLoading={false} onPress={onPressButton}>
                    {currentLabel ?? placeholder}
                </CustomButton>
                <Separator orientation="vertical" style={{ backgroundColor: "transparent" }} />
                <SelectPrimitive.Trigger asChild>
                    <CustomButton style={styles.buttonDropdown} variant={variant as any} size={"icon"} isLoading={false}>
                        <ChevronDown size={20} color={getTextColor()} />
                    </CustomButton>
                </SelectPrimitive.Trigger>
            </View>
            <SelectContent
                style={{
                    width: triggerWidth,
                    backgroundColor: getBackgroundColor(),
                    borderColor: getSelectContentBorderColor(),
                }}
                align="end"
                sideOffset={1}
                position="popper"
                portalHost={portalHost}
            >
                <NativeSelectScrollView>
                    <SelectGroup>
                        {groupLabel && <SelectLabel style={{ color: getTextColor() }}>{groupLabel}</SelectLabel>}
                        {options &&
                            options.map((option) => (
                                <SelectItem key={option.value} label={option.label} value={option.value} disabled={option.disabled} textStyle={{ color: getTextColor() }}>
                                    {option.label}
                                </SelectItem>
                            ))}
                    </SelectGroup>
                </NativeSelectScrollView>
            </SelectContent>
        </Select>
    )
}

export default React.memo(SelectButton)
