// Local Master-Data Compiler v1 - canonical artifact text and its digest.
//
// The compiler writes every artifact through canonicalJson and records the SHA256 of those exact bytes in
// the manifest; the reader re-derives both to verify what it loaded. They live together so the writer and
// the verifier cannot drift apart, and both are dependency-free so the reader stays importable from the
// app bundle (no node:crypto).

const EM_DASH = String.fromCharCode(0x2014)
const EM_DASH_ESCAPE = "\\u2014"

/**
 * Deterministic JSON: fixed 2-space indent, trailing newline, key order taken from construction order.
 *
 * The U+2014 (em dash) code point is re-emitted as the six-character JSON escape. This is purely textual:
 * JSON.stringify only ever places that code point inside a string value (every structural token is ASCII),
 * and the escape decodes back to the identical code point, so JSON.parse yields exactly the same string.
 * This repository avoids the literal U+2014 character in newly generated tracked files, but skill/race
 * source text legitimately contains em dashes (game skill names), and a compiled artifact is itself a new
 * tracked file, so escaping keeps the canonical value intact while producing a committable artifact.
 * Applies to every artifact this serializer emits, so future em-dash source is handled too.
 */
export function canonicalJson(value: unknown): string {
    return JSON.stringify(value, null, 2).split(EM_DASH).join(EM_DASH_ESCAPE) + "\n"
}

/**
 * UTF-8 bytes of a string. Hand-rolled rather than using TextEncoder because the reader runs on Hermes as
 * well as Node. An unpaired surrogate becomes U+FFFD, matching what Node's encoders do.
 */
function utf8Bytes(text: string): Uint8Array {
    const out = new Uint8Array(text.length * 3)
    let n = 0
    for (let i = 0; i < text.length; i++) {
        let cp = text.charCodeAt(i)
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < text.length) {
            const low = text.charCodeAt(i + 1)
            if (low >= 0xdc00 && low <= 0xdfff) {
                cp = 0x10000 + ((cp - 0xd800) << 10) + (low - 0xdc00)
                i++
            }
        }
        if (cp >= 0xd800 && cp <= 0xdfff) cp = 0xfffd
        if (cp < 0x80) {
            out[n++] = cp
        } else if (cp < 0x800) {
            out[n++] = 0xc0 | (cp >> 6)
            out[n++] = 0x80 | (cp & 0x3f)
        } else if (cp < 0x10000) {
            out[n++] = 0xe0 | (cp >> 12)
            out[n++] = 0x80 | ((cp >> 6) & 0x3f)
            out[n++] = 0x80 | (cp & 0x3f)
        } else {
            out[n++] = 0xf0 | (cp >> 18)
            out[n++] = 0x80 | ((cp >> 12) & 0x3f)
            out[n++] = 0x80 | ((cp >> 6) & 0x3f)
            out[n++] = 0x80 | (cp & 0x3f)
        }
    }
    return out.subarray(0, n)
}

const ROUND_CONSTANTS = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
])

function rotr(x: number, n: number): number {
    return ((x >>> n) | (x << (32 - n))) >>> 0
}

/** Lowercase hex SHA256 of a string's UTF-8 bytes. Matches `createHash("sha256").update(text, "utf8")`. */
export function sha256Hex(text: string): string {
    const bytes = utf8Bytes(text)
    const total = Math.ceil((bytes.length + 9) / 64) * 64
    const padded = new Uint8Array(total)
    padded.set(bytes)
    padded[bytes.length] = 0x80
    const view = new DataView(padded.buffer)
    const bitLength = bytes.length * 8
    view.setUint32(total - 8, Math.floor(bitLength / 0x100000000))
    view.setUint32(total - 4, bitLength >>> 0)

    const h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19])
    const w = new Uint32Array(64)
    for (let offset = 0; offset < total; offset += 64) {
        for (let i = 0; i < 16; i++) w[i] = view.getUint32(offset + i * 4)
        for (let i = 16; i < 64; i++) {
            const x = w[i - 15]
            const y = w[i - 2]
            const s0 = (rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3)) >>> 0
            const s1 = (rotr(y, 17) ^ rotr(y, 19) ^ (y >>> 10)) >>> 0
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        let a = h[0]
        let b = h[1]
        let c = h[2]
        let d = h[3]
        let e = h[4]
        let f = h[5]
        let g = h[6]
        let acc = h[7]
        for (let i = 0; i < 64; i++) {
            const s1 = (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) >>> 0
            const ch = ((e & f) ^ (~e & g)) >>> 0
            const t1 = (acc + s1 + ch + ROUND_CONSTANTS[i] + w[i]) >>> 0
            const s0 = (rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) >>> 0
            const maj = ((a & b) ^ (a & c) ^ (b & c)) >>> 0
            const t2 = (s0 + maj) >>> 0
            acc = g
            g = f
            f = e
            e = (d + t1) >>> 0
            d = c
            c = b
            b = a
            a = (t1 + t2) >>> 0
        }
        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += acc
    }

    let hex = ""
    for (let i = 0; i < 8; i++) hex += h[i].toString(16).padStart(8, "0")
    return hex
}
