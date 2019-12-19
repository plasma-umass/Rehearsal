import express from 'express';
import request from 'request-promise-native';
import morgan from 'morgan';


let images: Map<string, string> = new Map();

let cache: Map<string, Map<string, any>> = new Map();

images.set('centos-6', 'http://rehearsal-centos-6-package-list:8080');
images.set('ubuntu-trusty', 'http://rehearsal-ubuntu-trusty-package-list:8080');

cache.set('centos-6', new Map());
cache.set('ubuntu-trusty', new Map());

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
  let platformMap = cache.get(platform)!;
  let result = platformMap.get(package_);
  if (result !== undefined) {
      res.status(200).send(JSON.stringify({ files: result.files }));
      return;
  }

  let url = getPlatformUrl(platform);
  request({
    method: 'GET',
    uri: `${url}/${package_}`,
    json: true
  }).then(body => {
    platformMap.set(package_, body);
    res.status(200).send(JSON.stringify(body));
  })
  .catch(reason => {
    console.error(reason);
    res.status(404).send(reason)
  });

});

app.listen(8080);