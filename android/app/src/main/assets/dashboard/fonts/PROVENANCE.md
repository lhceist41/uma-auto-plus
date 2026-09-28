# Font provenance

All three families are OFL-1.1, from the `@fontsource` npm packages named below. Only the latin
and latin-ext, non-italic woff2 files of the weights the page uses are included; `dashboard.css`
writes its own `@font-face` rules.

## Barlow Condensed (600, 700)

- Package: `@fontsource/barlow-condensed@5.3.0`
- Files (from the package's `files/` directory):
  - `barlow-condensed-latin-600-normal.woff2`
  - `barlow-condensed-latin-700-normal.woff2`
  - `barlow-condensed-latin-ext-600-normal.woff2`
  - `barlow-condensed-latin-ext-700-normal.woff2`
- License: OFL-1.1, the package's `LICENSE`, copied as `barlow-condensed/OFL.txt`

SHA-256 (of the copies under `fonts/barlow-condensed/`):

```
215a93c696f442034a46fbb382958f753fda60e30490683aeea6b235fcbb2b66  barlow-condensed-latin-600-normal.woff2
3787a5a419171630e6890cfa47c4da067474d005cd0ff8dc11ec090fdc3ee2b8  barlow-condensed-latin-700-normal.woff2
aef7edf54f79a5329fbca9c065b56dcec3852c680d58cf89a9ef2db6940ca4b3  barlow-condensed-latin-ext-600-normal.woff2
9d351bd9222bfab90f943850f8039aa73cdb40e0a6cfe4127308a48bef59d5f5  barlow-condensed-latin-ext-700-normal.woff2
046ee27d8bddc6b6ee08eee53c96dbf3ec1f6df561644e018507dd8ffcb11d31  OFL.txt
```

## Plus Jakarta Sans (variable)

- Package: `@fontsource-variable/plus-jakarta-sans@5.3.0`
- Files:
  - `plus-jakarta-sans-latin-wght-normal.woff2`
  - `plus-jakarta-sans-latin-ext-wght-normal.woff2`
- License: OFL-1.1, the package's `LICENSE`, copied as `plus-jakarta-sans/OFL.txt`
- Variable axis: `wght` 200-800. Loaded with `font-weight: 200 800` in the `@font-face`
  rule so the page can use 400/500/600/700 from the one file.

SHA-256 (of the copies under `fonts/plus-jakarta-sans/`):

```
153fc85b70298beeb1d61a5f723331649e7f23bb77302a66e61cb3e2fbdb5e79  plus-jakarta-sans-latin-wght-normal.woff2
38e3b8fd8045048eb311d90170a4429ed2c8f405852dc3d91b5af8452758703f  plus-jakarta-sans-latin-ext-wght-normal.woff2
e07fd1167c2aaaa6fb965dd66e1e73b13c9c95b76b2d648b6e938780f30b37af  OFL.txt
```

## JetBrains Mono (variable)

- Package: `@fontsource-variable/jetbrains-mono@5.3.0`
- Files:
  - `jetbrains-mono-latin-wght-normal.woff2`
  - `jetbrains-mono-latin-ext-wght-normal.woff2`
- License: OFL-1.1, the package's `LICENSE`, copied as `jetbrains-mono/OFL.txt`
- Variable axis: `wght` 100-800. Loaded with `font-weight: 100 800`.

SHA-256 (of the copies under `fonts/jetbrains-mono/`):

```
18be452724bfdc236c074ca94a249a7f41a86752c7d04ab258ce9ed5651f6a7e  jetbrains-mono-latin-wght-normal.woff2
79bfdab9ba467e26eea4122e6f2567e188dd8a09a8c730d501fc487c4ab99c6e  jetbrains-mono-latin-ext-wght-normal.woff2
403581b69dac5cff4079205e01c6b467e56af449ecbd7247693ddb1baafa005b  OFL.txt
```
