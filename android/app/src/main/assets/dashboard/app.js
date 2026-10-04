/* UMA Auto+ Race Control dashboard: DOM + the WebSocket.
   Read only: this page never sends CMD:* and never requests images.
   Every string that comes from the server is rendered with textContent,
   never innerHTML/outerHTML/insertAdjacentHTML/document.write. */
(function () {
  'use strict';

  var L = window.RaceControlLogic;
  var MAX_LOG_LINES = 500;
  // The existing viewer's WS upgrade path. If the server registers the
  // socket at a different path than the page route, change this one
  // constant.
  var WS_PATH = '/';
  var TONE_CLASS = {
    live: 'rc-badge--live',
    ready: 'rc-badge--ready',
    between: 'rc-badge--between',
    warn: 'rc-badge--paused',
    neutral: 'rc-badge--disconnected',
  };

  var TAG_COLORS = {
    '[QUEUE]': '#86E4B0',
    '[NAV]': '#9FB4CC',
    '[ROTATION]': '#B48CFF',
    '[DATE]': '#F1D48A',
    '[TRAINING]': '#5BA8FF',
    '[RACE]': '#FF8FB8',
    '[SKILLS]': '#5ED39A',
    '[KNAPSACK]': '#F2B24C',
    '[DECISION]': '#9CC8FF',
    '[CAREER_END]': '#E8B84B',
    '[CONFIG_DRIFT]': '#FFB3A8',
    '[DECK_VALIDATION]': '#B48CFF',
    '[TRACKBLAZER]': '#5BA8FF',
  };
  var TAG_COLOR_DEFAULT = '#9FB4CC';
  var LEVEL_COLOR_OVERRIDE = { WARN: '#F1D48A', ERROR: '#FF7A6B' };

  var els = {};
  function byId(id) { return document.getElementById(id); }

  var state = {
    ws: null,
    authed: false,
    code: null,
    reconnectAttempt: 0,
    reconnectTimer: null,
    everConnected: false,
    statusReceivedAt: null,
    lastGoodStatus: null,
    lastGoodReceivedAt: null,
    connected: false,
    logLines: [],
    historyDone: false,
    logOpen: true,
    selectedRunN: null,
    lastAnnouncedAction: null,
    lastRenderedPhase: null,
    stopConfirming: false,
  };

  function setText(el, text) {
    if (!el) return;
    var next = text == null ? '' : String(text);
    if (el.textContent !== next) el.textContent = next;
  }
  function setHidden(el, hidden) {
    if (!el) return;
    if (hidden) el.setAttribute('hidden', ''); else el.removeAttribute('hidden');
  }
  function clearChildren(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }
  function el(tag, className, text) {
    var e = document.createElement(tag);
    if (className) e.className = className;
    if (text != null) e.textContent = text;
    return e;
  }

  // ---------------- access / auth ----------------

  function showAccess(errorText) {
    setHidden(els.access, false);
    setHidden(els.dashboard, true);
    setText(els.accessError, errorText || '');
    if (!errorText) els.code.focus();
  }
  function showDashboard() {
    setHidden(els.access, true);
    setHidden(els.dashboard, false);
  }

  function wsUrl() {
    var scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return scheme + '//' + window.location.host + WS_PATH;
  }

  // The remote log viewer's port is a player setting (default 9000, but
  // never guaranteed); the page knows its own origin, so it never hard-codes
  // a port. On the default port for the scheme, `location.port` is empty;
  // fall back to the scheme's own default rather than printing an empty
  // "tcp: tcp:".
  function currentPort() {
    if (window.location.port) return window.location.port;
    return window.location.protocol === 'https:' ? '443' : '80';
  }
  function fillAccessInstructions() {
    if (els.accessOpenHost) setText(els.accessOpenHost, window.location.host);
    if (els.accessAdbForward) {
      var port = currentPort();
      setText(els.accessAdbForward, 'adb forward tcp:' + port + ' tcp:' + port);
    }
  }

  function connect(code) {
    state.code = code;
    state.authed = false;
    var ws;
    try {
      ws = new WebSocket(wsUrl());
    } catch (e) {
      showAccess('Could not connect. Is the remote log viewer turned on?');
      return;
    }
    state.ws = ws;
    ws.onopen = function () {
      ws.send('AUTH:' + code);
    };
    ws.onmessage = function (ev) { handleFrame(ev.data); };
    ws.onclose = function (ev) {
      state.connected = false;
      if (!state.authed && ev.code === 4401) {
        showAccess('Wrong code. Check Settings, Debug, Remote Log Viewer for the current code.');
        return;
      }
      if (!state.authed && !state.everConnected) {
        showAccess('Could not connect. Is the remote log viewer turned on?');
        return;
      }
      renderAll();
      scheduleReconnect();
    };
    ws.onerror = function () {
      // onclose follows every onerror for a WebSocket; nothing to do here.
    };
  }

  function scheduleReconnect() {
    if (state.reconnectTimer) return;
    var delayMs = Math.min(15000, 1000 * Math.pow(2, state.reconnectAttempt));
    state.reconnectAttempt += 1;
    state.reconnectTimer = window.setTimeout(function () {
      state.reconnectTimer = null;
      connect(state.code);
    }, delayMs);
  }

  function handleFrame(raw) {
    if (raw === 'AUTH_OK') {
      state.authed = true;
      state.connected = true;
      state.everConnected = true;
      state.reconnectAttempt = 0;
      showDashboard();
      renderAll();
      return;
    }
    if (raw === 'HISTORY_DONE') {
      state.historyDone = true;
      renderLog();
      return;
    }
    if (raw === 'CMD:CLEAR') {
      state.logLines = [];
      renderLog();
      return;
    }
    if (raw.indexOf('HB:') === 0) {
      var batch;
      try { batch = JSON.parse(raw.slice(3)); } catch (e) { return; }
      if (Array.isArray(batch)) {
        state.logLines = batch.slice(-MAX_LOG_LINES);
        renderLog();
      }
      return;
    }
    var msg;
    try { msg = JSON.parse(raw); } catch (e) { return; }
    if (!msg || typeof msg !== 'object') return;
    if (msg.type === 'image' || msg.type === 'image_batch_done') return; // never requested, always ignored
    if (msg.type === 'status') {
      state.statusReceivedAt = Date.now();
      state.lastGoodStatus = msg;
      state.lastGoodReceivedAt = state.statusReceivedAt;
      renderAll();
      return;
    }
    // otherwise: a log line ({newline, timestamp, level, message, ...})
    if (typeof msg.message === 'string') {
      state.logLines.push(msg);
      if (state.logLines.length > MAX_LOG_LINES) state.logLines.splice(0, state.logLines.length - MAX_LOG_LINES);
      appendLogLine(msg);
    }
  }

  // ---------------- view model: apply logic.js's decisions to the DOM ----------------
  // Every phase/badge/banner/block-visibility decision lives in
  // RaceControlLogic.viewModel(); this file only ever applies its output.
  // See test/logic.test.js for the per-phase assertions (armed, live,
  // between, every terminal key, unknown, connecting, and disconnected
  // after each phase).

  function currentViewModel() {
    return L.viewModel(state.lastGoodStatus, {
      connected: state.connected,
      receivedAt: state.lastGoodReceivedAt,
      now: Date.now(),
    });
  }

  function applyViewModel(vm) {
    applyBanner(vm);
    applyBadge(vm.badge);
    applyBlocks(vm.blocks, state.lastGoodStatus);
  }

  function applyBadge(badge) {
    var toneClass = TONE_CLASS[badge.tone] || TONE_CLASS.neutral;
    els.connBadge.className = 'rc-badge ' + toneClass;
    setText(els.connBadgeText, badge.text);
    setHidden(els.lamp, !badge.lampLit);
    els.stateBadge.className = 'rc-badge ' + toneClass;
    setText(els.stateBadge, badge.text);

    var status = state.lastGoodStatus;
    if (status && status.run) {
      setHidden(els.runCounter, false);
      setText(els.runCounter, 'RUN ' + L.naText(status.run.current) + ' OF ' + L.naText(status.run.total));
    } else {
      setHidden(els.runCounter, true);
    }

    if (badge.text === 'CONNECTING') {
      setText(els.updatedMain, 'Connecting');
    } else if (badge.text === 'DISCONNECTED') {
      setText(els.updatedMain, 'Disconnected: ' + (currentBannerMeta || 'showing the last status'));
    } else if (state.lastGoodReceivedAt != null && status) {
      var ageMs = L.computeAgeMs(status.sentAt, status.sentAt, state.lastGoodReceivedAt, Date.now());
      setText(els.updatedMain, 'Updated ' + L.formatAge(ageMs));
    }
  }

  // Set by applyBanner, which always runs first, so the header's
  // "Disconnected: ..." text always agrees with the banner's own meta line
  // instead of formatting the same clock value twice.
  var currentBannerMeta = '';

  function applyBanner(vm) {
    var banner = vm.banner;
    setHidden(els.stateView, !banner.visible);
    currentBannerMeta = banner.meta;
    if (!banner.visible) return;

    setText(els.stateHeadline, banner.headline);
    setText(els.stateMeta, banner.meta);
    setText(els.stateBody, banner.body);

    setHidden(els.stateWhatToDo, banner.whatToDoText == null);
    if (banner.whatToDoText != null) setText(els.stateWhatToDoText, banner.whatToDoText);

    setHidden(els.stateStripe, !banner.stripe);
    if (banner.stripe) {
      clearChildren(els.stateStripe);
      banner.stripe.forEach(function (cell) { els.stateStripe.appendChild(stripeCell(cell.label, cell.value)); });
    }

    setHidden(els.stateDisconnectedFooter, vm.phase !== 'disconnected');
    if (vm.phase === 'disconnected') {
      setText(els.stateDisconnectedTitle, 'Disconnected' + (banner.meta ? (' · ' + banner.meta) : ''));
    }
  }

  function stripeCell(label, value) {
    var cell = el('div', 'rc-state-stripe-cell');
    cell.appendChild(el('span', 'rc-info-label', label));
    cell.appendChild(el('span', 'rc-info-value', value));
    return cell;
  }

  function applyBlocks(blocks, status) {
    var anyVisible = blocks.nowVisible || blocks.queueVisible || blocks.sessionVisible;
    setHidden(els.liveView, !anyVisible);
    els.liveView.classList.toggle('rc-dimmed', !!blocks.dimmed);

    setHidden(els.liveNowBlock, !blocks.nowVisible);
    if (blocks.nowVisible && status) renderNowContent(status);

    setHidden(els.queueBlock, !blocks.queueVisible);
    if (blocks.queueVisible && status) {
      renderRunRows(status);
      renderDetails(status);
    }

    setHidden(els.sessionBlock, !blocks.sessionVisible);
    if (blocks.sessionVisible && status) renderSession(status.session);
  }

  // ---------------- live view: NOW / STATS ----------------

  // Called only when the view model says the NOW block should be visible
  // (live or unknown: an unrecognised statusKey still shows the live data,
  // it just never earns the LIVE badge). career.action is published
  // independently of statusKey, so the hero always reflects it when this
  // block is showing, with no separate "is this really live" gate inside
  // here.
  function renderNowContent(status) {
    var career = status && status.career;
    if (!career) return;

    var action = career.action;
    setText(els.nowWord, action ? L.actionWord(action.kind) : 'WORKING');
    if (action && action.at != null) {
      setText(els.nowDecided, 'decided ' + L.formatAge(L.computeAgeMs(status.sentAt, action.at, state.statusReceivedAt, Date.now())));
    } else {
      setText(els.nowDecided, '');
    }
    if (action && action.detail) {
      setHidden(els.nowChip, false);
      setText(els.nowChip, String(action.detail).toUpperCase());
      var statColor = L.STAT_COLORS[String(action.detail).toUpperCase()];
      els.nowChip.style.background = statColor || L.ACCENT;
    } else {
      setHidden(els.nowChip, true);
    }
    announceAction(action);

    setText(els.nowSub, [career.trainee, career.scenario].filter(Boolean).map(function (v) { return v; }).join(' · ') || 'not available');

    var date = career.date;
    setText(els.infoDate, date ? [date.year, date.label].filter(Boolean).join(' · ') : 'not available');
    setText(els.infoTurn, date && Number.isFinite(date.turn)
      ? (career.course && Number.isFinite(career.course.finalTurn) ? (date.turn + ' of ' + career.course.finalTurn) : String(date.turn))
      : 'not available');
    var goal = career.goal;
    var turnsUntil = goal ? L.turnsUntilGoal(goal.dueTurn, date && date.turn) : null;
    setText(els.infoGoal, goal
      ? (goal.name != null
        ? (goal.name + (turnsUntil != null ? (' · in ' + turnsUntil + ' turns') : ''))
        : (turnsUntil != null ? ('in ' + turnsUntil + ' turns') : 'not available'))
      : 'not available');

    var energy = career.energy;
    setText(els.energyValue, energy && Number.isFinite(energy.percent)
      ? energy.percent + '%'
      : 'not available');
    clearChildren(els.energyCells);
    var energyLit = energy && Number.isFinite(energy.percent) ? Math.round((energy.percent / 100) * 20) : 0;
    L.buildCells(20, energyLit, '#5ED39A').forEach(function (c) { els.energyCells.appendChild(cellSpan(c.color)); });

    var mood = career.mood;
    setText(els.moodValue, mood ? L.naText(mood.level) : 'not available');
    var moodOrder = ['AWFUL', 'BAD', 'NORMAL', 'GOOD', 'GREAT'];
    var moodLit = mood ? Math.max(0, moodOrder.indexOf(mood.level) + 1) : 0;
    clearChildren(els.moodCells);
    L.buildCells(5, moodLit, L.ACCENT).forEach(function (c) { els.moodCells.appendChild(cellSpan(c.color)); });

    renderStats(career.stats);
    renderCourse(career, date, status);
  }

  // The NOW region is a polite live region. Write the action word and its
  // detail at most once per actual change, never per log line or per
  // 1-second tick.
  function announceAction(action) {
    var text = action ? (L.actionWord(action.kind) + (action.detail ? (': ' + String(action.detail).toUpperCase()) : '')) : '';
    if (text && text !== state.lastAnnouncedAction) {
      setText(els.liveRegion, text);
      state.lastAnnouncedAction = text;
    }
  }

  function cellSpan(color) {
    var s = el('span', 'rc-cell');
    s.style.background = color;
    return s;
  }

  function renderStats(stats) {
    clearChildren(els.statsRows);
    var rows = L.buildStatRows(stats, L.ACCENT);
    if (!rows.length) {
      els.statsRows.appendChild(el('div', 'rc-details-empty', 'not available'));
      return;
    }
    rows.forEach(function (r) {
      var row = el('div', 'rc-stat-row');
      var name = el('span', 'rc-stat-name', r.name);
      name.style.color = r.color;
      row.appendChild(name);
      var gradeEl = el('span', 'rc-stat-grade', r.grade == null ? '–' : r.grade);
      gradeEl.style.color = r.gradeColor;
      row.appendChild(gradeEl);
      var track = el('span', 'rc-stat-bar-track');
      var fill = el('span', 'rc-stat-bar-fill');
      fill.style.width = r.width;
      fill.style.background = r.color;
      track.appendChild(fill);
      row.appendChild(track);
      row.appendChild(el('span', 'rc-stat-value', r.value == null ? '–' : String(r.value)));
      els.statsRows.appendChild(row);
    });
  }

  // ---------------- course ----------------

  function renderCourse(career, date, status) {
    var racesRun = career.racesRun;
    var turnLabel = date && date.turn != null && career.course && career.course.finalTurn
      ? ('Turn ' + date.turn + ' of ' + career.course.finalTurn)
      : (date && date.turn != null ? ('Turn ' + date.turn) : 'Turn not available');
    clearChildren(els.courseMeta);
    els.courseMeta.appendChild(document.createTextNode(turnLabel));
    if (Number.isFinite(racesRun)) {
      els.courseMeta.appendChild(document.createTextNode(' · ' + racesRun + (racesRun === 1 ? ' race run' : ' races run')));
    }
    // The design's "last progress N s ago" accent, omitted when null.
    if (status && status.lastProgressAt != null && Number.isFinite(status.sentAt) && state.lastGoodReceivedAt != null) {
      var progressAge = L.computeAgeMs(status.sentAt, status.lastProgressAt, state.lastGoodReceivedAt, Date.now());
      if (progressAge != null) {
        els.courseMeta.appendChild(document.createTextNode(' · last progress '));
        els.courseMeta.appendChild(el('span', 'rc-course-meta-accent', L.formatAge(progressAge)));
      }
    }

    clearChildren(els.courseTrack);
    els.courseTrack.style.height = '';
    var goal = career.goal;
    var goalDueTurn = goal && Number.isFinite(goal.dueTurn) ? goal.dueTurn : null;
    var course = L.buildCourse(career.course, date && date.turn, goalDueTurn);
    els.courseTrack.setAttribute('aria-label', 'Career course' + (date && date.turn != null ? (': turn ' + date.turn) : '') + (career.course && career.course.finalTurn ? (' of ' + career.course.finalTurn) : ''));
    if (!course) {
      // Daily Races, Team Trials and an unread scenario send no `course`;
      // it must read as a normal, finished state, not a broken one.
      els.courseTrack.appendChild(el('div', 'rc-course-empty', 'No course map yet for this scenario. Date and turn are shown above.'));
      return;
    }

    course.years.forEach(function (y) {
      var label = el('span', 'rc-course-year-label', y.label);
      label.style.left = y.left;
      label.style.color = y.current ? '#EDF1F5' : '#7D8A99';
      els.courseTrack.appendChild(label);
    });
    els.courseTrack.appendChild(el('div', 'rc-course-lane'));
    if (course.runner) {
      var runnerZone = el('div', 'rc-course-runner-zone');
      runnerZone.style.width = course.runner;
      els.courseTrack.appendChild(runnerZone);
    }
    if (course.finale) {
      var finaleZone = el('div', 'rc-course-finale-zone');
      finaleZone.style.left = course.finale.left;
      els.courseTrack.appendChild(finaleZone);
    }
    els.courseTrack.appendChild(el('div', 'rc-course-topline'));
    els.courseTrack.appendChild(el('div', 'rc-course-ticks'));
    els.courseTrack.appendChild(el('div', 'rc-course-start-pole'));
    course.poles.forEach(function (p) {
      var pole = el('span', 'rc-course-pole');
      pole.style.left = p;
      els.courseTrack.appendChild(pole);
    });
    els.courseTrack.appendChild(el('div', 'rc-course-end-pole'));
    if (course.goal) {
      var goalPole = el('div', 'rc-course-goal-pole');
      goalPole.style.left = course.goal;
      els.courseTrack.appendChild(goalPole);
      var flagWrap = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      flagWrap.setAttribute('class', 'rc-course-goal-flag');
      flagWrap.setAttribute('width', '18');
      flagWrap.setAttribute('height', '13');
      flagWrap.setAttribute('viewBox', '0 0 18 13');
      flagWrap.setAttribute('aria-hidden', 'true');
      flagWrap.style.left = course.goal;
      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', 'M0 0H18L13 6.5L18 13H0Z');
      path.setAttribute('fill', L.ACCENT);
      flagWrap.appendChild(path);
      els.courseTrack.appendChild(flagWrap);
    }
    var runNumber = state.lastGoodStatus && state.lastGoodStatus.run && Number.isFinite(state.lastGoodStatus.run.current) ? state.lastGoodStatus.run.current : null;
    if (course.runner && runNumber != null) {
      var bracket = L.bracketColor(runNumber);
      var badge = el('div', 'rc-course-runner-badge', String(runNumber));
      badge.style.left = course.runner;
      badge.style.background = bracket.bg;
      badge.style.color = bracket.fg;
      badge.style.border = bracket.border;
      els.courseTrack.appendChild(badge);
    }
    els.courseTrack.appendChild(el('div', 'rc-course-furlongs'));
    if (course.goal && career.goal) {
      var goalLabel = el('div', 'rc-course-goal-label');
      goalLabel.style.left = course.goal;
      goalLabel.setAttribute('data-at', course.goal);
      // The goal name is only known once the bot has OCR'd it that turn; do
      // not print "not available" here, just show the turn on its own.
      if (career.goal.name != null) {
        goalLabel.appendChild(el('span', 'rc-course-goal-name', career.goal.name));
      }
      goalLabel.appendChild(el('span', 'rc-course-goal-turn', 'turn ' + goalDueTurn));
      els.courseTrack.appendChild(goalLabel);
    }
    if (course.finale) {
      var finaleLabel = el('div', 'rc-course-finale-label');
      finaleLabel.appendChild(el('span', 'rc-course-finale-turns', 'turns ' + course.finale.from + ' to ' + course.finale.to));
      finaleLabel.appendChild(el('span', 'rc-course-finale-text', course.finale.label));
      els.courseTrack.appendChild(finaleLabel);
    }
    placeCourseLabels();
  }

  // Whether goal and finale labels collide depends on goal turn, text width and track width, which CSS cannot see.
  function placeCourseLabels() {
    var track = els.courseTrack;
    var goal = track.querySelector('.rc-course-goal-label');
    var finale = track.querySelector('.rc-course-finale-label');
    var width = track.clientWidth;
    if (!width || (!goal && !finale)) return;
    var rowTop = 104;
    var goalLeft = 0;
    var bottom = rowTop;
    if (goal) {
      var goalWidth = goal.offsetWidth;
      goalLeft = Math.max(0, Math.min(width - goalWidth, parseFloat(goal.getAttribute('data-at')) / 100 * width - goalWidth / 2));
      goal.style.left = goalLeft + 'px';
      goal.style.transform = 'none';
      bottom = rowTop + goal.offsetHeight;
    }
    if (finale) {
      var below = goal && goalLeft + goal.offsetWidth + 12 > width - finale.offsetWidth;
      var finaleTop = below ? bottom + 6 : rowTop;
      finale.style.top = finaleTop + 'px';
      bottom = Math.max(bottom, finaleTop + finale.offsetHeight);
    }
    track.style.height = Math.max(128, bottom + 4) + 'px';
  }

  // ---------------- today's card / run details ----------------

  function renderRunRows(status) {
    clearChildren(els.runRows);
    var runs = Array.isArray(status.runs) ? status.runs : [];
    var run = status.run;
    setText(els.cardMeta, run ? (runFinishedCount(runs) + ' of ' + L.naText(run.total)) + ' finished' : '');
    if (state.selectedRunN == null || !runs.some(function (r) { return r.n === state.selectedRunN; })) {
      state.selectedRunN = (run && run.current) || (runs[0] && runs[0].n) || null;
    }
    if (!runs.length) {
      els.runRows.appendChild(el('div', 'rc-details-empty', 'not available'));
      return;
    }
    runs.forEach(function (r) {
      var isSelected = r.n === state.selectedRunN;
      var row = L.buildRunRow(r, isSelected, L.ACCENT);
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'rc-row rc-run-row';
      button.style.background = row.bg;
      button.setAttribute('aria-pressed', String(isSelected));
      button.setAttribute('aria-label', row.aria);
      button.addEventListener('click', function () {
        state.selectedRunN = r.n;
        renderAll();
      });
      var gate = el('span', 'rc-run-gate', String(row.n));
      gate.style.background = row.gateBg;
      gate.style.color = row.gateFg;
      gate.style.border = row.gateBorder;
      button.appendChild(gate);
      var traineeCol = el('div', 'rc-run-trainee-col');
      traineeCol.appendChild(el('span', 'rc-run-trainee', row.trainee));
      traineeCol.appendChild(el('span', 'rc-run-scenario', row.scenario));
      button.appendChild(traineeCol);
      var result = el('span', 'rc-run-result', row.result);
      result.style.color = row.resultColor;
      button.appendChild(result);
      button.appendChild(el('span', 'rc-run-score', row.score == null ? '' : row.score));
      button.appendChild(el('span', 'rc-run-fans', row.fans == null ? '' : row.fans));
      var chevronWrap = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      chevronWrap.setAttribute('class', 'rc-run-chevron');
      chevronWrap.setAttribute('width', '16');
      chevronWrap.setAttribute('height', '16');
      chevronWrap.setAttribute('viewBox', '0 0 24 24');
      chevronWrap.setAttribute('fill', 'none');
      chevronWrap.setAttribute('stroke', row.chevron);
      chevronWrap.setAttribute('stroke-width', '2.4');
      chevronWrap.setAttribute('stroke-linecap', 'round');
      chevronWrap.setAttribute('stroke-linejoin', 'round');
      chevronWrap.setAttribute('aria-hidden', 'true');
      var chevronPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      chevronPath.setAttribute('d', 'M9 5l7 7-7 7');
      chevronWrap.appendChild(chevronPath);
      button.appendChild(chevronWrap);
      els.runRows.appendChild(button);
    });
  }
  function runFinishedCount(runs) {
    return runs.filter(function (r) { return r.state === 'done'; }).length;
  }

  function renderDetails(status) {
    var runs = Array.isArray(status.runs) ? status.runs : [];
    var run = runs.filter(function (r) { return r.n === state.selectedRunN; })[0];
    clearChildren(els.detailsBody);
    setText(els.detailsTitle, state.selectedRunN != null ? ('RUN ' + state.selectedRunN) : 'RUN DETAILS');
    if (!run) {
      setText(els.detailsWhen, '');
      els.detailsBody.appendChild(el('div', 'rc-details-empty', 'not available'));
      return;
    }
    if (run.state === 'done') {
      setText(els.detailsWhen, run.startedAt != null && run.endedAt != null
        ? (L.formatClock(run.startedAt) + ' to ' + L.formatClock(run.endedAt))
        : '');
      var body = el('div', 'rc-details-body');
      var top = el('div', 'rc-details-top');
      var rankBadge = el('div', 'rc-details-rank-badge');
      var rankInner = el('div', 'rc-details-rank-inner');
      rankInner.appendChild(el('span', 'rc-details-rank-label', 'EST. RANK'));
      rankInner.appendChild(el('span', 'rc-details-rank-value', run.rank != null ? run.rank : '–'));
      rankBadge.appendChild(rankInner);
      top.appendChild(rankBadge);
      var textCol = el('div');
      textCol.style.display = 'flex';
      textCol.style.flexDirection = 'column';
      textCol.style.gap = '6px';
      textCol.style.minWidth = '0';
      textCol.appendChild(el('div', 'rc-details-trainee', L.naText(run.trainee)));
      textCol.appendChild(el('div', 'rc-details-outcome', run.outcome === 'FORCE_END' ? 'Ended early: a goal was missed' : 'Career finished'));
      top.appendChild(textCol);
      body.appendChild(top);

      var strip = el('div', 'rc-details-strip');
      strip.appendChild(detailsStripCell('EST. SCORE', L.naText(run.estScore, L.formatNumber(run.estScore))));
      strip.appendChild(detailsStripCell('FANS', L.naText(run.fans, L.formatNumber(run.fans))));
      strip.appendChild(detailsStripCell('FINALE WINS', L.describeFinale(run.finale) || 'not available'));
      body.appendChild(strip);

      var finalsBlock = el('div', 'rc-details-finals-head');
      finalsBlock.appendChild(el('span', 'rc-info-label', 'FINAL STATS'));
      var finalsGrid = el('div', 'rc-details-finals-grid');
      L.buildStatRows(run.finalStats, L.ACCENT, run.lastKnownStats).forEach(function (r) {
        var col = el('div', 'rc-details-final-col');
        col.style.borderTopColor = r.color;
        var name = el('span', 'rc-details-final-name', r.name);
        name.style.color = r.color;
        col.appendChild(name);
        col.appendChild(el('span', 'rc-details-final-value', r.value == null ? '–' : String(r.value)));
        var gradeEl = el('span', 'rc-details-final-grade', r.grade == null ? '–' : r.grade);
        gradeEl.style.color = r.gradeColor;
        col.appendChild(gradeEl);
        if (r.lastKnown) col.appendChild(el('span', 'rc-details-final-mark', 'earlier read'));
        finalsGrid.appendChild(col);
      });
      finalsBlock.appendChild(finalsGrid);
      body.appendChild(finalsBlock);

      var sparksBlock = el('div', 'rc-sparks-block');
      var sparksHead = el('div', 'rc-sparks-head');
      sparksHead.appendChild(el('span', 'rc-info-label', 'SPARKS KEPT'));
      sparksHead.appendChild(el('span', 'rc-sparks-note', run.sparksNote || ''));
      sparksBlock.appendChild(sparksHead);
      (run.sparks || []).forEach(function (spark) {
        var row = el('div', 'rc-spark-row');
        var diamond = el('span', 'rc-spark-diamond');
        diamond.style.background = L.sparkColor(spark.type);
        row.appendChild(diamond);
        row.appendChild(el('span', 'rc-spark-name', spark.name));
        row.appendChild(el('span', 'rc-spark-kind', spark.type));
        var starsWrap = el('span', 'rc-spark-stars');
        starsWrap.setAttribute('aria-label', spark.stars + (spark.stars === 1 ? ' star' : ' stars'));
        for (var i = 0; i < 3; i++) {
          var starSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
          starSvg.setAttribute('width', '14');
          starSvg.setAttribute('height', '14');
          starSvg.setAttribute('viewBox', '0 0 24 24');
          starSvg.setAttribute('aria-hidden', 'true');
          var starPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
          starPath.setAttribute('d', 'M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z');
          starPath.setAttribute('fill', i < spark.stars ? L.ACCENT : '#2A3544');
          starSvg.appendChild(starPath);
          starsWrap.appendChild(starSvg);
        }
        row.appendChild(starsWrap);
        sparksBlock.appendChild(row);
      });
      body.appendChild(sparksBlock);
      els.detailsBody.appendChild(body);
    } else if (run.state === 'stopped' || run.state === 'errored') {
      setText(els.detailsWhen, run.startedAt != null ? ('started ' + L.formatClock(run.startedAt)) : '');
      var failed = el('div', 'rc-pending-body');
      failed.appendChild(el('div', 'rc-details-trainee', L.naText(run.trainee)));
      var failedLabel = el('div', 'rc-run-result', run.state === 'stopped' ? 'STOPPED' : 'ERROR');
      failedLabel.style.color = run.state === 'stopped' ? '#FFB3A8' : '#FF7A6B';
      failed.appendChild(failedLabel);
      failed.appendChild(el('div', 'rc-pending-outcome', (run.words && run.words.reason) || 'not available'));
      els.detailsBody.appendChild(failed);
    } else {
      setText(els.detailsWhen, L.pendingRunWhen(run));
      var pending = el('div', 'rc-pending-body');
      pending.appendChild(el('div', 'rc-details-trainee', L.naText(run.trainee)));
      pending.appendChild(el('div', 'rc-pending-outcome', 'Its result appears here when the career ends.'));
      els.detailsBody.appendChild(pending);
    }
  }
  function detailsStripCell(label, value) {
    var cell = el('div', 'rc-details-strip-cell');
    cell.appendChild(el('span', 'rc-info-label', label));
    cell.appendChild(el('span', 'rc-details-strip-value', value));
    return cell;
  }

  function renderSession(session) {
    // A null/absent tpRestores or recoveries, or a non-finite total, shows
    // "not available", never a guessed 0 or the literal text "null".
    var tpTotal = session ? L.totalTpRestores(session.tpRestores) : null;
    setText(els.sessionTp, L.naText(tpTotal, String(tpTotal)));
    setText(els.sessionTpSub, session ? (L.describeTpRestores(session.tpRestores) || '') : '');
    var recTotal = session ? L.totalRecoveries(session.recoveries) : null;
    setText(els.sessionRec, L.naText(recTotal, String(recTotal)));
    setText(els.sessionRecSub, session ? (L.describeRecoveries(session.recoveries) || '') : '');
    setText(els.sessionTime, session && session.startedAt != null ? L.formatDuration(Date.now() - session.startedAt) : 'not available');
    setText(els.sessionTimeSub, session && session.startedAt != null ? ('started ' + L.formatClock(session.startedAt)) : '');
    var run = state.lastGoodStatus && state.lastGoodStatus.run;
    setText(els.sessionNext, run && Number.isFinite(run.current) && Number.isFinite(run.total) && run.current < run.total ? ('Run ' + (run.current + 1)) : 'not available');
  }

  // ---------------- log ----------------

  // Splits a leading "[TAG] " off the message so the tag shows once (in its
  // own coloured column), not twice: the design's log rows never repeat it
  // inline in the text column.
  function splitTagAndText(message) {
    var match = /^\s*(\[[A-Z_]+\])\s*(.*)$/.exec(message || '');
    return match ? { tag: match[1], text: match[2] } : { tag: null, text: message || '' };
  }
  function colorForLine(entry, tag) {
    if (entry.level && LEVEL_COLOR_OVERRIDE[entry.level]) return LEVEL_COLOR_OVERRIDE[entry.level];
    if (tag && TAG_COLORS[tag]) return TAG_COLORS[tag];
    return TAG_COLOR_DEFAULT;
  }

  function appendLogLine(entry) {
    if (els.logLines.children.length >= MAX_LOG_LINES) {
      els.logLines.removeChild(els.logLines.firstChild);
    }
    els.logLines.appendChild(logLineNode(entry));
    els.logLines.scrollTop = els.logLines.scrollHeight;
  }
  function logLineNode(entry) {
    var split = splitTagAndText(entry.message);
    var row = el('div', 'rc-log-line');
    row.appendChild(el('span', 'rc-log-time', entry.timestamp || ''));
    var tagEl = el('span', 'rc-log-tag', split.tag || entry.level || '');
    tagEl.style.color = colorForLine(entry, split.tag);
    row.appendChild(tagEl);
    row.appendChild(el('span', 'rc-log-text', split.text));
    return row;
  }
  function renderLog() {
    clearChildren(els.logLines);
    if (!state.logLines.length) {
      els.logLines.appendChild(el('div', 'rc-log-empty', state.historyDone ? 'No log lines yet.' : 'Loading history...'));
      return;
    }
    state.logLines.forEach(function (entry) { els.logLines.appendChild(logLineNode(entry)); });
    els.logLines.scrollTop = els.logLines.scrollHeight;
  }

  function toggleLog() {
    state.logOpen = !state.logOpen;
    setHidden(els.logLines, !state.logOpen);
    setText(els.logToggle, state.logOpen ? 'Hide' : 'Show');
    els.logToggle.setAttribute('aria-expanded', String(state.logOpen));
  }

  // ---------------- top-level render ----------------

  function renderAll() {
    var vm = currentViewModel();
    applyViewModel(vm);
    state.lastRenderedPhase = vm.phase;
    renderStopAfterCareer();
  }

  // ---------------- stop after this career ----------------

  function stopAfterCareerView() {
    return L.stopAfterCareerView(state.lastGoodStatus, state.connected, state.stopConfirming);
  }

  function renderStopAfterCareer() {
    var view = stopAfterCareerView();
    if (!view.visible || view.requested) state.stopConfirming = false;
    els.stopAfter.hidden = !view.visible;
    setText(els.stopAfterButton, view.buttonText);
    els.stopAfterButton.setAttribute('aria-label', view.buttonLabel);
    els.stopAfterButton.hidden = view.confirmVisible;
    els.stopAfterConfirm.hidden = !view.confirmVisible;
    setText(els.stopAfterNote, view.note);
  }

  // Only on the authenticated socket; the next STATUS shows what happened.
  function sendCommand(command) {
    if (state.ws && state.authed && state.connected) state.ws.send(command);
  }

  function tick() {
    // Cheap: refresh only the age-dependent badge/banner text every second;
    // rebuilding the stat rows/course track/run rows this often would be
    // real DOM churn for no visual change (STATUS pushes, which is when
    // renderAll runs the full pipeline, arrive at most once a second).
    var vm = currentViewModel();
    applyBanner(vm);
    applyBadge(vm.badge);
    // A phase transition that happens between STATUS pushes (the reconnect
    // backoff completing, or a drop) still needs the full layout to catch up.
    if (vm.phase !== state.lastRenderedPhase) {
      applyBlocks(vm.blocks, state.lastGoodStatus);
      state.lastRenderedPhase = vm.phase;
    }
  }

  // ---------------- boot ----------------

  function boot() {
    els.access = byId('rc-access');
    els.accessForm = byId('rc-access-form');
    els.accessError = byId('rc-access-error');
    els.accessOpenHost = byId('rc-access-open-host');
    els.accessAdbForward = byId('rc-access-adb-forward');
    els.code = byId('rc-code');
    els.dashboard = byId('rc-dashboard');
    els.connBadge = byId('rc-conn-badge');
    els.connBadgeText = byId('rc-conn-badge-text');
    els.lamp = byId('rc-lamp');
    els.runCounter = byId('rc-run-counter');
    els.updatedMain = byId('rc-updated-main');
    els.stateView = byId('rc-state-view');
    els.stateBadge = byId('rc-state-badge');
    els.stateMeta = byId('rc-state-meta');
    els.stateHeadline = byId('rc-state-headline');
    els.stateBody = byId('rc-state-body');
    els.stateStripe = byId('rc-state-stripe');
    els.stateWhatToDo = byId('rc-state-whattodo');
    els.stateWhatToDoText = byId('rc-state-whattodo-text');
    els.stateDisconnectedFooter = byId('rc-state-disconnected-footer');
    els.stateDisconnectedTitle = byId('rc-state-disconnected-title');
    els.liveRegion = byId('rc-live-region');
    els.liveView = byId('rc-live-view');
    els.liveNowBlock = byId('rc-live-now-block');
    els.queueBlock = byId('rc-queue-block');
    els.sessionBlock = byId('rc-session-block');
    els.nowWord = byId('rc-now-word');
    els.nowChip = byId('rc-now-chip');
    els.nowDecided = byId('rc-now-decided');
    els.nowSub = byId('rc-now-sub');
    els.infoDate = byId('rc-info-date');
    els.infoTurn = byId('rc-info-turn');
    els.infoGoal = byId('rc-info-goal');
    els.energyValue = byId('rc-energy-value');
    els.energyCells = byId('rc-energy-cells');
    els.moodValue = byId('rc-mood-value');
    els.moodCells = byId('rc-mood-cells');
    els.statsRows = byId('rc-stats-rows');
    els.courseMeta = byId('rc-course-meta');
    els.courseTrack = byId('rc-course-track');
    els.cardMeta = byId('rc-card-meta');
    els.runRows = byId('rc-run-rows');
    els.detailsTitle = byId('rc-details-title');
    els.detailsWhen = byId('rc-details-when');
    els.detailsBody = byId('rc-details-body');
    els.sessionTp = byId('rc-session-tp');
    els.sessionTpSub = byId('rc-session-tp-sub');
    els.sessionRec = byId('rc-session-rec');
    els.sessionRecSub = byId('rc-session-rec-sub');
    els.sessionTime = byId('rc-session-time');
    els.sessionTimeSub = byId('rc-session-time-sub');
    els.sessionNext = byId('rc-session-next');
    els.stopAfter = byId('rc-stop-after');
    els.stopAfterButton = byId('rc-stop-after-button');
    els.stopAfterConfirm = byId('rc-stop-after-confirm');
    els.stopAfterYes = byId('rc-stop-after-yes');
    els.stopAfterNo = byId('rc-stop-after-no');
    els.stopAfterNote = byId('rc-stop-after-note');
    els.logToggle = byId('rc-log-toggle');
    els.logLines = byId('rc-log-lines');

    els.accessForm.addEventListener('submit', function (ev) {
      ev.preventDefault();
      var code = els.code.value.trim();
      if (!code) return;
      setText(els.accessError, '');
      connect(code);
    });
    els.logToggle.addEventListener('click', toggleLog);
    els.stopAfterButton.addEventListener('click', function () {
      var view = stopAfterCareerView();
      if (view.buttonCommand) sendCommand(view.buttonCommand);
      else state.stopConfirming = true;
      renderStopAfterCareer();
    });
    els.stopAfterYes.addEventListener('click', function () {
      state.stopConfirming = false;
      sendCommand(L.STOP_AFTER_CAREER_COMMAND);
      renderStopAfterCareer();
    });
    els.stopAfterNo.addEventListener('click', function () {
      state.stopConfirming = false;
      renderStopAfterCareer();
    });

    // The page scrollbar appearing after the first render also changes the track width.
    if (window.ResizeObserver) new ResizeObserver(function () { requestAnimationFrame(placeCourseLabels); }).observe(els.courseTrack);
    if (document.fonts) document.fonts.addEventListener('loadingdone', placeCourseLabels);

    fillAccessInstructions();
    showAccess();
    window.setInterval(tick, 1000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
