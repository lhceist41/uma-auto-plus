#!/usr/bin/env node
/**
 * Repository hygiene guard.
 *
 * Fails the build when a tracked path is one this project has decided must never be published:
 * private local material, machine captures, diagnostics, build output, or credentials. The rules
 * below are deliberately narrow and path based. There is no regex secret scanner here on purpose:
 * a heuristic content scanner produces false positives that people learn to skip, which is worse
 * than no check at all. Content review is a human step, described in CONTRIBUTING.md.
 *
 * Source of truth is `git ls-files`, so only TRACKED paths are inspected. Files that are merely
 * present locally and ignored are none of this script's business.
 *
 * Exit code 0 means every tracked path complies. Any other exit code lists the offending paths.
 */

import { execFileSync } from "node:child_process"
import path from "node:path"

/** Tracked paths, POSIX separators, sorted for deterministic output. */
function trackedFiles() {
    const out = execFileSync("git", ["ls-files", "-z"], { encoding: "buffer", maxBuffer: 64 * 1024 * 1024 })
    return out
        .toString("utf8")
        .split("\0")
        .filter(Boolean)
        .map((p) => p.split(path.sep).join("/"))
        .sort()
}

/**
 * Root-level Markdown that is genuinely public documentation. Anything else tracked at the root
 * with a .md extension fails: that is where one-off notes, task files, investigation write-ups and
 * pasted instructions accumulate, and naming them individually would be a losing game.
 */
const ALLOWED_ROOT_MARKDOWN = new Map([
    ["README.md", "setup and feature overview for players"],
    ["CHANGELOG.md", "shipped behavior per release"],
    ["CONTRIBUTING.md", "publication policy and contribution rules"],
    ["HOW_IT_WORKS.md", "contributor-facing architecture reference"],
    ["PRESETS_GUIDE.md", "player-facing preset documentation"],
    ["TROUBLESHOOTING.md", "player-facing symptom guide"],
])

/**
 * Hidden directories at the repository root that may be tracked. Everything else dotted at the
 * root is some local tool's state, and those directories tend to accumulate instructions, session
 * notes and machine-specific configuration that must never be published. A new hidden directory
 * gets tracked only by earning an entry here.
 */
const ALLOWED_HIDDEN_DIRS = new Map([
    [".github", "workflows, issue template, funding metadata and PR template consumed by GitHub itself"],
])

/**
 * Narrow, reasoned exceptions. Every entry must say why the path is tracked despite matching a
 * rule, so an exception cannot quietly become a habit.
 */
const ALLOWLIST = new Map([
    [
        "android/app/src/test/resources/fixtures/grandconcert/PROVENANCE.md",
        "fixture provenance required by the recognition tests that consume these captures",
    ],
    [
        "android/app/src/test/resources/fixtures/sparks/PROVENANCE.md",
        "fixture provenance required by the spark-reading tests that consume these captures",
    ],
])

/** One rule: a predicate over a tracked path plus the reason it is refused. */
const RULES = [
    {
        id: "private-directory",
        why: "private local working directories must never be tracked",
        test: (p) => /^(scratchpad|private|validation|captures|gc-validation|gc-watch-out|docs-local|tools)\//.test(p),
    },
    {
        id: "hidden-directory",
        why: "top-level hidden directories hold local tool state; a tracked one needs an explicit entry",
        test: (p) => p.startsWith(".") && p.includes("/") && !ALLOWED_HIDDEN_DIRS.has(p.split("/")[0]),
    },
    {
        id: "database",
        why: "databases and their journals carry account state",
        test: (p) => /\.(db|db-wal|db-shm|sqlite|sqlite3)$/i.test(p),
    },
    {
        id: "raw-telemetry",
        why: "run logs and JSONL telemetry are per-account diagnostics, not source",
        test: (p) => /\.(log|jsonl|hprof)$/i.test(p),
    },
    {
        id: "build-output",
        why: "installable and intermediate build output is regenerated, never committed",
        test: (p) => /\.(apk|aab|apks|idsig|dm)$/i.test(p) || /(^|\/)index\.android\.bundle$/.test(p),
    },
    {
        id: "credential",
        why: "keys, certificates and keystores must never enter the repository",
        test: (p) => /\.(jks|p12|pem|key|keystore)$/i.test(p),
    },
    {
        id: "env-file",
        why: "environment files hold secrets; only .env.example may be tracked",
        test: (p) => /(^|\/)\.env($|\.)/.test(p) && !/(^|\/)\.env\.example$/.test(p),
    },
    {
        id: "root-markdown",
        why: "only the documented public Markdown set may live at the repository root",
        test: (p) => p.endsWith(".md") && !p.includes("/") && !ALLOWED_ROOT_MARKDOWN.has(p),
    },
    {
        id: "removed-report",
        why: "this internal diagnostic write-up was removed from published history and must not return",
        test: (p) => p === "GC_CONTROL_CAREER_REPORT.md",
    },
]

function main() {
    let files
    try {
        files = trackedFiles()
    } catch (error) {
        console.error("repo-hygiene: could not list tracked files. Is this a git repository?")
        console.error(String(error && error.message ? error.message : error))
        process.exit(2)
    }

    const violations = []
    for (const file of files) {
        if (ALLOWLIST.has(file)) continue
        for (const rule of RULES) {
            if (rule.test(file)) {
                violations.push({ file, rule })
                break
            }
        }
    }

    console.log(`repo-hygiene: inspected ${files.length} tracked paths, ${RULES.length} rules, ${ALLOWLIST.size} allowlisted.`)

    if (violations.length === 0) {
        console.log("repo-hygiene: OK")
        process.exit(0)
    }

    console.error(`repo-hygiene: ${violations.length} tracked path(s) must not be published:`)
    for (const { file, rule } of violations) {
        console.error(`  ${file}`)
        console.error(`      rule ${rule.id}: ${rule.why}`)
    }
    console.error("")
    console.error("Fix by removing the path from tracking (git rm --cached <path>) and adding an ignore rule,")
    console.error("or, if it genuinely belongs in the repository, add it to ALLOWLIST with a reason.")
    console.error("See CONTRIBUTING.md for what may and may not be committed.")
    process.exit(1)
}

main()
