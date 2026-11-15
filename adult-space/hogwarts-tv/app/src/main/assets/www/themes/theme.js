// Pantheon: themes and the voice banner, for the Adult Space page.
//
// Themes are defined on the Android side (theme/ThemeManager.java and the ThemeData classes) and handed to the page
// as JSON: { id, name, mascot, complete, colors: {gold, ink, ...}, images: {castle, shelf, ...} }.
// colors become CSS variables on :root (index.html's styles use them); images replace the pictures marked
// data-slot="<slot>" (and --mist). A slot a theme leaves out keeps the picture already shown.
//
// In a desktop browser (no window.TV) the page keeps its built-in Hogwarts defaults; add ?theme=got|marvel to preview
// a stub palette there.
(function () {
  const PREVIEW = {   // browser preview only; on the TV the Java ThemeData classes are the source of truth
    got:    { id: "got",    colors: { gold: "#9fb3c2", "gold-hi": "#dfe9f0", ink: "#e9e4dc", accent: "#c9b28a", "accent-soft": "#d8d0c2", spine: "#b9c6cf", "panel-1": "#0f1418", "panel-2": "#040506" } },
    marvel: { id: "marvel", colors: { gold: "#e62429", "gold-hi": "#ffcf3f", ink: "#ffffff", accent: "#f2c14e", "accent-soft": "#ffe3a3", spine: "#ffcf3f", "panel-1": "#1a0b0d", "panel-2": "#060203" } },
  };

  window.pantheonApplyTheme = function (t) {
    if (typeof t === "string") { try { t = JSON.parse(t); } catch (e) { console.log("Pantheon: bad theme JSON"); return; } }
    if (!t || !t.id) return;
    const root = document.documentElement.style, colors = t.colors || {}, images = t.images || {};
    for (const k in colors) root.setProperty("--" + k, colors[k]);
    document.querySelectorAll("[data-slot]").forEach(el => { const src = images[el.dataset.slot]; if (src && el.getAttribute("src") !== src) el.src = src; });
    if (images.mist) root.setProperty("--mist", "url(" + images.mist + ")");
    document.body.dataset.theme = t.id;
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
    else if (state === "heard") show("sending", "Sending by owl post", "“" + text + "”");
    else if (state === "reply") show("reply", "Reply", replyText(text), 9000);
    else if (state === "error") show("error", "Hmm", text || "Something went wrong", 6000);
    else { const b = box(); if (b) b.classList.remove("show"); }
  };

  // The mic icon (Up from the dock, then Left, then OK).
  window.pantheonListen = function () {
    if (window.TV && TV.listen) TV.listen();
    else window.pantheonVoice("error", "Voice works in the TV app");
  };

  // ---------------------------------------------------------------- start-up
  if (window.TV && TV.theme) window.pantheonApplyTheme(TV.theme());
  else { const q = (location.search.match(/[?&]theme=(\w+)/) || [])[1]; if (q && PREVIEW[q]) window.pantheonApplyTheme(PREVIEW[q]); }
})();
