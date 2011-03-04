VectorMap = (() -> (  

 max_clock = (c1,c2) ->
       if c1[0] >= c2[0]  
         c1  
       else  
         c2 

 add_one_peer = (out, peer, c) ->
       if (out[peer]?) 
          out[peer] = max_clock(c, out[peer])
       else
          out[peer] = c

 add_one_vc = (out, vc) ->
       add_one_peer(out, peer, vc[peer]) for peer of vc


 # merge a number of vclocks, producing the "least 
 vclock_lub = (vcs...) ->
     out = {}
     add_one_vc(out, vc) for vc in vcs
     out


 vclock_increment = (vc, peer) ->
     out =  if vc? then vc.clone else {}
     c = out[peer]
     if c != null
       out[peer] = [c[0]+1, Date.getTime()/1000]
     else
       out[peer] = [1, Date.getTime()/1000]
     out


  # "number"	Operand is a number
  # "string"	Operand is a string
  # "boolean"	Operand is a Boolean
  # "object"	Operand is an object
  # null	Operand is null.
  # "undefined"	Operand is not defined.

 hashVMap = (vmap) ->
    ""

 computeHash = (any) ->
   switch typeof any
     when "string", "number", "boolean"
       return Sha1.hash("text/plain;charset=utf8" + any, true)
       
     when "object"
       if any._vmeta? 
          hashVMap(any)
       else if any._mime?
          bin = Base64.decode(any.base64)
          return Sha1.hash(any._mime + bin, false)
       else
          json = JSON.stringify(any)
          return Sha1.hash("application/json;charset=utf-8" + json, true)

     when "undefined"
       null

     when null
       null

 # returns true if the value changed
 updateKey = (vmap,key,editVC) ->

   if (mo=vmap._meta[key])?
      # did know about key

      if mo.deleted == true    
         # was deleted
 
         if vmap[key]?

            # has been resurrected
            delete mo.deleted   
            mo.vclock = editVC
            mo.hash = compute_hash(vmap[key])
            return true

         #  ... and is still deleted
         return false
         
      if vmap[key] == null # is now deleted
         mo.deleted = true
         delete mo.hash
         mo.vclock = editVC
         return true
       
      # still exists
      new_hash = compute_hash(vmap[key])

      if new_hash == mo.hash # no sweat, nothing happened
        return false
      
      mo.hash = new_hash
      mo.vclock = editVC       
      return true

   else
      vmap._meta[key].hash = compute_hash(vmap[key])
      vmap._meta[key].vclock = editVC
      return true
             
 hexDecode = (str) ->
    str.replace /([0-9A-Fa-f]{2})/g, (hex) ->
        String.fromCharCode(parseInt(hex, 16));
    

 updateVMapHash = (vmap, mapKeys) ->
   string = ""
   string += (hexDecode(vmap._vmeta[key].hash)+"\n") for key in mapKeys.sort()
   Sha1.hash(string, false)

 updateEdits: (vmap, peerID) ->
   vmeta = vmap['_vmeta']
   vmeta = (vmap['_vmeta']={}) if !vmeta 
   
   mapKeys = key for key of vmap when key != '_vmeta'
   metaKeys = key for key of vmeta 

   metaVClocks = (vmeta[key].vclock for key in metaKeys)
   maxVC = vclock_lub(metaVClocks...)
   editVC = vclock_increment(maxVC, peerID) 
   allKeys = mapKeys.union(metaKeys)

   changed = false
   changed |= updateKey(vmap,key,editVC) for key of allKeys

   if changed
      updateVMapHash(vmap, mapKeys)
   vmap

))()

