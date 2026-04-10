(() => {
   // Footer year
   const y = document.getElementById("y");
   if (y) y.textContent = new Date().getFullYear();

   // Mobile drawer
   const header = document.querySelector(".nav");
   if (header) {
   const btn = document.getElementById("menu");
   const nav = header.querySelector("nav");
   let overlay = header.querySelector(".menu-overlay");

   if (!overlay) {
   overlay = document.createElement("div");
   overlay.className = "menu-overlay";
   overlay.hidden = true;
   header.appendChild(overlay);
}

   const FOCUSABLE =
   'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"]), input, select, textarea';

   let bodyTouchLock = null;
   let cleanupFocusTrap = () => {};

   const lockScroll = () => {
   bodyTouchLock = (e) => e.preventDefault();
   document.body.addEventListener("touchmove", bodyTouchLock, { passive: false });
   document.body.classList.add("no-scroll");
};

   const unlockScroll = () => {
   if (bodyTouchLock) {
   document.body.removeEventListener("touchmove", bodyTouchLock);
   bodyTouchLock = null;
}
   document.body.classList.remove("no-scroll");
};

   const trapFocus = (container) => {
   const focusables = container.querySelectorAll(FOCUSABLE);
   if (!focusables.length) return () => {};
   const first = focusables[0];
   const last = focusables[focusables.length - 1];

   const handler = (e) => {
   if (e.key !== "Tab") return;
   if (e.shiftKey && document.activeElement === first) {
   e.preventDefault();
   last.focus();
} else if (!e.shiftKey && document.activeElement === last) {
   e.preventDefault();
   first.focus();
}
};

   container.addEventListener("keydown", handler);
   return () => container.removeEventListener("keydown", handler);
};

   const openMenu = () => {
   if (header.classList.contains("open")) return;
   header.classList.add("open");
   btn && btn.setAttribute("aria-expanded", "true");
   overlay.hidden = false;
   lockScroll();

   cleanupFocusTrap = trapFocus(nav || header);
   const first = (nav || header).querySelector(FOCUSABLE);
   (first || btn || document.body).focus({ preventScroll: true });
};

   const closeMenu = () => {
   if (!header.classList.contains("open")) return;
   header.classList.remove("open");
   btn && btn.setAttribute("aria-expanded", "false");
   overlay.hidden = true;
   unlockScroll();
   cleanupFocusTrap();
   cleanupFocusTrap = () => {};
   btn && btn.focus({ preventScroll: true });
};

   const toggleMenu = () => (header.classList.contains("open") ? closeMenu() : openMenu());

   btn && btn.addEventListener("click", toggleMenu);
   overlay.addEventListener("click", closeMenu);

   if (nav) {
   nav.addEventListener("click", (e) => {
   const a = e.target.closest("a");
   if (a) closeMenu();
});
}

   window.addEventListener("keydown", (e) => {
   if (e.key === "Escape") closeMenu();
});

   window.addEventListener("hashchange", closeMenu);
   window.addEventListener("popstate", closeMenu);

   const handleResize = () => {
   const isDesktop = window.matchMedia("(min-width:1200px)").matches;
   if (isDesktop) {
   closeMenu();
   if (nav) nav.style.removeProperty("display");
}
};

   let resizeTimer;
   const debouncedResize = () => {
   clearTimeout(resizeTimer);
   resizeTimer = setTimeout(handleResize, 120);
};

   window.addEventListener("resize", debouncedResize);
   window.addEventListener("orientationchange", debouncedResize);
   handleResize();
}

   // Scroll reveal
   const els = document.querySelectorAll(".reveal");
   if (els.length) {
   if (!("IntersectionObserver" in window)) {
   els.forEach((el) => el.classList.add("show"));
} else {
   const io = new IntersectionObserver(
   (entries) => {
   entries.forEach((entry) => {
   if (entry.isIntersecting) {
   entry.target.classList.add("show");
   io.unobserve(entry.target);
}
});
},
{ rootMargin: "0px 0px -10% 0px", threshold: 0.08 }
   );

   els.forEach((el) => io.observe(el));
}
}
})();
