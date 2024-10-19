# `injest`: `+>` `+>>` `x>>` `=>>`

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.john/injest.svg)](https://clojars.org/net.clojars.john/injest)
[![Cljdoc](https://cljdoc.org/badge/net.clojars.john/injest)](https://cljdoc.org/d/net.clojars.john/injest)
[![project chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://clojurians.zulipchat.com/#streams/302003/injest)

Clojure's [threading macros](https://clojure.org/guides/threading_macros) (the `->` and `->>` [thrushes](http://blog.fogus.me/2010/09/28/thrush-in-clojure-redux/)) are great for navigating into data and transforming sequences. `injest`'s [_path thread_](#path-threads) macros `+>` and `+>>` are just like `->` and `->>` but with expanded path navigating abilities similar to `get-in`.

[Transducers](https://clojure.org/reference/transducers) are great for performing sequence transformations efficiently. `x>>` combines the efficiency of transducers with the better ergonomics of `+>>`. Thread performance can be further extended by automatically parallelizing work with `=>>`.

`injest` macros achieve this by scanning forms for transducers and `comp`ing them together into a function that either `sequence`s or parallel `fold`s the values flowing in the thread through the transducers.

## Getting Started
### deps.edn
Place the following in the `:deps` map of your `deps.edn` file:
```clojure
  ...
  net.clojars.john/injest {:mvn/version "0.1.0-beta.8"}
  ...
```
### clj-kondo
Make clj-kondo/Clojure-LSP aware of `injest` by adding `"net.clojars.john/injest"` to the `:config-paths` vector of your `.clj-kondo/config.edn` file:
```clojure
{:config-paths ["net.clojars.john/injest"]}
```
This will automatically import `injest`'s lint definitions in Calva and other IDE's that leverage clj-kondo and/or Clojure-LSP.

### Quickstart
To try it in a repl right now with `criterium` and `net.cgrand.xforms`, drop this in your shell:
```clojure
clj -Sdeps \
    '{:deps 
      {net.clojars.john/injest {:mvn/version "0.1.0-beta.8"}
       criterium/criterium {:mvn/version "0.4.6"}
       net.cgrand/xforms {:mvn/version "0.19.2"}}}'
```
### Requiring
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

### Available Transducers
| These are the core functions that are available to use as transducers in a `x>>` thread-last: |
| --- |
| `take-nth`, `disj!`, `dissoc!`, `distinct`, `keep-indexed`, `random-sample`, `map-indexed`, `map`, `replace`, `drop`, `remove`, `cat`, `partition-all`, `interpose`, `mapcat`, `dedupe`, `drop-while`, `partition-by`, `take-while`, `take`, `keep`, `filter`, `halt-when` |

## `=>>` Auto Parallelization
`injest` provides a parallel version of `x>>` as well. `=>>` leverages Clojure's parallel [`fold`](https://clojuredocs.org/clojure.core.reducers/fold) [reducer](https://clojure.org/reference/reducers#_using_reducers) in order to execute stateless transducers over a [Fork/Join pool](http://gee.cs.oswego.edu/dl/papers/fj.pdf). Remaining stateful transducers are `comp`ed and threaded just like `x>>`.

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

> There is also a parallel thread macro (`|>>`) that uses `core.async/pipeline` for parallelization. It's still available for folks interested in improving it, but is not recomended for general use. `=>>` performs better in most cases. A soon-to-be-updated analysis ([shootout.md](https://github.com/johnmn3/injest/blob/main/docs/shootout.md)) compares the differences between `|>>` and `=>>`.

### Available Parallel Transducers
| These are the core functions that are available to use as parallel transducers in a `=>>` thread-last: |
| --- |
| `dedupe`, `disj!`, `dissoc!`, `filter`, `keep`, `map`, `random-sample`, `remove`, `replace`, `take-while`, `halt-when`, `mapcat`, `cat` |

## Clojurescript
~In Clojurescript we don't yet have parallel thread macro implementations but for `x>>`~ 

> Update: The parallel (`=>>`) thread macro has been implemented in [`cljs-thread`](https://github.com/johnmn3/cljs-thread?tab=readme-ov-file). We'll get into the Clojurescript version of `=>>` below, but first let's look at the single threaded `x>>`.

The performance gains for `x>>` are even more pronounced than in Clojure. On my macbook pro, with an initial value of `(range 1000000)` in the above thread from our first example, the default threading macro `->>` produces:
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
### `=>>` in Clojurescript
So, suppose you have some non-trivial work:
```clojure
(defn flip [n]
  (apply comp (take n (cycle [inc dec]))))
```
On a single thread, in Chrome, this takes between 16 and 20 seconds (on this computer):
```clojure
(->> (range)
     (map (flip 100))
     (map (flip 100))
     (map (flip 100))
     (take 1000000)
     (apply +)
     time)
```
On Safari and Firefox, that will take between 60 and 70 seconds.

Let's try it with `=>>`:
```clojure
(=>> (range)
     (map (flip 100))
     (map (flip 100))
     (map (flip 100))
     (take 1000000)
     (apply +)
     time)
```
On Chrome, that'll take only about 8 to 10 seconds. On Safari it takes about 30 seconds and in Firefox it takes around 20 seconds.

So in Chrome and Safari, you can roughly double your speed and in Firefox you can go three or more times faster.

By changing only one character, we can double or triple our performance, all while leaving the main thread free to render at 60 frames per second. Notice also how it's lazy :)

See the [`cljs-thread`](https://github.com/johnmn3/cljs-thread) repo to learn more about how to set things up with the web workers.

> Note: On the main/screen thread, `=>>` returns a promise. `=>>` defaults to a chunk size of 512.
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
In Clojurescript, you can add another Clojure (`*.clj`) namespace to your project and register there with the `regxf!` function and explicitly namespaced symbols.
```clojure
(i.s/regxf! 'my.cljs.xforms.library/sliding-window)
```
Or, if a transducer library like `net.cgrand.xforms` exports the same namespaces and names for both Clojure and Clojurescript, you can just `(i.s/reg-xf! x/reduce)` in a Clojure namespace in your project and then it will be available to the `x>>`/`=>>` threads in both your Clojure and Clojurescript namespaces.

## Reporting Instrumentation
You can optionally instrument the `x>>` and `=>>` macros for profiling code in a deployed runtime environment like so:
```clojure
(ns ...
  (:require
   [injest.report :as r]
   [injest.report.path :as injest :refer [+> +>> x>> =>>]]))
```
Then in some core namespace, just register a report handler and then turn it on:
```clojure
(r/add-report-tap! println 60) ;; <- or tap>, log/info, etc
(r/report! true)
```
If you don't provide `add-report-tap!` a second seconds parameter it will default to 10 seconds. The above expressions will handle reporting events with the `println` function, called once every 60 seconds.

Then, in any namespace, be sure to require the macros from the `injest.report.path` namespace:
```clojure
(ns ...
  (:require
   [injest.report.path :as injest :refer [+> +>> x>> =>>]]))
```
Then you can use `x>>` and `=>>` like you normally would, but you will see a report on all instances in the repl:
```clojure
{:namespace "injest.test"
 :line 15
 :column 5
 :x>> "x>> is 1.08 times faster than =>>"
 :=>> "=>> is 2.67 times faster than +>>"}

{:namespace "injest.test"
 :line 38
 :column 3
 :+>> "+>> is 2.5 times faster than x>>"}

{:namespace "injest.test"
 :line 44
 :column 5
 :=>> "=>> is 1.9 times faster than x>>"
 :x>> "x>> is 1.4 times faster than +>>"}

```
As you can see, the first line of a given report result is the namespace, along with `?line=` and the line number and `&col=` and the column number. For the `x>>` variant, only `x>>` and `+>>` are compared. When `=>>` is used, all three of `=>>`, `x>>` and `+>>` are compared.

This allows you to leverage the instrumented versions of the macros in order to assess which one is most appropriate for the runtime load in your actually running application.
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

# Get Involved

Want to implement the `somex>>` macro? Just copy how I did it and feel free to submit a PR. If you see a difficiency, file an issue here or swing by and join the discussion on the [zulip channel](https://clojurians.zulipchat.com/#streams/302003/injest).

# License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
