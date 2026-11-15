// Pantheon: the Ledger: what the agent has learned about you, on parchment, where you can strike it out.
// The facts it keeps (agent/Memory.java, on the TV only) and what you've watched from Pantheon. OK on a fact strikes
// it and the agent forgets it; at the bottom, forget what you've watched, or everything (OK twice). Opened from the
// top row, or by asking the agent what it knows about you. Each theme names it its own way.
(function () {
  const WORDS = {
    got:      { icon: "The Ledger", title: "The Ledger", sub: "What the Hand knows of you, Your Grace", empty: "The Ledger is empty. Tell the Hand what you like.",
                watched: "Recently watched", forgetWatched: "Forget what I've watched", forgetAll: "Burn the Ledger", sure: "Press OK again to burn it all" },
    hogwarts: { icon: "The Headmaster's Notes", title: "The Headmaster's Notes", sub: "What I've learned about you, my dear", empty: "Nothing noted yet. Tell me what you like.",
                watched: "Recently watched", forgetWatched: "Forget what I've watched", forgetAll: "Forget everything", sure: "Press OK again to forget everything" },
    _:        { icon: "What I remember", title: "What Pantheon remembers", sub: "Facts learned from what you've said", empty: "Nothing yet.",
                watched: "Recently watched", forgetWatched: "Forget what I've watched", forgetAll: "Forget everything", sure: "Press OK again to forget everything" },
  };
  const words = () => WORDS[document.body.dataset.theme] || WORDS._;
  const esc = s => String(s || "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const when = d => { try { return new Date(d + "T12:00:00").toLocaleDateString([], { day: "numeric", month: "short" }); } catch (e) { return ""; } };

  const L = window.pantheonLedger = {
    open: false, el: null, i: 0, items: [], data: { facts: [], opened: [] }, armed: 0,

    build() {
      const s = document.createElement("style");
      s.textContent = `
        #ledger { position: absolute; z-index: 65; inset: 0; display: flex; align-items: center; justify-content: center;
                  background: radial-gradient(ellipse at 50% 40%, rgba(20,14,9,.86), rgba(0,0,0,.96) 75%); opacity: 0; pointer-events: none; transition: opacity .3s; }
        #ledger.open { opacity: 1; }
        #ledger .page { position: relative; width: 1040px; max-height: 860px; padding: 46px 64px 40px; box-sizing: border-box; color: #2b190a;
          background: linear-gradient(90deg, rgba(60,35,10,.30), rgba(0,0,0,0) 6%, rgba(0,0,0,0) 94%, rgba(60,35,10,.32)),
                      linear-gradient(180deg, #d9c398 0%, #ecdcb4 12%, #f0e3c4 50%, #e6d2a6 88%, #cdb07a 100%);
          box-shadow: 0 24px 70px rgba(0,0,0,.7); }
        #ledger .rod { position: absolute; left: -16px; right: -16px; height: 20px; border-radius: 10px;
          background: linear-gradient(180deg, #4a2f14, #a5814b 40%, #d8bd85 55%, #5b3b19); box-shadow: 0 5px 14px rgba(0,0,0,.6); }
        #ledger .rod.t { top: -10px; } #ledger .rod.b { bottom: -10px; }
        #ledger .h { font: 700 46px/1.05 "Cinzel", Georgia, serif; color: #4a2c0f; text-align: center; }
        #ledger .sub { margin: 8px 0 26px; text-align: center; font: italic 24px/1.3 Georgia, serif; color: #6b4a22; }
        #ledger .list { display: flex; flex-direction: column; gap: 4px; max-height: 400px; overflow: hidden; scroll-behavior: smooth; }
        #ledger .row { display: flex; align-items: baseline; gap: 18px; padding: 9px 16px; border-radius: 8px; font: italic 500 26px/1.3 Georgia, serif;
                       transition: background .15s, opacity .5s; position: relative; }
        #ledger .row .d { margin-left: auto; font: 500 15px/1 Menlo, monospace; color: #8a6a3c; font-style: normal; white-space: nowrap; }
        #ledger .row::before { content: "❧"; color: #8a5a28; font-style: normal; }
        #ledger .row.cur { background: rgba(120,80,30,.16); box-shadow: inset 3px 0 0 #8a5a28; }
        #ledger .row .strike { position: absolute; left: 44px; right: 120px; top: 52%; height: 3px; background: #8e1d16; border-radius: 2px;
                               transform: scaleX(0); transform-origin: left; transition: transform .45s ease-out; }
        #ledger .row.struck .strike { transform: scaleX(1); }
        #ledger .row.struck { opacity: .35; }
        #ledger .empty { text-align: center; font: italic 26px Georgia, serif; color: #6b4a22; padding: 30px 0; }
        #ledger .sec { margin-top: 22px; padding-top: 16px; border-top: 1px solid rgba(109,74,31,.35); }
        #ledger .lab { font: 700 15px/1 "Cinzel", Georgia, serif; letter-spacing: .3em; text-transform: uppercase; color: #6d4a1f; margin: 0 0 10px 16px; }
        #ledger .watched { font: italic 21px/1.4 Georgia, serif; color: #4a3218; margin: 0 16px 14px; }
        #ledger .acts { display: flex; gap: 18px; justify-content: center; margin-top: 18px; }
        #ledger .act { padding: 12px 26px; border-radius: 26px; font: 700 17px/1 "Cinzel", Georgia, serif; letter-spacing: .1em; text-transform: uppercase;
                       color: #4a2c0f; border: 1.5px solid rgba(109,74,31,.55); }
        #ledger .act.cur { background: #4a2c0f; color: #f3e3bf; border-color: #4a2c0f; }
        #ledger .act.burn.cur { background: #8e1d16; border-color: #8e1d16; }
        #ledger .hint { margin-top: 18px; text-align: center; font: 15px/1 Menlo, monospace; letter-spacing: .14em; text-transform: uppercase; color: #8a6a3c; }`;
      document.head.appendChild(s);
      const el = this.el = document.createElement("div"); el.id = "ledger";
      el.innerHTML = `<div class="page"><div class="rod t"></div><div class="rod b"></div><div class="h"></div><div class="sub"></div>
        <div class="list"></div><div class="sec"><div class="lab"></div><div class="watched"></div></div><div class="acts"></div>
        <div class="hint">↑ ↓ choose · OK strike it out · Back close</div></div>`;
      document.getElementById("stage").appendChild(el);
    },

    load() {
      try { this.data = JSON.parse(window.TV && TV.ledger ? TV.ledger() : JSON.stringify(window.__ledgerSample || { facts: [], opened: [] })); }
      catch (e) { this.data = { facts: [], opened: [] }; }
      this.data.facts = this.data.facts || []; this.data.opened = this.data.opened || [];
    },

    draw() {
      const w = words(), d = this.data;
      this.el.querySelector(".h").textContent = w.title;
      this.el.querySelector(".sub").textContent = w.sub;
      this.el.querySelector(".list").innerHTML = d.facts.length
        ? d.facts.map(f => `<div class="row"><span>${esc(f.t)}</span><span class="d">${esc(when(f.at))}</span><span class="strike"></span></div>`).join("")
        : `<div class="empty">${esc(w.empty)}</div>`;
      const sec = this.el.querySelector(".sec");
      sec.style.display = d.opened.length ? "" : "none";
      sec.querySelector(".lab").textContent = w.watched;
      sec.querySelector(".watched").textContent = d.opened.slice().reverse().slice(0, 8).map(o => o.title).join(" · ");
      const acts = [];
      if (d.opened.length) acts.push(["watched", w.forgetWatched]);
      if (d.facts.length || d.opened.length) acts.push(["all", this.armed && Date.now() - this.armed < 5000 ? w.sure : w.forgetAll]);
      this.el.querySelector(".acts").innerHTML = acts.map(([k, t]) => `<div class="act ${k === "all" ? "burn" : ""}" data-k="${k}">${esc(t)}</div>`).join("");
      this.items = [...this.el.querySelectorAll(".row"), ...this.el.querySelectorAll(".act")];
      this.i = Math.max(0, Math.min(this.i, this.items.length - 1));
      this.render();
    },

    render() {
      this.items.forEach((e, k) => e.classList.toggle("cur", k === this.i));
      const e = this.items[this.i], list = this.el.querySelector(".list");             // keep the chosen fact in view
      if (e && e.classList.contains("row")) {
        if (e.offsetTop - list.offsetTop < list.scrollTop) list.scrollTop = e.offsetTop - list.offsetTop;
        else if (e.offsetTop - list.offsetTop + e.offsetHeight > list.scrollTop + list.clientHeight)
          list.scrollTop = e.offsetTop - list.offsetTop + e.offsetHeight - list.clientHeight;
      }
    },

    show() {
      if (!this.el) this.build();
      if (typeof setGear === "function") setGear(false);
      if (window.pantheonCouncil && pantheonCouncil.open) pantheonCouncil.close();
      this.load(); this.i = 0; this.armed = 0; this.shownAt = Date.now(); this.draw();
      this.open = true; this.el.classList.add("open");
    },
    close() { this.open = false; if (this.el) this.el.classList.remove("open"); },

    key(k) {
      if (k === "Escape" || k === "Backspace") { this.close(); return; }
      if (k === "ArrowUp") this.i = Math.max(0, this.i - 1);
      else if (k === "ArrowDown") this.i = Math.min(this.items.length - 1, this.i + 1);
      else if (k === "Enter" || k === " ") {
        if (Date.now() - (this.shownAt || 0) < 900) return;    // it can open by voice: an OK meant for something else mustn't strike a fact
        const e = this.items[this.i]; if (!e) return;
        if (e.classList.contains("row")) {                       // strike this fact out, then forget it
          if (this.el.querySelector(".row.struck")) return;      // one at a time: indices shift once it's gone
          const idx = [...this.el.querySelectorAll(".row")].indexOf(e);
          e.classList.add("struck");
          setTimeout(() => { if (window.TV && TV.strikeFact) TV.strikeFact(idx); else this.data.facts.splice(idx, 1);
                             if (window.TV) this.load(); this.draw(); }, 650);
          return;
        }
        if (e.dataset.k === "watched") { if (window.TV && TV.forgetOpened) TV.forgetOpened(); else this.data.opened = []; if (window.TV) this.load(); this.draw(); return; }
        if (e.dataset.k === "all") {
          if (this.armed && Date.now() - this.armed < 5000) {
            if (window.TV && TV.forgetEverything) TV.forgetEverything(); this.data = { facts: [], opened: [] }; this.armed = 0;
          } else this.armed = Date.now();
          this.draw(); return;
        }
      }
      this.render();
    },
  };
  window.pantheonLedgerIcon = () => words().icon;
})();
