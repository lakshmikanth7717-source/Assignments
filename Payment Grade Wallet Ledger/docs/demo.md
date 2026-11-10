# Five-minute demo

[Download or play demo.mp4](demo.mp4) (300 seconds, 1280 x 720, captioned, no audio).
This is a rendered walkthrough of real captured HTTP responses, rather than a
screen recording or a model-generated simulation. The source responses and
assertions are available for independent review.

| Time | Demonstration |
| --- | --- |
| 00:00 | Compose startup, test command, evidence provenance |
| 00:30 | Create and fund wallets through the API |
| 01:00 | One transfer end to end, including committed signed entries |
| 01:30 | Atomic status history and ordered wallet locking |
| 02:00 | Retry the same key and payload; reject a changed payload |
| 02:30 | Velocity hold, empty ledger, unchanged sender balance |
| 03:00 | Hold/release semantics and audit policy |
| 03:30 | Enqueue reconciliation; separate worker returns PASSED |
| 04:00 | Q&A answer backed by caller-scoped SQL; held intent excluded |
| 04:30 | Reject prompt injection; seed scale and review evidence |

Run the demo yourself:

```sh
docker compose up --build -d
docker compose --profile tools run --rm demo
```

`scripts/demo.py` exits nonzero on unexpected HTTP status, incorrect replay,
financial movement on hold, failed worker checks, missing Q&A results or accepted
injection. It records [evidence/demo.json](evidence/demo.json) and a readable
[evidence/demo.txt](evidence/demo.txt). Only demo credentials are used. Request IDs,
transaction IDs and timestamps reflect the recorded run and will differ on replay.

To regenerate the video after capturing new evidence (Docker, no host Python needed):

```sh
docker run --rm -v "${PWD}:/workspace" -w /workspace python:3.12-slim sh -c 'pip install --quiet Pillow==11.1.0 imageio-ffmpeg==0.6.0 && python scripts/render_demo.py'
```

The renderer reads the evidence, confirms the original/retry bytes match, and
renders thirty ten-second caption pages. Long responses are visibly truncated
with a link to the complete JSON; no response values are fabricated. The bundled
FFmpeg encoder writes a Git-compatible MP4 with fast-start playback. The recording
uses the explicitly documented offline count grammar; it is not evidence of live
LLM language understanding.
