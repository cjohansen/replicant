(ns replicant.string
  (:require [clojure.string :as str]
            [replicant.alias :as alias]
            [replicant.core :as r]
            [replicant.hiccup-headers :as hiccup]))

(def self-closing?
  #{"area" "audio" "base" "br" "col" "embed" "hr" "img"
    "input" "link" "meta" "param" "source" "track" "wbr"})

(defn render-attrs [attrs]
  (some->> (dissoc attrs :on)
           (keep (fn [[k v]]
                   (when (and v (nil? (namespace k)))
                     (let [v (cond-> v
                               (keyword? v)
                               name)]
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
                                (str "=\"" v "\""))))))))
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

(defn get-expanded-headers [opt headers]
  (when (and (qualified-keyword? (hiccup/tag-name headers))
             (nil? (get (:aliases opt) (hiccup/tag-name headers))))
    (throw (ex-info (str "Tried to expand undefined alias " (hiccup/tag-name headers))
                    {:missing (hiccup/tag-name headers)
                     :available (:aliases opt)})))
  (or (when-let [aliased (r/get-alias-headers opt headers)]
        (get-expanded-headers opt aliased))
      headers))

(defn render-node [headers & [{:keys [depth indent aliases alias-data]}]]
  (let [indent-s (when (< 0 indent) (str/join (repeat (* depth indent) " ")))
        newline (when (< 0 indent) "\n")
        headers (get-expanded-headers {:aliases aliases
                                       :alias-data alias-data} headers)]
    (if-let [text (hiccup/text headers)]
      (str indent-s (apply str (map escape-html text)) newline)
      (let [tag-name (hiccup/tag-name headers)
            attrs (r/get-attrs headers)]
        (str indent-s
             "<" tag-name
             (when (and (= "svg" tag-name)
                        (not (:xmlns attrs)))
               (str " xmlns=\"http://www.w3.org/2000/svg\""))
             (render-attrs (dissoc attrs :innerHTML)) ">"
             newline
             (or (:innerHTML attrs)
                 (->> (r/get-children headers (hiccup/html-ns headers))
                      (keep #(some-> % (render-node {:depth (inc depth)
                                                     :indent indent
                                                     :aliases aliases
                                                     :alias-data alias-data})))
                      str/join))
             (when-not (self-closing? tag-name)
               (str indent-s "</" tag-name ">" newline)))))))

(defn render [hiccup & [{:keys [aliases alias-data indent]}]]
  (if hiccup
    (render-node (r/get-hiccup-headers nil hiccup)
                 {:indent (or indent 0)
                  :depth 0
                  :aliases (or aliases (alias/get-registered-aliases))
                  :alias-data alias-data})
    ""))
