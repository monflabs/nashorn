// @option -scripting
// With -scripting: heredocs, $ENV, expression interpolation in double-quoted
// strings, and # line comments.

# a comment, shell style

var name = 'playground';
var count = 3;

// Interpolation in double quotes - single quotes stay literal
print("Hello ${name}, ${count} times: ${'*'.repeat(count)}");
print('Not here: ${name}');

// A heredoc, interpolated too
var text = <<EOF
Dear ${name},

  the time is ${new Date().getHours()}h,
  and 2 + 2 is ${2 + 2}.
EOF
print(text);

// The environment and the properties of the JVM
print('HOME is', $ENV.HOME || $ENV.USERPROFILE);
print('java.version is', java.lang.System.getProperty('java.version'));

// exit codes and $ARG exist for command-line scripts; here $ARG is empty
print('arguments:', $ARG.length);
