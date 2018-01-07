// This is based on:
//
//   https://github.com/apache/incubator-openwhisk-runtime-docker/blob/master/core/actionProxy/actionproxy.py
var express = require('express');
var bodyParser = require('body-parser');
var cp = require('child_process');

var app = express();
var jsonParser = bodyParser.json()

var LOG_SENTINEL = 'XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX\n';

function complete() {
  process.stdout.write(LOG_SENTINEL);
  process.stderr.write(LOG_SENTINEL);
}

// This handler receives executable source code if an action is a ZIP file
// based on this image. But, we are never going to do that.
app.post('/init', function(req, resp) {
  resp.status(200).send();
});

app.post('/run', jsonParser, function(req, resp) {
  var packageName = req.body.value.package;
  if (typeof packageName !== 'string') {
    resp.status(404).send(JSON.stringify({
      error: 'expected "package" parameter'
    }));
    complete();
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
        complete();
        return;
      }

      // Drop the trailing newline or we get an empty string in the file list.
      var files = stdout.slice(0, stdout.length - 1) // drop trailing newline
        .split('\n') // one file per line
        .map(x => x.split(' ', 3)[1]); // 2nd column, space delimited
      resp.status(200).send(JSON.stringify({
        files: files
      }));
      complete();
    });
});

app.listen(8080);