"""Narrated demo of Broker Order Desk: real packaged jar (Java 25), real HTTP calls, real clicks, synthetic data, local Kokoro voice.
Run: cd demo && uv run python make_demo.py      (needs JDK 25, ffmpeg, Playwright Chromium, Kokoro model files)
Dry run without voice or video: DEMO_FAST=1 uv run python make_demo.py"""
import os, pathlib, shutil, subprocess, tempfile, time
import httpx, numpy as np, soundfile as sf
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "demo/out"
PORT = 8796
BASE = f"http://127.0.0.1:{PORT}"
NAME = "broker-order-desk-demo"
VOICE, SPEED = os.environ.get("KOKORO_VOICE", "af_heart"), float(os.environ.get("KOKORO_SPEED", "1.0"))
MODELS = os.path.expanduser(os.environ.get("KOKORO_MODELS", "~/work/throughline-tools/models"))
W, H = 1440, 900
FAST = bool(int(os.environ.get("DEMO_FAST", "0")))
JAR = ROOT / "target/broker-order-desk-0.1.0.jar"
JAVA_HOME = os.environ.get("JAVA_HOME", os.path.expanduser("~/.sdkman/candidates/java/25-tem"))

CURSOR_JS = """(() => { const add = () => { if (document.getElementById('demo-cursor') || !document.documentElement) return;
  const d = document.createElement('div'); d.id = 'demo-cursor';
  d.style.cssText = 'position:fixed;z-index:99999;width:30px;height:30px;border-radius:50%;border:4px solid #e11d48;background:rgba(225,29,72,.22);pointer-events:none;left:-60px;top:-60px;transition:left .4s,top .4s';
  document.documentElement.appendChild(d); };
  document.addEventListener('DOMContentLoaded', add); add(); })();"""

LINES = {
    "intro": "This is Broker Order Desk: the backend of a brokerage. Java twenty five, Spring Boot three point five, Spring Security, J P A, and a single writer matching engine. The data is synthetic: invented symbols, invented investors.",
    "sell": "Investor two places a sell order for twenty thousand shares at twelve riyals five hundred. Pre trade risk checks the price band and reserves the shares, so they cannot be sold twice. The order rests in the book, and the depth panel shows it.",
    "buy": "Investor one buys the same size. The engine matches by price and time priority, and the trade settles in one database transaction. The account is margin financed: the cash balance is now negative, which is the loan, and equity is forty percent of the position, above the thirty five percent maintenance level.",
    "sell2": "Now the price moves. Investor two offers five thousand shares at eleven riyals three hundred, still inside the ten percent daily band.",
    "buy2": "Investor one buys a thousand at that price. The last price drops, the position is marked to market, and equity falls under the maintenance level. The account is now in margin call, and the screen says so.",
    "calls": "A risk officer signs in. Only the risk role can list margin calls, and investor one is on the list.",
    "forbidden": "Security is enforced on every endpoint. Investor one tries to read investor two's account, and is refused with a four zero three.",
    "band": "Pre trade risk, case one: a price far outside the daily band is rejected before it can reach the book.",
    "power": "Case two: an order bigger than cash plus the margin line is rejected for insufficient buying power.",
    "shares": "Case three: selling shares you do not own is rejected. No short selling.",
    "replay": "A client retries its first order after a timeout. The client order id makes it idempotent: the same order comes back, and the investor is not charged twice.",
    "cancel": "Investor two cancels the resting sell. The engine removes it from the book, and the reserved shares are released.",
    "quote": "Finally the liquidity provider role: one call places a bid and an offer together, through the same risk, matching and settlement path as any other order.",
    "outro": "Twenty six automated tests stand behind this, including a stress test with many concurrent orders that proves cash and shares are conserved, and a test where the database fails mid settlement and the desk recovers. There is a Dockerfile, Kubernetes manifests, a GitHub Actions pipeline, and the code is in the Git repository.",
}


def nap(x): time.sleep(x * (0.15 if FAST else 1))


def synth(texts, folder):
    from kokoro_onnx import Kokoro
    k = Kokoro(os.path.join(MODELS, "kokoro-v1.0.onnx"), os.path.join(MODELS, "voices-v1.0.bin"))
    clips = {}
    for cid, text in texts.items():
        audio, sr = k.create(text, voice=VOICE, speed=SPEED, lang="en-us")
        idx = np.where(np.abs(audio) > 0.01)[0]
        if len(idx): audio = audio[max(idx[0] - 800, 0): idx[-1] + 2400]
        p = str(folder / f"{cid}.wav"); sf.write(p, audio, sr)
        clips[cid] = (p, len(audio) / sr, sr); print("voice", cid, round(len(audio) / sr, 1), flush=True)
    return clips


class Rec:
    def __init__(self): self.timeline, self.t0 = [], None
    def now(self): return time.monotonic() - self.t0


def say(rec, clips, cid):
    s, d = rec.now(), clips[cid][1]; rec.timeline.append((cid, s, s + d, LINES[cid])); return s + d


def until(rec, t_end, pad=0.6):
    d = t_end + pad - rec.now()
    if d > 0 and not FAST: time.sleep(d)


def tap(pg, sel, pause=0.5):
    loc = pg.locator(sel).first; loc.scroll_into_view_if_needed(); b = loc.bounding_box()
    pg.evaluate("([x,y]) => { const c=document.getElementById('demo-cursor'); if(c){c.style.left=(x-15)+'px'; c.style.top=(y-15)+'px';} }", [b["x"] + b["width"] / 2, b["y"] + b["height"] / 2])
    nap(0.5); loc.click(); nap(pause)


def expect(pg, sel, text, timeout=15000):
    """The narration must match the screen: fail loudly if it does not."""
    try:
        pg.wait_for_function("([s,t]) => (document.querySelector(s)||{textContent:''}).textContent.includes(t)", arg=[sel, text], timeout=timeout)
    except Exception:
        raise AssertionError(f"narration expects '{text}' in {sel}, screen has: {pg.locator(sel).inner_text()[:300]}")


def story(pg, rec, clips):
    S = lambda c: say(rec, clips, c)
    pg.goto(BASE + "/"); pg.wait_for_selector("#sc-sell"); nap(0.6)
    e = S("intro"); until(rec, e)

    e = S("sell"); tap(pg, "#sc-sell"); expect(pg, "#response", '"status": "NEW"'); expect(pg, "#depth", "20,000"); until(rec, e)
    e = S("buy"); tap(pg, "#sc-buy"); expect(pg, "#response", '"status": "FILLED"'); expect(pg, "#st-margin", "40.0%"); until(rec, e)
    e = S("sell2"); tap(pg, "#sc-sell2"); expect(pg, "#response", '"status": "NEW"'); until(rec, e)
    e = S("buy2"); tap(pg, "#sc-buy2"); expect(pg, "#response", '"status": "FILLED"'); expect(pg, "#st-margin", "MARGIN CALL"); until(rec, e)
    e = S("calls"); tap(pg, "#sc-calls"); expect(pg, "#response", "trader1"); expect(pg, "#pane", "trader1"); until(rec, e)
    e = S("forbidden"); tap(pg, "#sc-forbidden"); expect(pg, "#response", "403"); until(rec, e)
    e = S("band"); tap(pg, "#sc-band"); expect(pg, "#response", "PRICE_OUTSIDE_BAND"); until(rec, e)
    e = S("power"); tap(pg, "#sc-power"); expect(pg, "#response", "INSUFFICIENT_BUYING_POWER"); until(rec, e)
    e = S("shares"); tap(pg, "#sc-shares"); expect(pg, "#response", "INSUFFICIENT_SHARES"); until(rec, e)
    e = S("replay"); tap(pg, "#sc-replay"); expect(pg, "#response", "b1-"); expect(pg, "#response", '"status": "FILLED"'); until(rec, e)
    e = S("cancel"); tap(pg, "#sc-cancel"); expect(pg, "#response", "CANCELLED"); until(rec, e)
    e = S("quote"); tap(pg, "#sc-quote"); expect(pg, "#response", '"bid"'); expect(pg, "#depth", "12.400"); until(rec, e)
    e = S("outro"); until(rec, e, 1.2)


def ts(t, sep): return f"{int(t // 3600):02d}:{int(t % 3600 // 60):02d}:{t % 60:06.3f}".replace(".", sep)


def captions(rec):
    vtt, srt, n = ["WEBVTT", ""], [], 0
    for cid, start, end, text in rec.timeline:
        rows, cur = [], ""
        for w in text.split():
            if len(cur) + len(w) + 1 > 64: rows.append(cur); cur = w
            else: cur = (cur + " " + w).strip()
        rows.append(cur)
        for c in range(0, len(rows), 2):
            a, b = start + (end - start) * c / len(rows), start + (end - start) * min(c + 2, len(rows)) / len(rows)
            n += 1; vtt += [f"{ts(a, '.')} --> {ts(b, '.')}", *rows[c:c + 2], ""]; srt += [str(n), f"{ts(a, ',')} --> {ts(b, ',')}", *rows[c:c + 2], ""]
    (OUT / f"{NAME}.vtt").write_text("\n".join(vtt)); (OUT / f"{NAME}.srt").write_text("\n".join(srt))


def finish(rec, clips, work, video):
    total = max(e for _, _, e, _ in rec.timeline) + 2.5; sr = 24000
    mix = np.zeros(int(total * sr) + sr, dtype="float32")
    for cid, start, end, _ in rec.timeline:
        a, s = sf.read(clips[cid][0], dtype="float32"); i = int(start * sr); mix[i:i + len(a)] += a
    sf.write(str(work / "mix.wav"), mix, sr); captions(rec)
    mp4 = OUT / f"{NAME}.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", video, "-i", str(work / "mix.wav"), "-i", str(OUT / f"{NAME}.srt"),
                    "-map", "0:v", "-map", "1:a", "-map", "2:s", "-vf", f"scale={W * 3 // 2}:{H * 3 // 2}:flags=lanczos",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "22", "-preset", "slow", "-r", "25", "-c:a", "aac", "-b:a", "128k",
                    "-c:s", "mov_text", "-metadata:s:s:0", "language=eng", "-t", f"{total:.2f}", "-movflags", "+faststart", str(mp4)], check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "50", "-i", str(mp4), "-frames:v", "1", str(OUT / f"{NAME}.png")], check=True)
    print("video", mp4, round(total), "s")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    if not JAR.exists(): subprocess.run(["./mvnw", "-q", "-DskipTests", "package"], cwd=ROOT, check=True, env=dict(os.environ, JAVA_HOME=JAVA_HOME))
    work = pathlib.Path(tempfile.mkdtemp(prefix="cardhub-demo-"))
    clips = {k: (None, 4.0, 24000) for k in LINES} if FAST else synth(LINES, work)
    env = dict(os.environ, SPRING_PROFILES_ACTIVE="demo", CLEARING_INBOX=str(work / "inbox"))
    srv = subprocess.Popen([f"{JAVA_HOME}/bin/java", "-XX:+UseCompactObjectHeaders", "-jar", str(JAR), f"--server.port={PORT}"], env=env, cwd=work, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    rec = Rec()
    try:
        for _ in range(120):
            try:
                if httpx.get(BASE + "/actuator/health", timeout=1).status_code == 200: break
            except Exception: time.sleep(0.5)
        with sync_playwright() as p:
            b = p.chromium.launch()
            ctx = b.new_context(viewport={"width": W, "height": H}, record_video_dir=str(work / "video"), record_video_size={"width": W, "height": H})
            ctx.add_init_script(CURSOR_JS); pg = ctx.new_page(); rec.t0 = time.monotonic()
            try: story(pg, rec, clips)
            except Exception:
                (ROOT / "demo/.work").mkdir(exist_ok=True); pg.screenshot(path=str(ROOT / "demo/.work/failure.png"), full_page=True); raise
            video = pg.video.path(); ctx.close(); b.close()
        print("dry run OK") if FAST else finish(rec, clips, work, video)
    finally:
        srv.terminate(); shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__": main()
