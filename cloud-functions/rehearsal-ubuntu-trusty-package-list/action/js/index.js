var express = require('express');
var bodyParser = require('body-parser');
var cp = require('child_process');

let port = Number(process.env.PORT);

if (port <= 0) {
  throw new Error('PORT environment variable was not set.')
}

var app = express();
var jsonParser = bodyParser.json()

app.post('/', jsonParser, function(req, resp) {
  console.log(req.body);
  var packageName = req.body.value.package;
  if (typeof packageName !== 'string') {
    resp.status(404).send(JSON.stringify({
      error: 'expected "package" parameter'
    }));
    return;
  }

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

app.listen(port);