# `+>` - `+>>` - `x>` - `x>>` - `=>` - `=>>`
## `injest`: Path-navigating, auto-transducifying, parallelizing thread macros
This library makes it easier to use Clojure's most powerful features.

`injest`'s thread macros scan forms for contiguous groups of transducers and comps them together into a function that sequences or parallel-pipeline's the values flowing in the thread through the transducers.
## Getting Started
Place the following in the `:deps` map of your `deps.edn` file:
```clojure
  ...
  io.github.johnmn3/injest  {:git/tag "v0.1-alpha.4" :git/sha "ddf8a6e"}
  ...
```
To try it in a repl right now, drop this in your shell:
```clojure
clj -Sdeps \
    '{:deps 
      {io.github.johnmn3/injest 
       {:git/tag "v0.1-alpha.4" 
        :git/sha "ddf8a6e"}}}'
```
Then require the `injest` macros in your project:
```clojure
(ns ...
  (:require [injest.core :as injest :refer [x> x>> => =>>]]
   ...
```
For even _more_ modern conveniences, I recommend requiring in the `injest.path` namespace instead of the `injest.core` namespace, described [in more details below](#extras).
```clojure
(ns ...
  (:require [injest.path :as injest :refer [x> x>> +> +>> => =>>]]
   ...
```
The `injest.path` namespace provides `x>`, `x>>`, `=>` and `=>>` with the additional _'path thread'_ semantics of `+>` and `+>>`, described below. However, because `injest.path` provides an extra value proposition, this library won't be imposing those conveniences on the default transducifyng semantics. As such, Clojure shop's with different appetite's for semantic advancements can adopt these extra features more gradually, if at all.
# Details
Why? Well:

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

Two to three times the speed with basically the same code.

The more transducers you can get lined up contiguously, the less boxing you’ll have in your thread. Let’s uncomment the `(mapv dec)` that is present in both the threads above. Because `mapv` is not a transducer, items get boxed halfway through our thread. As a result our performance degrades slightly for `x>>`.

First, let's see it with `->>`:
```clojure
"Elapsed time: 6947.00928 msecs"
44999977000016
```
Hmm, `->>` actually goes faster now, perhaps due to `mapv` removing some laziness.

But now, for `x>>`:
```clojure
"Elapsed time: 3706.701192 msecs"
44999977000016
```
So we lost some speed due to the boxing, but we’re still doing a worthy bit better than the default thread macro. Point is, if you want to maximize performance, try to align your transducers contiguously.

Note that `x>` is different than `->` in that if a transducer is passed in, it appears as if it is a thread-last on that transducer form.

## Parallel `=>` & `=>>`
`injest` also provides parallel versions of `x>` and `x>>`. Instead of using `sequence` on the thread, `=>` and `=>>` leverage `core.async`'s parallel `pipeline` in order to execute the transducers over a pool of threads equal to `(+ 2 your-number-of-cores)`. It doesn't work well for small data payloads though, so for demonstration purposes let's augment our above threads:
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
; "Elapsed time: 2862.172838 msecs"
;=> 234
```
Under 3 seconds. Much, much better!

In the future I'd like to explore using parallel `folder` instead of `core.async` but this works pretty well.
## Clojurescript 
In Clojurescript, the performance gains are even more pronounced. On my macbook pro, with an initial value of `(range 1000000)` in the above thread, the default threading macro `->>` produces:
```clojure
"Elapsed time: 3523.186678 msecs"
50005499994
```
While `x>>` version produces:
```clojure
"Elapsed time: 574.145888 msecs"
50005499994
```
That's a _six times_ speedup!

Perhaps that speedup would not be so drastic if we tested both versions in _advanced_ compile mode. Then the difference in speed might come closer to the Clojure version. In any case, this is some very low-hanging performance fruit.
## Extending `injest`
This feature is very experimental, but there is a `reg-xf!` macro that can take one or more transducers. `injest` macros will then include those functions when deciding which forms should be treated as transducers. You should only need to call `reg-xf!` in one of your namespaces - preferably in an initially loaded one.
```clojure
(require '[net.cgrand.xforms :as x])

(injest/reg-xf! x/reduce)

(x>> (range 10000000)
     (map inc)
     (filter odd?)
     (mapcat #(do [% (dec %)]))
     (partition-by #(= 0 (mod % 5)))
     (map (partial apply +))
    ;; (mapv dec)
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
(injest/regxf! 'net.cgrand.xforms/reduce)
```
Or, if a transducer library like `net.cgrand.xforms` exports the same namespaces and names for both Clojure and Clojurescript, you can just `(injest/reg-xf! x/reduce)` in a Clojure namespace in your project and then it will be available to `x>>` threads in both your Clojure and Clojurescript namespaces.
# Extras
`injest` comes with extra features that allow for more intuitive path navigation, like you're used to with the `(-> m :a :b :c)` idiom. We refer to these as _path threads_. Simply require instead from the `injest.path` namespace, like so:
```clojure
(ns ...
  (:require [injest.path :refer [x> x>> +> +>>]]
   ...
```
## As a replacement for `get-in`
In path threads, naked integers, strings, booleans and nils in a thread become lookups on the value threading through, making those tokens useful again in threads. You can index into sequences and replace `get-in` for most cases involving access in heterogeneous nestings:
```clojure
(let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :res}]}])}]
  (x>> m 1 2 "b" 2 :c name)) ;=> "res"
```
Here, we're looking up `1` in the map, then getting the third element of the sequence returned, then looking up `"b"` in the returned map, then getting the third element of the returned vector, then looking up `:c` in the returned map, and then finally calling name on the returned keyword value.

> Note that we are not introducing any new powers of abstraction here, as the new semantics can only apply to the top level tokens of the thread form and do not recurse into the form. We're simply reclaiming for usage some unusable tokens in the already existing thread abstractions. So we don't have to worry about users creating unreadable abstractions with this addition. Also note, this feature does _not prevent_ the creation of any new abstractions that could have been created before this feature's addition, except for the possible callability of strings, numbers, booleans and nils, which are abstractions we'd rather not implement or maintain anyway (though you could still attempt that outside these macros).
## Lambda wrapping
If you'd like to thread values through anonymous functions, like `#(- 10 % 1)` or `(fn [x] (- 10 x 1))`, without having to wrap them in an extra enclosing set of parenthesis, you can do that with path threads too:
```clojure
(x> 10 range rest 2 #(- 10 % 1)) ;=> 6
```
This has the added benefit of conveying to the reader that the author intends for the anonymous function to only take one parameter. In the classical thread syntax, the reader would have to scan all the way to the end of `(#(... ` in order to know if an extra parameter is being passed in. It also prevents people from creating unmaintainable abstractions involving the threading of values into a literal lambda definition, which I would rather not have to maintain.
## Backwards compatability
`+>` and `+>>` have the same laziness semantics as `->` and `+>>`. So, if you find yourself wanting to migrate a _path thread_ away from transducers, back to the more lazy semantics, but you want to keep the path navigation semantics, you can simply replace the `x>` or `x>>` macro with the corresponding `+>` or `+>>` macro we required in above. Path navigating will continue to work:
```clojure
(let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :res}]}])}]
  (+>> m 1 2 "b" 2 :c name)) ;=> "res"
```
You can also just use `+>` and `+>>` on their own, without the transducifying macros, if you only want the more convenient ergonomics.
# Future work
A `px>>` thread macro that automatically parallelizes `folder`able `map`ping (and any other stateless) transducers would be nice. There are also other avenues of optimization [discussed on clojureverse](https://clojureverse.org/t/x-x-auto-transducifying-thread-macros/8122).
# Caveats
It should be noted as well:

1. Because transducers have different laziness semantics, you can't be as liberal with your consumption, so test on live data before using this as a drop-in replacement for the default thread macros.

2. Some stateful transducers may be optimized for single-threaded performance and may not produce expected results in some multi-threaded scenarios… not sure if that applies to these macros, as some context outside the thread would likely be orchestrating mutli-threading, which I don’t think would usually reach into those transducer’s internal state independently… But proceed with caution around super fancy, stateful, parallel transducers for now.

If you have any problems, feature requests or ideas, feel free to drop a note in the issues or discuss it in the clojureverse [thread](https://clojureverse.org/t/x-x-auto-transducifying-thread-macros/8122/9).
# References
Some other perfomance-related investigations you may be interested in:
* [clj-fast](https://github.com/bsless/clj-fast) - optimized core functions
* [structural](https://github.com/joinr/structural) - efficient destructuring

Inspiration for the lambda wrapping came from this ask.clojure request: [should-the-threading-macros-handle-lambdas](https://ask.clojure.org/index.php/9023/should-the-threading-macros-handle-lambdas)
# License

Currently, the source draws heavily from the default `clojure.core`'s `->` and `->>` macros, so we're using the same license here.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
