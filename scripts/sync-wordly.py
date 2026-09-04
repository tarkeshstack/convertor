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
      // Close whichever full-screen overlay is open (onboarding tour, Quick
      // Type, SpeakEasy); otherwise exit the app.
      const OVERLAY_IDS = ['tour-overlay', 'quicktype-overlay', 'speakeasy-overlay'];
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
      this._userStopped = false;
    }
    NativeRecognition.prototype.start = function(){
      const self = this;
      if(!CapSTT){
        if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
        if(typeof self.onend === 'function') self.onend();
        return;
      }
      self._active = true;
      self._userStopped = false;

      // A pass that hears literally nothing usually just means the recognizer's
      // end-of-speech detector never triggered on quiet/whispered audio, not
      // that there was truly nothing to hear. Give it one silent second try
      // (no onerror/onend in between) before actually giving up.
      function attempt(retriesLeft){
        if(!self._active) return;

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
          cleanup();
          if(errorCode === 'no-speech' && retriesLeft > 0 && !self._userStopped){
            attempt(retriesLeft - 1);
            return;
          }
          self._active = false;
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
      }

      CapSTT.requestPermissions().then(function(perm){
        if(!self._active) return;
        if(perm && perm.speechRecognition && perm.speechRecognition !== 'granted'){
          self._active = false;
          if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
          if(typeof self.onend === 'function') self.onend();
          return;
        }
        attempt(1);
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
      // stops. _userStopped just tells that flow not to silently retry a
      // "no speech heard" outcome the user deliberately cut short.
      self._userStopped = true;
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

    // ---- Message relay: answers the postMessage speech shim running inside
    // the SpeakEasy blob: iframe (see sync-wordly.py). ----
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


def patch_speakeasy_theme(fragment: str) -> str:
    """Match SpeakEasy's own standalone page to the parent app's look: plain
    white background (no cream tint / decorative gradient blobs), plus a
    light-grey dark-mode palette that the parent syncs into this iframe over
    postMessage (see the 'wordly:theme' listener added below)."""
    old_root = """  :root {
    --saffron-soft: #FBE3C6;
    --green-soft: #E1F3EC;
    --saffron: #D9822F;
    --green: #2F8F4E;
    --chakra-blue: #2563EB;
    --accent: #B23A55;
    --primary-container: #F5DCE1;
    --maroon: #B23A55;
    --teal: #17A98F;
    --bg: #FBF3E9;
    --surface: #FFFFFF;
    --surface-variant: #F6F4EF;
    --on-surface: #2B2620;
    --on-surface-variant: #8C8375;
    --outline: #D9AAB2;
    --error: #C23B3B;
    --font-display: "Poppins", "Segoe UI", system-ui, sans-serif;
    --font-body: "Work Sans", "Segoe UI", system-ui, sans-serif;
  }"""
    new_root = """  :root {
    --saffron-soft: #FBE3C6;
    --green-soft: #E1F3EC;
    --saffron: #D9822F;
    --green: #2F8F4E;
    --chakra-blue: #2563EB;
    --accent: #B23A55;
    --primary-container: #F5DCE1;
    --maroon: #B23A55;
    --teal: #17A98F;
    --bg: #FFFFFF;
    --surface: #FFFFFF;
    --surface-variant: #F6F4EF;
    --on-surface: #2B2620;
    --on-surface-variant: #8C8375;
    --outline: #D9AAB2;
    --error: #C23B3B;
    --font-display: "Poppins", "Segoe UI", system-ui, sans-serif;
    --font-body: "Work Sans", "Segoe UI", system-ui, sans-serif;
  }
  :root[data-theme="dark"] {
    --bg: #D9D9DC;
    --surface: #E8E8EA;
    --surface-variant: #CFCFD2;
    --on-surface: #1F2024;
    --on-surface-variant: #55565C;
    --outline: #B7B7BB;
  }"""
    if old_root not in fragment:
        raise ValueError("speakeasy :root not found — upstream styles changed")
    fragment = fragment.replace(old_root, new_root)

    old_html_bg = """  html {
    margin: 0;
    min-height: 100%;
    background:
      radial-gradient(circle at top left, rgba(255,153,51,0.13) 0%, rgba(255,153,51,0.13) 14%, transparent 42%),
      radial-gradient(circle at bottom right, rgba(19,136,8,0.13) 0%, rgba(19,136,8,0.13) 14%, transparent 42%),
      var(--bg);
    background-attachment: fixed;
    background-size: cover;
  }"""
    new_html_bg = """  html {
    margin: 0;
    min-height: 100%;
    background: var(--bg);
  }"""
    if old_html_bg not in fragment:
        raise ValueError("speakeasy html background not found — upstream styles changed")
    fragment = fragment.replace(old_html_bg, new_html_bg)

    fragment = _remove_block(fragment, "  .chakra-watermark{", "\n\n  header.hero {")
    fragment = _remove_block(fragment, '<svg class="chakra-watermark"', "</svg>\n\n<div class=\"phone\">")

    old_close = """</script>
</body>
</html>"""
    new_close = """</script>
<script>
  window.addEventListener('message', function(e){
    if(!e.data || e.data.type !== 'wordly:theme') return;
    document.documentElement.setAttribute('data-theme', e.data.theme === 'dark' ? 'dark' : 'light');
  });
</script>
</body>
</html>"""
    if old_close not in fragment:
        raise ValueError("speakeasy closing </body></html> not found — upstream markup changed")
    idx = fragment.rindex(old_close)
    fragment = fragment[:idx] + new_close + fragment[idx + len(old_close):]

    return fragment


def _remove_block(html: str, start_marker: str, end_marker: str, *, use_rindex: bool = False) -> str:
    """Delete everything from start_marker up to (not including) end_marker.
    Pass use_rindex=True when end_marker's text also appears earlier inside
    the block being removed (e.g. a standalone tool's own inner </body></html>
    before the outer page's), so the *last* occurrence is used instead of the
    first one found after start_marker."""
    start = html.index(start_marker)
    end = html.rindex(end_marker) if use_rindex else html.index(end_marker, start)
    return html[:start] + html[end:]


def patch_remove_scanline_tool(html: str) -> str:
    """Drop the Image Translator (Scanline) tool entirely: its launcher
    button, overlay, iframe wiring, tour step, and the standalone <template>
    it was built from."""
    old_frame_ids = "#scanline-frame, #speakeasy-frame, #writing-frame{"
    new_frame_ids = "#speakeasy-frame{"
    if old_frame_ids not in html:
        raise ValueError("tool-frame CSS selector not found — upstream styles changed")
    html = html.replace(old_frame_ids, new_frame_ids)

    html = _remove_block(
        html,
        '<button class="photo-btn" id="ocr-open-btn"',
        '<button class="clear-btn" id="clear-btn">',
    )
    html = _remove_block(
        html,
        '<div class="scanline-overlay" id="scanline-overlay">',
        '<div class="scanline-overlay" id="speakeasy-overlay">',
    )
    html = _remove_block(
        html,
        "// ---------------- Scanline: image translator (embedded standalone app) ----------------",
        "// ---------------- Writing Practice (embedded standalone app) ----------------",
    )

    old_msg = "  if(e.data.app === 'scanline') closeScanline();\n"
    if old_msg not in html:
        raise ValueError("scanline back-message handler not found — upstream logic changed")
    html = html.replace(old_msg, "")

    old_tour_step = """  {
    target: '#search-box',
    title: 'Translate from a photo',
    desc: 'Tap the camera to scan text from a sign, label, or document and get an instant translation.'
  },
"""
    if old_tour_step not in html:
        raise ValueError("scanline tour step not found — upstream tour changed")
    html = html.replace(old_tour_step, "")

    html = _remove_block(html, '<template id="scanline-src">', '<template id="speakeasy-src">')
    return html


def patch_remove_writing_tool(html: str) -> str:
    """Drop the Writing Practice tool entirely: its pencil launcher link,
    overlay, iframe wiring, tour step, and the standalone <template> it was
    built from."""
    old_link_css = """  .writing-practice-link{
    position: absolute;
    right: -20px;
    bottom: -2px;
    z-index: 3;
    width: 20px;
    height: 20px;
    display:flex;
    align-items:center;
    justify-content:center;
    color: #7BA7E8;
    cursor: pointer;
  }
  .writing-practice-link:hover{ color: #2563EB; }
"""
    if old_link_css not in html:
        raise ValueError("writing-practice-link CSS not found — upstream styles changed")
    html = html.replace(old_link_css, "")

    old_wrap = """    <div class="script-glyph-wrap">
      <div class="script-glyph ml">അ</div>
      <a href="#" class="writing-practice-link" id="writing-practice-link" title="Practice writing letters">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
      </a>
    </div>"""
    new_wrap = """    <div class="script-glyph ml">അ</div>"""
    if old_wrap not in html:
        raise ValueError("writing-practice-link wrap not found — upstream markup changed")
    html = html.replace(old_wrap, new_wrap)

    html = _remove_block(
        html,
        '<div class="scanline-overlay" id="writing-overlay">',
        '<div class="bottom-bar">',
    )
    html = _remove_block(
        html,
        "// ---------------- Writing Practice (embedded standalone app) ----------------",
        "window.addEventListener('message', (e)=>{",
    )

    old_msg = "  if(e.data.app === 'writing') closeWriting();\n"
    if old_msg not in html:
        raise ValueError("writing back-message handler not found — upstream logic changed")
    html = html.replace(old_msg, "")

    old_tour_step = """  {
    target: '#writing-practice-link',
    title: 'Practice writing',
    desc: 'Tap the pencil to trace letters by hand and learn to write in a new script.'
  },
"""
    if old_tour_step not in html:
        raise ValueError("writing tour step not found — upstream tour changed")
    html = html.replace(old_tour_step, "")

    html = _remove_block(html, '<template id="writing-src">', "\n</body>\n</html>", use_rindex=True)
    return html


def patch_remove_footer_and_social(html: str) -> str:
    """Drop the "Design and Developed by Tarkesh" footer credit and the
    WhatsApp/Instagram/X/LinkedIn share-the-app row, plus their now-dead JS
    wiring and tour step, and reclaim the bottom padding that was reserved
    for the fixed bottom bar."""
    old_padding = "    padding: 18px 12px 90px;"
    new_padding = "    padding: 18px 12px 18px;"
    if old_padding not in html:
        raise ValueError("body padding not found — upstream styles changed")
    html = html.replace(old_padding, new_padding)

    html = _remove_block(
        html,
        '<div class="bottom-bar">',
        "<script>\n// ---------------- Dictionary data ----------------",
    )

    old_listener = """document.getElementById('share-apps-row').addEventListener('click', (e)=>{
  const btn = e.target.closest('.share-app-btn');
  if(!btn) return;
  shareViaApp(btn.getAttribute('data-app'), buildFullShareText());
});

"""
    if old_listener not in html:
        raise ValueError("share-apps-row listener not found — upstream logic changed")
    html = html.replace(old_listener, "")

    old_tour_step = """  {
    target: '#share-apps-row',
    title: 'Share Wordly',
    desc: 'Know someone who\\u2019d find this useful? Share it straight from here, anytime.'
  }
"""
    if old_tour_step not in html:
        raise ValueError("share tour step not found — upstream tour changed")
    html = html.replace(old_tour_step, "")

    old_guard = "  if(e.target.closest('.share-apps-row')) return; // handled by its own listener below\n"
    if old_guard not in html:
        raise ValueError("share-apps-row click guard not found — upstream logic changed")
    html = html.replace(old_guard, "")

    html = _remove_block(html, "  .share-app-btn:hover{", "  .toast{")
    html = _remove_block(html, "  .bottom-bar{", "  @media (max-width: 520px){")

    return html


def patch_white_background(html: str) -> str:
    """Flatten every surface to plain white instead of the warm off-white /
    cream tones and decorative orange-green gradient blobs the main page
    used behind its content."""
    old_root_bg = """    --panel: #FFFFFF;
    --bg: #FDFCFA;
    --bg-tint: #F6F4EF;"""
    new_root_bg = """    --panel: #FFFFFF;
    --bg: #FFFFFF;
    --bg-tint: #F7F7F7;"""
    if old_root_bg not in html:
        raise ValueError("main :root background vars not found — upstream styles changed")
    html = html.replace(old_root_bg, new_root_bg)

    old_body_bg = """  body{
    background:
      radial-gradient(circle at top left, rgba(255,153,51,0.13) 0%, rgba(255,153,51,0.13) 14%, transparent 42%),
      radial-gradient(circle at bottom right, rgba(19,136,8,0.13) 0%, rgba(19,136,8,0.13) 14%, transparent 42%),
      var(--bg);
    background-attachment: fixed;
    background-size: cover;
    color: var(--ink);"""
    new_body_bg = """  body{
    background: var(--bg);
    color: var(--ink);"""
    if old_body_bg not in html:
        raise ValueError("main body background not found — upstream styles changed")
    html = html.replace(old_body_bg, new_body_bg)

    return html


def patch_remove_chakra_watermark(html: str) -> str:
    """Drop the large faint chakra-wheel SVG watermarked behind the main
    page's content."""
    html = _remove_block(html, "  .chakra-watermark{", "\n\n  /* ---------- onboarding tour ---------- */")
    html = _remove_block(html, '<svg class="chakra-watermark"', "</svg>\n\n<header>")
    return html


def patch_examples_only_when_to_english(html: str) -> str:
    """The "Usage in English" example sentences used to render unconditionally
    for every result, even when translating *from* English into another
    language -- where an English example is redundant, since the input side
    is already English. Show it only when the "To" language is English
    itself (translating *into* English), matching the same toLang === 'en'
    condition already used to decide word-head vs. lang-grid visibility."""
    old = """  document.getElementById('word-head').style.display = toLang === 'en' ? 'flex' : 'none';
  document.getElementById('lang-grid').style.display = toLang === 'en' ? 'none' : 'grid';
  document.getElementById('r-examples').innerHTML = entry.ex.map(e => `<div class="example-line">${e}</div>`).join('');"""
    new = """  document.getElementById('word-head').style.display = toLang === 'en' ? 'flex' : 'none';
  document.getElementById('lang-grid').style.display = toLang === 'en' ? 'none' : 'grid';
  document.querySelector('.examples').style.display = toLang === 'en' ? '' : 'none';
  document.getElementById('r-examples').innerHTML = entry.ex.map(e => `<div class="example-line">${e}</div>`).join('');"""
    if old not in html:
        raise ValueError("result-card toLang visibility block not found — upstream logic changed")
    html = html.replace(old, new)
    return html


def patch_dark_mode(html: str) -> str:
    """Add a light-grey dark mode: a header toggle button that flips
    :root[data-theme] between light and dark, persisted in localStorage, and
    synced into the SpeakEasy iframe (the only embedded tool left) over
    postMessage since it's a separate document with its own CSSOM."""
    old_root_end = """    --select-blue-bg: #E7EEFD;
    --shadow: 0 16px 40px -20px rgba(33,36,46,0.18);
  }"""
    new_root_end = """    --select-blue-bg: #E7EEFD;
    --shadow: 0 16px 40px -20px rgba(33,36,46,0.18);
    --glass-bg: rgba(255,255,255,0.55);
  }
  :root[data-theme="dark"]{
    --ink: #1F2024;
    --sub: #55565C;
    --line: #B7B7BB;
    --panel: #E8E8EA;
    --bg: #D9D9DC;
    --bg-tint: #CFCFD2;
    --shadow: 0 16px 40px -20px rgba(0,0,0,0.35);
    --glass-bg: rgba(232,232,234,0.75);
  }"""
    if old_root_end not in html:
        raise ValueError("main :root end not found — upstream styles changed")
    html = html.replace(old_root_end, new_root_end)

    old_glass_bg = """    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    border: 1.5px solid rgba(229,226,218,0.8);
    color: var(--ink);
    border-radius: 50%;
    cursor: pointer;
    box-shadow: var(--shadow);
    z-index: 50;
  }
  .add-home-btn.show{ display:flex; }
  .add-home-btn:hover{ border-color: var(--select-blue); color: var(--select-blue); }
  .help-btn{
    position: fixed;
    top: 16px;
    right: 16px;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    border: 1.5px solid rgba(229,226,218,0.8);
    color: var(--sub);
    font-family: 'Fraunces', serif;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    box-shadow: var(--shadow);
    z-index: 50;
  }
  .help-btn:hover{ border-color: var(--select-blue); color: var(--select-blue); }"""
    new_glass_bg = """    background: var(--glass-bg);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    border: 1.5px solid rgba(229,226,218,0.8);
    color: var(--ink);
    border-radius: 50%;
    cursor: pointer;
    box-shadow: var(--shadow);
    z-index: 50;
  }
  .add-home-btn.show{ display:flex; }
  .add-home-btn:hover{ border-color: var(--select-blue); color: var(--select-blue); }
  .help-btn{
    position: fixed;
    top: 16px;
    right: 16px;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: var(--glass-bg);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    border: 1.5px solid rgba(229,226,218,0.8);
    color: var(--sub);
    font-family: 'Fraunces', serif;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    box-shadow: var(--shadow);
    z-index: 50;
  }
  .help-btn:hover{ border-color: var(--select-blue); color: var(--select-blue); }
  .theme-toggle-btn{
    position: fixed;
    top: 16px;
    left: 16px;
    width: 34px;
    height: 34px;
    display:flex;
    align-items:center;
    justify-content:center;
    border-radius: 50%;
    background: var(--glass-bg);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    border: 1.5px solid rgba(229,226,218,0.8);
    color: var(--sub);
    cursor: pointer;
    box-shadow: var(--shadow);
    z-index: 50;
  }
  .theme-toggle-btn:hover{ border-color: var(--select-blue); color: var(--select-blue); }
  .theme-icon-moon{ display:none; }
  :root[data-theme="dark"] .theme-icon-sun{ display:none; }
  :root[data-theme="dark"] .theme-icon-moon{ display:block; }"""
    if old_glass_bg not in html:
        raise ValueError("add-home-btn/help-btn CSS not found — upstream styles changed")
    html = html.replace(old_glass_bg, new_glass_bg)

    old_help_btn = """  <button class="help-btn" id="help-btn" title="Show a quick tour">?</button>"""
    new_help_btn = """  <button class="help-btn" id="help-btn" title="Show a quick tour">?</button>
  <button class="theme-toggle-btn" id="theme-toggle-btn" title="Toggle dark mode" aria-label="Toggle dark mode">
    <svg class="theme-icon-sun" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
    <svg class="theme-icon-moon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
  </button>"""
    if old_help_btn not in html:
        raise ValueError("help-btn markup not found — upstream markup changed")
    html = html.replace(old_help_btn, new_help_btn)

    old_open_speakeasy = """function openSpeakEasy(){
  if(!speakeasyBlobUrl){
    const src = document.getElementById('speakeasy-src').innerHTML;
    const blob = new Blob([src], { type: 'text/html' });
    speakeasyBlobUrl = URL.createObjectURL(blob);
  }
  if(!speakeasyFrame.src) speakeasyFrame.src = speakeasyBlobUrl;
  speakeasyOverlay.classList.add('show');
}"""
    new_open_speakeasy = """function currentTheme(){ return document.documentElement.getAttribute('data-theme') || 'light'; }
function sendThemeToSpeakEasy(){
  if(!speakeasyFrame.contentWindow) return;
  try{ speakeasyFrame.contentWindow.postMessage({ type: 'wordly:theme', theme: currentTheme() }, '*'); }catch(err){}
}
function openSpeakEasy(){
  if(!speakeasyBlobUrl){
    const src = document.getElementById('speakeasy-src').innerHTML;
    const blob = new Blob([src], { type: 'text/html' });
    speakeasyBlobUrl = URL.createObjectURL(blob);
  }
  if(!speakeasyFrame.src){
    speakeasyFrame.addEventListener('load', sendThemeToSpeakEasy);
    speakeasyFrame.src = speakeasyBlobUrl;
  }
  sendThemeToSpeakEasy();
  speakeasyOverlay.classList.add('show');
}"""
    if old_open_speakeasy not in html:
        raise ValueError("openSpeakEasy() not found — upstream logic changed")
    html = html.replace(old_open_speakeasy, new_open_speakeasy)

    # The theme-toggle init IIFE must run only after speakeasyFrame/speakeasyOverlay
    # (const, not hoisted) are actually declared -- anchoring it any earlier
    # (e.g. right after help-btn's wiring, which runs first) hits their
    # temporal dead zone the moment applyTheme() -> sendThemeToSpeakEasy()
    # touches speakeasyFrame, throwing and aborting the rest of the script
    # (silently breaking the mic button and everything below it).
    old_speakeasy_wiring = """micBtn.addEventListener('click', openSpeakEasy);
document.getElementById('speakeasy-close-btn').addEventListener('click', closeSpeakEasy);
speakeasyOverlay.addEventListener('click', (e)=>{
  if(e.target === speakeasyOverlay) closeSpeakEasy();
});"""
    new_speakeasy_wiring = """micBtn.addEventListener('click', openSpeakEasy);
document.getElementById('speakeasy-close-btn').addEventListener('click', closeSpeakEasy);
speakeasyOverlay.addEventListener('click', (e)=>{
  if(e.target === speakeasyOverlay) closeSpeakEasy();
});

// ---------------- dark mode ----------------
(function(){
  var THEME_KEY = 'wordly_theme';
  function applyTheme(theme){
    document.documentElement.setAttribute('data-theme', theme);
    sendThemeToSpeakEasy();
  }
  var storedTheme = null;
  try{ storedTheme = localStorage.getItem(THEME_KEY); }catch(e){}
  applyTheme(storedTheme === 'dark' ? 'dark' : 'light');
  document.getElementById('theme-toggle-btn').addEventListener('click', function(){
    var next = currentTheme() === 'dark' ? 'light' : 'dark';
    try{ localStorage.setItem(THEME_KEY, next); }catch(e){}
    applyTheme(next);
  });
})();"""
    if old_speakeasy_wiring not in html:
        raise ValueError("speakeasy wiring block not found — upstream logic changed")
    html = html.replace(old_speakeasy_wiring, new_speakeasy_wiring)

    return html


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
    if template_id == "speakeasy-src":
        fragment = inject_after_head(fragment)
        fragment = patch_speakeasy_share_save(fragment)
        fragment = patch_speakeasy_credit_caption(fragment)
        fragment = patch_speakeasy_theme(fragment)
    return before + fragment + after


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    src_dir = Path(sys.argv[1])
    src_html = (src_dir / "index.html").read_text(encoding="utf-8")

    for template_id in ("speakeasy-src",):
        src_html = patch_template(src_html, template_id)

    src_html = patch_remove_scanline_tool(src_html)
    src_html = patch_remove_writing_tool(src_html)
    src_html = patch_remove_footer_and_social(src_html)
    src_html = patch_white_background(src_html)
    src_html = patch_remove_chakra_watermark(src_html)
    src_html = patch_examples_only_when_to_english(src_html)
    src_html = patch_dark_mode(src_html)

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
