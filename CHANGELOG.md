# Changelog

## 1.0.0 · 2026-11-15

The first release of Pantheon as its own project.

**Home screen**
- Pantheon replaces Google's home (Home watch, self-repair, crash guard); one-command install; unbloat script.
- Themes, switchable from the crown or by voice; Game of Thrones is the default, Hogwarts is included.
- Game of Thrones: a throne room with live torches; Drogon hops between apps and breathes fire on the one you pick;
  the eight seasons as rustic books.
- The weather wizard: location from the TV's connection, live weather in the windows (rain, snow, fog, lightning),
  the room relit by the sun's real position, the real moon phase, night fog and sunlit dust.
- OLED true black at every hour; place, temperature and weather under the clock; a royal greeting on a scroll.
- The Ravens: national, world and finance headlines, each story sorted into its row by Jev.
- Sound: torch crackle, the score (or your own track), rain, wind and thunder; a sleep timer with a countdown.

**Agent (on the TV)**
- The Small Council: an agentic remote (DeepSeek, TMDB, Jev scoring, reasons), with wax-seal choices.
- The Hand of the King: voice agent routed by Jev; live web, headlines and FAA flight status; TV control; an
  expressive ElevenLabs v3 voice with recorded lines; the conversation shown on a scroll.
- On-device memory: follow-ups, the Council's picks, facts it learns; recall and forget by voice.

**Engineering**
- Configuration and test commands only over adb (DUMP-guarded receiver).
- Architecture document and decision records; Java and JavaScript tests; `./test.sh`.
