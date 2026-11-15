// Pantheon: the sleep timer's countdown. After 30 minutes with no button pressed on the home screen (the TV app
// keeps time: MainActivity's sleep timer), this shows for the last 30 seconds; any button stops it. At zero the TV
// app puts the TV to sleep. Worded per theme.
(function () {
  const WORDS = {
    got:      ["The night grows long, Your Grace", "The hall goes dark in", "Press any button to stay"],
    hogwarts: ["Lights out soon, my dear", "The castle sleeps in", "Press any button to stay up"],
    _:        ["Still watching?", "The TV turns off in", "Press any button to stay"],
  };
  let el, num, ring, t = 0, left = 0, total = 30;

  function build() {
    const s = document.createElement("style");
    s.textContent = `
      #sleepy { position: absolute; z-index: 70; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center;
                background: radial-gradient(ellipse at 50% 50%, rgba(0,0,0,.55), rgba(0,0,0,.92) 70%); opacity: 0; pointer-events: none;
                transition: opacity .6s; color: var(--ink); text-align: center; }
      #sleepy.on { opacity: 1; }
      #sleepy .h { font: 600 52px/1.1 "Cinzel", Georgia, serif; color: var(--gold-hi); }
      body:not([data-theme="got"]) #sleepy .h { font-family: inherit; font-weight: 400; }
      #sleepy .s { margin-top: 18px; font-size: 24px; letter-spacing: .24em; text-transform: uppercase; opacity: .7; }
      #sleepy .dial { position: relative; width: 220px; height: 220px; margin: 34px 0 30px; }
      #sleepy svg { position: absolute; inset: 0; transform: rotate(-90deg); }
      #sleepy .n { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font: 500 96px/1 Georgia, serif; color: #fff6e2; }
      #sleepy .k { font-size: 22px; letter-spacing: .2em; text-transform: uppercase; opacity: .55; }`;
    document.head.appendChild(s);
    el = document.createElement("div"); el.id = "sleepy";
    el.innerHTML = `<div class="h"></div><div class="s"></div>
      <div class="dial"><svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="45" fill="none" stroke="rgba(255,255,255,.12)" stroke-width="3"/>
        <circle class="ring" cx="50" cy="50" r="45" fill="none" stroke="var(--gold-hi)" stroke-width="3" stroke-linecap="round" stroke-dasharray="282.7" stroke-dashoffset="0"/></svg>
        <div class="n"></div></div><div class="k"></div>`;
    document.getElementById("stage").appendChild(el);
    num = el.querySelector(".n"); ring = el.querySelector(".ring");
  }
  function draw() { num.textContent = left; ring.style.strokeDashoffset = String(282.7 * (1 - left / total)); }

  window.pantheonSleep = {
    start(seconds) {
      if (!el) build();
      if (window.pantheonConvoClose) pantheonConvoClose();             // the conversation scroll steps aside
      const w = WORDS[document.body.dataset.theme] || WORDS._;
      el.querySelector(".h").textContent = w[0]; el.querySelector(".s").textContent = w[1]; el.querySelector(".k").textContent = w[2];
      total = 30; left = Math.min(30, Math.round(seconds)); draw();
      el.classList.add("on");
      clearInterval(t); t = setInterval(() => { left = Math.max(0, left - 1); draw(); }, 1000);
    },
    stop() { clearInterval(t); if (el) el.classList.remove("on"); },
  };
})();
