# Debugging

Every script in the playground is compiled with `--debugger`, which is what makes **Stop** able to end a runaway loop, and what a Chrome DevTools client attaches to.

1. Tick **Start the debugger server** on the toolbar. The status line shows the WebSocket URL the server listens on (port 9229).
2. In Chrome, open `chrome://inspect`, and click **inspect** under *Remote Target* on the `nashorn` entry.
3. Run the sample: it pauses at the `debugger;` statement. Step, inspect the scopes, evaluate in the console, set breakpoints by clicking line numbers.

The **Debug** button runs paused at the first statement - for a script with no `debugger;` in
it - one run at a time.

From a Java program the same is `--inspect` / `--inspect-brk` among the engine options, or `Debugger.of(engine)` from `org.monflabs.nashorn.api.debugger`.
