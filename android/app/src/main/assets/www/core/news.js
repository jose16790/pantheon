// Pantheon: the home's headlines, top right under the icons, for every theme.
//
// Three rows: the TV's own country, the world, and business. Each shows one headline at a time from its top ten,
// moving on every three minutes; the rows take turns (one changes each minute), so the panel is never all moving.
// The TV app fetches them (news/News.java: Google News' public feeds, by the country the TV is in) and hands them over
// with pantheonNews(json); the last set is read at start with TV.news(). Each theme names the rows its own way.
(function () {
  const LABELS = {
    got:      { head: "The Ravens", rows: ["The Realm", "Across the Narrow Sea", "The Iron Bank"] },
    hogwarts: { head: "The Daily Prophet", rows: ["Home News", "Foreign Affairs", "Gringotts"] },
    _:        { head: "Headlines", rows: ["National", "World", "Markets"] },
  };
  const KEYS = ["national", "world", "business"];
  let data = null, panel = null, shown = ["", "", ""];

  function css() {
    const st = document.createElement("style");
    st.textContent = `
      #news { position: absolute; right: 112px; top: 204px; width: 560px; padding: 16px 0 6px; color: var(--ink);
              background: linear-gradient(180deg, rgba(0,0,0,.66), rgba(0,0,0,.52)); border: 1px solid rgba(214,178,110,.38);
              box-shadow: 0 10px 30px rgba(0,0,0,.45), inset 0 0 0 4px rgba(0,0,0,.35), inset 0 0 0 5px rgba(214,178,110,.16); }
      #news:not(.ready) { display: none; }
      #news .head { text-align: center; font: 700 15px/1 "Cinzel", Georgia, serif; letter-spacing: .34em; text-transform: uppercase;
                    color: var(--gold-hi, #e9c98a); opacity: .9; padding-bottom: 12px; margin: 0 24px 2px;
                    border-bottom: 1px solid rgba(214,178,110,.28); }
      #news .row { padding: 14px 26px 13px; }
      #news .row + .row { border-top: 1px solid rgba(214,178,110,.14); }
      #news .lab { font: 700 12.5px/1 "Cinzel", Georgia, serif; letter-spacing: .28em; text-transform: uppercase; color: var(--gold-hi, #e9c98a); opacity: .75; }
      #news .txt { transition: opacity .9s; }
      #news .txt.fade { opacity: 0; }
      #news .t { margin-top: 8px; font-size: 21px; line-height: 1.3; text-shadow: 0 2px 8px rgba(0,0,0,.6);
                 display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
      #news .m { margin-top: 6px; font-size: 14px; letter-spacing: .08em; opacity: .55; }`;
    document.head.appendChild(st);
  }
  function make() {
    const clock = document.querySelector("#clock"); if (!clock) return null;
    css();
    const el = document.createElement("div"); el.id = "news";
    el.innerHTML = '<div class="head"></div>' + KEYS.map(() => '<div class="row"><div class="lab"></div><div class="txt"><div class="t"></div><div class="m"></div></div></div>').join("");
    clock.after(el);
    return { el, head: el.querySelector(".head"), rows: [...el.querySelectorAll(".row")].map(r => ({ lab: r.querySelector(".lab"), txt: r.querySelector(".txt"),
                                                                                                     t: r.querySelector(".t"), m: r.querySelector(".m") })) };
  }
  const ago = ts => { if (!ts) return ""; const m = Math.max(1, Math.round((Date.now() - ts) / 6e4));
                      return m < 60 ? `${m} min ago` : m < 48 * 60 ? `${Math.round(m / 60)} h ago` : `${Math.round(m / 1440)} days ago`; };

  function render(force) {
    if (!panel) panel = make(); if (!panel) return;
    const L = LABELS[document.body.dataset.theme] || LABELS._;
    panel.head.textContent = L.head;
    let any = false;
    const step = Math.floor(Date.now() / 6e4);                         // minutes: row k moves on when step % 3 === k
    KEYS.forEach((k, i) => {
      const R = panel.rows[i], list = (data && data[k]) || [];
      R.lab.textContent = L.rows[i];
      if (!list.length) { R.t.textContent = ""; R.m.textContent = ""; return; }
      any = true;
      let n = Math.floor((step - i) / 3), item = list[n % list.length];
      for (let tries = 0; tries < list.length && shown.some((t, j) => j !== i && t === item.t); tries++) item = list[++n % list.length];   // never the same story in two rows
      const key = item.t;
      R.m.textContent = [item.src, ago(item.ts)].filter(Boolean).join("  ·  ");
      if (key === shown[i] && !force) return;
      shown[i] = key;
      if (force || !R.t.textContent) { R.t.textContent = item.t; return; }
      R.txt.classList.add("fade");                                      // the old line fades out, the new one in
      setTimeout(() => { R.t.textContent = item.t; R.txt.classList.remove("fade"); }, 900);
    });
    panel.el.classList.toggle("ready", any);
  }

  window.pantheonNews = json => { try { data = typeof json === "string" ? JSON.parse(json) : json; render(true); } catch (e) { } };
  document.addEventListener("DOMContentLoaded", () => {
    if (window.TV && TV.news) { try { data = JSON.parse(TV.news()); } catch (e) { } }
    render(true);
    setInterval(() => render(false), 15000);
    new MutationObserver(() => render(true)).observe(document.body, { attributes: true, attributeFilter: ["data-theme"] });   // theme switched
  });
})();
