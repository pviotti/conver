# Con:heavy_check_mark:er [![Build Status](https://travis-ci.org/pviotti/conver-scala.svg?branch=master)](https://travis-ci.org/pviotti/conver-scala)

Conver verifies implementations of the most common non-transactional consistency models.  

It spawns client processes that perform concurrent reads
and writes on the distributed store, and records their outcomes.
Then it builds graph entities that express ordering and mutual visibility of operations.
Finally, it uses such graph entities to check consistency semantics
defined as first-order logic predicates.  

The approach implemented in Conver has been described in [this PaPoC 2016 paper][papoc].  

**NOTE: This is a work-in-progress Scala rewrite of the [original](https://github.com/pviotti/conver) 
Erlang implementation. It features a new linearizability checker, and improved consistency checks.**


## Features

 * linearizability checker, based on pseudocode in 
	"Existential Consistency: Measuring and Understanding Consistency at Facebook" (Lu et al., SOSP '15)
 * new and more appropriate macro- consistency models describing monotonicity of operations
	within and across sessions ([this survey][survey] gives an overview of the consistency semantics 
	included in those macro- models)  
 * besides textual output, Conver generates a visualization of the executions, 
	highlighting the operations that caused violations of consistency models.  

To do:

 * integrate and test some datastores
 * ...


## Getting started

Once installed JDK and Scala, to build Conver issue:

    $ make

...  


Similar projects: [Jepsen][jepsen], [Hermitage][hermitage].
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
