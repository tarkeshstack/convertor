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
   ============================================================ */
(function(){
  if(!window.Capacitor) return;

  function loadPlugin(src){
    return new Promise(function(resolve){
      const s = document.createElement('script');
      s.src = src;
      s.onload = resolve;
      s.onerror = resolve;
      document.body.appendChild(s);
    });
  }

  Promise.all([
    loadPlugin('capacitor-plugins/app.js'),
    loadPlugin('capacitor-plugins/status-bar.js'),
    loadPlugin('capacitor-plugins/text-to-speech.js'),
    loadPlugin('capacitor-plugins/speech-recognition.js')
  ]).then(function(){
    const CapApp = window.capacitorApp && window.capacitorApp.App;
    const CapStatusBar = window.capacitorStatusBar && window.capacitorStatusBar.StatusBar;
    const CapTTS = window.capacitorTextToSpeech && window.capacitorTextToSpeech.TextToSpeech;
    const CapSTT = window.capacitorSpeechRecognition && window.capacitorSpeechRecognition.SpeechRecognition;

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
        const listenerPromise = CapSTT.addListener('partialResults', function(res){
          if(!self._active) return;
          const matches = (res && res.matches) || [];
          if(!matches.length) return;
          const evt = { resultIndex: 0, results: [[{ transcript: matches[0] }]] };
          evt.results[0].isFinal = false;
          if(typeof self.onresult === 'function') self.onresult(evt);
        });
        CapSTT.start({ language: self.lang || 'en-US', partialResults: true, popup: false, maxResults: 1 })
          .then(function(result){
            listenerPromise.then(function(h){ h.remove(); }).catch(function(){});
            if(!self._active) return;
            self._active = false;
            const matches = (result && result.matches) || [];
            if(matches.length){
              const evt = { resultIndex: 0, results: [[{ transcript: matches[0] }]] };
              evt.results[0].isFinal = true;
              if(typeof self.onresult === 'function') self.onresult(evt);
            }
            if(typeof self.onend === 'function') self.onend();
          })
          .catch(function(err){
            listenerPromise.then(function(h){ h.remove(); }).catch(function(){});
            if(!self._active) return;
            self._active = false;
            const msg = (err && err.message) || '';
            const code = /permission|denied/i.test(msg) ? 'not-allowed' : (/network/i.test(msg) ? 'network' : 'no-speech');
            if(typeof self.onerror === 'function') self.onerror({ error: code });
            if(typeof self.onend === 'function') self.onend();
          });
      }).catch(function(){
        self._active = false;
        if(typeof self.onerror === 'function') self.onerror({ error: 'not-allowed' });
        if(typeof self.onend === 'function') self.onend();
      });
    };
    NativeRecognition.prototype.stop = function(){
      this._active = false;
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
      }
    });
  });
})();
</script>
"""


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
