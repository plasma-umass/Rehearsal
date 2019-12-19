import express from 'express';
import bodyParser from 'body-parser';
import cp from 'child_process';
import tmp from 'tmp';
import fs from 'fs';
import cors from 'cors';

var app = express();
var jsonParser = bodyParser.json()

app.use (cors({ origin: '*' }));

app.post('/', jsonParser, function(req, resp) {
  var { manifest, os, pred } = req.body;
  var tmpPath = tmp.fileSync().name;
  fs.writeFileSync(tmpPath, manifest);
  const result = cp.spawnSync('/usr/bin/java',
    [ '-Xmx2G', '-jar', 'rehearsal.jar',  'check', '--filename', tmpPath, '--os', os,
      '--predicate', pred],
    { encoding: 'utf8', cwd: '/action', stdio: [ 'ignore', 'pipe', 'pipe' ] });
  let body;
  if (result.error !== null) {
    body = result.stderr + '\n' + result.stdout;
  }
  else {
    body = result.stdout;
  }
  resp.status(200).send(body);
})

app.listen(8080);