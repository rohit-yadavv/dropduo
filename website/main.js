// DropDuo website behavior. No dependencies; every feature degrades to static HTML.
(() => {
  "use strict";

  const REPO = "rohit-yadavv/dropduo";
  const RELEASES_PAGE = `https://github.com/${REPO}/releases`;
  const CACHE_KEY = "dropduo:release:v1";
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

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

  // Suggest the visitor's platform. User agents are a hint, so both stay visible.
  const ua = navigator.userAgent;
  const isAndroid = /Android/i.test(ua);
  const isMac = /Macintosh|Mac OS X/i.test(ua) && navigator.maxTouchPoints < 2;
  const platform = isAndroid ? "android" : isMac ? "mac" : null;
  if (platform) {
    const label = document.querySelector("[data-platform-label]");
    label.textContent = platform === "android" ? "Download for Android" : "Download for Mac";
    const card = document.querySelector(`[data-platform="${platform}"]`);
    card.classList.add("is-suggested");
    if (platform === "android") card.parentElement.prepend(card);
  }

  // Release assets. Published names follow docs/downloads.md:
  // DropDuo-vVERSION-macos-arm64[-development].zip, -macos-x86_64, -android.apk
  const platforms = document.querySelector(".platforms");
  const status = document.querySelector("[data-release-status]");
  const notes = document.querySelector("[data-release-notes]");
  const patterns = {
    "mac-arm64": /-macos-arm64(-development)?\.zip$/i,
    "mac-x86_64": /-macos-x86_64(-development)?\.zip$/i,
    android: /-android(-development)?\.apk$/i,
  };

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
    for (const [key, pattern] of Object.entries(patterns)) {
      const asset = release.assets.find((a) => pattern.test(a.name));
      const link = document.querySelector(`[data-asset="${key}"]`);
      if (!link) continue;
      link.href = asset ? asset.url : release.page;
    }
    const version = release.tag.replace(/^v/, "");
    status.textContent = release.prerelease ? `Version ${version}, pre-release` : `Version ${version}`;
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
        assets: picked.assets.map((a) => ({ name: a.name, url: a.browser_download_url })),
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
