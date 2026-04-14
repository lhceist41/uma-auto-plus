import { Settings } from "../context/BotStateContext"

export interface CharacterPreset {
    name: string
    scenario: string
    settings: Partial<Settings>
}

export const characterPresets: CharacterPreset[] = 
[
    {
        "name": "Agnes Tachyon",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Mile",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Wit",
                    "Stamina",
                    "Power",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 700,
                "trainingMileStatTarget_gutsStatTarget": 350,
                "trainingMileStatTarget_witStatTarget": 700,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 700,
                "trainingMediumStatTarget_gutsStatTarget": 350,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 700,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Air Groove",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 700,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Daiwa Scarlet",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "El Condor Pasa",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Gold Ship",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 900,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 1000,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Grass Wonder",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Haru Urara",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Dirt",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Guts",
                    "Wit"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Hishi Amazon",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "King Halo",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 850,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Matikanefukukitaru",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Mayano Top Gun",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Nice Nature",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Sakura Bakushin O",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Wit",
                    "Guts",
                    "Stamina"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 300,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 450,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 600,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Taiki Shuttle",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Vodka",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Agnes Tachyon",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Mile",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Wit",
                    "Stamina",
                    "Power",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 700,
                "trainingMileStatTarget_gutsStatTarget": 350,
                "trainingMileStatTarget_witStatTarget": 700,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 700,
                "trainingMediumStatTarget_gutsStatTarget": 350,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 700,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Air Groove",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 700,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Daiwa Scarlet",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "El Condor Pasa",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Gold Ship",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 900,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 1000,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Grass Wonder",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Haru Urara",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Dirt",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Guts",
                    "Wit"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Hishi Amazon",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "King Halo",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 850,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Matikanefukukitaru",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Mayano Top Gun",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Nice Nature",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Sakura Bakushin O",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Wit",
                    "Guts",
                    "Stamina"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 300,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 450,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 600,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Taiki Shuttle",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Vodka",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Agnes Tachyon",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Mile",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Wit",
                    "Stamina",
                    "Power",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 700,
                "trainingMileStatTarget_gutsStatTarget": 350,
                "trainingMileStatTarget_witStatTarget": 700,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 700,
                "trainingMediumStatTarget_gutsStatTarget": 350,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 700,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Air Groove",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 700,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Daiwa Scarlet",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "El Condor Pasa",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Gold Ship",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 900,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 1000,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Grass Wonder",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Haru Urara",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Dirt",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Guts",
                    "Wit"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Hishi Amazon",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "End Closer",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "King Halo",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1100,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 500,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 500,
                "trainingMileStatTarget_powerStatTarget": 850,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 500,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 500,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 500
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Matikanefukukitaru",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Mayano Top Gun",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Long",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Stamina",
                    "Power",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 1000,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Nice Nature",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 900,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Sakura Bakushin O",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front Runner",
                "preferredTrackDistance": "Short",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Wit",
                    "Guts",
                    "Stamina"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 300,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 450,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 600,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 800,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Taiki Shuttle",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Short",
                    "Mile"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Pace Chaser",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 400,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Vodka",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late Surger",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Auto",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 450,
                "trainingSprintStatTarget_powerStatTarget": 900,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1200,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 650,
                "trainingMileStatTarget_powerStatTarget": 1000,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 800,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 700,
                "trainingMediumStatTarget_powerStatTarget": 900,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 600,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 900,
                "trainingLongStatTarget_powerStatTarget": 900,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 600
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Maruzensky (Formula R)",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Wit",
                    "Stamina",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 500,
                "trainingSprintStatTarget_witStatTarget": 1000,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 500,
                "trainingMileStatTarget_witStatTarget": 1000,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 500,
                "trainingMediumStatTarget_witStatTarget": 1000,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 500,
                "trainingLongStatTarget_witStatTarget": 1000
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Maruzensky (Formula R)",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Wit",
                    "Stamina",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 400,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 400,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 400,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 400
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Maruzensky (Formula R)",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Front",
                "preferredTrackDistance": "Mile",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1000,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 300,
                "trainingSprintStatTarget_witStatTarget": 300,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 300,
                "trainingMileStatTarget_witStatTarget": 300,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 300,
                "trainingMediumStatTarget_witStatTarget": 300,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 300,
                "trainingLongStatTarget_witStatTarget": 300
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Gold City (Autumn Cosmos)",
        "scenario": "Trackblazer",
        "settings": {
            "general": {
                "scenario": "Trackblazer",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": false,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 400,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 400,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 400,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 400
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 3,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1",
                    "G2"
                ],
                "trackblazerMinStatGainForCharm": 30,
                "trackblazerMaxRetriesPerRace": 1,
                "trackblazerWhistleForcesTraining": true,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": true,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 1
            }
        }
    },
    {
        "name": "Gold City (Autumn Cosmos)",
        "scenario": "Unity Cup",
        "settings": {
            "general": {
                "scenario": "Unity Cup",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1200,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 400,
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 400,
                "trainingMileStatTarget_witStatTarget": 400,
                "trainingMediumStatTarget_speedStatTarget": 1200,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 400,
                "trainingMediumStatTarget_witStatTarget": 400,
                "trainingLongStatTarget_speedStatTarget": 1200,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 400,
                "trainingLongStatTarget_witStatTarget": 400
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    },
    {
        "name": "Gold City (Autumn Cosmos)",
        "scenario": "URA Finale",
        "settings": {
            "general": {
                "scenario": "URA Finale",
                "enablePopupCheck": false,
                "enableCraneGameAttempt": true,
                "enableStopBeforeFinals": false,
                "enableStopAtDate": false,
                "stopAtDates": [
                    "Senior January Early"
                ],
                "waitDelay": 0.5,
                "dialogWaitDelay": 0.5
            },
            "racing": {
                "enableFarmingFans": false,
                "ignoreConsecutiveRaceWarning": false,
                "daysToRunExtraRaces": 5,
                "disableRaceRetries": false,
                "enableFreeRaceRetry": true,
                "enableCompleteCareerOnFailure": true,
                "enableStopOnMandatoryRaces": false,
                "enableForceRacing": false,
                "enableUserInGameRaceAgenda": false,
                "limitRacesToInGameAgenda": false,
                "skipSummerTrainingForAgenda": false,
                "selectedUserAgenda": "Agenda 1",
                "customAgendaTitle": "",
                "enableRacingPlan": false,
                "enableMandatoryRacingPlan": false,
                "racingPlan": "",
                "minFansThreshold": 0,
                "preferredTerrain": "Turf",
                "preferredGrades": [
                    "G1",
                    "G2",
                    "G3"
                ],
                "preferredDistances": [
                    "Mile",
                    "Medium",
                    "Long"
                ],
                "lookAheadDays": 10,
                "smartRacingCheckInterval": 2,
                "juniorYearRaceStrategy": "Default",
                "originalRaceStrategy": "Default",
                "minimumQualityThreshold": 50,
                "timeDecayFactor": 0.7,
                "improvementThreshold": 50
            },
            "skills": {
                "enableSkillPointCheck": false,
                "skillPointCheck": 600,
                "preferredRunningStyle": "Late",
                "preferredTrackDistance": "Medium",
                "preferredTrackSurface": "Turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": false,
                        "strategy": "default",
                        "enableBuyInheritedUniqueSkills": false,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": ""
                    }
                }
            },
            "trainingEvent": {
                "enablePrioritizeEnergyOptions": true,
                "enableAutomaticOCRRetry": true,
                "ocrConfidence": 90,
                "enableHideOCRComparisonResults": true,
                "specialEventOverrides": {
                    "New Year's Resolutions": {
                        "selectedOption": "Option 2: Energy +20",
                        "requiresConfirmation": false
                    },
                    "New Year's Shrine Visit": {
                        "selectedOption": "Option 1: Energy +30",
                        "requiresConfirmation": false
                    },
                    "Victory!": {
                        "selectedOption": "Option 2: Energy -5 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Solid Showing": {
                        "selectedOption": "Option 1: Energy -15 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Defeat": {
                        "selectedOption": "Option 1: Energy -25 and random stat gain",
                        "requiresConfirmation": false
                    },
                    "Get Well Soon!": {
                        "selectedOption": "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Don't Overdo It!": {
                        "selectedOption": "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status",
                        "requiresConfirmation": false
                    },
                    "Extra Training": {
                        "selectedOption": "Option 2: Energy +5",
                        "requiresConfirmation": false
                    },
                    "Acupuncture (Just an Acupuncturist, No Worries! ☆)": {
                        "selectedOption": "Option 3: Energy recovery + Heal all negative status effects",
                        "requiresConfirmation": false
                    },
                    "Etsuko's Exhaustive Coverage": {
                        "selectedOption": "Option 2: Energy Down / Gain skill points",
                        "requiresConfirmation": false
                    },
                    "A Team at Last": {
                        "selectedOption": "Default",
                        "requiresConfirmation": false
                    }
                },
                "characterEventOverrides": {},
                "supportEventOverrides": {},
                "scenarioEventOverrides": {}
            },
            "misc": {
                "enableSettingsDisplay": false,
                "enableMessageIdDisplay": false,
                "messageLogFontSize": 8,
                "overlayButtonSizeDP": 40
            },
            "training": {
                "trainingBlacklist": [],
                "statPrioritization": [
                    "Speed",
                    "Power",
                    "Stamina",
                    "Wit",
                    "Guts"
                ],
                "maximumFailureChance": 15,
                "disableTrainingOnMaxedStat": true,
                "focusOnSparkStatTarget": [
                    "Speed",
                    "Stamina",
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Mile",
                "mustRestBeforeSummer": true,
                "enableRiskyTraining": false,
                "riskyTrainingMinStatGain": 20,
                "riskyTrainingMaxFailureChance": 30,
                "trainWitDuringFinale": true,
                "enablePrioritizeSkillHints": true,
                "enableTrainingAnalysisValidation": false,
                "enableYoloStatDetection": false
            },
            "trainingStatTarget": {
                "trainingSprintStatTarget_speedStatTarget": 1000,
                "trainingSprintStatTarget_staminaStatTarget": 600,
                "trainingSprintStatTarget_powerStatTarget": 800,
                "trainingSprintStatTarget_gutsStatTarget": 300,
                "trainingSprintStatTarget_witStatTarget": 300,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 300,
                "trainingMileStatTarget_witStatTarget": 300,
                "trainingMediumStatTarget_speedStatTarget": 1000,
                "trainingMediumStatTarget_staminaStatTarget": 600,
                "trainingMediumStatTarget_powerStatTarget": 800,
                "trainingMediumStatTarget_gutsStatTarget": 300,
                "trainingMediumStatTarget_witStatTarget": 300,
                "trainingLongStatTarget_speedStatTarget": 1000,
                "trainingLongStatTarget_staminaStatTarget": 600,
                "trainingLongStatTarget_powerStatTarget": 800,
                "trainingLongStatTarget_gutsStatTarget": 300,
                "trainingLongStatTarget_witStatTarget": 300
            },
            "debug": {
                "enableDebugMode": false,
                "ocrThreshold": 230,
                "templateMatchConfidence": 0.8,
                "templateMatchCustomScale": 1,
                "debugMode_startTemplateMatchingTest": false,
                "debugMode_startSingleTrainingOCRTest": false,
                "debugMode_startComprehensiveTrainingOCRTest": false,
                "debugMode_startRaceListDetectionTest": false,
                "debugMode_startMainScreenUpdateTest": false,
                "debugMode_startSkillListBuyTest": false,
                "debugMode_startScrollBarDetectionTest": false,
                "debugMode_startTrackblazerRaceSelectionTest": false,
                "debugMode_startTrackblazerInventorySyncTest": false,
                "debugMode_startTrackblazerBuyItemsTest": false,
                "enableScreenRecording": false,
                "recordingBitRate": 6,
                "recordingFrameRate": 30,
                "recordingResolutionScale": 1,
                "enableRemoteLogViewer": false,
                "remoteLogViewerPort": 9000
            },
            "discord": {
                "enableDiscordNotifications": false,
                "discordUserID": ""
            },
            "scenarioOverrides": {
                "trackblazerConsecutiveRacesLimit": 5,
                "trackblazerEnergyThreshold": 40,
                "trackblazerShopCheckGrades": [
                    "G1"
                ],
                "trackblazerMinStatGainForCharm": 15,
                "trackblazerMaxRetriesPerRace": 3,
                "trackblazerWhistleForcesTraining": false,
                "trackblazerRetryRacesBeforeFinalGrades": [
                    "G1"
                ],
                "trackblazerEnableIrregularTraining": false,
                "trackblazerIrregularTrainingMinStatGain": 20,
                "trackblazerExcludedItems": [],
                "trackblazerShopCheckFrequency": 3
            }
        }
    }
]
