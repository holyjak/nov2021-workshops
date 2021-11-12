(ns inferme-tutorials.intro
  (:require [fastmath.core :as math]
            [fastmath.random :as random]
            [fastmath.stats :as stats]
            [inferme.core :refer :all]
            [inferme-tutorials.plot :as plot]
            [inferme.plot]
            [scicloj.notespace.v4.api :as notespace]
            [scicloj.notespace.v4.view :as notespace.view]
            [scicloj.notespace.v4.config :as notespace.config]
            [scicloj.kindly.api :as kindly]
            [scicloj.kindly.kind :as kind]
            [fitdistr.core :as fitdistr]
            [fitdistr.distributions]))

;; # Intro to probabilistic modelling with Inferme

;; ## Setup

;; Clone [Inferme](github.com/generateme/inferme/) and install it locally by `lein install`.

;; Notespace setup:
(defonce notespace-started
  (notespace/restart!))

^kind/hidden
(comment
  ;; Customize and troubleshoot Notespace (WIP):
  (notespace/restart-events!)
  (notespace.config/set! {:notes?     true
                          :header?    true
                          :last-eval? true})
  (notespace.config/set! {:header?    true
                          :last-eval? true}))

;; Adapt Inferme's plotting to Notespace:

(intern 'cljplot.core 'show :buffer)

;; ## Background


;; ### The Bayesian inference landscape in Clojure

;; * [anglican](https://probprog.github.io/anglican/index.html)

;; * [bayadera](https://github.com/uncomplicate/bayadera)

;; * [distributions](https://github.com/michaellindon/distributions)

;; * [metaprob](https://github.com/probcomp/metaprob)

;; * [inferme](https://github.com/generateme/inferme)

;; * [daphne](https://github.com/plai-group/daphne)

;; ### Libraries used in this session

;; * [inferme](https://github.com/generateme/inferme)

;; * [fastmath](https://github.com/generateme/fastmath)

;; * [fitdistr](https://github.com/generateme/fitdistr)

;; * [cljplot](https://github.com/generateme/cljplot)

;; ### (pseudo) random numbers with Fathmath

(random/frand)

(repeatedly 3 random/frand)

(let [rng (random/rng :isaac 1337)]
  (random/frandom rng))

(repeatedly 3
            #(let [rng (random/rng :isaac 1337)]
               (random/frandom rng)))

;; ## Basic notions

;; ### Probability space

(def N 1000)

(def Omega (shuffle (range N)))

;; ### Events

(def Odd
  (->> Omega
       (filter (fn [omega]
                 (< (* 3 omega) N)))))

;; ### Probability

(defn P [A]
  (-> A
      count
      float
      (/ N)))

(P Odd)

;; ### Random variables

(defn X [omega]
  (* omega omega))

(->> Omega
     (map X)
     (take 3))

(defn Y [omega]
  (let [rng (random/rng :isaac omega)]
    (random/frandom rng)))

(->> Omega
     (map Y)
     (take 3))

(defn Z [omega]
  (let [rng (random/rng :isaac omega)]
    (random/grandom rng)))

(->> Omega
     (map Z)
     (take 3))

;; ### Events of a random variable

(def Y-is-less-than-half
  (->> Omega
       (filter (fn [omega]
                 (< (Y omega) 1/2)))
       set))

(P Y-is-less-than-half)

;; ### Distributions of random variables

(->> Omega
     (map X)
     plot/histogram)

(->> Omega
     (map Y)
     plot/histogram)

(->> Omega
     (map (comp math/sq Y))
     plot/histogram)

(->> Omega
     (map Z)
     plot/histogram)

;; ### Conditional probability

(def Y-is-more-than-third
  (->> Omega
       (filter (fn [omega]
                 (> (Y omega) 1/3)))
       set))

(/ (P (filter Y-is-less-than-half Y-is-more-than-third))
   (P Y-is-less-than-half))

;; ### Conditional distribution

(->> Omega
     (filter Y-is-less-than-half)
     (map Y)
     plot/histogram)

(->> Omega
     (filter Y-is-less-than-half)
     (map (comp math/sq Y))
     plot/histogram)

;; ## Modeling with Inferme

(defmodel model1
  [Y (:uniform-real)]
  (model-result [(condition (< Y 1/2))]
                {:Y2 (math/sq Y)}))

(def results1
  (delay
    (infer :rejection-sampling model1)))

(-> @results1
    (trace :Y)
    plot/histogram
    delay)

(-> @results1
    (trace :Y2)
    plot/histogram
    delay)

(-> @results1
    (trace :Y)
    (->> (map (fn [y] (> y 1/3))))
    frequencies
    delay)

;; ## Binomial distribution

(defmodel binomial-model-1
  []
  (let [coin (distr :bernoulli {:p 0.5})
        flips (repeatedly 3 #(random/sample coin))
        total (reduce + flips)]
    (model-result []
                  {:total total})))

(-> (infer :forward-sampling binomial-model-1 {:samples 10000})
    (trace :total)
    frequencies
    delay)


(defmodel binomial-model-2
  []
  (let [total (random/sample (distr :binomial {:trials 3
                                               :p      0.5}))]
    (model-result []
                  {:total total})))


(-> (infer :forward-sampling binomial-model-2 {:samples 10000})
    (trace :total)
    frequencies
    delay)

;; ## Getting to know a coin -- a Bayesian approach

(def coin1-trials 5)
(def coin1-heads 2)
(def coin1-tails (- coin1-trials coin1-heads))

(defmodel coin-model-1
  [p (:uniform-real)]
  (let [coin-flipping (distr :binomial {:trials coin1-trials
                                        :p p})]
    (model-result [(observe1 coin-flipping coin1-heads)])))

(def coin-model-1-result
  (-> (infer :metropolis-hastings coin-model-1 {:samples 10000
                                                :thin 100
                                                :steps [0.2]})
      delay))

(-> @coin-model-1-result
    :acceptance-ratio)

(-> @coin-model-1-result
    (trace :p)
    plot/lag
    delay)

(-> @coin-model-1-result
    (trace :p)
    plot/histogram
    delay)

(-> @coin-model-1-result
    (trace :p)
    stats/mean
    delay)

(-> @coin-model-1-result
    (trace :p)
    (->> (fitdistr/fit :mle :beta))
    delay)

(defmodel coin-model-2
  [p (:beta {:alpha 10
             :beta 10})]
  (let [coin-flipping (distr :binomial {:trials coin1-trials
                                        :p      p})]
    (model-result [(observe1 coin-flipping coin1-heads)])))

(def coin-model-2-result
  (-> (infer :metropolis-hastings coin-model-2 {:samples 10000
                                                :thin 100
                                                :steps [0.2]})
      delay))

(-> @coin-model-2-result
    :acceptance-ratio)

(-> @coin-model-2-result
    (trace :p)
    plot/lag
    delay)

(defmodel coin-model-3
  [p (:beta {:alpha (+ 10 coin1-heads)
             :beta  (+ 10 coin1-tails)})]
  (model-result []))

(def coin-model-3-result
  (-> (infer :metropolis-hastings coin-model-2 {:samples 10000})
      delay))


^kind/hiccup
[:div
 [:p "by sampling"]
 (-> @coin-model-2-result
     (trace :p)
     (concat [0 1])
     (plot/histogram {:height 200})
     kindly/to-hiccup)
 [:p "by conjugate"]
 (-> @coin-model-3-result
     (trace :p)
     (concat [0 1])
     (plot/histogram {:height 200})
     kindly/to-hiccup)]


