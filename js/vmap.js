## generated from vmap.coffee

var VectorMap;
var __slice = Array.prototype.slice;
VectorMap = (function() {
  var add_one_peer, add_one_vc, computeHash, hashVMap, hexDecode, max_clock, updateKey, updateVMapHash, vclock_increment, vclock_lub;
  max_clock = function(c1, c2) {
    if (c1[0] >= c2[0]) {
      return c1;
    } else {
      return c2;
    }
  };
  add_one_peer = function(out, peer, c) {
    if ((out[peer] != null)) {
      return out[peer] = max_clock(c, out[peer]);
    } else {
      return out[peer] = c;
    }
  };
  add_one_vc = function(out, vc) {
    var peer, _results;
    _results = [];
    for (peer in vc) {
      _results.push(add_one_peer(out, peer, vc[peer]));
    }
    return _results;
  };
  vclock_lub = function() {
    var out, vc, vcs, _i, _len;
    vcs = 1 <= arguments.length ? __slice.call(arguments, 0) : [];
    out = {};
    for (_i = 0, _len = vcs.length; _i < _len; _i++) {
      vc = vcs[_i];
      add_one_vc(out, vc);
    }
    return out;
  };
  vclock_increment = function(vc, peer) {
    var c, out;
    out = vc != null ? vc.clone : {};
    c = out[peer];
    if (c !== null) {
      out[peer] = [c[0] + 1, Date.getTime() / 1000];
    } else {
      out[peer] = [1, Date.getTime() / 1000];
    }
    return out;
  };
  hashVMap = function(vmap) {
    return "";
  };
  computeHash = function(any) {
    var bin, json;
    switch (typeof any) {
      case "string":
      case "number":
      case "boolean":
        return Sha1.hash("text/plain;charset=utf8" + any, true);
      case "object":
        if (any._vmeta != null) {
          return hashVMap(any);
        } else if (any._mime != null) {
          bin = Base64.decode(any.base64);
          return Sha1.hash(any._mime + bin, false);
        } else {
          json = JSON.stringify(any);
          return Sha1.hash("application/json;charset=utf-8" + json, true);
        }
        break;
      case "undefined":
        return null;
      case null:
        return null;
    }
  };
  updateKey = function(vmap, key, editVC) {
    var mo, new_hash;
    if ((mo = vmap._meta[key]) != null) {
      if (mo.deleted === true) {
        if (vmap[key] != null) {
          delete mo.deleted;
          mo.vclock = editVC;
          mo.hash = compute_hash(vmap[key]);
          return true;
        }
        return false;
      }
      if (vmap[key] === null) {
        mo.deleted = true;
        delete mo.hash;
        mo.vclock = editVC;
        return true;
      }
      new_hash = compute_hash(vmap[key]);
      if (new_hash === mo.hash) {
        return false;
      }
      mo.hash = new_hash;
      mo.vclock = editVC;
      return true;
    } else {
      vmap._meta[key].hash = compute_hash(vmap[key]);
      vmap._meta[key].vclock = editVC;
      return true;
    }
  };
  hexDecode = function(str) {
    return str.replace(/([0-9A-Fa-f]{2})/g, function(hex) {
      return String.fromCharCode(parseInt(hex, 16));
    });
  };
  updateVMapHash = function(vmap, mapKeys) {
    var key, string, _i, _len, _ref;
    string = "";
    _ref = mapKeys.sort();
    for (_i = 0, _len = _ref.length; _i < _len; _i++) {
      key = _ref[_i];
      string += hexDecode(vmap._vmeta[key].hash) + "\n";
    }
    return Sha1.hash(string, false);
  };
  return {
    updateEdits: function(vmap, peerID) {
      var allKeys, changed, editVC, key, mapKeys, maxVC, metaKeys, metaVClocks, vmeta;
      vmeta = vmap['_vmeta'];
      if (!vmeta) {
        vmeta = (vmap['_vmeta'] = {});
      }
      for (key in vmap) {
        if (key !== '_vmeta') {
          mapKeys = key;
        }
      }
      for (key in vmeta) {
        metaKeys = key;
      }
      metaVClocks = (function() {
        var _i, _len, _results;
        _results = [];
        for (_i = 0, _len = metaKeys.length; _i < _len; _i++) {
          key = metaKeys[_i];
          _results.push(vmeta[key].vclock);
        }
        return _results;
      })();
      maxVC = vclock_lub.apply(null, metaVClocks);
      editVC = vclock_increment(maxVC, peerID);
      allKeys = mapKeys.union(metaKeys);
      changed = false;
      for (key in allKeys) {
        changed |= updateKey(vmap, key, editVC);
      }
      if (changed) {
        updateVMapHash(vmap, mapKeys);
      }
      return vmap;
    }
  };
})();
