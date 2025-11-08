(ns replicant.top-level-collection)

(defn app [{:keys [text]}]
  (list
   [:h1 "Top-level colllection"]
   [:p "These elements are rendered as direct decendants of body"]
   [:p "By passing a list to replicant.dom/render, you can avoid unnecessary wrapping divs"]
   [:p text]))

(defn render [text]
  (d/render js/document.body (app {:text text})))

(defn ^:export start [text]
  (set! (.-innerHTML js/document.body) "")
  (render text))

(comment

  (start "This final paragraph may change")
  (render "Like that - it changed!")

  )
