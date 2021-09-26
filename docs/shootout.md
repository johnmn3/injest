## Parallel Transducing Context Shootout: `|>>` vs `=>>`

Welcome to the parallel transducer context shootout!

Here you'll find comparative benchmarks between `|>>` _('pipeline-thread-last')_ and `=>>` _('fold-thread-last')_.

You can learn more about these `injest` macros in the [readme](https://github.com/johnmn3/injest/blob/main/README.md).

In this comparative analysis, we explore a few different scenarios on both a 4 core machine and a 16 core machine.

First, let's define some testing functions:

```clojure
(require '[clojure.edn :as edn])

(defn work-1000 [work-fn]
  (range (last (repeatedly 1000 work-fn))))

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

;; and one extra macro for returning a value for the number of seconds passed:

(defmacro time-val [& body]
  `(x>> (time ~@body)
        with-out-str
        (drop 15)
        reverse
        (drop 8)
        reverse
        (apply str)
        edn/read-string
        (* 0.001)))
```

You may recognize those test functions from the [readme](https://github.com/johnmn3/injest/blob/main/README.md). Now let's exercise them:

```clojure
(dotimes [i 50]
  (println
   (=>> (range 100)
        (repeat i)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        time-val)))
;; and
(dotimes [i 50]
  (println
   (|>> (range 100)
        (repeat i)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        (map x>>work)
        time-val)))
```

With 4 cores:

<img width="600" alt="Screen Shot 1" src="https://user-images.githubusercontent.com/127271/134777757-84d53baf-839e-416d-8781-9bc40886669c.png">

With 16 cores:

<img width="600" alt="Screen Shot 2" src="https://user-images.githubusercontent.com/127271/134777623-3fe45739-ee97-4d0f-b940-b6aebf170481.png">

In the above example, all we're doing is increasing sequence size while keeping the workload the same, so `|>>` and `=>>` are tracking pretty closely to one another.

If we want to measure different workloads, we'll need to get a little fancier with our testing functions.

```clojure
(defn work [n]
  (time-val
   (->> (range n)
        (mapv (fn [_]
                (x>> (range n)
                     (map inc)
                     (filter odd?)
                     (mapcat #(do [% (dec %)]))
                     (partition-by #(= 0 (mod % 5)))
                     (map (partial apply +))
                     (map (partial + 10))
                     (map #(do {:temp-value %}))
                     (map :temp-value)
                     (filter even?)
                     (apply +)))))))

(defn run-|>> [l w]
  (|>> (range l)
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))))

(defn run-=>> [l w]
  (=>> (range l)
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))))
```
We start with a `work` function that becomes increasingly more expensive as `n` rises. We then define run functions `run-|>>` and `run-=>>` that take a sequence length `l` and a work width `w`. Each run function exercises the work function 16 times. This way, we can get a sense of how sequence size vs workload size affects performance characteristics.

Let's look at a "medium" sized work load:
```clojure
(dotimes [n 10]
  (println (time-val (last (run-|>> 100 (* n 100))))))
;; and 
(dotimes [n 10]
  (println (time-val (last (run-=>> 100 (* n 100))))))
```
Here, we're saying `100 (* n 100)` is a sequence 100 elements long, where `n` increases by 100 on each step. Let's see how they compare.

On 4 cores:

<img width="600" alt="Screen Shot 3" src="https://user-images.githubusercontent.com/127271/134776775-2bdf7e53-db57-497b-a74a-ec75c6635de9.png">

On 16 cores:

<img width="600" alt="Screen Shot 4" src="https://user-images.githubusercontent.com/127271/134776819-d91f5db4-5ea0-4239-a658-01e99f23be39.png">

In this example, the `|>>` pipeline thread does a little better in the high core count scenario. In the low core count scenario, they're almost identical.

Now let's try a small, constant size workload with an increasingly larger sequence:
```clojure
(dotimes [n 10]
  (println (time-val (last (run-|>> (* n 1000) 10)))))
;; and 
(dotimes [n 10]
  (println (time-val (last (run-=>> (* n 1000) 10)))))
```

On 4 cores:

<img width="600" alt="Screen Shot 5" src="https://user-images.githubusercontent.com/127271/134776902-8854c7c9-9a40-4a03-b180-a1c2e957c3e7.png">

On 16 cores:

<img width="600" alt="Screen Shot 6" src="https://user-images.githubusercontent.com/127271/134776977-a1299ce0-701a-405f-b3dd-2d9e47a696ef.png">

Much to my surprise, `|>>` won out with this particular workload on both 4 and 16 cores.

How far can we take that? Let's try it with a really big sequence and a really small workload:
```clojure
(dotimes [n 10]
  (println (time-val (last (run-|>> (* n 10000) 1)))))
;; and
(dotimes [n 10]
  (println (time-val (last (run-=>> (* n 10000) 1)))))
```

On 4 cores:

<img width="600" alt="Screen Shot 7" src="https://user-images.githubusercontent.com/127271/134777081-a8d7de22-984e-4ec8-8e4a-687d701c8230.png">

On 16 cores:

<img width="600" alt="Screen Shot 8" src="https://user-images.githubusercontent.com/127271/134777118-0cb1711c-1245-4f4b-af4e-50b76252d7a6.png">

On both core counts, `=>>` wins out slightly. Here, we can see that `|>>` starts to fall behind when threads are not optimized for heavy workloads.

What about the opposite scenario? Let's try a small, constant size sequence with an increasingly, extremely large workload per item:

```clojure
(dotimes [n 4]
  (println (time-val (last (run-|>> 10 (* n 1000))))))
;; and
(dotimes [n 4]
  (println (time-val (last (run-=>> 10 (* n 1000))))))
```
We're only doing 4 runs here because the results take a while.

On 4 cores:

<img width="600" alt="Screen Shot 9" src="https://user-images.githubusercontent.com/127271/134777203-ff4464f1-f937-4bba-a1c0-5ccbd04515fc.png">

On 16 cores:

<img width="600" alt="Screen Shot 10" src="https://user-images.githubusercontent.com/127271/134777258-7bc139cd-6cee-417a-a592-ec13378ad031.png">

As you can see, this is where `|>>` really shines: With super heavy work and a very high core count, `pipeline` starts to show significant efficiencies.

Given these characteristics, one might ask, _"Why not always use `|>>` then?"_

Unfortunately, `|>>` falls over with extremely large sequences with small, heterogeneous workloads. `injest` is designed to allow users to mix and match threads with transformation functions that are fully lazy, transducable and/or parallelizable. Under the hood, this sometimes involves passing some results to a `sequence` operation, then to a `pipeline` operation, then to a lazy `(apply foo)` operation, etc. I believe that in these heterogeneous workload scenarios, the thread communications for `|>>` is causing a traffic jam. Still under investigation though.

For example, let's look at this test scenario:
```clojure
(dotimes [n 10]
  (|>> (range (* n 100000))
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
       time-val
       println))
;; and
(dotimes [n 10]
  (=>> (range (* n 100000))
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
       time-val
       println))
```
On 4 cores:

> todo

On 16 cores:

<img width="600" alt="Screen Shot 12" src="https://user-images.githubusercontent.com/127271/134779298-bf407a3d-9e00-4d6d-af5c-16fac5650e49.png">

And that issue only compounds as the sequence size rises.

So, let's be honest: at least half of the sequence transformation threads that we usually build with `->>` in Clojure are _not_ homogenous, heavily loaded threads. So, if a given thread is only _just starting_ to seem like it could benefit from parallelization, then it's a good chance that `|>>` will be a footgun for you, while `=>>` may pay dividends - so in general I recommend reaching for `=>>` first. However, once your threads' workloads starts to become _embarrasingly parallel_, then it makes sense to try out `|>>`, to see if it can get you even farther - especially with more available cores.

I know, you're wondering, what do these tests look like against the single threaded transducing `x>>` and classical, lazy `->>` macros?

Let's add a test case for that:
```clojure
(defn lazy-work [n]
  (time-val
   (->> (range n)
        (mapv (fn [_]
                (->> (range n)
                     (map inc)
                     (filter odd?)
                     (mapcat #(do [% (dec %)]))
                     (partition-by #(= 0 (mod % 5)))
                     (map (partial apply +))
                     (map (partial + 10))
                     (map #(do {:temp-value %}))
                     (map :temp-value)
                     (filter even?)
                     (apply +)))))))

(defn run-x>> [l w]
  (x>> (range l)
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))
       (map (fn [_] (work w)))))

(defn run-->> [l w]
  (->> (range l)
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))
       (map (fn [_] (lazy-work w)))))
```
Now, looking at our "medium" sized work load above:
```clojure
(dotimes [n 10]
  (println (time-val (last (run-x>> 100 (* n 100))))))
;; and 
(dotimes [n 10]
  (println (time-val (last (run-->> 100 (* n 100))))))
```
And adding those to our times, we get:

On 4 cores:

<img width="600" alt="Screen Shot 13" src="https://user-images.githubusercontent.com/127271/134789493-d80f346e-0236-4598-81d9-a6e1fc3b9519.png">

On 16 cores:

<img width="600" alt="Screen Shot 14" src="https://user-images.githubusercontent.com/127271/134789521-43138f38-b856-42c9-a3ad-2482a8d55c4b.png">

As you can see, it would have taken a _very_ long time for the lazy version to ever finish all ten iterations.
