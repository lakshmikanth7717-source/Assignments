"""Render a 300-second captioned walkthrough from actual successful demo HTTP evidence.

Requires Pillow==11.1.0 and imageio-ffmpeg==0.6.0. No API calls or invented results.
"""
import json
from pathlib import Path
import subprocess
import tempfile
import textwrap

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
events = json.loads((ROOT / 'docs/evidence/demo.json').read_text())


def first(predicate):
    return next(event for event in events if predicate(event))


postings = [e for e in events if e['path'] == '/transfers' and e['status'] == 201]
posted, retry = postings[:2]
assert posted['raw'] == retry['raw'] and posted['key'] == retry['key']
detail = first(lambda e: e['method'] == 'GET' and e['path'] == '/transfers/' + str(posted['response']['transactionId']))
held = first(lambda e: e['path'] == '/transfers' and e['status'] == 202)
hold_detail = first(lambda e: e['method'] == 'GET' and e['path'] == '/transfers/' + str(held['response']['transactionId']))
report = first(lambda e: '/admin/reconciliation/runs/' in e['path'] and e['response']['run']['status'] == 'PASSED')
answer = first(lambda e: '/statement/ask' in e['path'] and e['status'] == 200)
rejected = first(lambda e: '/statement/ask' in e['path'] and e['status'] == 422)
conflict = first(lambda e: e['path'] == '/transfers' and e['status'] == 422)


def http(e):
    return f'{e["method"]} {e["path"]}\nHTTP {e["status"]}\n' + json.dumps(e['response'], indent=2)


chapters = [
    ('Start the application', [
        ('One repository, one project directory', 'cd "Payment Grade Wallet Ledger"\ndocker compose up --build -d\n\nAPI: localhost:8081\nPostgreSQL 17 + Java API + separate worker'),
        ('Run every automated test', 'docker compose --profile test run --build --rm tests\n\nReal PostgreSQL; Flyway from empty schemas.\nStampede, idempotency, reconciliation, injection and auth tests.\nNo host Java or external model credentials.'),
        ('Where this recording comes from', 'This captioned walkthrough displays the real HTTP responses\nsaved by scripts/demo.py in docs/evidence/demo.json.\nThe script asserts outcomes before writing evidence.\nDefault Q&A mode uses a deterministic offline grammar.')]),
    ('Create and fund wallets', [
        ('Authenticated caller', 'Demo Basic Auth: admin / user123\nThe server supplies the numeric caller ID.\nThe script creates fresh sender and recipient wallets.\nThis is an assignment identity stub, not production auth.'),
        ('Create a wallet', http(first(lambda e: '/wallets' in e['path'] and e['method'] == 'POST'))),
        ('Fund through balanced ledger entries', http(first(lambda e: e['path'] == '/transfers/topup')))]),
    ('One transfer end to end', [
        ('Send whole paise with a fresh key', 'POST /transfers\nIdempotency-Key: ' + posted['key'] + '\n\n' + json.dumps(posted['request'], indent=2)),
        ('The transfer is posted', http(posted)),
        ('Inspect committed ledger movements', json.dumps(detail['response']['entries'], indent=2))]),
    ('Atomicity and locking', [
        ('Status history belongs to the same transaction', json.dumps(detail['response']['statusHistory'], indent=2)),
        ('Why competing spends cannot overspend', 'BEGIN at READ COMMITTED\nLock every participating wallet by ascending wallet_id.\nCheck the balances returned under those locks.\nWrite header, ledger, cache, history and saved response.\nCOMMIT all changes together.'),
        ('Twenty-client stampede', 'Each client asks to spend 8000 against a 10000 balance.\nTests observe all 20 requests waiting on PostgreSQL locks.\nExactly one succeeds; nineteen get INSUFFICIENT_FUNDS.\nFinal sender: 2000; recipient: 8000.\nLedger and caches must agree.')]),
    ('Retry the same transfer', [
        ('Retry the original operation', 'Reuse the original Idempotency-Key:\n' + retry['key'] + '\n\nSend the same validated request body.'),
        ('The exact response replays', http(retry) + '\n\nAssertion: original bytes == retried bytes.'),
        ('Changed payload is rejected', http(conflict))]),
    ('Trigger one held transfer', [
        ('Deterministic velocity rule', 'Six equal transfers have posted from this fresh sender.\nThe next request sees more than five prior outgoing transfers\nin the ten-minute window and triggers HOLD.\nThere is no network model call in offline mode.'),
        ('The API returns HELD', http(held)),
        ('No money moved', 'Ledger entries for the held transfer:\n' + json.dumps(hold_detail['response']['entries']) + '\n\nThe script compares sender balance before and after HOLD.\nThose balances are equal. Intent is durable, funds unreserved.')]),
    ('Hold semantics and review', [
        ('A retry preserves the original outcome', 'A held transfer has immutable recipient intent and rule reason.\nIts original idempotency response remains HELD even if an\nadministrator later releases it. GET details returns current state.'),
        ('Release requires a fresh funds check', 'POST /admin/transfers/{transactionId}/release\n\nThe service locks the header and all participating wallets.\nInsufficient funds leave it HELD; a valid release posts atomically.\nAI labels cannot approve or release a transfer.'),
        ('Reconciliation is separate from financial repair', 'The worker reports inconsistencies from a consistent snapshot.\nIt does not rewrite posted history or silently fix balances.\nCorrections use audited reversing transactions.')]),
    ('Run reconciliation in the worker', [
        ('Queue an explicit run', http(first(lambda e: e['path'] == '/admin/reconciliation/run'))),
        ('A separate process consumes the request', 'API only enqueues the durable run.\nWorker polls each second and holds the shared job-row lock.\nConcurrent workers skip a locked run.\nChecks use one REPEATABLE READ financial snapshot.'),
        ('Read the durable report', http(report))]),
    ('Ask one statement question', [
        ('Question text is untrusted input', json.dumps(answer['request'], indent=2) + '\n\nOffline mode accepts this exact count grammar.\nLive mode supports four validated plan types.'),
        ('Answer from caller-scoped PostgreSQL', http(answer)),
        ('A held transfer is excluded', 'Only POSTED transfer IDs contribute to the answer.\nThe held ID ' + str(held['response']['transactionId']) + ' is absent from transferIds.\nThe SQL actor comes from authentication, never from model output.\nModel-produced SQL is never executed.')]),
    ('Verify injection resistance and scale', [
        ('Extra instructions are rejected', http(rejected)),
        ('Load the required seed scale', 'docker compose --profile tools run --rm seed\n\n1000 users, 2000 wallets, 100000 posted transfers\nplus 2000 balanced opening top-ups.\nRun on fresh financial data; repeat adds no rows.'),
        ('Review evidence and trade-offs', 'docs/schema.md: nouns, cardinalities, ER diagram\ndocs/isolation.md: two sessions, actual blocking and output\ndocs/evidence: executed HTTP and SQL evidence\nREADME: design decisions, trade-offs and next steps\nCI repeats tests, seed, HTTP demo and isolation lab.')]),
]

font_paths = ['/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',
              '/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf',
              'C:/Windows/Fonts/consola.ttf']
font_path = next((p for p in font_paths if Path(p).exists()), None)
font = ImageFont.truetype(font_path, 21) if font_path else ImageFont.load_default(size=21)
title_font = ImageFont.truetype(font_path, 32) if font_path else ImageFont.load_default(size=32)

with tempfile.TemporaryDirectory() as temp:
    folder = Path(temp)
    slides = []
    for index, (chapter, pages) in enumerate(chapters):
        for page, (title, body) in enumerate(pages):
            frame = Image.new('RGB', (1280, 720), '#0b1220')
            draw = ImageDraw.Draw(frame)
            draw.text((42, 30), 'PAYMENT GRADE WALLET LEDGER', font=font, fill='#5eead4')
            draw.text((42, 75), f'{index + 1:02}. {chapter}', font=title_font, fill='white')
            draw.text((42, 126), title, font=font, fill='#93c5fd')
            wrapped = []
            for line in body.splitlines():
                wrapped.extend(textwrap.wrap(line, width=89, subsequent_indent='  ') or [''])
            # Long real responses get a readable structured subset, never a fabricated value.
            if len(wrapped) > 20:
                wrapped = wrapped[:19] + ['... full response: docs/evidence/demo.json']
            draw.multiline_text((42, 175), '\n'.join(wrapped), font=font, fill='#e2e8f0', spacing=3)
            elapsed = index * 30 + page * 10
            draw.text((42, 674), f'{elapsed//60:02}:{elapsed%60:02} / 05:00   |   Captioned walkthrough of executed API evidence', font=font, fill='#94a3b8')
            draw.rectangle((0, 710, int(1280 * (elapsed + 10) / 300), 719), fill='#2dd4bf')
            target = folder / f'{len(slides):02}.png'
            frame.save(target)
            slides.append(target)
    manifest = folder / 'slides.txt'
    manifest.write_text(''.join(f"file '{p.as_posix()}'\nduration 10\n" for p in slides)
                        + f"file '{slides[-1].as_posix()}'\n")
    output = ROOT / 'docs/demo.mp4'
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-f', 'concat', '-safe', '0',
                    '-i', str(manifest), '-vf', 'fps=24,format=yuv420p', '-t', '300',
                    '-c:v', 'libx264', '-preset', 'fast', '-crf', '24', '-movflags', '+faststart',
                    str(output)], check=True)
    print(f'Created {output}: 300 seconds, 1280x720, captioned evidence walkthrough.')
