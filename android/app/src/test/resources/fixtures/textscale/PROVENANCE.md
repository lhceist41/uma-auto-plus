# Text scale fixtures

Crops only; no trainer names, IDs, club or account data.

- `menu_240.png`, `menu_480.png`: a 140x70 crop (x 920-1060, y 70-140) of the Home screen's top-right "Menu" button on the same MuMu 1080x1920 instance, at 240 DPI and at 480 DPI. `components/label/home_menu` is cut from the 240 frame (x 942-1029, y 90-120, the word only). At 480 DPI the word scores 0.833 at 1.0 and 0.962 at 0.98 (OpenCV).
- `auto_select_480.png`: Legacy Select's Auto-Select button at 480 DPI (x 568-1008, y 1411-1519). It scores 0.745 at 1.0 and 0.879 at 0.98.
- `details_480.png`: Scenario Select's Details button at 480 DPI (x 877-1063, y 237-335). It scores 0.790 at 1.0 and 0.933 at 0.98.
- `absent/m240_<template>.png`, `absent/p2316_<template>.png`: for each template the text-scale ladder may retry, the window where it scored highest at 0.98 to 0.90 while missing at 1.0, from a full-frame search of 42 MuMu 1080x1920 240 DPI frames (`m240`) and 36 1080x2316 phone frames at 450 DPI (`p2316`), padded by 24 px. The highest is 0.789 (`home_fans_info` on a shop row), so none reaches the ladder's 0.85 bar. `home_menu` windows are included for the Start measurement.
