// Network recorder for the local Antigravity hub UI. Injected only into http://localhost:4400 pages.
// Logs fetch / XHR / WebSocket traffic through the AgyRec Java bridge; the app writes nothing
// unless the user turned recording on. Secrets are redacted here and again on the Kotlin side.
(function () {
  if (window.__agyRec || !window.AgyRec) return;
  window.__agyRec = true;
  var MAX = 40000;
  var seq = 0;
  function log(o) { try { o.ts = Date.now(); AgyRec.log(JSON.stringify(o)); } catch (e) {} }
  function on() { try { return AgyRec.enabled(); } catch (e) { return false; } }
  function red(u) { return String(u).replace(/#t=[^&]*/, '#t=<redacted>').replace(/([?&](?:t|token|key|code)=)[^&#]*/gi, '$1<redacted>'); }
  function hdrs(h) {
    var o = {};
    try {
      new Headers(h || {}).forEach(function (v, k) {
        o[k] = /csrf|authorization|cookie|token|secret|api-key/i.test(k) ? '<redacted>' : v;
      });
    } catch (e) {}
    return o;
  }
  // WebSocket frames carry Connect envelopes ({procedure, headers, payload}): redact headers and shrink
  // long strings (images) so each logged frame stays valid JSON.
  function wsData(d) {
    if (typeof d !== 'string') return '[binary ' + (d.byteLength || d.size || 0) + 'B]';
    try {
      var o = JSON.parse(d);
      (function walk(x, key) {
        if (!x || typeof x !== 'object') return;
        for (var k in x) {
          var v = x[k];
          if (/csrf|authorization|cookie|token|secret/i.test(k) && k !== 'streamId') { x[k] = '<redacted>'; continue; }
          if (typeof v === 'string' && v.length > 2000) x[k] = v.slice(0, 120) + '…<' + v.length + ' chars>';
          else if (typeof v === 'object') walk(v, k);
        }
      })(o);
      return JSON.stringify(o);
    } catch (e) { return d.slice(0, MAX); }
  }
  function b64(u8) {
    var s = '';
    for (var i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);
    return 'b64:' + btoa(s);
  }
  function isText(ct) { return /json|text|javascript|xml/.test(ct || '') && !/proto|grpc/.test(ct || ''); }
  async function reqBody(b) {
    if (b == null) return null;
    try {
      if (typeof b === 'string') return b.slice(0, MAX);
      if (b instanceof URLSearchParams) return b.toString().slice(0, MAX);
      if (typeof FormData !== 'undefined' && b instanceof FormData) {
        var parts = [];
        b.forEach(function (v, k) { parts.push(k + '=' + (typeof v === 'string' ? v.slice(0, 200) : '[file ' + (v.name || '') + ' ' + (v.type || '') + ' ' + (v.size || 0) + 'B]')); });
        return 'formdata:' + parts.join('&');
      }
      if (b instanceof Blob) b = await b.arrayBuffer();
      if (b instanceof ArrayBuffer) return b64(new Uint8Array(b, 0, Math.min(b.byteLength, MAX)));
      if (ArrayBuffer.isView(b)) return b64(new Uint8Array(b.buffer, b.byteOffset, Math.min(b.byteLength, MAX)));
    } catch (e) { return '[unreadable ' + e + ']'; }
    return '[' + Object.prototype.toString.call(b) + ']';
  }
  function timeout(ms) { return new Promise(function (r) { setTimeout(function () { r({ done: true, timeout: true }); }, ms); }); }
  async function resBody(r) {
    var ct = r.headers.get('content-type') || '';
    if (!r.body) return null;
    var rd = r.body.getReader(), chunks = [], n = 0, end = Date.now() + 20000;
    while (n < MAX && Date.now() < end) {
      var x = await Promise.race([rd.read(), timeout(Math.max(1, end - Date.now()))]);
      if (x.done) break;
      chunks.push(x.value); n += x.value.length;
    }
    try { rd.cancel(); } catch (e) {}
    var u8 = new Uint8Array(Math.min(n, MAX)), o = 0;
    for (var i = 0; i < chunks.length && o < u8.length; i++) {
      var k = Math.min(chunks[i].length, u8.length - o);
      u8.set(chunks[i].subarray(0, k), o); o += k;
    }
    if (isText(ct) || /connect\+json/.test(ct)) return new TextDecoder().decode(u8);
    return b64(u8);
  }

  // fetch (Connect-web uses this)
  var of = window.fetch;
  window.fetch = async function (input, init) {
    if (!on()) return of.apply(this, arguments);
    var id = ++seq;
    var url = typeof input === 'string' ? input : (input && input.url) || String(input);
    var method = (init && init.method) || (input && input.method) || 'GET';
    var headers = (init && init.headers) || (input && input.headers);
    log({ t: 'fetch', id: id, method: method, url: red(url), headers: hdrs(headers), body: await reqBody(init && init.body) });
    var t0 = Date.now(), res;
    try { res = await of.apply(this, arguments); }
    catch (e) { log({ t: 'fetch-error', id: id, error: String(e) }); throw e; }
    try {
      var c = res.clone();
      resBody(c).then(function (b) {
        log({ t: 'response', id: id, status: res.status, ms: Date.now() - t0, contentType: res.headers.get('content-type'), body: b });
      });
    } catch (e) {}
    return res;
  };

  // XMLHttpRequest
  var xo = XMLHttpRequest.prototype.open, xs = XMLHttpRequest.prototype.send, xh = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open = function (m, u) { this.__rec = { method: m, url: u, headers: {} }; return xo.apply(this, arguments); };
  XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
    if (this.__rec) this.__rec.headers[k] = /csrf|authorization|cookie|token|secret/i.test(k) ? '<redacted>' : v;
    return xh.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (body) {
    var x = this, r = x.__rec;
    if (r && on()) {
      var id = ++seq, t0 = Date.now();
      reqBody(body).then(function (b) { log({ t: 'xhr', id: id, method: r.method, url: red(r.url), headers: r.headers, body: b }); });
      x.addEventListener('loadend', function () {
        var out = null;
        try {
          if (x.responseType === '' || x.responseType === 'text') out = String(x.responseText).slice(0, MAX);
          else if (x.responseType === 'json') out = JSON.stringify(x.response).slice(0, MAX);
          else if (x.responseType === 'arraybuffer' && x.response) out = b64(new Uint8Array(x.response, 0, Math.min(x.response.byteLength, MAX)));
          else out = '[' + x.responseType + ']';
        } catch (e) { out = '[unreadable]'; }
        log({ t: 'xhr-response', id: id, status: x.status, ms: Date.now() - t0, contentType: x.getResponseHeader('content-type'), body: out });
      });
    }
    return xs.apply(this, arguments);
  };

  // WebSocket
  var OWS = window.WebSocket;
  if (OWS) {
    var W = function (u, p) {
      var ws = p === undefined ? new OWS(u) : new OWS(u, p);
      var id = ++seq;
      if (on()) log({ t: 'ws-open', id: id, url: red(u) });
      var send = ws.send;
      ws.send = function (d) {
        if (on()) log({ t: 'ws-send', id: id, data: wsData(d) });
        return send.apply(ws, arguments);
      };
      ws.addEventListener('message', function (ev) {
        if (on()) log({ t: 'ws-message', id: id, data: wsData(ev.data) });
      });
      return ws;
    };
    W.prototype = OWS.prototype;
    ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (k) { W[k] = OWS[k]; });
    window.WebSocket = W;
  }
  log({ t: 'page', url: red(location.href), ua: navigator.userAgent });
})();
