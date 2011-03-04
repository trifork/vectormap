
load("vmap.js");
load("sha1.js");
load("base64.js");
load("json2.js");


var x={"a":"b","c":"y","_vmeta":{}};

VectorMap.updateEdits(x, "krab");

print ("computed:  " + x._vmeta.c.hash);
print ("should be: dbcba096f13a5cb8315ebebc0bb384d10b4");
