# Trainee-in-deck fixture provenance

Full-resolution (1080x1920) captures that pin the pixel probe in `utils/TraineeInDeckProbe.kt`.
Every other fixture PNG is a negative.

| Fixture | Shows |
|---|---|
| trainee_in_deck_slot2.png | The career-start Support Formation on Deck 7 with Agnes Tachyon as the trainee: slot 2 holds an Agnes Tachyon card and carries the red "! Trainee" tag; Start Career is greyed out. |
| trainee_in_deck_slot4.png | The same screen with Mihono Bourbon as the trainee: slot 4 carries the tag. |
| trainee_in_deck_before_borrow.png | The slot 2 case before the Friends card was borrowed (empty Friends slot): the tag is already shown. |
| support_formation_valid.png | The same Deck 7, including the same Agnes Tachyon card, with another trainee: no tag, Start Career enabled. |
| borrow_picker_duplicate_support.png | The Borrow Card picker with red "Duplicate Support" tags on its rows. |

## Source and colour

`adb screencap` captures from MuMu on 2026-09-28 (true RGB), re-encoded losslessly as RGBA with
identical pixels. The failure they document: with a required deck holding a card of the trainee's
own character, the bot pressed the disabled Start Career five times and then stopped with a reason
about an empty deck slot.
