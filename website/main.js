// DropDuo website behavior. No dependencies; every feature degrades to static HTML.
(() => {
  "use strict";

  const REPO = "rohit-yadavv/dropduo";
  const RELEASES_PAGE = `https://github.com/${REPO}/releases`;
  const CACHE_KEY = "dropduo:release:v2";
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  // Theme: the head script applies the initial value; this keeps it in sync.
  const root = document.documentElement;
  const toggle = document.querySelector("[data-theme-toggle]");
  const themeMeta = document.querySelector('meta[name="theme-color"]');
  const systemDark = window.matchMedia("(prefers-color-scheme: dark)");
  const THEME_KEY = "dropduo:theme";
  const saved = () => {
    try { return localStorage.getItem(THEME_KEY); } catch { return null; }
  };
  const applyTheme = (theme) => {
    root.dataset.theme = theme;
    themeMeta.content = theme === "dark" ? "#0D0E10" : "#F5F6F8";
    toggle.setAttribute("aria-label", theme === "dark" ? "Switch to light theme" : "Switch to dark theme");
  };
  applyTheme(root.dataset.theme);
  toggle.addEventListener("click", () => {
    const next = root.dataset.theme === "dark" ? "light" : "dark";
    try { localStorage.setItem(THEME_KEY, next); } catch { /* still switch for this visit */ }
    if (document.startViewTransition && !reduceMotion.matches) {
      document.startViewTransition(() => applyTheme(next));
    } else {
      applyTheme(next);
    }
  });
  // Follow the system until the visitor picks a theme.
  systemDark.addEventListener("change", (event) => {
    if (!saved()) applyTheme(event.matches ? "dark" : "light");
  });

  // Navigation hairline once the page has moved past the top.
  const nav = document.querySelector(".nav");
  const sentinel = document.createElement("div");
  sentinel.setAttribute("aria-hidden", "true");
  sentinel.style.cssText = "position:absolute;top:0;height:1px;width:1px";
  document.body.prepend(sentinel);
  new IntersectionObserver(([entry]) => {
    nav.classList.toggle("is-scrolled", !entry.isIntersecting);
  }).observe(sentinel);

  // Reveal sections as they enter the viewport.
  const revealer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add("is-in");
      revealer.unobserve(entry.target);
    }
  }, { rootMargin: "0px 0px -12% 0px", threshold: 0.12 });
  document.querySelectorAll(".reveal").forEach((el) => revealer.observe(el));

  // Pointer tilt on the hero tile. Pointer devices only, never with reduced motion.
  const visual = document.querySelector("[data-tilt]");
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");
  if (visual && finePointer.matches) {
    const hero = visual.closest(".hero");
    let frame = 0;
    let x = 0;
    let y = 0;
    const apply = () => {
      frame = 0;
      visual.style.setProperty("--rx", `${(-y * 10).toFixed(2)}deg`);
      visual.style.setProperty("--ry", `${(x * 12).toFixed(2)}deg`);
    };
    hero.addEventListener("pointermove", (event) => {
      if (reduceMotion.matches) return;
      const rect = visual.getBoundingClientRect();
      x = Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1));
      y = Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1));
      if (!frame) frame = requestAnimationFrame(apply);
    });
    hero.addEventListener("pointerleave", () => {
      x = 0;
      y = 0;
      if (!frame) frame = requestAnimationFrame(apply);
    });
  }

  // Things you send: the item nearest the middle of the viewport plays on the stage.
  const stage = document.querySelector("[data-stage]");
  const sends = [...document.querySelectorAll(".send")];
  if (stage && sends.length) {
    const track = stage.querySelector("[data-track]");
    const token = stage.querySelector("[data-token]");
    const fill = stage.querySelector("[data-fill]");
    const icon = stage.querySelector("[data-token-icon]");
    const nameEl = stage.querySelector("[data-stage-name]");
    const sizeEl = stage.querySelector("[data-stage-size]");
    const statusEl = stage.querySelector("[data-stage-status]");
    const howEl = stage.querySelector("[data-stage-how]");
    const ends = {
      mac: stage.querySelector('[data-end="mac"]'),
      android: stage.querySelector('[data-end="android"]'),
    };
    let active = null;
    let visible = false;
    let run = 0;
    let timers = [];
    let animations = [];

    const clear = () => {
      timers.forEach(clearTimeout);
      timers = [];
      animations.forEach((a) => a.cancel());
      animations = [];
      track.classList.remove("is-broken");
    };
    const later = (ms, fn) => timers.push(setTimeout(fn, ms));
    const status = (text, quiet = false) => {
      statusEl.textContent = text;
      statusEl.classList.toggle("is-quiet", quiet);
    };

    const play = () => {
      clear();
      const id = ++run;
      const item = active.dataset;
      const toAndroid = item.dir === "m2a";
      const resume = item.mode === "resume";
      const receiver = toAndroid ? ends.android : ends.mac;
      const distance = Math.max(0, track.clientWidth - token.offsetWidth);
      const at = (p) => `translateX(${((toAndroid ? p : 1 - p) * distance).toFixed(1)}px)`;
      fill.style.transformOrigin = toAndroid ? "left" : "right";

      if (reduceMotion.matches) {
        token.style.transform = at(1);
        fill.style.transform = "scaleX(1)";
        status("Received and verified");
        return;
      }

      const stops = resume ? [[0, 0], [0.38, 0.46], [0.66, 0.46], [1, 1]] : [[0, 0], [1, 1]];
      const duration = resume ? 4200 : 1700;
      const easing = "cubic-bezier(0.65, 0, 0.35, 1)";
      const options = { duration, easing: resume ? "linear" : easing, fill: "forwards" };
      animations = [
        token.animate(stops.map(([offset, p]) => ({ offset, transform: at(p), easing })), options),
        fill.animate(stops.map(([offset, p]) => ({ offset, transform: `scaleX(${p})`, easing })), options),
      ];
      status("Sending", true);
      if (resume) {
        later(duration * 0.38, () => { track.classList.add("is-broken"); status("Wi-Fi dropped", true); });
        later(duration * 0.52, () => status("Retry"));
        later(duration * 0.66, () => { track.classList.remove("is-broken"); status("Resuming from 46%", true); });
      }
      animations[0].finished.then(() => {
        if (id !== run) return;
        receiver.classList.remove("is-receiving");
        void receiver.offsetWidth; // restart the ring animation
        receiver.classList.add("is-receiving");
        status("Received and verified");
        later(2200, () => { if (id === run && visible) play(); });
      }).catch(() => { /* cancelled by a newer run */ });
    };

    const select = (li) => {
      if (li === active) return;
      active?.classList.remove("is-active");
      active = li;
      li.classList.add("is-active");
      const item = li.dataset;
      stage.dataset.dir = item.dir;
      icon.setAttribute("href", `assets/icons.svg#${item.icon}`);
      nameEl.textContent = item.name;
      sizeEl.textContent = item.size;
      howEl.textContent = item.how;
      if (visible) play();
    };

    const centre = new IntersectionObserver((entries) => {
      for (const entry of entries) if (entry.isIntersecting) select(entry.target);
    }, { rootMargin: "-45% 0px -45% 0px" });
    sends.forEach((li) => {
      centre.observe(li);
      li.addEventListener("click", () => select(li));
      li.addEventListener("focus", () => select(li));
    });

    new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      if (visible) play();
      else clear();
    }).observe(stage);

    select(sends[0]);
  }

  // Suggest the visitor's platform. User agents are a hint, so both stay visible.
  const ua = navigator.userAgent;
  const isAndroid = /Android/i.test(ua);
  const isMac = /Macintosh|Mac OS X/i.test(ua) && navigator.maxTouchPoints < 2;
  const platform = isAndroid ? "android" : isMac ? "mac" : null;
  if (platform) {
    const label = document.querySelector("[data-platform-label]");
    label.textContent = platform === "android" ? "Download for Android" : "Download for Mac";
    const half = document.querySelector(`[data-platform="${platform}"]`);
    half.classList.add("is-suggested");
    if (platform === "android") half.parentElement.classList.add("duo--android-first");
  }

  // Release assets. Published names follow docs/downloads.md:
  // DropDuo-vVERSION-macos-arm64[-development].zip, -macos-x86_64, -android.apk
  const platforms = document.querySelector("[data-release-state]");
  const status = document.querySelector("[data-release-status]");
  const notes = document.querySelector("[data-release-notes]");
  const patterns = {
    "mac-arm64": /-macos-arm64(-development)?\.zip$/i,
    "mac-x86_64": /-macos-x86_64(-development)?\.zip$/i,
    android: /-android(-development)?\.apk$/i,
  };

  const formatSize = (bytes) => bytes >= 1e6 ? `${(bytes / 1e6).toFixed(bytes >= 1e7 ? 0 : 1)} MB` : `${Math.max(1, Math.round(bytes / 1e3))} KB`;

  const setState = (state) => platforms.setAttribute("data-release-state", state);

  const choose = (releases) => {
    const usable = releases.filter((release) => !release.draft && release.assets.some((a) => patterns.android.test(a.name)));
    return usable.find((release) => !release.prerelease) || usable[0] || null;
  };

  const render = (release) => {
    if (!release) {
      setState("pending");
      status.textContent = "Coming soon";
      return;
    }
    let development = false;
    for (const [key, pattern] of Object.entries(patterns)) {
      const asset = release.assets.find((a) => pattern.test(a.name));
      const link = document.querySelector(`[data-asset="${key}"]`);
      if (!link) continue;
      link.href = asset ? asset.url : release.page;
      const size = document.querySelector(`[data-size="${key}"]`);
      if (asset && size && asset.size) size.textContent = formatSize(asset.size);
      if (asset && /-development\./i.test(asset.name)) development = true;
    }
    // Development builds are ad-hoc signed (Mac) and debug-signed (Android); say so.
    document.querySelectorAll("[data-dev-note]").forEach((note) => { note.hidden = !development; });
    const version = release.tag.replace(/^v/, "");
    status.textContent = release.prerelease ? `v${version}, pre-release` : `v${version}`;
    const notesLink = document.createElement("a");
    notesLink.href = release.page;
    notesLink.textContent = "Release notes and checksums";
    notes.replaceChildren(notesLink);
    setState("ready");
  };

  const fail = () => {
    // Links already point at the releases page, so the buttons still work.
    status.textContent = "Latest release on GitHub";
    setState("ready");
  };

  const load = async () => {
    try {
      const cached = JSON.parse(sessionStorage.getItem(CACHE_KEY) || "null");
      if (cached && Date.now() - cached.at < 10 * 60 * 1000) return render(cached.release);
    } catch { /* Storage can be unavailable in private modes. */ }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 8000);
    try {
      const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=15`, {
        headers: { Accept: "application/vnd.github+json" },
        signal: controller.signal,
      });
      if (!response.ok) throw new Error(`GitHub responded ${response.status}`);
      const picked = choose(await response.json());
      const release = picked && {
        tag: picked.tag_name,
        prerelease: picked.prerelease,
        page: picked.html_url || RELEASES_PAGE,
        assets: picked.assets.map((a) => ({ name: a.name, url: a.browser_download_url, size: a.size })),
      };
      try { sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), release })); } catch { /* ignore */ }
      render(release);
    } catch {
      fail();
    } finally {
      clearTimeout(timer);
    }
  };

  load();
})();
