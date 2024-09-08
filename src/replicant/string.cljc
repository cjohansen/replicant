(ns replicant.string
  (:require [clojure.string :as str]
            [replicant.core :as r]
            [replicant.hiccup :as hiccup]))

(def self-closing?
  #{"area" "audio" "base" "br" "col" "embed" "hr" "img"
    "input" "link" "meta" "param" "source" "track" "wbr"})

(defn render-attrs [attrs]
  (some->> (dissoc attrs :on)
           (keep (fn [[k v]]
                   (when (and v (nil? (namespace k)))
                     (case k
                       :classes
                       (str "class=\"" (str/join " " v) "\"")

                       :style
                       (str "style=\"" (->> (keep
                                             (fn [[prop val]]
                                               (when-let [val (r/get-style-val prop val)]
                                                 (str (name prop) ": " val ";")))
                                             v)
                                            (str/join " ")) "\"")

                       (str (name k)
                            (when (or (number? v)
                                      (and (string? v) (< 0 (count v))))
                              (str "=\"" v "\"")))))))
           seq
           (str/join " ")
           (str " ")))

(defn escape-html
  "Change special characters into HTML character entities.

  Taken from Hiccup:
  https://github.com/weavejester/hiccup/blob/5a6d45c17728dcbcb3aeb32ea890fd9dc1508547/src/hiccup/util.clj#L80-L88"
  [text]
  (-> text
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&apos;")))

(defn render-node [headers & [{:keys [depth indent]}]]
  (let [indent-s (when (< 0 indent) (str/join (repeat (* depth indent) " ")))
        newline (when (< 0 indent) "\n")]
    (if-let [text (hiccup/text headers)]
      (str indent-s (apply str (map escape-html text)) newline)
      (let [tag-name (hiccup/tag-name headers)
            attrs (r/get-attrs headers)
            innerHTML (:innerHTML attrs)
            rendered-attrs (render-attrs (dissoc attrs :innerHTML))]
        (str indent-s
             "<" tag-name
             (when (and (= "svg" tag-name)
                        (not (:xmlns attrs)))
               (str " xmlns=\"http://www.w3.org/2000/svg\""))
             rendered-attrs ">"
             newline
             (or innerHTML
                 (->> (r/get-children headers (hiccup/html-ns headers))
                      (keep #(some-> % (render-node {:depth (inc depth) :indent indent})))
                      str/join))
             (when-not (self-closing? tag-name)
               (str indent-s "</" tag-name ">" newline)))))))

(defn render [hiccup & [{:keys [indent]}]]
  (if hiccup
    (render-node (r/get-hiccup-headers hiccup nil) {:indent (or indent 0) :depth 0})
    ""))
