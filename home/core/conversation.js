// Pantheon: the conversation on screen. When you talk to the agent (the mic), a scroll hangs at the top of the screen
// and writes down both sides: what you said, and what the agent answers, its words appearing as its voice speaks
// them. In Game of Thrones you are The King and the agent is The Hand; in Hogwarts, You and The Headmaster.
// The TV app calls pantheonVoice(state, text) for your side (listening, heard, reply, error) and, from the agent's
// voice (agent/Speaker.java), pantheonHand(id, words) then pantheonSpeaking(id, ms) when that line starts playing.
(function () {
  const WHO = { got: ["The King", "The Hand", "Speak, Your Grace…"], hogwarts: ["You", "The Headmaster", "Speak, my dear…"], _: ["You", "Pantheon", "Listening…"] };
  const who = () => WHO[document.body.dataset.theme] || WHO._;
  let el, king, hand, note, handBox, hideT = 0, revealT = 0, waitT = 0, lastHandAt = 0, current = "";
  const pending = {};

  function build() {
    const s = document.createElement("style");
    s.textContent = `
      #convo { position: absolute; z-index: 60; left: 50%; top: 330px; width: 980px; margin-left: -490px; pointer-events: none;
               opacity: 0; transition: opacity .5s; }
      #convo.show { opacity: 1; }
      body.council-open #convo { top: auto; bottom: 34px; }
      body.convo-open #news { opacity: .12; transition: opacity .5s; }        /* the headlines step back while it's shown */
      #convo .paper { position: relative; padding: 26px 44px 28px; color: #2b190a; text-shadow: none;
        background: linear-gradient(90deg, rgba(60,35,10,.30), rgba(0,0,0,0) 7%, rgba(0,0,0,0) 93%, rgba(60,35,10,.32)),
                    linear-gradient(180deg, rgba(204,174,120,.86), rgba(236,221,186,.84) 12%, rgba(240,228,198,.84) 50%, rgba(230,212,170,.85) 88%, rgba(200,170,115,.88));
        -webkit-backdrop-filter: blur(12px) saturate(1.2); backdrop-filter: blur(12px) saturate(1.2);
        box-shadow: 0 14px 40px rgba(0,0,0,.55); clip-path: inset(0 0 100% 0); transition: clip-path .9s cubic-bezier(.35,.6,.2,1); }
      #convo.show .paper { clip-path: inset(0 0 0 0); }
      #convo .rod { position: absolute; left: -12px; right: -12px; height: 16px; border-radius: 8px;
        background: linear-gradient(180deg, #4a2f14, #a5814b 40%, #d8bd85 55%, #5b3b19); box-shadow: 0 4px 12px rgba(0,0,0,.6); }
      #convo .rod::before, #convo .rod::after { content: ""; position: absolute; top: 2px; width: 9px; height: 12px; border-radius: 3px;
        background: linear-gradient(180deg, #8a6a2c, #f0d58a, #8a6a2c); }
      #convo .rod::before { left: -6px; } #convo .rod::after { right: -6px; }
      #convo .top { top: -8px; } #convo .bot { bottom: -8px; }
      #convo .who { font: 700 15px/1 "Cinzel", Georgia, serif; letter-spacing: .32em; text-transform: uppercase; color: #6d4a1f; margin-bottom: 8px; }
      #convo .say { font: italic 500 27px/1.38 Georgia, "Times New Roman", serif; }
      #convo .king .say.wait { opacity: .45; }
      #convo .hand { margin-top: 20px; padding-top: 18px; border-top: 1px solid rgba(109,74,31,.3); display: none; }
      #convo .hand.on { display: block; }
      #convo .hand .say span { opacity: 0; transition: opacity .25s; }
      #convo .hand .say span.in { opacity: 1; }
      #convo .hand .say .dots::after { content: ""; animation: cdots2 1.2s steps(4) infinite; }
      @keyframes cdots2 { 0% { content: ""; } 25% { content: "."; } 50% { content: ".."; } 75% { content: "..."; } }
      #convo .note { margin-top: 10px; font-size: 18px; letter-spacing: .04em; color: #5b4326; opacity: .8; display: none; }
      #convo .note.on { display: block; }
      body:not([data-theme="got"]):not([data-theme="hogwarts"]) #convo .paper { background: rgba(14,12,10,.86); color: var(--ink); }
      body:not([data-theme="got"]):not([data-theme="hogwarts"]) #convo .who { color: var(--gold-hi); }`;
    document.head.appendChild(s);
    el = document.createElement("div"); el.id = "convo";
    el.innerHTML = `<div class="paper"><div class="king"><div class="who"></div><div class="say"></div></div>
                    <div class="hand"><div class="who"></div><div class="say"></div><div class="note"></div></div></div>
                    <div class="rod top"></div><div class="rod bot"></div>`;
    document.getElementById("stage").appendChild(el);
    king = el.querySelector(".king .say"); handBox = el.querySelector(".hand"); hand = el.querySelector(".hand .say"); note = el.querySelector(".note");
  }

  function open() {
    if (!el) build();
    const w = who();
    el.querySelector(".king .who").textContent = w[0];
    el.querySelector(".hand .who").textContent = w[1];
    clearTimeout(hideT);
    el.classList.add("show"); document.body.classList.add("convo-open");
    const old = document.getElementById("voice"); if (old) old.classList.remove("show");   // the old one-line banner
  }
  function close(after) {
    clearTimeout(hideT);
    hideT = setTimeout(() => { if (el) el.classList.remove("show"); document.body.classList.remove("convo-open"); }, after || 0);
  }
  window.pantheonConvoClose = () => { if (el && el.classList.contains("show")) { close(0); return true; } return false; };

  // your side
  function kingSays(text, waiting) {
    open();
    king.textContent = waiting ? text : "“" + text + "”";
    king.classList.toggle("wait", !!waiting);
  }
  // the agent's side: shown "…" until its voice starts, then its words appear across the time it takes to say them
  function handThinking() {
    handBox.classList.add("on"); hand.innerHTML = '<span class="in dots"></span>'; note.classList.remove("on");
  }
  function handSays(text, ms) {
    open(); handBox.classList.add("on");
    clearTimeout(revealT); clearTimeout(waitT);
    current = text;
    const words = text.split(/\s+/).filter(Boolean);
    hand.innerHTML = words.map(w => `<span>${w.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[c])} </span>`).join("");
    const spans = hand.querySelectorAll("span"), step = Math.max(60, (ms || text.length * 60) / Math.max(1, spans.length));
    let i = 0;
    (function next() { if (i < spans.length) { spans[i++].classList.add("in"); revealT = setTimeout(next, step); } })();
    close((ms || text.length * 60) + Math.max(6000, text.length * 55));   // then time to read it, then it rolls up
  }

  window.pantheonHand = (id, text) => {
    lastHandAt = Date.now();
    pending[id] = text;
    open(); handThinking();
    clearTimeout(waitT);
    waitT = setTimeout(() => { if (pending[id]) { handSays(pending[id]); delete pending[id]; } }, 9000);   // no voice came: show it anyway
  };
  window.pantheonSpeaking = (id, ms) => {
    const text = pending[id]; if (!text) return;
    delete pending[id];
    handSays(text, ms);
  };

  const replyText = body => { try { const j = JSON.parse(body); return j.reply || j.text || j.message || ""; } catch (e) { return body || ""; } };
  const original = window.pantheonVoice;                                 // the one-line banner (core/theme.js)
  window.pantheonVoice = function (state, text) {
    if (state === "listening") { kingSays(text || who()[2], !text); handBox.classList.remove("on"); }
    else if (state === "heard") { kingSays(text); handThinking(); }
    else if (state === "reply" || state === "error") {
      const t = state === "error" ? (text || "Something went wrong") : replyText(text);
      if (!t) return;
      const spoken = Date.now() - lastHandAt < 25000;                   // the agent's voice is saying (or about to say) something
      const same = s => s && s.replace(/\W+/g, "") === t.replace(/\W+/g, "");
      if (spoken && (same(current) || Object.values(pending).some(same))) return;   // that's what the voice is saying: no second copy
      if (spoken) { note.textContent = t; note.classList.add("on"); }
      else if (!spoken) handSays(t);
    }
    else if (state === "idle") close(1500);
    if (original && !el) original(state, text);
  };
})();
