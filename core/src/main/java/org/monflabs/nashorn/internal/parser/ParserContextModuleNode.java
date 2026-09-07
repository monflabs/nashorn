/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.monflabs.nashorn.internal.parser;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.Module;
import org.monflabs.nashorn.internal.ir.Module.ExportEntry;
import org.monflabs.nashorn.internal.ir.Module.ImportEntry;

/**
 * ParserContextNode that represents a module.
 */
class ParserContextModuleNode extends ParserContextBaseNode {

    /** Module name. */
    private final String name;

    private final List<String> requestedModules = new ArrayList<>();
    private final List<ImportEntry> importEntries = new ArrayList<>();
    private final List<ExportEntry> localExportEntries = new ArrayList<>();
    private final List<ExportEntry> indirectExportEntries = new ArrayList<>();
    private final List<ExportEntry> starExportEntries = new ArrayList<>();

    private boolean hasTopLevelAwait;

    void setHasTopLevelAwait() {
        hasTopLevelAwait = true;
    }

    /**
     * Constructor.
     *
     * @param name name of the module
     */
    ParserContextModuleNode(final String name) {
        this.name = name;
    }

    /**
     * Returns the name of the module.
     *
     * @return name of the module
     */
    public String getModuleName() {
        return name;
    }

    public void addModuleRequest(final IdentNode moduleRequest) {
        requestedModules.add(moduleRequest.getName());
    }

    public void addImportEntry(final ImportEntry importEntry) {
        importEntries.add(importEntry);
    }

    public void addLocalExportEntry(final ExportEntry exportEntry) {
        localExportEntries.add(exportEntry);
    }

    public void addIndirectExportEntry(final ExportEntry exportEntry) {
        indirectExportEntries.add(exportEntry);
    }

    public void addStarExportEntry(final ExportEntry exportEntry) {
        starExportEntries.add(exportEntry);
    }

    /**
     * The module as the runtime needs it.
     *
     * ES2015 15.2.1.16 ParseModule step 9: exporting a name the module imported
     * is a re-export of what the other module holds, not an export of anything
     * of this one's - so it becomes an indirect export naming the module the
     * import came from. Written as a local export it would be a second binding
     * of the same thing, which is what makes a name exported twice through two
     * routes look ambiguous when it is not. A namespace import is the exception:
     * that binding is the module's own.
     *
     * @return the module record's static half
     */
    public Module createModule() {
        final List<ExportEntry> locals = new ArrayList<>(localExportEntries.size());
        final List<ExportEntry> indirects = new ArrayList<>(indirectExportEntries);
        for (final ExportEntry local : localExportEntries) {
            final ImportEntry imported = importOf(local.getLocalName().getName());
            if (imported == null || Module.STAR_NAME.equals(imported.getImportName().getName())) {
                locals.add(local);
            } else {
                indirects.add(Module.ExportEntry
                        .exportSpecifier(local.getExportName(), imported.getImportName(),
                                local.getStartPosition(), local.getEndPosition())
                        .withFrom(imported.getModuleRequest(), local.getEndPosition()));
            }
        }
        final Module result = new Module(requestedModules, importEntries, locals, indirects, starExportEntries);
        result.setHasTopLevelAwait(hasTopLevelAwait);
        return result;
    }

    private ImportEntry importOf(final String localName) {
        for (final ImportEntry entry : importEntries) {
            if (localName.equals(entry.getLocalName().getName())) {
                return entry;
            }
        }
        return null;
    }
}
