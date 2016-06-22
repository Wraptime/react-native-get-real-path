'use strict';

// This file supports both iOS and Android

// Stop bluebird going nuts because it can't find "self"
if (typeof self === 'undefined') {
  global.self = global;
}

var GRP = require('react-native').NativeModules.GRP;
var NativeAppEventEmitter = require('react-native').NativeAppEventEmitter;
var Promise = require('bluebird');

var _getRealPathFromURI = Promise.promisify(GRP.getRealPathFromURI);

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

