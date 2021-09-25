# `injest`: `+>` `+>>` `x>>` `=>>`
Clojure's [threading macros](https://clojure.org/guides/threading_macros) (the `->` and `->>` [thrushes](http://blog.fogus.me/2010/09/28/thrush-in-clojure-redux/)) are great for navigating into data and transforming sequences. `injest`'s [_path thread_](#path-threads) macros `+>` and `+>>` are just like `->` and `->>` but with expanded path navigating abilities similar to `get-in`.

[Transducers](https://clojure.org/reference/transducers) are great for performing sequence transformations efficiently. `x>>` combines the efficiency of transducers with the better ergonomics of `+>>`. Thread performance can be further extended by automatically parallelizing work with `=>>`.

`injest` macros achieve this by scanning forms for contiguous groups of transducers and `comp`ing them together into a function that either `sequence`s or parallel `fold`s the values flowing in the thread through the transducers.
## Getting Started
Place the following in the `:deps` map of your `deps.edn` file:
```clojure
  ...
  net.clojars.john/injest {:mvn/version "0.1.0-alpha.13"}
  ...
```
To try it in a repl right now with `criterium` and `net.cgrand.xforms`, drop this in your shell:
```clojure
clj -Sdeps \
    '{:deps 
      {net.clojars.john/injest {:mvn/version "0.1.0-alpha.13"}
       criterium/criterium {:mvn/version "0.4.6"}
       net.cgrand/xforms {:mvn/version "0.19.2"}}}'
```
Then require the `injest` macros in your project:
```clojure
(ns ...
  (:require [injest.path :refer [+> +>> x>> =>>]]
   ...
```
To just use `x>>` or `=>>` with the classical thread behavior, without the additional [_path thread_](#path-threads) semantics, you can require in the `injest.classical` namespace instead of the `injest.path` namespace:
```clojure
(ns ...
  (:require [injest.classical :refer [x>> =>>]]
   ...
```
Having these two `:require` options allows individuals and organizations to adopt a la carte these orthogonal value propositions of _improved performance_ and _improved navigation_.
# Path Threads
`injest.path` allows for more intuitive path navigation, like you're used to with the `(-> m :a :b :c)` idiom. We refer to these as _path threads_.

Ergonomically, path threads provide a semantic superset of the behaviors found in `->` and `->>`. In other words, there is generally nothing you can do with `->` that you can't do with `+>`. All the thread macros in `injest.path` have these path thread semantics.
## As a replacement for `get-in`, `get` and `nth`
In path threads, naked integers and strings become lookups on the value being passed in, making those tokens useful again in threads. You can index into sequences with integers, like you would with `nth`, and replace `get`/`get-in` for most cases involving access in heterogeneous map nestings:
```clojure
(let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :res}]}])}]
  (+> m 1 2 "b" 2 :c name)) ;=> "res"
```
Here, we're looking up `1` in the map, then getting the third element of the sequence returned, then looking up `"b"` in the returned map, then getting the third element of the returned vector, then looking up `:c` in the returned map, and then finally calling name on the returned keyword value.

In the above form, you could replace `+>` with either `+>>`, `x>>` or `=>>`, and you will still get the same result. `+>>` is simply the thread-last version of `+>` and `x>>` and `=>>` are transducing and parallel versions of `+>>`.
## Lambda wrapping
Path threads allow you to thread values through anonymous functions, like `#(- 10 % 1)` or `(fn [x] (- 10 x 1))`, without having to wrap them in an extra enclosing set of parenthesis:
```clojure
(x> 10 range rest 2 #(- 10 % 1)) ;=> 6
```
Or, extending our prior example:
```clojure
(let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :bob}]}])}]
  (x>> m 1 2 "b" 2 :c name #(println "hi " % "!"))) ;=> "hi bob!"
```
This has the added benefit of conveying to the reader that the author intends for the anonymous function to only take one parameter. In the classical thread syntax, the reader would have to scan all the way to the end of `(#(... ` in order to know if an extra parameter is being passed in. This also prevents people from creating unmaintainable abstractions involving the threading of values into a literal lambda definition - a [common](https://stackoverflow.com/questions/7838326/function-call-in-threading-macro) [source](https://stackoverflow.com/questions/25317235/thread-first-array-map-literal-to-anonymous-function-in-clojure) [of](https://stackoverflow.com/questions/29897115/clojure-threading-first-macro-with-math-pow-or-any-other-multiple-args-functi) [errors](https://stackoverflow.com/questions/60027298/clojure-custom-function-for-threading-macro).
## Backwards compatability
`+>` and `+>>` have the same laziness semantics as `->` and `->>`. So, if you find yourself wanting to migrate a _path thread_ away from a transducer/parallel context, back to the more lazy semantics, but you want to keep the path navigation semantics, you can simply replace the `x>>` or `=>>` macros with the `+>>` macro we required in above. Path navigating will continue to work:
```clojure
(let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :bob}]}])}]
  (+>> m 1 2 "b" 2 :c name #(println "hi " % "!"))) ;=> "hi bob!"
```
You can also just use `+>` and `+>>` on their own, without the transducifying macros, if you only want the more convenient ergonomics.

As stated above, you can also require `x>>` and `=>>` in from `injest.classical` and, in the event you want to revert back to `->>`, you will be able to do that knowing that no one has added any _path thread_ semantics to the thread that would also need to be converted to the classical syntax.
# `x>>` Auto Transducification
Why? Well, for one, speed. Observe:
```clojure
(->> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
    ;;  (mapv dec)
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
```
Returns:
```clojure
"Elapsed time: 8275.319295 msecs"
5000054999994
```
Whereas:
```clojure
(x>> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
    ;;  (mapv dec)
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
```
Returns:
```clojure
"Elapsed time: 2913.851103 msecs"
5000054999994
```

Two to three times the speed with basically the same code. The more transducers you can get lined up contiguously, the less boxing you’ll have in your thread. 

> Note: These times reflect the execution environment provided by Github's browser-based vscode runtime. My local box performs much better and yours likely will too.

Let’s uncomment the `(mapv dec)` that is currently commented out in both the threads above. Because `mapv` is not a transducer, items get boxed halfway through our thread. As a result our performance degrades slightly for `x>>`.

First, let's see it with `->>`:
```clojure
(->> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (mapv dec)
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
"Elapsed time: 6947.00928 msecs"
44999977000016
```
Hmm, `->>` actually goes faster now, perhaps due to `mapv` removing some laziness. The more lazy semantics are less predictable in that way.

But now, for `x>>`:
```clojure
(x>> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (mapv dec)
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
"Elapsed time: 3706.701192 msecs"
44999977000016
```
So we lost some speed due to the boxing, but we’re still doing a worthy bit better than the default thread macro. So keep in mind, if you want to maximize performance, try to align your transducers contiguously.

> Note: In addition to improved speed, transducers also provide improved memory efficiency over finite sequences. So `x>>` may lower your memory usage as well.
## `=>>` Auto Parallelization
`injest` provides a parallel version of `x>>` as well. `=>>` leverages Clojure's parallel [`fold`](https://clojuredocs.org/clojure.core.reducers/fold) [reducer](https://clojure.org/reference/reducers#_using_reducers) in order to execute contiguous _or singular_ stateless transducers over a [Fork/Join pool](http://gee.cs.oswego.edu/dl/papers/fj.pdf). Remaining contiguous (non-singular) stateful transducers are `comp`ed and threaded just like `x>>`.

`=>>`'s execution groups are partitioned by the length of the sequence passed in divided by 2 plus the number of cores available. For instance, if you have 4 cores and you pass it a sequence of 1000 elements, the execution groups will be 166 elements in length.

It doesn't work well for small workloads though, so for demonstration purposes let's augment our above threads:
```clojure
(require '[clojure.edn :as edn])

(defn work-1000 [work-fn]
  (range (last (repeatedly 1000 work-fn))))

(defn ->>work [input]
  (work-1000
   (fn []
     (->> input
          (map inc)
          (filter odd?)
          (mapcat #(do [% (dec %)]))
          (partition-by #(= 0 (mod % 5)))
          (map (partial apply +))
          (map (partial + 10))
          (map #(do {:temp-value %}))
          (map :temp-value)
          (filter even?)
          (apply +)
          str
          (take 3)
          (apply str)
          edn/read-string))))  

(defn x>>work [input]
  (work-1000
   (fn []
     (x>> input
          (map inc)
          (filter odd?)
          (mapcat #(do [% (dec %)]))
          (partition-by #(= 0 (mod % 5)))
          (map (partial apply +))
          (map (partial + 10))
          (map #(do {:temp-value %}))
          (map :temp-value)
          (filter even?)
          (apply +)
          str
          (take 3)
          (apply str)
          edn/read-string))))
```
Same deal as before but we're just doing a little extra work in our thread, repeating it a thousand times and then preparing the results for handoff to the next stage of execution.

Now let's run the classical `->>` macro:
```clojure
(->> (range 100)
     (repeat 10)
     (map ->>work)
     (map ->>work)
     (map ->>work)
     (map ->>work)
     (map ->>work)
     (map ->>work)
     last
     count
     time)
; "Elapsed time: 18309.397391 msecs"
;=> 234
```
Just over 18 seconds. Now let's try the `x>>` version:
```clojure
(x>> (range 100)
     (repeat 10)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     last
     count
     time)
; "Elapsed time: 6252.224178 msecs"
;=> 234
```
Just over 6 seconds. Much better. Now let's try the parallel `=>>` version:
```clojure
(=>> (range 100)
     (repeat 10)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     last
     count
     time)
; "Elapsed time: 3142.75057 msecs"
;=> 234
```
Just over 3 seconds. Much, much better!

Again, in local dev your times may look a bit different. On my Macbook Pro here, those times are `11812.604504`, `5096.267348` and `933.940569` msecs. So, in other words, `=>>` can sometimes be 5 times faster than `x>>` and 10 times faster than `->>`, depending on the shape of your workloads and the number of cores you have available.
### `|>>` Parallel Pipeline
`injest` also provides `|>>` - a parallel, transducing thread macro based on Clojure's [`pipeline`](https://clojuredocs.org/clojure.core.async/pipeline). In general, `=>>` should be preferred for most workloads, but `|>>` is available for edge cases where it is more efficient.

Instead of dividing work into execution groups, a parallelization value of 2 plus the number of available cores are passed to `pipeline` and `core.async` manages everything else under the hood. The thread-overhead costs for `|>>` are different than `=>>` though, so only use it on sequences with heavy workloads.
```clojure
(|>> (range 100)
     (repeat 10)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     (map x>>work)
     last
     count
     time)
; "Elapsed time: 3057.507032 msecs"
;|> 234
```
`|>>` actually beat out `=>>` here, but `=>>` usually wins - your milage may vary. Whatever you do, don't use `|>>` on massive sequences with very small workloads on each item. This causes a traffic jam:
```clojure
;; don't run this
(|>> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
;; takes 3 minutes :/
```
Whereas `=>>` will complete in about 10 seconds. Worse than `x>>` for the same sequence and workload, but at least it's within the ballpark of usability. And `=>>` just has better execution semantics when used in chains with other transducers. So use `|>>` with caution and lots of repl'ing.
## Clojurescript
In Clojurescript we don't yet have parallel thread macro implementations but for `x>>` the performance gains are even more pronounced than in Clojure. On my macbook pro, with an initial value of `(range 1000000)` in the above thread from our first example, the default threading macro `->>` produces:
```clojure
(->> (range 1000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
"Elapsed time: 3523.186678 msecs"
50005499994
```
While the `x>>` version produces:
```clojure
(x>> (range 1000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (apply +)
     time)
"Elapsed time: 574.145888 msecs"
50005499994
```
That's a _six times_ speedup!

Perhaps that speedup would not be so large if we tested both versions in _advanced_ compile mode. Then the difference in speed might come closer to the Clojure version. In any case, this is some very low-hanging performance fruit.
## Extending `injest`
The `injest.state` namespaces provides the `reg-xf!` and `reg-pxf!` macros that can take one or more transducers. Only stateless transducers (or, more precisely, transducers that can be used safely within a parallel `fold` or `pipeline` context) should be registered with `reg-pxf!`. `injest`'s thread macros will then include those functions when deciding which forms should be treated as transducers. You should only need to call `reg-xf!` in one of your initially loaded namesapces.
```clojure
(require '[injest.state :as i.s])
(require '[net.cgrand.xforms :as x])

(i.s/reg-xf! x/reduce)

(x>> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
     (map (partial + 10))
     (map #(do {:temp-value %}))
     (map :temp-value)
     (filter even?)
     (x/reduce +)
     first
     time)
```
Even better!
```clojure
"Elapsed time: 2889.771067 msecs"
5000054999994
```
In Clojurescript, you can add another Clojure (`*.clj`) namespace to your project and register there with the `regxf!` function and expclicitly namespaced symbols.
```clojure
(i.s/regxf! 'net.cgrand.xforms/reduce)
```
Or, if a transducer library like `net.cgrand.xforms` exports the same namespaces and names for both Clojure and Clojurescript, you can just `(i.s/reg-xf! x/reduce)` in a Clojure namespace in your project and then it will be available to the `x>>` threads in both your Clojure and Clojurescript namespaces.
# Caveats
It should be noted as well:

* Because transducers have different laziness semantics, you can't be as liberal with your consumption, so test on live data before using this as a drop-in replacement for the default thread macros.

If you have any problems, feature requests or ideas, feel free to drop a note in the issues or discuss it in the clojureverse [thread](https://clojureverse.org/t/x-x-auto-transducifying-thread-macros/8122/9).
# References
Some other perfomance-related investigations you may be interested in:
* [cgrand/xforms](https://github.com/cgrand/xforms) - More transducers and reducing functions for Clojure(script)!
* [clj-fast](https://github.com/bsless/clj-fast) - optimized core functions
* [structural](https://github.com/joinr/structural) - efficient destructuring

Inspiration for the lambda wrapping came from this ask.clojure request: [should-the-threading-macros-handle-lambdas](https://ask.clojure.org/index.php/9023/should-the-threading-macros-handle-lambdas)

Inspiration for the `fold` implementation of `=>>` came from [reborg/parallel](https://github.com/reborg/parallel#ptransduce)'s `p/transduce`
# License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
