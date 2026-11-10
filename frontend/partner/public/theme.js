// Apply the saved theme BEFORE React loads, so the page never flashes the wrong colors.
// (A separate file, not inline in index.html, so the Content-Security-Policy can forbid inline scripts.)
try {
  var t = localStorage.getItem('vornx.partner.theme')
  if (!t) t = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  document.documentElement.dataset.theme = t
} catch (e) {}
