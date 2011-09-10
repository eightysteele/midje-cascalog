(ns midje.cascalog
  (:use midje.sweet
        [cascalog.testing :only (process?-)]))

(def mocking-forms #{'against-background 'provided})
(def checker-forms #{'contains 'just})

(defn clause-from-set? [set x]
  (when (coll? x)
    (set (first x))))

(def ^{:doc "Returns true if the supplied form (or sequence) is a
  midje `provided` or `against-background` clause, false otherwise."}
  mocking-form?
  (partial clause-from-set? mocking-forms))

(def ^{:doc "Returns true if the supplied form (or sequence) is a midje
  collection checker form, false otherwise."}
  checker-form?
  (partial clause-from-set? checker-forms))

(defn extract-mockers
  "Returns a vector of two sequences, obtained by splitting the
  supplied `coll` into midje forms and rest."
  [coll]
  ((juxt filter remove) mocking-form? coll))

(defn fact-line
  "Returns a syntax-quoted list representing the guts of a midje fact
  for the supplied cascalog query and result.

  Note that this fact will check that all tuples inside of `result`
  are generated by the supplied query, in any order. Log Level "
  [result query ll]
  (let [process #(first (second (apply process?- %&)))
        [result checker] (if (checker-form? result)
                           [(second result) `~result]
                           [result (list `just result :in-any-order)])]
    `((~process ~@(when ll [ll]) ~result ~query) => ~checker)))

(defn- build-fact?-
  "Accepts a sequence of fact?- bindings and a midje \"factor\" --
  `fact`, or `future-fact`, for example -- and returns a syntax-quoted
  version of the sequence with all result-query pairs replaced with
  corresponding midje fact-result pairs. For example:

  (build-fact?- '(\"string\" [[1]] (<- [[?a]] ([[1]] ?a))) `fact)
   ;=> (fact <results-of-query> => (just [[1]] :in-any-order)"
  [bindings factor]
  (let [[ll & more] bindings
        [ll bindings] (if (keyword? ll)
                        [ll more]
                        [nil bindings])]
    `(~factor
      ~@(loop [[x y & more :as forms] bindings, res []]
          (cond (not x) res
                (or (string? x)
                    (mocking-form? x)) (recur (rest forms) (conj (vec res) x))
                :else (->> (fact-line x y ll)
                           (concat res)
                           (recur more)))))))

(defn- build-fact?<-
  "Similar to `build-fact?-`; args must contain a result sequence, a
  query return arg vector, and any number of predicates. The last
  forms can be midje provided or background clauses."
  [args factor]
  (let [[ll :as args] (remove string? args)
        [begin args] (if (keyword? ll)
                       (split-at 2 args)
                       (split-at 1 args))
        [m body] (extract-mockers args)]
    `(~factor ~@begin (cascalog.api/<- ~@body) ~@m)))

(defmacro fact?- [& bindings] (build-fact?- bindings `fact))
(defmacro fact?<- [& args] (build-fact?<- args `fact?-))

(defmacro future-fact?- [& bindings] (build-fact?- bindings `future-fact))
(defmacro future-fact?<- [& args] (build-fact?<- args `future-fact?-))

(defmacro pending-fact?- [& bindings] (build-fact?- bindings `pending-fact))
(defmacro pending-fact?<- [& args] (build-fact?<- args `pending-fact?-))