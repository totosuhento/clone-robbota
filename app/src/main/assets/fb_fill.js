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

  /** Set nilai input/textarea yang dikendalikan React. */
  function setValue(el, value) {
    el.focus();
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    return norm(el.value) === norm(value);
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
      // Kolom hanya menerima satu file sekaligus: kirim satu per satu.
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
    var el = await waitFor(function () { return findByName(selector || 'input,textarea', labels); }, 6000);
    if (!el) { step(name, false, 'kolom tidak ditemukan'); return false; }
    var ok = setValue(el, value);
    step(name, ok, ok ? '' : 'nilai tidak tersimpan');
    return ok;
  }

  async function pickDropdown(name, labels, wanted, searchText) {
    if (!wanted || !wanted.length || !wanted[0]) { step(name, true, 'kosong, dilewati'); return true; }
    var box = await waitFor(function () {
      return findByName('[role=combobox],label[aria-haspopup],div[aria-haspopup],[aria-haspopup=listbox]', labels) ||
        findByName('label,div[role=button]', labels);
    }, 6000);
    if (!box) { step(name, false, 'dropdown tidak ditemukan'); return false; }
    if (wanted.some(function (w) { return norm(box.innerText).indexOf(norm(w)) >= 0; })) {
      step(name, true, 'sudah terisi'); return true;
    }
    realClick(box);
    await sleep(900);
    // Sebagian dropdown punya kolom pencarian.
    var search = Array.prototype.slice.call(document.querySelectorAll('[role=dialog] input,[role=listbox] input,input[type=search]'))
      .filter(visible)[0];
    if (search && searchText) { setValue(search, searchText); await sleep(1200); }
    var opt = await waitFor(function () { return findOption(wanted); }, 5000);
    if (!opt) {
      step(name, false, '"' + wanted[0] + '" tidak ada di pilihan');
      try { document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); } catch (e) {}
      return false;
    }
    realClick(opt);
    await sleep(700);
    step(name, true, norm(opt.innerText).slice(0, 40));
    return true;
  }

  async function fillLocation(location) {
    if (!location) { step('Lokasi', true, 'kosong, dilewati'); return true; }
    var city = location.split(',')[0].trim();
    var el = await waitFor(function () { return findByName('input', ['Lokasi', 'Location']); }, 5000);
    if (!el) { step('Lokasi', false, 'kolom lokasi tidak ditemukan'); return false; }
    if (norm(el.value).indexOf(norm(city)) >= 0) { step('Lokasi', true, 'sudah ' + city); return true; }
    setValue(el, city);
    await sleep(1500);
    var opt = await waitFor(function () { return findOption([location, city]); }, 6000);
    if (!opt) { step('Lokasi', false, 'saran "' + city + '" tidak muncul'); return false; }
    realClick(opt);
    await sleep(600);
    step('Lokasi', true, norm(opt.innerText).slice(0, 40));
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
    results.kategori = await pickDropdown('Kategori', ['Kategori', 'Category'], [d.category, d.categoryMain], d.category);
    results.kondisi = await pickDropdown('Kondisi', ['Kondisi', 'Condition'], d.conditionLabels, null);
    results.deskripsi = await fillText('Deskripsi', ['Deskripsi', 'Description'], d.description, 'textarea,input');
    results.lokasi = await fillLocation(d.location);
    var failed = Object.keys(results).filter(function (k) { return !results[k]; });
    if (failed.length) log('Kolom terlihat: ' + describeFields());
    if (results.foto && results.judul && results.harga && d.autoNext) await goNext();
    B.finished(JSON.stringify({ form: true, results: results }));
  }

  window.__asisten = { run: run };
  installPublishWatcher();
})();
