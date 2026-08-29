#!/usr/bin/env python3
"""
Pull the latest tarkeshstack/wordly site into www/, wrapped for the native
Android shell.

Usage:
    python3 scripts/sync-wordly.py <path-to-cloned-wordly-repo>

What it does:
  1. Copies manifest.json / sw.js / icon-192.png / icon-512.png into www/.
  2. Injects a small postMessage-based Web Speech API shim into the <head>
     of the embedded SpeakEasy and Writing Practice tool templates (they run
     in sandboxed blob: iframes and call window.speechSynthesis /
     window.SpeechRecognition directly; Android's WebView doesn't implement
     either, so those iframes forward speech requests to the parent page,
     which has the real native plugins).
  3. Appends the native bridge script to the end of <body>: status bar
     theming, hardware back button (closes whichever tool overlay is open,
     else exits), and native Text-to-Speech / Speech-Recognition plugin
     wiring (both a direct shim for the main page and a message relay that
     answers the iframe shim from step 2).

Re-run this after every content pull from the live site instead of hand-
patching www/index.html — it's the one place all of the native-shell
wiring lives.
"""
import sys
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

IFRAME_SPEECH_SHIM = """<script>
/* Web Speech API shim for embedded tool iframes (SpeakEasy, Writing
   Practice). Android's WebView implements neither window.speechSynthesis
   nor window.SpeechRecognition, so instead of calling them directly this
   forwards requests to the parent page over postMessage; the parent (see
   the CAPACITOR NATIVE BRIDGE script at the end of the main document)
   answers using the native TextToSpeech / SpeechRecognition plugins and
   relays results back the same way. No-op structurally when not embedded
   (window.parent === window) — falls through to native browser behavior. */
(function(){
  if(window.parent === window) return;
  var reqCounter = 0;
  var pending = {};

  function Utterance(text){
    this.text = text; this.lang = ''; this.rate = 1; this.pitch = 1; this.volume = 1;
    this.voice = null; this.onend = null; this.onerror = null;
  }
  window.SpeechSynthesisUtterance = Utterance;
  window.speechSynthesis = {
    getVoices: function(){ return []; },
    cancel: function(){ window.parent.postMessage({ type: 'wordly-speech:cancel' }, '*'); },
    speak: function(u){
      var id = 'tts' + (++reqCounter);
      pending[id] = u;
      window.parent.postMessage({
        type: 'wordly-speech:speak', reqId: id,
        text: u.text, lang: u.lang, rate: u.rate, pitch: u.pitch, volume: u.volume
      }, '*');
    },
    onvoiceschanged: null
  };

  var activeRecognizer = null;
  function NativeRecognition(){
    this.lang = ''; this.interimResults = true; this.continuous = false; this.maxAlternatives = 1;
    this.onresult = null; this.onerror = null; this.onend = null;
    this._id = null;
  }
  NativeRecognition.prototype.start = function(){
    var id = 'stt' + (++reqCounter);
    this._id = id;
    pending[id] = this;
    activeRecognizer = this;
    window.parent.postMessage({ type: 'wordly-speech:start', reqId: id, lang: this.lang }, '*');
  };
  NativeRecognition.prototype.stop = function(){
    window.parent.postMessage({ type: 'wordly-speech:stop' }, '*');
  };
  window.SpeechRecognition = NativeRecognition;
  window.webkitSpeechRecognition = NativeRecognition;

  window.addEventListener('message', function(e){
    var data = e.data;
    if(!data || typeof data !== 'object' || typeof data.type !== 'string' || data.type.indexOf('wordly-speech:') !== 0) return;
    var target = pending[data.reqId];
    if(data.type === 'wordly-speech:tts-end'){
      if(target && typeof target.onend === 'function') target.onend();
      delete pending[data.reqId];
    } else if(data.type === 'wordly-speech:tts-error'){
      if(target && typeof target.onerror === 'function') target.onerror({ error: data.error });
      delete pending[data.reqId];
    } else if(data.type === 'wordly-speech:stt-result'){
      if(target && typeof target.onresult === 'function'){
        var evt = { resultIndex: 0, results: [[{ transcript: data.transcript }]] };
        evt.results[0].isFinal = !!data.isFinal;
        target.onresult(evt);
      }
    } else if(data.type === 'wordly-speech:stt-error'){
      if(target && typeof target.onerror === 'function') target.onerror({ error: data.error });
    } else if(data.type === 'wordly-speech:stt-end'){
      if(target && typeof target.onend === 'function') target.onend();
      delete pending[data.reqId];
      if(activeRecognizer === target) activeRecognizer = null;
    }
  });
})();
</script>
"""

NATIVE_BRIDGE_SCRIPT = """
<script>
/* ============================================================
   CAPACITOR NATIVE BRIDGE (Android/iOS app shell)
   No-op when running as a plain website — window.Capacitor is
   only defined when the page is loaded inside the native app.

   Capacitor Android auto-injects window.Capacitor.Plugins.<Name> for
   every native plugin registered in capacitor.settings.gradle (see
   JSExport.getPluginJS in @capacitor/android) — there is no separate
   JS bundle to load for that. An earlier version of this bridge tried
   to <script src> the plugins' own dist/plugin.js UMD builds, but
   those reference a `capacitorExports` global that this runtime never
   defines (it only exists in @capacitor/core's standalone browser
   bundle, which isn't part of this app), so every one of those loads
   silently threw and left status bar theming, the back button, mic,
   and speaker all non-functional. Talk to window.Capacitor.Plugins
   directly instead.
   ============================================================ */
(function(){
  if(!window.Capacitor || !window.Capacitor.Plugins) return;

  (function(){
    const CapApp = window.Capacitor.Plugins.App;
    const CapStatusBar = window.Capacitor.Plugins.StatusBar;
    const CapTTS = window.Capacitor.Plugins.TextToSpeech;
    const CapSTT = window.Capacitor.Plugins.SpeechRecognition;
    const CapHttp = window.Capacitor.Plugins.CapacitorHttp;
    const CapFS = window.Capacitor.Plugins.Filesystem;
    const CapShare = window.Capacitor.Plugins.Share;

    if(CapStatusBar){
      CapStatusBar.setBackgroundColor({ color: '#FDFCFA' }).catch(function(){});
      CapStatusBar.setStyle({ style: 'Light' }).catch(function(){});
    }

    if(CapApp){
      // Close whichever full-screen overlay is open (onboarding tour, Quick Type,
      // Image Translator, SpeakEasy, Writing Practice); otherwise exit the app.
      const OVERLAY_IDS = ['tour-overlay', 'quicktype-overlay', 'scanline-overlay', 'speakeasy-overlay', 'writing-overlay'];
      CapApp.addListener('backButton', function(){
        const open = OVERLAY_IDS
          .map(function(id){ return document.getElementById(id); })
          .find(function(el){ return el && el.classList.contains('show'); });
        if(open){
          open.classList.remove('show');
        } else {
          CapApp.exitApp();
        }
      });
    }

    // ---- Native Speech-Recognition helper, shared by the direct shim below
    // and the message relay that answers the embedded tool iframes. ----
    //
    // The native plugin's start({partialResults:true}) call resolves the
    // instant listening begins — NOT when recognition finishes — and every
    // result (interim and final alike) streams through the 'partialResults'
    // event with no flag telling them apart. The actual end of an utterance
    // is signalled separately via the 'listeningState' event (status
    // 'stopped', fired from onEndOfSpeech), slightly BEFORE the recognizer's
    // final onResults callback delivers its best transcript as one last
    // 'partialResults' event — so treat the most recent transcript received
    // as final once 'stopped' fires, after a brief grace delay for that
    // trailing event. onError has no event path at all when partialResults
    // is true (the plugin call it would normally reject is already
    // resolved), so a timeout is the only backstop against that.
    function NativeRecognition(){
      this.lang = ''; this.interimResults = true; this.continuous = false; this.maxAlternatives = 1;
      this.onresult = null; this.onerror = null; this.onend = null;
      this._active = false;
    }
    NativeRecognition.prototype.start = function(){
      const self = this;
      if(!CapSTT){
        if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
        if(typeof self.onend === 'function') self.onend();
        return;
      }
      self._active = true;
      CapSTT.requestPermissions().then(function(perm){
        if(!self._active) return;
        if(perm && perm.speechRecognition && perm.speechRecognition !== 'granted'){
          self._active = false;
          if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
          if(typeof self.onend === 'function') self.onend();
          return;
        }

        let finished = false;
        let gotResult = false;
        let lastTranscript = '';
        let partialHandle = null;
        let stateHandle = null;
        let timeoutId = null;

        function cleanup(){
          if(partialHandle) partialHandle.then(function(h){ h.remove(); }).catch(function(){});
          if(stateHandle) stateHandle.then(function(h){ h.remove(); }).catch(function(){});
          if(timeoutId) clearTimeout(timeoutId);
        }
        function finish(errorCode){
          if(finished || !self._active) return;
          finished = true;
          self._active = false;
          cleanup();
          if(errorCode && typeof self.onerror === 'function') self.onerror({ error: errorCode });
          if(typeof self.onend === 'function') self.onend();
        }

        partialHandle = CapSTT.addListener('partialResults', function(res){
          if(finished || !self._active) return;
          const matches = (res && res.matches) || [];
          if(!matches.length) return;
          gotResult = true;
          lastTranscript = matches[0];
          const evt = { resultIndex: 0, results: [[{ transcript: matches[0] }]] };
          evt.results[0].isFinal = false;
          if(typeof self.onresult === 'function') self.onresult(evt);
        });
        stateHandle = CapSTT.addListener('listeningState', function(res){
          if(finished || !self._active) return;
          if(!res || res.status !== 'stopped') return;
          // Give the trailing onResults-driven 'partialResults' event a
          // moment to land before wrapping up with whatever we last heard.
          setTimeout(function(){
            if(finished || !self._active) return;
            if(gotResult){
              const evt = { resultIndex: 0, results: [[{ transcript: lastTranscript }]] };
              evt.results[0].isFinal = true;
              if(typeof self.onresult === 'function') self.onresult(evt);
              finish();
            } else {
              finish('no-speech');
            }
          }, 400);
        });
        timeoutId = setTimeout(function(){ finish(gotResult ? undefined : 'no-speech'); }, 15000);

        CapSTT.start({ language: self.lang || 'en-US', partialResults: true, popup: false, maxResults: 1 })
          .catch(function(err){
            const msg = (err && err.message) || '';
            const code = /permission|denied/i.test(msg) ? 'not-allowed' : (/network/i.test(msg) ? 'network' : 'no-speech');
            finish(code);
          });
      }).catch(function(){
        self._active = false;
        if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
        if(typeof self.onend === 'function') self.onend();
      });
    };
    NativeRecognition.prototype.stop = function(){
      // Don't clear _active here — that would make the 'listeningState:stopped'
      // event this stop() call itself triggers get ignored by the listener
      // above (it bails out once _active is false), so onend() would never
      // fire and the UI would stay stuck showing "listening". Let the normal
      // finish() flow (event or timeout) clear it once recognition actually
      // stops.
      if(CapSTT) CapSTT.stop().catch(function(){});
    };

    // ---- Direct shim for the main page's own speechSynthesis / mic usage ----
    if(CapTTS){
      function Utterance(text){
        this.text = text; this.lang = ''; this.rate = 1; this.pitch = 1; this.volume = 1;
        this.voice = null; this.onend = null; this.onerror = null;
      }
      window.SpeechSynthesisUtterance = Utterance;
      window.speechSynthesis = {
        getVoices: function(){ return []; },
        cancel: function(){ CapTTS.stop().catch(function(){}); },
        speak: function(u){
          CapTTS.speak({
            text: u.text, lang: u.lang || 'en-US', rate: u.rate || 1.0,
            pitch: u.pitch || 1.0, volume: u.volume == null ? 1.0 : u.volume,
            category: 'playback'
          }).then(function(){
            if(typeof u.onend === 'function') u.onend();
          }).catch(function(err){
            if(typeof u.onerror === 'function') u.onerror(err);
          });
        },
        onvoiceschanged: null
      };
    }
    window.SpeechRecognition = NativeRecognition;
    window.webkitSpeechRecognition = NativeRecognition;

    // ---- Native audio sharing for SpeakEasy (see the 'wordly-speech:share'
    // case below). WebView's own Web Share API proved unreliable here for
    // file attachments (both an on-click fetch and an eagerly-prefetched one
    // silently fell back to text-only — see patch_speakeasy_share_save in
    // sync-wordly.py for the full history), so this goes through genuine
    // native plugins instead: CapacitorHttp fetches the mp3 (native
    // networking, not subject to the TTS endpoint's missing CORS headers),
    // Filesystem saves it to the cache dir, and Share hands that file to
    // Android's real share sheet — a proven, widely-used path with none of
    // the WebView-specific quirks the other two attempts ran into.
    function shareSpeakEasyAudio(shareText, ttsText, lang){
      // Most share targets (WhatsApp included) don't show a caption
      // alongside a shared audio/voice attachment — Intent.EXTRA_TEXT
      // rides along in the intent (confirmed in @capacitor/share's own
      // Android source), but the receiving app's own UI has nowhere to
      // put it for audio specifically. Copy it to the clipboard as a
      // fallback the user can paste in as a follow-up message.
      try{ navigator.clipboard && navigator.clipboard.writeText(shareText); }catch(err){}

      function textOnlyFallback(){
        if(CapShare){
          CapShare.share({ title: 'SpeakEasy translation', text: shareText }).catch(function(){});
        }
      }
      if(!CapHttp || !CapFS || !CapShare){ textOnlyFallback(); return; }
      const trimmed = ttsText.length > 200 ? ttsText.slice(0, 200) : ttsText;
      const url = 'https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=' +
        encodeURIComponent(lang) + '&q=' + encodeURIComponent(trimmed);
      CapHttp.request({
        url: url,
        method: 'GET',
        responseType: 'arraybuffer',
        // This is an unofficial endpoint that may treat a native HTTP
        // client's default (non-browser) headers differently than the
        // browser-context fetch() it was originally designed to answer —
        // send headers that look like the real page it's meant for.
        headers: {
          'User-Agent': 'Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
          'Referer': 'https://translate.google.com/',
          'Accept': 'audio/mpeg,audio/*;q=0.9,*/*;q=0.8'
        }
      }).then(function(res){
        if(!res || res.status !== 200) throw new Error('tts http ' + (res && res.status));
        const base64 = res.data;
        if(!base64 || typeof base64 !== 'string') throw new Error('empty tts response');
        // Android's native encoder (Base64.DEFAULT) line-wraps every 76
        // chars; strip that out so a stricter base64 decoder on the
        // Filesystem write side can't choke on the embedded newlines.
        const cleanBase64 = base64.replace(/\s+/g, '');
        if(cleanBase64.length < 100) throw new Error('tts response too small');
        const path = 'speakeasy-share.mp3';
        return CapFS.writeFile({ path: path, data: cleanBase64, directory: 'CACHE' }).then(function(){
          return CapFS.getUri({ path: path, directory: 'CACHE' });
        });
      }).then(function(uriResult){
        return CapShare.share({
          title: 'SpeakEasy translation',
          text: shareText,
          files: [uriResult.uri],
          dialogTitle: 'Share translation'
        });
      }).catch(function(){
        textOnlyFallback();
      });
    }

    // ---- Native save/share for Scanline (Image Translator) — same reasoning
    // as shareSpeakEasyAudio above: a WebView's own download-via-<a download>
    // and Web Share API are both unreliable here, so this writes the
    // translated-image PNG to the cache dir and hands it to Android's real
    // share sheet, which lets the user pick where to save it (Files, Photos,
    // Drive, etc.) — there's no permission-free way to write straight into
    // shared storage on modern Android without that picker.
    function saveScanlineImage(base64Png, mode){
      if(!CapFS || !CapShare || !base64Png) return;
      const path = 'scanline-translated.png';
      CapFS.writeFile({ path: path, data: base64Png, directory: 'CACHE' }).then(function(){
        return CapFS.getUri({ path: path, directory: 'CACHE' });
      }).then(function(uriResult){
        return CapShare.share({
          title: mode === 'download' ? 'Save image' : 'Image Translator',
          text: mode === 'download' ? undefined : 'Translated with Wordly Image Translator',
          files: [uriResult.uri],
          dialogTitle: mode === 'download' ? 'Save image to your device' : 'Share translated image'
        });
      }).catch(function(){ /* user cancelled, or write/share failed — nothing to fall back to here */ });
    }
    window.addEventListener('message', function(e){
      const data = e.data;
      if(!data || typeof data !== 'object' || data.type !== 'wordly-scanline:save') return;
      saveScanlineImage(data.data, data.mode);
    });

    // ---- Message relay: answers the postMessage speech shim running inside
    // the SpeakEasy / Writing Practice blob: iframes (see sync-wordly.py). ----
    let activeRelayRecognition = null;
    window.addEventListener('message', function(e){
      const data = e.data;
      if(!data || typeof data !== 'object' || typeof data.type !== 'string' || data.type.indexOf('wordly-speech:') !== 0) return;
      const source = e.source;
      const reqId = data.reqId;
      if(data.type === 'wordly-speech:speak'){
        if(!CapTTS){
          try{ source.postMessage({ type: 'wordly-speech:tts-error', reqId, error: 'unsupported' }, '*'); }catch(err){}
          return;
        }
        CapTTS.speak({
          text: data.text, lang: data.lang || 'en-US', rate: data.rate || 1.0,
          pitch: data.pitch || 1.0, volume: data.volume == null ? 1.0 : data.volume,
          category: 'playback'
        }).then(function(){
          try{ source.postMessage({ type: 'wordly-speech:tts-end', reqId }, '*'); }catch(err){}
        }).catch(function(err){
          try{ source.postMessage({ type: 'wordly-speech:tts-error', reqId, error: (err && err.message) || 'tts-error' }, '*'); }catch(e2){}
        });
      } else if(data.type === 'wordly-speech:cancel'){
        if(CapTTS) CapTTS.stop().catch(function(){});
      } else if(data.type === 'wordly-speech:start'){
        if(activeRelayRecognition) activeRelayRecognition.stop();
        const rec = new NativeRecognition();
        rec.lang = data.lang || 'en-US';
        rec.onresult = function(evt){
          const r = evt.results[0];
          try{ source.postMessage({ type: 'wordly-speech:stt-result', reqId, isFinal: !!r.isFinal, transcript: r[0].transcript }, '*'); }catch(err){}
        };
        rec.onerror = function(evt){
          try{ source.postMessage({ type: 'wordly-speech:stt-error', reqId, error: evt.error }, '*'); }catch(err){}
        };
        rec.onend = function(){
          try{ source.postMessage({ type: 'wordly-speech:stt-end', reqId }, '*'); }catch(err){}
          if(activeRelayRecognition === rec) activeRelayRecognition = null;
        };
        activeRelayRecognition = rec;
        rec.start();
      } else if(data.type === 'wordly-speech:stop'){
        if(activeRelayRecognition){ activeRelayRecognition.stop(); activeRelayRecognition = null; }
      } else if(data.type === 'wordly-speech:share'){
        shareSpeakEasyAudio(data.text, data.ttsText, data.lang || 'en-US');
      }
    });
  })();
})();
</script>
"""


def patch_speakeasy_share_save(fragment: str) -> str:
    """Drop the "Save audio" button and make Share send the original + translated
    text together with the spoken translation as a playable audio attachment,
    so apps like WhatsApp deliver it as a voice note instead of a text snippet.

    Two prior approaches through the WebView's own Web Share API both failed
    in practice: fetching the TTS mp3 inside the click handler lost
    navigator.share({files})'s required user-activation window, and even
    fetching it eagerly beforehand ran into the TTS endpoint blocking a plain
    fetch() via CORS (no-cors mode got real bytes but the file share itself
    still silently fell back to text — WebView's Web Share Level 2 file
    support is evidently not reliable here regardless).

    So this routes the whole thing to the parent page via postMessage (see
    NATIVE_BRIDGE_SCRIPT's 'wordly-speech:share' handler) to use genuine
    native Android plugins instead of WebView Web APIs: CapacitorHttp to
    fetch the mp3 (native networking isn't subject to browser CORS at all),
    Filesystem to save it, and Share to hand it to Android's real share
    sheet — none of which have activation-window or CORS concerns."""

    old_buttons = """        <button class="icon-btn" id="shareBtn" type="button" aria-label="Share" title="Share">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 16.08a2.99 2.99 0 0 0-1.96.77l-7.13-4.15a3 3 0 0 0 0-1.4l7.05-4.11a3 3 0 1 0-.9-1.72l-7.05 4.11a3 3 0 1 0 0 4.84l7.13 4.15a3 3 0 1 0 2.86-2.49Z"/></svg>
        </button>
        <button class="icon-btn" id="saveBtn" type="button" aria-label="Save audio" title="Save audio">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7ZM5 18v2h14v-2H5Z"/></svg>
        </button>"""
    new_buttons = """        <button class="icon-btn" id="shareBtn" type="button" aria-label="Share" title="Share">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 16.08a2.99 2.99 0 0 0-1.96.77l-7.13-4.15a3 3 0 0 0 0-1.4l7.05-4.11a3 3 0 1 0-.9-1.72l-7.05 4.11a3 3 0 1 0 0 4.84l7.13 4.15a3 3 0 1 0 2.86-2.49Z"/></svg>
        </button>"""
    if old_buttons not in fragment:
        raise ValueError("speakeasy share/save buttons markup not found — upstream layout changed")
    fragment = fragment.replace(old_buttons, new_buttons)

    old_share_fn = """  function shareResult(text) {
    if (navigator.share) {
      navigator.share({ title: "SpeakEasy translation", text: text }).catch(function () {});
    } else {
      copyText(text);
      flashAction("Sharing isn't supported here — copied instead.");
    }
  }

  // Downloads the spoken translation as an mp3 via a free, keyless TTS endpoint
  // (translate.google.com's public TTS route) — an <a download> click is a plain
  // browser navigation, not a fetch(), so it isn't blocked by CORS the way reading
  // the audio bytes back into JS would be. Capped at ~200 chars, that endpoint's
  // practical limit per request.
  function saveAudio(text, langCode) {
    var trimmed = text.length > 200 ? text.slice(0, 200) : text;
    var url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" +
      encodeURIComponent(langCode) + "&q=" + encodeURIComponent(trimmed);
    var a = document.createElement("a");
    a.href = url;
    a.download = "speakeasy-" + langCode + ".mp3";
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    a.remove();
    flashAction("Downloading…");
  }"""
    new_share_fn = """  function shareTextOnly(text) {
    if (navigator.share) {
      navigator.share({ title: "SpeakEasy translation", text: text }).catch(function () {});
    } else {
      copyText(text);
      flashAction("Sharing isn't supported here — copied instead.");
    }
  }

  // Shares the original + translated text together with the spoken
  // translation's audio. When embedded in the native app, hands the whole
  // thing to the parent page over postMessage — it has real native plugins
  // (CapacitorHttp/Filesystem/Share) that can fetch and attach the audio
  // reliably, unlike this WebView's own Web Share API. Outside the app
  // (e.g. testing this tool standalone in a browser) falls back to the
  // original text-only share.
  function shareResult(originalText, translatedText, targetCode) {
    var combined = translatedText + "\\n" + originalText;
    if (window.parent && window.parent !== window) {
      window.parent.postMessage({ type: "wordly-speech:share", text: combined, ttsText: translatedText, lang: targetCode }, "*");
      // Most share targets (WhatsApp included) don't show a caption next to
      // a shared audio attachment, so the parent also copies this text to
      // the clipboard as a fallback — let the user know it's there to paste.
      flashAction("Sharing audio — text copied too, paste it if needed");
      return;
    }
    shareTextOnly(combined);
  }"""
    if old_share_fn not in fragment:
        raise ValueError("speakeasy shareResult/saveAudio functions not found — upstream logic changed")
    fragment = fragment.replace(old_share_fn, new_share_fn)

    old_listeners = """  document.getElementById("copyBtn").addEventListener("click", function () {
    if (state.result) copyText(state.result.translatedText);
  });
  document.getElementById("shareBtn").addEventListener("click", function () {
    if (state.result) shareResult(state.result.translatedText);
  });
  document.getElementById("saveBtn").addEventListener("click", function () {
    if (state.result) saveAudio(state.result.translatedText, state.result.targetCode);
  });"""
    new_listeners = """  document.getElementById("copyBtn").addEventListener("click", function () {
    if (state.result) copyText(state.result.translatedText + "\\n" + state.result.originalText);
  });
  document.getElementById("shareBtn").addEventListener("click", function () {
    if (state.result) shareResult(state.result.originalText, state.result.translatedText, state.result.targetCode);
  });"""
    if old_listeners not in fragment:
        raise ValueError("speakeasy copyBtn/shareBtn/saveBtn listeners not found — upstream logic changed")
    fragment = fragment.replace(old_listeners, new_listeners)

    return fragment


def patch_speakeasy_credit_caption(fragment: str) -> str:
    """Add a small "translated with Wordly" line directly under the
    translated-text result, tight against it with no gap, where "Wordly"
    itself is the (real, HTML) hyperlink — no separate URL text shown."""

    old_result = """      <p class="label" id="targetLabel">Translation</p>
      <p class="translated" id="translatedText"></p>
      <p class="action-flash" id="actionFlash" hidden></p>"""
    new_result = """      <p class="label" id="targetLabel">Translation</p>
      <p class="translated" id="translatedText"></p>
      <p class="wordly-credit">translated with <a href="https://tarkeshstack.github.io/wordly/" target="_blank" rel="noopener">Wordly</a></p>
      <p class="action-flash" id="actionFlash" hidden></p>"""
    if old_result not in fragment:
        raise ValueError("speakeasy translatedText/actionFlash markup not found — upstream layout changed")
    fragment = fragment.replace(old_result, new_result)

    old_css = """  .result-card .translated {
    margin: 0;
    font-family: var(--font-display);
    font-weight: 600;
    font-size: 19px;
  }"""
    new_css = """  .result-card .translated {
    margin: 0;
    font-family: var(--font-display);
    font-weight: 600;
    font-size: 19px;
  }
  .wordly-credit {
    margin: 0;
    font-size: 8px;
    color: var(--on-surface-variant);
  }
  .wordly-credit a {
    color: var(--chakra-blue);
    text-decoration: none;
  }
  .wordly-credit a:hover, .wordly-credit a:active { text-decoration: underline; }"""
    if old_css not in fragment:
        raise ValueError("speakeasy .translated CSS not found — upstream styles changed")
    fragment = fragment.replace(old_css, new_css)

    return fragment


def patch_scanline_ocr_confidence(fragment: str) -> str:
    """Filter out Tesseract's low-confidence noise "detections" — e.g. a
    photo with no text at all (bark, leaves, texture) still produced short
    junk strings that then got "translated" into nonsense, making it look
    like a photo of a tree was mistranslated. Line-level confidence plus a
    minimum length and an actual-letter/digit check reject that noise while
    still accepting real (if imperfect) OCR text."""

    old_filter = """    lines = (data.lines || [])
      .map(l=>({ bbox: l.bbox, text: l.text.trim() }))
      .filter(l=> l.text.length > 0);"""
    new_filter = """    lines = (data.lines || [])
      .map(l=>({ bbox: l.bbox, text: l.text.trim(), confidence: l.confidence }))
      .filter(l=> l.text.length > 1 && (l.confidence === undefined || l.confidence >= 60) && /[\\p{L}\\p{N}]/u.test(l.text));"""
    if old_filter not in fragment:
        raise ValueError("scanline OCR line filter not found — upstream logic changed")
    fragment = fragment.replace(old_filter, new_filter)

    return fragment


def patch_scanline_lang_prompt(fragment: str) -> str:
    """Move the From/To language selects out of the always-visible top row
    and into a prompt that appears only after an image is uploaded, right
    where the selects used to sit — so translation doesn't start (with
    whatever languages happened to be selected before) until the user has
    actually confirmed the languages for this image."""

    old_top_row = """    <div class="topRow">
      <label class="fieldLabel">From
        <select id="fromSelect"></select>
      </label>
      <label class="fieldLabel">To
        <select id="toSelect"></select>
      </label>
      <div class="cornerActions">
        <button class="downloadCorner" id="shareBtn" disabled title="Share image with translation" aria-label="Share image with translation">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
            <path d="M8.6 10.5l6.8-3.9"/><path d="M8.6 13.5l6.8 3.9"/>
          </svg>
        </button>
        <button class="downloadCorner" id="downloadBtn" disabled title="Download image with translation" aria-label="Download image with translation">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3v12"/><path d="M7 10l5 5 5-5"/><path d="M5 21h14"/>
          </svg>
        </button>
      </div>
    </div>"""
    new_top_row = """    <div class="topRow">
      <div class="cornerActions">
        <button class="downloadCorner" id="shareBtn" disabled title="Share image with translation" aria-label="Share image with translation">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
            <path d="M8.6 10.5l6.8-3.9"/><path d="M8.6 13.5l6.8 3.9"/>
          </svg>
        </button>
        <button class="downloadCorner" id="downloadBtn" disabled title="Download image with translation" aria-label="Download image with translation">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3v12"/><path d="M7 10l5 5 5-5"/><path d="M5 21h14"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="topRow" id="langPrompt">
      <label class="fieldLabel">From
        <select id="fromSelect"></select>
      </label>
      <label class="fieldLabel">To
        <select id="toSelect"></select>
      </label>
      <button class="btn primary" id="translateBtn" type="button">Translate</button>
    </div>"""
    if old_top_row not in fragment:
        raise ValueError("scanline topRow markup not found — upstream layout changed")
    fragment = fragment.replace(old_top_row, new_top_row)

    old_css = """  .topRow, .actionRow{
    display:flex; flex-wrap:wrap; gap:10px; align-items:flex-end;
  }
  .topRow{ margin-top: 6px; }
  .actionRow{ margin-top:12px; }"""
    new_css = """  .topRow, .actionRow{
    display:flex; flex-wrap:wrap; gap:10px; align-items:flex-end;
  }
  .topRow{ margin-top: 6px; }
  .actionRow{ margin-top:12px; }
  #langPrompt{ display:none; }
  #langPrompt.show{ display:flex; }"""
    if old_css not in fragment:
        raise ValueError("scanline .topRow CSS not found — upstream styles changed")
    fragment = fragment.replace(old_css, new_css)

    old_consts = """const fromSelect = document.getElementById('fromSelect');
const toSelect = document.getElementById('toSelect');
const statusEl = document.getElementById('status');"""
    new_consts = """const fromSelect = document.getElementById('fromSelect');
const toSelect = document.getElementById('toSelect');
const langPrompt = document.getElementById('langPrompt');
const translateBtn = document.getElementById('translateBtn');
const statusEl = document.getElementById('status');"""
    if old_consts not in fragment:
        raise ValueError("scanline const declarations not found — upstream logic changed")
    fragment = fragment.replace(old_consts, new_consts)

    old_prevent_lang = """fromSelect.addEventListener('change', ()=>{ preventSameLang(fromSelect, toSelect); });
toSelect.addEventListener('change', ()=> preventSameLang(toSelect, fromSelect));"""
    new_prevent_lang = """fromSelect.addEventListener('change', ()=>{ preventSameLang(fromSelect, toSelect); });
toSelect.addEventListener('change', ()=> preventSameLang(toSelect, fromSelect));
translateBtn.addEventListener('click', ()=>{
  langPrompt.classList.remove('show');
  runScanAndTranslate();
});"""
    if old_prevent_lang not in fragment:
        raise ValueError("scanline language-select listeners not found — upstream logic changed")
    fragment = fragment.replace(old_prevent_lang, new_prevent_lang)

    old_load_file = """function loadFile(file){
  const img = new Image();
  img.onload = ()=>{
    baseImage = img;
    drawBase();
    placeholder.hidden = true;
    canvas.hidden = false;
    downloadBtn.disabled = true;
    shareBtn.disabled = true;
    resultsEl.hidden = true;
    lines = [];
    setStatus('');
    runScanAndTranslate();
  };
  img.src = URL.createObjectURL(file);
}"""
    new_load_file = """function loadFile(file){
  const img = new Image();
  img.onload = ()=>{
    baseImage = img;
    drawBase();
    placeholder.hidden = true;
    canvas.hidden = false;
    downloadBtn.disabled = true;
    shareBtn.disabled = true;
    resultsEl.hidden = true;
    lines = [];
    setStatus('');
    langPrompt.classList.add('show');
  };
  img.src = URL.createObjectURL(file);
}"""
    if old_load_file not in fragment:
        raise ValueError("scanline loadFile() not found — upstream logic changed")
    fragment = fragment.replace(old_load_file, new_load_file)

    return fragment


def patch_scanline_share_download(fragment: str) -> str:
    """Route Download/Share to the parent page's real Android Filesystem +
    Share plugins instead of a WebView <a download> click (which doesn't
    trigger Android's download manager here) and the Web Share API (already
    proven unreliable for file attachments in this WebView — see
    shareSpeakEasyAudio/patch_speakeasy_share_save). Falls back to the
    original browser-only behavior when opened standalone outside the app."""

    old_buttons = """downloadBtn.addEventListener('click', ()=>{
  if(!baseImage) return;
  canvas.toBlob(blob=>{
    if(!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'scanline-translated.png';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }, 'image/png');
});

shareBtn.addEventListener('click', ()=>{
  if(!baseImage) return;
  canvas.toBlob(async blob=>{
    if(!blob) return;
    const file = new File([blob], 'scanline-translated.png', { type: 'image/png' });
    if(navigator.canShare && navigator.canShare({ files: [file] })){
      try{
        await navigator.share({ files: [file], title: 'Image Translator', text: 'Translated with Image Translator' });
      }catch(err){
        // user cancelled the share sheet — nothing to do
      }
    } else if(navigator.share){
      try{
        await navigator.share({ title: 'Image Translator', text: 'Translated with Image Translator' });
      }catch(err){ /* cancelled */ }
    } else {
      setStatus('Sharing isn\\'t supported in this browser — try Download instead.', {err:true});
    }
  }, 'image/png');
});"""
    new_buttons = """downloadBtn.addEventListener('click', ()=>{
  if(!baseImage) return;
  if(window.parent && window.parent !== window){
    const base64 = canvas.toDataURL('image/png').split(',')[1];
    window.parent.postMessage({ type: 'wordly-scanline:save', mode: 'download', data: base64 }, '*');
    setStatus('Choose an app to save the image to (Files, Photos, Drive\\u2026).');
    return;
  }
  canvas.toBlob(blob=>{
    if(!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'scanline-translated.png';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }, 'image/png');
});

shareBtn.addEventListener('click', ()=>{
  if(!baseImage) return;
  if(window.parent && window.parent !== window){
    const base64 = canvas.toDataURL('image/png').split(',')[1];
    window.parent.postMessage({ type: 'wordly-scanline:save', mode: 'share', data: base64 }, '*');
    setStatus('Opening the share sheet\\u2026');
    return;
  }
  canvas.toBlob(async blob=>{
    if(!blob) return;
    const file = new File([blob], 'scanline-translated.png', { type: 'image/png' });
    if(navigator.canShare && navigator.canShare({ files: [file] })){
      try{
        await navigator.share({ files: [file], title: 'Image Translator', text: 'Translated with Image Translator' });
      }catch(err){
        // user cancelled the share sheet — nothing to do
      }
    } else if(navigator.share){
      try{
        await navigator.share({ title: 'Image Translator', text: 'Translated with Image Translator' });
      }catch(err){ /* cancelled */ }
    } else {
      setStatus('Sharing isn\\'t supported in this browser — try Download instead.', {err:true});
    }
  }, 'image/png');
});"""
    if old_buttons not in fragment:
        raise ValueError("scanline download/share button handlers not found — upstream logic changed")
    fragment = fragment.replace(old_buttons, new_buttons)

    return fragment


def patch_writing_persistent_back_btn(fragment: str) -> str:
    """The "back to Wordly" exit button lives inside #home-screen, which is
    display:none whenever #practice-screen is active — so once you're
    actually practicing there's no on-screen way to exit the tool (only the
    in-tool "‹ Back" link, which returns to the character grid, not to
    Wordly). Move the button to be a direct child of #app instead, so it
    stays rendered (and, thanks to its own position:absolute + z-index,
    visually anchored to the same top-left corner) across every screen."""

    old_home_open = """<div id="app">
  <!-- ============= HOME ============= -->
  <div id="home-screen" class="screen active">"""
    new_home_open = """<div id="app">
  <button class="wordly-back-btn" id="wordly-back-btn" title="Back to Wordly" aria-label="Back to Wordly">
    <svg width="54" height="26" viewBox="0 0 30 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M25 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
  </button>
  <!-- ============= HOME ============= -->
  <div id="home-screen" class="screen active">"""
    if old_home_open not in fragment:
        raise ValueError("writing #app/#home-screen markup not found — upstream layout changed")
    fragment = fragment.replace(old_home_open, new_home_open)

    old_inline_btn = """    <button class="wordly-back-btn" id="wordly-back-btn" title="Back to Wordly" aria-label="Back to Wordly">
      <svg width="54" height="26" viewBox="0 0 30 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M25 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
    </button>
    <div id="home-content">"""
    new_inline_btn = """    <div id="home-content">"""
    if old_inline_btn not in fragment:
        raise ValueError("writing inline wordly-back-btn not found — upstream layout changed")
    fragment = fragment.replace(old_inline_btn, new_inline_btn)

    return fragment


def patch_writing_remove_practice_back_link(fragment: str) -> str:
    """Remove the in-tool "‹ Back" link from the practice header — once the
    persistent "back to Wordly" exit button (see
    patch_writing_persistent_back_btn) is visible during practice too, this
    second, differently-scoped back control sitting right next to it in the
    same corner is redundant and visually clashes with it. The "Jump to a
    character" modal (tap the title) and the new Previous/Next buttons cover
    the same navigation need."""

    old_header = """    <div class="practice-header">
      <button class="back-link" id="practice-back-btn">‹ Back</button>
      <button class="practice-header-center" id="practice-open-grid-btn">
        <div class="practice-title" id="practice-title"></div>
        <div class="practice-progress" id="practice-progress"></div>
      </button>
    </div>"""
    new_header = """    <div class="practice-header">
      <button class="practice-header-center" id="practice-open-grid-btn">
        <div class="practice-title" id="practice-title"></div>
        <div class="practice-progress" id="practice-progress"></div>
      </button>
    </div>"""
    if old_header not in fragment:
        raise ValueError("writing practice-header markup not found — upstream layout changed")
    fragment = fragment.replace(old_header, new_header)

    old_listener = """  document.getElementById("practice-back-btn").onclick = function () {
    if (practice.autoAdvanceTimer) clearTimeout(practice.autoAdvanceTimer);
    state.scores = loadScores();
    showScreen("home-screen");
    renderHome();
  };
  document.getElementById("practice-open-grid-btn").onclick = openGridModal;"""
    new_listener = """  document.getElementById("practice-open-grid-btn").onclick = openGridModal;"""
    if old_listener not in fragment:
        raise ValueError("writing practice-back-btn listener not found — upstream logic changed")
    fragment = fragment.replace(old_listener, new_listener)

    return fragment


def patch_writing_guide_tolerance(fragment: str) -> str:
    """The tracing check only ever verified that most of what the user drew
    landed somewhere near *some* point of the guide (borderToleranceAccuracy
    — nearest-template-point distance per user point). For a guide made of
    several separate strokes spread across the canvas, that's satisfied by
    almost any scribble that happens to cross the guide's general area,
    without the user ever actually tracing its path — confirmed by a
    screenshot showing a handful of unrelated diagonal lines scored as
    "Great job!". Add the symmetric check (how much of the *guide* the user's
    strokes actually came near) and require both to pass, and tighten the
    radius (22 -> 14, in the 0-100 template-space grid) — together these
    catch strokes that touch the guide's neighborhood without following it."""

    old_radius = """  var BORDER_TOLERANCE_RADIUS = 22; // template-space units (canvas is 0-100); how far off the guide line a point may land"""
    new_radius = """  var BORDER_TOLERANCE_RADIUS = 14; // template-space units (canvas is 0-100); how far off the guide line a point may land"""
    if old_radius not in fragment:
        raise ValueError("writing BORDER_TOLERANCE_RADIUS not found — upstream logic changed")
    fragment = fragment.replace(old_radius, new_radius)

    old_accuracy_fn = """  function borderToleranceAccuracy(userStrokes, templateStrokes, radius) {
    var userPoints = flattenStrokes(userStrokes);
    var templatePoints = flattenStrokes(templateStrokes);
    if (userPoints.length === 0 || templatePoints.length === 0) return 0;
    var within = 0;
    for (var i = 0; i < userPoints.length; i++) {
      var u = userPoints[i];
      var best = Infinity;
      for (var j = 0; j < templatePoints.length; j++) {
        var t = templatePoints[j];
        var d = Math.hypot(u.x - t.x, u.y - t.y);
        if (d < best) best = d;
      }
      if (best <= radius) within++;
    }
    return within / userPoints.length;
  }"""
    new_accuracy_fn = """  function borderToleranceAccuracy(userStrokes, templateStrokes, radius) {
    var userPoints = flattenStrokes(userStrokes);
    var templatePoints = flattenStrokes(templateStrokes);
    if (userPoints.length === 0 || templatePoints.length === 0) return 0;
    var within = 0;
    for (var i = 0; i < userPoints.length; i++) {
      var u = userPoints[i];
      var best = Infinity;
      for (var j = 0; j < templatePoints.length; j++) {
        var t = templatePoints[j];
        var d = Math.hypot(u.x - t.x, u.y - t.y);
        if (d < best) best = d;
      }
      if (best <= radius) within++;
    }
    return within / userPoints.length;
  }

  // Symmetric to borderToleranceAccuracy: how much of the *guide* was
  // actually come near, rather than how much of what was drawn is near the
  // guide. A precise-but-tiny scribble in one corner of a multi-stroke
  // guide can pass the accuracy check alone without ever tracing most of
  // the character — this catches that.
  function borderToleranceCoverage(userStrokes, templateStrokes, radius) {
    var userPoints = flattenStrokes(userStrokes);
    var templatePoints = flattenStrokes(templateStrokes);
    if (userPoints.length === 0 || templatePoints.length === 0) return 0;
    var covered = 0;
    for (var i = 0; i < templatePoints.length; i++) {
      var t = templatePoints[i];
      var best = Infinity;
      for (var j = 0; j < userPoints.length; j++) {
        var u = userPoints[j];
        var d = Math.hypot(t.x - u.x, t.y - u.y);
        if (d < best) best = d;
      }
      if (best <= radius) covered++;
    }
    return covered / templatePoints.length;
  }"""
    if old_accuracy_fn not in fragment:
        raise ValueError("writing borderToleranceAccuracy not found — upstream logic changed")
    fragment = fragment.replace(old_accuracy_fn, new_accuracy_fn)

    old_score_fn = """    var radius = BORDER_TOLERANCE_RADIUS * (0.5 + tolerance);
    var accuracy = borderToleranceAccuracy(userStrokes, templateStrokes, radius);

    return {"""
    new_score_fn = """    var radius = BORDER_TOLERANCE_RADIUS * (0.5 + tolerance);
    var precision = borderToleranceAccuracy(userStrokes, templateStrokes, radius);
    var coverage = borderToleranceCoverage(userStrokes, templateStrokes, radius);
    var accuracy = Math.min(precision, coverage);

    return {"""
    if old_score_fn not in fragment:
        raise ValueError("writing scoreAttempt radius/accuracy lines not found — upstream logic changed")
    fragment = fragment.replace(old_score_fn, new_score_fn)

    return fragment


def patch_writing_prev_btn(fragment: str) -> str:
    """Add a "Previous" button to go back a character, and move it and the
    existing "Next" (skip) button to float over the left/right corners of
    the writing pad itself, leaving the "Redo" (clear) button alone and
    centered in the toolbar row above."""

    old_toolbar_and_pad = """    <div class="pad-toolbar">
      <button class="pad-icon-btn" id="clear-btn" title="Redo" aria-label="Redo">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></svg>
      </button>
      <button class="pad-icon-btn" id="skip-btn" title="Next" aria-label="Next">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M5 12h14"/><path d="M13 6l6 6-6 6"/></svg>
      </button>
    </div>

    <div class="pad-wrapper">
      <canvas id="pad-canvas"></canvas>
    </div>"""
    new_toolbar_and_pad = """    <div class="pad-toolbar">
      <button class="pad-icon-btn" id="clear-btn" title="Redo" aria-label="Redo">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></svg>
      </button>
    </div>

    <div class="pad-wrapper">
      <button class="pad-icon-btn pad-corner-btn pad-corner-left" id="prev-btn" title="Previous" aria-label="Previous">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M19 12H5"/><path d="M11 6l-6 6 6 6"/></svg>
      </button>
      <canvas id="pad-canvas"></canvas>
      <button class="pad-icon-btn pad-corner-btn pad-corner-right" id="skip-btn" title="Next" aria-label="Next">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M5 12h14"/><path d="M13 6l6 6-6 6"/></svg>
      </button>
    </div>"""
    if old_toolbar_and_pad not in fragment:
        raise ValueError("writing pad-toolbar/pad-wrapper markup not found — upstream layout changed")
    fragment = fragment.replace(old_toolbar_and_pad, new_toolbar_and_pad)

    old_css = """  .pad-wrapper { display: flex; justify-content: center; }"""
    new_css = """  .pad-wrapper { display: flex; justify-content: center; position: relative; }
  .pad-corner-btn { position: absolute; top: 50%; transform: translateY(-50%); z-index: 2; }
  .pad-corner-btn.pad-corner-left { left: -6px; }
  .pad-corner-btn.pad-corner-right { right: -6px; }"""
    if old_css not in fragment:
        raise ValueError("writing .pad-wrapper CSS not found — upstream styles changed")
    fragment = fragment.replace(old_css, new_css)

    old_toolbar_css = """  .pad-toolbar {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    max-width: 380px;
    margin: 0 auto 8px;
    padding: 0 4px;
  }"""
    new_toolbar_css = """  .pad-toolbar {
    display: flex;
    justify-content: center;
    gap: 8px;
    max-width: 380px;
    margin: 0 auto 8px;
    padding: 0 4px;
  }"""
    if old_toolbar_css not in fragment:
        raise ValueError("writing .pad-toolbar CSS not found — upstream styles changed")
    fragment = fragment.replace(old_toolbar_css, new_toolbar_css)

    old_listener = """document.getElementById("skip-btn").onclick = function () { goToIndex(practice.index + 1); };"""
    new_listener = """document.getElementById("prev-btn").onclick = function () { goToIndex(practice.index - 1); };
  document.getElementById("skip-btn").onclick = function () { goToIndex(practice.index + 1); };"""
    if old_listener not in fragment:
        raise ValueError("writing skip-btn listener not found — upstream logic changed")
    fragment = fragment.replace(old_listener, new_listener)

    return fragment


def inject_after_head(fragment: str) -> str:
    idx = fragment.find("<head>")
    if idx != -1:
        insert_at = idx + len("<head>")
    else:
        m = re.search(r"<html[^>]*>", fragment)
        if not m:
            raise ValueError("couldn't find <head> or <html> to inject after")
        insert_at = m.end()
    return fragment[:insert_at] + "\n" + IFRAME_SPEECH_SHIM + fragment[insert_at:]


def patch_template(html: str, template_id: str) -> str:
    start_marker = f'<template id="{template_id}">'
    start = html.index(start_marker)
    end = html.index("</template>", start)
    before, fragment, after = html[:start], html[start:end], html[end:]
    if template_id in ("speakeasy-src", "writing-src"):
        fragment = inject_after_head(fragment)
    if template_id == "speakeasy-src":
        fragment = patch_speakeasy_share_save(fragment)
        fragment = patch_speakeasy_credit_caption(fragment)
    elif template_id == "writing-src":
        fragment = patch_writing_persistent_back_btn(fragment)
        fragment = patch_writing_remove_practice_back_link(fragment)
        fragment = patch_writing_guide_tolerance(fragment)
        fragment = patch_writing_prev_btn(fragment)
    elif template_id == "scanline-src":
        fragment = patch_scanline_ocr_confidence(fragment)
        fragment = patch_scanline_lang_prompt(fragment)
        fragment = patch_scanline_share_download(fragment)
    return before + fragment + after


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    src_dir = Path(sys.argv[1])
    src_html = (src_dir / "index.html").read_text(encoding="utf-8")

    for template_id in ("speakeasy-src", "writing-src", "scanline-src"):
        src_html = patch_template(src_html, template_id)

    marker = "</body>"
    idx = src_html.rindex(marker)
    out_html = src_html[:idx] + NATIVE_BRIDGE_SCRIPT + src_html[idx:]

    www = ROOT / "www"
    (www / "index.html").write_text(out_html, encoding="utf-8")

    for asset in ("manifest.json", "sw.js", "icon-192.png", "icon-512.png"):
        shutil.copyfile(src_dir / asset, www / asset)

    print(f"wrote {www / 'index.html'} ({len(out_html)} chars)")


if __name__ == "__main__":
    main()
