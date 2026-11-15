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
  const SCONCES = [{ x: 1748, y: 348, h: 112 }, { x: 182, y: 350, h: 112 }, { x: 402, y: 471, h: 80 }, { x: 1525, y: 473, h: 80 }];   // each basket's centre, just inside its rim (photo px)

  const SEASONS = [   // [year, episodes, leather colour, what happens (four words at most)]
    [2011, 10, "#5c1c16", "Ned Stark loses everything"], [2012, 10, "#2a3644", "Battle of the Blackwater"], [2013, 10, "#4a2a1a", "The Red Wedding"],
    [2014, 10, "#3c2040", "Joffrey's poisoned wedding"], [2015, 10, "#24382c", "Jon Snow is betrayed"], [2016, 10, "#5e2c10", "Battle of the Bastards"],
    [2017, 7, "#1e2c46", "Dragons meet the dead"], [2019, 6, "#3a3434", "The Iron Throne falls"]];
  const WORDS = ["One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight"];

  let on = false, ready = false, sconces = [], fire = [], roast = {}, landedOn = -1, settled = 0, nextBreath = 0, sprites = null, covers = null;
  let jet = null, sconceDt = 0, sky = null, room = null;
  // the glass of the three windows, in the photo's 1935 x 1080 frame (tools/got_windows.py) and its mask
  const GLASS = { x: 613.2, y: 7.6, w: 702.0, h: 499.8, mask: "img/got/glass.png" };
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

  // ---------------------------------------------------------------- the torch sconces: real flame footage (tools/got_torch.py)
  // Each sconce has a small canvas in the photo, playing the flame loop from its own starting point and at its own
  // speed, over a soft flickering glow on the stone. The darker the room, the brighter the torches look.
  function makeSconces() {
    const box = document.querySelector("#home .photoBox"), T = window.TORCH_SHEET;
    const img = T ? Object.assign(new Image(), { src: T.src }) : null;
    return SCONCES.map((s, i) => {
      const w = Math.round(s.h * 1.5), h = Math.round(s.h * 2.0);
      const cv = document.createElement("canvas"); cv.className = "sconce";
      cv.width = Math.round(w * .75); cv.height = Math.round(h * .75);
      Object.assign(cv.style, { position: "absolute", left: (s.x - w / 2) + "px", top: (s.y - h * 0.86) + "px",
                                width: w + "px", height: h + "px", pointerEvents: "none" });
      box.appendChild(cv);
      return { s, cv, g: cv.getContext("2d"), w, h, img, T, phase: i * 9.7 + Math.random() * 3, rate: .55 + Math.random() * .12, seed: Math.random() * 10 };
    });
  }
  function drawSconces(t, dt) {
    if (TVMODE) { sconceDt += dt; if (!everyOther) return; dt = sconceDt; sconceDt = 0; }   // 30 fps on the TV
    const dark = sky && sky.wx ? sky.wx.light.dark : 1, bright = .55 + .45 * dark;
    for (const sc of sconces) {
      const { g, s, cv } = sc, k = cv.width / sc.w;
      const flick = .88 + .12 * Math.sin(t * 2.9 + sc.seed) * Math.sin(t * 1.3 + sc.seed * 2);   // a slow breathing glow
      g.setTransform(k, 0, 0, k, 0, 0); g.clearRect(0, 0, sc.w, sc.h);
      g.globalAlpha = .5 * flick * bright; g.globalCompositeOperation = "lighter";
      g.drawImage(sprites.glow, sc.w / 2 - s.h * .7, sc.h * .86 - s.h * 1.05, s.h * 1.4, s.h * 1.4);
      g.globalCompositeOperation = "source-over";
      if (sc.img && sc.img.complete && sc.img.naturalWidth) {
        // played slower than filmed, so each frame is blended into the next (no stepping)
        const T = sc.T, pos = (t * sc.rate + sc.phase) * T.fps, f = Math.floor(pos) % T.n, mix = pos - Math.floor(pos);
        const fh = s.h * 1.18, fw = fh * T.w / T.h, x = sc.w / 2 - fw / 2, y = sc.h * .86 - fh, a = .8 + .2 * dark;
        const [fx, fy] = T.frames[f], [nx, ny] = T.frames[(f + 1) % T.n];
        g.globalAlpha = a; g.drawImage(sc.img, fx, fy, T.w, T.h, x, y, fw, fh);
        g.globalAlpha = a * mix; g.drawImage(sc.img, nx, ny, T.w, T.h, x, y, fw, fh);
      }
    }
  }

  // ---------------------------------------------------------------- the sky through the windows (themes/weather.js)
  // One small canvas over the glass (half resolution: the sky is soft anyway), redrawn ~30 times a second: the sky's
  // colour for the time of day, stars, the sun's glow and the moon (its real position and phase), two layers of clouds
  // drifting with the wind, rain or snow outside, lightning. The glass mask keeps the stone tracery and the throne in front.
  const SKY = {   // colours of the sky's top and bottom for each kind of light
    night: ["#060b18", "#14223e"], dawn: ["#2a3358", "#d98a6a"], golden: ["#3d5a8c", "#f2a65a"], day: ["#4f86c6", "#bcd6ee"] };
  const mixc = (a, b, t) => { const p = x => x[0] === "#" ? [1, 3, 5].map(k => parseInt(x.substr(k, 2), 16)) : x.match(/\d+/g).map(Number);
                               const A = p(a), B = p(b);
                               return `rgb(${A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(",")})`; };
  function noiseTile(w, h, seed) {             // tileable cloud texture (value-noise fbm), alpha only: made once
    const c = document.createElement("canvas"); c.width = w; c.height = h;
    const g = c.getContext("2d"), im = g.createImageData(w, h);
    let s = seed; const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
    const grids = [4, 8, 16, 32].map(n => { const a = []; for (let i = 0; i < n * (n >> 1 || 1); i++) a.push(rnd()); return { n, m: n >> 1 || 1, a }; });
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let v = 0, amp = .55;
      for (const G of grids) {
        const fx = x / w * G.n, fy = y / h * G.m, ix = Math.floor(fx), iy = Math.floor(fy), tx = fx - ix, ty = fy - iy;
        const at = (i, j) => G.a[((j % G.m + G.m) % G.m) * G.n + ((i % G.n) + G.n) % G.n];
        const sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
        v += amp * ((at(ix, iy) * (1 - sx) + at(ix + 1, iy) * sx) * (1 - sy) + (at(ix, iy + 1) * (1 - sx) + at(ix + 1, iy + 1) * sx) * sy);
        amp *= .5;
      }
      im.data[(y * w + x) * 4 + 3] = Math.round(Math.min(1, v) * 255);
    }
    g.putImageData(im, 0, 0); return c;
  }
  function makeSky() {
    const box = document.querySelector("#home .photoBox"), K = 0.5;
    const cv = document.createElement("canvas"); cv.className = "gotsky";
    cv.width = Math.round(GLASS.w * K); cv.height = Math.round(GLASS.h * K);
    Object.assign(cv.style, { position: "absolute", left: GLASS.x + "px", top: GLASS.y + "px", width: GLASS.w + "px", height: GLASS.h + "px", pointerEvents: "none" });
    box.insertBefore(cv, box.children[1] || null);                       // over the photo, under the mist
    const flash = document.createElement("div"); flash.className = "gotflash";
    Object.assign(flash.style, { position: "absolute", inset: "0", pointerEvents: "none", background: "#cfe0ff", opacity: "0" });
    box.appendChild(flash);
    const mask = new Image(); mask.src = GLASS.mask;
    const stars = Array.from({ length: 70 }, () => ({ x: Math.random(), y: Math.random() * .8, r: .5 + Math.random() * .9, p: Math.random() * 6 }));
    const c1 = Object.assign(new Image(), { src: "img/got/clouds_wispy.webp" }), c2 = Object.assign(new Image(), { src: "img/got/clouds_puffy.webp" });
    return { cv, g: cv.getContext("2d"), K, flash, mask, stars, c1, c2,
             drops: [], bolt: null, nextBolt: 0, flashT: 0, tint: null, tintKey: "", acc: 0, wx: null, wxAt: 0 };
  }
  // where the sun or moon is in the windows: the windows face south, so east is left; 0° high is the glass's bottom
  // the sun and moon mostly cross the middle window, above the throne (its spikes reach ~42% down the glass); far
  // east or west they move into the side windows. Their height: from just above the spikes (0°) to the window tops.
  const skyX = (az, w) => {
    const a = Math.max(-110, Math.min(110, az));
    if (Math.abs(a) <= 60) return w * (0.5 + a / 60 * 0.085);
    const side = (Math.abs(a) - 60) / 50;                                  // 0..1 across a side window
    return w * (a > 0 ? 0.83 + side * 0.14 : 0.17 - side * 0.14);
  };
  const skyY = (alt, h) => h * (0.40 - Math.max(-6, Math.min(alt, 70)) / 70 * 0.33);
  function drawMoon(g, x, y, R, m) {
    const s = m.waxing ? -1 : 1, k = 2 * m.frac - 1;                    // the dark limb's side; -1 new .. 1 full
    const glow = g.createRadialGradient(x, y, R * .8, x, y, R * 4);
    glow.addColorStop(0, `rgba(210,225,255,${.35 * m.frac})`); glow.addColorStop(1, "rgba(210,225,255,0)");
    g.fillStyle = glow; g.fillRect(x - R * 4, y - R * 4, R * 8, R * 8);
    g.fillStyle = "#e9eef7"; g.beginPath(); g.arc(x, y, R, 0, 7); g.fill();
    g.fillStyle = "rgba(28,36,58,.93)"; g.beginPath();                     // the shadowed part
    g.arc(x, y, R + .3, -Math.PI / 2, Math.PI / 2, s < 0);
    g.ellipse(x, y, Math.abs(k) * R, R + .3, 0, Math.PI / 2, -Math.PI / 2, (k > 0) !== (s < 0));
    g.fill();
  }
  function cloudTint(sk, w, light) {           // the cloud panoramas coloured for the light and weather now (redone when they change)
    if (!sk.c1.complete || !sk.c1.naturalWidth || !sk.c2.complete || !sk.c2.naturalWidth) return null;
    const key = `${w.kind}|${light.night.toFixed(1)}|${light.day.toFixed(1)}|${light.golden.toFixed(1)}|${light.dawn.toFixed(1)}`;
    if (sk.tintKey === key) return sk.tint;
    const storm = /thunder|heavyrain|blizzard/.test(w.kind) ? 1 : /rain|snow|overcast|drizzle/.test(w.kind) ? .45 : 0;
    let col = mixc(mixc(mixc("#5a6886", "#e4e8ef", light.day), "#ffb27a", light.golden * .85), "#c8909a", light.dawn * .7);
    col = mixc(col, light.day > .5 ? "#7d838e" : "#2b3140", storm);
    const out = [sk.c1, sk.c2].map(src => {
      const W = 512, H = Math.round(512 * src.naturalHeight / src.naturalWidth);
      const c = document.createElement("canvas"); c.width = W; c.height = H;
      const g = c.getContext("2d");
      g.drawImage(src, 0, 0, W, H);
      g.globalCompositeOperation = "multiply"; g.fillStyle = col; g.fillRect(0, 0, W, H);          // tint, keeping the shading
      g.globalCompositeOperation = "destination-in"; g.drawImage(src, 0, 0, W, H);                // and the cloud's own shape
      return c;
    });
    sk.tint = out; sk.tintKey = key; return out;
  }
  function bolt(w, h) {                        // a lightning bolt: midpoint displacement, with a branch
    const pts = [[w * (.25 + Math.random() * .5), -5], [w * (.2 + Math.random() * .6), h * (.75 + Math.random() * .2)]];
    for (let pass = 0; pass < 5; pass++) for (let i = pts.length - 1; i > 0; i--) {
      const [ax, ay] = pts[i - 1], [bx, by] = pts[i], o = (Math.hypot(bx - ax, by - ay)) * .25;
      pts.splice(i, 0, [(ax + bx) / 2 + (Math.random() - .5) * o, (ay + by) / 2 + (Math.random() - .5) * o * .3]);
    }
    return pts;
  }
  function drawSky(t, dt) {
    const sk = sky; if (!sk) return;
    sk.acc += dt; if (TVMODE && !everyOther) return; const step = sk.acc; sk.acc = 0;
    if (!sk.wx || t - sk.wxAt > 2) { sk.wx = PANTHEON_WX.now(); sk.wxAt = t; }   // the weather, re-read every 2 s
    const w = sk.wx, L = w.light, g = sk.g, W = sk.cv.width, H = sk.cv.height;
    // sky colour
    const top = mixc(mixc(mixc(SKY.night[0], SKY.dawn[0], L.dawn), SKY.golden[0], L.golden), SKY.day[0], L.day);
    const bot = mixc(mixc(mixc(SKY.night[1], SKY.dawn[1], L.dawn), SKY.golden[1], L.golden), SKY.day[1], L.day);
    const storm = /thunder|heavyrain|blizzard/.test(w.kind) ? .55 : w.kind === "overcast" || w.kind === "rain" || w.kind === "snow" ? .3 : 0;
    const grad = g.createLinearGradient(0, 0, 0, H);
    grad.addColorStop(0, mixc(top, "#1a1e26", storm));
    grad.addColorStop(1, mixc(bot, "#2a2f38", storm));
    g.globalCompositeOperation = "source-over"; g.globalAlpha = 1; g.fillStyle = grad; g.fillRect(0, 0, W, H);
    // stars, on clear-ish nights
    const starA = L.night * (1 - w.cloud) * (1 - w.fog);
    if (starA > .02) for (const st of sk.stars) { g.globalAlpha = starA * (.55 + .45 * Math.sin(t * 2 + st.p)); g.fillStyle = "#fff"; g.fillRect(st.x * W, st.y * H, st.r, st.r); }
    // the sun's glow and the moon
    if (w.sun.alt > -4) {
      const sx = skyX(w.sun.az, W), sy = skyY(w.sun.alt, H), a = Math.max(0, 1 - w.cloud * .8) * (L.day + L.golden + L.dawn * .6);
      const sg = g.createRadialGradient(sx, sy, 2, sx, sy, H * .6);
      sg.addColorStop(0, `rgba(255,${L.golden > .3 ? 210 : 245},${L.golden > .3 ? 150 : 225},${.9 * a})`); sg.addColorStop(1, "rgba(255,230,200,0)");
      g.globalAlpha = 1; g.fillStyle = sg; g.fillRect(0, 0, W, H);
    }
    if (w.moon.alt > -3 && L.day < .8) { g.globalAlpha = (1 - L.day) * (1 - .6 * w.fog); drawMoon(g, skyX(w.moon.az, W), skyY(w.moon.alt, H), H * .045, w.moon); }
    // clouds: two layers drifting with the wind (and a little even on a calm day)
    const tinted = cloudTint(sk, w, L), speed = 2 + w.wind * .3;
    if (tinted && w.cloud > .03) {
      const [k1, k2] = tinted;
      sk.o1 = ((sk.o1 || 0) + step * speed) % k1.width; sk.o2 = ((sk.o2 || 0) + step * speed * 1.6) % k2.width;
      const layer = (img, off, y, hgt, a) => { g.globalAlpha = a; const sw = img.width * (hgt / img.height);
        for (let x = -((off / img.width) * sw); x < W; x += sw) g.drawImage(img, x, y, sw, hgt); };
      const cov = w.cloud;
      layer(k1, sk.o1, -H * .02, H * .62, Math.min(1, .25 + cov * .9));                       // high, wind-torn veils
      if (cov > .3) layer(k2, sk.o2, H * (.28 - cov * .1), H * (.32 + cov * .25), Math.min(1, (cov - .3) * 1.6));   // lower, heavier masses
      if (cov > .8) { g.globalAlpha = (cov - .8) * 3; g.fillStyle = mixc(L.day > .5 ? "#9aa1ab" : "#262b36", "#1b1e25", storm); g.fillRect(0, 0, W, H); }   // a full overcast
    }
    // rain or snow, outside the glass
    const want = Math.round(w.rain * 140 + w.snow * 170);
    while (sk.drops.length < want) sk.drops.push({ x: Math.random() * W, y: Math.random() * H, v: 0.6 + Math.random() * .6, ph: Math.random() * 6 });
    if (sk.drops.length > want) sk.drops.length = want;
    if (want) {
      const slant = Math.min(1.2, w.wind / 40), snow = w.snow > w.rain;
      g.globalAlpha = snow ? .9 : .45; g.strokeStyle = g.fillStyle = L.day > .5 ? "#eef3fa" : "#b9c8e2"; g.lineWidth = .8;
      g.beginPath();
      for (const p of sk.drops) {
        if (snow) { p.y += step * (14 + 26 * p.v) * (w.kind === "blizzard" ? 2.4 : 1); p.x += step * (Math.sin(t + p.ph) * 6 + slant * 40 * (w.kind === "blizzard" ? 2 : 1)); }
        else { p.y += step * (170 + 90 * p.v); p.x += step * slant * 90; }
        if (p.y > H) { p.y -= H + 10; p.x = Math.random() * W; } if (p.x > W) p.x -= W; if (p.x < 0) p.x += W;
        if (snow) g.rect(p.x, p.y, 1.3 * p.v + .5, 1.3 * p.v + .5); else { g.moveTo(p.x, p.y); g.lineTo(p.x - slant * 5, p.y - 9); }
      }
      if (snow) g.fill(); else g.stroke();
    }
    // fog: the far side goes milky
    if (w.fog > .02) { g.globalAlpha = .75 * w.fog; g.fillStyle = L.day > .5 ? "#c4c9cf" : "#2c3340"; g.fillRect(0, 0, W, H); }
    // lightning, in a thunderstorm: a bolt in the windows, the hall lit for a moment, thunder a little later
    if (w.kind === "thunder") {
      if (!sk.nextBolt) sk.nextBolt = t + 3 + Math.random() * 6;
      if (t > sk.nextBolt) { sk.bolt = { pts: bolt(W, H), t0: t }; sk.flashT = t; sk.nextBolt = t + 6 + Math.random() * 14;
                             if (window.pantheonAudio) setTimeout(() => pantheonAudio.thunder(), 700 + Math.random() * 2200); }
    } else sk.nextBolt = 0;
    if (sk.bolt) {
      const age = t - sk.bolt.t0;
      if (age > .45) sk.bolt = null;
      else if (age < .1 || (age > .18 && age < .28)) {
        g.globalAlpha = .35; g.fillStyle = "#dfe8ff"; g.fillRect(0, 0, W, H);
        g.beginPath(); sk.bolt.pts.forEach(([x, y], i) => (i ? g.lineTo(x, y) : g.moveTo(x, y)));
        g.globalAlpha = .3; g.strokeStyle = "#a9c3ff"; g.lineWidth = 6; g.stroke();         // its glow (no shadowBlur: slow on the TV)
        g.globalAlpha = 1; g.strokeStyle = "#f4f7ff"; g.lineWidth = 1.6; g.stroke();
      }
    }
    const fa = sk.flashT ? Math.max(0, 1 - (t - sk.flashT) / .7) : 0;
    css(sk.flash, "opacity", (fa * (fa > .7 || (fa > .35 && fa < .55) ? .32 : .12)).toFixed(3));
    // only the glass
    if (sk.mask.complete && sk.mask.naturalWidth) { g.globalAlpha = 1; g.globalCompositeOperation = "destination-in"; g.drawImage(sk.mask, 0, 0, W, H); }
    g.globalCompositeOperation = "source-over"; g.globalAlpha = 1;
  }

  // ---------------------------------------------------------------- the room itself: its light follows the sun and weather
  // Four versions of the same room (night = the theme's picture; overcast, sunny day and golden hour, made to line up
  // with it) are stacked in the photo and blended by the sun's height and the cloud: layer k's opacity is
  // w_k / (w_0 + ... + w_k), so stacking them gives exactly the weighted mix. The blend eases slowly (no sudden
  // changes). A storm shades the hall; fog drifts across the floor; on sunny days light shafts slant with the sun.
  const ROOM = [["overcast", "img/got/throne_overcast.jpg"], ["golden", "img/got/throne_golden.jpg"], ["day", "img/got/throne_day.jpg"]];
  function makeRoom() {
    const box = document.querySelector("#home .photoBox"), base = box.querySelector("img");
    const layers = ROOM.map(([k, src]) => {
      const im = new Image(); im.src = src; im.className = "gotroom";
      Object.assign(im.style, { position: "absolute", left: "0", top: "0", width: "100%", height: "100%", opacity: "0", pointerEvents: "none" });
      base.after(im); return { k, im, a: 0 };
    }).reverse();                                                          // (after() inserts in reverse: now overcast, golden, day)
    layers.sort((x, y) => ROOM.findIndex(r => r[0] === x.k) - ROOM.findIndex(r => r[0] === y.k));
    let last = base; layers.forEach(l => { last.after(l.im); last = l.im; });
    const shade = document.createElement("div"); shade.className = "gotshade";
    Object.assign(shade.style, { position: "absolute", inset: "0", background: "#05070c", opacity: "0", pointerEvents: "none" });
    last.after(shade);
    // fog: two soft bands drifting across the floor and the steps (moved by CSS transforms: cheap on the TV)
    const fog = [0, 1, 2].map(i => {                                           // the floor, nearer the floor, and mid-air
      const tile = noiseTile(256, 48, 13 + i * 31), c = document.createElement("canvas"); c.width = 1024; c.height = 140;
      const g = c.getContext("2d"); g.filter = "blur(6px)";
      g.drawImage(tile, 0, 0, 512, 140); g.drawImage(tile, 512, 0, 512, 140);
      g.filter = "none"; g.globalCompositeOperation = "source-in"; g.fillStyle = "#cfd6e0"; g.fillRect(0, 0, 1024, 140);
      g.globalCompositeOperation = "destination-in";                                            // soft top and bottom
      const v = g.createLinearGradient(0, 0, 0, 140); v.addColorStop(0, "rgba(0,0,0,0)"); v.addColorStop(.45, "rgba(0,0,0,1)"); v.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = v; g.fillRect(0, 0, 1024, 140);
      const wrap = document.createElement("div");
      Object.assign(wrap.style, { position: "absolute", left: "0", top: [470, 590, 250][i] + "px", width: "3870px", height: [230, 300, 420][i] + "px",
                                  pointerEvents: "none", willChange: "transform", opacity: "0",
                                  backgroundImage: `url(${c.toDataURL()})`, backgroundSize: `1935px 100%`, backgroundRepeat: "repeat-x" });
      shade.after(wrap); return { el: wrap, x: Math.random() * 1935, v: [5, 9, 3][i], k: [1, 1, .55][i] };
    });
    // light shafts: a few long soft beams from the windows, angled by the sun (redrawn only when the sun moves)
    const rays = document.createElement("canvas"); rays.width = 480; rays.height = 270; rays.className = "gotrays";
    Object.assign(rays.style, { position: "absolute", left: "0", top: "0", width: "100%", height: "100%", opacity: "0", pointerEvents: "none" });
    fog[2].el.after(rays);
    // dust in the sunlight: soft specks drifting slowly through the middle of the hall by day (CSS-moved: cheap)
    const dust = document.createElement("div"); dust.className = "gotdust";
    Object.assign(dust.style, { position: "absolute", inset: "0", pointerEvents: "none", opacity: "0", visibility: "hidden" });
    rays.after(dust);
    const motes = Array.from({ length: 46 }, () => {
      const el = document.createElement("div"), r = 4 + Math.random() * 7;
      Object.assign(el.style, { position: "absolute", left: "0", top: "0", width: r * 2 + "px", height: r * 2 + "px", borderRadius: "50%", willChange: "transform, opacity",
                                background: "radial-gradient(circle, #fff6dc 0%, rgba(255,236,190,.8) 22%, rgba(255,222,160,.25) 50%, rgba(255,220,160,0) 72%)" });
      dust.appendChild(el);
      return { el, x: 420 + Math.random() * 1100, y: 110 + Math.random() * 820, vx: (Math.random() - .5) * 9, vy: -1.5 - Math.random() * 4,
               ph: Math.random() * 7, tw: .5 + Math.random() * .5 };
    });
    return { layers, shade, fog, rays, dust, motes, rg: rays.getContext("2d"), rayAz: 999 };
  }
  function drawRoom(t, dt) {
    const R = room, w = sky && sky.wx; if (!R || !w) return;
    const L = w.light, cloudy = Math.min(1, Math.max(0, (w.cloud - .45) / .4));
    const storm = /thunder|heavyrain|blizzard/.test(w.kind) ? 1 : /rain|snow/.test(w.kind) ? .5 : 0;
    const wt = { night: L.night + L.dawn * .45, overcast: (L.day + L.golden + L.dawn * .55) * cloudy, golden: (L.golden + L.dawn * .55) * (1 - cloudy), day: L.day * (1 - cloudy) };
    let cum = wt.night;
    for (const l of R.layers) {
      cum += wt[l.k]; const target = cum > 1e-6 ? wt[l.k] / cum : 0;
      l.a += (target - l.a) * Math.min(1, dt / 3);                         // eases over a few seconds
      css(l.im, "opacity", l.a.toFixed(3)); css(l.im, "visibility", l.a > .003 ? "visible" : "hidden");
    }
    R.storm = (R.storm || 0) + (storm * (.18 + .1 * L.night) - (R.storm || 0)) * Math.min(1, dt / 3);
    css(R.shade, "opacity", R.storm.toFixed(3));
    // fog: a little, drifting through the hall at night (and in foggy or stormy weather); gone by day
    const night = L.night + L.dawn * .6;
    const fogA = Math.min(.6, .03 + .3 * night + w.fog * .4 + storm * .06);
    R.fog.forEach(f => { const a = fogA * f.k; css(f.el, "visibility", a > .01 ? "visible" : "hidden"); if (a <= .01) return;
                         f.x = (f.x + dt * f.v * (1 + w.wind / 30)) % 1935;
                         f.el.style.transform = `translate3d(${(-f.x).toFixed(1)}px,0,0)`; css(f.el, "opacity", a.toFixed(3)); });
    // dust in the light: morning to late afternoon, when the sun gets in
    const dustA = Math.min(1, (L.day + L.golden) * 1.2) * (1 - .75 * cloudy) * (1 - storm * .8) * (w.sun.alt > 0 ? 1 : 0);
    css(R.dust, "opacity", dustA.toFixed(3)); css(R.dust, "visibility", dustA > .01 ? "visible" : "hidden");
    if (dustA > .01) for (const m of R.motes) {
      m.ph += dt * .5; m.x += (m.vx + Math.sin(m.ph) * 4) * dt; m.y += (m.vy + Math.cos(m.ph * .7) * 2.5) * dt;
      if (m.y < 90) { m.y = 940; m.x = 420 + Math.random() * 1100; } if (m.x < 380) m.x += 1180; if (m.x > 1560) m.x -= 1180;
      m.el.style.transform = `translate3d(${m.x.toFixed(1)}px,${m.y.toFixed(1)}px,0)`;
      m.el.style.opacity = (m.tw * (.55 + .45 * Math.sin(m.ph * 1.7))).toFixed(2);
    }
    // shafts: only with the sun up and the sky not overcast
    const sunny = (L.day + L.golden) * (1 - cloudy) * Math.max(0, Math.min(1, w.sun.alt / 6));
    css(R.rays, "opacity", (sunny * (.55 + .1 * Math.sin(t * .4))).toFixed(3));
    if (sunny > .02 && Math.abs(w.sun.az - R.rayAz) > 1) {
      R.rayAz = w.sun.az; const g = R.rg, W = 480, H = 270;
      g.clearRect(0, 0, W, H); g.globalCompositeOperation = "lighter";
      const lean = Math.max(-1, Math.min(1, w.sun.az / 60)), drop = Math.max(.2, 1 - w.sun.alt / 60);   // sun in the west: beams lean east (left)
      const warm = L.golden > L.day ? "255,176,92" : "255,236,200";
      for (const [wx, ww] of [[GLASS.x + GLASS.w * .09, GLASS.w * .1], [GLASS.x + GLASS.w * .5, GLASS.w * .16], [GLASS.x + GLASS.w * .91, GLASS.w * .1]]) {
        const x0 = wx / 1935 * W, y0 = (GLASS.y + GLASS.h * .35) / 1080 * H, x1 = x0 - lean * W * .32 * drop, y1 = H * (.72 + .2 * drop), ww2 = ww / 1935 * W;
        const gr = g.createLinearGradient(x0, y0, x1, y1); gr.addColorStop(0, `rgba(${warm},.22)`); gr.addColorStop(1, `rgba(${warm},0)`);
        g.fillStyle = gr; g.beginPath(); g.moveTo(x0 - ww2 / 2, y0); g.lineTo(x0 + ww2 / 2, y0); g.lineTo(x1 + ww2 * 1.6, y1); g.lineTo(x1 - ww2 * 1.6, y1); g.fill();
      }
    }
  }

  // ---------------------------------------------------------------- sound: the fire, the music, and the weather outside
  // Fire crackle (sounds/got/fire.js: the torch footage's sound): an endless, never-repeating bed (and a soft roar) made of overlapping
  // snippets from random points in it, each faded in and out (Web Audio). Without Web Audio, two players take turns
  // with sounds/got/fire.mp3, cross-fading. Music: your own track
  // (sounds/got/music-local.mp3, see scripts/add-music.sh), if there is one, plays once each time you come to the
  // home; then the original score (sounds/got/music.mp3) loops. Rain, wind and thunder are synthesised to fit the weather
  // (Web Audio: filtered noise), so they never loop audibly. Quiet by default; muted from the top row; silent
  // whenever an app is open (the TV app calls pause/resume).
  const VOL = { fire: .14, music: .14, rain: .22, wind: .2, thunder: .55 };
  const audio = { on: false, muted: (() => { try { return localStorage.getItem("pantheon_mute") === "1"; } catch (e) { return false; } })(), paused: false };
  function startAudio() {
    if (audio.fire) return;
    audio.fire = [0, 1].map(() => Object.assign(new Audio("sounds/got/fire.mp3"), { volume: 0 }));
    audio.cur = 0; audio.fireLvl = 0; audio.musicLvl = 0; audio.theme = true;
    audio.music = Object.assign(new Audio(), { volume: 0 });
    audio.music.onended = () => { if (!audio.music.loop) toScore(); };      // your theme played through: on to the score
    audio.music.onerror = () => { if (audio.theme) toScore(); };           // no theme of your own
    playTheme();
    try {
      const AC = window.AudioContext || window.webkitAudioContext, ac = audio.ac = new AC();
      const n = ac.createBuffer(1, ac.sampleRate * 3, ac.sampleRate), d = n.getChannelData(0);
      let b0 = 0, b1 = 0, b2 = 0;                                                    // pink-ish noise
      for (let i = 0; i < d.length; i++) { const w = Math.random() * 2 - 1; b0 = .99765 * b0 + w * .099; b1 = .963 * b1 + w * .2965; b2 = .57 * b2 + w * 1.0527; d[i] = (b0 + b1 + b2 + w * .1848) * .2; }
      audio.noise = n;
      const src = () => { const s = ac.createBufferSource(); s.buffer = n; s.loop = true; s.start(0, Math.random() * 2.5); return s; };
      audio.master = ac.createGain(); audio.master.connect(ac.destination);
      // rain: a high hiss and a lower patter
      audio.rain = ac.createGain(); audio.rain.gain.value = 0; audio.rain.connect(audio.master);
      const hp = ac.createBiquadFilter(); hp.type = "highpass"; hp.frequency.value = 1200; src().connect(hp); hp.connect(audio.rain);
      const bp = ac.createBiquadFilter(); bp.type = "bandpass"; bp.frequency.value = 450; bp.Q.value = .7; const bg = ac.createGain(); bg.gain.value = .6;
      src().connect(bp); bp.connect(bg); bg.connect(audio.rain);
      // wind: a band of noise whose pitch and loudness gust
      audio.wind = ac.createGain(); audio.wind.gain.value = 0; audio.wind.connect(audio.master);
      const wf = audio.windF = ac.createBiquadFilter(); wf.type = "bandpass"; wf.frequency.value = 380; wf.Q.value = 1.4; src().connect(wf); wf.connect(audio.wind);
      // the crackle's bed
      audio.fireBus = ac.createGain(); audio.fireBus.gain.value = 0; audio.fireBus.connect(audio.master);
      const rl = ac.createBiquadFilter(), rh = ac.createBiquadFilter(), rg = ac.createGain();   // and a soft, steady roar under it
      rl.type = "lowpass"; rl.frequency.value = 700; rh.type = "highpass"; rh.frequency.value = 90; rg.gain.value = .25;   // ~6 dB under the burn
      src().connect(rl); rl.connect(rh); rh.connect(rg); rg.connect(audio.fireBus);
      if (window.FIRE_SOUND) {
        const bin = atob(FIRE_SOUND), u = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
        const done = b => { audio.fireBuf = b; audio.fire.forEach(f => f.pause()); };
        const p = ac.decodeAudioData(u.buffer, done, () => { });
        if (p && p.catch) p.catch(() => { });
      }
    } catch (e) { audio.ac = null; }
  }
  const GRAIN = 3, HOP = .75, SINE = new Float32Array(65).map((_, i) => Math.sin(Math.PI * i / 64) * Math.sqrt(2 * HOP / GRAIN));
  function fireGrains() {                      // keep the next ~half second of snippets scheduled
    const ac = audio.ac, buf = audio.fireBuf, now = ac.currentTime;
    if (!audio.nextGrain || audio.nextGrain < now) audio.nextGrain = now + .05;
    while (audio.nextGrain < now + .5) {
      // 3 s snippets, a new one every .75 s, each under a sine-shaped fade: four always overlap, so the loudness
      // stays even (measured: within ~2 dB, which is just the crackle's own pops)
      const at = audio.nextGrain, dur = GRAIN, off = Math.random() * (buf.duration / .9 - dur - .1);
      const src = ac.createBufferSource(), g = ac.createGain();
      src.buffer = buf; src.playbackRate.value = .94 + Math.random() * .12;
      g.gain.setValueAtTime(0, at); g.gain.setValueCurveAtTime(SINE, at, dur);
      src.connect(g); g.connect(audio.fireBus); src.start(at, Math.max(0, off * .9), dur * 1.1);
      audio.nextGrain = at + HOP;
    }
  }
  function playTheme() { audio.theme = true; audio.music.loop = false; audio.music.src = "sounds/got/music-local.mp3"; }
  function toScore() {
    audio.theme = false; audio.music.loop = true; audio.music.src = "sounds/got/music.mp3";
    if (audio.on && !audio.paused && !audio.muted) audio.music.play().catch(() => { });
  }
  function thunder() {
    if (!audio.ac || audio.muted || audio.paused || !audio.on) return;
    const ac = audio.ac, now = ac.currentTime, s = ac.createBufferSource(); s.buffer = audio.noise;
    const lp = ac.createBiquadFilter(); lp.type = "lowpass"; lp.frequency.setValueAtTime(1800, now); lp.frequency.exponentialRampToValueAtTime(90, now + 2.5);
    const g = ac.createGain(), v = VOL.thunder * (.6 + Math.random() * .4);
    g.gain.setValueAtTime(0, now); g.gain.linearRampToValueAtTime(v, now + .06); g.gain.setValueAtTime(v * .7, now + .4);
    g.gain.linearRampToValueAtTime(v * .9, now + .9); g.gain.exponentialRampToValueAtTime(.001, now + 4.5 + Math.random() * 2);
    s.connect(lp); lp.connect(g); g.connect(audio.master); s.start(now, Math.random() * 2); s.stop(now + 7);
  }
  function mixAudio(t, dt) {
    if (!audio.fire) return;
    const live = audio.on && !audio.muted && !audio.paused, w = sky && sky.wx, k = Math.min(1, dt * 1.5);
    audio.fireLvl += ((live ? VOL.fire : 0) - audio.fireLvl) * k;
    audio.musicLvl += ((live ? VOL.music : 0) - audio.musicLvl) * k;
    const m = audio.music; m.volume = Math.max(0, Math.min(1, audio.musicLvl));
    if (live && m.paused && m.src) m.play().catch(() => { }); else if (!live && !m.paused && audio.musicLvl < .01) m.pause();
    // the crackle: as the playing copy nears its end, the other starts from the top; each fades in and out by its position
    const XF = .8, [a, b] = [audio.fire[audio.cur], audio.fire[1 - audio.cur]];
    if (audio.fireBuf) { audio.fireBus.gain.value = audio.fireLvl; if (live) fireGrains(); }
    else if (!live && audio.fireLvl < .01) audio.fire.forEach(f => f.paused || f.pause());
    else {
      if (a.paused && (!a.duration || a.currentTime < a.duration - .05)) a.play().catch(() => { });
      if (a.duration && a.currentTime > a.duration - XF && b.paused) { b.currentTime = 0; b.play().catch(() => { }); audio.cur = 1 - audio.cur; }
      for (const f of audio.fire) {
        const d = f.duration || 5, fw = f.paused ? 0 : Math.max(0, Math.min(1, f.currentTime / XF, (d - f.currentTime) / XF));
        f.volume = Math.max(0, Math.min(1, audio.fireLvl * fw));
      }
    }
    if (audio.ac && w) {
      if (live && audio.ac.state === "suspended") audio.ac.resume();
      const rain = live ? VOL.rain * Math.max(w.rain, w.kind === "drizzle" ? .3 : 0) : 0;
      const gust = .6 + .4 * Math.sin(t * .7) * Math.sin(t * .23 + 1);
      const wind = live ? VOL.wind * Math.min(1, w.wind / 50 + (w.kind === "blizzard" ? .6 : 0)) * gust : 0;
      audio.rain.gain.value += (rain - audio.rain.gain.value) * k; audio.wind.gain.value += (wind - audio.wind.gain.value) * k;
      audio.windF.frequency.value = 300 + 260 * gust;
    }
  }
  window.pantheonAudio = {
    pause() { audio.paused = true; [...(audio.fire || []), audio.music].forEach(a => a && a.pause()); if (audio.ac) audio.ac.suspend(); },
    resume() { audio.paused = false; if (audio.music) { audio.musicLvl = 0; playTheme(); } rollScroll(); },   // back home: your theme again, the greeting unrolled
    toggle() { audio.muted = !audio.muted; try { localStorage.setItem("pantheon_mute", audio.muted ? "1" : "0"); } catch (e) { }
               document.body.classList.toggle("muted", audio.muted); return !audio.muted; },
    thunder, get muted() { return audio.muted; },
    get _music() { return audio.music; },                                  // (for testing)
    get state() { return audio.fire && { music: audio.music.src.split("/").pop(), at: +audio.music.currentTime.toFixed(1), loop: audio.music.loop,
                                         grains: audio.fireBuf ? +audio.fireBus.gain.value.toFixed(3) : "off", next: audio.nextGrain && +(audio.nextGrain - audio.ac.currentTime).toFixed(2),
                                         fire: audio.fire.map(f => f.paused ? "-" : `${f.currentTime.toFixed(2)}@${f.volume.toFixed(2)}`) }; },
  };
  document.addEventListener("DOMContentLoaded", () => document.body.classList.toggle("muted", audio.muted));

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
    drawSky(t, dt);
    drawRoom(t, dt);
    drawSconces(t, dt);
    mixAudio(t, dt);
    breathe(t, dt);
    drawJet(t);
    roastIcons(dt);
    drawFire(dt);
  };

  // ---------------------------------------------------------------- the greeting: a parchment scroll under the clock
  // A line from the royal court for the time of day (a different one each day), unrolled each time you come home.
  const GREET = {
    morning:   ["Good morrow, Your Grace.", "The realm wakes for you, Your Grace.", "Rise, Your Grace. The court awaits.", "Long may you reign, Your Grace."],
    afternoon: ["The realm awaits your command, Your Grace.", "The small council is assembled, Your Grace.", "The day is yours to rule, Your Grace.", "All hail His Grace."],
    evening:   ["The torches are lit, Your Grace.", "A feast awaits, Your Grace.", "The hall is yours tonight, Your Grace.", "The wine is poured, Your Grace. Rest your crown."],
    night:     ["Night gathers, Your Grace. Your watch begins.", "Even kings must sleep, Your Grace.", "The realm is quiet. Rest, Your Grace.", "The castle sleeps. Long may you reign."],
  };
  function greeting(d) {   // morning 5-12, afternoon 12-17, evening 17-21, night 21-5 (the small hours belong to the night before)
    const h = d.getHours(), part = h >= 5 && h < 12 ? "morning" : h >= 12 && h < 17 ? "afternoon" : h >= 17 && h < 21 ? "evening" : "night";
    const day = Math.floor((d.getTime() - d.getTimezoneOffset() * 6e4 - (h < 5 ? 864e5 : 0)) / 864e5);
    return GREET[part][day % GREET[part].length];
  }
  let scroll = null;
  function makeScroll() {
    const st = document.createElement("style");
    st.textContent = `
      .gotscroll { position: relative; display: inline-block; margin: 24px 0 0 6px; }
      .gotscroll .paper { padding: 11px 34px 12px; color: #3b2611; text-shadow: none; white-space: nowrap;
        font: italic 600 26px/1.25 Georgia, "Times New Roman", serif; letter-spacing: .01em;
        /* frosted parchment: the hall shows through, blurred, behind a ~70% sheet (glass, but still easy to read) */
        background: linear-gradient(180deg, rgba(255,250,235,.32), rgba(255,250,235,0) 30%, rgba(0,0,0,0) 75%, rgba(60,35,10,.22)),
                    linear-gradient(90deg, rgba(196,164,108,.74), rgba(228,207,159,.70) 10%, rgba(238,223,184,.68) 50%, rgba(226,202,151,.70) 90%, rgba(192,159,104,.74));
        -webkit-backdrop-filter: blur(10px) saturate(1.25); backdrop-filter: blur(10px) saturate(1.25);
        border-top: 1px solid rgba(255,248,225,.55); border-bottom: 1px solid rgba(90,60,25,.35);
        box-shadow: 0 8px 22px rgba(0,0,0,.45); clip-path: inset(0 100% 0 0); }
      .gotscroll .rod { position: absolute; top: -7px; bottom: -7px; width: 15px; border-radius: 8px;
        background: linear-gradient(90deg, #4a2f14, #a5814b 40%, #d8bd85 55%, #5b3b19); box-shadow: 0 4px 10px rgba(0,0,0,.6); }
      .gotscroll .rod::before, .gotscroll .rod::after { content: ""; position: absolute; left: 2px; width: 11px; height: 7px; border-radius: 3px;
        background: linear-gradient(90deg, #8a6a2c, #f0d58a, #8a6a2c); }
      .gotscroll .rod::before { top: -4px; } .gotscroll .rod::after { bottom: -4px; }
      .gotscroll .l { left: -8px; } .gotscroll .r { right: -8px; }
      .gotscroll.open .paper { clip-path: inset(0 0 0 0); transition: clip-path 1.6s cubic-bezier(.35,.6,.2,1); }
      .gotscroll.open .r { transform: none !important; transition: transform 1.6s cubic-bezier(.35,.6,.2,1); }`;
    document.head.appendChild(st);
    const el = document.createElement("div"); el.className = "gotscroll";
    el.innerHTML = '<div class="paper"></div><div class="rod l"></div><div class="rod r"></div>';
    document.querySelector("#clock").appendChild(el);
    return { el, paper: el.querySelector(".paper"), r: el.querySelector(".r"), text: "" };
  }
  function rollScroll() {   // roll it up, then unroll it (with today's line for this time of day)
    if (!scroll || !on) return;
    const s = scroll; s.text = greeting(new Date()); s.paper.textContent = s.text;
    s.el.classList.remove("open"); s.el.style.display = "";
    s.r.style.transform = `translateX(${-(s.paper.offsetWidth - 2)}px)`;
    clearTimeout(s.t); s.t = setTimeout(() => s.el.classList.add("open"), 700);
  }
  setInterval(() => { if (scroll && on && greeting(new Date()) !== scroll.text) rollScroll(); }, 60000);   // a new part of the day

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
      sky = sky || makeSky(); sky.cv.style.display = sky.flash.style.display = "";
      room = room || makeRoom(); room.layers.forEach(l => (l.im.style.display = "")); [room.shade, room.rays, room.dust, ...room.fog.map(f => f.el)].forEach(e => (e.style.display = ""));
      startAudio(); audio.on = true;
      scroll = scroll || makeScroll(); rollScroll();
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
      if (sky) { sky.cv.style.display = sky.flash.style.display = "none"; }
      if (room) { room.layers.forEach(l => (l.im.style.display = "none")); [room.shade, room.rays, room.dust, ...room.fog.map(f => f.el)].forEach(e => (e.style.display = "none")); }
      audio.on = false; window.pantheonAudio.pause(); audio.paused = false;
      if (scroll) { scroll.el.style.display = "none"; clearTimeout(scroll.t); }
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
