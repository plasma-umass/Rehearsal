import express from 'express';
import request from 'request-promise-native';
import { Datastore } from '@google-cloud/datastore';
import morgan from 'morgan';

const ds = new Datastore({});

let images: Map<string, string> = new Map();

images.set('centos-6',
  'https://rehearsal-ubuntu-centos6-package-list-26qzybhotq-uc.a.run.app/');
images.set('ubuntu-trusty',
  'https://rehearsal-ubuntu-trusty-package-list-26qzybhotq-uc.a.run.app/');


function getPlatformUrl(platform: string): string {
  let url = images.get(platform);
  if (url === undefined) {
    throw new Error('unknown platform');
  }
  return url;
}

export const app = express();
app.use(morgan(
  ':method :url :status',
  {
    stream: {
      write: (str: string) => console.log(str)
    }
}));

app.get('/query/:platform/:package', (req, res) => {

  let platform = req.params.platform;
  let package_ = req.params.package;
  if (typeof platform !== 'string' || typeof package_ !== 'string') {
    res.status(400).send('platform and package must be strings');
    return;
  }
  // let key = ds.key(['rehearsal', package_, platform, 'Platform' ]);
  let key = ds.key(['Application', 'rehearsal', 'Platform', platform, 'Package', package_]);
  ds.get(key).then(([result]) => {
    if (result !== undefined) {
      res.status(200).send(JSON.stringify({ files: result.files }));
      return;
    }

    let url = getPlatformUrl(platform);
    request({
      method: 'POST',
      uri: url,
      body: { value: { package: package_ } },
      json: true
    }).then(body => {
      ds.upsert({
        key: key,
        data: body
      }).then(_ => res.status(200).send(JSON.stringify(body)))
    })
    .catch(reason => {
      console.error(reason);
      res.status(404).send(reason)
    });
  });

});
