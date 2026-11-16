// Pantheon: the agentic remote's panel, "what should I watch?", for every theme.
//
// Left: how much time you have, movie or series, genre pills, and "something like…" (type it, or say it to the mic).
// Right: the picks, as cards: the poster, what it is, where it streams for you, and one line on why. OK opens it.
// The TV app's agent does the work (android/.../agent/: TMDB finds, Jev decides, DeepSeek explains) and answers
// through pantheonCouncil.onPicks / onReasons / onError. Each theme names the panel its own way.
(function () {
  const WORDS = {
    got:      { icon: "Small Council", title: "The Small Council", sub: "What shall we watch, Your Grace?", ask: "Ask the Council",
                wait: "The ravens are flying…", none: "The Council awaits your question", like: "Something like…" },
    hogwarts: { icon: "Sorting Hat", title: "The Sorting Hat", sub: "What shall we watch tonight?", ask: "Ask the Hat",
                wait: "The Hat is thinking…", none: "The Hat awaits", like: "Something like…" },
    _:        { icon: "What to watch", title: "What to watch", sub: "Tell me what you're in the mood for", ask: "Find something",
                wait: "Looking…", none: "Your picks will appear here", like: "Something like…" },
  };
  const TIMES = [0, 20, 30, 45, 60, 90, 120, 180];           // minutes (0: any length)
  const KINDS = [["any", "Either"], ["movie", "Movie"], ["tv", "Series"]];
  const GENRES = ["Thriller", "Comedy", "Drama", "Sci-fi", "Fantasy", "Crime", "Mystery", "Action", "Romance", "Horror",
                  "Documentary", "Animation", "Family", "History"];
  const GCOLS = 3;
  const words = () => WORDS[document.body.dataset.theme] || WORDS._;
  // Hogwarts marks a choice with a Golden Snitch (drawn here, original): faint and still until chosen, then gold, wings beating
  const WING = "M19 11.5C15 5.5 7.5 2.5 1 3.5c2.6 1.6 4.4 3 5.4 4.3C4 8 2.6 8.8 1.8 10c3 .2 5.4.9 7 2 2.8.3 6.4.6 10.2 1z";
  const RIBS = "M18 11.6C13 8 8 5.5 3.5 4.6M17.4 12.2c-3.2-1.6-6.6-2.6-10-3";
  const SNITCH = `<svg class="snitch" viewBox="0 0 48 24" aria-hidden="true">
    <g class="wing l"><path class="vane" d="${WING}"/><path class="rib" d="${RIBS}"/></g>
    <g class="wing r" transform="translate(48 0) scale(-1 1)"><g class="beat"><path class="vane" d="${WING}"/><path class="rib" d="${RIBS}"/></g></g>
    <circle class="ball" cx="24" cy="13" r="6.4"/><path class="etch" d="M18.2 12.2c3.6 1.6 8 1.6 11.6 0M24 6.6c-2 3.6-2 9.2 0 12.8"/>
    <circle class="shine" cx="21.8" cy="10.6" r="1.7"/></svg>`;
  const esc = s => String(s || "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

  const C = window.pantheonCouncil = {
    open: false, el: null, zone: 0, gi: 0, ri: 0, busy: false, picks: [], reasons: {},
    st: { t: 3, kind: 0, genres: [], like: "" },

    build() {
      const s = document.createElement("style");
      s.textContent = `
        #council { position: absolute; z-index: 45; inset: 0; display: flex; gap: 70px; padding: 70px 112px 56px; box-sizing: border-box;
                   background: radial-gradient(ellipse at 30% 20%, var(--panel-1, #241812), var(--panel-2, #0b0806) 80%);
                   opacity: 0; pointer-events: none; transition: opacity .3s; color: var(--ink); }
        #council.open { opacity: 1; }
        #council .form { width: 640px; flex: none; display: flex; flex-direction: column; }
        #council .h { font: 600 50px/1.05 "Cinzel", Georgia, serif; color: var(--gold-hi); }
        body:not([data-theme="got"]) #council .h { font-family: inherit; font-weight: 400; }
        #council .sub { margin: 10px 0 22px; font-size: 22px; letter-spacing: .06em; opacity: .7; font-style: italic; }
        #council .lab { font: 700 14px/1 "Cinzel", Georgia, serif; letter-spacing: .3em; text-transform: uppercase; color: var(--gold-hi); opacity: .8; margin: 0 0 14px; }
        #council .zone { margin-bottom: 16px; padding: 12px 18px; margin-left: -18px; border-radius: 14px; border: 2px solid transparent; transition: border-color .2s, background .2s; }
        #council .zone.foc { border-color: rgba(233,201,138,.55); background: rgba(255,255,255,.04); }
        #council .slider { position: relative; height: 34px; }
        #council .track { position: absolute; left: 0; right: 0; top: 15px; height: 4px; border-radius: 2px; background: rgba(255,255,255,.18); }
        #council .fill { position: absolute; left: 0; top: 15px; height: 4px; border-radius: 2px; background: var(--gold-hi); }
        #council .knob { position: absolute; top: 4px; width: 26px; height: 26px; margin-left: -13px; border-radius: 50%; background: var(--gold-hi);
                         box-shadow: 0 0 0 5px rgba(233,201,138,.25), 0 3px 10px rgba(0,0,0,.6); }
        #council .tval { font-size: 30px; margin-top: 10px; }
        /* choices as wax seals: an empty ring, filled with red wax when chosen; the name beside it, never inside a box */
        #council .pills { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px 10px; }
        #council .kinds { grid-template-columns: repeat(3, max-content); gap: 14px 40px; }
        #council .pill { display: flex; align-items: center; gap: 14px; min-width: 0; padding: 4px 0; color: var(--ink); opacity: .78;
                         font: 600 16.5px/1.2 "Cinzel", Georgia, serif; letter-spacing: .04em; transition: opacity .15s, color .15s; }
        #council .pill .seal { flex: none; position: relative; width: 26px; height: 26px; border-radius: 50%;
                               border: 1.5px solid rgba(233,201,138,.55); box-sizing: border-box; transition: transform .2s, box-shadow .2s; }
        #council .pill .lbl { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        #council .pill.on { opacity: 1; }
        #council .pill.on .seal { border: none; transform: scale(1.12);
          background: radial-gradient(circle at 38% 32%, #d4504a 0%, #9d1f1c 46%, #5e0d0c 100%);
          box-shadow: 0 0 0 2px #c9a45c, 0 0 0 4px rgba(0,0,0,.45), 0 3px 8px rgba(0,0,0,.6), inset 0 -2px 4px rgba(0,0,0,.45), 0 0 14px rgba(196,40,36,.45); }
        #council .pill.on .seal::after { content: ""; position: absolute; inset: 7px; border-radius: 50%; border: 1.5px solid rgba(255,214,190,.35); }
        #council .pill.cur { opacity: 1; color: var(--gold-hi); }
        #council .pill.cur .lbl { text-decoration: underline; text-decoration-color: rgba(233,201,138,.6); text-underline-offset: 6px; }
        #council .pill.cur .seal { box-shadow: 0 0 0 3px #fff8e6, 0 0 16px rgba(255,240,200,.5); }
        #council .pill.on.cur .seal { box-shadow: 0 0 0 2px #c9a45c, 0 0 0 4px #fff8e6, 0 0 18px rgba(255,220,180,.6); }
        /* Game of Thrones: a real wax seal (themes/got/img/seal.webp): a faint grey impression until chosen, then red wax */
        body[data-theme="got"] #council .pill .seal { width: 36px; height: 36px; border: none; border-radius: 0; box-shadow: none;
          background: url(themes/got/img/seal.webp) center / contain no-repeat; filter: grayscale(1) brightness(1.5) contrast(.8); opacity: .32; }
        body[data-theme="got"] #council .pill.on .seal { filter: drop-shadow(0 3px 4px rgba(0,0,0,.7)); opacity: 1; transform: scale(1.18) rotate(-8deg); background-color: transparent; box-shadow: none; }
        body[data-theme="got"] #council .pill.on .seal::after { display: none; }
        body[data-theme="got"] #council .pill.cur .seal { box-shadow: none; opacity: .65; }
        #council .snitch { display: none; }
        /* Hogwarts: a Golden Snitch instead of a seal */
        body[data-theme="hogwarts"] #council .pill .seal { width: 46px; height: 23px; border: none; border-radius: 0; background: none; box-shadow: none; transform: none; }
        body[data-theme="hogwarts"] #council .pill .seal::after { display: none; }
        body[data-theme="hogwarts"] #council .pill.on .seal, body[data-theme="hogwarts"] #council .pill.cur .seal,
        body[data-theme="hogwarts"] #council .pill.on.cur .seal { box-shadow: none; transform: none; background: none; }
        body[data-theme="hogwarts"] #council .snitch { display: block; width: 100%; height: 100%; overflow: visible; opacity: .42; transition: opacity .2s, filter .2s; }
        body[data-theme="hogwarts"] #council .snitch .vane { fill: rgba(240,232,214,.55); stroke: #cdbf9e; stroke-width: .7; }
        body[data-theme="hogwarts"] #council .snitch .rib { fill: none; stroke: #b9a983; stroke-width: .5; }
        body[data-theme="hogwarts"] #council .snitch .ball { fill: #9c9284; stroke: #6d6458; stroke-width: .6; }
        body[data-theme="hogwarts"] #council .snitch .etch { fill: none; stroke: rgba(60,50,30,.55); stroke-width: .55; }
        body[data-theme="hogwarts"] #council .snitch .shine { fill: rgba(255,255,255,.35); }
        body[data-theme="hogwarts"] #council .pill.cur .snitch { opacity: .7; }
        body[data-theme="hogwarts"] #council .pill.on .snitch { opacity: 1; filter: drop-shadow(0 0 5px rgba(255,205,90,.75)) drop-shadow(0 2px 3px rgba(0,0,0,.6));
                                                                animation: snitchHover 1.6s ease-in-out infinite; }
        body[data-theme="hogwarts"] #council .pill.on .snitch .vane { fill: rgba(250,246,234,.92); stroke: #e8cf8a; }
        body[data-theme="hogwarts"] #council .pill.on .snitch .ball { fill: #e0ac35; stroke: #8a5f12; }
        body[data-theme="hogwarts"] #council .pill.on .snitch .shine { fill: #fff3c4; }
        body[data-theme="hogwarts"] #council .pill.on .snitch .wing.l,
        body[data-theme="hogwarts"] #council .pill.on .snitch .beat { transform-origin: 19px 12px; animation: snitchBeat .16s ease-in-out infinite alternate; }
        body[data-theme="hogwarts"] #council .pill.on.cur .snitch { filter: drop-shadow(0 0 9px rgba(255,220,120,.95)) drop-shadow(0 2px 3px rgba(0,0,0,.6)); }
        @keyframes snitchBeat { from { transform: rotate(14deg) scaleY(.82); } to { transform: rotate(-12deg) scaleY(1); } }
        @keyframes snitchHover { 0%, 100% { transform: translateY(1px); } 50% { transform: translateY(-2px); } }
        body[data-theme="got"] #council .pill.on.cur .seal { box-shadow: none; opacity: 1; filter: drop-shadow(0 0 8px rgba(255,214,160,.75)) drop-shadow(0 3px 4px rgba(0,0,0,.7)); }
        #council input { width: 100%; box-sizing: border-box; font: inherit; font-size: 24px; padding: 14px 18px; border-radius: 12px; outline: none;
                         background: rgba(0,0,0,.35); color: var(--ink); border: 1.5px solid rgba(233,201,138,.35); }
        #council input::placeholder { color: var(--ink); opacity: .4; font-style: italic; }
        #council .ask { align-self: flex-start; margin-top: 4px; padding: 16px 40px; border-radius: 34px; font: 700 20px/1 "Cinzel", Georgia, serif; letter-spacing: .14em;
                        text-transform: uppercase; background: linear-gradient(180deg, #e9c98a, #b98f4e); color: #1a0f06; box-shadow: 0 6px 20px rgba(0,0,0,.5); }
        #council .zone.foc .ask { box-shadow: 0 0 0 4px #fff8e6, 0 6px 26px rgba(233,201,138,.4); transform: scale(1.05); }
        #council .res { flex: 1; min-width: 0; display: flex; flex-direction: column; }
        #council .msg { margin: auto; font-size: 30px; font-style: italic; opacity: .55; text-align: center; }
        #council .msg.busy::after { content: ""; display: inline-block; width: 1.2em; text-align: left; animation: cdots 1.2s steps(4) infinite; }
        @keyframes cdots { 0% { content: ""; } 25% { content: "."; } 50% { content: ".."; } 75% { content: "..."; } }
        #council .sum { display: none; font-size: 17px; letter-spacing: .04em; opacity: .5; margin: 4px 0 20px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        #council .grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 26px 28px; margin-top: 8px; }
        #council .card { display: flex; flex-direction: column; min-width: 0; border-radius: 12px; transition: transform .2s; transform-origin: 50% 30%; }
        #council .poster { width: 100%; aspect-ratio: 2 / 3; max-height: 300px; border-radius: 10px; background: #1a1410 center / cover no-repeat;
                           box-shadow: 0 8px 24px rgba(0,0,0,.55); border: 2px solid rgba(255,255,255,.08); }
        #council .card.cur { transform: scale(1.07); z-index: 2; }
        #council .card.cur .poster { border-color: var(--gold-hi); box-shadow: 0 0 0 3px var(--gold-hi), 0 14px 40px rgba(0,0,0,.6); }
        #council .ct { margin-top: 10px; font-size: 19px; line-height: 1.2; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        #council .cm { margin-top: 4px; font-size: 13.5px; letter-spacing: .06em; opacity: .55; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        #council .cw { margin-top: 6px; font-size: 15px; line-height: 1.3; font-style: italic; opacity: .85; min-height: 2.6em;
                       display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; transition: opacity .6s; }
        #council .cw:empty { opacity: 0; }
        #council .hint { margin-top: auto; padding-top: 18px; font-size: 16px; letter-spacing: .18em; text-transform: uppercase; opacity: .4; }`;
      document.head.appendChild(s);
      const el = this.el = document.createElement("div"); el.id = "council";
      el.innerHTML = `<div class="form">
          <div class="h"></div><div class="sub"></div>
          <div class="zone z0"><div class="lab">Time you have</div><div class="slider"><div class="track"></div><div class="fill"></div><div class="knob"></div></div><div class="tval"></div></div>
          <div class="zone z1"><div class="lab">Movie or series</div><div class="pills kinds">${KINDS.map(k => `<div class="pill"><span class="seal">${SNITCH}</span><span class="lbl">${k[1]}</span></div>`).join("")}</div></div>
          <div class="zone z2"><div class="lab">In the mood for</div><div class="pills genres">${GENRES.map(g => `<div class="pill"><span class="seal">${SNITCH}</span><span class="lbl">${g}</span></div>`).join("")}</div></div>
          <div class="zone z3"><div class="lab like"></div><input type="text" placeholder="like Severance, but funnier"></div>
          <div class="zone z4"><div class="ask"></div></div>
        </div>
        <div class="res"><div class="sum"></div><div class="grid"></div><div class="msg"></div>
          <div class="hint">↑ ↓ ← → move · OK choose · Back close · data: TMDB</div></div>`;
      document.getElementById("stage").appendChild(el);
      this.input = el.querySelector("input");
      this.input.addEventListener("keydown", e => {                // typing: Enter asks, ↓ leaves the field
        if (e.key === "Enter") { e.preventDefault(); e.stopPropagation(); this.input.blur(); this.st.like = this.input.value; this.ask(); }
        else if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Escape") { e.preventDefault(); e.stopPropagation(); this.input.blur(); this.st.like = this.input.value;
          this.zone = e.key === "ArrowUp" ? 2 : 4; this.render(); }
        else e.stopPropagation();
      });
      try { Object.assign(this.st, JSON.parse(localStorage.getItem("pantheon_council") || "{}")); } catch (e) { }
    },

    show() {
      if (!this.el) this.build();
      if (typeof setGear === "function") setGear(false);
      const w = words();
      this.el.querySelector(".h").textContent = w.title;
      this.el.querySelector(".sub").textContent = w.sub;
      this.el.querySelector(".lab.like").textContent = w.like;
      this.el.querySelector(".ask").textContent = w.ask;
      this.input.value = this.st.like || "";
      this.open = true; this.el.classList.add("open"); document.body.classList.add("council-open");
      if (!this.picks.length && !this.busy) this.message(w.none, false);
      this.render();
    },
    close() {
      this.open = false; if (this.el) this.el.classList.remove("open"); document.body.classList.remove("council-open");
      if (this.input) this.input.blur();
      try { localStorage.setItem("pantheon_council", JSON.stringify(this.st)); } catch (e) { }
    },

    // the agent's answers
    onPicks(r) {
      this.busy = false; this.picks = (r && r.picks) || []; this.reasons = {}; this.ri = 0;
      this.el.querySelector(".sum").textContent = r && r.summary ? r.summary : "";
      this.message("", false); this.drawCards();
      if (this.picks.length && this.zone === 4) this.zone = 5;
      this.render();
    },
    onReasons(r) {
      this.reasons = r || {};
      this.el.querySelectorAll(".card").forEach((c, i) => { const p = this.picks[i]; c.querySelector(".cw").textContent = p ? (this.reasons[p.key] || "") : ""; });
    },
    onError(m) { this.busy = false; this.picks = []; this.drawCards(); this.message(m, false); },

    // a request said to the mic ("something light for 40 minutes"): the panel opens and asks with those words
    fromVoice(text) {
      if (!this.open) this.show();
      this.st.t = 0; this.st.kind = 0; this.st.genres = [];             // spoken: the words decide the time and genres
      this.st.like = text; this.input.value = text; this.render();
      this.ask();
    },

    ask() {
      const w = words();
      if (!(window.TV && TV.council)) { this.message("The Council sits in the TV app", false); return; }
      if (TV.agentReady && !TV.agentReady()) { this.message("The agent has no keys yet: run scripts/agent-keys.sh on your computer", false); return; }
      this.busy = true; this.picks = []; this.drawCards();
      this.el.querySelector(".sum").textContent = "";
      this.message(w.wait, true);
      const t = TIMES[this.st.t];
      TV.council(JSON.stringify({ minutes: t, kind: KINDS[this.st.kind][0], genres: this.st.genres.map(g => g.toLowerCase()), like: this.st.like || "" }));
      try { localStorage.setItem("pantheon_council", JSON.stringify(this.st)); } catch (e) { }
    },

    message(text, busy) {
      const m = this.el.querySelector(".msg");
      m.textContent = text; m.classList.toggle("busy", !!busy); m.style.display = text ? "" : "none";
    },

    drawCards() {
      const g = this.el.querySelector(".grid");
      g.innerHTML = this.picks.map(p => {
        const series = p.type === "tv";
        const len = p.runtime ? (series ? `${p.runtime} min episodes` : `${Math.floor(p.runtime / 60) ? Math.floor(p.runtime / 60) + " h " : ""}${p.runtime % 60} min`) : "";
        const meta = [series ? `Series${p.seasons ? " · " + p.seasons + (p.seasons > 1 ? " seasons" : " season") : ""}` : "Movie", p.year, len, p.service].filter(Boolean).join("  ·  ");
        return `<div class="card"><div class="poster" style="background-image:url('${esc(p.poster)}')"></div>
                <div class="ct">${esc(p.title)}</div><div class="cm">${esc(meta)}</div><div class="cw">${esc(this.reasons[p.key] || "")}</div></div>`;
      }).join("");
    },

    render() {
      if (!this.el) return;
      const z = this.zone;
      this.el.querySelectorAll(".zone").forEach((e, i) => e.classList.toggle("foc", i === z));
      const f = this.st.t / (TIMES.length - 1);
      this.el.querySelector(".fill").style.width = (f * 100) + "%";
      this.el.querySelector(".knob").style.left = (f * 100) + "%";
      const t = TIMES[this.st.t];
      this.el.querySelector(".tval").textContent = t ? (t < 60 ? `${t} minutes` : `${t / 60} hour${t > 60 ? "s" : ""}`.replace("1.5 hours", "1½ hours")) : "Any length";
      this.el.querySelectorAll(".kinds .pill").forEach((e, i) => { e.classList.toggle("on", i === this.st.kind); e.classList.toggle("cur", z === 1 && i === this.st.kind); });
      this.el.querySelectorAll(".genres .pill").forEach((e, i) => { e.classList.toggle("on", this.st.genres.includes(GENRES[i])); e.classList.toggle("cur", z === 2 && i === this.gi); });
      this.el.querySelectorAll(".card").forEach((e, i) => e.classList.toggle("cur", z === 5 && i === this.ri));
    },

    key(k) {
      const back = k === "Escape" || k === "Backspace";
      if (back) { this.close(); return; }
      const z = this.zone, n = this.picks.length;
      if (z === 5) {                                          // the result cards: 4 across
        if (k === "ArrowLeft") { if (this.ri % 4 === 0) this.zone = 4; else this.ri--; }
        else if (k === "ArrowRight") this.ri = Math.min(n - 1, this.ri + 1);
        else if (k === "ArrowUp") { if (this.ri >= 4) this.ri -= 4; }
        else if (k === "ArrowDown") { if (this.ri + 4 < n) this.ri += 4; }
        else if (k === "Enter" || k === " ") { const p = this.picks[this.ri]; if (p && window.TV && TV.openTitle) TV.openTitle(p.pkg, p.title); }
        this.render(); return;
      }
      if (k === "ArrowRight" && n && (z === 4 || z === 3 || (z === 2 && this.gi % GCOLS === GCOLS - 1) || (z === 0 && this.st.t === TIMES.length - 1))) { this.zone = 5; this.render(); return; }
      if (z === 0) {
        if (k === "ArrowLeft") this.st.t = Math.max(0, this.st.t - 1);
        else if (k === "ArrowRight") this.st.t = Math.min(TIMES.length - 1, this.st.t + 1);
        else if (k === "ArrowDown") this.zone = 1;
      } else if (z === 1) {
        if (k === "ArrowLeft") this.st.kind = Math.max(0, this.st.kind - 1);
        else if (k === "ArrowRight") this.st.kind = Math.min(KINDS.length - 1, this.st.kind + 1);
        else if (k === "ArrowUp") this.zone = 0; else if (k === "ArrowDown") this.zone = 2;
      } else if (z === 2) {
        const g = this.gi;
        if (k === "ArrowLeft") { if (g % GCOLS) this.gi--; }
        else if (k === "ArrowRight") { if (g % GCOLS < GCOLS - 1 && g + 1 < GENRES.length) this.gi++; }
        else if (k === "ArrowUp") { if (g >= GCOLS) this.gi -= GCOLS; else this.zone = 1; }
        else if (k === "ArrowDown") { if (g + GCOLS < GENRES.length) this.gi += GCOLS; else this.zone = 3; }
        else if (k === "Enter" || k === " ") { const name = GENRES[g], i = this.st.genres.indexOf(name); if (i >= 0) this.st.genres.splice(i, 1); else this.st.genres.push(name); }
      } else if (z === 3) {
        if (k === "Enter" || k === " ") { this.input.focus(); if (window.TV && TV.keyboard) TV.keyboard(); return; }
        else if (k === "ArrowUp") this.zone = 2; else if (k === "ArrowDown") this.zone = 4;
      } else if (z === 4) {
        if (k === "Enter" || k === " ") { this.ask(); return; }
        else if (k === "ArrowUp") this.zone = 3;
      }
      this.render();
    },
  };
  window.pantheonCouncilIcon = () => words().icon;               // the top row's tip for its icon
})();
