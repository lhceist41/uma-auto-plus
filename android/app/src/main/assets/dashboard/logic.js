/* UMA Auto+ Race Control dashboard: pure data/formatting logic.
   No DOM access here; app.js owns the DOM and the socket. UMD so this file
   loads both as a browser <script> (window.RaceControlLogic) and via
   Node's require() from test/logic.test.js. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.RaceControlLogic = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var ACCENT = '#E8B84B';

  var STAT_NAMES = ['SPEED', 'STAMINA', 'POWER', 'GUTS', 'WIT'];
  var STAT_KEYS = ['speed', 'stamina', 'power', 'guts', 'wit'];
  var STAT_COLORS = {
    SPEED: '#5BA8FF',
    STAMINA: '#FF7A6B',
    POWER: '#F2B24C',
    GUTS: '#FF8FB8',
    WIT: '#5ED39A',
  };
  var STAT_BAR_MAX = 1200;

  var SPARK_COLORS = {
    stat: '#5BA8FF',
    aptitude: '#FF6F82',
    unique: '#5ED39A',
    other: '#8C97A6',
  };

  // Brackets 1-4 are the design's exact reference values. 5-8 are not in the
  // design (only 4 sample runs were shown); extended to match its palette
  // and saturation.
  var BRACKET_COLORS = {
    1: { bg: '#F2F2EE', fg: '#0D1218', border: '1px solid #F2F2EE' },
    2: { bg: '#161616', fg: '#F2F2EE', border: '1px solid #6A6A6A' },
    3: { bg: '#D8413A', fg: '#FFFFFF', border: '1px solid #D8413A' },
    4: { bg: '#2F6FD6', fg: '#FFFFFF', border: '1px solid #2F6FD6' },
    5: { bg: '#E0B23A', fg: '#0D1218', border: '1px solid #E0B23A' },
    6: { bg: '#3FA85C', fg: '#FFFFFF', border: '1px solid #3FA85C' },
    7: { bg: '#D97A34', fg: '#FFFFFF', border: '1px solid #D97A34' },
    8: { bg: '#D6588B', fg: '#FFFFFF', border: '1px solid #D6588B' },
  };
  var BRACKET_FALLBACK = { bg: '#2A3544', fg: '#EDF1F5', border: '1px solid #2A3544' };

  // action.kind -> headline word. The contract names the kind enum; the
  // display word itself is a page-side choice, not a contract rule.
  var ACTION_WORDS = {
    training: 'TRAINING',
    race: 'RACING',
    rest: 'RESTING',
    recreation: 'RECREATION',
    infirmary: 'INFIRMARY',
    skills: 'SKILLS',
    event: 'EVENT',
    shop: 'SHOPPING',
    navigating: 'NAVIGATING',
    other: 'WORKING',
  };

  // Exact reference algorithm from the approved design (Main.dc.html); it
  // was manually checked against the contract's prose table at every band
  // edge (49/50, 199/200, 399/400, 999/1000, 1049/1050, 1200/1201) and at
  // the worked example (1248 -> UG4). Both agree everywhere.
  var GRADE_BANDS = [
    [50, 'G'], [100, 'G+'], [150, 'F'], [200, 'F+'], [250, 'E'], [300, 'E+'],
    [350, 'D'], [400, 'D+'], [500, 'C'], [600, 'C+'], [700, 'B'], [800, 'B+'],
    [900, 'A'], [1000, 'A+'], [1050, 'S'], [1100, 'S+'], [1150, 'SS'], [1201, 'SS+'],
  ];

  function grade(v) {
    if (!Number.isFinite(v)) return null;
    for (var i = 0; i < GRADE_BANDS.length; i++) {
      if (v < GRADE_BANDS[i][0]) return GRADE_BANDS[i][1];
    }
    var over = v - 1201;
    var letter = 'GFEDCBAS'[Math.min(7, Math.floor(over / 100))];
    var digit = Math.floor((over % 100) / 10);
    return 'U' + letter + digit;
  }

  function gradeColor(v, accent) {
    if (!Number.isFinite(v)) return '#EDF1F5';
    return v >= 1000 ? (accent || ACCENT) : '#EDF1F5';
  }

  function pct(v, max) {
    if (!Number.isFinite(v) || !max) return '0%';
    var p = Math.min(100, (Math.round((v / max) * 1000) / 10));
    return Math.max(0, p) + '%';
  }

  // "Latest event wins" (contract amendment, 2026-09-27): the producer sets
  // statusKey to `running` on every per-turn in-career publish, and a later
  // queue event overwrites it, so `running` is what a queued career reads
  // for as long as it is actually mid-turn, not a stale launch-time value.
  // Only the keys the contract names verbatim are recognised; anything else
  // (including a future key not in either family) falls to "Working", per
  // UI truthfulness rule 5: never guess a label for an unenumerated value.
  var BETWEEN_KEYS = ['completed', 'navigating', 'waiting', 'starting', 'resuming', 'retrying'];
  var TERMINAL_KEYS = ['queueFailed', 'queueHalted', 'queueStopped', 'queueComplete', 'stoppedAfterCareer', 'notRunning'];

  function statusPhase(statusKey) {
    if (statusKey === 'armed') {
      return { label: 'Ready: tap the start button in the game', terminal: false, phase: 'armed' };
    }
    if (statusKey === 'running') {
      return { label: 'Running', terminal: false, phase: 'live' };
    }
    if (BETWEEN_KEYS.indexOf(statusKey) !== -1) {
      return { label: 'Between runs', terminal: false, phase: 'between' };
    }
    if (TERMINAL_KEYS.indexOf(statusKey) !== -1) {
      return { label: 'The bot is not running', terminal: true, phase: 'terminal' };
    }
    return { label: 'Working', terminal: false, phase: 'unknown' };
  }

  // Skew-safe age: never compares device time with PC time directly.
  function computeAgeMs(sentAt, fieldAt, receivedAt, now) {
    if (!Number.isFinite(sentAt) || !Number.isFinite(fieldAt) ||
      !Number.isFinite(receivedAt) || !Number.isFinite(now)) {
      return null;
    }
    return (sentAt - fieldAt) + (now - receivedAt);
  }

  function formatAge(ms) {
    if (!Number.isFinite(ms)) return null;
    var clamped = Math.max(0, ms);
    var s = Math.floor(clamped / 1000);
    if (s < 60) return s + ' s ago';
    var m = Math.floor(s / 60);
    if (m < 60) return m + ' min ago';
    var h = Math.floor(m / 60);
    if (h < 24) return h + ' h ago';
    var d = Math.floor(h / 24);
    return d + ' d ago';
  }

  function formatClock(ms) {
    if (!Number.isFinite(ms)) return null;
    var d = new Date(ms);
    var hh = String(d.getHours()).padStart(2, '0');
    var mm = String(d.getMinutes()).padStart(2, '0');
    return hh + ':' + mm;
  }

  function formatDuration(ms) {
    if (!Number.isFinite(ms) || ms < 0) return null;
    var totalMin = Math.floor(ms / 60000);
    if (totalMin < 60) return totalMin + ' min';
    var h = Math.floor(totalMin / 60);
    var m = totalMin % 60;
    return m ? (h + ' h ' + m + ' min') : (h + ' h');
  }

  function formatNumber(n) {
    if (!Number.isFinite(n)) return null;
    return n.toLocaleString();
  }

  function naText(v, formatted) {
    if (v === null || v === undefined) return 'not available';
    return formatted === undefined ? String(v) : formatted;
  }

  // The producer sends the goal's absolute due turn directly (`goal.dueTurn`);
  // the page only derives how many turns remain, and only while that is not
  // negative (a goal already due does not show a countdown into the past).
  function turnsUntilGoal(dueTurn, currentTurn) {
    if (!Number.isFinite(dueTurn) || !Number.isFinite(currentTurn)) return null;
    var remaining = dueTurn - currentTurn;
    return remaining >= 0 ? remaining : null;
  }

  function bracketColor(n) {
    return BRACKET_COLORS[n] || BRACKET_FALLBACK;
  }

  function sparkColor(type) {
    return SPARK_COLORS[type] || SPARK_COLORS.other;
  }

  function actionWord(kind) {
    return ACTION_WORDS[kind] || ACTION_WORDS.other;
  }

  // Builds the 5-row stat table (speed/stamina/power/guts/wit) from
  // career.stats. Bars always run to 1200, per the design's own caption.
  // `lastKnown` (a run's lastKnownStats) names the stats whose value is the last one the
  // bot confirmed rather than a final read; those rows carry lastKnown: true. Their grade is
  // kept: it grades the value shown, which the mark says is an earlier read one.
  function buildStatRows(stats, accent, lastKnown) {
    if (!stats) return [];
    var marked = Array.isArray(lastKnown) ? lastKnown : [];
    return STAT_NAMES.map(function (name, i) {
      var v = stats[STAT_KEYS[i]];
      if (!Number.isFinite(v)) {
        return { name: name, value: null, width: '0%', color: STAT_COLORS[name], grade: null, gradeColor: '#EDF1F5', lastKnown: false };
      }
      return {
        name: name,
        value: v,
        width: pct(v, STAT_BAR_MAX),
        color: STAT_COLORS[name],
        grade: grade(v),
        gradeColor: gradeColor(v, accent),
        lastKnown: marked.indexOf(STAT_KEYS[i]) !== -1,
      };
    });
  }

  // Energy/mood cell strips: `lit` cells (from the left) get `on`, the rest
  // get the off colour.
  function buildCells(n, lit, on) {
    var off = '#1A222C';
    var out = [];
    for (var i = 0; i < n; i++) out.push({ color: i < (lit || 0) ? on : off });
    return out;
  }

  // Course geometry, drawn only from career.course and the current turn
  // (never hard-coded), per the contract's page rule.
  function buildCourse(course, turn, goalDueTurn) {
    if (!course || !Array.isArray(course.segments) || course.segments.length === 0) {
      return null;
    }
    var total = course.finalTurn;
    if (!Number.isFinite(total) || total <= 0) return null;
    var at = function (t) {
      var clamped = Math.max(0, Math.min(t, total));
      return (Math.round((clamped / total) * 10000) / 100) + '%';
    };
    var financeSeg = null;
    for (var i = 0; i < course.segments.length; i++) {
      if (course.segments[i].finale) { financeSeg = course.segments[i]; break; }
    }
    var years = course.segments
      .filter(function (s) { return !s.finale; })
      .map(function (s) {
        return {
          left: s.from <= 1 ? '0%' : at(s.from - 1),
          label: String(s.label).toUpperCase(),
          current: Number.isFinite(turn) && turn >= s.from,
        };
      });
    var poles = course.segments.slice(1).map(function (s) { return at(s.from - 1); });
    var runner = Number.isFinite(turn) ? at(turn) : null;
    var goal = Number.isFinite(goalDueTurn) ? at(goalDueTurn) : null;
    var finale = financeSeg ? {
      left: at(Math.max(financeSeg.from - 1, 0)),
      from: financeSeg.from,
      to: financeSeg.to,
      label: String(financeSeg.label).toUpperCase(),
    } : null;
    return { runner: runner, goal: goal, poles: poles, years: years, finale: finale };
  }

  // A run-row for the TODAY'S CARD table. `run` is one entry of the
  // contract's `runs[]`.
  function buildRunRow(run, isSelected, accent) {
    var bracket = bracketColor(run.n);
    var resultLabel = null;
    var resultColor = '#7D8A99';
    if (run.state === 'done' && run.rank) {
      resultLabel = 'EST. ' + run.rank;
      resultColor = '#F1D48A';
    } else if (run.state === 'running') {
      resultLabel = 'RUNNING';
      resultColor = '#86E4B0';
    } else if (run.state === 'next') {
      resultLabel = 'UP NEXT';
      resultColor = '#AAB5C2';
    } else if (run.state === 'waiting') {
      resultLabel = 'WAITING';
      resultColor = '#7D8A99';
    } else if (run.state === 'stopped') {
      resultLabel = 'STOPPED';
      resultColor = '#FFB3A8';
    } else if (run.state === 'errored') {
      resultLabel = 'ERROR';
      resultColor = '#FF7A6B';
    }
    // A run that has not started has no trainee or scenario yet; that is not a missing value.
    var trainee = pendingTraineeText(run);
    var notStarted = trainee === PENDING_TRAINEE;
    return {
      n: run.n,
      trainee: trainee,
      scenario: notStarted ? '' : naText(run.scenario),
      result: resultLabel || 'WAITING',
      resultColor: resultColor,
      score: formatNumber(run.estScore),
      fans: formatNumber(run.fans),
      gateBg: bracket.bg,
      gateFg: bracket.fg,
      gateBorder: bracket.border,
      bg: isSelected ? '#131B25' : 'transparent',
      chevron: isSelected ? (accent || ACCENT) : '#3A4656',
      aria: 'Run ' + run.n + ', ' + trainee + ', ' + (resultLabel || 'waiting').toLowerCase(),
    };
  }

  // What a run that has not started shows for its trainee: the row and the details pane say the same.
  var PENDING_TRAINEE = 'Named when it starts';
  function pendingTraineeText(run) {
    return (run.state === 'next' || run.state === 'waiting') && run.trainee == null && run.scenario == null ? PENDING_TRAINEE : naText(run.trainee);
  }

  function describeTpRestores(t) {
    if (!t) return null;
    var items = Number.isFinite(t.items) ? t.items : 0;
    var carats = Number.isFinite(t.carats) ? t.carats : 0;
    return items + ' with items · ' + carats + ' with Carats';
  }

  function describeRecoveries(r) {
    if (!r) return null;
    var parts = [];
    if (r.accessibility) parts.push(r.accessibility + (r.accessibility === 1 ? ' accessibility repair' : ' accessibility repairs'));
    if (r.relaunches) parts.push(r.relaunches + (r.relaunches === 1 ? ' game reopen' : ' game reopens'));
    if (r.lobby) parts.push(r.lobby + (r.lobby === 1 ? ' lobby re-entry' : ' lobby re-entries'));
    if (r.connection) parts.push(r.connection + (r.connection === 1 ? ' connection hold' : ' connection holds'));
    return parts.length ? parts.join(' · ') : 'none yet';
  }

  // The details header for a run that has no result yet: the live run has no
  // start time, but it is running, not "not started".
  function pendingRunWhen(run) {
    if (run.startedAt != null) return 'started ' + formatClock(run.startedAt);
    return run.state === 'running' ? 'running now' : 'not started';
  }

  // finale.of counts the finale races the bot ran, not the scenario's three,
  // so a career that ended early reads "0 of 1 raced", never "0 of 3".
  function describeFinale(f) {
    if (!f || !Number.isFinite(f.won) || !Number.isFinite(f.of)) return null;
    return f.won + ' of ' + f.of + ' raced';
  }

  // A null/absent tpRestores, or a non-finite items/carats (a malformed or
  // hostile payload), shows "not available", never a guessed 0.
  function totalTpRestores(t) {
    if (!t || !Number.isFinite(t.items) || !Number.isFinite(t.carats)) return null;
    return t.items + t.carats;
  }

  // A null/absent recoveries, or a non-finite total, shows "not available".
  function totalRecoveries(r) {
    if (!r || !Number.isFinite(r.total)) return null;
    return r.total;
  }

  // ---------------- view model: every state-composition decision, in one
  // place, so app.js only ever applies this to the DOM. ----------------

  var TERMINAL_BADGE = {
    queueComplete: 'FINISHED',
    queueStopped: 'STOPPED', queueHalted: 'STOPPED', queueFailed: 'STOPPED',
    stoppedAfterCareer: 'PAUSED',
    notRunning: 'NOT RUNNING',
  };
  // "warn" tones the badge red (an interruption); "neutral" is the plain
  // grey badge used for a clean finish or plain idle.
  var TERMINAL_TONE = {
    queueComplete: 'neutral',
    queueStopped: 'warn', queueHalted: 'warn', queueFailed: 'warn',
    stoppedAfterCareer: 'neutral',
    notRunning: 'neutral',
  };

  function badgeFor(phase, status) {
    switch (phase) {
      case 'connecting': return { text: 'CONNECTING', lampLit: false, tone: 'neutral' };
      case 'armed': return { text: 'READY', lampLit: false, tone: 'ready' };
      case 'live': return { text: 'LIVE', lampLit: true, tone: 'live' };
      // An unrecognised statusKey is never LIVE. The live data may
      // still be shown (see blocksFor); only the badge and lamp differ.
      case 'unknown': return { text: 'WORKING', lampLit: false, tone: 'neutral' };
      case 'between': return { text: 'BETWEEN RUNS', lampLit: false, tone: 'between' };
      case 'terminal': {
        var key = status && status.statusKey;
        return { text: TERMINAL_BADGE[key] || 'NOT RUNNING', lampLit: false, tone: TERMINAL_TONE[key] || 'neutral' };
      }
      case 'disconnected': return { text: 'DISCONNECTED', lampLit: false, tone: 'neutral' };
      default: return { text: 'WORKING', lampLit: false, tone: 'neutral' };
    }
  }

  function bannerFor(phase, status, receivedAt, now) {
    var empty = { visible: false, headline: '', meta: '', body: '', whatToDoText: null, stripe: null };
    // An unrecognised statusKey keeps the live data on screen with no banner
    // at all, exactly like `live`; only the badge differs.
    if (phase === 'live' || phase === 'unknown') return empty;

    if (phase === 'connecting') {
      return { visible: true, headline: 'CONNECTING', meta: '', body: 'Waiting for the first status update.', whatToDoText: null, stripe: null };
    }

    if (phase === 'disconnected') {
      // Never keep a previous banner's headline (armed's "TAP THE START
      // BUTTON..." or between's "LAUNCHING RUN N") on screen after a drop.
      // The frozen last-known view is shown dimmed instead (see blocksFor);
      // this banner is only the badge and the "since HH:MM" meta line.
      var whenText = receivedAt != null ? formatClock(receivedAt) : null;
      return {
        visible: true, headline: '',
        meta: whenText ? ('showing the last status from ' + whenText) : '',
        body: '', whatToDoText: null, stripe: null,
      };
    }

    if (phase === 'armed') {
      var stripe = null;
      if (status && status.run) {
        stripe = [
          { label: 'QUEUE', value: naText(status.run.total, status.run.total + (status.run.total === 1 ? ' run' : ' runs')) },
          { label: 'TRAINEE', value: naText(status.career && status.career.trainee) },
          { label: 'SCENARIO', value: naText(status.career && status.career.scenario) },
        ];
      }
      return {
        visible: true, headline: 'TAP THE START BUTTON IN THE GAME', meta: '',
        body: 'UMA Auto+ is armed. The session begins when you tap the floating button over the game. Nothing runs until then.',
        whatToDoText: null, stripe: stripe,
      };
    }

    if (phase === 'between') {
      // run.current is the just-finished run until `starting`, so name the
      // producer's `next` run; none (the last run is finalizing) says so plainly.
      var nextRun = status && Array.isArray(status.runs) ? status.runs.filter(function (r) { return r.state === 'next'; })[0] : null;
      var headline = nextRun ? ('LAUNCHING RUN ' + naText(nextRun.n)) : 'BETWEEN RUNS';
      // The most recently *finished* run, not simply the last array entry
      // (which is usually a run that has not started yet).
      var lastRun = status && Array.isArray(status.runs) ? status.runs.filter(function (r) { return r.state === 'done'; }).pop() : null;
      var body = (lastRun && lastRun.rank)
        ? ('Run ' + lastRun.n + ' finished with an estimated rank of ' + lastRun.rank + '.' + (nextRun ? ' The bot is starting the next run.' : ''))
        : 'The bot is between runs.';
      var meta = '';
      if (status && status.lastProgressAt != null && Number.isFinite(status.sentAt) && receivedAt != null) {
        var age = computeAgeMs(status.sentAt, status.lastProgressAt, receivedAt, now);
        if (age != null) meta = 'last progress ' + formatAge(age);
      }
      return { visible: true, headline: headline, meta: meta, body: body, whatToDoText: null, stripe: null };
    }

    // terminal: the contract's page rule is a fixed headline ("The bot is
    // not running") "with the words" as supporting text, not words.title
    // replacing it (every other phase already uses its own fixed headline,
    // never words.title). words.reason is the explanation; words.nextAction
    // (when the producer sends it) is the actual instruction, which is what
    // "WHAT TO DO" means, not the reason. The specific FINISHED/STOPPED
    // badge text comes only from the key table (see badgeFor), never
    // inferred from run counts.
    var words = status && status.words;
    return {
      visible: true,
      headline: 'THE BOT IS NOT RUNNING',
      meta: '',
      body: (words && words.reason) || '',
      whatToDoText: (words && words.nextAction) || null,
      stripe: null,
    };
  }

  function blocksFor(phase, status) {
    var career = status && status.career;
    switch (phase) {
      case 'connecting':
        return { nowVisible: false, queueVisible: false, sessionVisible: false, dimmed: false };
      case 'armed': {
        var runs = (status && Array.isArray(status.runs)) ? status.runs : [];
        return { nowVisible: false, queueVisible: runs.length > 0, sessionVisible: false, dimmed: false };
      }
      case 'between':
      case 'terminal':
        // Hide the NOW block (date/turn/goal/energy/mood/stats/course);
        // Today's card, run details, the session strip and the log stay.
        return { nowVisible: false, queueVisible: true, sessionVisible: true, dimmed: false };
      case 'live':
      case 'unknown':
        return { nowVisible: !!career, queueVisible: true, sessionVisible: true, dimmed: false };
      case 'disconnected':
        // Dim, not hide, exactly as the States artboard does (everything
        // below the badge dimmed); the data itself is still the true last
        // known snapshot, not invented.
        return { nowVisible: !!career, queueVisible: true, sessionVisible: true, dimmed: true };
      default:
        return { nowVisible: false, queueVisible: false, sessionVisible: false, dimmed: false };
    }
  }

  // The one place every state-composition decision is made. `status` is the
  // last known STATUS payload (or null if none has arrived yet); `connection`
  // is `{ connected, receivedAt, now }`, all page-clock values. app.js only
  // ever applies this return value to the DOM; it makes no phase decisions
  // of its own.
  function viewModel(status, connection) {
    connection = connection || {};
    var connected = !!connection.connected;
    var receivedAt = connection.receivedAt != null ? connection.receivedAt : null;
    var now = Number.isFinite(connection.now) ? connection.now : Date.now();

    var phase;
    if (!connected) {
      phase = status ? 'disconnected' : 'connecting';
    } else if (!status) {
      phase = 'connecting';
    } else {
      phase = statusPhase(status.statusKey).phase;
    }

    return {
      phase: phase,
      badge: badgeFor(phase, status),
      banner: bannerFor(phase, status, receivedAt, now),
      blocks: blocksFor(phase, status),
    };
  }

  // "Stop after this career" from the dashboard: the same request Home makes.
  // Shown only when STATUS says it is offered on a live connection; the
  // request goes through a confirmation, a cancel does not. What it shows
  // after a press comes from the next STATUS, never from the press itself.
  var STOP_AFTER_CAREER_COMMAND = 'CMD:STOP_AFTER_CAREER';
  var CANCEL_STOP_AFTER_CAREER_COMMAND = 'CMD:CANCEL_STOP_AFTER_CAREER';

  function stopAfterCareerView(status, connected, confirming) {
    var hidden = { visible: false, requested: false, buttonText: '', buttonLabel: '', buttonCommand: null, confirmVisible: false, note: '' };
    var stop = status && status.sessionActive === true && status.session && status.session.stopAfterCareer;
    if (!connected || !stop || stop.offered !== true) return hidden;
    if (stop.requested === true) {
      return {
        visible: true, requested: true,
        buttonText: 'Cancel stop', buttonLabel: 'Cancel the stop after this career',
        buttonCommand: CANCEL_STOP_AFTER_CAREER_COMMAND, confirmVisible: false,
        note: 'The queue pauses once this career has finished.',
      };
    }
    return {
      visible: true, requested: false,
      buttonText: 'Stop after this career', buttonLabel: 'Stop the queue after this career',
      buttonCommand: null, confirmVisible: confirming === true,
      note: '',
    };
  }

  return {
    ACCENT: ACCENT,
    STAT_NAMES: STAT_NAMES,
    STAT_KEYS: STAT_KEYS,
    STAT_COLORS: STAT_COLORS,
    STAT_BAR_MAX: STAT_BAR_MAX,
    SPARK_COLORS: SPARK_COLORS,
    BRACKET_COLORS: BRACKET_COLORS,
    ACTION_WORDS: ACTION_WORDS,
    grade: grade,
    gradeColor: gradeColor,
    pct: pct,
    statusPhase: statusPhase,
    computeAgeMs: computeAgeMs,
    formatAge: formatAge,
    formatClock: formatClock,
    formatDuration: formatDuration,
    formatNumber: formatNumber,
    naText: naText,
    turnsUntilGoal: turnsUntilGoal,
    bracketColor: bracketColor,
    sparkColor: sparkColor,
    actionWord: actionWord,
    buildStatRows: buildStatRows,
    buildCells: buildCells,
    buildCourse: buildCourse,
    buildRunRow: buildRunRow,
    describeTpRestores: describeTpRestores,
    describeRecoveries: describeRecoveries,
    describeFinale: describeFinale,
    pendingTraineeText: pendingTraineeText,
    pendingRunWhen: pendingRunWhen,
    totalTpRestores: totalTpRestores,
    totalRecoveries: totalRecoveries,
    viewModel: viewModel,
    stopAfterCareerView: stopAfterCareerView,
    STOP_AFTER_CAREER_COMMAND: STOP_AFTER_CAREER_COMMAND,
  };
});
