// Apply the saved theme BEFORE React loads, so the page never flashes the wrong colors.
// (A separate file, not inline in index.html, so the Content-Security-Policy can forbid inline scripts.)
try {
  var t = localStorage.getItem('vornx.customer.theme')
  if (!t) t = 'light' // the design's default
  document.documentElement.dataset.theme = t
} catch (e) {}
