(ns birdwatch.util
  (:require [clojure.string :as s]
            [birdwatch.state :as state]
            [tailrecursion.priority-map :refer [priority-map-by]]))

(defn by-id [id] (.getElementById js/document id))

(defn search-hash []
  (subs (js/decodeURIComponent (aget js/window "location" "hash")) 1))

(defn number-format
  "formats a number for display, e.g. 1.7K, 122K or 1.5M followers"
  [number]
  (cond
    (< number 1000) (str number)
    (< number 100000) (str (/ (.round js/Math (/ number 100)) 10) "K")
    (< number 1000000) (str (.round js/Math (/ number 1000)) "K")
    :default (str (/ (.round js/Math (/ number 100000)) 10) "M")))

(defn from-now
  "format date using the external moment.js library"
  [date]
  (let [time-string (. (js/moment. date) (fromNow true))]
    (if (= time-string "a few seconds") "just now" time-string)))

(defn- url-replacer
  "replace URL occurences in tweet texts with HTML (including links)"
  [acc entity]
  (s/replace acc (:url entity)
             (str "<a href='" (:url entity) "' target='_blank'>" (:display_url entity) "</a>")))

(defn- hashtags-replacer
  "replace hashtags in tweet text with HTML (including links)"
  [acc entity]
  (let [hashtag (:text entity)]
    (s/replace acc (str "#" hashtag)
                         (str "<a href='https://twitter.com/search?q=%23" hashtag "' target='_blank'>#" hashtag "</a>"))))

(defn- mentions-replacer
  "replace user mentions in tweet text with HTML (including links)"
  [acc entity]
  (let [screen-name (:screen_name entity)]
    (s/replace acc (str "@" screen-name)
               (str "<a href='http://www.twitter.com/" screen-name "' target='_blank'>@" screen-name "</a>"))))

(defn- reducer
  "generic reducer, allowing to call specified function for each item in collection"
  [text coll fun]
  (reduce fun text coll))

(defn format-tweet
  "format tweet text for display"
  [tweet]
  (let [{:keys [urls media user_mentions hashtags]} (:entities tweet)]
    (assoc tweet :html-text
      (-> (:text tweet)
          (reducer , urls url-replacer)
          (reducer , media url-replacer)
          (reducer , user_mentions mentions-replacer)
          (reducer , hashtags hashtags-replacer)
          (s/replace , "RT " "<strong>RT </strong>")))))

(defn entity-count
  "gets count of specified entity from either tweet, or, when exists, original (retweeted) tweet"
  [tweet sym s]
  (let [rt-id (if (contains? tweet :retweeted_status) (:id_str (:retweeted_status tweet)) (:id_str tweet))
        count (sym ((keyword rt-id) (:tweets-map @state/app)))]
    (if (not (nil? count)) (str (number-format count) s) "")))

(defn rt-count [tweet] (entity-count tweet :retweet_count " RT | "))
(defn fav-count [tweet] (entity-count tweet :favorite_count " fav"))

(defn rt-count-since-startup
  "gets RT count since startup for tweet, if exists returns formatted string"
  [tweet]
  (let [t (if (contains? tweet :retweeted_status) (:retweeted_status tweet) tweet)
        cnt ((keyword (:id_str t)) (:by-rt-since-startup @state/app))
        reach ((keyword (:id_str t)) (:by-reach @state/app))]
    (if (> cnt 0) (str "analyzed: " (number-format cnt) " retweets, reach " (number-format reach)))))

(defn swap-pmap
  "swaps item in priority-map"
  [app priority-map id n]
  (swap! app assoc priority-map (assoc (priority-map @app) id n)))

(defn tweets-by-order
  "find top n tweets by specified order"
  [tweets-map order]
  (fn [app n skip]
    (vec (map (fn [m] ((keyword (first m))(tweets-map app))) (take n (drop (* n skip) (order app)))))))

(defn tweets-by-order2
  "find top n tweets by specified order"
  [order app n skip]
  (vec
   (filter identity
           (map
            (fn [m] ((first m) (:tweets-map app)))
            (->> (order app)
                 (drop (* n skip) ,)
                 (take n ,))))))
