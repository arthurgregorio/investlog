(function () {
  var links = Array.prototype.slice.call(document.querySelectorAll('.toc a'));
  var sections = links.map(function (link) { return document.getElementById(link.hash.slice(1)); });

  function markCurrent(id) {
    links.forEach(function (link) {
      if (link.hash === '#' + id) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }

  var observer = new IntersectionObserver(function (entries) {
    var visible = entries.filter(function (entry) { return entry.isIntersecting; });
    if (visible.length) markCurrent(visible[0].target.id);
  }, {rootMargin: '-90px 0px -70% 0px'});

  sections.forEach(function (section) { observer.observe(section); });
  markCurrent(sections[0].id);
})();
