// @ts-nocheck -- logic.js is the dashboard page's plain browser script; these checks read it as the page does.
import assert from "node:assert/strict"
import fs from "node:fs"
import path from "node:path"

import logic from "../../../android/app/src/main/assets/dashboard/logic.js"

const ASSETS_DIR = path.join(__dirname, "..", "..", "..", "android", "app", "src", "main", "assets")

test('status label table: named keys', () => {
  assert.equal(logic.statusPhase('armed').label, 'Ready: tap the start button in the game');
  assert.equal(logic.statusPhase('armed').phase, 'armed');
  assert.equal(logic.statusPhase('armed').terminal, false);
  assert.equal(logic.statusPhase('running').label, 'Running');
  assert.equal(logic.statusPhase('running').phase, 'live');
  assert.equal(logic.statusPhase('running').terminal, false);
  assert.equal(logic.statusPhase('notRunning').label, 'The bot is not running');
  assert.equal(logic.statusPhase('notRunning').phase, 'terminal');
  assert.equal(logic.statusPhase('notRunning').terminal, true);
});

test('status label table: "latest event wins" between-runs family (completed/navigating/waiting/starting/resuming/retrying)', () => {
  for (const key of ['completed', 'navigating', 'waiting', 'starting', 'resuming', 'retrying']) {
    assert.equal(logic.statusPhase(key).label, 'Between runs', key);
    assert.equal(logic.statusPhase(key).phase, 'between', key);
    assert.equal(logic.statusPhase(key).terminal, false, key);
  }
});

test('status label table: the real terminal literals from the source map, not just notRunning', () => {
  for (const key of ['queueFailed', 'queueHalted', 'queueStopped', 'queueComplete', 'stoppedAfterCareer']) {
    assert.equal(logic.statusPhase(key).label, 'The bot is not running', key);
    assert.equal(logic.statusPhase(key).phase, 'terminal', key);
    assert.equal(logic.statusPhase(key).terminal, true, key);
  }
});

test('a finished run is between runs, not a stopped bot, while the career-end navigation runs', () => {
  const v = logic.viewModel(statusWith({ statusKey: 'completed', sessionActive: true, words: null }), connectedNow());
  assert.equal(v.phase, 'between');
  assert.equal(v.badge.text, 'BETWEEN RUNS');
  assert.notEqual(v.banner.headline, 'THE BOT IS NOT RUNNING');
});

test("the page treats exactly the producer's terminal keys (plus notRunning) as terminal", () => {
  const producer = fs.readFileSync(path.join(ASSETS_DIR, '..', 'java', 'com', 'steve1316', 'uma_android_automation', 'utils', 'StatusBoard.kt'), 'utf8').replace(/\r\n/g, '\n');
  const declared = /TERMINAL_KEYS = setOf\(([^)]*)\)/.exec(producer);
  assert.ok(declared, 'the producer declares its terminal keys');
  const producerKeys = [...declared[1].matchAll(/"(\w+)"/g)].map((m) => m[1]);
  assert.ok(producer.includes('else -> "notRunning"'), 'the producer sends notRunning when nothing is live');
  const pageKeys = /var TERMINAL_KEYS = \[([^\]]*)\]/.exec(fs.readFileSync(path.join(ASSETS_DIR, 'dashboard', 'logic.js'), 'utf8'));
  assert.ok(pageKeys, 'the page declares its terminal keys');
  const page = [...pageKeys[1].matchAll(/'(\w+)'/g)].map((m) => m[1]);
  assert.deepEqual([...page].sort(), [...producerKeys, 'notRunning'].sort());
  for (const key of page) assert.equal(logic.statusPhase(key).terminal, true, key);
});

test('status label table: unknown key fails closed to Working, never a real label', () => {
  assert.equal(logic.statusPhase('someFutureKey').label, 'Working');
  assert.equal(logic.statusPhase('someFutureKey').phase, 'unknown');
  assert.equal(logic.statusPhase(undefined).label, 'Working');
  assert.equal(logic.statusPhase(null).label, 'Working');
  // mutation guard: an unknown key must never resolve to any of the named labels
  const namedLabels = ['Ready: tap the start button in the game', 'Between runs', 'The bot is not running', 'Running'];
  assert.ok(!namedLabels.includes(logic.statusPhase('totallyUnknown').label));
});

test('grade bands: every edge named in the task', () => {
  assert.equal(logic.grade(49), 'G');
  assert.equal(logic.grade(50), 'G+');
  assert.equal(logic.grade(199), 'F+');
  assert.equal(logic.grade(200), 'E');
  assert.equal(logic.grade(399), 'D+');
  assert.equal(logic.grade(400), 'C');
  assert.equal(logic.grade(999), 'A+');
  assert.equal(logic.grade(1000), 'S');
  assert.equal(logic.grade(1049), 'S');
  assert.equal(logic.grade(1050), 'S+');
  assert.equal(logic.grade(1200), 'SS+');
  assert.equal(logic.grade(1201), 'UG0');
  assert.equal(logic.grade(1248), 'UG4');
});

test('grade bands: full table spot-check against contract prose', () => {
  const table = [
    [1, 'G'], [49, 'G'], [50, 'G+'], [99, 'G+'],
    [100, 'F'], [149, 'F'], [150, 'F+'], [199, 'F+'],
    [200, 'E'], [249, 'E'], [250, 'E+'], [299, 'E+'],
    [300, 'D'], [349, 'D'], [350, 'D+'], [399, 'D+'],
    [400, 'C'], [499, 'C'], [500, 'C+'], [599, 'C+'],
    [600, 'B'], [699, 'B'], [700, 'B+'], [799, 'B+'],
    [800, 'A'], [899, 'A'], [900, 'A+'], [999, 'A+'],
    [1000, 'S'], [1049, 'S'], [1050, 'S+'], [1099, 'S+'],
    [1100, 'SS'], [1149, 'SS'], [1150, 'SS+'], [1200, 'SS+'],
  ];
  for (const [v, g] of table) assert.equal(logic.grade(v), g, `grade(${v})`);
});

test('grade: U-band letter and digit past 1201', () => {
  assert.equal(logic.grade(1301), 'UF0');
  assert.equal(logic.grade(1401), 'UE0');
  assert.equal(logic.grade(2001), 'US0');
  assert.equal(logic.grade(1291), 'UG9');
});

test('gradeColor: accent only from 1000 up, otherwise the base text colour', () => {
  assert.equal(logic.gradeColor(999, '#E8B84B'), '#EDF1F5');
  assert.equal(logic.gradeColor(1000, '#E8B84B'), '#E8B84B');
});

test('skew-safe age: computed from device/PC clock references, never a direct compare', () => {
  // device sent status at t=1000 (device clock), field observed at t=990 (device clock)
  // page received it at t=5000 (page clock), and it is now t=5010 (page clock)
  const ms = logic.computeAgeMs(1000, 990, 5000, 5010);
  // age = (1000 - 990) + (5010 - 5000) = 10 + 10 = 20
  assert.equal(ms, 20);
});

test('skew-safe age: large device/PC clock offset does not corrupt the result', () => {
  // device clock is a year behind the page clock; the skew-safe formula must
  // still return the true elapsed time (10s), because sentAt/receivedAt absorb
  // the offset. A direct fieldAt-vs-now compare would be wildly wrong here.
  const YEAR_MS = 365 * 24 * 3600 * 1000;
  const deviceNow = 1_000_000_000;
  const pageNow = deviceNow + YEAR_MS;
  const fieldAt = deviceNow - 10_000; // observed 10s before the device sent it
  const receivedAt = pageNow;
  const now = pageNow + 5_000; // 5s after the page received the frame
  const ms = logic.computeAgeMs(deviceNow, fieldAt, receivedAt, now);
  assert.equal(ms, 15_000);
  const directCompare = now - fieldAt; // what a broken implementation would do
  assert.notEqual(ms, directCompare);
});

test('formatAge: seconds, minutes, hours, days', () => {
  assert.equal(logic.formatAge(0), '0 s ago');
  assert.equal(logic.formatAge(45000), '45 s ago');
  assert.equal(logic.formatAge(90000), '1 min ago');
  assert.equal(logic.formatAge(3 * 3600 * 1000), '3 h ago');
  assert.equal(logic.formatAge(2 * 86400 * 1000), '2 d ago');
});

test('turnsUntilGoal: dueTurn minus the current turn, never negative', () => {
  assert.equal(logic.turnsUntilGoal(12, 2), 10);
  assert.equal(logic.turnsUntilGoal(0, 0), 0);
  assert.equal(logic.turnsUntilGoal(null, 2), null);
  assert.equal(logic.turnsUntilGoal(12, null), null);
  // a goal already due (dueTurn < currentTurn) shows no countdown, per the
  // contract's "while that is at least 0"
  assert.equal(logic.turnsUntilGoal(5, 8), null);
});

const SAMPLE_COURSE = {
  finalTurn: 75,
  segments: [
    { label: 'Junior Year', from: 1, to: 24 },
    { label: 'Classic Year', from: 25, to: 48 },
    { label: 'Senior Year', from: 49, to: 72 },
    { label: 'URA Finale', from: 73, to: 75, finale: true },
  ],
};

test('course geometry: positions match the design sample at turn 2, goal turn 12', () => {
  const c = logic.buildCourse(SAMPLE_COURSE, 2, 12);
  assert.equal(c.runner, '2.67%'); // round(2/75*10000)/100
  assert.equal(c.goal, '16%'); // round(12/75*10000)/100
  assert.deepEqual(c.poles, ['32%', '64%', '96%']); // at(24), at(48), at(72)
  assert.equal(c.years.length, 3); // finale is not a top "year" label
  assert.equal(c.years[0].label, 'JUNIOR YEAR');
  assert.equal(c.years[0].left, '0%');
  assert.equal(c.years[0].current, true); // turn 2 >= from 1
  assert.equal(c.years[1].current, false); // turn 2 < from 25
  assert.equal(c.finale.label, 'URA FINALE');
  assert.equal(c.finale.left, '96%');
  assert.equal(c.finale.from, 73);
  assert.equal(c.finale.to, 75);
});

test('course geometry: the goal flag sits at the goal\'s dueTurn, not at the current turn', () => {
  const atCurrentTurn = logic.buildCourse(SAMPLE_COURSE, 2, 2).goal;
  const atDueTurn = logic.buildCourse(SAMPLE_COURSE, 2, 12).goal;
  assert.notEqual(atCurrentTurn, atDueTurn);
  assert.equal(atDueTurn, '16%');
});

test('course geometry: null course shows no course (date/turn only)', () => {
  assert.equal(logic.buildCourse(null, 2, 12), null);
  assert.equal(logic.buildCourse({ segments: [] }, 2, 12), null);
});

test('course geometry: a scenario with no finale segment has no finale zone', () => {
  const noFinale = {
    finalTurn: 48,
    segments: [
      { label: 'Junior Year', from: 1, to: 24 },
      { label: 'Classic Year', from: 25, to: 48 },
    ],
  };
  const c = logic.buildCourse(noFinale, 10, 20);
  assert.equal(c.finale, null);
});

test('null handling: naText shows "not available", never invents a value', () => {
  assert.equal(logic.naText(null), 'not available');
  assert.equal(logic.naText(undefined), 'not available');
  assert.equal(logic.naText('El Condor Pasa'), 'El Condor Pasa');
  assert.equal(logic.naText(5, logic.formatNumber(5)), '5');
});

test('formatNumber: thousands separators, and null passthrough', () => {
  assert.equal(logic.formatNumber(221054), (221054).toLocaleString());
  assert.equal(logic.formatNumber(null), null);
});

test('spark colours: stat blue, aptitude red, unique green, others neutral', () => {
  assert.equal(logic.sparkColor('stat'), '#5BA8FF');
  assert.equal(logic.sparkColor('aptitude'), '#FF6F82');
  assert.equal(logic.sparkColor('unique'), '#5ED39A');
  assert.equal(logic.sparkColor('skill'), logic.SPARK_COLORS.other);
  assert.equal(logic.sparkColor('race'), logic.SPARK_COLORS.other);
  assert.equal(logic.sparkColor('scenario'), logic.SPARK_COLORS.other);
  assert.equal(logic.sparkColor('other'), logic.SPARK_COLORS.other);
});

test('bracket colours: the four the design gives, exactly', () => {
  assert.deepEqual(logic.bracketColor(1), { bg: '#F2F2EE', fg: '#0D1218', border: '1px solid #F2F2EE' });
  assert.deepEqual(logic.bracketColor(2), { bg: '#161616', fg: '#F2F2EE', border: '1px solid #6A6A6A' });
  assert.deepEqual(logic.bracketColor(3), { bg: '#D8413A', fg: '#FFFFFF', border: '1px solid #D8413A' });
  assert.deepEqual(logic.bracketColor(4), { bg: '#2F6FD6', fg: '#FFFFFF', border: '1px solid #2F6FD6' });
});

test('bracket colours: 5-8 are defined and distinct (extended beyond the design sample)', () => {
  const seen = new Set();
  for (let n = 1; n <= 8; n++) {
    const c = logic.bracketColor(n);
    assert.ok(c.bg, `bracket ${n} has a colour`);
    seen.add(c.bg);
  }
  assert.equal(seen.size, 8);
});

test('buildStatRows: names, order, colours and grades', () => {
  const rows = logic.buildStatRows({ speed: 197, stamina: 104, power: 122, guts: 138, wit: 96 }, '#E8B84B');
  assert.deepEqual(rows.map((r) => r.name), ['SPEED', 'STAMINA', 'POWER', 'GUTS', 'WIT']);
  assert.equal(rows[0].color, '#5BA8FF');
  assert.equal(rows[0].grade, grade197());
  function grade197() { return logic.grade(197); }
  assert.equal(rows.every((r) => r.width.endsWith('%')), true);
});

test('buildStatRows: null stats yields an empty table, never fabricated rows', () => {
  assert.deepEqual(logic.buildStatRows(null), []);
});

test('buildCells: lit cells get the "on" colour, the rest stay off', () => {
  const cells = logic.buildCells(5, 3, '#5ED39A');
  assert.deepEqual(cells.map((c) => c.color), ['#5ED39A', '#5ED39A', '#5ED39A', '#1A222C', '#1A222C']);
});

test('buildRunRow: the producer\'s six run states map to the design labels', () => {
  const done = logic.buildRunRow({ n: 1, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'done', rank: 'A', estScore: 10757, fans: 221054 }, false, '#E8B84B');
  assert.equal(done.result, 'EST. A');
  assert.equal(done.gateBg, '#F2F2EE');
  const running = logic.buildRunRow({ n: 2, state: 'running' }, true, '#E8B84B');
  assert.equal(running.result, 'RUNNING');
  assert.equal(running.bg, '#131B25');
  assert.equal(running.chevron, '#E8B84B');
  const next = logic.buildRunRow({ n: 3, state: 'next' }, false, '#E8B84B');
  assert.equal(next.result, 'UP NEXT');
  const waiting = logic.buildRunRow({ n: 4, state: 'waiting' }, false, '#E8B84B');
  assert.equal(waiting.result, 'WAITING');
  const stopped = logic.buildRunRow({ n: 5, state: 'stopped' }, false, '#E8B84B');
  assert.equal(stopped.result, 'STOPPED');
  const errored = logic.buildRunRow({ n: 6, state: 'errored' }, false, '#E8B84B');
  assert.equal(errored.result, 'ERROR');
});

test('buildRunRow: an unrecognised state fails closed to WAITING, not a guessed result', () => {
  const row = logic.buildRunRow({ n: 3, state: 'somethingNew' }, false, '#E8B84B');
  assert.equal(row.result, 'WAITING');
});

test('describeFinale says how many finale races were raced, never a count of the scenario\'s three', () => {
  assert.equal(logic.describeFinale({ won: 3, of: 3 }), '3 of 3 raced');
  assert.equal(logic.describeFinale({ won: 0, of: 1 }), '0 of 1 raced');
  assert.equal(logic.describeFinale(null), null);
  assert.equal(logic.describeFinale({ won: 'x', of: 1 }), null);
});

test('the slip and the banner call the rank an estimate', () => {
  const app = fs.readFileSync(path.join(ASSETS_DIR, 'dashboard', 'app.js'), 'utf8');
  assert.ok(app.includes("'rc-details-rank-label', 'EST. RANK'"));
  assert.ok(!app.includes("'rc-details-rank-label', 'RANK'"));
});

test('describeTpRestores and describeRecoveries compose from the actual sub-counts', () => {
  assert.equal(logic.describeTpRestores({ items: 0, carats: 0 }), '0 with items · 0 with Carats');
  assert.equal(logic.describeRecoveries({ total: 2, accessibility: 2, relaunches: 0, connection: 0 }), '2 accessibility repairs');
  assert.equal(logic.describeRecoveries({ total: 1, accessibility: 1, relaunches: 0, connection: 0 }), '1 accessibility repair');
  assert.equal(logic.describeRecoveries({ total: 1, accessibility: 0, relaunches: 0, connection: 1 }), '1 connection hold');
  assert.equal(logic.describeRecoveries({ total: 2, accessibility: 0, relaunches: 0, connection: 2 }), '2 connection holds');
  assert.equal(logic.describeRecoveries({ total: 1, accessibility: 0, relaunches: 0, lobby: 1, connection: 0 }), '1 lobby re-entry');
  assert.equal(logic.describeRecoveries({ total: 6, accessibility: 2, relaunches: 1, lobby: 2, connection: 1 }), '2 accessibility repairs · 1 relaunch · 2 lobby re-entries · 1 connection hold');
  assert.equal(logic.describeRecoveries({ total: 0, accessibility: 0, relaunches: 0, connection: 0 }), 'none yet');
  assert.equal(logic.describeRecoveries(null), null);
});

test('formatDuration and formatClock', () => {
  assert.equal(logic.formatDuration(54 * 60 * 1000), '54 min');
  assert.equal(logic.formatDuration(90 * 60 * 1000), '1 h 30 min');
  assert.equal(logic.formatDuration(120 * 60 * 1000), '2 h');
  const d = new Date();
  d.setHours(20, 32, 0, 0);
  assert.equal(logic.formatClock(d.getTime()), '20:32');
});

// --- Text-only rendering guard: app.js must never assign raw HTML. ---
test('text-only rendering: app.js never uses innerHTML/outerHTML/insertAdjacentHTML/document.write', () => {
  const appJsPath = path.join(ASSETS_DIR, 'dashboard', 'app.js');
  const src = fs.readFileSync(appJsPath, 'utf8');
  const forbidden = [/\.innerHTML\s*=/, /\.outerHTML\s*=/, /\.insertAdjacentHTML\s*\(/, /document\.write\s*\(/];
  for (const re of forbidden) {
    assert.equal(re.test(src), false, `app.js must not match ${re}`);
  }
});

// --- No external URL in anything the page loads or executes at runtime.
// (License/provenance text files are exempt: OFL.txt legitimately carries
// upstream copyright URLs, and neither file is ever fetched by the page.
// The XML namespace URIs SVG requires are inert identifier strings, never
// network requests, so they are exempt too.)
test('no external URL in any shipped html/css/js file', () => {
  const externalUrl = /\b(https?:)?\/\/(?!localhost\b)[a-z0-9.-]+\.[a-z]{2,}/i;
  const allowed = [/^http:\/\/www\.w3\.org\/(1999\/xhtml|2000\/svg)$/];
  const offenders = [];
  (function walk(dir) {
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) { walk(full); continue; }
      if (!/\.(html|css|js)$/i.test(name)) continue;
      const text = fs.readFileSync(full, 'utf8');
      const lines = text.split('\n');
      lines.forEach((line, i) => {
        if (!externalUrl.test(line)) return;
        const match = line.match(/(https?:)?\/\/[^\s"'()]+/);
        if (match && allowed.some((re) => re.test(match[0]))) return;
        offenders.push(full + ':' + (i + 1) + ': ' + line.trim());
      });
    }
  })(path.join(ASSETS_DIR, 'dashboard'));
  assert.deepEqual(offenders, []);
});

// ==================================================================
// viewModel(): every state-composition decision, in one pure place.
// An unknown statusKey showing LIVE, and a disconnected banner keeping a
// stale headline, were both app.js bugs that no test caught, because
// nothing tested what app.js actually rendered for a phase. These tests
// assert the view model itself, and app.js is now a thin layer that only
// applies it to the DOM.
// ==================================================================

const T = 1_800_000_000_000; // an arbitrary fixed "now", page clock

const LIVE_CAREER = {
  trainee: 'El Condor Pasa', scenario: 'URA Finale',
  action: { kind: 'training', detail: 'SPEED', at: T - 6000 },
  date: { year: 'Junior Year', label: 'Late January', turn: 2, at: T - 10000 },
  goal: { name: 'Junior Make Debut', dueTurn: 12, at: T - 10000 },
  stats: { speed: 197, stamina: 104, power: 122, guts: 138, wit: 96, at: T - 10000 },
  energy: { percent: 74, at: T - 10000 },
  mood: { level: 'GOOD', at: T - 10000 },
  racesRun: 0,
};

function statusWith(overrides) {
  return Object.assign({
    type: 'status', v: 1, sentAt: T, sessionActive: true,
    statusKey: 'running',
    run: { current: 2, total: 4 },
    career: LIVE_CAREER,
    lastProgressAt: T - 4000,
    runs: [
      { n: 1, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'done', rank: 'B', estScore: 8000, fans: 100000 },
      { n: 2, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'running' },
    ],
    session: { startedAt: T - 3240000, tpRestores: { items: 0, carats: 0 }, recoveries: { total: 2, accessibility: 2, relaunches: 0, connection: 0 } },
    words: null,
  }, overrides);
}

function connectedNow(receivedAt) {
  return { connected: true, receivedAt: receivedAt != null ? receivedAt : T, now: T };
}

test('viewModel: connecting, no status yet', () => {
  const vm1 = logic.viewModel(null, { connected: true, receivedAt: null, now: T });
  assert.equal(vm1.phase, 'connecting');
  assert.equal(vm1.badge.text, 'CONNECTING');
  assert.equal(vm1.badge.lampLit, false);
  assert.equal(vm1.banner.visible, true);
  assert.equal(vm1.banner.headline, 'CONNECTING');
  assert.deepEqual(vm1.blocks, { nowVisible: false, queueVisible: false, sessionVisible: false, dimmed: false });

  // Never connected at all (not even a socket open yet) is the same phase.
  const vm2 = logic.viewModel(null, { connected: false, receivedAt: null, now: T });
  assert.equal(vm2.phase, 'connecting');
});

test('viewModel: armed, with and without a queued runs[]', () => {
  const noQueue = logic.viewModel(
    statusWith({ statusKey: 'armed', run: { current: 1, total: 4 }, career: { trainee: 'El Condor Pasa', scenario: 'URA Finale' }, runs: [], session: null }),
    connectedNow()
  );
  assert.equal(noQueue.phase, 'armed');
  assert.equal(noQueue.badge.text, 'READY');
  assert.equal(noQueue.badge.lampLit, false);
  assert.equal(noQueue.banner.headline, 'TAP THE START BUTTON IN THE GAME');
  assert.deepEqual(noQueue.banner.stripe, [
    { label: 'QUEUE', value: '4 runs' },
    { label: 'TRAINEE', value: 'El Condor Pasa' },
    { label: 'SCENARIO', value: 'URA Finale' },
  ]);
  // Both live blocks and the session strip are hidden; with an empty
  // runs[], Today's card hides too.
  assert.deepEqual(noQueue.blocks, { nowVisible: false, queueVisible: false, sessionVisible: false, dimmed: false });

  const withQueue = logic.viewModel(
    statusWith({ statusKey: 'armed', run: { current: 1, total: 4 }, runs: [{ n: 1, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'waiting' }] }),
    connectedNow()
  );
  // Today's card may show the queued slots when runs[] has them.
  assert.equal(withQueue.blocks.queueVisible, true);
  assert.equal(withQueue.blocks.nowVisible, false);
  assert.equal(withQueue.blocks.sessionVisible, false);
});

test('viewModel: live (statusKey running)', () => {
  const vm = logic.viewModel(statusWith({}), connectedNow());
  assert.equal(vm.phase, 'live');
  assert.equal(vm.badge.text, 'LIVE');
  assert.equal(vm.badge.lampLit, true);
  assert.equal(vm.banner.visible, false);
  assert.deepEqual(vm.blocks, { nowVisible: true, queueVisible: true, sessionVisible: true, dimmed: false });
});

test('viewModel: between runs, with and without a just-finished run', () => {
  const withLastRank = logic.viewModel(
    statusWith({
      statusKey: 'waiting', run: { current: 3, total: 4 },
      runs: [
        { n: 1, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'done', rank: 'A' },
        { n: 2, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'done', rank: 'B' },
        { n: 3, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'next' },
      ],
    }),
    connectedNow()
  );
  assert.equal(withLastRank.phase, 'between');
  assert.equal(withLastRank.badge.text, 'BETWEEN RUNS');
  assert.equal(withLastRank.badge.lampLit, false);
  assert.equal(withLastRank.banner.headline, 'LAUNCHING RUN 3');
  assert.match(withLastRank.banner.body, /Run 2 finished with an estimated rank of B/);
  // The NOW block hides; the queue and session stay.
  assert.deepEqual(withLastRank.blocks, { nowVisible: false, queueVisible: true, sessionVisible: true, dimmed: false });

  const noRunYet = logic.viewModel(statusWith({ statusKey: 'starting', run: null, runs: [] }), connectedNow());
  assert.equal(noRunYet.banner.headline, 'BETWEEN RUNS');
  assert.equal(noRunYet.banner.body, 'The bot is between runs.');
});

// The producer keeps run.current on the just-finished run for completed,
// navigating and waiting; runs[] marks the run that is actually next.
const done = (n, rank) => ({ n, trainee: 'El Condor Pasa', scenario: 'URA Finale', state: 'done', rank });
const upcoming = (n, state) => ({ n, trainee: null, scenario: null, state });

for (const key of ['completed', 'navigating', 'waiting']) {
  test(`between-runs banner for ${key}: names the next run, not the one that just finished`, () => {
    const vm = logic.viewModel(
      statusWith({ statusKey: key, run: { current: 2, total: 4 }, runs: [done(1, 'A'), done(2, 'B'), upcoming(3, 'next'), upcoming(4, 'waiting')] }),
      connectedNow()
    );
    assert.equal(vm.phase, 'between');
    assert.equal(vm.banner.headline, 'LAUNCHING RUN 3');
    assert.equal(vm.banner.body, 'Run 2 finished with an estimated rank of B. The bot is starting the next run.');
  });
}

test('between-runs banner for starting: the next run is the one starting', () => {
  const vm = logic.viewModel(
    statusWith({ statusKey: 'starting', run: { current: 3, total: 4 }, runs: [done(1, 'A'), done(2, 'B'), upcoming(3, 'next'), upcoming(4, 'waiting')] }),
    connectedNow()
  );
  assert.equal(vm.banner.headline, 'LAUNCHING RUN 3');
});

test('between-runs banner for resuming: the first run of the resumed queue', () => {
  const vm = logic.viewModel(
    statusWith({ statusKey: 'resuming', run: { current: 3, total: 4 }, runs: [upcoming(3, 'next'), upcoming(4, 'waiting')] }),
    connectedNow()
  );
  assert.equal(vm.banner.headline, 'LAUNCHING RUN 3');
  assert.equal(vm.banner.body, 'The bot is between runs.');
});

test('between-runs banner for retrying: the run played again', () => {
  const vm = logic.viewModel(
    statusWith({ statusKey: 'retrying', run: { current: 2, total: 4 }, runs: [done(1, 'A'), upcoming(2, 'next'), upcoming(3, 'waiting'), upcoming(4, 'waiting')] }),
    connectedNow()
  );
  assert.equal(vm.banner.headline, 'LAUNCHING RUN 2');
});

test('between-runs banner after the last run: no run to launch, and no claim that one is starting', () => {
  const vm = logic.viewModel(
    statusWith({ statusKey: 'completed', run: { current: 4, total: 4 }, runs: [done(1, 'A'), done(2, 'B'), done(3, 'A'), done(4, 'S')] }),
    connectedNow()
  );
  assert.equal(vm.banner.headline, 'BETWEEN RUNS');
  assert.equal(vm.banner.body, 'Run 4 finished with an estimated rank of S.');
});

test('viewModel: every terminal key gets its badge from the key table, never inferred from run counts', () => {
  const cases = [
    ['queueComplete', 'FINISHED', 'neutral'],
    ['queueStopped', 'STOPPED', 'warn'],
    ['queueHalted', 'STOPPED', 'warn'],
    ['queueFailed', 'STOPPED', 'warn'],
    ['stoppedAfterCareer', 'PAUSED', 'neutral'],
    ['notRunning', 'NOT RUNNING', 'neutral'],
  ];
  for (const [key, text, tone] of cases) {
    // Same key, two very different run-count shapes: the badge must not
    // change, because the contract has no "resumable" field to infer from.
    const incomplete = logic.viewModel(statusWith({ statusKey: key, run: { current: 2, total: 5 }, words: { reason: 'r', nextAction: 'n' } }), connectedNow());
    const complete = logic.viewModel(statusWith({ statusKey: key, run: { current: 5, total: 5 }, words: { reason: 'r', nextAction: 'n' } }), connectedNow());
    assert.equal(incomplete.badge.text, text, key + ' (incomplete run count)');
    assert.equal(incomplete.badge.tone, tone, key);
    assert.equal(complete.badge.text, text, key + ' (complete run count)');
    assert.equal(incomplete.phase, 'terminal', key);
    // The fixed contract headline, never words.title.
    assert.equal(incomplete.banner.headline, 'THE BOT IS NOT RUNNING', key);
    assert.equal(incomplete.banner.body, 'r', key);
    assert.equal(incomplete.banner.whatToDoText, 'n', key);
    // The NOW block still hides for every terminal key.
    assert.equal(incomplete.blocks.nowVisible, false, key);
    assert.equal(incomplete.blocks.queueVisible, true, key);
    assert.equal(incomplete.blocks.sessionVisible, true, key);
  }
});

test('viewModel: terminal with no words.nextAction has no WHAT TO DO text', () => {
  const vm = logic.viewModel(statusWith({ statusKey: 'queueComplete', words: { reason: 'All 5 runs are done.' } }), connectedNow());
  assert.equal(vm.banner.whatToDoText, null);
  assert.equal(vm.banner.body, 'All 5 runs are done.');
});

test('viewModel: an unknown statusKey is never LIVE; the live data may stay visible', () => {
  const vm = logic.viewModel(statusWith({ statusKey: 'someFutureKeyThePageHasNeverSeen' }), connectedNow());
  assert.equal(vm.phase, 'unknown');
  assert.equal(vm.badge.text, 'WORKING');
  assert.notEqual(vm.badge.text, 'LIVE');
  assert.equal(vm.badge.lampLit, false, 'no lamp for an unrecognised key');
  assert.equal(vm.banner.visible, false, 'no banner override; the live data stays as-is');
  assert.equal(vm.blocks.nowVisible, true, 'the live data may stay visible');
  assert.equal(vm.blocks.dimmed, false);
});

test('viewModel: disconnected never keeps a stale headline, after Ready, after Between or after live', () => {
  const afterReady = logic.viewModel(
    statusWith({ statusKey: 'armed', career: { trainee: 'El Condor Pasa', scenario: 'URA Finale' } }),
    { connected: false, receivedAt: T, now: T + 5000 }
  );
  assert.equal(afterReady.phase, 'disconnected');
  assert.notEqual(afterReady.banner.headline, 'TAP THE START BUTTON IN THE GAME');
  assert.equal(afterReady.banner.headline, '');

  const afterBetween = logic.viewModel(
    statusWith({ statusKey: 'waiting', run: { current: 3, total: 4 } }),
    { connected: false, receivedAt: T, now: T + 5000 }
  );
  assert.notEqual(afterBetween.banner.headline, 'LAUNCHING RUN 3');
  assert.equal(afterBetween.banner.headline, '');

  const afterLive = logic.viewModel(statusWith({}), { connected: false, receivedAt: T, now: T + 5000 });
  assert.equal(afterLive.phase, 'disconnected');
  assert.equal(afterLive.banner.headline, '');
  assert.equal(afterLive.badge.text, 'DISCONNECTED');
  assert.equal(afterLive.badge.lampLit, false);
  assert.match(afterLive.banner.meta, /showing the last status from \d\d:\d\d/);
  // Dim, don't hide, and never re-derive stale data as if it were fresh.
  assert.deepEqual(afterLive.blocks, { nowVisible: true, queueVisible: true, sessionVisible: true, dimmed: true });
});

test('viewModel: connected with no status yet is connecting, not disconnected', () => {
  // Distinct from the disconnected tests above: `connected: true` but no
  // status object at all (immediately after AUTH_OK, before the first
  // status frame) must not claim a "last status" that does not exist.
  const vm = logic.viewModel(null, { connected: true, receivedAt: null, now: T });
  assert.equal(vm.phase, 'connecting');
  assert.notEqual(vm.phase, 'disconnected');
});

test('viewModel: total size and shape are stable regardless of session/tpRestores presence (totals are covered by totalTpRestores/totalRecoveries)', () => {
  const vm = logic.viewModel(statusWith({ session: null }), connectedNow());
  assert.equal(vm.blocks.sessionVisible, true, 'the section still shows; it is renderSession\'s job to print "not available" inside it');
});

test('totalTpRestores/totalRecoveries: null or non-finite shows nothing (not a guessed 0 or "null")', () => {
  assert.equal(logic.totalTpRestores(null), null);
  assert.equal(logic.totalTpRestores({ items: 0, carats: 0 }), 0);
  assert.equal(logic.totalTpRestores({ items: 2, carats: 3 }), 5);
  assert.equal(logic.totalTpRestores({ items: '2', carats: 3 }), null, 'a string field is not silently concatenated');
  assert.equal(logic.totalTpRestores({ items: NaN, carats: 3 }), null);
  assert.equal(logic.totalRecoveries(null), null);
  assert.equal(logic.totalRecoveries({ total: 0 }), 0);
  assert.equal(logic.totalRecoveries({ total: 5 }), 5);
  assert.equal(logic.totalRecoveries({ total: '5' }), null);
});

test('non-finite numbers are treated as not available everywhere, never a concatenated string', () => {
  assert.equal(logic.grade('197'), null);
  assert.equal(logic.formatNumber('12'), null);
  assert.equal(logic.formatNumber(Infinity), null);
  assert.equal(logic.formatNumber(NaN), null);
  assert.equal(logic.computeAgeMs('1', 2, 3, 4), null);
  assert.equal(logic.turnsUntilGoal('12', 2), null);
  assert.deepEqual(logic.buildStatRows({ speed: '197', stamina: 104, power: 122, guts: 138, wit: 96 })[0].value, null);
});

// ---------------- stop after this career ----------------

const stopStatus = (stop, extra) => statusWith(Object.assign({ session: { startedAt: T - 60000, stopAfterCareer: stop } }, extra || {}));

test('stop after this career: shown only when STATUS offers it on a live connection', () => {
  const offered = logic.stopAfterCareerView(stopStatus({ requested: false, offered: true }), true, false);
  assert.equal(offered.visible, true);
  assert.equal(offered.buttonText, 'Stop after this career');
  assert.equal(offered.buttonLabel, 'Stop the queue after this career');
  assert.equal(offered.buttonCommand, null, 'a request goes through the confirmation, never straight out');
  assert.equal(offered.confirmVisible, false);
  for (const [status, connected, why] of [
    [stopStatus({ requested: false, offered: false }), true, 'not offered'],
    [stopStatus({ requested: true, offered: false }), true, 'requested but not offered'],
    [stopStatus({ requested: false, offered: true }), false, 'disconnected'],
    [stopStatus({ requested: false, offered: true }, { sessionActive: false }), true, 'no session'],
    [statusWith({ session: { startedAt: T } }), true, 'an older producer without the field'],
    [null, true, 'no STATUS yet'],
  ]) {
    assert.equal(logic.stopAfterCareerView(status, connected, false).visible, false, why);
  }
});

test('stop after this career: confirm first, then the state comes from STATUS', () => {
  const confirming = logic.stopAfterCareerView(stopStatus({ requested: false, offered: true }), true, true);
  assert.equal(confirming.confirmVisible, true);
  assert.equal(logic.STOP_AFTER_CAREER_COMMAND, 'CMD:STOP_AFTER_CAREER');
  const requested = logic.stopAfterCareerView(stopStatus({ requested: true, offered: true }), true, true);
  assert.equal(requested.buttonText, 'Cancel stop');
  assert.equal(requested.buttonLabel, 'Cancel the stop after this career');
  assert.equal(requested.buttonCommand, 'CMD:CANCEL_STOP_AFTER_CAREER', 'a cancel goes straight out');
  assert.equal(requested.confirmVisible, false);
  assert.equal(requested.note, 'The queue pauses once this career has finished.');
});

test('stop after this career: the page sends only on the authenticated socket, as text, with its own markup', () => {
  const app = fs.readFileSync(path.join(ASSETS_DIR, 'dashboard', 'app.js'), 'utf8').replace(/\r\n/g, '\n');
  assert.match(app, /function sendCommand\(command\) \{\n    if \(state\.ws && state\.authed && state\.connected\) state\.ws\.send\(command\);\n  \}/);
  assert.match(app, /if \(view\.buttonCommand\) sendCommand\(view\.buttonCommand\);\n      else state\.stopConfirming = true;/);
  assert.match(app, /state\.stopConfirming = false;\n      sendCommand\(L\.STOP_AFTER_CAREER_COMMAND\);/);
  assert.equal((app.match(/sendCommand\(/g) || []).length, 3, 'the helper and its two callers only');
  const html = fs.readFileSync(path.join(ASSETS_DIR, 'dashboard', 'index.html'), 'utf8');
  for (const id of ['rc-stop-after', 'rc-stop-after-button', 'rc-stop-after-confirm', 'rc-stop-after-yes', 'rc-stop-after-no', 'rc-stop-after-note']) {
    assert.ok(html.includes(`id="${id}"`), id);
  }
  assert.ok(html.includes('<div id="rc-stop-after" class="rc-stop-after" hidden>'), 'hidden until STATUS offers it');
  assert.ok(html.includes("content=\"default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; connect-src 'self' ws://localhost:* ws://127.0.0.1:*; base-uri 'none'; form-action 'self'\""), 'CSP unchanged');
});
