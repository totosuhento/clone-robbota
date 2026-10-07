/*
 * Asisten Marketplace — pengisi form "Tawaran baru" Facebook versi web.
 * Dijalankan di WebView milik aplikasi. Mengisi foto, judul, harga, kategori,
 * kondisi, deskripsi, dan lokasi. TIDAK menekan "Terbitkan"; hanya mendeteksi
 * saat pengguna menekannya.
 *
 * Komunikasi ke aplikasi lewat objek AsistenBridge:
 *   log(msg), step(name, ok, note), finished(json), published(), photoCount(), photo(i)
 */
(function () {
  if (window.__asisten) return;

  var B = window.AsistenBridge;
  function log(m) { try { B.log(String(m)); } catch (e) {} }
  function step(name, ok, note) { try { B.step(name, !!ok, note || ''); } catch (e) {} }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function norm(s) { return (s || '').replace(/\s+/g, ' ').trim().toLowerCase(); }

  async function waitFor(fn, timeout, every) {
    var end = Date.now() + (timeout || 8000);
    while (Date.now() < end) {
      try { var v = fn(); if (v) return v; } catch (e) {}
      await sleep(every || 300);
    }
    return null;
  }

  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var st = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';
  }

  /** Nama aksesibel elemen: aria-label, aria-labelledby, atau teks <label> pembungkus. */
  function nameOf(el) {
    var n = el.getAttribute('aria-label') || '';
    var lb = el.getAttribute('aria-labelledby');
    if (!n && lb) {
      n = lb.split(/\s+/).map(function (id) {
        var e = document.getElementById(id);
        return e ? e.innerText : '';
      }).join(' ');
    }
    if (!n) {
      var l = el.closest('label');
      if (l) n = l.innerText;
    }
    if (!n) n = el.getAttribute('placeholder') || '';
    return norm(n);
  }

  function findByName(selector, labels) {
    var els = Array.prototype.slice.call(document.querySelectorAll(selector)).filter(visible);
    for (var i = 0; i < labels.length; i++) {
      var want = norm(labels[i]);
      for (var j = 0; j < els.length; j++) {
        var nm = nameOf(els[j]);
        if (nm === want || nm.indexOf(want) === 0) return els[j];
      }
    }
    return null;
  }

  /** Klik yang meniru urutan event mouse/pointer asli (lebih andal untuk React). */
  function realClick(el) {
    if (!el) return false;
    try { el.scrollIntoView({ block: 'center' }); } catch (e) {}
    var r = el.getBoundingClientRect();
    var o = { bubbles: true, cancelable: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
    ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function (t) {
      try {
        var ev = t.indexOf('pointer') === 0 ? new PointerEvent(t, o) : new MouseEvent(t, o);
        el.dispatchEvent(ev);
      } catch (e) {}
    });
    return true;
  }

  function optionNodes() {
    var sel = '[role=option],[role=menuitem],[role=menuitemradio],[role=radio],[role=listbox] [role=button],[role=dialog] [role=button],[role=listbox] div[tabindex]';
    return Array.prototype.slice.call(document.querySelectorAll(sel)).filter(visible);
  }

  function findOption(wanted) {
    var opts = optionNodes();
    var w = wanted.map(norm);
    for (var i = 0; i < w.length; i++) {
      var exact = opts.find(function (o) { return norm(o.innerText) === w[i]; });
      if (exact) return exact;
    }
    for (var k = 0; k < w.length; k++) {
      var starts = opts.find(function (o) { return norm(o.innerText).indexOf(w[k]) === 0; });
      if (starts) return starts;
    }
    for (var m = 0; m < w.length; m++) {
      var has = opts.find(function (o) { return norm(o.innerText).indexOf(w[m]) >= 0; });
      if (has) return has;
    }
    return null;
  }

  /** Daftar kolom yang terlihat, untuk diagnosa bila ada yang tidak ketemu. */
  function describeFields() {
    var els = Array.prototype.slice.call(document.querySelectorAll(
      'input,textarea,[role=combobox],[aria-haspopup]')).filter(visible);
    return els.slice(0, 25).map(function (e) {
      return (e.getAttribute('role') || e.tagName.toLowerCase()) + ':' + (nameOf(e) || '?');
    }).join(' | ');
  }

  function photoCounter() {
    var m = /(foto|photos?)\s*[·:•]?\s*(\d+)\s*\/\s*\d+/i.exec(document.body.innerText || '');
    return m ? parseInt(m[2], 10) : null;
  }

  /**
   * Ketik nilai seperti keyboard asli (execCommand insertText), supaya kolom
   * berformat seperti Harga ("Rp 29.000") menerima angka dengan benar.
   * Cadangan: setter nilai React.
   */
  function typeInto(el, value) {
    value = String(value);
    try { el.scrollIntoView({ block: 'center' }); } catch (e) {}
    el.focus();
    try { el.select(); } catch (e) {}
    try { document.execCommand('selectAll', false, null); } catch (e) {}
    var ok = false;
    try { ok = document.execCommand('insertText', false, value); } catch (e) { ok = false; }
    if (!ok || !matches(el.value, value)) {
      var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }
    return matches(el.value, value);
  }

  /** Sama bila teksnya sama, atau (untuk angka) digitnya sama: "Rp29.000" == "29000". */
  function matches(actual, wanted) {
    if (norm(actual) === norm(wanted)) return true;
    var a = String(actual || '').replace(/\D/g, ''), w = String(wanted || '').replace(/\D/g, '');
    return /^\d+$/.test(String(wanted).trim()) && a === w;
  }
  // nama lama tetap dipakai oleh fungsi lain
  function setValue(el, value) { return typeInto(el, value); }

  // ------------------------------------------------------------------ pemilih opsi pintar

  function canon(s) {
    return norm(s).replace(/&/g, ' dan ').replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
  }

  /** Skor kemiripan 0..1 antara teks opsi dan teks yang dicari. */
  function similarity(option, wanted) {
    var o = canon(option), w = canon(wanted);
    if (!o || !w) return 0;
    if (o === w) return 1;
    if (o.indexOf(w) === 0 || w.indexOf(o) === 0) return 0.9;
    if (o.indexOf(w) >= 0 || w.indexOf(o) >= 0) return 0.8;
    var ot = o.split(' ').filter(function (t) { return t.length > 2 && t !== 'dan'; });
    var wt = w.split(' ').filter(function (t) { return t.length > 2 && t !== 'dan'; });
    if (!ot.length || !wt.length) return 0;
    var hit = wt.filter(function (t) {
      return ot.some(function (x) { return x === t || x.indexOf(t) === 0 || t.indexOf(x) === 0; });
    }).length;
    return 0.7 * hit / Math.max(wt.length, ot.length);
  }

  /** Elemen teks yang benar-benar tampil paling atas (tidak tertutup lapisan lain). */
  function topmostTextItems(minTop) {
    var out = [], seen = {};
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      var t = (node.nodeValue || '').replace(/\s+/g, ' ').trim();
      if (t.length < 2 || t.length > 50) continue;
      var el = node.parentElement;
      if (!el || !visible(el)) continue;
      var r = el.getBoundingClientRect();
      if (r.bottom < 0 || r.top > window.innerHeight || r.top < (minTop || 0)) continue;
      var cx = r.left + Math.min(r.width / 2, 20), cy = r.top + r.height / 2;
      var hit = document.elementFromPoint(cx, cy);
      if (!hit || !(hit === el || el.contains(hit) || hit.contains(el))) continue;
      var key = t + '|' + Math.round(r.top);
      if (seen[key]) continue;
      seen[key] = 1;
      out.push({ text: t, el: el });
    }
    return out;
  }

  function clickableOf(el) {
    return el.closest('[role=option],[role=menuitem],[role=menuitemradio],[role=radio],[role=button],[tabindex]') || el;
  }

  function scrollableAncestor(el) {
    var p = el;
    while (p && p !== document.body) {
      var st = getComputedStyle(p);
      if ((st.overflowY === 'auto' || st.overflowY === 'scroll') && p.scrollHeight > p.clientHeight + 10) return p;
      p = p.parentElement;
    }
    return null;
  }

  /**
   * Buka dropdown berlabel [labels], (opsional) ketik di kolom cari, lalu pilih opsi paling mirip.
   * Bila [learn], semua teks opsi yang terlihat dikumpulkan untuk aplikasi (daftar kategori FB).
   */
  async function smartPick(name, labels, wanted, opts) {
    opts = opts || {};
    wanted = (wanted || []).filter(function (w) { return w && String(w).trim(); });
    if (!wanted.length) { step(name, true, 'kosong, dilewati'); return true; }
    var box = await waitFor(function () {
      return findByName('[role=combobox],label[aria-haspopup],div[aria-haspopup],[aria-haspopup=listbox]', labels) ||
        findByName('label,div[role=button]', labels);
    }, 6000);
    if (!box) { step(name, false, 'dropdown tidak ditemukan'); return false; }
    if (wanted.some(function (w) { return similarity(box.innerText.replace(new RegExp(labels[0], 'i'), ''), w) >= 0.9; })) {
      step(name, true, 'sudah terisi'); return true;
    }
    var boxBottom = box.getBoundingClientRect().top;
    realClick(box);
    await sleep(1000);

    var search = Array.prototype.slice.call(document.querySelectorAll('input')).filter(visible)
      .filter(function (i) { return i !== box && !box.contains(i) && /search|cari/i.test((i.getAttribute('aria-label') || '') + (i.placeholder || '') + (i.type || '')); })[0];
    var learned = {};
    var best = null, bestScore = 0;

    function scan() {
      topmostTextItems(0).forEach(function (it) {
        if (search && (it.el === search || it.el.contains(search))) return;
        if (opts.learn) learned[it.text] = 1;
        wanted.forEach(function (w) {
          var s = similarity(it.text, w);
          if (s > bestScore) { bestScore = s; best = it; }
        });
      });
    }

    if (search && opts.searchText && !opts.learn) {
      typeInto(search, opts.searchText);
      await sleep(1500);
    }
    scan();
    // Gulir daftar bila belum ketemu yang persis (atau sedang merekam daftar).
    var scroller = best ? scrollableAncestor(best.el) : null;
    if (!scroller) {
      var items = topmostTextItems(boxBottom);
      if (items.length) scroller = scrollableAncestor(items[items.length - 1].el);
    }
    for (var i = 0; i < (opts.learn ? 25 : 12) && (bestScore < 1 || opts.learn); i++) {
      if (!scroller) break;
      var before = scroller.scrollTop;
      scroller.scrollTop = before + scroller.clientHeight * 0.8;
      await sleep(450);
      if (scroller.scrollTop === before) break;
      scan();
    }
    if (opts.learn) {
      try { B.categories(JSON.stringify(Object.keys(learned))); } catch (e) {}
    }
    if (!best || bestScore < (opts.minScore || 0.45)) {
      step(name, false, '"' + wanted[0] + '" tidak ada di pilihan Facebook');
      try { document.activeElement && document.activeElement.blur(); } catch (e) {}
      try { document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); } catch (e) {}
      return false;
    }
    try { best.el.scrollIntoView({ block: 'center' }); } catch (e) {}
    await sleep(300);
    realClick(clickableOf(best.el));
    await sleep(900);
    step(name, true, best.text + (bestScore < 1 ? ' (paling mirip)' : ''));
    return true;
  }

  // ------------------------------------------------------------------ langkah

  async function addPhotos() {
    var n = 0;
    try { n = B.photoCount(); } catch (e) {}
    if (!n) { step('Foto', false, 'produk tidak punya foto'); return false; }

    var input = await waitFor(function () {
      var all = Array.prototype.slice.call(document.querySelectorAll('input[type=file]'));
      return all.find(function (i) { return /image/i.test(i.accept || '') || !i.accept; });
    }, 10000);
    if (!input) { step('Foto', false, 'kolom unggah foto tidak ditemukan'); return false; }

    var before = photoCounter() || 0;
    var dt = new DataTransfer();
    for (var i = 0; i < n; i++) {
      var b64 = B.photo(i);
      if (!b64) continue;
      var bin = atob(b64);
      var arr = new Uint8Array(bin.length);
      for (var j = 0; j < bin.length; j++) arr[j] = bin.charCodeAt(j);
      dt.items.add(new File([arr], 'foto_' + (i + 1) + '.jpg', { type: 'image/jpeg', lastModified: Date.now() }));
    }
    if (!input.multiple && dt.files.length > 1) {
      for (var k = 0; k < dt.files.length; k++) {
        var one = new DataTransfer();
        one.items.add(dt.files[k]);
        var inp = document.querySelectorAll('input[type=file]')[0] || input;
        inp.files = one.files;
        inp.dispatchEvent(new Event('change', { bubbles: true }));
        await sleep(2500);
      }
    } else {
      input.files = dt.files;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    var after = await waitFor(function () {
      var c = photoCounter();
      return c !== null && c > before ? c : null;
    }, 15000, 500);
    step('Foto', !!after, after ? (after + ' foto masuk') : 'jumlah foto tidak bertambah');
    return !!after;
  }

  async function fillText(name, labels, value, selector) {
    if (!value) { step(name, true, 'kosong, dilewati'); return true; }
    var el = await waitFor(function () { return findByName(selector || 'input,textarea', labels); }, 8000);
    if (!el) { step(name, false, 'kolom tidak ditemukan'); return false; }
    var ok = typeInto(el, value);
    if (!ok) { await sleep(400); ok = typeInto(el, value); }
    step(name, ok, ok ? '' : 'nilai tidak tersimpan (terisi: ' + (el.value || '').slice(0, 20) + ')');
    return ok;
  }

  async function fillLocation(location) {
    if (!location) { step('Lokasi', true, 'kosong, dilewati'); return true; }
    var city = location.split(',')[0].trim();
    var el = await waitFor(function () { return findByName('input', ['Lokasi', 'Location']); }, 6000);
    if (!el) { step('Lokasi', false, 'kolom lokasi tidak ditemukan'); return false; }
    if (norm(el.value).indexOf(norm(city)) >= 0) { step('Lokasi', true, 'sudah ' + city); return true; }
    typeInto(el, city);
    await sleep(1800);
    var top = el.getBoundingClientRect().bottom - 5;
    var best = null, score = 0;
    topmostTextItems(top).forEach(function (it) {
      [location, city].forEach(function (w) {
        var s = similarity(it.text, w);
        if (s > score) { score = s; best = it; }
      });
    });
    if (!best || score < 0.5) { step('Lokasi', false, 'saran "' + city + '" tidak muncul'); return false; }
    realClick(clickableOf(best.el));
    await sleep(700);
    step('Lokasi', true, best.text);
    return true;
  }

  /** Kolom "Label produk": ketik tiap label lalu Enter. */
  async function fillTags(tags) {
    if (!tags || !tags.length) return true;
    var el = await waitFor(function () { return findByName('input,textarea', ['Label produk', 'Label', 'Product tags', 'Tags']); }, 2500);
    if (!el) { step('Label', true, 'kolom label tidak ada, label masuk ke deskripsi'); return true; }
    for (var i = 0; i < Math.min(tags.length, 20); i++) {
      typeInto(el, tags[i]);
      await sleep(250);
      ['keydown', 'keypress', 'keyup'].forEach(function (t) {
        el.dispatchEvent(new KeyboardEvent(t, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
      });
      await sleep(400);
    }
    step('Label', true, tags.length + ' label');
    return true;
  }

  function installPublishWatcher() {
    if (window.__asistenWatch) return;
    window.__asistenWatch = true;
    document.addEventListener('click', function (e) {
      var b = e.target && e.target.closest && e.target.closest('[role=button],button');
      if (!b) return;
      var t = norm(b.innerText || b.getAttribute('aria-label'));
      if (t === 'terbitkan' || t === 'publish' || t === 'publikasikan' || t === 'posting') {
        try { B.published(); } catch (err) {}
      }
    }, true);
  }

  /** Tekan "Berikutnya" (bukan Terbitkan) supaya tombol Terbitkan tampil. */
  async function goNext() {
    var btn = Array.prototype.slice.call(document.querySelectorAll('[role=button],button')).filter(visible)
      .find(function (b) { var t = norm(b.innerText || b.getAttribute('aria-label')); return t === 'berikutnya' || t === 'next'; });
    if (!btn) return false;
    if (btn.getAttribute('aria-disabled') === 'true' || btn.disabled) { step('Berikutnya', false, 'masih ada kolom wajib kosong'); return false; }
    realClick(btn);
    await sleep(2000);
    var pub = Array.prototype.slice.call(document.querySelectorAll('[role=button],button')).filter(visible)
      .find(function (b) { var t = norm(b.innerText || b.getAttribute('aria-label')); return t === 'terbitkan' || t === 'publish'; });
    if (pub) { try { pub.scrollIntoView({ block: 'center' }); } catch (e) {} }
    step('Berikutnya', true, pub ? 'tombol Terbitkan siap' : '');
    return true;
  }

  async function run(d) {
    installPublishWatcher();
    var results = {};
    var form = await waitFor(function () {
      return findByName('input,textarea', ['Judul', 'Title']) || document.querySelector('input[type=file]');
    }, 15000);
    if (!form) {
      log('Form tidak ditemukan. Kolom terlihat: ' + describeFields());
      B.finished(JSON.stringify({ form: false }));
      return;
    }
    await sleep(800);
    results.foto = await addPhotos();
    results.judul = await fillText('Judul', ['Judul', 'Title'], d.title);
    results.harga = await fillText('Harga', ['Harga', 'Price'], String(d.price));
    // Kategori dulu: Facebook baru menampilkan Kondisi/Deskripsi/Lokasi setelah kategori dipilih.
    results.kategori = await smartPick('Kategori', ['Kategori', 'Category'], [d.category, d.categoryMain],
      { searchText: d.category, learn: !!d.learnCategories });
    if (!results.kategori && d.learnCategories) {
      // Daftar sudah direkam; coba sekali lagi dengan pencarian.
      results.kategori = await smartPick('Kategori', ['Kategori', 'Category'], [d.category, d.categoryMain],
        { searchText: d.category });
    }
    await sleep(800);
    results.kondisi = await smartPick('Kondisi', ['Kondisi', 'Condition'], d.conditionLabels, { minScore: 0.6 });
    results.deskripsi = await fillText('Deskripsi', ['Deskripsi', 'Description'], d.description, 'textarea,input');
    results.lokasi = await fillLocation(d.location);
    results.label = await fillTags(d.tags || []);
    var failed = Object.keys(results).filter(function (k) { return !results[k]; });
    if (failed.length) log('Kolom terlihat: ' + describeFields());
    if (results.foto && results.judul && results.harga && d.autoNext) await goNext();
    B.finished(JSON.stringify({ form: true, results: results }));
  }

  // ------------------------------------------------------------------ perbarui tawaran

  var RENEW = ['perbarui', 'perbarui tawaran', 'perbarui sekarang', 'renew', 'renew listing'];

  function renewButtons() {
    return Array.prototype.slice.call(document.querySelectorAll('[role=button],button,a[role=link]')).filter(visible)
      .filter(function (b) {
        var t = norm(b.innerText || b.getAttribute('aria-label'));
        return RENEW.indexOf(t) >= 0 && b.getAttribute('aria-disabled') !== 'true' && !b.__asistenDone;
      });
  }

  /** Gulir halaman "Tawaran Anda" untuk memuat lebih banyak, lalu hitung tombol Perbarui. */
  async function scanRenew() {
    var last = -1;
    for (var i = 0; i < 8; i++) {
      var n = renewButtons().length;
      if (n === last && i > 2) break;
      last = n;
      window.scrollBy(0, window.innerHeight * 0.9);
      await sleep(1200);
    }
    window.scrollTo(0, 0);
    await sleep(500);
    var count = renewButtons().length;
    try { B.renewFound(count); } catch (e) {}
    return count;
  }

  /** Tekan "Perbarui" satu per satu (maks [max]), dengan jeda, setelah pengguna mengonfirmasi. */
  async function renewAll(max) {
    var done = 0;
    for (var guard = 0; guard < 200 && done < max; guard++) {
      var b = renewButtons()[0];
      if (!b) {
        window.scrollBy(0, window.innerHeight * 0.9);
        await sleep(1200);
        if (!renewButtons()[0]) break;
        continue;
      }
      b.__asistenDone = true;
      realClick(b);
      await sleep(1500);
      // Sebagian versi menampilkan dialog konfirmasi "Perbarui".
      var confirm = Array.prototype.slice.call(document.querySelectorAll('[role=dialog] [role=button],[role=dialog] button')).filter(visible)
        .find(function (x) { return RENEW.indexOf(norm(x.innerText || x.getAttribute('aria-label'))) >= 0; });
      if (confirm) { realClick(confirm); await sleep(1500); }
      done++;
      try { B.renewProgress(done); } catch (e) {}
      await sleep(1500);
    }
    try { B.renewFinished(done); } catch (e) {}
    return done;
  }

  window.__asisten = { run: run, scanRenew: scanRenew, renewAll: renewAll };
  installPublishWatcher();
})();
