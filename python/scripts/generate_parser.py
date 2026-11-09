"""Generate Python sources from the repository's shared ANTLR grammar."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request


VERSION = "4.13.2"
PROJECT = Path(__file__).resolve().parents[1]
GRAMMAR = PROJECT.parent / "src/main/antlr/CanadianAddress.g4"
OUTPUT = PROJECT / "src/northpost_parser/generated"
FILES = (
    "CanadianAddressLexer.py",
    "CanadianAddressParser.py",
    "CanadianAddressVisitor.py",
)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if generated sources are stale")
    parser.add_argument("--antlr-jar", type=Path, help=f"Local antlr-{VERSION}-complete.jar")
    args = parser.parse_args()
    java = shutil.which("java")
    if java is None:
        parser.error("Java is required for ANTLR code generation (not for using the library)")
    jar = args.antlr_jar
    if jar is None:
        cache = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "northpost-parser"
        cache.mkdir(parents=True, exist_ok=True)
        jar = cache / f"antlr-{VERSION}-complete.jar"
        if not jar.exists():
            with tempfile.TemporaryDirectory(dir=cache) as download:
                target = Path(download) / jar.name
                urllib.request.urlretrieve(f"https://www.antlr.org/download/{jar.name}", target)
                target.replace(jar)
    if not jar.is_file():
        parser.error(f"ANTLR jar not found: {jar}")
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run(
            [java, "-jar", str(jar.resolve()), "-Dlanguage=Python3", "-visitor",
             "-no-listener", "-Xexact-output-dir", "-o", directory, GRAMMAR.name],
            cwd=GRAMMAR.parent,
            check=True,
        )
        stale = []
        for name in FILES:
            generated = Path(directory) / name
            destination = OUTPUT / name
            if args.check:
                if not destination.exists() or destination.read_bytes() != generated.read_bytes():
                    stale.append(name)
            else:
                shutil.copyfile(generated, destination)
        if stale:
            parser.exit(1, f"Stale generated sources: {', '.join(stale)}. Run scripts/generate_parser.py.\n")


if __name__ == "__main__":
    main()
