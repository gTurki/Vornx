// Apply the saved theme BEFORE React loads, so the page never flashes the wrong colors.
// (A separate file, not inline in index.html, so the Content-Security-Policy can forbid inline scripts.)
try {
  var t = localStorage.getItem('vornx.customer.theme')
  if (!t) t = 'light' // the design's default
  document.documentElement.dataset.theme = t
  // the phone's status bar / browser bar takes the page background (not a blue strip above a dark page)
  var m = document.querySelector('meta[name="theme-color"]')
  if (m) m.setAttribute('content', t === 'dark' ? '#080B14' : '#F4F6FB')
} catch (e) {}
