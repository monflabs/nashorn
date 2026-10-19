#!/usr/bin/env bash
#
# Copyright (c) 2026, Philippe Riand. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Philippe Riand designates this
# particular file as subject to the "Classpath" exception as provided
# in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Serves the docsify documentation site (doc/nashorn) over HTTP for local
# preview. Docsify is client-side and fetches its markdown at runtime, so it
# needs a static HTTP server (opening index.html as a file:// URL does not
# work); python3's http.server is all it takes - the site itself is
# vendored, so this runs fully offline. One addition: HTTP Range requests,
# which CheerpJ uses to read the browser playground's jar and which the
# stock http.server ignores. Build that jar first with
# ./buildtools/build-playground-site.sh to try http://localhost:<port>/playground/
#
# Usage:
#   ./serve-docs.sh [port]      # default port 8000, or set PORT=...
# then open the printed http://localhost:<port>/ ; Ctrl-C to stop.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCS="$ROOT/doc/nashorn"
PORT="${1:-${PORT:-8000}}"

command -v python3 >/dev/null 2>&1 || { echo "error: python3 not found on PATH" >&2; exit 1; }
[ -f "$DOCS/index.html" ] || { echo "error: no docsify site at $DOCS" >&2; exit 1; }

echo "Serving the Nashorn docs at http://localhost:${PORT}/   (Ctrl-C to stop)"
exec python3 - "$PORT" "$DOCS" <<'PY'
import functools, http.server, os, re, sys

class RangeHandler(http.server.SimpleHTTPRequestHandler):
    """http.server's handler, answering a single "bytes=a-b" Range with a 206."""

    _range = None

    def send_head(self):
        self._range = None
        m = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", "").strip())
        path = self.translate_path(self.path)
        if m is None or not os.path.isfile(path):
            return super().send_head()
        size = os.path.getsize(path)
        first, last = m.group(1), m.group(2)
        if first:
            start, end = int(first), int(last) if last else size - 1
        elif last:
            start, end = max(0, size - int(last)), size - 1
        else:
            return super().send_head()
        end = min(end, size - 1)
        if start > end:
            self.send_response(416)
            self.send_header("Content-Range", "bytes */%d" % size)
            self.end_headers()
            return None
        f = open(path, "rb")
        f.seek(start)
        self._range = end - start + 1
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self.send_header("Content-Length", str(self._range))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        return f

    def copyfile(self, source, outputfile):
        if self._range is None:
            return super().copyfile(source, outputfile)
        outputfile.write(source.read(self._range))


port, root = int(sys.argv[1]), sys.argv[2]
handler = functools.partial(RangeHandler, directory=root)
with http.server.ThreadingHTTPServer(("", port), handler) as server:
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
PY
