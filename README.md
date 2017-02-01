# Conver [![Build Status](https://travis-ci.org/pviotti/conver-scala.svg?branch=master)](https://travis-ci.org/pviotti/conver-scala)

Conver is a testing tool that verifies implementations of the most common 
non-transactional consistency models.  

It spawns client processes that perform concurrent reads and writes on 
the distributed store, and records their outcomes.
After the execution, it builds graph entities that describe ordering and 
mutual visibility of operations.
Finally, it uses these graph entities to check consistency semantics
defined as first-order logic predicates.  

By default, Conver conveniently starts distributed stores as local clusters of Docker containers,
and can emulate WAN latencies through `netem` (without requiring admin rights).  

The approach implemented in Conver has been described in [this PaPoC 2016 paper][papoc].  

NOTE: This is a work-in-progress Scala rewrite of the [original](https://github.com/pviotti/conver) 
Erlang implementation. It features a new linearizability checker, and improved consistency checks.


## Getting started

Once installed JDK, [Scala][scala] (with its building tool [sbt][sbt]) and Docker, 
to build Conver issue:

    $ sbt compile

This command spawns a local cluster of Docker containers running ZooKeeper and
performs the consistency verification test by using 10 concurrent clients
that invoke 10 operations on average:

    $ sbt "run -c zk -n 10 -o 10"

The resulting textual and graphical outputs look like the following.


![Conver execution](https://i.imgur.com/NSuyhVp.png)
 

## Features and related work

At the moment Conver supports:

 * linearizability checking based on pseudocode from [Lu et al., SOSP '15][exist], and
   checking of consistency semantics describing causality and monotonicity of operations
   within and across sessions (e.g., monotonic writes, monotonic reads, sequential consistency, regular 
   semantics, causal consistency --- [this survey][survey] provides an overview of all these consistency 
   semantics)  
 * WAN latencies emulation


Similar projects: [Jepsen][jepsen], [Hermitage][hermitage], [WatCA][watca].  
Linearizability checkers: [Horn's][horn], [Lowe's][lowe].  


## Authors and license

Conver has been developed at [EURECOM][eurecom].  
License: Apache 2.0.


 [survey]: http://arxiv.org/abs/1512.00168
 [papoc]: http://www.eurecom.fr/fr/publication/4874/download/ds-publi-4874.pdf
 [jepsen]: http://jepsen.io
 [hermitage]: https://github.com/ept/hermitage
 [eurecom]: http://www.eurecom.fr
 [horn]: https://github.com/ahorn/linearizability-checker
 [lowe]: http://www.cs.ox.ac.uk/people/gavin.lowe/LinearizabiltyTesting/
 [scala]: https://www.scala-lang.org/
 [sbt]: http://www.scala-sbt.org/
 [exist]: http://sigops.org/sosp/sosp15/current/2015-Monterey/printable/240-lu.pdf
 [watca]: https://github.com/wgolab/WatCA
