(ns replicant.string
  (:require [clojure.string :as str]
            [replicant.core :as r]
            [replicant.hiccup :as hiccup]))

(def self-closing?
  #{"area" "audio" "base" "br" "col" "embed" "hr" "img"
    "input" "link" "meta" "param" "source" "track" "wbr"})

(defn render-attrs [attrs]
  (some->> attrs
           (keep (fn [[k v]]
                   (when v
                     (case k
                       :classes
                       (str "class=\"" (str/join " " v) "\"")

                       :style
                       (str "style=\"" (->> (keep
                                             (fn [[prop val]]
                                               (when val
                                                 (str (name prop) ": " val ";")))
                                             v)
                                            (str/join " ")) "\"")

                       (str (name k)
                            (when (and (string? v) (< 0 (count v)))
                              (str "=\"" v "\"")))))))
           seq
           (str/join " ")
           (str " ")))

(defn render-node [headers & [{:keys [depth indent]}]]
  (let [indent-s (when (< 0 indent) (str/join (repeat (* depth indent) " ")))
        newline (when (< 0 indent) "\n")]
    (if-let [text (hiccup/text headers)]
      (str indent-s text newline)
      (let [tag-name (hiccup/tag-name headers)]
        (str indent-s
             "<" tag-name
             (when (= "svg" tag-name)
               (str " xmlns=\"http://www.w3.org/2000/svg\""))
             (render-attrs (r/get-attrs headers)) ">"
             newline
             (->> (r/get-children headers (hiccup/html-ns headers))
                  (keep #(some-> % (render-node {:depth (inc depth) :indent indent})))
                  str/join)
             (when-not (self-closing? tag-name)
               (str indent-s "</" tag-name ">" newline)))))))

(defn render [hiccup & [{:keys [indent]}]]
  (if hiccup
    (render-node (r/get-hiccup-headers hiccup nil) {:indent (or indent 0) :depth 0})
    ""))
