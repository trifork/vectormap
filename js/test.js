
load("vmap.js");
load("sha1.js");
load("base64.js");
load("json2.js");


var x={"a":"b","c":"data","_vmeta":{}};

VectorMap.updateEdits(x, "krab");

val = "text/plain;charset=utf-8\ndata";
print ("sha1("+JSON.stringify(val)+")");
print ("  -->      " + Sha1.hash(val, true));
print ("sha1("+JSON.stringify(val)+")");
print ("  -->      " + Sha1.hash(val, true));
print ("computed:  " + x._vmeta.c.hash);
print ("should be: 1F307913BE39D2C82D934CDF3DF7048AADA6F16F");
