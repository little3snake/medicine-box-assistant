#!/usr/bin/env python3
"""Fail if staged files (or, with --all, all tracked files) contain something that looks like a secret.

Install as a git pre-commit hook (once per clone):
    python tools/check_secrets.py --install-hook

Why a hook and not only a rule in AGENTS.md: instructions to an agent are advice, not law
(Lecture 8, slide 12); a check that runs before the action is part of the harness you control (slide 27).
A key in the git history costs points (course requirements, section 5).
"""
import pathlib
import re
import subprocess
import sys

PATTERNS = [
    ("Google API key", re.compile(r"AIza[0-9A-Za-z_\-]{35}")),
    ("Google API key (AQ. format)", re.compile(r"AQ\.[0-9A-Za-z_\-.]{20,}")),
    ("OpenAI-style key", re.compile(r"sk-[A-Za-z0-9_\-]{20,}")),
    ("Anthropic key", re.compile(r"sk-ant-[A-Za-z0-9_\-]{20,}")),
    ("GitHub token", re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}")),
    ("Private key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("Assigned key in config", re.compile(r"(?i)(api[_-]?key|token|secret|password)\s*[=:]\s*['\"]?[A-Za-z0-9_\-]{16,}")),
]
SKIP_SUFFIXES = {".png", ".jpg", ".jpeg", ".gif", ".pdf", ".zip", ".jar", ".class"}


def files(all_files: bool):
    cmd = ["git", "ls-files"] if all_files else ["git", "diff", "--cached", "--name-only", "--diff-filter=ACM"]
    out = subprocess.run(cmd, stdout=subprocess.PIPE, universal_newlines=True, check=True).stdout
    return [pathlib.Path(p) for p in out.splitlines() if p]


def scan(paths):
    problems = []
    for p in paths:
        if p.suffix.lower() in SKIP_SUFFIXES or not p.is_file():
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if "check_secrets: ignore" in line:
                continue
            for name, pat in PATTERNS:
                if pat.search(line):
                    problems.append(f"{p}:{i}: looks like {name}")
    return problems


def install_hook():
    hook = pathlib.Path(".git/hooks/pre-commit")
    hook.write_text("#!/bin/sh\npython3 tools/check_secrets.py\n", encoding="utf-8")
    hook.chmod(0o755)
    print(f"Installed {hook}")


def main():
    if "--install-hook" in sys.argv:
        install_hook()
        return
    problems = scan(files("--all" in sys.argv))
    if problems:
        print("Possible secrets found — commit blocked:")
        print("\n".join("  " + p for p in problems))
        print("Move the value to an environment variable. False positive? add 'check_secrets: ignore' on that line.")
        sys.exit(1)
    print("check_secrets: no secrets found")


if __name__ == "__main__":
    main()
