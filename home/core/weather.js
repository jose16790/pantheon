// Pantheon: the weather wizard's engine, for any theme's scene.
//
// The TV app fetches where the TV is and the weather there (weather/Weather.java: IP location + Open-Meteo) and hands
// it over with pantheonWeather(json); in a desktop browser this file fetches it itself. Everything astronomical (the
// sun's and moon's positions, the moon's phase) is worked out here, offline, from the time and the coordinates.
//
// window.PANTHEON_WX.now() returns one picture of "outside, right now":
//   kind     clear | partly | overcast | fog | drizzle | rain | heavyrain | snow | blizzard | thunder
//   cloud    0..1 cloud cover          rain, snow   0..1 how hard          wind   km/h       fog   0..1
//   light    { night, dawn, day, golden } weights summing to 1 (from the sun's height), and dark 0..1
//   sun      { alt, az } degrees (az from south, + towards west)         moon   { alt, az, frac, phase, waxing }
//   place, temp, code, isDay
// Testing: ?wx=<kind>&at=<ISO time> in a browser, or adb shell am start ... --es wx thunder --es wx_at 2026-11-14T14:00
(function () {
  const RAD = Math.PI / 180, DAY = 864e5, J1970 = 2440588, J2000 = 2451545, E = RAD * 23.4397;

  // ---------------------------------------------------------------- sun and moon (after Mourner's SunCalc, BSD; re-derived here)
  const days = t => t / DAY - 0.5 + J1970 - J2000;
  const ra = (l, b) => Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l));
  const dec = (l, b) => Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l));
  const sidereal = (d, lw) => RAD * (280.16 + 360.9856235 * d) - lw;
  const azimuth = (H, phi, de) => Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(de) * Math.cos(phi));
  const altitude = (H, phi, de) => Math.asin(Math.sin(phi) * Math.sin(de) + Math.cos(phi) * Math.cos(de) * Math.cos(H));
  function sunCoords(d) {
    const M = RAD * (357.5291 + 0.98560028 * d);
    const L = M + RAD * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M)) + RAD * 102.9372 + Math.PI;
    return { dec: dec(L, 0), ra: ra(L, 0) };
  }
  function moonCoords(d) {
    const L = RAD * (218.316 + 13.176396 * d), M = RAD * (134.963 + 13.064993 * d), F = RAD * (93.272 + 13.22935 * d);
    const l = L + RAD * 6.289 * Math.sin(M), b = RAD * 5.128 * Math.sin(F);
    return { ra: ra(l, b), dec: dec(l, b), dist: 385001 - 20905 * Math.cos(M) };
  }
  function position(c, d, lat, lon) {
    const lw = -RAD * lon, phi = RAD * lat, H = sidereal(d, lw) - c.ra;
    return { alt: altitude(H, phi, c.dec) / RAD, az: azimuth(H, phi, c.dec) / RAD };
  }
  function moonPhase(d) {
    const s = sunCoords(d), m = moonCoords(d), sd = 149598000;
    const phi = Math.acos(Math.sin(s.dec) * Math.sin(m.dec) + Math.cos(s.dec) * Math.cos(m.dec) * Math.cos(s.ra - m.ra));
    const inc = Math.atan2(sd * Math.sin(phi), m.dist - sd * Math.cos(phi));
    const ang = Math.atan2(Math.cos(s.dec) * Math.sin(s.ra - m.ra), Math.sin(s.dec) * Math.cos(m.dec) - Math.cos(s.dec) * Math.sin(m.dec) * Math.cos(s.ra - m.ra));
    return { frac: (1 + Math.cos(inc)) / 2, phase: 0.5 + 0.5 * inc * (ang < 0 ? -1 : 1) / Math.PI, waxing: ang < 0 };
  }

  // ---------------------------------------------------------------- the weather, in the scene's words
  // WMO weather codes (Open-Meteo): 0 clear, 1-3 cloud, 45/48 fog, 5x drizzle, 6x rain, 7x snow, 8x showers, 9x storms
  function classify(c) {
    const code = c.weather_code | 0, wind = Math.max(c.wind_speed_10m || 0, (c.wind_gusts_10m || 0) * 0.7);
    const precip = Math.min(1, (c.precipitation || 0) / 6), snowfall = Math.min(1, (c.snowfall || 0) / 2.5);
    let kind = "clear";
    if (code === 1) kind = "clear"; else if (code === 2) kind = "partly"; else if (code === 3) kind = "overcast";
    else if (code === 45 || code === 48) kind = "fog";
    else if (code >= 51 && code <= 57) kind = "drizzle";
    else if ([61, 63, 66, 80, 81].includes(code)) kind = "rain";
    else if ([65, 67, 82].includes(code)) kind = "heavyrain";
    else if ((code >= 71 && code <= 77) || code === 85 || code === 86) kind = (code === 75 || code === 86) && wind > 30 || wind > 45 ? "blizzard" : "snow";
    else if (code >= 95) kind = "thunder";
    const cloud = Math.max((c.cloud_cover || 0) / 100, { overcast: .95, fog: 1, drizzle: .9, rain: .95, heavyrain: 1, snow: .95, blizzard: 1, thunder: 1 }[kind] || 0);
    const vis = c.visibility == null ? 20000 : c.visibility;
    return {
      kind, cloud, wind, code,
      rain: { drizzle: .25, rain: Math.max(.5, precip), heavyrain: Math.max(.85, precip), thunder: Math.max(.7, precip) }[kind] || 0,
      snow: { snow: Math.max(.45, snowfall), blizzard: 1 }[kind] || 0,
      fog: kind === "fog" ? 1 : Math.max(0, Math.min(1, (8000 - vis) / 7000)),
    };
  }
  const TEST = {   // what an override looks like, for testing every kind without waiting for it
    clear: { weather_code: 0, cloud_cover: 5 }, partly: { weather_code: 2, cloud_cover: 45 }, overcast: { weather_code: 3, cloud_cover: 100 },
    fog: { weather_code: 45, visibility: 300 }, drizzle: { weather_code: 53, precipitation: .4 }, rain: { weather_code: 63, precipitation: 3 },
    heavyrain: { weather_code: 65, precipitation: 9 }, snow: { weather_code: 73, snowfall: 1 }, blizzard: { weather_code: 75, snowfall: 3, wind_speed_10m: 55 },
    thunder: { weather_code: 95, precipitation: 6, wind_speed_10m: 30 },
  };

  // light from the sun's height: night below -10°, dawn/dusk -10..2°, golden 2..14°, day above
  function light(alt, cloud) {
    const sm = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
    const up = sm(-10, -2, alt), lit = sm(2, 14, alt);
    let night = 1 - up, dawn = up * (1 - sm(-2, 5, alt)), golden = sm(-2, 5, alt) * (1 - lit), day = lit;
    golden *= 1 - .7 * cloud; day += sm(-2, 5, alt) * (1 - lit) * .7 * cloud;                 // a cloudy sunset is just grey
    const s = night + dawn + golden + day;
    return { night: night / s, dawn: dawn / s, golden: golden / s, day: day / s, dark: Math.max(0, 1 - sm(-12, 10, alt)) };
  }

  let data = null, override = null, overrideAt = null;
  const q = (location.search.match(/[?&]wx=(\w+)/) || [])[1], qa = (location.search.match(/[?&]at=([^&]+)/) || [])[1];
  if (q && TEST[q]) override = q;
  if (qa) overrideAt = new Date(decodeURIComponent(qa)).getTime();

  window.PANTHEON_WX = {
    now() {
      const t = overrideAt ? overrideAt + (performance.now() - started) : Date.now();
      const p = (data && data.place) || { lat: 43.45, lon: -80.49, city: "" };        // until the first fetch: somewhere sensible
      const cur = override ? TEST[override] : (data && data.current) || { weather_code: 1, cloud_cover: 20 };
      const d = days(t), sun = position(sunCoords(d), d, p.lat, p.lon), mp = position(moonCoords(d), d, p.lat, p.lon);
      const w = classify(cur);
      return Object.assign(w, { light: light(sun.alt, w.cloud), sun, moon: Object.assign(mp, moonPhase(d)), place: p,
                                temp: cur.temperature_2m, isDay: sun.alt > -0.8, time: t });
    },
    set(json) { data = typeof json === "string" ? JSON.parse(json) : json; },
    test(kind, at) { override = TEST[kind] ? kind : null; overrideAt = at ? new Date(at).getTime() : null; started = performance.now(); },
  };
  let started = performance.now();
  window.pantheonWeather = json => { try { PANTHEON_WX.set(json); } catch (e) { } };
  window.pantheonWxTest = (kind, at) => PANTHEON_WX.test(kind, at);

  // start: the TV's last fetch, or (in a browser) fetch it here
  if (window.TV && TV.weather) { try { PANTHEON_WX.set(TV.weather()); } catch (e) { } }
  else if (!override) {
    fetch("https://ipapi.co/json/").then(r => r.json()).then(g => fetch(`https://api.open-meteo.com/v1/forecast?latitude=${g.latitude}&longitude=${g.longitude}`
      + "&current=temperature_2m,is_day,weather_code,cloud_cover,precipitation,rain,showers,snowfall,wind_speed_10m,wind_gusts_10m,visibility&timezone=auto")
      .then(r => r.json()).then(w => PANTHEON_WX.set({ place: { lat: g.latitude, lon: g.longitude, city: g.city }, current: w.current })))
      .catch(() => { });
  }
})();
