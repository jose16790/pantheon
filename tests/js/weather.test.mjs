// The weather wizard's maths (home/core/weather.js): the sun's height and the moon's phase against known astronomy,
// and every weather kind classified as itself. Run: node --test tests/js   (or ./test.sh)
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

function load() {
  const ctx = { location: { search: "" }, performance: { now: () => 0 }, fetch: () => new Promise(() => {}), console };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(readFileSync(new URL("../../home/core/weather.js", import.meta.url), "utf8"), ctx);
  return ctx.PANTHEON_WX;                       // place defaults to Kitchener, Ontario (43.45 N, 80.49 W) until fetched
}
const WX = load();
const at = (iso, kind = "clear") => { WX.test(kind, iso); return WX.now(); };
const near = (v, want, tol, what) => assert.ok(Math.abs(v - want) <= tol, `${what}: ${v.toFixed(2)} not within ${tol} of ${want}`);

test("sun at the June solstice, local noon: 90 - 43.45 + 23.44 = 70.0 degrees", () => {
  near(at("2026-06-21T17:24:00Z").sun.alt, 70.0, 0.8, "June noon");
});

test("sun at the December solstice, local noon: 90 - 43.45 - 23.44 = 23.1 degrees", () => {
  near(at("2026-12-21T17:20:00Z").sun.alt, 23.1, 0.8, "December noon");
});

test("sun is well below the horizon at 11 p.m.", () => {
  assert.ok(at("2026-11-15T04:00:00Z").sun.alt < -30);
});

test("moon phase: new, first quarter (waxing), full (April 2024)", () => {
  assert.ok(at("2024-04-08T18:21:00Z").moon.frac < 0.03, "new moon");
  const q = at("2024-04-15T19:13:00Z").moon;
  near(q.frac, 0.5, 0.08, "first quarter"); assert.equal(q.waxing, true);
  assert.ok(at("2024-04-23T23:49:00Z").moon.frac > 0.97, "full moon");
});

test("light weights always sum to 1, and noon in June is day", () => {
  for (const iso of ["2026-06-21T17:24:00Z", "2026-06-21T23:40:00Z", "2026-06-22T03:00:00Z", "2026-11-15T12:30:00Z"]) {
    const L = at(iso).light, s = L.night + L.dawn + L.golden + L.day;
    near(s, 1, 1e-9, `weights at ${iso}`);
  }
  assert.ok(at("2026-06-21T17:24:00Z").light.day > 0.9);
});

test("every weather kind is classified as itself, with rain/snow where it should be", () => {
  for (const k of ["clear", "partly", "overcast", "fog", "drizzle", "rain", "heavyrain", "snow", "blizzard", "thunder"]) {
    const w = at("2026-11-15T17:00:00Z", k);
    assert.equal(w.kind, k);
    if (["rain", "heavyrain", "thunder"].includes(k)) assert.ok(w.rain > 0.4, `${k} rains`);
    if (["snow", "blizzard"].includes(k)) assert.ok(w.snow > 0.4, `${k} snows`);
    if (k === "clear") assert.equal(w.rain + w.snow, 0);
  }
});
