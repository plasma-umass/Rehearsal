const IBMCos = require('ibm-cos-sdk');
// This gives compile errors. Uncomment for type completion.
// import * as IBMCos from 'ibm-cos-sdk';
import * as Openwhisk from 'openwhisk';

export function main(params: any) {
  console.log(JSON.stringify(params));
  const cos = new IBMCos.S3({
    endpoint: 'https://s3.us-south.objectstorage.softlayer.net',
    apiKeyId: params.apiKeyId,
    ibmAuthEndpoint: 'https://iam.ng.bluemix.net/oidc/token',
    serviceInstanceId: params.serviceInstanceId,
  });
  const ow = Openwhisk();

  const path = params.__ow_path.split('/');


  if (!(path.length === 4 &&
        path[0] === '' &&
        path[1] === 'query' &&
        (path[2] === 'centos-6' || path[2] === 'ubuntu-trusty'))) {
    return { body: `path ${path} is invalid` };
  }

  const os = path[2];
  const pkg = path[3];

  const key = `${os}/${pkg}`;
  return cos.getObject({ Bucket: 'rehearsal-cache', Key: key }).promise()
    .then((value: any) => ({ body: value.Body.toString() }))
    .catch((reason: any) =>
      ow.actions.invoke({
        name: `rehearsal-${os}-package-list`,
        result: true,
        blocking: true,
        params: { package: pkg }
      })
      .then(value => {
        const files = (value.files as string[]).join('\n');
        return cos.putObject({
            Body: files,
            Bucket: 'rehearsal-cache',
            Key: key
          }).promise()
          .then(() => ({ body: files }));
      }))
      .catch((reason: any) => ({ body: reason }));
}