#!/usr/bin/env python3
"""Turn a "Help improve the parser" report into golden SMS test stubs.

Usage: scripts/feedback-to-golden.py <report> [--dry-run] [--mark-done]

Reads the report the app emails or saves (see core/data/.../feedback/ParserFeedback.kt): JSON
Lines from 1.0.3 (ADR-0047; a pasted email body works too, even if the mail app wrapped long
lines, and several reports can be concatenated), or the 1.0.2 text format
("budget-widget-parser-feedback v1"). One file may hold several reports in either format, one
after another (the owner's docs/parser-dataset.md grows that way). It appends one stub per message
to the matching ingest/src/test/resources/golden/<bank>.txt, with `type: TODO`. Senders of banks
we don't ship go to golden/unsupported.txt, which the tests do not load.

Messages already handled are skipped: a ledger next to the report (<report>.ledger, one SHA-256
of sender and body per line; gitignored, it is derived from private messages) records every
message turned into a stub. --mark-done records the messages without writing stubs, for ones
handled by hand.

A stub makes GoldenSmsTest fail until someone fills in the expected values by reading the SMS
(docs/PARSER_IMPROVEMENT.md). Messages that still look unredacted (an @, or a run of 6+ digits
that isn't the app's mask: all 9s except possibly the last 2) are refused, never written.
Standard library only.
"""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GOLDEN = ROOT / "ingest/src/test/resources/golden"
HEADER = "budget-widget-parser-feedback v1"

# TRAI header core (the part after "XX-") -> golden file. Same banks as SCOPE.md section 2.
BANKS = [
    (r"HDFC", "hdfc"), (r"SBI|ATMSBI|SBICRD|SBIINB", "sbi"), (r"AXIS", "axis"), (r"KOTAK", "kotak"),
    (r"FEDBNK|FEDERAL", "federal"), (r"JTEDGE|JUPITR|JUPITER", "jupiter"), (r"SLICE|SLCEIT|SLCBNK", "slice"),
    # Formats only our LocalFormats reads (ADR-0061); add a line with each new one.
    (r"NKGSBK", "nkgsb"), (r"IRSMS", "irctc"), (r"CREDWL", "cred"),
]
ENTRY = re.compile(r"^--- (\d+) \| (.+?) \| ([A-Z_]+) \| (\d{4}-\d{2}) \| x(\d+)$")
LONG_RUN = re.compile(r"\d{6,}")


def looks_unredacted(body):
    """1.0.3+ reports turn digits into 9s (ADR-0046); account and card numbers keep their last 2."""
    return "@" in body or any(set(m[0][:-2]) != {"9"} for m in LONG_RUN.finditer(body))


SECTION = re.compile(r"^#+\s")


def parse(text):
    """Every report in the file: v1 blocks (from their header to the next header, JSON line or
    Markdown heading) and JSON Lines anywhere else."""
    v1_blocks, rest, block = [], [], None
    for line in text.splitlines():
        if line.strip() == HEADER:
            block = [line]
            v1_blocks.append(block)
        elif block is not None and not line.lstrip().startswith("{") and not SECTION.match(line):
            block.append(line)
        else:
            block = None
            rest.append(line)
    entries = [e for b in v1_blocks for e in parse_v1("\n".join(b))]
    entries += parse_json_lines("\n".join(rest), required=not entries)
    for i, e in enumerate(entries, 1):
        e["n"] = str(i)
    return entries


def parse_json_lines(text, required=True):
    """One object per line. Lines that aren't JSON (the email's intro) are skipped. A line a mail
    app wrapped is joined back: text is gathered until it parses as a whole object."""
    entries, buffer = [], ""
    for line in text.splitlines():
        if not buffer and not line.lstrip().startswith("{"):
            continue
        buffer += line
        try:
            obj = json.loads(buffer)
        except json.JSONDecodeError:
            continue
        buffer = ""
        if not isinstance(obj, dict) or obj.get("v") != 2:
            sys.exit(f"unsupported report line (expected v 2): {str(obj)[:60]}")
        entries.append({
            "n": str(len(entries) + 1), "sender": obj["sender"], "reason": obj["reason"],
            "month": obj["month"], "count": str(obj["count"]), "body": obj["body"].replace("\n", "\\n"),
        })
    if buffer:
        sys.exit("report ends in the middle of a message (cut short?)")
    if not entries and required:
        sys.exit("not a parser feedback report (no messages found)")
    return entries


def parse_v1(text):
    lines = text.lstrip().splitlines()
    entries, current = [], None
    for line in lines[1:]:
        m = ENTRY.match(line)
        if m:
            current = {"n": m[1], "sender": m[2], "reason": m[3], "month": m[4], "count": m[5], "body": []}
            entries.append(current)
        elif current is not None:
            current["body"].append(line)
    for e in entries:
        e["body"] = "\\n".join(e["body"]).strip()
    return entries


def bank_file(sender):
    core = sender.split("-")[1] if sender.count("-") >= 1 else sender
    for pattern, name in BANKS:
        if re.search(pattern, core, re.IGNORECASE):
            return name
    return "unsupported"


def stub(e):
    return (
        f"# From parser feedback ({e['month']}, seen {e['count']}x, reason {e['reason']}). "
        "Fill in from the SMS text, not from the parser.\n"
        f"name: TODO describe ({e['sender']} #{e['n']})\n"
        f"sender: {e['sender']}\n"
        f"body: {e['body']}\n"
        "type: TODO\n"
    )


def fingerprint(e):
    return hashlib.sha256(f"{e['sender']}\n{e['body']}".encode("utf-8")).hexdigest()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    mark_done = "--mark-done" in sys.argv
    if len(args) != 1:
        sys.exit(__doc__)
    report = pathlib.Path(args[0])
    ledger = report.with_name(report.name + ".ledger")
    done = set(ledger.read_text(encoding="utf-8").split()) if ledger.exists() else set()
    entries = parse(report.read_text(encoding="utf-8"))
    refused, written, new, skipped = [], {}, [], 0
    for e in entries:
        key = fingerprint(e)
        if key in done:
            skipped += 1
            continue
        if looks_unredacted(e["body"]):
            refused.append(e)
            continue
        done.add(key)
        new.append(key)
        if not mark_done:
            written.setdefault(bank_file(e["sender"]), []).append(stub(e))
    print(f"{len(entries)} messages: {skipped} already handled, {len(new)} new, {len(refused)} refused")
    for name, stubs in written.items():
        path = GOLDEN / f"{name}.txt"
        print(f"{path.relative_to(ROOT)}: +{len(stubs)}")
        if not dry:
            existing = path.read_text(encoding="utf-8").rstrip("\n") if path.exists() else ""
            sep = "\n---\n" if existing else ""
            path.write_text(existing + sep + "\n---\n".join(stubs), encoding="utf-8")
    if new and not dry:
        with ledger.open("a", encoding="utf-8") as f:
            f.write("".join(k + "\n" for k in new))
        print(f"{ledger}: +{len(new)}" + (" (marked done, no stubs)" if mark_done else ""))
    for e in refused:
        print(f"REFUSED #{e['n']} from {e['sender']}: looks unredacted (6+ digits or @). Mask it by hand.",
              file=sys.stderr)
    return 1 if refused else 0


if __name__ == "__main__":
    sys.exit(main())
