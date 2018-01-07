// This is based on:
//
//   https://github.com/apache/incubator-openwhisk-runtime-docker/blob/master/core/actionProxy/actionproxy.py
var express = require('express');
var bodyParser = require('body-parser');
var cp = require('child_process');
var tmp = require('tmp');
var fs = require('fs');

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
  var { manifest, os, pred } = req.body.value;
  var tmpPath = tmp.fileSync({ suffix: '.pp' }).name;
  fs.writeFileSync(tmpPath, manifest);
  const result = cp.spawnSync('/usr/bin/java',
    [ '-jar', 'rehearsal.jar',  'check', '--filename', tmpPath, '--os', os,
      '--predicate', pred],
    { encoding: 'utf8', cwd: '/action', stdio: [ 'ignore', 'pipe', 'pipe' ] });
  let body;
  if (result.error !== null) {
    body = result.stderr + '\n' + result.stdout;
  }
  else {
    body = result.stdout;
  }
  resp.status(200).send({
    headers: {
      'Cotent-Type': 'text/plain',
    },
    body: body
  });
  complete();
})

app.listen(8080);