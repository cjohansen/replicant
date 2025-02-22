(ns replicant.duplicate-key-bug
  (:require [replicant.dom :as r]))

(def initial-data
  '{:session/route :route/home
    :scans
    [{:scan/id #uuid "67ad8476-5d02-489a-a17b-9e41c32d6655"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:34:46.516-00:00"}
     {:scan/id #uuid "67ad846f-4958-4373-b60e-6826559bc80e"
      :scan/tape-keycode "23457"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:34:39.985-00:00"}
     {:scan/id #uuid "67ad8469-9b87-4aca-994f-a4d7ce051825"
      :scan/tape-keycode "22222"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:34:33.503-00:00"}
     {:scan/id #uuid "67ad845d-294f-4b93-abc5-eceb88428306"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:34:21.409-00:00"}
     {:scan/id #uuid "67ad8420-0832-45e8-a28c-d9fdcf7ddb93"
      :scan/tape-keycode "23457"
      :scan/location {:location/title "Area B"}
      :scan/date #inst "2025-02-13T05:33:20.295-00:00"}
     {:scan/id #uuid "67ad8409-5474-46ae-ac7e-ca598a29beff"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:57.676-00:00"}
     {:scan/id #uuid "67ad8403-b367-42ff-bcd1-968f68adcc26"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area B"}
      :scan/date #inst "2025-02-13T05:32:51.495-00:00"}
     {:scan/id #uuid "67ad83f9-b4f4-41e5-9847-41c84c144aad"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:41.259-00:00"}
     {:scan/id #uuid "67ad83f3-7ac2-4d57-80e0-bf2ac48e1b06"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:35.096-00:00"}
     {:scan/id #uuid "67ad83ee-8e86-4ec5-83cb-1fc157250fe2"
      :scan/tape-keycode "23457"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:30.073-00:00"}
     {:scan/id #uuid "67ad83e8-f623-4830-9618-aa4275eca3f1"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:24.196-00:00"}
     {:scan/id #uuid "67ad83e2-1730-4d30-90c1-96f904ac0962"
      :scan/tape-keycode "11111"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T05:32:18.111-00:00"}
     {:scan/id #uuid "67ad7a02-e77d-456d-9d89-203f78a1b36f"
      :scan/tape-keycode "12345"
      :scan/location {:location/title "Area A"}
      :scan/date #inst "2025-02-13T04:50:10.083-00:00"}
     {:scan/tape-keycode "B1234915"
      :scan/location {:location/title "Area B"}
      :scan/date #inst "2025-02-01T00:00:00.000-00:00"}
     {:scan/tape-keycode "B1844650"
      :scan/location {:location/title "Area B"}
      :scan/date #inst "2025-01-01T00:00:00.000-00:00"}]
    :locations
    ({:location/title "Area A"
      :location/id #uuid "7962f42c-4370-438a-98ce-4261d772b91e"}
     {:location/title "Area B"
      :location/id #uuid "872b005f-d601-4197-9dee-932116ff2758"})
    :form/new-scan {:values {:key-code "" :location ""}}})

(def extra-scan
  {:scan/id #uuid "67ad848f-1397-4c2c-9221-f3dc52c5eda4"
   :scan/tape-keycode "11111"
   :scan/location {:location/title "Area A"}
   :scan/date #inst "2025-02-13T05:35:11.903-00:00"})

(defn scans-view [{:keys [scans]}]
  [:ul {:role "list" :class '[divide-y divide-gray-100 dark:divide-gray-700]}
   (for [{:scan/keys [tape-keycode location date]} scans]
     [:li {:replicant/key tape-keycode
           :class '[flex justify-between gap-x-6 py-5]}
      [:div {:class '[flex min-w-0 gap-x-4]}
       [:div {:class '[min-w-0 flex-auto]}
        [:p {:class '["text-s/6" font-semibold text-gray-900 dark:text-white]}
         [:span (or tape-keycode "unknown keycode")]
         [:span {:class '[font-light text-gray-500 dark:text-gray-400]} " - "]
         [:span (:location/title location)]]
        [:p {:class '[mt-1 truncate "text-xs/5" text-gray-500 dark:text-gray-400]}
         [:time {:datetime date} (.toLocaleString (js/Date. date))]]]]])])

(defn app [state]
  [:div
   [:button {:on {:click [:add-scan]}} "Add another"]
   (scans-view state)])

(defn start []
  (let [store (atom nil)]
    (add-watch store ::render (fn [_ _ _ state]
                                (r/render js/document.body (app state))))

    (r/set-dispatch!
     (fn [_ data]
       (case data
         [:add-scan] (swap! store update :scans conj extra-scan))))

    (reset! store initial-data)))

(comment

  (start)

)
