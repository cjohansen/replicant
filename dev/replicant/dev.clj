(ns replicant.dev
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [replicant.dev-actions :as actions]
            [replicant.hiccup :as hiccup]
            [replicant.shadow-dom]
            [replicant.string :as s]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource]))

(def store (atom {}))

(def examples
  [replicant.shadow-dom/example])

(defn render-frontpage []
  [:main
   [:h1 "Replicant examples"]
   [:ul.list
    (for [{:keys [title k]} examples]
      [:li
       [:a.link
        {:href (str "/examples/" (name k))}
        title]])]])

(defn render-example [id]
  (let [render (:f (first (filter (comp #{id} :k) examples)))]
    (render @store id)))

(defn dispatch-actions [actions]
  (doseq [[action & args] actions]
    (actions/process-actions {:store store
                              :dispatch-actions #(dispatch-actions %2)
                              :examples examples} action args)))

(defn process-actions [req]
  (dispatch-actions (read-string (slurp (:body req))))
  {:status 200})

(defn handler [req]
  (let [[_ kind id] (str/split (:uri req) #"/")]
    (cond
      (= "examples" kind)
      {:body (render-example (keyword id))}

      (= "actions" kind)
      (process-actions req)

      (nil? kind)
      {:body (render-frontpage)}

      :else
      {:status 404})))

(defn wrap-hiccup [handler]
  (fn [req]
    (let [res (handler req)]
      (if (hiccup/hiccup? (:body res))
        (-> res
            (assoc-in [:headers "content-type"] "text/html")
            (update :body s/render))
        res))))

(def layout
  (let [html (slurp (io/resource "public/index.html"))]
    [(str (first (str/split html #"<body>")) "<body>")
     (str "<script src=\"/server-actions.js\"></script>"
          "<p><a href=\"/\">Back to examples</a></p>"
          "</body>"
          (second (str/split html #"</body>")))]))

(defn wrap-layout [handler]
  (fn [req]
    (let [res (handler req)]
      (cond-> res
        (= "text/html" (get-in res [:headers "content-type"]))
        (update :body #(str (first layout) % (second layout)))))))

(defn datafy-event-handlers [hiccup]
  (walk/postwalk
   (fn [x]
     (if (:on x)
       (into (dissoc x :on)
             (for [[event actions] (:on x)]
               [(keyword (str "data-replicant-" (name event)))
                (pr-str actions)]))
       x))
   hiccup))

(defn wrap-datafy-event-handlers [handler]
  (fn [req]
    (let [res (handler req)]
      (cond-> res
        (hiccup/hiccup? (:body res))
        (update :body datafy-event-handlers)))))

(defonce server (atom nil))

(defn start []
  (when-not @server
    (reset! server (-> #'handler
                       wrap-datafy-event-handlers
                       wrap-hiccup
                       wrap-layout
                       (ring.middleware.resource/wrap-resource "public")
                       (jetty/run-jetty {:port 8079
                                         :join? false})))))

(defn stop []
  (org.eclipse.jetty.server.Server/.stop @server)
  (reset! server nil))

(defn restart []
  (stop)
  (start))

(comment
  (set! *print-namespace-maps* false)
  (start)
  (stop)
  (restart)
  )
