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
#
# Builds the browser (CheerpJ) playground and copies its jar into the docsify
# site at doc/nashorn/playground/, beside the page that runs it - which is what
# the "Playground in the browser" link of the site's User's Guide opens.
#
# The jar is NOT checked in - doc/nashorn/playground/*.jar is gitignored. Run
# this before ./serve-docs.sh to try the playground locally, then open
# http://localhost:8000/playground/ ; the publish workflow
# (.github/workflows/publish-docs.yml) runs it on every push to main.
#
# Usage:
#   ./buildtools/build-playground-site.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mvn -B -pl playground-cheerpj -am -DskipTests package
cp playground-cheerpj/target/nashorn-playground-cheerpj.jar doc/nashorn/playground/
echo "Copied the browser playground to doc/nashorn/playground/"
