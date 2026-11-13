(function () {
  var STORAGE_KEY = 'investlog.landing.theme';
  var root = document.documentElement;
  var lightQuery = window.matchMedia('(prefers-color-scheme: light)');
  var toggle = document.getElementById('themeToggle');
  var sunIcon = toggle.querySelector('.theme-icon-sun');
  var moonIcon = toggle.querySelector('.theme-icon-moon');
  var lightSources = Array.prototype.slice.call(document.querySelectorAll('source[data-light-source]'));

  function shownTheme() {
    return getComputedStyle(root).colorScheme === 'light' ? 'light' : 'dark';
  }

  function sourceMedia() {
    if (root.dataset.theme === 'light') return 'all';
    if (root.dataset.theme === 'dark') return 'not all';
    return '(prefers-color-scheme: light)';
  }

  function syncPage() {
    var theme = shownTheme();
    sunIcon.toggleAttribute('hidden', theme !== 'dark');
    moonIcon.toggleAttribute('hidden', theme !== 'light');
    toggle.setAttribute('aria-label', theme === 'dark' ? 'Ativar tema claro' : 'Ativar tema escuro');
    var media = sourceMedia();
    lightSources.forEach(function (source) {
      if (source.media !== media) source.media = media;
    });
  }

  function storeTheme(theme) {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch (error) {}
  }

  toggle.addEventListener('click', function () {
    var next = shownTheme() === 'dark' ? 'light' : 'dark';
    root.dataset.theme = next;
    storeTheme(next);
    syncPage();
  });
  lightQuery.addEventListener('change', syncPage);

  syncPage();
  toggle.hidden = false;
})();
