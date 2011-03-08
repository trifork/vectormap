(function() {
  var VectorMap, add_one_peer, add_one_vc, clone, computeEditVC, computeHash, hashVMap, hexDecode, max_clock, missing, updateEdits, updateKey, updateVMapHash, utc_secs, vclock_increment, vclock_lub;
  var __slice = Array.prototype.slice;
  max_clock = function(c1, c2) {
    return [Math.max(c1[0], c2[0]), Math.max(c1[1], c2[1])];
  };
  missing = function(o) {
    return o == null || (typeof o) == "undefined";
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
  utc_secs = function() {
    return Math.floor(new Date().getTime() / 1000);
  };
  clone = function(o) {
    var k, res, _i, _len;
    res = {};
    for (_i = 0, _len = o.length; _i < _len; _i++) {
      k = o[_i];
      res[k] = o[k];
    }
    return res;
  };
  vclock_increment = function(vc, peer) {
    var c, out;
    out = vc != null ? clone(vc) : {};
    c = out[peer];
    if (c != null) {
      out[peer] = [c[0] + 1, utc_secs()];
    } else {
      out[peer] = [1, utc_secs()];
    }
    return out;
  };
  hashVMap = function(vmap) {
    return "";
  };
  computeHash = function(any) {
    var bin, json, sha, val;
    switch (typeof any) {
      case "string":
      case "number":
      case "boolean":
        val = "text/plain;charset=utf-8\n" + any;
        sha = Sha1.hash(val, false);
        return sha;
      case "object":
        if (any._vmeta != null) {
          return hashVMap(any);
        } else if (any._mime != null) {
          bin = Base64.decode(any.base64);
          return Sha1.hash(any._mime + "\n" + bin, false);
        } else {
          json = JSON.stringify(any);
          return Sha1.hash("application/json;charset=utf-8\n" + json, true);
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
    if (!missing(mo = vmap._vmeta[key])) {
      print("key " + key + " was known");
      if (mo.deleted === true) {
        if (vmap[key] != null) {
          delete mo.deleted;
          mo.vclock = editVC;
          mo.hash = computeHash(vmap[key]);
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
      new_hash = computeHash(vmap[key]);
      if (new_hash === mo.hash) {
        return false;
      }
      mo.hash = new_hash;
      mo.vclock = editVC;
      return true;
    } else {
      print("key " + key + " was NOT known");
      vmap._vmeta[key] = {
        orig: vmap[key],
        hash: computeHash(vmap[key]),
        vclock: editVC
      };
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
  computeEditVC = function(vmap, peerID) {};
  updateEdits = function(vmap, peerID) {
    var allKeys, changed, editVC, key, mapKeys, maxVC, metaKeys, metaVClocks, vmeta, _i, _len;
    vmeta = vmap['_vmeta'];
    if (!vmeta) {
      vmeta = (vmap['_vmeta'] = {});
    }
    mapKeys = (function() {
      var _results;
      _results = [];
      for (key in vmap) {
        if (key !== '_vmeta') {
          _results.push(key);
        }
      }
      return _results;
    })();
    metaKeys = (function() {
      var _results;
      _results = [];
      for (key in vmeta) {
        _results.push(key);
      }
      return _results;
    })();
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
    allKeys = mapKeys;
    changed = false;
    for (_i = 0, _len = allKeys.length; _i < _len; _i++) {
      key = allKeys[_i];
      changed |= updateKey(vmap, key, editVC);
    }
    print(JSON.stringify(mapKeys));
    print(JSON.stringify(vmap));
    if (changed) {
      updateVMapHash(vmap, mapKeys);
    }
    return vmap;
  };
  VectorMap = (function() {
    function VectorMap(json) {
      this.json = json;
    }
    VectorMap.prototype.put = function(key, value) {
      return this.json[key] = value;
    };
    return VectorMap;
  })();
  VectorMap.updateEdits = updateEdits;
  (typeof exports != "undefined" && exports !== null ? exports : this).VectorMap = VectorMap;
}).call(this);
