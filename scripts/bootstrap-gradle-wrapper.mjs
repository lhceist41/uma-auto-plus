#!/usr/bin/env node
/**
 * Fetch and verify the Gradle wrapper jar.
 *
 * The jar is deliberately not tracked (a third-party binary), but Gradle cannot start without it
 * and fails with a bare "Unable to access jarfile" that does not explain itself. This script gives
 * a fresh clone a working, verified wrapper with one command:
 *
 *   yarn bootstrap:android
 *
 * It reads the Gradle version pinned in gradle-wrapper.properties, downloads the matching wrapper
 * jar from the Gradle repository, and refuses to install a file whose SHA-256 does not match the
 * pin below. The build workflows run the same script, so CI and a local clone bootstrap the same
 * way.
 *
 * When upgrading Gradle: bump distributionUrl in gradle-wrapper.properties, download the new
 * version's jar, hash it, and record the value in EXPECTED_SHA256 in the same change, so the
 * wrapper and the distribution can never drift apart.
 */

import { createHash } from "node:crypto"
import { existsSync, readFileSync, writeFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const PROPERTIES = path.join(ROOT, "android", "gradle", "wrapper", "gradle-wrapper.properties")
const JAR = path.join(ROOT, "android", "gradle", "wrapper", "gradle-wrapper.jar")

/** SHA-256 of gradle/wrapper/gradle-wrapper.jar at the matching tag of github.com/gradle/gradle. */
const EXPECTED_SHA256 = {
    "8.14.3": "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172",
}

// Thrown instead of calling process.exit: exiting while the fetch's sockets are still open trips
// a libuv handle assert on Windows node, so the caller sets process.exitCode and returns instead.
class BootstrapError extends Error {}

function fail(message) {
    throw new BootstrapError(message)
}

function sha256(buffer) {
    return createHash("sha256").update(buffer).digest("hex")
}

function pinnedVersion() {
    let properties
    try {
        properties = readFileSync(PROPERTIES, "utf8")
    } catch {
        fail(`cannot read ${PROPERTIES}; run this from a checkout of the repository.`)
    }
    const match = properties.match(/distributionUrl=.*gradle-([0-9][0-9a-z.-]*)-(?:bin|all)\.zip/)
    if (!match) fail(`could not find a gradle-<version>-bin.zip distributionUrl in ${PROPERTIES}.`)
    return match[1]
}

async function main() {
    const version = pinnedVersion()
    const expected = EXPECTED_SHA256[version]
    if (!expected) {
        fail(
            `gradle-wrapper.properties pins Gradle ${version}, but this script has no wrapper-jar ` +
                `hash for it. Download the jar for that version, hash it, and add the entry to ` +
                `EXPECTED_SHA256 in the same change that bumps Gradle.`,
        )
    }

    if (existsSync(JAR)) {
        const have = sha256(readFileSync(JAR))
        if (have === expected) {
            console.log(`bootstrap-gradle-wrapper: gradle-wrapper.jar present and verified for Gradle ${version}.`)
            return
        }
        console.warn(`bootstrap-gradle-wrapper: existing gradle-wrapper.jar does not match the pinned hash for Gradle ${version}.`)
        console.warn(`  have   ${have}`)
        console.warn(`  expect ${expected}`)
        console.warn(`  Leaving it in place because current builds may rely on it. To replace it with the`)
        console.warn(`  verified jar, delete ${JAR} and rerun this script.`)
        return
    }

    const url = `https://raw.githubusercontent.com/gradle/gradle/v${version}/gradle/wrapper/gradle-wrapper.jar`
    console.log(`bootstrap-gradle-wrapper: gradle-wrapper.jar is absent (it is deliberately not tracked).`)
    console.log(`bootstrap-gradle-wrapper: downloading ${url}`)

    let body
    try {
        const response = await fetch(url)
        if (!response.ok) fail(`download failed with HTTP ${response.status}. Check the network and rerun.`)
        body = Buffer.from(await response.arrayBuffer())
    } catch (error) {
        fail(
            `download failed (${error?.message ?? error}). Without this jar every gradlew invocation ` +
                `fails with "Unable to access jarfile". Check the network and rerun, or fetch the file ` +
                `from the URL above by other means and verify its SHA-256 is ${expected}.`,
        )
    }

    const got = sha256(body)
    if (got !== expected) {
        fail(
            `downloaded jar hash mismatch, refusing to install it.\n` +
                `  got    ${got}\n` +
                `  expect ${expected}\n` +
                `The download may be corrupt or the source compromised. Nothing was written.`,
        )
    }

    writeFileSync(JAR, body)
    console.log(`bootstrap-gradle-wrapper: installed and verified gradle-wrapper.jar for Gradle ${version} (sha256 ${got}).`)
}

await main().catch((error) => {
    if (error instanceof BootstrapError) {
        console.error(`bootstrap-gradle-wrapper: ${error.message}`)
    } else {
        console.error(`bootstrap-gradle-wrapper: unexpected failure: ${error?.stack ?? error}`)
    }
    process.exitCode = 1
})
