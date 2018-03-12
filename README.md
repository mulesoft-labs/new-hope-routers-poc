This example shows how we should handle live streaming routers such as Apikit 2.0 and RosettaNet.

This example is generic and meant to built on.

Basically there's a route operation and a route listener. They both share a common routeKey:

The connector verifies that:

* For every routeKey in a route operation there's ONE and ONLY ONE listener with that key
* For every listener, there's AT LEAST ONE route operation with a matching key
* No two listeners with the same key.

Listeners and sources register themselves through an intermediary RoutingManager. The RoutingManager passes messages back and forth.

Pay speciall attention to the RoutingContext implemented in the Source. There's some special magic in there to make sure that streams generated in the listener flow are not closed until the routing flow is finished. *This is the most important part of it all!*

There's a test case which does some basic assertions, the most important part of the test are the log messages.
