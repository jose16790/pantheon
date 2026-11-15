// Pantheon: themes and the voice banner, for the Adult Space page.
//
// Themes are defined on the Android side (theme/ThemeManager.java and the ThemeData classes) and handed to the page
// as JSON: { id, name, mascot, complete, colors: {gold, ink, ...}, images: {castle, shelf, ...} }.
// colors become CSS variables on :root (index.html's styles use them); images replace the pictures marked
// data-slot="<slot>" (and --mist). A slot a theme leaves out keeps the picture already shown.
//
// A theme can also have a scene (PANTHEON_SCENES[id], e.g. themes/got/scene.js): its own mascot, live effects and library.
// Scenes start once the page has finished setting up (DOMContentLoaded), since they use its dock and frame loop.
//
// The theme picker (the crown in the top row) lists the themes; choosing one asks the TV app to switch
// (TV.setTheme, saved), which calls pantheonApplyTheme back with the new theme.
//
// In a desktop browser (no window.TV) the page starts as Hogwarts; ?theme=got|marvel, or the picker, previews another.
(function () {
  const HOGWARTS_IMAGES = { castle: "themes/hogwarts/img/castle.jpg", windowsA: "themes/hogwarts/img/windows_a_crop.png", windowsB: "themes/hogwarts/img/windows_b_crop.png",
                            shelf: "themes/hogwarts/img/shelf.jpg", greenhouse: "themes/hogwarts/img/greenhouse_beasts.jpg", mist: "themes/hogwarts/img/mist.png" };
  const PREVIEW = {   // browser preview only; on the TV the Java ThemeData classes are the source of truth
    hogwarts: { id: "hogwarts", name: "Hogwarts", colors: { gold: "#d9b45f", "gold-hi": "#f3dc9a", ink: "#f4ead6", accent: "#f0c29c", "accent-soft": "#f6d9bb", spine: "#e8c97e", "panel-1": "#111a26", "panel-2": "#000000" }, images: HOGWARTS_IMAGES },
    got:      { id: "got", name: "Game of Thrones", mascot: "dragon", colors: { gold: "#c9a45c", "gold-hi": "#f2d49a", ink: "#ede5d8", accent: "#e8a070", "accent-soft": "#f0cfae", spine: "#d8b470", "panel-1": "#16100d", "panel-2": "#000000" },
                images: { castle: "themes/got/img/throne.jpg", shelf: "themes/got/img/shelf.jpg" } },
    marvel:   { id: "marvel", name: "Marvel", complete: false, colors: { gold: "#e62429", "gold-hi": "#ffcf3f", ink: "#ffffff", accent: "#f2c14e", "accent-soft": "#ffe3a3", spine: "#ffcf3f", "panel-1": "#1a0b0d", "panel-2": "#000000" }, images: HOGWARTS_IMAGES },
  };
  const CARDS = [   // the picker's cards: [id, name, picture, line]
    ["hogwarts", "Hogwarts", "themes/hogwarts/img/castle.jpg", "The castle, the owl, the films"],
    ["got", "Game of Thrones", "themes/got/img/throne.jpg", "The throne room, the dragon, the seasons"],
    ["marvel", "Marvel", "", "Colours only for now"],
  ];

  // castle-only touches that make no sense in another scene
  const style = document.createElement("style");
  style.textContent = `
    body[data-theme="got"] #winA, body[data-theme="got"] #winB, body[data-theme="got"] #sky, body[data-theme="got"] #water,
    body[data-theme="got"] #moonglow, body[data-theme="got"] #cloud1, body[data-theme="got"] #potG, body[data-theme="got"] #potP,
    body[data-theme="got"] #potA { display: none !important; }
    body[data-nogarden] #toGarden { display: none !important; }
    body[data-theme="got"] #mist1, body[data-theme="got"] #mist2 { display: none !important; }
    #soundIcon .slash { display: none; } body.muted #soundIcon .slash { display: inline; } body.muted #soundIcon .wave { display: none; }
    body:not([data-theme="got"]) #soundIcon { display: none; }
    /* Game of Thrones books: rustic. Worn leather, bronze bands, the art inlaid like a plate, a parchment label */
    body[data-theme="got"] .spine .roman { margin-top: 3px; font-family: "Cinzel", Georgia, serif; font-weight: 700; font-size: 14px;
                                           color: #d9b778; letter-spacing: .04em; }
    body[data-theme="got"] .spine .stitle { font-family: "Cinzel", Georgia, serif; font-weight: 600; font-size: 13px; letter-spacing: .12em;
                                            color: #c9a66a; text-transform: uppercase; }
    body[data-theme="got"] .spine .star { visibility: hidden; }
    body[data-theme="got"] .spine .bands { height: 6px; border-top: 2px solid #6e5532; border-bottom: 2px solid #6e5532;
                                           background: linear-gradient(180deg, rgba(0,0,0,.45), rgba(150,115,60,.35), rgba(0,0,0,.45));
                                           box-shadow: 0 1px 1px rgba(0,0,0,.6); }
    body[data-theme="got"] .front.captioned img { position: absolute; left: 9px; top: 9px; width: calc(100% - 18px); height: calc(100% - 66px);
                                                  border: 1px solid rgba(120,90,45,.9); box-shadow: 0 0 0 2px rgba(0,0,0,.55), inset 0 0 18px rgba(0,0,0,.7);
                                                  filter: sepia(.28) saturate(.82) contrast(1.06) brightness(.9); }
    body[data-theme="got"] .front.captioned::after { content: ""; position: absolute; inset: 0; pointer-events: none; z-index: 2;
                                                     box-shadow: inset 0 0 22px rgba(0,0,0,.8), inset 0 0 3px rgba(0,0,0,.9); }
    body[data-theme="got"] .front .cap { left: 9px; right: 9px; bottom: 9px; height: 44px; border: 1px solid #5a4320; border-radius: 2px;
                                         background: radial-gradient(ellipse at 50% 40%, #d8c398 0%, #c2a774 55%, #9c7f4e 100%);
                                         box-shadow: inset 0 0 10px rgba(80,50,20,.55), 0 1px 3px rgba(0,0,0,.6);
                                         color: #2c1a0b; text-shadow: 0 1px 0 rgba(255,240,210,.35); font-size: 13px; letter-spacing: .05em; }
    #picker { position: absolute; z-index: 40; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 46px;
              background: radial-gradient(ellipse at 50% 30%, var(--panel-1), var(--panel-2) 75%); opacity: 0; pointer-events: none; transition: opacity .35s; }
    #picker.open { opacity: 1; }
    #picker .h { font-size: 58px; color: var(--gold-hi); letter-spacing: .02em; }
    #picker .sub { margin-top: -30px; font-size: 22px; letter-spacing: .28em; text-transform: uppercase; color: var(--accent); opacity: .8; }
    #picker .cards { display: flex; gap: 48px; }
    #picker .card { position: relative; width: 470px; height: 300px; border-radius: 18px; overflow: hidden; background: #0c0a0a center / cover no-repeat;
                    border: 2px solid rgba(255,255,255,.12); transition: transform .25s, box-shadow .25s, border-color .25s; }
    #picker .card::after { content: ""; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0) 40%, rgba(0,0,0,.85)); }
    #picker .card .n { position: absolute; z-index: 1; left: 26px; bottom: 54px; font-size: 38px; color: #fff; }
    #picker .card .l { position: absolute; z-index: 1; left: 26px; bottom: 22px; font-size: 19px; letter-spacing: .06em; color: rgba(255,255,255,.75); }
    #picker .card .now { position: absolute; z-index: 1; right: 18px; top: 16px; padding: 6px 14px; border-radius: 20px; font-size: 16px; letter-spacing: .2em;
                         text-transform: uppercase; background: var(--gold); color: #140c06; display: none; }
    #picker .card.current .now { display: block; }
    #picker .card.foc { transform: scale(1.06); border-color: var(--gold-hi); box-shadow: 0 0 0 4px var(--gold-hi), 0 18px 60px rgba(0,0,0,.6), 0 0 50px rgba(243,220,154,.25); }
    #picker .card.marvel { background-image: linear-gradient(135deg, #8b0f14, #e62429 45%, #ffcf3f); }
    #picker .hint { font-size: 20px; letter-spacing: .2em; text-transform: uppercase; color: var(--ink); opacity: .55; }`;
  document.head.appendChild(style);

  // ---------------------------------------------------------------- scenes
  let current = null, pendingScene = null, started = false;
  function useScene(id) {
    if (!started) { pendingScene = id; return; }
    const S = window.PANTHEON_SCENES || {};
    if (current === id) return;
    if (current && S[current] && S[current].leave) S[current].leave();
    current = id;
    if (S[id] && S[id].enter) S[id].enter();
  }
  document.addEventListener("DOMContentLoaded", () => { started = true; if (pendingScene) useScene(pendingScene); });

  window.pantheonApplyTheme = function (t) {
    if (typeof t === "string") { try { t = JSON.parse(t); } catch (e) { console.log("Pantheon: bad theme JSON"); return; } }
    if (!t || !t.id) return;
    const root = document.documentElement.style, colors = t.colors || {}, images = t.images || {};
    for (const k in colors) root.setProperty("--" + k, colors[k]);
    document.querySelectorAll("[data-slot]").forEach(el => { const src = images[el.dataset.slot]; if (src && el.getAttribute("src") !== src) el.src = src; });
    if (images.mist) root.setProperty("--mist", "url(" + images.mist + ")");
    document.body.dataset.theme = t.id;
    useScene(t.id);
    if (picker.open) picker.render();
    console.log("Pantheon: theme " + t.id + (t.complete === false ? " (stub: palette only)" : ""));
  };

  // ---------------------------------------------------------------- voice
  const box = () => document.getElementById("voice");
  let hideTimer = 0;
  function show(kind, label, text, ms) {
    const b = box(); if (!b) return;
    clearTimeout(hideTimer);
    b.className = "show " + kind;
    b.querySelector(".k").textContent = label;
    b.querySelector(".t").textContent = text || "";
    if (ms) hideTimer = setTimeout(() => b.classList.remove("show"), ms);
  }
  // The agent may answer with JSON ({reply}, {text}, {message}, {response}) or plain text: show the words.
  function replyText(body) {
    if (!body) return "Done.";
    try {
      const j = JSON.parse(body);
      const t = typeof j === "string" ? j : (j.reply || j.text || j.message || j.response || j.answer);
      if (t) return String(t);
    } catch (e) { /* plain text */ }
    return body.length > 280 ? body.slice(0, 277) + "…" : body;
  }

  // Called by the TV app (voice/VoiceAgent.java) as things happen.
  window.pantheonVoice = function (state, text) {
    if (state === "listening") show("listening", "Listening", text || "Speak now…");
    else if (state === "heard") show("sending", document.body.dataset.theme === "got" ? "Sending by raven" : "Sending by owl post", "“" + text + "”");
    else if (state === "reply") show("reply", "Reply", replyText(text), 9000);
    else if (state === "error") show("error", "Hmm", text || "Something went wrong", 6000);
    else { const b = box(); if (b) b.classList.remove("show"); }
  };

  // The mic icon (Up from the dock, then Left, then OK).
  window.pantheonListen = function () {
    if (window.TV && TV.listen) TV.listen();
    else window.pantheonVoice("error", "Voice works in the TV app");
  };

  // ---------------------------------------------------------------- the theme picker (the crown, top right)
  const picker = window.pantheonPicker = {
    open: false, i: 0, el: null,
    build() {
      const el = this.el = document.createElement("div"); el.id = "picker";
      el.innerHTML = `<div class="h">Choose your realm</div><div class="sub">The whole home changes with it</div><div class="cards">${
        CARDS.map(([id, n, img, l]) => `<div class="card ${id}" data-id="${id}" style="${img ? `background-image:url(${img})` : ""}"><div class="now">Now</div><div class="n">${n}</div><div class="l">${l}</div></div>`).join("")
      }</div><div class="hint">← → choose &nbsp;·&nbsp; OK switch &nbsp;·&nbsp; Back close</div>`;
      document.getElementById("stage").appendChild(el);
    },
    render() {
      const now = document.body.dataset.theme || "got";
      this.el.querySelectorAll(".card").forEach((c, k) => { c.classList.toggle("foc", k === this.i); c.classList.toggle("current", c.dataset.id === now); });
    },
    show() {
      if (!this.el) this.build();
      if (typeof setGear === "function") setGear(false);
      this.i = Math.max(0, CARDS.findIndex(c => c[0] === (document.body.dataset.theme || "got")));
      this.open = true; this.el.classList.add("open"); this.render();
    },
    close() { this.open = false; if (this.el) this.el.classList.remove("open"); },
    choose(id) {
      if (window.TV && TV.setTheme) TV.setTheme(id);           // the TV app saves it and calls pantheonApplyTheme back
      else if (PREVIEW[id]) window.pantheonApplyTheme(PREVIEW[id]);
      setTimeout(() => this.close(), 450);
    },
    key(k) {
      if (k === "ArrowLeft") this.i = Math.max(0, this.i - 1);
      else if (k === "ArrowRight") this.i = Math.min(CARDS.length - 1, this.i + 1);
      else if (k === "Enter" || k === " ") { this.choose(CARDS[this.i][0]); return; }
      else if (k === "Escape" || k === "Backspace" || k === "ArrowUp" || k === "ArrowDown") { this.close(); return; }
      this.render();
    },
  };

  // ---------------------------------------------------------------- start-up
  if (window.TV && TV.theme) window.pantheonApplyTheme(TV.theme());
  else { const q = (location.search.match(/[?&]theme=(\w+)/) || [])[1]; window.pantheonApplyTheme(PREVIEW[q] || PREVIEW.got); }
})();
