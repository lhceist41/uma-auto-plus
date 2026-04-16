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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
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
                "characterEventOverrides": {
                    "Agnes Tachyon|Expression of Conviction": 0,
                    "Agnes Tachyon|Tachyon the Spoiled Child": 1,
                    "Agnes Tachyon|At Tachyon's Pace": 1,
                    "Agnes Tachyon|The Strongest Collaborator?!": 0,
                    "Agnes Tachyon|Hamburger Helper!": 0,
                    "Agnes Tachyon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trainingMediumStatTarget_speedStatTarget": 1100,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
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
                "characterEventOverrides": {
                    "Air Groove|The Empress and Mom": 0,
                    "Air Groove|Seize Her!": 1,
                    "Air Groove|Take Good Care of Your Tail": 1,
                    "Air Groove|A Taste of Effort": 0,
                    "Air Groove|Imprinted Memories": 0,
                    "Air Groove|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Air Groove|Flowers for You": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
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
                "characterEventOverrides": {
                    "Daiwa Scarlet|The Weight of Racewear": 0,
                    "Daiwa Scarlet|Recommended Restaurant": 0,
                    "Daiwa Scarlet|Advice from an Older Student": 0,
                    "Daiwa Scarlet|Enjoying Number One": 0,
                    "Daiwa Scarlet|Can't Lose Sight of Number One!": 0,
                    "Daiwa Scarlet|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "El Condor Pasa|Passion-filled Outfit": 1,
                    "El Condor Pasa|A Personalized Mask": 0,
                    "El Condor Pasa|Go for the Extra-Large Pizza!": 0,
                    "El Condor Pasa|Hot and Spicy!": 0,
                    "El Condor Pasa|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "El Condor Pasa|Determination of the World's Strongest": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
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
                "characterEventOverrides": {
                    "Gold Ship|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Gold Ship|The Red of the Protagonist!": 0,
                    "Gold Ship|A Date, Golshi Style": 0,
                    "Gold Ship|A Sudden Episode from Golshi's Past!": 1,
                    "Gold Ship|Pair Discount Repeat Offender": 1,
                    "Gold Ship|Which Did You Lose?": 1,
                    "Gold Ship|My Part-Time Job Is... Crazy?": 0,
                    "Gold Ship|The Day After, Voices Hoarse": 0,
                    "Gold Ship|This One's For Keeps!": 0,
                    "Gold Ship|Killer Appetite!": 0,
                    "Gold Ship|Legend of the Left Pinky": 0,
                    "Gold Ship|Hello From About 1.5 Billion Years Ago": 1,
                    "Gold Ship|A Lovely Place": 0,
                    "Gold Ship|Nighttime Park Visit": 1,
                    "Gold Ship|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Guts"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
                    "Medium",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
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
                "characterEventOverrides": {
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Grass Wonder|A Warrior's Spirit": 0,
                    "Grass Wonder|Whimsical Encounter": 1,
                    "Grass Wonder|Everlasting Game": 0,
                    "Grass Wonder|Errands Have Perks": 0,
                    "Grass Wonder|Beauteaful": 0,
                    "Grass Wonder|Tracen Karuta Queen": 1,
                    "Grass Wonder|Together for Tea": 0,
                    "Grass Wonder|Yamato Nadeshiko": 1,
                    "Grass Wonder|Childhoods Apart": 1,
                    "Grass Wonder|Childhood Dream": 1,
                    "Grass Wonder|Flower Vase": 1,
                    "Grass Wonder|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Grass Wonder|Hidden Meaning": 1,
                    "Grass Wonder|Principles": 0,
                    "Grass Wonder|Hate to Lose": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
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
                "characterEventOverrides": {
                    "Haru Urara|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Haru Urara|Arm-Wrestling Contest": 1,
                    "Haru Urara|Looking for Something Important": 1,
                    "Haru Urara|Sand Training!": 0,
                    "Haru Urara|The Final Boss... Spe!": 0,
                    "Haru Urara|A Little Detour!": 0,
                    "Haru Urara|Parks Are Fun!": 0,
                    "Haru Urara|So Cool!": 0,
                    "Haru Urara|Forgot to Eat!": 1,
                    "Haru Urara|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Haru Urara|The Racewear I Love!": 0,
                    "Haru Urara|Pair Interview!": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
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
                "characterEventOverrides": {
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Hishi Amazon|Another Level": 0,
                    "Hishi Amazon|One-on-One! Gangster! Racewear!": 0,
                    "Hishi Amazon|Friend or Rival?": 0,
                    "Hishi Amazon|Hishiama's Dorm-Leader Breakfast": 0,
                    "Hishi Amazon|Hishiama's Needlework": 0,
                    "Hishi Amazon|Hishiama's Foraging": 0,
                    "Hishi Amazon|The Magic of Sweets?": 0,
                    "Hishi Amazon|Blazing Memories": 0,
                    "Hishi Amazon|Cool and Fiery Sisters": 0,
                    "Hishi Amazon|Hishiama's Special View": 1,
                    "Hishi Amazon|Hishiama and the Arts": 0,
                    "Hishi Amazon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
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
                "characterEventOverrides": {
                    "King Halo|Etsuko's Exhaustive Coverage (G1)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "King Halo|The Outfit That Suits Me Most": 1,
                    "King Halo|Running Isn't Everything": 1,
                    "King Halo|Manners Are Common Sense": 0,
                    "King Halo|Movies Are Full of Learning Opportunities": 0,
                    "King Halo|The King Knows No Exhaustion": 0,
                    "King Halo|First-Rate in Studies Too": 0,
                    "King Halo|After-School Soda": 1,
                    "King Halo|Three Heads Are Better than One": 0,
                    "King Halo|Sweet Tooth Temptation": 0,
                    "King Halo|First-Rate Spot": 1,
                    "King Halo|First-Rate Harvest": 1,
                    "King Halo|Crowds Are No Problem": 1,
                    "King Halo|Breaking Curfew is Second-Rate": 1,
                    "King Halo|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 550,
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
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
                "characterEventOverrides": {
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Matikanefukukitaru|I'll Protect You!": 0,
                    "Matikanefukukitaru|Now or Never! Sacred Sites": 0,
                    "Matikanefukukitaru|When Fukukitaru Comes, Fortune Follows": 0,
                    "Matikanefukukitaru|Cursed Camera": 1,
                    "Matikanefukukitaru|Manhattan's Dream": 1,
                    "Matikanefukukitaru|Pretty Gunslingers": 0,
                    "Matikanefukukitaru|Seven Gods of Fortune Fine Food Tour": 0,
                    "Matikanefukukitaru|Punch in a Pinch": 0,
                    "Matikanefukukitaru|Taking the Plunge": 0,
                    "Matikanefukukitaru|Shrine Visit": 1,
                    "Matikanefukukitaru|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Matikanefukukitaru|Room of the Chosen Ones": 1,
                    "Matikanefukukitaru|Better Fortune! Lucky Telephone": 0,
                    "Matikanefukukitaru|Under the Meteor Shower": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
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
                "characterEventOverrides": {
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Mayano Top Gun|You're My Sunshine ☆": 0,
                    "Mayano Top Gun|Meant to Be ♪": 0,
                    "Mayano Top Gun|With My Whole Heart!": 0,
                    "Mayano Top Gun|Maya Will Teach You ☆": 0,
                    "Mayano Top Gun|Tips from a Top Model!": 0,
                    "Mayano Top Gun|Maya's Race Class ☆": 0,
                    "Mayano Top Gun|Hearty Chanko! ☆": 0,
                    "Mayano Top Gun|Maya's Exciting ☆ Livestream!": 0,
                    "Mayano Top Gun|Maya's Euphoric ☆ Livestream!": 1,
                    "Mayano Top Gun|Maya's Special Someone!": 0,
                    "Mayano Top Gun|Wish on a Star": 1,
                    "Mayano Top Gun|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Mayano Top Gun|Maya's Thrilling ☆ Test of Courage": 0,
                    "Mayano Top Gun|Sweet Feelings for You ♪": 0,
                    "Mayano Top Gun|Mayano Takes Off ☆": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
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
                "characterEventOverrides": {
                    "Nice Nature|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Nice Nature|Nature and Her Tired Trainer": 0,
                    "Nice Nature|Bittersweet Sparkle": 0,
                    "Nice Nature|Festive Colors": 1,
                    "Nice Nature|Rainy-Day Fun": 0,
                    "Nice Nature|Not My Style": 1,
                    "Nice Nature|Whirlwind Advice": 1,
                    "Nice Nature|A Little Can't Hurt": 0,
                    "Nice Nature|A Phone Call from Mom": 0,
                    "Nice Nature|Once in a While": 1,
                    "Nice Nature|Snapshot of Emotions": 1,
                    "Nice Nature|Let's Watch the Fish": 1,
                    "Nice Nature|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
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
                "characterEventOverrides": {
                    "Sakura Bakushin O|Bakushin for Love!": 0,
                    "Sakura Bakushin O|A Day Without a Class Rep": 0,
                    "Sakura Bakushin O|Bakushin in Signature Racewear!": 1,
                    "Sakura Bakushin O|The Bakushin Book!": 0,
                    "Sakura Bakushin O|The Voices of the Students": 1,
                    "Sakura Bakushin O|Solving Riddles, Bakushin Style!": 0,
                    "Sakura Bakushin O|Bakushin?! Class?!": 0,
                    "Sakura Bakushin O|Bakushin-ing with a Classmate!": 1,
                    "Sakura Bakushin O|The Best Bakushin!": 0,
                    "Sakura Bakushin O|Bakushin, Now and Forever!": 0,
                    "Sakura Bakushin O|Together with Someone Important!": 1,
                    "Sakura Bakushin O|The Speed King": 0,
                    "Sakura Bakushin O|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
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
                "characterEventOverrides": {
                    "Taiki Shuttle|Quick Draw Showdown": 0,
                    "Taiki Shuttle|Must-Win Match": 0,
                    "Taiki Shuttle|To the Top!": 1,
                    "Taiki Shuttle|Hide-and-Seek": 0,
                    "Taiki Shuttle|Embracing Guidance": 0,
                    "Taiki Shuttle|Harvest Festival": 0,
                    "Taiki Shuttle|Meaty Heaven": 0,
                    "Taiki Shuttle|Rainy Power": 1,
                    "Taiki Shuttle|Rainy Choice": 0,
                    "Taiki Shuttle|Rainy Rescue": 0,
                    "Taiki Shuttle|Let's Patrol!": 0,
                    "Taiki Shuttle|Going Home Together": 0,
                    "Taiki Shuttle|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
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
                "characterEventOverrides": {
                    "Vodka|Vintage Style": 0,
                    "Vodka|Makings of a Friend": 0,
                    "Vodka|Hot and Cool": 0,
                    "Vodka|Like a Kid": 0,
                    "Vodka|Challenging Fate": 1,
                    "Vodka|Showdown by the River!": 0,
                    "Vodka|The Ultimate Choice": 0,
                    "Vodka|Awkward Honesty": 0,
                    "Vodka|The Standards of Coolness": 0,
                    "Vodka|Ring Out, Passionate Sound!": 0,
                    "Vodka|The Way of Cool": 1,
                    "Vodka|Let's Take a Little Detour": 0,
                    "Vodka|Sugar and Spice": 1,
                    "Vodka|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
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
                "characterEventOverrides": {
                    "Agnes Tachyon|Expression of Conviction": 0,
                    "Agnes Tachyon|Tachyon the Spoiled Child": 1,
                    "Agnes Tachyon|At Tachyon's Pace": 1,
                    "Agnes Tachyon|The Strongest Collaborator?!": 0,
                    "Agnes Tachyon|Hamburger Helper!": 0,
                    "Agnes Tachyon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trainingMediumStatTarget_speedStatTarget": 1100,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
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
                "characterEventOverrides": {
                    "Air Groove|The Empress and Mom": 0,
                    "Air Groove|Seize Her!": 1,
                    "Air Groove|Take Good Care of Your Tail": 1,
                    "Air Groove|A Taste of Effort": 0,
                    "Air Groove|Imprinted Memories": 0,
                    "Air Groove|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Air Groove|Flowers for You": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
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
                "characterEventOverrides": {
                    "Daiwa Scarlet|The Weight of Racewear": 0,
                    "Daiwa Scarlet|Recommended Restaurant": 0,
                    "Daiwa Scarlet|Advice from an Older Student": 0,
                    "Daiwa Scarlet|Enjoying Number One": 0,
                    "Daiwa Scarlet|Can't Lose Sight of Number One!": 0,
                    "Daiwa Scarlet|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "El Condor Pasa|Passion-filled Outfit": 1,
                    "El Condor Pasa|A Personalized Mask": 0,
                    "El Condor Pasa|Go for the Extra-Large Pizza!": 0,
                    "El Condor Pasa|Hot and Spicy!": 0,
                    "El Condor Pasa|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "El Condor Pasa|Determination of the World's Strongest": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
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
                "characterEventOverrides": {
                    "Gold Ship|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Gold Ship|The Red of the Protagonist!": 0,
                    "Gold Ship|A Date, Golshi Style": 0,
                    "Gold Ship|A Sudden Episode from Golshi's Past!": 1,
                    "Gold Ship|Pair Discount Repeat Offender": 1,
                    "Gold Ship|Which Did You Lose?": 1,
                    "Gold Ship|My Part-Time Job Is... Crazy?": 0,
                    "Gold Ship|The Day After, Voices Hoarse": 0,
                    "Gold Ship|This One's For Keeps!": 0,
                    "Gold Ship|Killer Appetite!": 0,
                    "Gold Ship|Legend of the Left Pinky": 0,
                    "Gold Ship|Hello From About 1.5 Billion Years Ago": 1,
                    "Gold Ship|A Lovely Place": 0,
                    "Gold Ship|Nighttime Park Visit": 1,
                    "Gold Ship|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Guts"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
                    "Medium",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
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
                "characterEventOverrides": {
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Grass Wonder|A Warrior's Spirit": 0,
                    "Grass Wonder|Whimsical Encounter": 1,
                    "Grass Wonder|Everlasting Game": 0,
                    "Grass Wonder|Errands Have Perks": 0,
                    "Grass Wonder|Beauteaful": 0,
                    "Grass Wonder|Tracen Karuta Queen": 1,
                    "Grass Wonder|Together for Tea": 0,
                    "Grass Wonder|Yamato Nadeshiko": 1,
                    "Grass Wonder|Childhoods Apart": 1,
                    "Grass Wonder|Childhood Dream": 1,
                    "Grass Wonder|Flower Vase": 1,
                    "Grass Wonder|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Grass Wonder|Hidden Meaning": 1,
                    "Grass Wonder|Principles": 0,
                    "Grass Wonder|Hate to Lose": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
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
                "characterEventOverrides": {
                    "Haru Urara|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Haru Urara|Arm-Wrestling Contest": 1,
                    "Haru Urara|Looking for Something Important": 1,
                    "Haru Urara|Sand Training!": 0,
                    "Haru Urara|The Final Boss... Spe!": 0,
                    "Haru Urara|A Little Detour!": 0,
                    "Haru Urara|Parks Are Fun!": 0,
                    "Haru Urara|So Cool!": 0,
                    "Haru Urara|Forgot to Eat!": 1,
                    "Haru Urara|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Haru Urara|The Racewear I Love!": 0,
                    "Haru Urara|Pair Interview!": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
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
                "characterEventOverrides": {
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Hishi Amazon|Another Level": 0,
                    "Hishi Amazon|One-on-One! Gangster! Racewear!": 0,
                    "Hishi Amazon|Friend or Rival?": 0,
                    "Hishi Amazon|Hishiama's Dorm-Leader Breakfast": 0,
                    "Hishi Amazon|Hishiama's Needlework": 0,
                    "Hishi Amazon|Hishiama's Foraging": 0,
                    "Hishi Amazon|The Magic of Sweets?": 0,
                    "Hishi Amazon|Blazing Memories": 0,
                    "Hishi Amazon|Cool and Fiery Sisters": 0,
                    "Hishi Amazon|Hishiama's Special View": 1,
                    "Hishi Amazon|Hishiama and the Arts": 0,
                    "Hishi Amazon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
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
                "characterEventOverrides": {
                    "King Halo|Etsuko's Exhaustive Coverage (G1)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "King Halo|The Outfit That Suits Me Most": 1,
                    "King Halo|Running Isn't Everything": 1,
                    "King Halo|Manners Are Common Sense": 0,
                    "King Halo|Movies Are Full of Learning Opportunities": 0,
                    "King Halo|The King Knows No Exhaustion": 0,
                    "King Halo|First-Rate in Studies Too": 0,
                    "King Halo|After-School Soda": 1,
                    "King Halo|Three Heads Are Better than One": 0,
                    "King Halo|Sweet Tooth Temptation": 0,
                    "King Halo|First-Rate Spot": 1,
                    "King Halo|First-Rate Harvest": 1,
                    "King Halo|Crowds Are No Problem": 1,
                    "King Halo|Breaking Curfew is Second-Rate": 1,
                    "King Halo|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 550,
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
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
                "characterEventOverrides": {
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Matikanefukukitaru|I'll Protect You!": 0,
                    "Matikanefukukitaru|Now or Never! Sacred Sites": 0,
                    "Matikanefukukitaru|When Fukukitaru Comes, Fortune Follows": 0,
                    "Matikanefukukitaru|Cursed Camera": 1,
                    "Matikanefukukitaru|Manhattan's Dream": 1,
                    "Matikanefukukitaru|Pretty Gunslingers": 0,
                    "Matikanefukukitaru|Seven Gods of Fortune Fine Food Tour": 0,
                    "Matikanefukukitaru|Punch in a Pinch": 0,
                    "Matikanefukukitaru|Taking the Plunge": 0,
                    "Matikanefukukitaru|Shrine Visit": 1,
                    "Matikanefukukitaru|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Matikanefukukitaru|Room of the Chosen Ones": 1,
                    "Matikanefukukitaru|Better Fortune! Lucky Telephone": 0,
                    "Matikanefukukitaru|Under the Meteor Shower": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
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
                "characterEventOverrides": {
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Mayano Top Gun|You're My Sunshine ☆": 0,
                    "Mayano Top Gun|Meant to Be ♪": 0,
                    "Mayano Top Gun|With My Whole Heart!": 0,
                    "Mayano Top Gun|Maya Will Teach You ☆": 0,
                    "Mayano Top Gun|Tips from a Top Model!": 0,
                    "Mayano Top Gun|Maya's Race Class ☆": 0,
                    "Mayano Top Gun|Hearty Chanko! ☆": 0,
                    "Mayano Top Gun|Maya's Exciting ☆ Livestream!": 0,
                    "Mayano Top Gun|Maya's Euphoric ☆ Livestream!": 1,
                    "Mayano Top Gun|Maya's Special Someone!": 0,
                    "Mayano Top Gun|Wish on a Star": 1,
                    "Mayano Top Gun|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Mayano Top Gun|Maya's Thrilling ☆ Test of Courage": 0,
                    "Mayano Top Gun|Sweet Feelings for You ♪": 0,
                    "Mayano Top Gun|Mayano Takes Off ☆": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
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
                "characterEventOverrides": {
                    "Nice Nature|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Nice Nature|Nature and Her Tired Trainer": 0,
                    "Nice Nature|Bittersweet Sparkle": 0,
                    "Nice Nature|Festive Colors": 1,
                    "Nice Nature|Rainy-Day Fun": 0,
                    "Nice Nature|Not My Style": 1,
                    "Nice Nature|Whirlwind Advice": 1,
                    "Nice Nature|A Little Can't Hurt": 0,
                    "Nice Nature|A Phone Call from Mom": 0,
                    "Nice Nature|Once in a While": 1,
                    "Nice Nature|Snapshot of Emotions": 1,
                    "Nice Nature|Let's Watch the Fish": 1,
                    "Nice Nature|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
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
                "characterEventOverrides": {
                    "Sakura Bakushin O|Bakushin for Love!": 0,
                    "Sakura Bakushin O|A Day Without a Class Rep": 0,
                    "Sakura Bakushin O|Bakushin in Signature Racewear!": 1,
                    "Sakura Bakushin O|The Bakushin Book!": 0,
                    "Sakura Bakushin O|The Voices of the Students": 1,
                    "Sakura Bakushin O|Solving Riddles, Bakushin Style!": 0,
                    "Sakura Bakushin O|Bakushin?! Class?!": 0,
                    "Sakura Bakushin O|Bakushin-ing with a Classmate!": 1,
                    "Sakura Bakushin O|The Best Bakushin!": 0,
                    "Sakura Bakushin O|Bakushin, Now and Forever!": 0,
                    "Sakura Bakushin O|Together with Someone Important!": 1,
                    "Sakura Bakushin O|The Speed King": 0,
                    "Sakura Bakushin O|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
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
                "characterEventOverrides": {
                    "Taiki Shuttle|Quick Draw Showdown": 0,
                    "Taiki Shuttle|Must-Win Match": 0,
                    "Taiki Shuttle|To the Top!": 1,
                    "Taiki Shuttle|Hide-and-Seek": 0,
                    "Taiki Shuttle|Embracing Guidance": 0,
                    "Taiki Shuttle|Harvest Festival": 0,
                    "Taiki Shuttle|Meaty Heaven": 0,
                    "Taiki Shuttle|Rainy Power": 1,
                    "Taiki Shuttle|Rainy Choice": 0,
                    "Taiki Shuttle|Rainy Rescue": 0,
                    "Taiki Shuttle|Let's Patrol!": 0,
                    "Taiki Shuttle|Going Home Together": 0,
                    "Taiki Shuttle|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
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
                "characterEventOverrides": {
                    "Vodka|Vintage Style": 0,
                    "Vodka|Makings of a Friend": 0,
                    "Vodka|Hot and Cool": 0,
                    "Vodka|Like a Kid": 0,
                    "Vodka|Challenging Fate": 1,
                    "Vodka|Showdown by the River!": 0,
                    "Vodka|The Ultimate Choice": 0,
                    "Vodka|Awkward Honesty": 0,
                    "Vodka|The Standards of Coolness": 0,
                    "Vodka|Ring Out, Passionate Sound!": 0,
                    "Vodka|The Way of Cool": 1,
                    "Vodka|Let's Take a Little Detour": 0,
                    "Vodka|Sugar and Spice": 1,
                    "Vodka|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100321,200581,201321,201311,201351,201111,201101,200372,200331,200351,200021,200352,200382,201161"
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
                "characterEventOverrides": {
                    "Agnes Tachyon|Expression of Conviction": 0,
                    "Agnes Tachyon|Tachyon the Spoiled Child": 1,
                    "Agnes Tachyon|At Tachyon's Pace": 1,
                    "Agnes Tachyon|The Strongest Collaborator?!": 0,
                    "Agnes Tachyon|Hamburger Helper!": 0,
                    "Agnes Tachyon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trainingMediumStatTarget_speedStatTarget": 1100,
                "trainingMediumStatTarget_staminaStatTarget": 800,
                "trainingMediumStatTarget_powerStatTarget": 800,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201351,201111,201101,201161,200331,200351,200021,200352,200382,200571"
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
                "characterEventOverrides": {
                    "Air Groove|The Empress and Mom": 0,
                    "Air Groove|Seize Her!": 1,
                    "Air Groove|Take Good Care of Your Tail": 1,
                    "Air Groove|A Taste of Effort": 0,
                    "Air Groove|Imprinted Memories": 0,
                    "Air Groove|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Air Groove|Flowers for You": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,200541,200551,200531,201251,201241,201041,201031,200681,200331,200021,201281,200352,200382,200552"
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
                "characterEventOverrides": {
                    "Daiwa Scarlet|The Weight of Racewear": 0,
                    "Daiwa Scarlet|Recommended Restaurant": 0,
                    "Daiwa Scarlet|Advice from an Older Student": 0,
                    "Daiwa Scarlet|Enjoying Number One": 0,
                    "Daiwa Scarlet|Can't Lose Sight of Number One!": 0,
                    "Daiwa Scarlet|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201321,201311,201041,201031,201351,200681,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "El Condor Pasa|Passion-filled Outfit": 1,
                    "El Condor Pasa|A Personalized Mask": 0,
                    "El Condor Pasa|Go for the Extra-Large Pizza!": 0,
                    "El Condor Pasa|Hot and Spicy!": 0,
                    "El Condor Pasa|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "El Condor Pasa|Determination of the World's Strongest": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "100071,201691,201173,201171,201181,200351,200331,200511,201692,201211,201221,201202,201201,200512,200352"
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
                "characterEventOverrides": {
                    "Gold Ship|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Gold Ship|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Gold Ship|The Red of the Protagonist!": 0,
                    "Gold Ship|A Date, Golshi Style": 0,
                    "Gold Ship|A Sudden Episode from Golshi's Past!": 1,
                    "Gold Ship|Pair Discount Repeat Offender": 1,
                    "Gold Ship|Which Did You Lose?": 1,
                    "Gold Ship|My Part-Time Job Is... Crazy?": 0,
                    "Gold Ship|The Day After, Voices Hoarse": 0,
                    "Gold Ship|This One's For Keeps!": 0,
                    "Gold Ship|Killer Appetite!": 0,
                    "Gold Ship|Legend of the Left Pinky": 0,
                    "Gold Ship|Hello From About 1.5 Billion Years Ago": 1,
                    "Gold Ship|A Lovely Place": 0,
                    "Gold Ship|Nighttime Park Visit": 1,
                    "Gold Ship|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Guts"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
                    "Medium",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,201171,201181,201381,201391,201161,200331,200512,201692,201442"
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
                "characterEventOverrides": {
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Grass Wonder|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Grass Wonder|A Warrior's Spirit": 0,
                    "Grass Wonder|Whimsical Encounter": 1,
                    "Grass Wonder|Everlasting Game": 0,
                    "Grass Wonder|Errands Have Perks": 0,
                    "Grass Wonder|Beauteaful": 0,
                    "Grass Wonder|Tracen Karuta Queen": 1,
                    "Grass Wonder|Together for Tea": 0,
                    "Grass Wonder|Yamato Nadeshiko": 1,
                    "Grass Wonder|Childhoods Apart": 1,
                    "Grass Wonder|Childhood Dream": 1,
                    "Grass Wonder|Flower Vase": 1,
                    "Grass Wonder|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Grass Wonder|Hidden Meaning": 1,
                    "Grass Wonder|Principles": 0,
                    "Grass Wonder|Hate to Lose": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "dirt",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201681,201671,201011,202041,200651,200961,200971,201381,201391,200991,200671,201001,202002,201682,200992"
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
                "characterEventOverrides": {
                    "Haru Urara|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Haru Urara|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Haru Urara|Arm-Wrestling Contest": 1,
                    "Haru Urara|Looking for Something Important": 1,
                    "Haru Urara|Sand Training!": 0,
                    "Haru Urara|The Final Boss... Spe!": 0,
                    "Haru Urara|A Little Detour!": 0,
                    "Haru Urara|Parks Are Fun!": 0,
                    "Haru Urara|So Cool!": 0,
                    "Haru Urara|Forgot to Eat!": 1,
                    "Haru Urara|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1,
                    "Haru Urara|The Racewear I Love!": 0,
                    "Haru Urara|Pair Interview!": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Medium",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "end_closer",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200641,200631,202021,200621,201481,201451,201461,201101,201111,200331,201161,200512,200642,201651,200352"
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
                "characterEventOverrides": {
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Hishi Amazon|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Hishi Amazon|Another Level": 0,
                    "Hishi Amazon|One-on-One! Gangster! Racewear!": 0,
                    "Hishi Amazon|Friend or Rival?": 0,
                    "Hishi Amazon|Hishiama's Dorm-Leader Breakfast": 0,
                    "Hishi Amazon|Hishiama's Needlework": 0,
                    "Hishi Amazon|Hishiama's Foraging": 0,
                    "Hishi Amazon|The Magic of Sweets?": 0,
                    "Hishi Amazon|Blazing Memories": 0,
                    "Hishi Amazon|Cool and Fiery Sisters": 0,
                    "Hishi Amazon|Hishiama's Special View": 1,
                    "Hishi Amazon|Hishiama and the Arts": 0,
                    "Hishi Amazon|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200601,200611,201691,201441,200961,200971,201381,201391,201051,200701,200331,200512,201692"
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
                "characterEventOverrides": {
                    "King Halo|Etsuko's Exhaustive Coverage (G1)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "King Halo|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "King Halo|The Outfit That Suits Me Most": 1,
                    "King Halo|Running Isn't Everything": 1,
                    "King Halo|Manners Are Common Sense": 0,
                    "King Halo|Movies Are Full of Learning Opportunities": 0,
                    "King Halo|The King Knows No Exhaustion": 0,
                    "King Halo|First-Rate in Studies Too": 0,
                    "King Halo|After-School Soda": 1,
                    "King Halo|Three Heads Are Better than One": 0,
                    "King Halo|Sweet Tooth Temptation": 0,
                    "King Halo|First-Rate Spot": 1,
                    "King Halo|First-Rate Harvest": 1,
                    "King Halo|Crowds Are No Problem": 1,
                    "King Halo|Breaking Curfew is Second-Rate": 1,
                    "King Halo|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 1
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trainingSprintStatTarget_gutsStatTarget": 400,
                "trainingSprintStatTarget_witStatTarget": 600,
                "trainingMileStatTarget_speedStatTarget": 1000,
                "trainingMileStatTarget_staminaStatTarget": 550,
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200351,201691,200691,200601,201391,201381,201181,201171,201201,201541,200741,200431"
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
                "characterEventOverrides": {
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Matikanefukukitaru|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Matikanefukukitaru|I'll Protect You!": 0,
                    "Matikanefukukitaru|Now or Never! Sacred Sites": 0,
                    "Matikanefukukitaru|When Fukukitaru Comes, Fortune Follows": 0,
                    "Matikanefukukitaru|Cursed Camera": 1,
                    "Matikanefukukitaru|Manhattan's Dream": 1,
                    "Matikanefukukitaru|Pretty Gunslingers": 0,
                    "Matikanefukukitaru|Seven Gods of Fortune Fine Food Tour": 0,
                    "Matikanefukukitaru|Punch in a Pinch": 0,
                    "Matikanefukukitaru|Taking the Plunge": 0,
                    "Matikanefukukitaru|Shrine Visit": 1,
                    "Matikanefukukitaru|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Matikanefukukitaru|Room of the Chosen Ones": 1,
                    "Matikanefukukitaru|Better Fortune! Lucky Telephone": 0,
                    "Matikanefukukitaru|Under the Meteor Shower": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                    "Long",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "long",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200331,200351,200571,201351,200581,201321,201311,201181,201171,201531,201201,200371,200431,200561"
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
                "characterEventOverrides": {
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Mayano Top Gun|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Mayano Top Gun|You're My Sunshine ☆": 0,
                    "Mayano Top Gun|Meant to Be ♪": 0,
                    "Mayano Top Gun|With My Whole Heart!": 0,
                    "Mayano Top Gun|Maya Will Teach You ☆": 0,
                    "Mayano Top Gun|Tips from a Top Model!": 0,
                    "Mayano Top Gun|Maya's Race Class ☆": 0,
                    "Mayano Top Gun|Hearty Chanko! ☆": 0,
                    "Mayano Top Gun|Maya's Exciting ☆ Livestream!": 0,
                    "Mayano Top Gun|Maya's Euphoric ☆ Livestream!": 1,
                    "Mayano Top Gun|Maya's Special Someone!": 0,
                    "Mayano Top Gun|Wish on a Star": 1,
                    "Mayano Top Gun|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Mayano Top Gun|Maya's Thrilling ☆ Test of Courage": 0,
                    "Mayano Top Gun|Sweet Feelings for You ♪": 0,
                    "Mayano Top Gun|Mayano Takes Off ☆": 1
                },
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
                "preferredDistanceOverride": "Long",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200591,200611,200331,200601,201691,201391,201381,201111,201101,201541,201692,200351,200431"
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
                "characterEventOverrides": {
                    "Nice Nature|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Nice Nature|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Nice Nature|Nature and Her Tired Trainer": 0,
                    "Nice Nature|Bittersweet Sparkle": 0,
                    "Nice Nature|Festive Colors": 1,
                    "Nice Nature|Rainy-Day Fun": 0,
                    "Nice Nature|Not My Style": 1,
                    "Nice Nature|Whirlwind Advice": 1,
                    "Nice Nature|A Little Can't Hurt": 0,
                    "Nice Nature|A Phone Call from Mom": 0,
                    "Nice Nature|Once in a While": 1,
                    "Nice Nature|Snapshot of Emotions": 1,
                    "Nice Nature|Let's Watch the Fish": 1,
                    "Nice Nature|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual",
                    "Wit Scroll",
                    "Wit Manual"
                ],
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "sprint",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201011,200651,200541,200331,200351,200552,200961,200971,201241,201251,201282,200431,200371,200372"
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
                "characterEventOverrides": {
                    "Sakura Bakushin O|Bakushin for Love!": 0,
                    "Sakura Bakushin O|A Day Without a Class Rep": 0,
                    "Sakura Bakushin O|Bakushin in Signature Racewear!": 1,
                    "Sakura Bakushin O|The Bakushin Book!": 0,
                    "Sakura Bakushin O|The Voices of the Students": 1,
                    "Sakura Bakushin O|Solving Riddles, Bakushin Style!": 0,
                    "Sakura Bakushin O|Bakushin?! Class?!": 0,
                    "Sakura Bakushin O|Bakushin-ing with a Classmate!": 1,
                    "Sakura Bakushin O|The Best Bakushin!": 0,
                    "Sakura Bakushin O|Bakushin, Now and Forever!": 0,
                    "Sakura Bakushin O|Together with Someone Important!": 1,
                    "Sakura Bakushin O|The Speed King": 0,
                    "Sakura Bakushin O|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Sprint",
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "pace_chaser",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200581,200681,201051,200341,200331,200431,200582,201031,201041,201311,201321,201902,200351,200512"
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
                "characterEventOverrides": {
                    "Taiki Shuttle|Quick Draw Showdown": 0,
                    "Taiki Shuttle|Must-Win Match": 0,
                    "Taiki Shuttle|To the Top!": 1,
                    "Taiki Shuttle|Hide-and-Seek": 0,
                    "Taiki Shuttle|Embracing Guidance": 0,
                    "Taiki Shuttle|Harvest Festival": 0,
                    "Taiki Shuttle|Meaty Heaven": 0,
                    "Taiki Shuttle|Rainy Power": 1,
                    "Taiki Shuttle|Rainy Choice": 0,
                    "Taiki Shuttle|Rainy Rescue": 0,
                    "Taiki Shuttle|Let's Patrol!": 0,
                    "Taiki Shuttle|Going Home Together": 0,
                    "Taiki Shuttle|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Stamina Scroll",
                    "Stamina Manual",
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Medium",
                    "Mile",
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "medium",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "201441,200601,200591,200611,201691,200331,200681,201111,201101,201391,201381,200351,200602,200512"
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
                "characterEventOverrides": {
                    "Vodka|Vintage Style": 0,
                    "Vodka|Makings of a Friend": 0,
                    "Vodka|Hot and Cool": 0,
                    "Vodka|Like a Kid": 0,
                    "Vodka|Challenging Fate": 1,
                    "Vodka|Showdown by the River!": 0,
                    "Vodka|The Ultimate Choice": 0,
                    "Vodka|Awkward Honesty": 0,
                    "Vodka|The Standards of Coolness": 0,
                    "Vodka|Ring Out, Passionate Sound!": 0,
                    "Vodka|The Way of Cool": 1,
                    "Vodka|Let's Take a Little Detour": 0,
                    "Vodka|Sugar and Spice": 1,
                    "Vodka|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0
                },
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
                    "Power",
                    "Stamina"
                ],
                "enableRainbowTrainingBonus": false,
                "preferredDistanceOverride": "Medium",
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
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
                "characterEventOverrides": {
                    "Maruzensky|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Maruzensky|The Maruzen I Admire": 0,
                    "Maruzensky|Welcome to Bubblyland": 1,
                    "Maruzensky|Maruzensky's Treasure": 0,
                    "Maruzensky|Hot Rod": 0,
                    "Maruzensky|Let's Play ♪": 0,
                    "Maruzensky|A Lady's Style ☆": 0,
                    "Maruzensky|Let's Cook!": 0,
                    "Maruzensky|The Road to a Rad Victory!": 0,
                    "Maruzensky|Down to Dance!": 0,
                    "Maruzensky|Nostalgia Fever ☆": 0,
                    "Maruzensky|The Secret to Supporting Each Other": 0,
                    "Maruzensky|Even Role Models Get Lonely": 1,
                    "Maruzensky|Meeting New People Is Trendy ☆": 1,
                    "Maruzensky|The Fun Never Stops ♪": 0,
                    "Maruzensky|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Maruzensky|Trendsetter": 0,
                    "Maruzensky|Sewing Star": 1,
                    "Maruzensky|My Favorite Things": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
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
                "characterEventOverrides": {
                    "Maruzensky|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Maruzensky|The Maruzen I Admire": 0,
                    "Maruzensky|Welcome to Bubblyland": 1,
                    "Maruzensky|Maruzensky's Treasure": 0,
                    "Maruzensky|Hot Rod": 0,
                    "Maruzensky|Let's Play ♪": 0,
                    "Maruzensky|A Lady's Style ☆": 0,
                    "Maruzensky|Let's Cook!": 0,
                    "Maruzensky|The Road to a Rad Victory!": 0,
                    "Maruzensky|Down to Dance!": 0,
                    "Maruzensky|Nostalgia Fever ☆": 0,
                    "Maruzensky|The Secret to Supporting Each Other": 0,
                    "Maruzensky|Even Role Models Get Lonely": 1,
                    "Maruzensky|Meeting New People Is Trendy ☆": 1,
                    "Maruzensky|The Fun Never Stops ♪": 0,
                    "Maruzensky|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Maruzensky|Trendsetter": 0,
                    "Maruzensky|Sewing Star": 1,
                    "Maruzensky|My Favorite Things": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trainingMileStatTarget_witStatTarget": 550,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "front_runner",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200541,200331,200371,200551,200681,201041,201031,201251,201241,201051,201281,201521,200431,200531"
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
                "characterEventOverrides": {
                    "Maruzensky|Etsuko's Exhaustive Coverage (G1)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (G2/G3)": 1,
                    "Maruzensky|Etsuko's Exhaustive Coverage (Pre/OP)": 1,
                    "Maruzensky|The Maruzen I Admire": 0,
                    "Maruzensky|Welcome to Bubblyland": 1,
                    "Maruzensky|Maruzensky's Treasure": 0,
                    "Maruzensky|Hot Rod": 0,
                    "Maruzensky|Let's Play ♪": 0,
                    "Maruzensky|A Lady's Style ☆": 0,
                    "Maruzensky|Let's Cook!": 0,
                    "Maruzensky|The Road to a Rad Victory!": 0,
                    "Maruzensky|Down to Dance!": 0,
                    "Maruzensky|Nostalgia Fever ☆": 0,
                    "Maruzensky|The Secret to Supporting Each Other": 0,
                    "Maruzensky|Even Role Models Get Lonely": 1,
                    "Maruzensky|Meeting New People Is Trendy ☆": 1,
                    "Maruzensky|The Fun Never Stops ♪": 0,
                    "Maruzensky|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 0,
                    "Maruzensky|Trendsetter": 0,
                    "Maruzensky|Sewing Star": 1,
                    "Maruzensky|My Favorite Things": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trainingMileStatTarget_speedStatTarget": 1200,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 350,
                "trainingMileStatTarget_witStatTarget": 550,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "Gold City|A City Girl's Mood ♪": 0,
                    "Gold City|A Quiet Talk Before the Show": 0,
                    "Gold City|A Delicious Trap?": 0,
                    "Gold City|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Gold City|Client's Orders": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trainingMileStatTarget_witStatTarget": 550,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "Gold City|A City Girl's Mood ♪": 0,
                    "Gold City|A Quiet Talk Before the Show": 0,
                    "Gold City|A Delicious Trap?": 0,
                    "Gold City|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Gold City|Client's Orders": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trainingMileStatTarget_witStatTarget": 550,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
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
                    "Short",
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
                "enableSkillPointCheck": true,
                "skillPointCheck": 1200,
                "preferredRunningStyle": "late_surger",
                "preferredTrackDistance": "mile",
                "preferredTrackSurface": "turf",
                "plans": {
                    "skillPointCheck": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "preFinals": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
                    },
                    "careerComplete": {
                        "enabled": true,
                        "strategy": "optimize_skills",
                        "enableBuyInheritedUniqueSkills": true,
                        "enableBuyNegativeSkills": false,
                        "plan": "200431,100711,200581,201391,201381,201041,201031,200681,201351,200331,200351,200021,200352,200382,201051"
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
                "characterEventOverrides": {
                    "Gold City|A City Girl's Mood ♪": 0,
                    "Gold City|A Quiet Talk Before the Show": 0,
                    "Gold City|A Delicious Trap?": 0,
                    "Gold City|Acupuncture (Just an Acupuncturist, No Worries! ☆)": 2,
                    "Gold City|Client's Orders": 0
                },
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
                    "Power",
                    "Stamina"
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
                "trainingMileStatTarget_speedStatTarget": 1100,
                "trainingMileStatTarget_staminaStatTarget": 600,
                "trainingMileStatTarget_powerStatTarget": 800,
                "trainingMileStatTarget_gutsStatTarget": 350,
                "trainingMileStatTarget_witStatTarget": 550,
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
                "trackblazerExcludedItems": [
                    "Guts Scroll",
                    "Guts Manual"
                ],
                "trackblazerShopCheckFrequency": 3
            }
        }
    }
]
