'use strict';

// This file supports both iOS and Android

// Stop bluebird going nuts because it can't find "self"
if (typeof self === 'undefined') {
  global.self = global;
}

var GRP = require('react-native').NativeModules.GRP;

function promisify(func) {
  return function promiseFunc(options) {
      return new Promise(function executor(resolve, reject) {
          func(options, function cb(err, val) {
              if (err) {
                  return reject(err);
              } else {
                  return resolve(val);
              }
          });
      });
  }
}

var _getRealPathFromURI = promisify(GRP ? GRP.getRealPathFromURI : (fileUri) => { return fileUri; });

var convertError = (err) => {
  if (err.isOperational && err.cause) {
    err = err.cause;
  }

  var error = new Error(err.description || err.message);
  error.code = err.code;
  throw error;
};

var RNGRP = {
  getRealPathFromURI(fileUri) {
    return _getRealPathFromURI(fileUri)
      .then(path => path)
      .catch(convertError);
  }
};

module.exports = RNGRP;

