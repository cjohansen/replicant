(ns replicant.string
  (:require [clojure.string :as str]
            [replicant.alias :as alias]
            [replicant.core :as r]
            [replicant.hiccup-headers :as hiccup]))

(defprotocol IStringifier
  (append [this s])
  (to-string [this]))

(defn create-renderer []
  #?(:clj
     (let [sb (StringBuilder.)]
       (reify IStringifier
         (append [_ s]
           (.append sb s))

         (to-string [_]
           (.toString sb))))

     :cljs
     (let [sb #js []]
       (reify IStringifier
         (append [_ s]
           (.push sb s))

         (to-string [_]
           (.join sb ""))))))

(def ^:no-doc self-closing?
  #{"area" "audio" "base" "br" "col" "embed" "hr" "img"
    "input" "link" "meta" "param" "source" "track" "wbr"})

(defn str-join [stringifier sep xs]
  (some->> (first xs) (append stringifier))
  (doseq [x (rest xs)]
    (when x
      (append stringifier sep)
      (append stringifier x)))
  stringifier)

(defn ^:no-doc render-attrs [stringifier attrs]
  (reduce-kv
   (fn [_ k v]
     (when (and (not (#{:on :innerHTML} k))
                v
                (nil? (namespace k)))
       (let [v (cond-> v
                 (keyword? v)
                 name)]
         (append stringifier " ")
         (case k
           :classes
           (do
             (append stringifier "class=\"")
             (str-join stringifier " " v)
             (append stringifier "\""))

           :style
           (do
             (append stringifier "style=\"")
             (->> v
                  (keep
                   (fn [[prop val]]
                     (when-let [val (r/get-style-val prop val)]
                       (str (name prop) ": " val ";"))))
                  (str-join stringifier " "))
             (append stringifier "\""))

           (if (or (number? v)
                   (and (string? v) (< 0 (count v))))
             (doto stringifier
               (append (name k))
               (append "=\"")
               (append v)
               (append "\""))
             (append stringifier (name k))))))
     nil)
   nil
   attrs))

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

(defn ^:no-doc get-expanded-headers [opt headers]
  (when (and (qualified-keyword? (hiccup/tag-name headers))
             (nil? (get (:aliases opt) (hiccup/tag-name headers))))
    (throw (ex-info (str "Tried to expand undefined alias " (hiccup/tag-name headers))
                    {:missing (hiccup/tag-name headers)
                     :available (:aliases opt)})))
  (or (when-let [aliased (r/get-alias-headers opt headers)]
        (get-expanded-headers opt aliased))
      headers))

(defn ^:no-doc render-node [stringifier headers {:keys [depth indent aliases alias-data]}]
  (let [indent? (pos? indent)
        indent-s (if indent? (str/join (repeat (* depth indent) " ")) "")
        newline (if indent? "\n" "")
        headers (get-expanded-headers {:aliases aliases
                                       :alias-data alias-data} headers)]
    (if-let [text (hiccup/text headers)]
      (doto stringifier
        (append indent-s)
        (append (escape-html text))
        (append newline))
      (let [tag-name (hiccup/tag-name headers)
            attrs (r/get-attrs headers)
            ns-string (if (and (= "svg" tag-name)
                               (not (:xmlns attrs)))
                        " xmlns=\"http://www.w3.org/2000/svg\""
                        "")]
        (doto stringifier
          (append indent-s)
          (append "<")
          (append tag-name)
          (append ns-string))
        (render-attrs stringifier attrs)
        (doto stringifier
          (append ">")
          (append newline))
        (if (:innerHTML attrs)
          (append stringifier (:innerHTML attrs))
          (run!
           (fn [child]
             (when child
               (render-node
                stringifier
                child
                {:depth (inc depth)
                 :indent indent
                 :aliases aliases
                 :alias-data alias-data})))
           (r/get-children headers (hiccup/html-ns headers))))
        (when-not (self-closing? tag-name)
          (doto stringifier
            (append indent-s)
            (append "</")
            (append tag-name)
            (append ">")
            (append newline)))
        stringifier))))

(defn render
  "Render `hiccup` to a string of HTML"
  [hiccup & [{:keys [aliases alias-data indent]}]]
  (if hiccup
    (let [stringifier (create-renderer)]
      (render-node
       stringifier
       (r/get-hiccup-headers nil hiccup)
       {:indent (or indent 0)
        :depth 0
        :aliases (or aliases (alias/get-registered-aliases))
        :alias-data alias-data})
      (to-string stringifier))
    ""))
