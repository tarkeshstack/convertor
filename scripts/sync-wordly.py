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
    new_share_fn = """  // Small attribution appended to anything copied or shared out of
  // SpeakEasy — the text credit line, and (since apps generally can't show
  // a caption next to a shared audio file) also what ends up on the
  // clipboard alongside the voice note. The URL is plain text rather than
  // an HTML link since that's all a share/clipboard payload can carry, but
  // messaging apps auto-linkify it into a clickable link on their end.
  var WORDLY_CREDIT = "\\n\\n— translated with Wordly · https://github.com/tarkeshstack/wordly";

  function shareTextOnly(text) {
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
    var combined = translatedText + "\\n" + originalText + WORDLY_CREDIT;
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

    old_listeners = """  document.getElementById("shareBtn").addEventListener("click", function () {
    if (state.result) shareResult(state.result.translatedText);
  });
  document.getElementById("saveBtn").addEventListener("click", function () {
    if (state.result) saveAudio(state.result.translatedText, state.result.targetCode);
  });"""
    new_listeners = """  document.getElementById("shareBtn").addEventListener("click", function () {
    if (state.result) shareResult(state.result.originalText, state.result.translatedText, state.result.targetCode);
  });"""
    if old_listeners not in fragment:
        raise ValueError("speakeasy shareBtn/saveBtn listeners not found — upstream logic changed")
    fragment = fragment.replace(old_listeners, new_listeners)

    old_copy_listener = """  document.getElementById("copyBtn").addEventListener("click", function () {
    if (state.result) copyText(state.result.translatedText);
  });"""
    new_copy_listener = """  document.getElementById("copyBtn").addEventListener("click", function () {
    if (state.result) copyText(state.result.translatedText + WORDLY_CREDIT);
  });"""
    if old_copy_listener not in fragment:
        raise ValueError("speakeasy copyBtn listener not found — upstream logic changed")
    fragment = fragment.replace(old_copy_listener, new_copy_listener)

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
    fragment = inject_after_head(fragment)
    if template_id == "speakeasy-src":
        fragment = patch_speakeasy_share_save(fragment)
    return before + fragment + after


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    src_dir = Path(sys.argv[1])
    src_html = (src_dir / "index.html").read_text(encoding="utf-8")

    for template_id in ("speakeasy-src", "writing-src"):
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
