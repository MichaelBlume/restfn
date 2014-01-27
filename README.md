restfn
======

[![Build Status](https://travis-ci.org/MichaelBlume/restfn.png?branch=master)](https://travis-ci.org/MichaelBlume/restfn)
[![Dependencies Status](http://jarkeeper.com/MichaelBlume/restfn/status.png)](http://jarkeeper.com/MichaelBlume/restfn)

This is a library for quickly bolting an admin REST API on to your long-running
app. Specifically it's a library for specifying the URL structure of that API
using nested Clojure literals. You pass a map literal specifying your API
structure to get-rest-handler and it gives you back a handler of the kind you
could then pass to Ring. Restfn doesn't depend on Ring, but it more or less
assumes you do.

You add restfn to your app in more or less the usual way.

In Leiningen:

```clj
[restfn "0.1.1"]
```

In Maven:

```xml
<dependency>
  <groupId>restfn</groupId>
  <artifactId>restfn</artifactId>
  <version>0.1.1</version>
</dependency>
```

Then within your project:

```clj
(ns your.ns
  (:use (restfn core)))
```

This will import one function, get-rest-handler, and one protocol,
RestSerializable. Pass your map to get-rest-handler, and pass the result to
Ring.

How does a map literal represent a URL structure? Well, you can probably
partially guess, but it works a bit like this. You pass in an object
implementing IFn, probably a map. Then the client sends in a URI. The URI is
split into segments. Restfn applies your object to the first segment, then
applies the result to the next segment, until there's no more, then returns the
result.

So, observe:

```clj
(get-rest-handler
  {"map" {"foo" "bar"}})
```

If you hit this handler at **/map**, you'll get the JSON-encoded map
```{"foo" "bar"}```.  If you hit it at **/map/foo** you'll get a JSON-encoding
of the string ```"bar"```.  Super-useful, right?

```clj
(get-rest-handler
  {"stats" {"total" total-stats
            "worker" get-stats-for-worker}})
```

Let's say ```total-stats``` is some stats object for your app, it implements the
```RestSerializable``` protocol:

```clj
(defprotocol RestSerializable
  (rest-serialize [this]
   "Convert to something Cheshire can JSONify"))
```

and returns a map containing stats for your app. Let's say
```get-stats-for-worker``` is a similar function which takes a worker number
and returns stats for that worker (either a map or a similar object
implementing ```RestSerializable```).

Then **/stats/total** will get you that map of total stats, and
**/stats/worker/3** will get you the stats for worker 3 (the "3" is
transparently converted to int before being fed to the function).

```clj
(get-rest-handler
  {"processing" {:delete stop-processing!
                 :post   start-processing!}})
```

This creates one endpoint, **/processing**. If you **POST** to it, it'll start
up your app. If you **DELETE** it, it'll stop your app.

Restfn has a bit of extra smarts for handling java ```Map```s and ```List```s.
By extra smarts, I mean, if it can't apply an object to a segment, it'll try
calling ```.get``` instead.

If your request causes an exception, you'll get a status code 500 and a
traceback.

Functions in a ```{:post fn :delete fn :put fn}``` map can optionally take a
parameter -- that parameter will be the request object. If you wrap your
handler in middleware that enhances the request object, these fns will, of
course, get the enhanced request.
