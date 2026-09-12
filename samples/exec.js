/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * A small replacement for the $EXEC function the removed scripting mode used
 * to install: runs an external command and returns its standard output.
 *
 * The exit status is left in exec.exitCode and the standard error in exec.err.
 * Set exec.throwOnError to throw on a non-zero exit status instead.
 *
 * Usage: load("exec.js") from a script that needs it.
 */

function exec(command, input) {
    var ProcessBuilder = Java.type("java.lang.ProcessBuilder");
    var StandardCharsets = Java.type("java.nio.charset.StandardCharsets");

    var args = Array.isArray(command) ? command : String(command).split(/\s+/);
    var process = new ProcessBuilder(Java.to(args, "java.lang.String[]")).start();

    if (input !== undefined) {
        process.outputStream.write(String(input).getBytes(StandardCharsets.UTF_8));
    }
    process.outputStream.close();

    var out = new java.lang.String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8);
    var err = new java.lang.String(process.errorStream.readAllBytes(), StandardCharsets.UTF_8);
    var code = process.waitFor();

    exec.out = String(out);
    exec.err = String(err);
    exec.exitCode = code;

    if (exec.throwOnError && code != 0) {
        throw new Error("command failed (" + code + "): " + command + "\n" + exec.err);
    }
    return exec.out;
}
