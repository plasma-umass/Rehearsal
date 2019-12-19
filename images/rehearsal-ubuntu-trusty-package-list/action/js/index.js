var express = require('express');
var cp = require('child_process');

var app = express();

app.get('/:package', function(req, resp) {
  var packageName = req.params.package;

  var cmd = `apt-file -F list ${packageName}`;
  cp.exec(cmd,
    { encoding: 'utf8' },
    function(err, stdout, stderr) {
      if (err !== null) {
        resp.status(404).send(JSON.stringify({
          error: {
            message: `error from ${cmd}`,
            stdout: stdout,
            stderr: stderr
          }
        }));
        return;
      }

      // Drop the trailing newline or we get an empty string in the file list.
      var files = stdout.slice(0, stdout.length - 1) // drop trailing newline
        .split('\n') // one file per line
        .map(x => x.split(' ', 3)[1]); // 2nd column, space delimited
      resp.status(200).send(JSON.stringify({
        files: files
      }));
    });
});

app.listen(8080);