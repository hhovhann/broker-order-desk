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
    "intro": "This is Broker Order Desk: an order desk for a stock brokerage, with a React dashboard on a Spring Boot backend, written in Java twenty five. There are four roles: investor, liquidity provider, risk officer, and administrator. Every name, symbol and price is synthetic.",
    "prices": "Investor two signs in and lands on the price board: best offer and bid from the live order book, last price, trades, average, volume, value, high, low, a trend line, and the change. Ten symbols carry synthetic opening trades.",
    "sell": "Investor two sells twenty thousand shares at twelve riyals and five hundred mills. Pre trade risk reserves those shares, so they cannot be sold twice, and the order rests in the book. The depth panel shows it.",
    "buy": "Investor one buys the same size. The engine matches by price and time, and the trade settles in one database transaction. At the top, the balance turned negative: that is the margin loan. The margin check is healthy at forty percent.",
    "sell2": "Now the price moves. Investor two offers five thousand shares at eleven riyals three hundred, still inside the ten percent daily band.",
    "buy2": "Investor one buys a thousand at that price. The last price drops, the position is marked to market, and equity falls below the maintenance level. The flag at the top turns red: margin call.",
    "margin": "The margin screen shows it on a gauge: the needle is inside the red zone, under the maintenance line.",
    "risk": "A risk officer signs in. Only that role sees margin calls across accounts, and investor one is on the list.",
    "reject": "Back as investor one, three pre trade checks. The ticket warns before sending, and the server still decides. A price outside the daily band is rejected. An order bigger than the buying power is rejected. Selling shares you do not own is rejected.",
    "cancel": "Investor two cancels the order that is still resting. The engine removes it from the book and the reserved shares are released.",
    "quote": "The liquidity provider posts a two sided quote, a bid and an offer together. It goes through the same risk, matching and settlement path as any other order.",
    "audit": "Finally the administrator reviews the audit trail: every accepted order, fill, rejection and cancel, with who did it and a correlation id.",
    "outro": "Twenty seven automated tests stand behind this, including a stress test with many concurrent orders that proves cash and shares are conserved, and a test where the database fails in the middle of settlement and the desk recovers. There is a Dockerfile, Kubernetes manifests, a CI pipeline, specs and a changelog for every feature, and the code is in the Git repository.",
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


def login_as(pg, user):
    if pg.locator('[data-testid=signout]').count():
        tap(pg, '[data-testid=signout]', 0.3)
    pg.wait_for_selector(f'[data-testid=demo-{user}]')
    tap(pg, f'[data-testid=demo-{user}]', 0.3)
    pg.wait_for_selector('[data-testid=who]')
    expect(pg, '[data-testid=who]', user)


def open_symbol_in_trading(pg):
    tap(pg, '[data-testid=nav-trading]', 0.3)
    pg.wait_for_selector('[data-testid=ticket]')


def order(pg, side, qty, price):
    tap(pg, f'[data-testid=side-{side}]', 0.2)
    for sel, val in (('[data-testid=qty]', str(qty)), ('[data-testid=price]', price)):
        loc = pg.locator(sel); loc.scroll_into_view_if_needed(); loc.fill(val); nap(0.3)
    tap(pg, '[data-testid=submit-order]', 0.3)


def story(pg, rec, clips):
    S = lambda c: say(rec, clips, c)
    pg.goto(BASE + "/"); pg.wait_for_selector('[data-testid=login-user]'); nap(0.6)
    e = S("intro"); until(rec, e)

    login_as(pg, "trader2")
    e = S("prices"); pg.wait_for_selector('[data-testid=row-FNBK]'); expect(pg, '[data-testid=board]', 'Sample Bank'); until(rec, e)
    e = S("sell"); open_symbol_in_trading(pg); order(pg, 'sell', 20000, '12.500')
    expect(pg, '.toasts', 'resting in the book'); expect(pg, '[data-testid=depth]', '20,000'); until(rec, e)

    login_as(pg, "trader1")
    e = S("buy"); open_symbol_in_trading(pg); order(pg, 'buy', 20000, '12.500')
    expect(pg, '.toasts', 'filled at 12.500'); expect(pg, '[data-testid=margin-flag]', '40.00%'); expect(pg, '[data-testid=strip-balance]', '-150,000.00'); until(rec, e)

    login_as(pg, "trader2")
    e = S("sell2"); open_symbol_in_trading(pg); order(pg, 'sell', 5000, '11.300'); expect(pg, '.toasts', 'resting in the book'); until(rec, e)

    login_as(pg, "trader1")
    e = S("buy2"); open_symbol_in_trading(pg); order(pg, 'buy', 1000, '11.300')
    expect(pg, '.toasts', 'filled at 11.300'); expect(pg, '[data-testid=margin-flag]', 'MARGIN CALL'); until(rec, e)
    e = S("margin"); tap(pg, '[data-testid=nav-margin]', 0.4); expect(pg, '[data-testid=margin-call]', 'MARGIN CALL'); until(rec, e)

    login_as(pg, "risk1")
    e = S("risk"); tap(pg, '[data-testid=nav-risk]', 0.4); expect(pg, '[data-testid=calls]', 'trader1'); until(rec, e)

    login_as(pg, "trader1")
    e = S("reject"); open_symbol_in_trading(pg)
    order(pg, 'buy', 10, '20.000'); expect(pg, '.hints', 'outside the daily band'); expect(pg, '.toasts', 'daily band')
    nap(1.0); order(pg, 'buy', 30000, '12.500'); expect(pg, '.toasts', 'buying power')
    nap(1.0); order(pg, 'sell', 50000, '12.500'); expect(pg, '.toasts', 'Not enough shares'); until(rec, e)

    login_as(pg, "trader2")
    e = S("cancel"); open_symbol_in_trading(pg)
    pg.wait_for_selector('[data-testid^=cancel-]'); tap(pg, '[data-testid^=cancel-]', 0.3); expect(pg, '.toasts', 'cancelled'); until(rec, e)

    login_as(pg, "lp1")
    e = S("quote"); tap(pg, '[data-testid=nav-liquidity]', 0.4)
    for sel, val in (('[data-testid=q-qty]', '1000'), ('[data-testid=q-bid]', '12.400'), ('[data-testid=q-ask]', '12.600')):
        pg.locator(sel).fill(val); nap(0.3)
    tap(pg, '[data-testid=q-submit]', 0.3); expect(pg, '.toasts', 'Quoted SBNK')
    tap(pg, '[data-testid=nav-trading]', 0.3); expect(pg, '[data-testid=depth]', '12.400'); expect(pg, '[data-testid=depth]', '12.600'); until(rec, e)

    login_as(pg, "admin")
    e = S("audit"); tap(pg, '[data-testid=nav-audit]', 0.4)
    for word in ('FILL', 'ORDER_REJECTED', 'CANCEL', 'ORDER_ACCEPTED'):
        expect(pg, '[data-testid=audit]', word)
    until(rec, e)
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
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "60", "-i", str(mp4), "-frames:v", "1", str(OUT / f"{NAME}.png")], check=True)
    print("video", mp4, round(total), "s")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    if not (ROOT / "src/main/resources/static/index.html").exists():
        subprocess.run(["npm", "ci"], cwd=ROOT / "web", check=True); subprocess.run(["npm", "run", "build"], cwd=ROOT / "web", check=True)
    if not JAR.exists(): subprocess.run(["./mvnw", "-q", "-DskipTests", "package"], cwd=ROOT, check=True, env=dict(os.environ, JAVA_HOME=JAVA_HOME))
    work = pathlib.Path(tempfile.mkdtemp(prefix="brokerdesk-demo-"))
    clips = {k: (None, 4.0, 24000) for k in LINES} if FAST else synth(LINES, work)
    env = dict(os.environ, SPRING_PROFILES_ACTIVE="demo")
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
