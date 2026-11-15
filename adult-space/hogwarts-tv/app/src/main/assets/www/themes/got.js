// Pantheon: the Game of Thrones scene (an unofficial fan theme; see NOTICE.md).
//
//   Home      the throne room (img/got/throne.jpg) with live flames on its four torch sconces; the dragon
//             (dragon/*.webp, tools/dragon_sprites.py) does the owl's job: it hops to the focused app, stands beside
//             it, and breathes fire on it: real fire footage (dragon/fire.webp, tools/fire_sprites.py) on its own
//             small canvas, aimed from the jaws at the icon. The icon glows, chars and smoulders, then cools back.
//   Library   the eight seasons, as leather books (covers drawn here), on the library shelf (img/got/shelf.jpg).
//   No greenhouse.
//
// theme.js calls PANTHEON_SCENES.got.enter() / leave() when the theme changes. Everything here runs inside the page's
// own frame loop (index.html calls sceneHome() at the end of home()), so it costs nothing while another theme is on.
(function () {
  const SCENES = window.PANTHEON_SCENES = window.PANTHEON_SCENES || {};

  // the torch flames in img/got/throne.jpg, in the photo's 1935 x 1080 frame (measured from the picture): x, y of
  // the flame's base, and its height
  const SCONCES = [{ x: 1760, y: 334, h: 112 }, { x: 166, y: 338, h: 112 }, { x: 390, y: 461, h: 80 }, { x: 1535, y: 466, h: 80 }];

  const SEASONS = [   // [year, episodes, leather colour, what happens (four words at most)]
    [2011, 10, "#5c1c16", "Ned Stark loses everything"], [2012, 10, "#2a3644", "Battle of the Blackwater"], [2013, 10, "#4a2a1a", "The Red Wedding"],
    [2014, 10, "#3c2040", "Joffrey's poisoned wedding"], [2015, 10, "#24382c", "Jon Snow is betrayed"], [2016, 10, "#5e2c10", "Battle of the Bastards"],
    [2017, 7, "#1e2c46", "Dragons meet the dead"], [2019, 6, "#3a3434", "The Iron Throne falls"]];
  const WORDS = ["One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight"];

  let on = false, ready = false, sconces = [], fire = [], roast = {}, landedOn = -1, settled = 0, nextBreath = 0, sprites = null, covers = null;
  let jet = null, sconceDt = 0;
  const ICON_Y = 853;                                           // the dock icons' middle (index.html: perchY, dsp)                                // jet: the fire-breath canvas (makeJet)

  // ---------------------------------------------------------------- the library: season books
  function drawCover(i) {
    const [year, , colour, words] = SEASONS[i];
    const c = document.createElement("canvas"); c.width = 400; c.height = 600;
    const g = c.getContext("2d");
    const lg = g.createLinearGradient(0, 0, 400, 600);
    lg.addColorStop(0, colour); lg.addColorStop(1, "#0d0807");
    g.fillStyle = lg; g.fillRect(0, 0, 400, 600);
    for (let k = 0; k < 2600; k++) {                          // grain of old leather
      g.fillStyle = `rgba(${Math.random() < .5 ? "0,0,0" : "255,220,170"},${Math.random() * .07})`;
      g.fillRect(Math.random() * 400, Math.random() * 600, 1 + Math.random() * 2, 1 + Math.random() * 2);
    }
    const gold = "#d8b46a";
    g.strokeStyle = gold; g.globalAlpha = .75; g.lineWidth = 3; g.strokeRect(22, 22, 356, 556);
    g.lineWidth = 1; g.strokeRect(32, 32, 336, 536); g.globalAlpha = 1;
    // a sword down the middle, faint, behind the numeral
    g.save(); g.globalAlpha = .28; g.fillStyle = gold;
    g.fillRect(196, 150, 8, 290); g.fillRect(160, 430, 80, 10); g.fillRect(193, 440, 14, 50);
    g.beginPath(); g.arc(200, 498, 12, 0, 7); g.fill(); g.beginPath(); g.moveTo(196, 150); g.lineTo(200, 128); g.lineTo(204, 150); g.fill();
    g.restore();
    g.fillStyle = gold; g.textAlign = "center"; g.shadowColor = "rgba(0,0,0,.7)"; g.shadowBlur = 6; g.shadowOffsetY = 2;
    g.font = "600 24px Cochin, Baskerville, Georgia, serif"; g.fillText("GAME  OF  THRONES", 200, 92);
    g.font = "700 150px Cochin, Baskerville, Georgia, serif"; g.fillText(ROMAN[i], 200, 340);
    g.font = "500 30px Cochin, Baskerville, Georgia, serif"; g.fillText(`SEASON ${WORDS[i].toUpperCase()}`, 200, 410);
    g.font = "500 22px Cochin, Baskerville, Georgia, serif"; g.globalAlpha = .7; g.fillText("✦", 200, 500);
    g.font = "500 22px Cochin, Baskerville, Georgia, serif"; g.globalAlpha = .7; g.fillText(String(year), 200, 548);
    const v = g.createRadialGradient(200, 300, 120, 200, 300, 420);  // worn, darker at the edges
    v.addColorStop(0, "rgba(0,0,0,0)"); v.addColorStop(1, "rgba(0,0,0,.55)");
    g.globalAlpha = 1; g.shadowColor = "transparent"; g.fillStyle = v; g.fillRect(0, 0, 400, 600);
    return c.toDataURL("image/jpeg", 0.85);
  }
  // each book's cover: the season art made for Pantheon (img/got/covers/N.jpg, original, in the repo), else one drawn here
  function seasons() {
    return SEASONS.map(([y, eps, c, line], i) => ({
      t: `Game of Thrones Season ${i + 1}`, s: "Game of Thrones", y, c, pages: eps * 60, id: "got" + (i + 1),
      cover: `img/got/covers/${i + 1}.jpg`, caption: line }));
  }
  function coverFallbacks() {                                  // a missing poster falls back, step by step
    books.forEach((b, i) => {
      const img = b.front.querySelector("img");
      img.onerror = () => { img.onerror = null; covers = covers || []; img.src = covers[i] = covers[i] || drawCover(i); };
      if (img.complete && !img.naturalWidth) img.onerror();
    });
  }

  // ---------------------------------------------------------------- sprites for the fire (made once, lazily: sprite() is index.html's)
  function makeSprites() {
    return {
      hot: sprite(64, 64, [[0, "rgba(255,252,225,1)"], [.2, "rgba(255,214,110,.95)"], [.5, "rgba(255,120,25,.45)"], [1, "rgba(210,50,0,0)"]]),
      flame: sprite(64, 64, [[0, "rgba(255,190,90,.9)"], [.35, "rgba(255,110,20,.55)"], [1, "rgba(160,30,0,0)"]]),
      ember: sprite(16, 16, [[0, "rgba(255,230,160,1)"], [.4, "rgba(255,140,40,.8)"], [1, "rgba(255,80,0,0)"]]),
      smoke: sprite(64, 64, [[0, "rgba(40,32,28,.55)"], [.6, "rgba(30,24,22,.25)"], [1, "rgba(20,16,14,0)"]]),
      glow: sprite(128, 128, [[0, "rgba(255,170,70,.55)"], [.4, "rgba(255,120,40,.18)"], [1, "rgba(255,90,20,0)"]]),
    };
  }

  // ---------------------------------------------------------------- the torch sconces: a small canvas on each, in the photo
  function makeSconces() {
    const box = document.querySelector("#home .photoBox");
    return SCONCES.map(s => {
      const w = Math.round(s.h * 1.3), h = Math.round(s.h * 2.1);
      const cv = document.createElement("canvas"); cv.className = "sconce";
      cv.width = Math.round(w / 2); cv.height = Math.round(h / 2);             // half resolution: flames are soft anyway
      Object.assign(cv.style, { position: "absolute", left: (s.x - w / 2) + "px", top: (s.y - h * 0.82) + "px",
                                width: w + "px", height: h + "px", pointerEvents: "none", mixBlendMode: TVMODE ? "normal" : "screen" });
      box.appendChild(cv);
      return { s, cv, g: cv.getContext("2d"), w, h, ps: [], seed: Math.random() * 10 };
    });
  }
  function drawSconces(t, dt) {
    if (TVMODE) { sconceDt += dt; if (!everyOther) return; dt = sconceDt; sconceDt = 0; }   // 30 fps on the TV: plenty for flicker
    for (const sc of sconces) {
      const { g, w, h, s } = sc, k = 0.5;                                      // drawing at half resolution
      const flick = 0.75 + 0.25 * Math.sin(t * 9.3 + sc.seed) * Math.sin(t * 3.7 + sc.seed * 2) + 0.08 * Math.sin(t * 23 + sc.seed);
      for (let n = dt * 46; n > 0; n--) {
        if (n < 1 && Math.random() > n) break;
        sc.ps.push({ x: w / 2 + (Math.random() - .5) * s.h * .28, y: h * .82, vx: (Math.random() - .5) * 18, vy: -(55 + Math.random() * 75) * s.h / 100,
                     life: .35 + Math.random() * .4, age: 0, r: (.16 + Math.random() * .14) * s.h });
      }
      g.setTransform(k, 0, 0, k, 0, 0);
      g.globalCompositeOperation = "source-over"; g.clearRect(0, 0, w, h);
      g.globalCompositeOperation = "lighter";
      g.globalAlpha = .55 * flick; g.drawImage(sprites.glow, w / 2 - s.h * .65, h * .82 - s.h * .95, s.h * 1.3, s.h * 1.3);
      sc.ps = sc.ps.filter(p => (p.age += dt) < p.life);
      for (const p of sc.ps) {
        const u = p.age / p.life;
        p.x += (p.vx + Math.sin((t + p.r) * 7) * 14) * dt; p.y += p.vy * dt; p.vx *= .97;
        const r = p.r * (1 - u * .65);
        g.globalAlpha = (1 - u) * .8 * flick;
        g.drawImage(u < .35 ? sprites.hot : sprites.flame, p.x - r, p.y - r, r * 2, r * 2);
      }
      if (Math.random() < dt * 3) sc.ps.push({ x: w / 2, y: h * .7, vx: (Math.random() - .5) * 40, vy: -120, life: .8, age: 0, r: 2.5 });   // a spark
    }
  }

  // ---------------------------------------------------------------- the fire breath: a loop of real fire footage
  // FIRE_SHEET (dragon/fire.js): frames of a flame jet streaming left to right, already see-through (black made clear),
  // so no blend mode is needed (the TV's GPU can't afford them). The canvas sits with its left edge at the jaws and
  // is turned towards the icon; while the jaws open the jet grows out to the icon, then it dies back.
  function makeJet() {
    const F = window.FIRE_SHEET; if (!F) return null;
    const img = new Image(); img.src = F.src;
    const cv = document.createElement("canvas"); cv.width = F.w; cv.height = F.h;
    Object.assign(cv.style, { position: "absolute", left: "0", top: "0", pointerEvents: "none", visibility: "hidden",
                              transformOrigin: `0px ${F.srcY}px`, willChange: "transform, opacity" });
    $("#home").insertBefore(cv, $("#mascot"));                    // behind the dragon, over the dock
    return { F, img, cv, g: cv.getContext("2d"), last: -1 };
  }
  function drawJet(t) {
    if (!jet) return;
    const sh = SHEET.breath, act = owl.act, [f0, f1] = sh ? sh.fire : [0, 0];
    const live = act && act.k === "breath" && act.t > f0 && act.t < f1 + .06;     // only while the jaws are open
    if (!live || !jet.img.complete) { css(jet.cv, "visibility", "hidden"); return; }
    // straight out of the jaws, level, through the icon and out past it on the far side: the blast engulfs it
    const F = jet.F, [mx, my] = mouth(), tx = appX(owl.at) + owl.face * 90, ty = ICON_Y + 6;
    const ang = Math.atan2(ty - my, tx - mx), dist = Math.hypot(tx - mx, ty - my);
    const grow = clamp((act.t - f0) / .32, 0, 1), die = clamp((f1 + .06 - act.t) / .22, 0, 1);
    const full = Math.max(240, dist + 40), len = full * ease(grow);
    const k = full / F.w;                                        // the footage's length, scaled to the blast
    css(jet.cv, "visibility", "visible");
    jet.cv.style.opacity = (Math.min(1, grow * 2) * die).toFixed(3);
    // facing left the jet turns round: flip it top to bottom too, so it is mirrored rather than upside down
    const flip = Math.abs(ang) > Math.PI / 2 ? -1 : 1;
    jet.cv.style.transform = `translate3d(${mx.toFixed(1)}px, ${(my - F.srcY).toFixed(1)}px, 0) rotate(${ang.toFixed(3)}rad) scale(${k.toFixed(3)}, ${(flip * k * 1.3).toFixed(3)})`;
    const f = Math.floor(t * F.fps) % F.n;
    if (f === jet.last && grow >= 1) return;
    jet.last = f;
    const [sx, sy] = F.frames[f], g = jet.g;
    const reach = Math.max(1, len / k);                          // only as far as it has grown, in sheet pixels
    g.globalCompositeOperation = "source-over";
    g.clearRect(0, 0, F.w, F.h);
    g.drawImage(jet.img, sx, sy, reach, F.h, 0, 0, reach, F.h);
    g.globalCompositeOperation = "destination-in";               // a soft tip: the flames thin out, no hard edge
    if (!jet.tip || jet.tipAt !== Math.round(reach)) {
      jet.tip = g.createLinearGradient(0, 0, reach, 0); jet.tipAt = Math.round(reach);
      jet.tip.addColorStop(0, "rgba(0,0,0,1)"); jet.tip.addColorStop(0.62, "rgba(0,0,0,1)"); jet.tip.addColorStop(1, "rgba(0,0,0,0)");
    }
    g.fillStyle = jet.tip; g.fillRect(0, 0, reach, F.h);
    g.globalCompositeOperation = "source-over";
  }

  // ---------------------------------------------------------------- the dragon: where its mouth is, the fire, the roast
  function mouth() {                                                            // in screen coordinates, for the breath clip's frame now
    const sh = SHEET.breath, f = Math.min(sh.n - 1, Math.floor(owl.act.t * sh.fps));
    const [mx, my] = sh.mouth[f], k = OWL_H / sh.h;
    return [owl.x + owl.face * (mx - sh.footX) * k, owl.y + (my - sh.footY) * k];
  }
  function breathe(t, dt) {
    if (owl.hopping || !SHEET.breath) { settled = 0; return; }
    if (owl.act) {
      owl.act.t += dt;
      const sh = SHEET.breath, [f0, f1] = sh.fire;
      if (owl.act.t > f0 + .12 && owl.act.t < f1 - .1) {                       // jaws forward: fire
        const [mx, my] = mouth(), tx = appX(owl.at) + 10, ty = 846;
        const dx = tx - mx, dy = ty - my, d = Math.hypot(dx, dy) || 1;
        if (!jet) for (let n = dt * 120; n > 0; n--) {        // (no fire footage: particles instead)
          if (n < 1 && Math.random() > n) break;
          const sp = 3.6 * (d + 25) * (0.8 + Math.random() * 0.45), side = (Math.random() - .5) * 110;
          fire.push({ x: mx, y: my, vx: dx / d * sp - dy / d * side, vy: dy / d * sp + dx / d * side, age: 0, life: .42 + Math.random() * .22,
                      r: 5 + Math.random() * 6, kind: "fire" });
        }
        if (Math.random() < dt * 14) fire.push({ x: tx + (Math.random() - .5) * 80, y: ty - Math.random() * 30, vx: (Math.random() - .5) * 120,
                                                  vy: -60 - Math.random() * 90, age: 0, life: .6 + Math.random() * .5, r: 1.6 + Math.random() * 1.6, kind: "ember" });
        heat(owl.at, dt, true);
      }
      if (owl.act.t >= SHEET.breath.n / SHEET.breath.fps) { owl.act = null; nextBreath = t + 11 + Math.random() * 9; }
      return;
    }
    settled += dt;
    if ((owl.at !== landedOn && settled > .55) || (nextBreath && t > nextBreath)) {   // just landed on a new app, or now and then
      owl.act = { k: "breath", t: 0 }; landedOn = owl.at; nextBreath = 0;
    }
  }
  function heat(i, dt, hit) {
    const r = roast[i] = roast[i] || { heat: 0, char: 0, el: null };
    if (hit) { r.heat = Math.min(1, r.heat + dt * 2.6); r.char = Math.min(.9, r.char + dt * .55); }
  }
  function roastIcons(dt) {
    for (const i in roast) {
      const r = roast[i], cell = cells[i];
      if (!cell) { delete roast[i]; continue; }
      const el = r.el = r.el && r.el.isConnected ? r.el : cell.logo.querySelector("img, span");
      const firing = owl.act && owl.at == i && owl.act.t > SHEET.breath.fire[0] + .25 && owl.act.t < SHEET.breath.fire[1];
      if (!firing) { r.heat = Math.max(0, r.heat - dt * 1.4); r.char = Math.max(0, r.char - dt * (r.heat > .1 ? .05 : .32)); }
      if (r.char > .25 && Math.random() < dt * 9 * r.char) {                   // embers and smoke off the charred icon
        fire.push({ x: appX(i) + (Math.random() - .5) * 120, y: 838 + (Math.random() - .5) * 50, vx: (Math.random() - .5) * 30, vy: -30 - Math.random() * 50,
                    age: 0, life: .9 + Math.random() * .8, r: 1.5 + Math.random() * 2, kind: "ember" });
        if (Math.random() < .35) fire.push({ x: appX(i) + (Math.random() - .5) * 90, y: 820, vx: (Math.random() - .5) * 12, vy: -35, age: 0, life: 1.6, r: 18, kind: "smoke" });
      }
      if (!el) continue;
      if (r.heat < .01 && r.char < .01) { el.style.filter = ""; delete roast[i]; continue; }
      const h = r.heat, c = r.char;
      el.style.filter = `brightness(${(1 + .55 * h - .62 * c).toFixed(3)}) sepia(${Math.min(1, .9 * h + c).toFixed(3)}) saturate(${(1 + 2.4 * h - .4 * c).toFixed(3)}) ` +
                        `hue-rotate(${(-22 * h).toFixed(1)}deg) drop-shadow(0 0 ${(4 + 22 * h).toFixed(1)}px rgba(255,${(110 - 40 * c) | 0},20,${(.9 * h + .25 * c).toFixed(3)}))`;
    }
  }
  function drawFire(dt) {
    if (!fire.length) return;
    css($("#dsp"), "visibility", "visible");
    fire = fire.filter(p => (p.age += dt) < p.life);
    for (const p of fire) {
      const u = p.age / p.life;
      if (p.kind === "fire") {
        p.vx *= 1 - 2.2 * dt; p.vy *= 1 - 2.2 * dt; p.vy -= 260 * dt * u;              // slows, spreads and rises as it burns
        if (p.y > 852) { p.y = 852; p.vy = -Math.abs(p.vy) * .3; p.vx *= 1.3; }        // splashes on the dock
      } else p.vy -= (p.kind === "smoke" ? 6 : 14) * dt;
      p.x += p.vx * dt; p.y += p.vy * dt;
    }
    dctx.globalCompositeOperation = "source-over";
    for (const p of fire) if (p.kind === "smoke") {
      const u = p.age / p.life, r = p.r * (1 + 2 * u);
      dctx.globalAlpha = .5 * Math.sin(Math.PI * u); dctx.drawImage(sprites.smoke, p.x - r, p.y - r, r * 2, r * 2);
    }
    dctx.globalCompositeOperation = "lighter";
    for (const p of fire) {
      if (p.kind === "smoke") continue;
      const u = p.age / p.life;
      if (p.kind === "ember") { dctx.globalAlpha = Math.min(1, 3 * (1 - u)); dctx.drawImage(sprites.ember, p.x - p.r * 2, p.y - p.r * 2, p.r * 4, p.r * 4); continue; }
      const r = p.r + u * 62;
      dctx.globalAlpha = (1 - u) * (u < .15 ? u / .15 : 1) * .85;
      dctx.drawImage(u < .3 ? sprites.hot : sprites.flame, p.x - r, p.y - r, r * 2, r * 2);
    }
    dctx.globalAlpha = 1; dctx.globalCompositeOperation = "source-over";
  }

  window.sceneHome = (t, dt) => {
    if (!on) return;
    drawSconces(t, dt);
    breathe(t, dt);
    drawJet(t);
    roastIcons(dt);
    drawFire(dt);
  };

  // ---------------------------------------------------------------- switching to and from this scene
  SCENES.got = {
    garden: false,
    enter() {
      if (on) return;
      on = true;
      sprites = sprites || makeSprites();
      document.body.dataset.nogarden = "1";
      const D = window.DRAGON_SHEETS, b = D.breath;
      const [f0, f1] = b.fire, mid = Math.floor((f0 + f1) / 2 * b.fps);
      // stand beside the app, low on the floor in front of the dock, so that at full lunge the open jaws are level
      // with the icon's middle and GAP px to its side: the fire then leaves the mouth level, the way the head points
      const reach = b.mouth[mid][0] - b.footX, rise = b.footY - b.mouth[mid][1], GAP = 92;
      window.DOCK_CELL = 380; buildDock();                       // room between the apps for the dragon to land in
      const big = 1.12;                                     // a touch bigger than its sheets, to read from the sofa
      useMascot(D, D.idle.h * big, { up: D.hop.up, down: D.hop.down, end: D.hop.end, rate: 1.6 },
                { perchDx: reach * big + GAP, perchY: () => ICON_Y + rise * big });
      sconces = makeSconces();
      jet = jet || makeJet();
      setFilms(seasons()); coverFallbacks();
      const lib = $("#toLib"); if (lib && lib.firstChild) lib.firstChild.textContent = "The Seasons";
      landedOn = -1; settled = 0; nextBreath = 0;
    },
    leave() {
      if (!on) return;
      on = false;
      delete document.body.dataset.nogarden;
      if (section === 2) section = 1;
      sconces.forEach(sc => sc.cv.remove()); sconces = [];
      if (jet) { jet.cv.remove(); jet = null; }
      for (const i in roast) if (roast[i].el) roast[i].el.style.filter = "";
      roast = {}; fire = [];
      window.DOCK_CELL = 0; buildDock();
      useMascot(OWL_SHEETS, 128, { up: 0.65, down: 2.22, end: 3.0, rate: 1.7 });
      setFilms(FILMS_HP);
      const lib = $("#toLib"); if (lib && lib.firstChild) lib.firstChild.textContent = "The Library";
    },
  };
})();
