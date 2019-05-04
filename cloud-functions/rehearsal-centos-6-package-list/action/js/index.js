var express = require('express');
var bodyParser = require('body-parser');
var cp = require('child_process');

var app = express();
var jsonParser = bodyParser.json()

let port = Number(process.env.PORT);

if (port <= 0) {
  throw new Error('PORT environment variable was not set.')
}

app.post('/', jsonParser, function(req, resp) {
  var packageName = req.body.value.package;
  if (typeof packageName !== 'string') {
    resp.status(404).send(JSON.stringify({
      error: 'expected "package" parameter'
    }));
    return;
  }

  var cmd = `repoquery -l ${packageName}`;
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

      var files = stdout.slice(0, stdout.length - 1) // drop trailing newline
        .split('\n'); // one file per line
      resp.status(200).send(JSON.stringify({
        files: files
      }));
    });
});

app.listen(port);