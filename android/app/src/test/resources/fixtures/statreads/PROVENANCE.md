# Details dialog stat-read provenance

`details_dialog_reads.csv` holds every Umamusume Details dialog stat read (`determineStatValues` with `isAptitudeDialog`) found in the debug-level logcats of live emulator and phone careers between 2026-09-29 and 2026-10-08: 440 reads whose true value the bot held right after the read.

| Column | Meaning |
|---|---|
| `stat` | the stat read |
| `raw` | the OCR text the read returned (`Raw OCR text for <stat>: '<raw>'`) |
| `held` | the value the bot held at its next stat log line: the read itself when accepted, the kept value when the floor rejected the read |

All 34 reads where `raw` differs from `held` returned exactly `1`, and every one of them held a value starting with 1 (152 such reads in total). No value starting with another digit failed (288 reads). The file holds numbers only: no trainer name, ID or other identifying data.
